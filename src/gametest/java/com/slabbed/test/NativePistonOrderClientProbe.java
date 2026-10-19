package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Deterministic packet ordering checks complement the live piston event cycle. */
public final class NativePistonOrderClientProbe {
    private static final BlockPos POS=new BlockPos(60,64,44);
    private NativePistonOrderClientProbe() {}
    public static void setup(World world) {
        world.setBlockState(POS,Blocks.PISTON.getDefaultState(),Block.NOTIFY_ALL);
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:piston"));
    }
    public static boolean ready(MinecraftClient client) {
        return client.world.getBlockState(POS).isOf(Blocks.PISTON) && NativeSeats.offset(client.world,POS)==-.5;
    }
    public static void verify(MinecraftClient client) {
        var world=client.world;
        // The final identity can be synchronized while the client still has a moving state.
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:piston"));
        world.setBlockState(POS,Blocks.MOVING_PISTON.getDefaultState(),Block.NOTIFY_LISTENERS);
        check(NativeSeats.offset(world,POS)==-.5,"final piston fact arriving before moving state retains height");
        world.setBlockState(POS,Blocks.PISTON.getDefaultState(),Block.NOTIFY_LISTENERS);
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:moving_piston"));
        var raw=NativeSeats.snapshot(world.getWorldChunk(POS));
        check(NativeSeats.offset(world,POS)==-.5,"moving fact arriving after final state retains height");
        check(raw==NativeSeats.snapshot(world.getWorldChunk(POS)),"pose reads leave the authoritative snapshot untouched");
        world.setBlockState(POS,Blocks.PISTON.getDefaultState(),Block.NOTIFY_LISTENERS);
        check(NativeSeats.offset(world,POS)==-.5,"no-op final state retains height");
        world.setBlockState(POS,Blocks.PISTON_HEAD.getDefaultState(),Block.NOTIFY_LISTENERS);
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:moving_piston"));
        check(NativeSeats.offset(world,POS)==-.5,"moving fact arriving after final head retains height");
        raw=NativeSeats.snapshot(world.getWorldChunk(POS));
        world.setBlockState(POS,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        check(NativeSeats.seat(world,POS)==null,"temporary air has no visible piston geometry");
        check(raw==NativeSeats.snapshot(world.getWorldChunk(POS)),"temporary client air does not erase an ahead moving fact");
        world.setBlockState(POS,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
        com.slabbed.nativeblock.NativePistons.publish(world,POS,-1);
        check(raw==NativeSeats.snapshot(world.getWorldChunk(POS)),"client piston settlement does not rewrite authoritative identity");
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:stone"));
        check(NativeSeats.offset(world,POS)==-.5,"authoritative final stone pose survives settlement");
        world.setBlockState(POS,Blocks.MOVING_PISTON.getDefaultState(),Block.NOTIFY_LISTENERS);
        var moving=(net.minecraft.block.entity.PistonBlockEntity)net.minecraft.block.PistonExtensionBlock.createBlockEntityPiston(
                POS,world.getBlockState(POS),Blocks.STONE.getDefaultState(),net.minecraft.util.math.Direction.EAST,true,false);
        moving.setWorld(world);world.addBlockEntity(moving);
        ((com.slabbed.nativeblock.NativePistonPose)moving).slabbed$halfOffset(0);
        raw=NativeSeats.snapshot(world.getWorldChunk(POS));
        com.slabbed.nativeblock.NativePistons.capture(moving);
        check(Integer.valueOf(-1).equals(((com.slabbed.nativeblock.NativePistonPose)moving).slabbed$halfOffset()),
                "ahead final stone fact corrects an initialized moving-entity fallback");
        check(raw==NativeSeats.snapshot(world.getWorldChunk(POS)),"moving capture does not rewrite authoritative facts");
        System.out.println("PHYSICAL_PISTON_CAPTURE_PASS");
        world.setBlockState(POS,Blocks.STICKY_PISTON.getDefaultState(),Block.NOTIFY_LISTENERS);
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:moving_piston"));
        check(NativeSeats.offset(world,POS)==-.5,"moving fact arriving after final sticky base retains height");
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:piston"));
        check(NativeSeats.seat(world,POS)==null,"base variants do not bridge directly without a moving identity");
        world.setBlockState(POS,Blocks.BARREL.getDefaultState(),Block.NOTIFY_LISTENERS);
        check(NativeSeats.seat(world,POS)==null,"unrelated replacement cannot acquire a piston pose");
        world.setBlockState(POS,Blocks.PISTON.getDefaultState(),Block.NOTIFY_LISTENERS);
        NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:piston"));
        System.out.println("PHYSICAL_PISTON_PACKET_ORDER_PASS");
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
