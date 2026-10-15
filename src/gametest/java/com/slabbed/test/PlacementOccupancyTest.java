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
    public void replacementOfGeneratedTerrainSlabKeepsNaturalBlockAtGridHeight(TestContext h) {
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
        h.assertTrue(before == Double.doubleToRawLongBits(0.0), "Replacing terrain support must not lower the natural block");
        ItemStack stack = new ItemStack(item, 2);
        h.assertTrue(place(player, stack, slab).isAccepted() && stack.getCount() == 1
                && world.getBlockState(slab).get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "The unshifted double slab must fit below unshifted natural terrain");
        h.assertTrue(height(world, terrain) == before, "Slab upgrade changed the terrain height");
        h.complete();
    }

    @GameTest(templateName = "slabbed:empty_16")
    public void placementFindsUpperOwnerAtMaximumStoredDepth(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = h.getAbsolutePos(new BlockPos(2, 6, 2));
        world.setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(owner, Double.doubleToRawLongBits(-3.0)));
        SlabAnchorAttachment.markPostPolicyPlacements(world, java.util.List.of(owner));
        PlayerEntity player = player(h, owner);
        h.assertTrue(place(player, new ItemStack(Items.DIRT), owner).isAccepted(), "Deep upper block did not place");
        BlockPos upper = owner.up();
        h.assertTrue(height(world, upper) == Double.doubleToRawLongBits(-3.0), "Deep placement must retain its native landing");
        world.breakBlock(owner, false);
        h.assertTrue(height(world, upper) == Double.doubleToRawLongBits(-3.0), "Removing the deep support moved the upper block");
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

    @GameTest(templateName = "slabbed:empty_16")
    public void loweredSidePlacementCannotEnterUnshiftedNaturalTerrain(TestContext h) {
        naturalTerrainPlacement(h, false);
    }

    @GameTest(templateName = "slabbed:empty_16")
    public void replacingGeneratedSlabCannotEnterUnshiftedTerrainBelow(TestContext h) {
        naturalTerrainPlacement(h, true);
    }

    private static void naturalTerrainPlacement(TestContext h, boolean replaceGenerated) {
        ServerWorld world = h.getWorld();
        BlockPos ground = h.getAbsolutePos(new BlockPos(3, 1, 3));
        PlayerEntity player = player(h, ground);
        setup(h, world, player, ground, Items.STONE_SLAB);
        BlockPos owner = ground.up(2);
        BlockPos target = owner.east();
        BlockPos terrain = target.down();
        long ownerHeight = height(world, owner);
        BlockState generated = TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM).with(TerrainSlabsTestShim.GENERATED, true);
        BlockState[] blockers = {Blocks.STONE.getDefaultState(), Blocks.DIRT.getDefaultState(),
                Blocks.GLASS.getDefaultState(), generated.with(SlabBlock.TYPE, SlabType.TOP), generated.with(SlabBlock.TYPE, SlabType.DOUBLE)};
        Item[] items = {Items.STONE, Items.GLASS, Items.STONE_SLAB,
                TerrainSlabsTestShim.TEST_TS_SLAB.asItem()};
        for (BlockState blocker : blockers) {
            world.setBlockState(terrain, blocker, Block.NOTIFY_ALL);
            h.assertTrue(height(world, terrain) == Double.doubleToRawLongBits(0.0),
                    "Natural blocker must exercise the unshifted lane");
            h.assertTrue(!SlabAnchorAttachment.isModernPlacement(world, terrain),
                    "Natural blocker must have no player placement marker");
            for (Item item : items) {
                if (replaceGenerated) {
                    world.setBlockState(target, generated, Block.NOTIFY_ALL);
                    h.assertTrue(world.breakBlock(target, false), "Generated slab did not break");
                }
                ItemStack stack = new ItemStack(item, 2);
                ActionResult result = physicalSide(player, stack, owner, ownerHeight);
                h.assertTrue(!result.isAccepted() && world.getBlockState(target).isAir() && stack.getCount() == 2,
                        "Lowered placement entered natural terrain: " + blocker + " item=" + item);
                h.assertTrue(world.getBlockState(terrain).equals(blocker)
                        && height(world, terrain) == Double.doubleToRawLongBits(0.0)
                        && height(world, owner) == ownerHeight,
                        "Rejected placement changed terrain or the placed owner's height");
            }
        }
        // A real half-height opening remains usable; contact is not an intersection.
        world.setBlockState(terrain, generated, Block.NOTIFY_ALL);
        h.assertTrue(physicalSide(player, new ItemStack(Items.STONE_SLAB),
                        owner, ownerHeight).isAccepted(), "Touching natural half slab must leave room");
        h.assertTrue(world.getBlockState(target).isOf(Blocks.STONE_SLAB)
                        && height(world, target) == Double.doubleToRawLongBits(-0.5),
                "Fitting slab must retain its aimed height");
        h.assertTrue(height(world, owner) == ownerHeight, "Fitting slab moved the placed owner");
        h.complete();
    }

    private static ActionResult physicalSide(PlayerEntity player, ItemStack stack, BlockPos owner, long height) {
        var hit = new net.minecraft.util.hit.BlockHitResult(
                new net.minecraft.util.math.Vec3d(owner.getX() + 1.0,
                        owner.getY() + Double.longBitsToDouble(height) + 0.25, owner.getZ() + 0.5),
                Direction.EAST, owner, false);
        return PlacementCaptureBoundaryGameTest.useOn(player, stack, hit);
    }

    @GameTest(templateName = "slabbed:empty_16")
    public void terrainSlabCannotPullDownExistingFrozenFlatBlocks(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockState[] states = {Blocks.STONE.getDefaultState(), Blocks.OAK_LOG.getDefaultState(),
                Blocks.OAK_PLANKS.getDefaultState()};
        for (int i = 0; i < states.length; i++) {
            BlockPos ground = h.getAbsolutePos(new BlockPos(2 + i * 4, 1, 3));
            BlockPos upper = ground.up(2);
            world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(ground.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(upper, states[i], Block.NOTIFY_ALL);
            SlabAnchorAttachment.freezeLoweredOnPlace(world, upper, states[i]);
            h.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, upper)
                            && !SlabAnchorAttachment.isModernPlacement(world, upper),
                    "Existing-world control must carry only a legacy flat marker");
            long before = height(world, upper);
            world.breakBlock(ground.up(), false);
            h.assertTrue(place(player(h, ground), new ItemStack(TerrainSlabsTestShim.TEST_TS_SLAB.asItem()),
                            ground).isAccepted(), "Terrain slab must fit under the unchanged upper block");
            h.assertTrue(height(world, upper) == before,
                    "A new Terrain Slabs support pulled an existing frozen-flat block down: " + states[i]);
            h.assertTrue(world.getBlockState(upper).getOutlineShape(world, upper).getMin(Direction.Axis.Y) == 0.0,
                    "Existing upper block acquired a displaced ghost outline");
        }
        h.complete();
    }

    @GameTest(templateName = "slabbed:empty_16")
    public void newSlabCannotAdoptUntouchedObjectCubesAboveIt(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockState[] states = {Blocks.GLASS.getDefaultState(), Blocks.CRAFTING_TABLE.getDefaultState(),
                Blocks.FURNACE.getDefaultState()};
        Item[] slabs = {Items.STONE_SLAB, TerrainSlabsTestShim.TEST_TS_SLAB.asItem()};
        for (int z = 0; z < slabs.length; z++) for (int i = 0; i < states.length; i++) {
            BlockPos ground = h.getAbsolutePos(new BlockPos(2 + i * 4, 1, 3 + z * 5));
            BlockPos upper = ground.up(2);
            world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(upper, states[i], Block.NOTIFY_ALL);
            long before = height(world, upper);
            h.assertTrue(before == Double.doubleToRawLongBits(0.0), "Untouched cube must begin flat");
            h.assertTrue(place(player(h, ground), new ItemStack(slabs[z]), ground).isAccepted(),
                    "Slab must fit below the untouched cube");
            h.assertTrue(height(world, upper) == before,
                    "A new slab pulled an untouched object cube down: " + states[i]);
            h.assertTrue(!SlabAnchorAttachment.isModernPlacement(world, upper)
                            && world.getBlockState(upper).equals(states[i]),
                    "Protecting an existing cube must not rewrite it or author placement provenance");
        }
        h.complete();
    }
}
