package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.WireVisualSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A redstone wire beside a lowered half-step reads the wire drawn on the slab next to it
 * (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla's incoming-signal read has three neighbour reads per direction: the neighbour cell, the
 * cell above it when the neighbour is a conductor, or the cell below it when it is not. The third
 * read is wrapped: when the non-conducting neighbour carries a wire drawn inside it, that wire is
 * read as well, through the same signal read vanilla uses for the cell below.
 *
 * <p>The wrapped read takes only a block state, so the neighbour cell is rebuilt from the loop's
 * direction (the method's only direction local) and the reader position; no block-position local
 * is matched by ordinal.
 *
 * <p>INVARIANT: vanilla's answer is kept and only raised by the drawn read; a wire drawn in its own
 * cell is unaffected. See {@link WireVisualSignal}.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedstoneWireBlockVisualStepMixin {

    @WrapOperation(
            method = "calculateTargetStrength(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;getWireSignal"
                            + "(Lnet/minecraft/world/level/block/state/BlockState;)I",
                    ordinal = 2))
    private int slabbed$alsoReadTheWireDrawnBeside(RedStoneWireBlock wire, BlockState belowState,
                                                   Operation<Integer> original,
                                                   @Local Direction direction,
                                                   @Local(argsOnly = true) Level level,
                                                   @Local(argsOnly = true) BlockPos pos) {
        int vanilla = original.call(wire, belowState);
        BlockPos drawn = WireVisualSignal.wireDrawnIn(level, pos, pos.relative(direction));
        if (drawn == null) {
            return vanilla;
        }
        return Math.max(vanilla, original.call(wire, level.getBlockState(drawn)));
    }
}
