package com.slabbed.test.mixin;

import com.slabbed.test.TerrainSlabsVegetationTest;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only reproduction of Terrain Slabs' native vegetation offset; absent from release jars. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class TerrainSlabsVegetationTestMixin {
    @Inject(method = "getModelOffset", at = @At("RETURN"), cancellable = true)
    private void slabbed$nativeTerrainOffset(BlockView view, BlockPos pos,
                                              CallbackInfoReturnable<Vec3d> cir) {
        if (!TerrainSlabsVegetationTest.nativeOffsetsEnabled || view instanceof ServerWorld
                || !(((BlockState) (Object) this).getBlock() instanceof PlantBlock)) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        BlockPos below = pos.down();
        if (state.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
                && state.get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
                == net.minecraft.block.enums.DoubleBlockHalf.UPPER) {
            below = below.down();
        }
        BlockState support = view.getBlockState(below);
        if (support.contains(SlabBlock.TYPE) && support.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
            Vec3d offset = cir.getReturnValue();
            cir.setReturnValue(new Vec3d(offset.x, -0.5d, offset.z));
        }
    }
}
