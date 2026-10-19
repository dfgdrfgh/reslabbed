package com.slabbed.nativeblock;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import com.slabbed.cell.PieceRules;
import com.slabbed.cell.SupplementalCells;
import com.slabbed.core.CellContents;
import com.slabbed.core.PhysicalPush;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Physical pieces move once per logical identity; native owners retain vanilla data (DY_SPEC.md). */
public final class PhysicalPistons {
    private static final ThreadLocal<Set<BlockPos>> VANILLA_SOURCES=new ThreadLocal<>();
    private PhysicalPistons() {}
    public record Plan(List<PhysicalPush.Move<BlockState>> moves,Set<BlockPos> nativeSources,boolean accepted) {
        public Plan {moves=List.copyOf(moves);nativeSources=Set.copyOf(nativeSources);}
    }

    /** The vanilla movement graph must match the native members of the physical plan exactly. */
    public static boolean vanillaMatches(List<BlockPos> sources) {
        var expected=VANILLA_SOURCES.get();
        return expected==null || expected.equals(Set.copyOf(sources));
    }

    private static boolean adhesive(BlockState state) {
        return state.isOf(Blocks.SLIME_BLOCK) || state.isOf(Blocks.HONEY_BLOCK);
    }

    private static Plan rejected() {return new Plan(List.of(),Set.of(),false);}

    /** Null retains the vanilla native-object path. A rejected physical plan changes nothing. */
    public static Plan prepare(World world,BlockPos piston,Direction facing,boolean extending) {
        int half=piston.getY()*2+NativePistons.baseHalf(world,piston);
        var front=piston.offset(facing,extending?1:2);
        int frontHalf=half+facing.getOffsetY()*(extending?2:4);
        var bounds=new PhysicalPush.Bounds(front.getX(),frontHalf,front.getZ(),front.getX()+1,frontHalf+2,front.getZ()+1);
        boolean physical=false;
        for(int y=Math.floorDiv(frontHalf,2);y<=Math.floorDiv(frontHalf+1,2);y++) {
            var cell=new BlockPos(front.getX(),y,front.getZ());
            if(world.getBlockState(cell).isOf(Slabbed.CELL) || SupplementalCells.has(world,cell)) physical=true;
        }
        var scene=new Scene(world,piston,facing);
        var seeds=scene.occupants(bounds);
        // Ordinary native piston graphs keep vanilla handling unless physical geometry participates.
        if(!physical && seeds.stream().noneMatch(piece->adhesive(piece.value())))return null;
        var motion=extending?facing:facing.getOpposite();
        var result=PhysicalPush.plan(seeds,PhysicalPush.Direction6.valueOf(motion.name()),
                scene::occupants,scene::attached);
        if(!physical && !scene.physical)return null;
        if(!result.success()) {
            com.slabbed.debug.DiagnosticWorld.piston(world,piston,"piston_plan",result.failure().name());return rejected();
        }
        var nativeSources=new java.util.LinkedHashSet<BlockPos>();
        for(var move:result.moves()) {
            var owner=scene.nativeSources.get(move.source().key());
            if(owner!=null)nativeSources.add(owner);
        }
        var head=piston.offset(facing);
        var headState=world.getBlockState(head);
        if(extending && NativeSeats.seat(world,head)!=null && !headState.isOf(Blocks.PISTON_HEAD)
                && !nativeSources.contains(head)) {
            com.slabbed.debug.DiagnosticWorld.piston(world,piston,"piston_plan","HEAD_TARGET_BLOCKED");return rejected();
        }
        var plan=new Plan(result.moves(),nativeSources,true);
        // Validate all target fragments before the first world write.
        try { changed(world,plan,true); }
        catch(IllegalArgumentException|IllegalStateException invalid) {
            com.slabbed.debug.DiagnosticWorld.piston(world,piston,"piston_plan","TARGET_BLOCKED_OR_CONFLICT");return rejected();
        }
        if(com.slabbed.debug.DiagnosticRecorder.isActive())com.slabbed.debug.DiagnosticWorld.piston(world,piston,
                "piston_plan","ACCEPTED moves="+plan.moves().size()+" facing="+facing+" extending="+extending);
        return plan;
    }

    private static final class Scene {
        private final World world;
        private final BlockPos piston;
        private final Direction facing;
        private final Map<PhysicalPush.SpatialKey,BlockPos> nativeSources=new LinkedHashMap<>();
        private boolean physical;

        private Scene(World world,BlockPos piston,Direction facing) {
            this.world=world;this.piston=piston;this.facing=facing;
        }

        private List<PhysicalPush.Piece<BlockState>> attached(PhysicalPush.Piece<BlockState> piece,PhysicalPush.Direction6 motion) {
            if(!adhesive(piece.value()))return List.of();
            var result=new LinkedHashMap<PhysicalPush.SpatialKey,PhysicalPush.Piece<BlockState>>();
            var bounds=piece.bounds();
            for(var face:Direction.values()) {
                PhysicalPush.Bounds adjacent;
                if(face==Direction.UP || face==Direction.DOWN) {
                    int half=face==Direction.UP?bounds.maxHalf():bounds.minHalf()-1;
                    adjacent=new PhysicalPush.Bounds(piece.x(),half,piece.z(),piece.x()+1,half+1,piece.z()+1);
                } else {
                    int x=piece.x()+face.getOffsetX(),z=piece.z()+face.getOffsetZ();
                    adjacent=new PhysicalPush.Bounds(x,bounds.minHalf(),z,x+1,bounds.maxHalf(),z+1);
                }
                for(var neighbor:occupants(adjacent)) {
                    var other=neighbor.bounds();
                    boolean contact=switch(face) {
                        case UP->other.minHalf()==bounds.maxHalf();
                        case DOWN->other.maxHalf()==bounds.minHalf();
                        case EAST->other.minX()==bounds.maxX();
                        case WEST->other.maxX()==bounds.minX();
                        case SOUTH->other.minZ()==bounds.maxZ();
                        case NORTH->other.maxZ()==bounds.minZ();
                    };
                    if(!contact || (adhesive(neighbor.value()) && piece.value().getBlock()!=neighbor.value().getBlock()))continue;
                    var owner=nativeSources.get(neighbor.key());
                    if(owner==null)owner=new BlockPos(neighbor.x(),Math.floorDiv(neighbor.originHalf(),2),neighbor.z());
                    // Vanilla ignores immovable side attachments; a blocked movable branch still rejects the push.
                    if(!neighbor.movable() && !PistonBlock.isMovable(neighbor.value(),world,owner,
                            Direction.valueOf(motion.name()),false,face))continue;
                    add(result,neighbor);
                }
            }
            return new ArrayList<>(result.values());
        }

        private List<PhysicalPush.Piece<BlockState>> occupants(PhysicalPush.Bounds bounds) {
            var result=new LinkedHashMap<PhysicalPush.SpatialKey,PhysicalPush.Piece<BlockState>>();
            for(int x=bounds.minX();x<bounds.maxX();x++) for(int z=bounds.minZ();z<bounds.maxZ();z++) {
                int low=Math.floorDiv(bounds.minHalf(),2),high=Math.floorDiv(bounds.maxHalf()-1,2);
                for(int y=low;y<=high;y++) {
                    var pos=new BlockPos(x,y,z);
                    if(!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)) {
                        add(result,new PhysicalPush.Piece<>(x,z,y*2,2,Blocks.BEDROCK.getDefaultState(),false));continue;
                    }
                    if(world.getBlockState(pos).isOf(Slabbed.CELL) || SupplementalCells.has(world,pos))physical=true;
                    var contents=staticContents(world,pos);
                    if(contents!=null) for(var part:contents.fragments()) {
                        var piece=new PhysicalPush.Piece<>(x,z,y*2+part.offset(),part.length(),part.value(),
                                part.value().getPistonBehavior()==PistonBehavior.NORMAL && part.value().getHardness(world,pos)>=0);
                        if(bounds.intersects(piece.bounds())) add(result,piece);
                    }
                }
                for(int y=low-1;y<=high+1;y++) {
                    var pos=new BlockPos(x,y,z);
                    if(!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos))continue;
                    var state=world.getBlockState(pos);
                    if(pos.equals(piston) || (pos.equals(piston.offset(facing)) && state.isOf(Blocks.PISTON_HEAD)))continue;
                    var seat=NativeSeats.seat(world,pos);
                    if(seat!=null && seat.halfOffset()!=0)physical=true;
                    if(seat==null && staticContents(world,pos)!=null)continue;
                    if(state.isAir() || state.isReplaceable())continue;
                    int offset=seat==null?0:seat.halfOffset();
                    var shape=NativeShapes.occupied(world,pos,state,offset);
                    var test=new net.minecraft.util.math.Box(bounds.minX()-x,bounds.minHalf()*.5-y,bounds.minZ()-z,
                            bounds.maxX()-x,bounds.maxHalf()*.5-y,bounds.maxZ()-z);
                    if(shape.getBoundingBoxes().stream().anyMatch(box->box.intersects(test))) {
                        var piece=new PhysicalPush.Piece<>(x,z,y*2+offset,2,state,adhesive(state)
                                && PistonBlock.isMovable(state,world,pos,facing,false,facing));
                        if(adhesive(state))nativeSources.put(piece.key(),pos.toImmutable());
                        add(result,piece);
                    }
                }
            }
            return new ArrayList<>(result.values());
        }
    }
    private static void add(Map<PhysicalPush.SpatialKey,PhysicalPush.Piece<BlockState>> pieces,PhysicalPush.Piece<BlockState> piece) {
        pieces.putIfAbsent(piece.key(),piece);
    }
    private static CellContents<BlockState> staticContents(World world,BlockPos pos) {
        var contents=CellWorld.contents(world,pos);
        if(contents!=null)return contents;
        var state=world.getBlockState(pos);
        if(NativeSeats.seat(world,pos)==null && PieceRules.accepts(state) && CellWorld.length(state)==2)
            return CellContents.<BlockState>empty().add(new CellContents.Fragment<>(0,2,state));
        return null;
    }
    private static Map<BlockPos,CellContents<BlockState>> changed(World world,Plan plan,boolean addTargets) {
        var changes=new LinkedHashMap<BlockPos,CellContents<BlockState>>();
        for(var move:plan.moves()) {
            var source=move.source();
            if(adhesive(source.value()))continue;
            for(int y=Math.floorDiv(source.originHalf(),2);y<=Math.floorDiv(source.originHalf()+source.length()-1,2);y++) {
                var pos=new BlockPos(source.x(),y,source.z());
                var contents=changes.computeIfAbsent(pos,p->staticContents(world,p));
                if(contents==null)throw new IllegalStateException("missing physical source");
                changes.put(pos,contents.remove(source.originHalf()-y*2));
            }
        }
        if(addTargets) for(var move:plan.moves()) {
            if(adhesive(move.source().value()))continue;
            var target=move.destination();int origin=target.minHalf(),length=move.source().length();
            for(int y=Math.floorDiv(origin,2);y<=Math.floorDiv(origin+length-1,2);y++) {
                var pos=new BlockPos(target.minX(),y,target.minZ());
                var contents=changes.computeIfAbsent(pos,p-> {
                    var before=staticContents(world,p);
                    return before==null && plan.nativeSources().contains(p)?CellContents.empty():before;
                });
                if(contents==null)throw new IllegalStateException("blocked physical destination");
                changes.put(pos,contents.add(new CellContents.Fragment<>(origin-y*2,length,move.source().value())));
            }
        }
        return changes;
    }
    public static boolean move(World world,Plan plan,java.util.function.BooleanSupplier vanilla) {
        if(!plan.accepted())return false;
        return CellWorld.pistonTransaction(world,()-> {
            var removed=changed(world,plan,false);
            var originals=new LinkedHashMap<BlockPos,CellContents<BlockState>>();
            removed.keySet().forEach(pos->originals.put(pos,staticContents(world,pos)));
            CellWorld.applyPiston(world,removed);
            boolean accepted;
            var previous=VANILLA_SOURCES.get();VANILLA_SOURCES.set(plan.nativeSources());
            try {accepted=vanilla.getAsBoolean();}
            finally {if(previous==null)VANILLA_SOURCES.remove();else VANILLA_SOURCES.set(previous);}
            if(!accepted) {CellWorld.applyPiston(world,originals);return false;}
            var targets=new LinkedHashMap<BlockPos,CellContents<BlockState>>();
            for(var move:plan.moves()) {
                if(adhesive(move.source().value()))continue;
                int origin=move.destination().minHalf(),length=move.source().length();
                for(int y=Math.floorDiv(origin,2);y<=Math.floorDiv(origin+length-1,2);y++) {
                    var pos=new BlockPos(move.destination().minX(),y,move.destination().minZ());
                    var contents=targets.computeIfAbsent(pos,p->staticContents(world,p));
                    if(contents==null)throw new IllegalStateException("piston destination changed during commit");
                    targets.put(pos,contents.add(new CellContents.Fragment<>(origin-y*2,length,move.source().value())));
                }
            }
            CellWorld.applyPiston(world,targets);return true;
        });
    }
}
