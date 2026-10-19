package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** First-use piston initialization and native movement with initialized client snapshots. */
public final class NativePistonClientProbe {
    private static final BlockPos[] BASES={new BlockPos(34,64,8),new BlockPos(34,64,12)};
    private static int stage,ticks,started;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private NativePistonClientProbe() {}

    public static boolean tick(MinecraftClient client) {
        if(stage==99)return true;
        if(failure!=null)throw new AssertionError("native piston client probe failed",failure);
        check(++ticks<600,"native piston client timeout stage="+stage);
        if(pending)return false;
        if(stage==0) {
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(int x=33;x<=37;x++)for(int y=62;y<=66;y++)for(int z=7;z<=13;z++)
                    world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                for(int n=0;n<BASES.length;n++) {
                    var base=BASES[n];
                    world.setBlockState(base,Blocks.STICKY_PISTON.getDefaultState().with(PistonBlock.FACING,Direction.EAST),Block.NOTIFY_ALL);
                    world.setBlockState(base.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                    if(n==1) {
                        NativeSeats.put(world,base,new NativeSeats.Seat(-1,"minecraft:sticky_piston"));
                        NativeSeats.put(world,base.east(),new NativeSeats.Seat(-1,"minecraft:stone"));
                    }
                }
            });next(1);return false;
        }
        if(stage==1) {
            if(ticks-started<5)return false;
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(var base:BASES)world.setBlockState(base.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
            });next(2);return false;
        }
        if(stage==2) {
            if(ticks-started<20)return false;
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(int n=0;n<BASES.length;n++) {
                    var base=BASES[n];double offset=n==0?0:-.5;
                    check(world.getBlockState(base).get(PistonBlock.EXTENDED),"server piston extends");
                    check(world.getBlockState(base.east()).isOf(Blocks.PISTON_HEAD),"server native head settles");
                    check(world.getBlockState(base.east(2)).isOf(Blocks.STONE),"server pushes native stone");
                    check(NativeSeats.offset(world,base.east())==offset && NativeSeats.offset(world,base.east(2))==offset,
                            "server moving head and stone keep their physical seats");
                }
            });next(3);return false;
        }
        if(stage==3) {
            for(int n=0;n<BASES.length;n++) {
                var base=BASES[n];double offset=n==0?0:-.5;
                if(!client.world.getBlockState(base.east()).isOf(Blocks.PISTON_HEAD)
                        || !client.world.getBlockState(base.east(2)).isOf(Blocks.STONE)
                        || NativeSeats.offset(client.world,base.east(2))!=offset)return false;
                check(Math.abs(client.world.getBlockState(base.east(2)).getCollisionShape(client.world,base.east(2)).getBoundingBox().minY-offset)<1e-7,
                        "client sees native stone at the physical height");
            }
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(var base:BASES)world.setBlockState(base.north(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            });next(4);return false;
        }
        if(stage==4) {
            if(ticks-started<20)return false;
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(var base:BASES) {
                    check(!world.getBlockState(base).get(PistonBlock.EXTENDED),"server piston retracts");
                    check(world.getBlockState(base.east()).isOf(Blocks.STONE) && world.getBlockState(base.east(2)).isAir(),
                            "server sticky pull restores native stone without residue");
                }
            });next(5);return false;
        }
        if(stage==5) {
            for(int n=0;n<BASES.length;n++) {
                var base=BASES[n];double offset=n==0?0:-.5;
                if(!client.world.getBlockState(base.east()).isOf(Blocks.STONE)
                        || !client.world.getBlockState(base.east(2)).isAir()
                        || NativeSeats.offset(client.world,base.east())!=offset)return false;
            }
            System.out.println("PHYSICAL_NATIVE_PISTON_CYCLE_PASS lithium="+FabricLoader.getInstance().isModLoaded("lithium"));
            stage=99;return true;
        }
        return false;
    }
    private static void next(int value) {stage=value;started=ticks;}
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static void server(MinecraftClient client,Runnable action) {
        pending=true;
        client.getServer().execute(()-> {try {action.run();}catch(Throwable error) {failure=error;}finally {pending=false;}});
    }
}
