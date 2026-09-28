package com.slabbed.mixin;

import com.slabbed.util.RailVisualSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A neighbour change delivered to the cell a lowered powered rail is DRAWN in is delivered to the
 * rail as well (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla only notifies the six neighbours of a changed cell (torches and wire reach one cell
 * further on their own). A lever or a repeater beside the slab notifies the slab, not the rail one
 * cell up, so the rail would keep its old state until something else touched it. The slab's
 * notification is forwarded to the rail drawn inside it, with the same source position, so the rail
 * re-evaluates exactly when a vanilla rail in that row would.
 *
 * <p>INVARIANT: never forward a change the rail itself caused (its own state update notifies its
 * support), or the two would notify each other forever. Server side only: the client does not run
 * redstone, and {@code Level.neighborChanged} is a no-op there (the server level routes it through
 * its neighbour updater).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class LoweredRailSupportNeighborMixin {

    @Inject(method = "neighborChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("HEAD"))
    private void slabbed$forwardToTheRailDrawnHere(Level level, BlockPos pos, Block block,
                                                   BlockPos fromPos, boolean movedByPiston,
                                                   CallbackInfo ci) {
        if (level == null || level.isClientSide() || block instanceof PoweredRailBlock) {
            return;
        }
        if (RailVisualSignal.carriesADrawnPoweredRail(level, pos)) {
            level.neighborChanged(pos.above(), block, fromPos);
        }
    }
}
