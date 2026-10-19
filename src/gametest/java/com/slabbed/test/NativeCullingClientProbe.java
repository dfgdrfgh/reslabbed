package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Exercise each installed occlusion backend after warming it with ordinary flush blocks. */
public final class NativeCullingClientProbe {
    private static final BlockPos NORMAL=new BlockPos(48,64,36),LOWERED=new BlockPos(48,64,40);
    private NativeCullingClientProbe() {}
    public static void setup(World world) {
        for(var pos:new BlockPos[]{NORMAL,LOWERED}) {
            world.setBlockState(pos,Blocks.PISTON.getDefaultState().with(PistonBlock.FACING,Direction.WEST),Block.NOTIFY_ALL);
            world.setBlockState(pos.north(),Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
        }
        NativeSeats.put(world,LOWERED,new NativeSeats.Seat(-1,"minecraft:piston"));
    }
    public static boolean ready(MinecraftClient client) {
        return client.world.getBlockState(NORMAL).isOf(Blocks.PISTON)
                && client.world.getBlockState(LOWERED).isOf(Blocks.PISTON)
                && NativeSeats.offset(client.world,LOWERED)==-.5;
    }
    public static void verify(MinecraftClient client) {
        check(!Block.shouldDrawSide(Blocks.GOLD_BLOCK.getDefaultState(),client.world,NORMAL.north(),Direction.SOUTH,NORMAL),"ordinary face is hidden");
        check(Block.shouldDrawSide(Blocks.GOLD_BLOCK.getDefaultState(),client.world,LOWERED.north(),Direction.SOUTH,LOWERED),"exposed face beside lowered piston is visible");
        try {
            if(FabricLoader.getInstance().isModLoaded("sodium")) {
                var type=Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache");
                var cache=type.getConstructor().newInstance();
                var method=type.getMethod("shouldDrawSide",BlockState.class,BlockView.class,BlockPos.class,Direction.class);
                check(!(boolean)method.invoke(cache,Blocks.GOLD_BLOCK.getDefaultState(),client.world,NORMAL.north(),Direction.SOUTH),"Sodium ordinary face is hidden");
                check((boolean)method.invoke(cache,Blocks.GOLD_BLOCK.getDefaultState(),client.world,LOWERED.north(),Direction.SOUTH),"Sodium exposed face remains visible");
            }
            if(FabricLoader.getInstance().isModLoaded("moreculling")) {
                var method=Class.forName("ca.fxco.moreculling.utils.CullingUtils").getMethod("shouldDrawSideCulling",BlockState.class,BlockView.class,BlockPos.class,Direction.class,BlockPos.class);
                check(!(boolean)method.invoke(null,Blocks.GOLD_BLOCK.getDefaultState(),client.world,NORMAL.north(),Direction.SOUTH,NORMAL),"MoreCulling ordinary face is hidden");
                check((boolean)method.invoke(null,Blocks.GOLD_BLOCK.getDefaultState(),client.world,LOWERED.north(),Direction.SOUTH,LOWERED),"MoreCulling exposed face remains visible");
            }
        } catch(ReflectiveOperationException error) {throw new AssertionError("cannot inspect installed culling backend",error);}
        System.out.println("PHYSICAL_NATIVE_FACE_CULLING_PASS sodium="+FabricLoader.getInstance().isModLoaded("sodium")
                +" moreculling="+FabricLoader.getInstance().isModLoaded("moreculling"));
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
