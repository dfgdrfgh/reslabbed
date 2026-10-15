package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Operation-local staging is published only after the complete placement validates (LAW.md). */
public final class PlacementTransaction {
    private static final ThreadLocal<ArrayDeque<Prepared>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);
    private PlacementTransaction() {}

    public static Prepared prepare(World world, PlacementPlan plan) {
        return new Prepared(world, plan);
    }

    public static SlabAnchorAttachment.PlacementDyFact stagedFact(BlockView view, BlockPos pos) {
        for (Prepared prepared : ACTIVE.get()) {
            if (prepared.world == view) {
                Long bits = prepared.heights.get(pos);
                if (bits != null) return new SlabAnchorAttachment.PlacementDyFact(true, bits);
            }
        }
        return null;
    }

    public static final class Prepared {
        private final World world;
        private final PlacementPlan plan;
        private final Map<BlockPos, Long> heights;
        private final Map<BlockPos, BlockState> beforeStates = new LinkedHashMap<>();
        private final Map<BlockPos, Long> beforeHeights = new LinkedHashMap<>();
        private boolean accepted;
        private boolean committed;
        private boolean closed;

        private Prepared(World world, PlacementPlan plan) {
            this.world = world;
            this.plan = plan;
            heights = plan == null ? Map.of() : plan.rawHeights();
            if (world == null || plan == null) return;
            Set<BlockPos> replaced = plan.cells().stream().map(PlacementPlan.Cell::pos).collect(Collectors.toSet());
            boolean primary = true;
            for (var cell : plan.cells()) {
                var chunk = SlabAnchorAttachment.loadedAttachmentChunk(world, cell.pos());
                if (chunk == null || world.isOutOfHeightLimit(cell.pos())) return;
                // Vanilla's initial check can precede item state components changing a pair's facing.
                // Every secondary cell in the final plan must still be a replaceable destination.
                if (!primary && !chunk.getBlockState(cell.pos()).isReplaceable()) return;
                primary = false;
                beforeStates.put(cell.pos(), chunk.getBlockState(cell.pos()));
                var fact = SlabAnchorAttachment.rawPlacementDyFact(world, cell.pos());
                if (fact.present()) beforeHeights.put(cell.pos(), fact.rawBits());
                if (PlacementOccupancy.intersectsDisplacedBody(world, cell.pos(), cell.finalState(),
                        SlabAnchorAttachment.dequantiseDy(cell.heightSixteenths()), replaced)) return;
            }
            if (SlabAnchorAttachment.preparePlacementDyBatch(world, heights) == null) return;
            accepted = true;
            ACTIVE.get().push(this);
        }

        public boolean accepted() { return accepted && !closed; }

        public boolean commit() {
            if (!accepted() || committed) return false;
            for (var cell : plan.cells()) {
                var chunk = SlabAnchorAttachment.loadedAttachmentChunk(world, cell.pos());
                if (chunk == null) return false;
                BlockState actual = chunk.getBlockState(cell.pos());
                // Vanilla predicts the bed foot locally; its server-created head arrives later.
                if (world.isClient() && actual.isAir() && beforeStates.get(cell.pos()).isAir()
                        && cell.finalState().getBlock() instanceof net.minecraft.block.BedBlock
                        && cell.finalState().get(net.minecraft.state.property.Properties.BED_PART)
                        == net.minecraft.block.enums.BedPart.HEAD) continue;
                if (!actual.isOf(cell.finalState().getBlock())) return false;
                if (actual.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
                        && actual.get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
                        != cell.finalState().get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)) return false;
                if (actual.contains(net.minecraft.state.property.Properties.BED_PART)
                        && (actual.get(net.minecraft.state.property.Properties.BED_PART)
                        != cell.finalState().get(net.minecraft.state.property.Properties.BED_PART)
                        || actual.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING)
                        != cell.finalState().get(net.minecraft.state.property.Properties.HORIZONTAL_FACING))) return false;
                var replaced = heights.keySet();
                if (PlacementOccupancy.intersectsDisplacedBody(world, cell.pos(), actual,
                        SlabAnchorAttachment.dequantiseDy(cell.heightSixteenths()), replaced)) return false;
            }
            if (world.isClient()) {
                committed = true;
                return true;
            }
            var publication = SlabAnchorAttachment.preparePlacementDyBatch(world, heights);
            committed = publication != null && publication.commit();
            return committed;
        }

        public void finish() { close(); }

        public void abort() {
            if (closed) return;
            close();
            if (!accepted || plan == null) return;
            var restore = new LinkedHashMap<BlockPos, Long>();
            for (var cell : plan.cells()) {
                var chunk = SlabAnchorAttachment.loadedAttachmentChunk(world, cell.pos());
                if (chunk == null) continue;
                BlockState current = chunk.getBlockState(cell.pos());
                BlockState before = beforeStates.get(cell.pos());
                if (before != null && current.isOf(cell.finalState().getBlock()) && current != before) {
                    world.setBlockState(cell.pos(), before, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                    if (!world.isClient()) SlabAnchorAttachment.removeAnchor(world, cell.pos());
                    Long old = beforeHeights.get(cell.pos());
                    if (old != null) restore.put(cell.pos(), old);
                }
            }
            if (!world.isClient() && !restore.isEmpty()) SlabAnchorAttachment.writePlacementDyBatch(world, restore);
        }

        private void close() {
            if (closed) return;
            closed = true;
            var active = ACTIVE.get();
            active.remove(this);
            if (active.isEmpty()) ACTIVE.remove();
        }
    }
}
