package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

/** Face visibility follows the gap between visible surfaces; seats remain unchanged (LAW.md). */
public final class VerticalStepFaceTest {
    private static void place(TestContext h, PlayerEntity player, net.minecraft.item.Item item, BlockPos owner) {
        h.assertTrue(PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(item), owner, Direction.UP).isAccepted(),
                "Native item placement failed: " + item);
    }

    private static int mask(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return SlabSupport.slabHeightStepFaceMask(world, pos, state, SlabSupport.getYOffset(world, pos, state));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void rebuiltStackKeepsTheExposedTopAndBottomFaces(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos ground = h.getAbsolutePos(new BlockPos(2, 1, 2));
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(ground.getX() + 3.5, ground.getY(), ground.getZ() + 0.5);
        for (int i = 0; i < 3; i++) place(h, player, Items.COBBLED_DEEPSLATE, ground.up(i));
        BlockPos floating = ground.up(3);
        long original = SlabAnchorAttachment.rawPlacementDyFact(world, floating).rawBits();
        world.breakBlock(ground.up(), false);
        world.breakBlock(ground.up(2), false);
        place(h, player, Items.STONE_SLAB, ground);
        place(h, player, Items.COBBLED_DEEPSLATE, ground.up());
        BlockPos lower = ground.up(2);
        h.assertTrue(SlabSupport.getYOffset(world, lower, world.getBlockState(lower)) == -0.5,
                "Rebuilt lower block must sit on its slab");
        h.assertTrue(SlabAnchorAttachment.rawPlacementDyFact(world, floating).rawBits() == original
                && SlabSupport.getYOffset(world, floating, world.getBlockState(floating)) == 0.0,
                "Support replacement moved the floating block");
        h.assertTrue((mask(world, lower) & (1 << Direction.UP.ordinal())) != 0,
                "Exposed lower top face was still culled");
        h.assertTrue((mask(world, floating) & (1 << Direction.DOWN.ordinal())) != 0,
                "Exposed floating bottom face was still culled");
        h.assertTrue((mask(world, lower) & (1 << Direction.DOWN.ordinal())) == 0,
                "The touching slab support must keep ordinary culling");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void equalHeightStackKeepsOrdinaryVerticalCulling(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos ground = h.getAbsolutePos(new BlockPos(2, 1, 2));
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(ground.getX() + 3.5, ground.getY(), ground.getZ() + 0.5);
        place(h, player, Items.STONE_SLAB, ground);
        place(h, player, Items.STONE, ground.up());
        place(h, player, Items.STONE, ground.up(2));
        BlockPos lower = ground.up(2), upper = lower.up();
        h.assertTrue((mask(world, lower) & (1 << Direction.UP.ordinal())) == 0
                && (mask(world, upper) & (1 << Direction.DOWN.ordinal())) == 0,
                "Touching shifted blocks must retain their shared-face culling");
        h.complete();
    }
}
