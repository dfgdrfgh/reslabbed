package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.item.*;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Neighbor identity alone cannot join objects whose physical bases differ. */
public final class NativeConnectionTest {
    private static BlockPos[] pair(TestContext t,Item item) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        for(int x=0;x<2;x++) {
            world.setBlockState(base.east(x).up(),Blocks.AIR.getDefaultState());
            world.setBlockState(base.east(x),Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE,x==0?SlabType.BOTTOM:SlabType.TOP));
        }
        var player=t.createMockPlayer(GameMode.SURVIVAL);player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);player.setYaw(0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item,2));
        for(int x=0;x<2;x++) {
            var floor=base.east(x);var point=Vec3d.ofBottomCenter(floor).add(0,x==0?.5:1,0);
            var result=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(point,Direction.UP,floor,false)));
            t.assertTrue(result.isAccepted(),"both stepped native items placed: "+item);
        }
        var low=base.up();var high=low.east();
        t.assertTrue(NativeSeats.seat(world,low)!=null && NativeSeats.seat(world,low).halfOffset()==-1,"lower native member retains half seat");
        t.assertTrue(NativeSeats.offset(world,high)==0,"upper native member remains on its aimed full-height floor");
        return new BlockPos[]{low,high};
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void steppedFencesPanesAndWallsDoNotJoinAcrossHeight(TestContext t) {
        for(var item:new Item[]{Items.OAK_FENCE,Items.GLASS_PANE,Items.COBBLESTONE_WALL}) {
            var pair=pair(t,item);var a=t.getWorld().getBlockState(pair[0]);var b=t.getWorld().getBlockState(pair[1]);
            if(item==Items.COBBLESTONE_WALL)
                t.assertTrue(a.get(Properties.EAST_WALL_SHAPE)==WallShape.NONE && b.get(Properties.WEST_WALL_SHAPE)==WallShape.NONE,"stepped walls do not draw mismatched arms");
            else t.assertFalse(a.get(Properties.EAST)||b.get(Properties.WEST),"stepped connectors do not draw mismatched arms: "+item);
        }
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void steppedChestsKeepIndependentInventories(TestContext t) {
        var pair=pair(t,Items.CHEST);
        for(var pos:pair)t.assertTrue(t.getWorld().getBlockState(pos).get(ChestBlock.CHEST_TYPE)==ChestType.SINGLE,"stepped chest remains a single native inventory");
        t.complete();
    }
}
