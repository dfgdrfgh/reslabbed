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

/** Authored vegetation uses its saved seat; unowned vegetation keeps native geometry (LAW.md). */
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
        // Suppress the competing vertical seat only when Slabbed actually owns this placement.
        // Absence adds no Slabbed translation and must preserve the compat mod's native offset.
        if (offset.y == -0.5d && com.slabbed.anchor.PlacementGeometry.fact(world, pos).present()) {
            return new Vec3d(offset.x, 0.0d, offset.z);
        }
        return offset;
    }
}
