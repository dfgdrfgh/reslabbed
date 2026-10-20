package com.slabbed.cell;

import com.slabbed.Slabbed;
import com.slabbed.core.CellContents;
import com.slabbed.core.HalfGrid;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.LinkedHashMap;
import java.util.Map;

/** Transactions touch only the cells intersecting a piece. See LAW.md. */
public final class CellWorld {
    private static final ThreadLocal<Boolean> MUTATING=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<java.util.Set<BlockPos>> PISTON_CHANGES=new ThreadLocal<>();
    private CellWorld() {}
    public static boolean mutating() { return MUTATING.get(); }
    public static int length(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE)!=SlabType.DOUBLE ? 1 : 2;
    }
    public static CellContents<BlockState> contents(World world,BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return null;
        var supplemental=SupplementalCells.contents(world,pos);
        if(supplemental!=null)return supplemental;
        var state=world.getBlockState(pos);
        var nativeSeat=com.slabbed.nativeblock.NativeSeats.seat(world,pos);
        if(nativeSeat!=null)return CellContents.empty();
        if (state.isOf(Slabbed.CELL)) return world.getBlockEntity(pos) instanceof CellBlockEntity cell?cell.contents():null;
        if (state.hasBlockEntity()) return null;
        if (state.isAir() || state.isReplaceable()) return CellContents.empty();
        if (state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE)!=SlabType.DOUBLE
                && !state.get(SlabBlock.WATERLOGGED)) {
            int half=state.get(SlabBlock.TYPE)==SlabType.TOP?1:0;
            return CellContents.<BlockState>empty().add(new CellContents.Fragment<>(half,1,state.with(SlabBlock.TYPE,SlabType.BOTTOM)));
        }
        return null;
    }
    public static boolean canPlace(World world,int x,int z,int origin,BlockState state) {
        return prepare(world,x,z,origin,state)!=null;
    }
    public static boolean place(World world,int x,int z,int origin,BlockState state) {
        var changes=prepare(world,x,z,origin,state);
        if(changes==null)return false;
        commit(world,changes);
        return true;
    }
    private static Map<BlockPos,CellContents<BlockState>> prepare(World world,int x,int z,int origin,BlockState state) {
        return prepare(world,x,z,origin,state,Map.of());
    }
    private static Map<BlockPos,CellContents<BlockState>> prepare(World world,int x,int z,int origin,BlockState state,
                                                                 Map<BlockPos,CellContents<BlockState>> initial) {
        if (!PieceRules.accepts(state)) return null;
        if (state.getBlock() instanceof SlabBlock && length(state)==1) state=state.with(SlabBlock.TYPE,SlabType.BOTTOM);
        if(com.slabbed.nativeblock.NativeShapes.intersects(world,x,z,origin,state))return null;
        int length=length(state); int low=HalfGrid.at(origin).cellY(),high=HalfGrid.at(origin+length-1).cellY();
        Map<BlockPos,CellContents<BlockState>> changes=new LinkedHashMap<>();
        for (int y=low;y<=high;y++) {
            var pos=new BlockPos(x,y,z);
            if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) return null;
            var before=initial.containsKey(pos)?initial.get(pos):contents(world,pos); if(before==null) return null;
            try { changes.put(pos,before.add(new CellContents.Fragment<>(origin-y*2,length,state))); }
            catch(IllegalArgumentException overlap) { return null; }
        }
        return changes;
    }
    /** An explicit second slab placement replaces the selected half with one vanilla double slab. */
    public static boolean extendSlab(World world,BlockPos cellPos,int offset,int addedOrigin) {
        var before=contents(world,cellPos);if(before==null)return false;
        var part=before.fragments().stream().filter(p->p.offset()==offset).findFirst().orElse(null);
        int original=cellPos.getY()*2+offset;
        if(part==null || part.length()!=1 || !(part.value().getBlock() instanceof SlabBlock)
                || Math.abs(addedOrigin-original)!=1)return false;
        var state=part.value().with(SlabBlock.TYPE,SlabType.DOUBLE);
        var changes=prepare(world,cellPos.getX(),cellPos.getZ(),Math.min(original,addedOrigin),state,
                Map.of(cellPos,before.remove(offset)));
        if(changes==null)return false;
        if(!world.isClient)commit(world,changes);
        return true;
    }
    public static void remove(World world,BlockPos cellPos,int offset,PlayerEntity player,boolean drop) {
        var cell=contents(world,cellPos); if(cell==null) return;
        var fragment=cell.fragments().stream().filter(p->p.offset()==offset).findFirst().orElse(null);
        if(fragment==null) return;
        int origin=cellPos.getY()*2+offset;
        Map<BlockPos,CellContents<BlockState>> changes=new LinkedHashMap<>();
        for(int y=HalfGrid.at(origin).cellY();y<=HalfGrid.at(origin+fragment.length()-1).cellY();y++) {
            var pos=new BlockPos(cellPos.getX(),y,cellPos.getZ()); var before=contents(world,pos);
            if(before==null) return;
            changes.put(pos,before.remove(origin-y*2));
        }
        commit(world,changes);
        if(drop && !world.isClient) Block.dropStacks(fragment.value(),world,BlockPos.ofFloored(cellPos.getX(),origin*.5,cellPos.getZ()),null,player,player==null?ItemStack.EMPTY:player.getMainHandStack());
    }
    private static void commit(World world,Map<BlockPos,CellContents<BlockState>> changes) {
        boolean previous=MUTATING.get(); MUTATING.set(true);
        try {
            for(var entry:changes.entrySet()) {
                var pos=entry.getKey(); var next=entry.getValue();
                if(com.slabbed.nativeblock.NativeSeats.seat(world,pos)!=null && !world.getBlockState(pos).isOf(Slabbed.CELL)) {
                    SupplementalCells.put(world,pos,next);
                    world.getChunkManager().getLightingProvider().checkBlock(pos);
                    continue;
                }
                SupplementalCells.remove(world,pos);
                if(next.fragments().isEmpty()) { world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS); continue; }
                if(next.fragments().size()==1) {
                    var p=next.fragments().getFirst();
                    if(p.length()==1 && p.offset()>=0) {
                        var slab=p.value().with(SlabBlock.TYPE,p.offset()==0?SlabType.BOTTOM:SlabType.TOP);
                        world.setBlockState(pos,slab,Block.NOTIFY_LISTENERS); continue;
                    }
                    if(p.offset()==0 && p.length()==2) { world.setBlockState(pos,p.value(),Block.NOTIFY_LISTENERS); continue; }
                }
                if(!world.getBlockState(pos).isOf(Slabbed.CELL)) world.setBlockState(pos,Slabbed.CELL.getDefaultState(),Block.NOTIFY_LISTENERS);
                if(!(world.getBlockEntity(pos) instanceof CellBlockEntity cell)) throw new IllegalStateException("Missing physical cell");
                cell.setContents(next);
            }
        } finally { MUTATING.set(previous); }
        var deferred=PISTON_CHANGES.get();
        if(deferred!=null) { deferred.addAll(changes.keySet()); return; }
        publishChanges(world,changes.keySet());
    }
    private static void publishChanges(World world,java.util.Set<BlockPos> positions) {
        for(var pos:positions) {
            world.updateNeighbors(pos,world.getBlockState(pos).getBlock());
            world.updateListeners(pos,world.getBlockState(pos),world.getBlockState(pos),Block.NOTIFY_ALL);
            if(!world.isClient) {PieceSupport.revalidate(world,pos);PieceSupport.revalidateNear(world,pos); }
            if(!world.isClient) com.slabbed.nativeblock.NativeSupport.revalidateNear(world,pos);
        }
    }
    /** Piston fragments and the vanilla head publish as one final physical edit (LAW.md). */
    public static boolean pistonTransaction(World world,java.util.function.BooleanSupplier action) {
        if(PISTON_CHANGES.get()!=null) return action.getAsBoolean();
        var changed=new java.util.LinkedHashSet<BlockPos>();
        PISTON_CHANGES.set(changed);
        try { return action.getAsBoolean(); }
        finally { PISTON_CHANGES.remove(); publishChanges(world,changed); }
    }
    public static void applyPiston(World world,Map<BlockPos,CellContents<BlockState>> changes) {
        commit(world,changes);
    }
    public static CellContents.Fragment<BlockState> pick(World world,BlockPos pos,BlockHitResult hit) {
        var cell=contents(world,pos); if(cell==null) return null;
        if(hit==null) return SupplementalCells.has(world,pos) || cell.fragments().isEmpty()?null:cell.fragments().getLast();
        var point=hit.getPos().subtract(Vec3d.of(pos)).subtract(Vec3d.of(hit.getSide().getVector()).multiply(1e-5));
        for(var p:cell.fragments()) {
            var shape=com.slabbed.nativeblock.NativeShapes.outline(world,pos,p.value()).offset(0,p.offset()*.5,0);
            for(var box:shape.getBoundingBoxes()) if(box.expand(1e-6).contains(point)) return p;
        }
        return null;
    }
    public static boolean replacePiece(World world,BlockPos cellPos,int offset,BlockState next) {
        if(!PieceRules.accepts(next))return false;
        var old=contents(world,cellPos);if(old==null)return false;
        var part=old.fragments().stream().filter(p->p.offset()==offset).findFirst().orElse(null);
        if(part==null || length(next)!=part.length())return false;
        int origin=cellPos.getY()*2+offset;
        Map<BlockPos,CellContents<BlockState>> changes=new LinkedHashMap<>();
        for(int y=HalfGrid.at(origin).cellY();y<=HalfGrid.at(origin+part.length()-1).cellY();y++) {
            var pos=new BlockPos(cellPos.getX(),y,cellPos.getZ());var before=contents(world,pos);if(before==null)return false;
            int local=origin-y*2;
            changes.put(pos,before.remove(local).add(new CellContents.Fragment<>(local,part.length(),next)));
        }
        commit(world,changes);return true;
    }
    public static void promoteSupplemental(World world,BlockPos pos,CellContents<BlockState> parts) {
        commit(world,Map.of(pos,parts));
    }
    public static void clearOutsideCell(World world,BlockPos removed,CellContents<BlockState> snapshot) {
        Map<BlockPos,CellContents<BlockState>> changes=new LinkedHashMap<>();
        for(var part:snapshot.fragments()) {
            int origin=removed.getY()*2+part.offset();
            for(int y=HalfGrid.at(origin).cellY();y<=HalfGrid.at(origin+part.length()-1).cellY();y++) {
                var pos=new BlockPos(removed.getX(),y,removed.getZ());
                if(pos.equals(removed)) continue;
                var before=changes.containsKey(pos)?changes.get(pos):contents(world,pos);
                if(before!=null) changes.put(pos,before.remove(origin-y*2));
            }
        }
        commit(world,changes);
    }
}
