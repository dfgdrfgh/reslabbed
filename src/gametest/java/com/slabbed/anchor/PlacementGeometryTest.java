package com.slabbed.anchor;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** The presence of a saved height is its authority, including zero (LAW.md). */
public final class PlacementGeometryTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void explicitDeepSeatNeedsNoSecondMarker(TestContext ctx) {
        verifyExactSeat(ctx, -8.5d);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void explicitSixteenthSeatNeedsNoSecondMarker(TestContext ctx) {
        verifyExactSeat(ctx, -0.4375d);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void explicitZeroRemainsDistinctFromAbsence(TestContext ctx) {
        verifyExactSeat(ctx, 0.0d);
    }

    private static void verifyExactSeat(TestContext ctx, double expected) {
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(3, 4, 3));
        BlockState state = Blocks.STONE.getDefaultState();
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        SlabAnchorAttachment.writePlacementDyBatch(world,
                Map.of(pos, Double.doubleToRawLongBits(expected)));
        ctx.assertTrue(!SlabAnchorAttachment.isModernPlacement(world, pos),
                "premise: an independently saved fact must not require a second marker");
        var before = world.getWorldChunk(pos).getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        for (int i = 0; i < 100; i++) {
            double actual = PlacementGeometry.offsetY(world, pos, state);
            ctx.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
                    "SAVED_POSITION_IGNORED: stored=" + expected + " read=" + actual);
        }
        ctx.assertTrue(SlabAnchorAttachment.rawPlacementDyFact(world, pos).present(),
                "zero and nonzero saved facts must remain present");
        ctx.assertTrue(before == world.getWorldChunk(pos).getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE),
                "geometry reads rewrote the immutable height snapshot");
        ctx.complete();
    }
}
