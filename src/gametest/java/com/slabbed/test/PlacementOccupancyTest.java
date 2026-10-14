package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.Map;

/** Placement must respect existing visible bodies without moving their seats (LAW.md). */
public final class PlacementOccupancyTest {
    private static long height(ServerWorld world, BlockPos pos) {
        return Double.doubleToRawLongBits(SlabSupport.getYOffset(world, pos, world.getBlockState(pos)));
    }

    private static PlayerEntity player(TestContext h, BlockPos ground) {
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(ground.getX() + 3.5, ground.getY(), ground.getZ() + 0.5);
        return player;
    }

    private static ActionResult place(PlayerEntity player, ItemStack stack, BlockPos owner) {
        return PlacementCaptureBoundaryGameTest.useOn(player, stack, owner, Direction.UP);
    }

    private static void setup(TestContext h, ServerWorld world, PlayerEntity player, BlockPos ground, Item slab) {
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        h.assertTrue(place(player, new ItemStack(slab), ground).isAccepted(), "Support slab did not place");
        h.assertTrue(place(player, new ItemStack(Items.DIRT), ground.up()).isAccepted(), "Upper block did not place");
        h.assertTrue(height(world, ground.up(2)) == Double.doubleToRawLongBits(-0.5), "Upper block must sit at slab height");
    }

    @GameTest(templateName = "slabbed:empty_16")
    public void removedSlabCellRejectsFullBlocksWithoutMovingUpperBlock(TestContext h) {
        ServerWorld world = h.getWorld();
        Item[] slabs = {Items.STONE_SLAB, Items.OAK_SLAB, TerrainSlabsTestShim.TEST_TS_SLAB.asItem()};
        Item[] fills = {Items.STONE, Items.GLASS, Items.FURNACE};
        for (int i = 0; i < slabs.length; i++) {
            BlockPos ground = h.getAbsolutePos(new BlockPos(2 + i * 4, 1, 2));
            PlayerEntity player = player(h, ground);
            setup(h, world, player, ground, slabs[i]);
            BlockPos upper = ground.up(2);
            long before = height(world, upper);
            h.assertTrue(world.breakBlock(ground.up(), false), "Support slab did not break");
            h.assertTrue(height(world, upper) == before, "Removing the support moved the upper block");
            for (Item fill : fills) {
                ItemStack stack = new ItemStack(fill, 2);
                ActionResult result = place(player, stack, ground);
                h.assertTrue(!result.isAccepted() && world.getBlockState(ground.up()).isAir()
                        && stack.getCount() == 2, "A full block occupied the lowered block's body: " + fill);
                h.assertTrue(world.getBlockState(upper).isOf(Blocks.DIRT) && height(world, upper) == before,
                        "Rejected placement changed the existing upper block");
            }
            h.assertTrue(place(player, new ItemStack(Items.STONE_SLAB), ground).isAccepted(),
                    "A bottom slab must still fit the empty lower half");
            h.assertTrue(height(world, upper) == before, "Restoring the bottom slab moved the upper block");
        }
        h.complete();
    }

    @GameTest(templateName = "slabbed:empty_16")
    public void slabUpgradeCannotGrowIntoLoweredUpperBlock(TestContext h) {
        ServerWorld world = h.getWorld();
        Item[] slabs = {Items.STONE_SLAB, Items.OAK_SLAB, TerrainSlabsTestShim.TEST_TS_SLAB.asItem()};
        for (int i = 0; i < slabs.length; i++) {
            BlockPos ground = h.getAbsolutePos(new BlockPos(2 + i * 4, 1, 2));
            PlayerEntity player = player(h, ground);
            setup(h, world, player, ground, slabs[i]);
            BlockState support = world.getBlockState(ground.up());
            long before = height(world, ground.up(2));
            ItemStack stack = new ItemStack(slabs[i], 2);
            h.assertTrue(!place(player, stack, ground.up()).isAccepted()
                    && world.getBlockState(ground.up()).equals(support) && stack.getCount() == 2,
                    "Second slab grew into the upper block");
            h.assertTrue(height(world, ground.up(2)) == before, "Rejected upgrade moved the upper block");
        }
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void replacementOfGeneratedTerrainSlabCannotGrowIntoTerrainAbove(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos ground = h.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockPos slab = ground.up();
        BlockPos terrain = slab.up();
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(slab, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM).with(TerrainSlabsTestShim.GENERATED, true), Block.NOTIFY_ALL);
        world.setBlockState(terrain, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = player(h, ground);
        world.breakBlock(slab, false);
        Item item = TerrainSlabsTestShim.TEST_TS_SLAB.asItem();
        h.assertTrue(place(player, new ItemStack(item), ground).isAccepted(), "Replacement bottom slab did not fit");
        h.assertTrue(!world.getBlockState(slab).get(TerrainSlabsTestShim.GENERATED), "Replacement must be player placed");
        long before = height(world, terrain);
        h.assertTrue(before == Double.doubleToRawLongBits(-0.5), "Legacy terrain must exercise the lowered support lane");
        ItemStack stack = new ItemStack(item, 2);
        h.assertTrue(!place(player, stack, slab).isAccepted() && stack.getCount() == 2
                && world.getBlockState(slab).get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "Replacement slab grew into the natural block above it");
        h.assertTrue(height(world, terrain) == before, "Rejected replacement changed the terrain height");
        h.complete();
    }

    @GameTest(templateName = "slabbed:empty_16")
    public void placementFindsUpperOwnerAtMaximumStoredDepth(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = h.getAbsolutePos(new BlockPos(2, 6, 2));
        world.setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(owner, Double.doubleToRawLongBits(-3.0)));
        PlayerEntity player = player(h, owner);
        h.assertTrue(place(player, new ItemStack(Items.DIRT), owner).isAccepted(), "Deep upper block did not place");
        BlockPos upper = owner.up();
        h.assertTrue(height(world, upper) == Double.doubleToRawLongBits(-3.0), "Deep placement must retain its native landing");
        // The existing owner's body is three cells below its grid cell. A new item must find it.
        BlockPos ground = upper.down(4);
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        player.setPosition(ground.getX() + 3.5, ground.getY(), ground.getZ() + 0.5);
        ItemStack stack = new ItemStack(Items.STONE, 2);
        long before = height(world, upper);
        h.assertTrue(!place(player, stack, ground).isAccepted() && stack.getCount() == 2
                && world.getBlockState(ground.up()).isAir(), "New block missed the deeply lowered owner");
        h.assertTrue(height(world, upper) == before, "Rejected deep placement moved the upper block");
        h.complete();
    }
}
