package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.WireVisualSignal;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RedstoneController;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A redstone wire beside a lowered half-step reads the wire drawn on the slab next to it
 * (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla's incoming-signal read has three neighbour reads per direction: the neighbour cell, the
 * cell above it when the neighbour is a conductor, or the cell below it when it is not. The third
 * read is wrapped: when the non-conducting neighbour carries a wire drawn inside it, that wire is
 * read as well. Both wire evaluators (default and experimental) share this method, and the wrapped
 * call dispatches to the running evaluator's own signal read, so the experimental evaluator's
 * in-flight power values are honoured. On this line the shared read is
 * {@code RedstoneController.calculateWirePowerAt} and the per-cell read is
 * {@code getWirePowerAt}, which {@code ExperimentalRedstoneController} overrides.
 *
 * <p>INVARIANT: vanilla's answer is kept and only raised by the drawn read; a wire drawn in its own
 * cell is unaffected. See {@link WireVisualSignal}.
 */
@Mixin(RedstoneController.class)
public abstract class RedstoneWireEvaluatorVisualStepMixin {

    @WrapOperation(
            method = "calculateWirePowerAt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/RedstoneController;getWirePowerAt"
                            + "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)I",
                    ordinal = 2))
    private int slabbed$alsoReadTheWireDrawnBeside(RedstoneController controller, BlockPos belowNeighbor,
                                                   BlockState belowState, Operation<Integer> original,
                                                   @Local(argsOnly = true) World world,
                                                   @Local(argsOnly = true) BlockPos pos) {
        int vanilla = original.call(controller, belowNeighbor, belowState);
        BlockPos drawn = WireVisualSignal.wireDrawnIn(world, pos, belowNeighbor.up());
        if (drawn == null) {
            return vanilla;
        }
        return Math.max(vanilla, original.call(controller, drawn, world.getBlockState(drawn)));
    }
}
