package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.WireVisualSignal;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RedstoneController;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A wire beside a lowered half-step also reads the wire drawn on the slab next to it, in the wire's
 * incoming-signal read (maintainer ruling, 2026-09-27). From 1.21.2 wire power lives on the redstone
 * controller: the read wrapped here is the third neighbour read in the per-direction loop, the one
 * that looks at the cell diagonally below the neighbour.
 */
@Mixin(RedstoneController.class)
public abstract class RedstoneWireVisualStepMixin {
    @WrapOperation(
            method = "calculateWirePowerAt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/RedstoneController;getWirePowerAt(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)I",
                    ordinal = 2))
    private int slabbed$alsoReadTheWireDrawnBeside(RedstoneController controller, BlockPos belowPos,
                                                   BlockState belowState, Operation<Integer> original,
                                                   @Local(argsOnly = true) World world,
                                                   @Local(argsOnly = true) BlockPos pos,
                                                   @Local Direction direction) {
        int vanilla = original.call(controller, belowPos, belowState);
        BlockPos drawn = WireVisualSignal.wireDrawnIn(world, pos, pos.offset(direction));
        if (drawn == null) {
            return vanilla;
        }
        return Math.max(vanilla, original.call(controller, drawn, world.getBlockState(drawn)));
    }
}
