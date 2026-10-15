package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Creation routes decide their cells and seat before mutating the world (LAW.md). */
public final class PlacementAdapters {
    private PlacementAdapters() {}

    public static PlacementPlan plan(World world, LandingResolver.PlacementAim aim,
                                     ItemPlacementContext context, BlockState state) {
        if (state == null || state.isAir()) return null;
        if (LandingResolver.compatOwnsFinalState(state)) return new PlacementPlan(List.of());
        BlockPos target = context.getBlockPos().toImmutable();
        var resolution = LandingResolver.resolve(world, aim, target, state, LandingResolver.classify(state));
        double dy = resolution == null ? 0.0d : resolution.landingDy();
        int grid = SlabAnchorAttachment.quantiseDy(dy);
        if (grid == SlabAnchorAttachment.UNREPRESENTABLE || dy * 16.0d != grid) return null;
        var cells = new ArrayList<PlacementPlan.Cell>();
        cells.add(new PlacementPlan.Cell(target, state, grid));
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            boolean lower = state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
            cells.add(new PlacementPlan.Cell(lower ? target.up() : target.down(),
                    state.with(Properties.DOUBLE_BLOCK_HALF,
                            lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER), grid));
        } else if (state.getBlock() instanceof BedBlock && state.contains(Properties.BED_PART)) {
            cells.add(new PlacementPlan.Cell(target.offset(BedBlock.getOppositePartDirection(state)),
                    state.with(Properties.BED_PART,
                            state.get(Properties.BED_PART) == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT), grid));
        }
        return new PlacementPlan(cells);
    }
}
