package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Real server piston events and client settlement against a cube in the lower adjacent cell. */
public final class NativeOffsetPistonClientProbe {
    private static final BlockPos MOVABLE=new BlockPos(68,64,24),BLOCKED=new BlockPos(68,64,28);
    private static int stage,ticks,started;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private NativeOffsetPistonClientProbe() {}
    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        if(failure!=null)throw new AssertionError("offset piston fixture failed",failure);
        check(++ticks<200,"offset piston timeout stage="+stage);
        if(pending)return false;
        if(stage==0) {
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(var base:new BlockPos[]{MOVABLE,BLOCKED}) {
                    for(int x=-1;x<=3;x++)for(int y=-2;y<=2;y++)for(int z=-1;z<=1;z++)
                        world.setBlockState(base.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    world.setBlockState(base.down(),Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
                    world.setBlockState(base,Blocks.PISTON.getDefaultState().with(PistonBlock.FACING,Direction.EAST),Block.NOTIFY_ALL);
                    NativeSeats.put(world,base,new NativeSeats.Seat(-1,"minecraft:piston"));
                    world.setBlockState(base.east().down(),(base.equals(MOVABLE)?Blocks.GRASS_BLOCK:Blocks.OBSIDIAN).getDefaultState(),Block.NOTIFY_ALL);
                }
            });stage=1;return false;
        }
        if(stage==1) {
            if(NativeSeats.offset(client.world,MOVABLE)!=-.5 || NativeSeats.offset(client.world,BLOCKED)!=-.5)return false;
            server(client,()-> {
                var world=client.getServer().getOverworld();
                world.setBlockState(MOVABLE.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(BLOCKED.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
            });started=ticks;stage=2;return false;
        }
        if(stage==2) {
            if(ticks-started<20)return false;
            server(client,()->verify(client.getServer().getOverworld()));stage=3;return false;
        }
        if(stage==3) {
            verify(client.world);
            System.out.println("PHYSICAL_OFFSET_PISTON_CONTACT_PASS lithium="+FabricLoader.getInstance().isModLoaded("lithium"));
            stage=99;return true;
        }
        return false;
    }
    private static void verify(net.minecraft.world.World world) {
        check(world.getBlockState(MOVABLE).get(PistonBlock.EXTENDED),"piston pushes physically contacted grass");
        check(world.getBlockState(MOVABLE.east().down()).isAir(),"head cannot pass through the lower grass owner");
        check(world.getBlockState(MOVABLE.east(2).down()).isOf(Blocks.GRASS_BLOCK),"whole grass block moves to its physical destination");
        check(NativeSeats.offset(world,MOVABLE.east(2).down())==0,"moved grass keeps its independent height");
        check(world.getBlockState(MOVABLE.east()).isOf(Blocks.PISTON_HEAD)
                && NativeSeats.offset(world,MOVABLE.east())==-.5,"native piston head keeps its slab height");
        check(!world.getBlockState(BLOCKED).get(PistonBlock.EXTENDED),"lower obsidian blocks the physical head");
        check(world.getBlockState(BLOCKED.east().down()).isOf(Blocks.OBSIDIAN),"obstruction is untouched");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void server(MinecraftClient client,Runnable action) {
        pending=true;client.getServer().execute(()-> {
            try {action.run();}catch(Throwable error) {failure=error;}finally {pending=false;}
        });
    }
}
