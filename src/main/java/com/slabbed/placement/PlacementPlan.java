package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact positions and final cell ownership for one placement operation (LAW.md). */
public record PlacementPlan(List<Cell> cells) {
    public PlacementPlan {
        cells = List.copyOf(cells);
        var positions = new HashSet<BlockPos>();
        for (Cell cell : cells) {
            if (!positions.add(cell.pos())) throw new IllegalArgumentException("duplicate placement cell");
        }
    }

    public record Cell(BlockPos pos, BlockState finalState, int heightSixteenths) {
        public Cell {
            pos = Objects.requireNonNull(pos, "pos").toImmutable();
            Objects.requireNonNull(finalState, "finalState");
            if (finalState.isAir() || heightSixteenths == SlabAnchorAttachment.UNREPRESENTABLE) {
                throw new IllegalArgumentException("invalid placement cell");
            }
        }
    }

    public Map<BlockPos, Long> rawHeights() {
        Map<BlockPos, Long> result = new LinkedHashMap<>();
        for (Cell cell : cells) result.put(cell.pos(),
                Double.doubleToRawLongBits(SlabAnchorAttachment.dequantiseDy(cell.heightSixteenths())));
        return Map.copyOf(result);
    }
}
