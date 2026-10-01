package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fence ceiling connections preserve the real placed seat (LAW.md). */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class FenceCeilingConnectionTest {
    private static final BlockPos OWNER = new BlockPos(2, 3, 2);
    private static final double EPS = 1.0e-6d;

    private static void store(ServerLevel w, BlockPos p, double dy) {
        SlabPlacementHeightAttachment.putHalfSteps(w.getChunkAt(p), p, (int)Math.round(dy*2.0d));
    }
    private static void topSlab(ServerLevel w, BlockPos owner, double dy) {
        w.setBlock(owner, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        store(w, owner, dy);
    }
    private static void place(GameTestHelper h, BlockPos owner, Direction face, double y) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(Items.OAK_FENCE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(owner.getX()+0.5d,y,owner.getZ()+0.5d),face,owner,false)));
    }
    private static void seat(GameTestHelper h, BlockPos post, double expected) {
        var w=h.getLevel();
        double saved=SlabPlacementHeightAttachment.storedOffset(w,post);
        if (!w.getBlockState(post).is(Blocks.OAK_FENCE) || Math.abs(saved-expected)>EPS
                || !Double.isFinite(saved) || Math.abs(SlabSupport.getYOffset(w,post,w.getBlockState(post))-expected)>EPS) {
            throw new GameTestAssertException("placed fence must keep its saved seat "+expected+", got "+saved);
        }
    }

    @GameTest(template="empty")
    public void placedPostConnectsWhenTheCeilingIsAdded(GameTestHelper h) {
        var w=h.getLevel();var owner=h.absolutePos(OWNER);var post=owner.below();var floor=post.below();
        w.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
        place(h,floor,Direction.UP,floor.getY()+1.0d);
        seat(h,post,0.0d);
        topSlab(w,owner,0.0d);
        var shape=w.getBlockState(post).getShape(w,post);
        if (Math.abs(post.getY()+shape.max(Direction.Axis.Y)-(owner.getY()+0.5d))>EPS) {
            throw new GameTestAssertException("the existing post must meet the top-slab underside");
        }
        Vec3 start=new Vec3(post.getX()-1.0d,post.getY()+1.25d,post.getZ()+0.5d);
        var hit=com.slabbed.util.SlabbedOffsetRaycast.raycast(w,start,start.add(2.0d,0.0d,0.0d),
                net.minecraft.world.phys.shapes.CollisionContext.empty());
        if (hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || !hit.getBlockPos().equals(post)) {
            throw new GameTestAssertException("the extended post must be targetable");
        }
        w.setBlock(owner,Blocks.AIR.defaultBlockState(),2);
        seat(h,post,0.0d);
        if (Math.abs(w.getBlockState(post).getShape(w,post).max(Direction.Axis.Y)-1.0d)>EPS) {
            throw new GameTestAssertException("removing the ceiling restores ordinary post geometry");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public void fenceCanBePlacedUnderAFlushTopSlab(GameTestHelper h) {
        var w=h.getLevel();var owner=h.absolutePos(OWNER);
        topSlab(w,owner,0.0d);
        place(h,owner,Direction.DOWN,owner.getY()+0.5d);
        seat(h,owner.below(),0.0d);
        h.succeed();
    }

    @GameTest(template="empty")
    public void aFullCellOpeningDoesNotStretchThePost(GameTestHelper h) {
        var w=h.getLevel();var owner=h.absolutePos(OWNER);var post=owner.below();
        topSlab(w,owner,0.0d);w.setBlock(post,Blocks.OAK_FENCE.defaultBlockState(),2);store(w,post,-0.5d);
        if (Math.abs(w.getBlockState(post).getShape(w,post).max(Direction.Axis.Y)-0.5d)>EPS) {
            throw new GameTestAssertException("a full-cell opening must retain the ordinary seated post");
        }
        seat(h,post,-0.5d);h.succeed();
    }

    @GameTest(template="empty")
    public void fittingPostUnderALoweredTopSlabKeepsItsSeat(GameTestHelper h) {
        var w=h.getLevel();var owner=h.absolutePos(OWNER);var post=owner.below();
        w.setBlock(post.below(),Blocks.OAK_FENCE.defaultBlockState(),2);store(w,post.below(),0.0d);
        topSlab(w,owner,-0.5d);
        place(h,owner,Direction.DOWN,owner.getY());
        seat(h,post,0.0d);
        if (Math.abs(post.getY()+w.getBlockState(post).getShape(w,post).max(Direction.Axis.Y)-owner.getY())>EPS) {
            throw new GameTestAssertException("fitting post and slab must meet without overlap");
        }
        w.setBlock(owner,Blocks.AIR.defaultBlockState(),2);seat(h,post,0.0d);h.succeed();
    }
}
