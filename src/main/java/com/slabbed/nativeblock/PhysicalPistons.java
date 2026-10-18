package com.slabbed.nativeblock;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import com.slabbed.cell.PieceRules;
import com.slabbed.cell.SupplementalCells;
import com.slabbed.core.CellContents;
import com.slabbed.core.PhysicalPush;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Physical pieces move once per logical identity; native owners retain vanilla data (DY_SPEC.md). */
public final class PhysicalPistons {
    private PhysicalPistons() {}
    public record Plan(List<PhysicalPush.Move<BlockState>> moves,boolean accepted) {}

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
        if(!physical)return null;
        var motion=extending?facing:facing.getOpposite();
        var seeds=occupants(world,piston,facing,bounds);
        var result=PhysicalPush.plan(seeds,PhysicalPush.Direction6.valueOf(motion.name()),
                destination->occupants(world,piston,facing,destination));
        if(!result.success())return new Plan(List.of(),false);
        var head=piston.offset(facing);
        var headState=world.getBlockState(head);
        if(extending && NativeSeats.seat(world,head)!=null && !headState.isOf(Blocks.PISTON_HEAD))
            return new Plan(List.of(),false);
        // Validate all target fragments before the first world write.
        try { changed(world,result.moves(),true); }
        catch(IllegalArgumentException|IllegalStateException invalid) {return new Plan(List.of(),false);}
        return new Plan(result.moves(),true);
    }

    private static List<PhysicalPush.Piece<BlockState>> occupants(World world,BlockPos piston,Direction facing,PhysicalPush.Bounds bounds) {
        var result=new LinkedHashMap<PhysicalPush.SpatialKey,PhysicalPush.Piece<BlockState>>();
        for(int x=bounds.minX();x<bounds.maxX();x++) for(int z=bounds.minZ();z<bounds.maxZ();z++) {
            int low=Math.floorDiv(bounds.minHalf(),2),high=Math.floorDiv(bounds.maxHalf()-1,2);
            for(int y=low;y<=high;y++) {
                var pos=new BlockPos(x,y,z);
                if(!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)) {
                    add(result,new PhysicalPush.Piece<>(x,z,y*2,2,Blocks.BEDROCK.getDefaultState(),false));continue;
                }
                var contents=staticContents(world,pos);
                if(contents!=null) for(var part:contents.fragments()) {
                    var piece=new PhysicalPush.Piece<>(x,z,y*2+part.offset(),part.length(),part.value(),
                            part.value().getPistonBehavior()==PistonBehavior.NORMAL && part.value().getHardness(world,pos)>=0);
                    if(bounds.intersects(piece.bounds())) add(result,piece);
                }
            }
            for(int y=low-1;y<=high+1;y++) {
                var pos=new BlockPos(x,y,z);var state=world.getBlockState(pos);
                if(pos.equals(piston) || (pos.equals(piston.offset(facing)) && state.isOf(Blocks.PISTON_HEAD)))continue;
                var seat=NativeSeats.seat(world,pos);
                if(seat==null && staticContents(world,pos)!=null)continue;
                if(state.isAir() || state.isReplaceable())continue;
                int offset=seat==null?0:seat.halfOffset();
                var shape=NativeShapes.occupied(world,pos,state,offset);
                var test=new net.minecraft.util.math.Box(bounds.minX()-x,bounds.minHalf()*.5-y,bounds.minZ()-z,
                        bounds.maxX()-x,bounds.maxHalf()*.5-y,bounds.maxZ()-z);
                if(shape.getBoundingBoxes().stream().anyMatch(box->box.intersects(test)))
                    add(result,new PhysicalPush.Piece<>(x,z,y*2+offset,2,state,false));
            }
        }
        return new ArrayList<>(result.values());
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
    private static Map<BlockPos,CellContents<BlockState>> changed(World world,List<PhysicalPush.Move<BlockState>> moves,boolean addTargets) {
        var changes=new LinkedHashMap<BlockPos,CellContents<BlockState>>();
        for(var move:moves) {
            var source=move.source();
            for(int y=Math.floorDiv(source.originHalf(),2);y<=Math.floorDiv(source.originHalf()+source.length()-1,2);y++) {
                var pos=new BlockPos(source.x(),y,source.z());
                var contents=changes.computeIfAbsent(pos,p->staticContents(world,p));
                if(contents==null)throw new IllegalStateException("missing physical source");
                changes.put(pos,contents.remove(source.originHalf()-y*2));
            }
        }
        if(addTargets) for(var move:moves) {
            var target=move.destination();int origin=target.minHalf(),length=move.source().length();
            for(int y=Math.floorDiv(origin,2);y<=Math.floorDiv(origin+length-1,2);y++) {
                var pos=new BlockPos(target.minX(),y,target.minZ());
                var contents=changes.computeIfAbsent(pos,p->staticContents(world,p));
                if(contents==null)throw new IllegalStateException("blocked physical destination");
                changes.put(pos,contents.add(new CellContents.Fragment<>(origin-y*2,length,move.source().value())));
            }
        }
        return changes;
    }
    public static boolean move(World world,Plan plan,java.util.function.BooleanSupplier vanilla) {
        if(!plan.accepted())return false;
        return CellWorld.pistonTransaction(world,()-> {
            var removed=changed(world,plan.moves(),false);
            var originals=new LinkedHashMap<BlockPos,CellContents<BlockState>>();
            removed.keySet().forEach(pos->originals.put(pos,staticContents(world,pos)));
            CellWorld.applyPiston(world,removed);
            if(!vanilla.getAsBoolean()) {CellWorld.applyPiston(world,originals);return false;}
            var targets=new LinkedHashMap<BlockPos,CellContents<BlockState>>();
            for(var move:plan.moves()) {
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
