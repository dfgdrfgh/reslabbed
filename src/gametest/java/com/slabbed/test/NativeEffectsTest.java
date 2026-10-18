package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Native effects include contextual collision at the object's remembered physical height. */
public final class NativeEffectsTest {
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void scaffoldingItemUsesBottomSlabPhysicalSupport(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.SCAFFOLDING,2));
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(base).add(0,.5,0),Direction.UP,base,false);
        var result=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        t.assertTrue(result.isAccepted(),"scaffolding item accepts the physical bottom-slab top");
        var owner=base.up();var state=world.getBlockState(owner);var seat=NativeSeats.seat(world,owner);
        t.assertTrue(state.isOf(Blocks.SCAFFOLDING),"scaffolding remains a native block");
        t.assertTrue(seat!=null && seat.halfOffset()==-1,"scaffolding stores the slab's physical -0.5 seat");
        t.assertTrue(state.get(ScaffoldingBlock.DISTANCE)==0,"physical floor supports native scaffolding distance zero");
        t.assertTrue(player.getMainHandStack().getCount()==1,"successful scaffolding placement consumes exactly one item");
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void loweredScaffoldingSupportsEntityAtRememberedTop(TestContext t) {
        var world=t.getWorld();var owner=t.getAbsolutePos(new BlockPos(3,4,3));
        // Isolate contextual collision from placement support and scheduled distance updates.
        var state=Blocks.SCAFFOLDING.getDefaultState().with(ScaffoldingBlock.DISTANCE,0);
        world.setBlockState(owner,state,Block.NOTIFY_LISTENERS);
        NativeSeats.put(world,owner,new NativeSeats.Seat(-1,"minecraft:scaffolding"));
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        double top=owner.getY()+.5;
        player.setPosition(owner.getX()+.5,top+1e-6,owner.getZ()+.5);
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        var collision=state.getCollisionShape(world,owner,ShapeContext.of(player));
        t.assertFalse(collision.isEmpty(),"entity standing at the lowered visible top receives the scaffolding platform");
        t.assertTrue(Math.abs(collision.getBoundingBox().maxY-.5)<1e-7,
                "contextual collision returns the platform at its stored physical top exactly once");
        player.move(MovementType.SELF,new Vec3d(0,-.25,0));
        t.assertTrue(Math.abs(player.getY()-top)<1e-5,"downward entity movement lands on the remembered scaffold top");
        t.assertTrue(NativeSeats.offset(world,owner)==-.5,"standing collision does not alter the stored seat");
        t.complete();
    }
}
