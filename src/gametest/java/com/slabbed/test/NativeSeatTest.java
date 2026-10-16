package com.slabbed.test;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

/** Native block identity and physical targeting must agree after half-height placement. */
public final class NativeSeatTest {
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void signRetainsNativeEntityAtPhysicalSeat(TestContext t) {
        var w=t.getWorld();var slab=t.getAbsolutePos(new BlockPos(3,3,3));
        w.setBlockState(slab,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(slab.getX()+.5,slab.getY(),slab.getZ()-3);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.OAK_SIGN,2));
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(slab).add(0,.5,0),Direction.UP,slab,false);
        var result=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        var owner=slab.up();
        t.assertTrue(result.isAccepted() && w.getBlockEntity(owner) instanceof SignBlockEntity,"placed a real native sign entity");
        t.assertTrue(player.getMainHandStack().getCount()==1,"placement consumed once");
        var ray=w.raycast(new RaycastContext(new Vec3d(slab.getX()+.5,slab.getY()+.75,slab.getZ()-2),
                new Vec3d(slab.getX()+.5,slab.getY()+.75,slab.getZ()+2),RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,player));
        t.assertTrue(ray.getType()==HitResult.Type.BLOCK && ray.getBlockPos().equals(owner),"physical sign ray targets native owner: "+ray);
        var before=ray.getPos();
        w.setBlockState(slab.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        w.removeBlock(slab.east(),false);
        var after=w.raycast(new RaycastContext(new Vec3d(slab.getX()+.5,slab.getY()+.75,slab.getZ()-2),
                new Vec3d(slab.getX()+.5,slab.getY()+.75,slab.getZ()+2),RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,player));
        t.assertTrue(after.getBlockPos().equals(owner) && before.equals(after.getPos()),"neighbor changes preserved physical sign seat");
        t.complete();
    }
}
