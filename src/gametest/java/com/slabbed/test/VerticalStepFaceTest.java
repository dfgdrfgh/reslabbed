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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void naturalCarpetMatchesLoweredSupportAndPlacedCarpetKeepsItsSeat(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos ground = h.getAbsolutePos(new BlockPos(2, 1, 2));
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(ground.getX() + 3.5, ground.getY(), ground.getZ() + .5);
        place(h, player, Items.STONE_SLAB, ground);
        place(h, player, Items.OAK_LOG, ground.up());
        BlockPos carpet = ground.up(3);
        world.setBlockState(carpet, Blocks.RED_CARPET.getDefaultState(), Block.NOTIFY_ALL);
        h.assertTrue(!SlabAnchorAttachment.isModernPlacement(world, carpet), "Natural carpet must have no placement fact");
        h.assertTrue(SlabSupport.getYOffset(world, carpet, world.getBlockState(carpet)) == -0.5,
                "Natural carpet floated above its lowered full-block support");
        var shape = world.getBlockState(carpet).getOutlineShape(world, carpet);
        h.assertTrue(Math.abs(shape.getMin(Direction.Axis.Y) + .5) < 1.0e-6
                        && Math.abs(shape.getMax(Direction.Axis.Y) + .4375) < 1.0e-6,
                "Natural carpet outline must use the same support plane");
        world.breakBlock(carpet, false);
        place(h, player, Items.RED_CARPET, ground.up(2));
        long before = SlabAnchorAttachment.rawPlacementDyFact(world, carpet).rawBits();
        world.breakBlock(ground.up(), false);
        h.assertTrue(world.getBlockState(carpet).isOf(Blocks.RED_CARPET)
                        && SlabAnchorAttachment.rawPlacementDyFact(world, carpet).rawBits() == before
                        && SlabSupport.getYOffset(world, carpet, world.getBlockState(carpet)) == -0.5,
                "A player carpet must retain its exact stored height after support edits");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void upwardSlabFullBlockSlabStackUsesBottomSlabAtStoredHeight(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos ground = h.getAbsolutePos(new BlockPos(2, 1, 2));
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(ground.getX() + 3.5, ground.getY(), ground.getZ() + .5);
        place(h, player, Items.STONE_SLAB, ground);
        place(h, player, Items.OAK_LOG, ground.up());
        place(h, player, Items.STONE_SLAB, ground.up(2));
        BlockPos slab = ground.up(3);
        var state = world.getBlockState(slab);
        h.assertTrue(state.isOf(Blocks.STONE_SLAB)
                        && state.get(net.minecraft.block.SlabBlock.TYPE) == net.minecraft.block.enums.SlabType.BOTTOM
                        && SlabSupport.getYOffset(world, slab, state) == -0.5,
                "An UP-face slab must start on the lowered top plane as a bottom slab");
        h.complete();
    }
    @GameTest(templateName = "slabbed:empty_16")
    public void supportReplacementDoesNotAdoptNaturalStructureIntoPlacedStack(TestContext h) {
        ServerWorld world = h.getWorld();
        net.minecraft.item.Item[] slabs = {Items.OAK_SLAB, Items.STONE_SLAB,
                TerrainSlabsTestShim.TEST_TS_SLAB.asItem()};
        for (int i = 0; i < slabs.length; i++) {
            BlockPos ground = h.getAbsolutePos(new BlockPos(2 + 4 * i, 1, 3));
            world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            for (int y = 1; y <= 4; y++) for (int x = -1; x <= 1; x++)
                world.setBlockState(ground.add(x, y, 0), Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
            world.breakBlock(ground.up(), false);
            world.breakBlock(ground.up(2), false);
            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            player.setPosition(ground.getX() + 3.5, ground.getY(), ground.getZ() + .5);
            place(h, player, slabs[i], ground);
            place(h, player, Items.COBBLED_DEEPSLATE, ground.up());
            BlockPos placed = ground.up(2), natural = ground.up(3);
            long seat = SlabAnchorAttachment.rawPlacementDyFact(world, placed).rawBits();
            for (int attempt = 0; attempt < 3; attempt++) {
                h.assertTrue(!SlabAnchorAttachment.isModernPlacement(world, natural)
                                && SlabSupport.getYOffset(world, natural, world.getBlockState(natural)) == 0,
                        "Natural trunk was pulled into the player's lowered stack");
                var naturalShape = world.getBlockState(natural).getOutlineShape(world, natural);
                var placedShape = world.getBlockState(placed).getOutlineShape(world, placed);
                h.assertTrue(naturalShape.getMin(Direction.Axis.Y) == 0
                                && placedShape.getMin(Direction.Axis.Y) == -.5
                                && placedShape.getMax(Direction.Axis.Y) == .5,
                        "Model seats and outline bodies must preserve the half-block opening");
                world.setBlockState(natural.east(), attempt % 2 == 0
                        ? Blocks.AIR.getDefaultState() : Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
                h.assertTrue(SlabAnchorAttachment.rawPlacementDyFact(world, placed).rawBits() == seat,
                        "Natural neighbor edits changed the placed block's stored seat");
            }
        }
        h.complete();
    }

}
