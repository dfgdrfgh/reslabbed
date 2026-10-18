package com.slabbed.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WallBlock.class)
public interface NativeWallAccess {
    @Invoker("getStateWith") BlockState slabbed$getStateWith(WorldView world,BlockState state,BlockPos pos,
            BlockState aboveState,boolean north,boolean east,boolean south,boolean west);
}
