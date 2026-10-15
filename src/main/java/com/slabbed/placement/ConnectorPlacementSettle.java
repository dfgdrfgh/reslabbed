package com.slabbed.placement;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Collection;


public final class ConnectorPlacementSettle {

    /**
     * {@code NOTIFY_LISTENERS} so the corrected arms reach players; {@code FORCE_STATE} so the write
     * does not launch a second shape pass, and no {@code NOTIFY_NEIGHBORS} so it raises no
     * neighbour-notification cascade. This pass already visits both ends of every pair it can affect
     * (the placed cell and its four horizontal neighbours), so no cascade is needed to be complete.
     */
    private static final int SETTLE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private ConnectorPlacementSettle() {
    }

    /**
     * Re-derives connector arms at each just-published cell and at its four horizontal neighbours.
     * Server-side only: the client receives the settled state as an ordinary block update, so it
     * never needs (and must not run) a second, independent decision.
     */
    public static void settlePublishedPlacements(World world, Collection<BlockPos> publishedPositions) {
        if (world == null || world.isClient() || publishedPositions == null || publishedPositions.isEmpty()) {
            return;
        }
        for (BlockPos published : publishedPositions) {
            if (published == null) {
                continue;
            }
            settleCell(world, published);
            for (Direction direction : Direction.Type.HORIZONTAL) {
                settleCell(world, published.offset(direction));
            }
        }
    }

    private static void settleCell(World world, BlockPos pos) {
        BlockState current = world.getBlockState(pos);
        if (!SlabSupport.isBeta35FenceWallVariantContactObject(current)) {
            return;
        }
        BlockState settled = Block.postProcessState(current, world, pos);
        if (settled == current || settled.getBlock() != current.getBlock()) {
            // Identical state: nothing to do. Different BLOCK (or air): vanilla judged the cell
            // unsurvivable, which is a removal decision this pass has no business making — a
            // placement-time arm refresh must never take a block away.
            return;
        }
        world.setBlockState(pos, settled, SETTLE_FLAGS);
    }
}
