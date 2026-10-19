package com.slabbed.nativeblock;

import com.slabbed.cell.CellWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RedstoneView;
import net.minecraft.world.World;

/** A frozen piston also receives direct side power in its second physical cell (LAW.md). */
public final class NativePistonSignal {
    private NativePistonSignal() {}
    public static boolean physicalPower(RedstoneView view,BlockPos owner,Direction facing) {
        var seat=NativeSeats.seat(view,owner);
        if(seat==null || seat.halfOffset()==0)return false;
        var cell=owner.up(seat.halfOffset());
        for(var side:Direction.values())if(side!=facing && view.isEmittingRedstonePower(cell.offset(side),side))return true;
        return view.isEmittingRedstonePower(cell,Direction.DOWN);
    }
    /** Ordinary neighbor notifications do not reach an owner diagonally above the visible source. */
    public static void changed(World world,BlockPos source,BlockState sourceState) {
        if(world.isClient || CellWorld.mutating())return;
        for(var side:Direction.Type.HORIZONTAL)for(int y=-1;y<=1;y+=2) {
            var owner=source.offset(side).up(y);
            if(!world.isChunkLoaded(owner))continue;
            var state=world.getBlockState(owner);
            if(!(state.getBlock() instanceof PistonBlock))continue;
            var seat=NativeSeats.seat(world,owner);
            if(seat!=null && seat.halfOffset()==-y)state.neighborUpdate(world,owner,sourceState.getBlock(),source,false);
        }
    }
}
