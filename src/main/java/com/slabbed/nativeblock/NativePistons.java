package com.slabbed.nativeblock;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Copies frozen source facts through vanilla piston identities without consulting supports (LAW.md). */
public final class NativePistons {
    private NativePistons() {}

    public static void publish(World world,BlockPos pos,Integer halfOffset) {
        if (world==null || halfOffset==null) return;
        var state=world.getBlockState(pos);
        if (state.isAir()) return;
        var desired=new NativeSeats.Seat(halfOffset,Registries.BLOCK.getId(state.getBlock()).toString());
        if (!desired.equals(NativeSeats.seat(world,pos))) NativeSeats.put(world,pos,desired);
        if (state.isOf(Blocks.MOVING_PISTON) && world.getBlockEntity(pos) instanceof PistonBlockEntity entity)
            ((NativePistonPose)entity).slabbed$halfOffset(halfOffset);
    }

    public static void capture(PistonBlockEntity entity) {
        var pose=(NativePistonPose)entity;
        if (pose.slabbed$halfOffset()!=null || entity.getWorld()==null) return;
        var seat=NativeSeats.seat(entity.getWorld(),entity.getPos());
        if (seat!=null) pose.slabbed$halfOffset(seat.halfOffset());
    }

    public static void settle(PistonBlockEntity entity) {
        var world=entity.getWorld();
        if (world==null) return;
        var current=world.getBlockState(entity.getPos());
        if (current.isOf(Blocks.MOVING_PISTON) || current.getBlock()==entity.getPushedBlock().getBlock())
            publish(world,entity.getPos(),((NativePistonPose)entity).slabbed$halfOffset());
    }
}
