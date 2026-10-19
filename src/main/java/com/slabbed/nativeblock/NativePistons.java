package com.slabbed.nativeblock;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Copies frozen source facts through vanilla piston identities without consulting supports (LAW.md). */
public final class NativePistons {
    private record BasePose(World world,BlockPos pos,int half) {}
    private static final ThreadLocal<java.util.ArrayDeque<BasePose>> BASE_POSES=ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static final ThreadLocal<PistonBlockEntity> ANIMATING=new ThreadLocal<>();
    private NativePistons() {}

    /** Only the moving identity bridges ordinary piston base/head facts on the client (LAW.md). */
    public static boolean clientIdentity(String stored,String observed) {
        return stored.equals("minecraft:moving_piston") && pistonIdentity(observed)
                || observed.equals("minecraft:moving_piston") && pistonIdentity(stored);
    }
    private static boolean pistonIdentity(String identity) {
        return identity.equals("minecraft:piston") || identity.equals("minecraft:sticky_piston")
                || identity.equals("minecraft:piston_head");
    }
    public static boolean settling(World world,BlockPos pos,net.minecraft.block.BlockState next) {
        var entity=ANIMATING.get();
        return entity!=null && entity.getWorld()==world && entity.getPos().equals(pos)
                && next.getBlock()==entity.getPushedBlock().getBlock();
    }
    public static void animate(PistonBlockEntity entity,Runnable action) {
        var previous=ANIMATING.get();ANIMATING.set(entity);
        try {action.run();}
        finally {if(previous==null)ANIMATING.remove();else ANIMATING.set(previous);}
    }

    public static int baseHalf(World world,BlockPos pos) {
        for(var pose:BASE_POSES.get()) if(pose.world()==world && pose.pos().equals(pos))return pose.half();
        return (int)Math.round(NativeSeats.offset(world,pos)*2);
    }
    public static boolean withBasePose(World world,BlockPos pos,NativeSeats.Seat seat,java.util.function.BooleanSupplier action) {
        if(seat==null)return action.getAsBoolean();
        var stack=BASE_POSES.get();stack.push(new BasePose(world,pos.toImmutable(),seat.halfOffset()));
        try {return action.getAsBoolean();}
        finally {stack.pop();if(stack.isEmpty())BASE_POSES.remove();}
    }

    public static void publish(World world,BlockPos pos,Integer halfOffset) {
        if (world==null || halfOffset==null) return;
        var state=world.getBlockState(pos);
        if (state.isAir()) return;
        if(world.isClient) {
            // Client animation must not replace an authoritative attachment that arrived ahead of it.
            if(state.isOf(Blocks.MOVING_PISTON) && world.getBlockEntity(pos) instanceof PistonBlockEntity entity) {
                capture(entity);
                if(((NativePistonPose)entity).slabbed$halfOffset()==null && halfOffset!=0)
                    ((NativePistonPose)entity).slabbed$halfOffset(halfOffset);
            }
            return;
        }
        var desired=new NativeSeats.Seat(halfOffset,Registries.BLOCK.getId(state.getBlock()).toString());
        var saved=NativeSeats.snapshot(world.getWorldChunk(pos)).get(Long.toString(pos.asLong()));
        if (!desired.equals(saved)) NativeSeats.put(world,pos,desired);
        if (state.isOf(Blocks.MOVING_PISTON) && world.getBlockEntity(pos) instanceof PistonBlockEntity entity)
            ((NativePistonPose)entity).slabbed$halfOffset(halfOffset);
    }

    public static void capture(PistonBlockEntity entity) {
        var pose=(NativePistonPose)entity;
        var world=entity.getWorld();
        if(world==null || !world.isClient && pose.slabbed$halfOffset()!=null)return;
        var seat=NativeSeats.seat(world,entity.getPos());
        if(world.isClient && seat==null) {
            var saved=NativeSeats.snapshot(world.getWorldChunk(entity.getPos())).get(Long.toString(entity.getPos().asLong()));
            if(saved!=null && saved.block().equals(Registries.BLOCK.getId(entity.getPushedBlock().getBlock()).toString()))seat=saved;
        }
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
