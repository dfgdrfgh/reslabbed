package com.slabbed.nativeblock;

import net.minecraft.block.BlockState;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Powered rails keep vanilla input and additionally hear their bounded physical cell. */
public final class NativeRailSignal {
    private NativeRailSignal() {}
    public static BlockPos drawnCell(BlockView world,BlockPos pos,BlockState state) {
        if(!NativeRailProfile.isRail(state))return null;
        var seat=NativeSeats.seat(world,pos);
        return seat!=null && seat.halfOffset()==-1?pos.down():null;
    }
    public static boolean hasDrawnCellSignal(World world,BlockPos pos) {
        var drawn=drawnCell(world,pos,world.getBlockState(pos));
        return drawn!=null && world.isReceivingRedstonePower(drawn);
    }
    public static boolean carriesADrawnPoweredRail(World world,BlockPos pos) {
        var above=pos.up();var state=world.getBlockState(above);
        return state.getBlock() instanceof PoweredRailBlock && pos.equals(drawnCell(world,above,state));
    }
}
