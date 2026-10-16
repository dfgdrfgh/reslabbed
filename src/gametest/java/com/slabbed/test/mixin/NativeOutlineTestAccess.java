package com.slabbed.test.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-only access to the native block outline, before the state-level Slabbed translation. */
@Mixin(AbstractBlock.class)
public interface NativeOutlineTestAccess {
    @Invoker("getOutlineShape")
    VoxelShape slabbed$nativeOutline(BlockState state, BlockView view, BlockPos pos, ShapeContext context);
}
