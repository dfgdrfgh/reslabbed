package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Repeated real placements must keep their remembered geometry and physical support (LAW.md). */
public final class RepeatedStackingTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void alternatingTowerRetainsStandingCollisionAndTargeting(TestContext h) {
        build(h, 24, true);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void alternatingTowerRetainsHeightsBeyondByteRange(TestContext h) {
        build(h, 48, false);
    }

    private static void build(TestContext h, int count, boolean physical) {
        var world = h.getWorld();
        BlockPos base = h.getAbsolutePos(new BlockPos(3, 24, 3));
        world.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        for (int step = 1; step <= count; step++) {
            BlockPos owner = base.up(step - 1);
            var ownerState = world.getBlockState(owner);
            double top = owner.getY() + ownerState.getOutlineShape(world, owner).getMax(Direction.Axis.Y);
            player.setPosition(base.getX() + 4.5, top + 1, base.getZ() + .5);
            ItemStack stack = new ItemStack((step & 1) == 1 ? Items.STONE_SLAB : Items.STONE);
            player.setStackInHand(Hand.MAIN_HAND, stack);
            var hit = new BlockHitResult(new Vec3d(base.getX() + .5, top, base.getZ() + .5),
                    Direction.UP, owner, false);
            var result = stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            h.assertTrue(result.isAccepted(), "Stack placement rejected at step " + step);
            BlockPos placed = base.up(step);
            var state = world.getBlockState(placed);
            h.assertTrue(state.isOf((step & 1) == 1 ? Blocks.STONE_SLAB : Blocks.STONE),
                    "Stack filled the wrong cell at step " + step);
            double expected = -(step / 2) * .5;
            h.assertTrue(SlabAnchorAttachment.storedPlacementDy(world, placed) == expected,
                    "Stack lost its remembered height at step " + step + " expected=" + expected);
            h.assertTrue(SlabSupport.getYOffset(world, placed, state) == expected,
                    "Live height differs from the placed height at step " + step);
            if (!physical) continue;
            double drawnTop = placed.getY() + state.getOutlineShape(world, placed).getMax(Direction.Axis.Y);
            Box interior = new Box(base.getX() + .3, drawnTop - .4, base.getZ() + .3,
                    base.getX() + .7, drawnTop - .1, base.getZ() + .7);
            h.assertTrue(!world.isSpaceEmpty(null, interior),
                    "Standing collision missed the drawn stack at step " + step);
            var target = SlabbedOffsetRaycast.raycast(world,
                    new Vec3d(base.getX() + .5, drawnTop - .25, base.getZ() - 1),
                    new Vec3d(base.getX() + .5, drawnTop - .25, base.getZ() + 2), ShapeContext.absent());
            h.assertTrue(target.getType() == HitResult.Type.BLOCK && target.getBlockPos().equals(placed),
                    "Ray targeted a ghost owner at step " + step + ": " + target);
            player.setPosition(base.getX() + .5, drawnTop + .4, base.getZ() + .5);
            player.move(MovementType.SELF, new Vec3d(0, -.8, 0));
            h.assertTrue(Math.abs(player.getY() - drawnTop) < 1.0e-6,
                    "Player fell through or stood on a ghost surface at step " + step);
        }
        if (physical) h.assertTrue(world.isSpaceEmpty(null, new Box(base.up(count)).contract(.2)),
                "The stack's old logical cell retained a ghost collision");
        h.complete();
    }
}
