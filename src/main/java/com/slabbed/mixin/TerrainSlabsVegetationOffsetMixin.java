package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.compat.CompatHooks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

/** Vegetation has one vertical owner: Slabbed's geometry and stored placement seat (LAW.md). */
@Mixin(value = AbstractBlock.AbstractBlockState.class, priority = 500)
public abstract class TerrainSlabsVegetationOffsetMixin {
    @WrapMethod(method = "getModelOffset")
    private Vec3d slabbed$singleVegetationOffset(BlockView world, BlockPos pos,
                                                Operation<Vec3d> original) {
        Vec3d offset = original.call(world, pos);
        if (!CompatHooks.isTerrainSlabsLoaded()
                || !(((BlockState) (Object) this).getBlock() instanceof PlantBlock)) {
            return offset;
        }
        // Terrain Slabs inserts -0.5 into the native model/outline offset. Slabbed already
        // applies the plant's total seat, so retain native horizontal jitter without adding
        // that second vertical translation. No support or attachment lookup belongs here.
        if (offset.y == -0.5d) {
            return new Vec3d(offset.x, 0.0d, offset.z);
        }
        return offset;
    }
}
