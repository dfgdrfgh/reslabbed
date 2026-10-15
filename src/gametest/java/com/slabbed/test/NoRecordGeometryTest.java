package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Cells without authored placement facts keep their native geometry (LAW.md). */
public final class NoRecordGeometryTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void noRecordBlockStaysAtGridAfterSupportEdits(TestContext ctx) {
        verifySupportEdit(ctx, Blocks.STONE.getDefaultState(), false);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void noRecordGlassAboveReplacementStaysPut(TestContext ctx) {
        verifySupportEdit(ctx, Blocks.GLASS.getDefaultState(), false);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void oldNoRecordColumnIsNotAdoptedAtChunkEdge(TestContext ctx) {
        verifySupportEdit(ctx, Blocks.STONE.getDefaultState(), true);
    }

    private static void verifySupportEdit(TestContext ctx, BlockState existing, boolean chunkEdge) {
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(3, 4, 3));
        if (chunkEdge) pos = new BlockPos((pos.getX() & ~15) + 15, pos.getY(), pos.getZ());
        BlockPos support = pos.down();
        world.setBlockState(pos, existing, Block.NOTIFY_ALL);
        world.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        ctx.assertTrue(!SlabAnchorAttachment.rawPlacementDyFact(world, pos).present(),
                "premise: command-created block unexpectedly has a saved position");
        ctx.assertTrue(SlabSupport.getYOffset(world, pos, existing) == 0.0d,
                "premise: unsupported no-record block must start at grid height");

        BlockState slab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlockState(support, slab, Block.NOTIFY_ALL);
        ctx.assertTrue(SlabSupport.getYOffset(world, pos, existing) == 0.0d,
                "NO_RECORD_MOVED: adding a slab moved an existing " + existing + " at " + pos);
        ctx.assertTrue(world.getBlockState(pos).equals(existing)
                        && !SlabAnchorAttachment.rawPlacementDyFact(world, pos).present(),
                "support edit changed or adopted the untouched overhead block");

        world.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        ctx.assertTrue(SlabSupport.getYOffset(world, pos, existing) == 0.0d,
                "support removal moved the untouched overhead block");
        ctx.complete();
    }
}
