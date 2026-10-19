package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
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

/** Real item placement on a slab followed by ordinary neighbor-driven piston events. */
public final class PistonSlabRegressionTest {
    private static BlockPos placed(TestContext t,Direction facing) {
        var world=t.getWorld();var slab=t.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(slab,Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(slab.getX()-3,slab.getY(),slab.getZ()+.5);
        var stack=new ItemStack(Items.PISTON,2);
        stack.set(DataComponentTypes.BLOCK_STATE,BlockStateComponent.DEFAULT.with(PistonBlock.FACING,facing));
        player.setStackInHand(Hand.MAIN_HAND,stack);
        var result=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofBottomCenter(slab).add(0,.5,0),Direction.UP,slab,false)));
        t.assertTrue(result.isAccepted() && stack.getCount()==1,"actual piston item is placed once");
        var owner=slab.up();
        t.assertTrue(NativeSeats.offset(world,owner)==-.5,"placed piston starts at the slab top");
        if(facing==Direction.DOWN)world.setBlockState(slab,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        return owner;
    }
    private static void cycle(TestContext t,Direction facing) {
        var owner=placed(t,facing);var world=t.getWorld();
        var source=owner.offset(facing.getAxis()==Direction.Axis.X?Direction.NORTH:Direction.WEST);
        for(int cycle=0;cycle<3;cycle++) {
            int start=2+cycle*20;
            t.runAtTick(start,()->world.setBlockState(source,Blocks.REDSTONE_BLOCK.getDefaultState(),Block.NOTIFY_ALL));
            t.runAtTick(start+7,()-> {
                t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"slab piston extends toward "+facing);
                t.assertTrue(world.getBlockState(owner.offset(facing)).isOf(Blocks.PISTON_HEAD),"head settles toward "+facing);
                t.assertTrue(NativeSeats.offset(world,owner)==-.5 && NativeSeats.offset(world,owner.offset(facing))==-.5,
                        "base and head remain at the placed height toward "+facing);
                world.setBlockState(source,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            });
            t.runAtTick(start+15,()-> {
                t.assertTrue(world.getBlockState(owner).isOf(Blocks.PISTON) && !world.getBlockState(owner).get(PistonBlock.EXTENDED),
                        "base retracts toward "+facing);
                t.assertTrue(NativeSeats.offset(world,owner)==-.5,"retracted base stays on slab toward "+facing);
                t.assertTrue(world.getBlockState(owner.offset(facing)).isAir(),"head fully retracts toward "+facing);
            });
        }
        t.runAtTick(60,t::complete);
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void slabPistonCyclesEast(TestContext t) {cycle(t,Direction.EAST);}
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void slabPistonCyclesWest(TestContext t) {cycle(t,Direction.WEST);}
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void slabPistonCyclesNorth(TestContext t) {cycle(t,Direction.NORTH);}
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void slabPistonCyclesSouth(TestContext t) {cycle(t,Direction.SOUTH);}
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void slabPistonCyclesUp(TestContext t) {cycle(t,Direction.UP);}
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void slabPistonCyclesDown(TestContext t) {cycle(t,Direction.DOWN);}

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void exposedStoneFaceBesideLoweredPistonIsVisible(TestContext t) {
        var owner=placed(t,Direction.EAST);var stone=owner.north();var world=t.getWorld();
        world.setBlockState(stone,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        t.assertTrue(Block.shouldDrawSide(world.getBlockState(stone),world,stone,Direction.SOUTH,owner),
                "stone face above a lowered piston remains visible");
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void ordinaryFaceCacheCannotHideFaceBesideLoweredPiston(TestContext t) {
        var world=t.getWorld();var owner=placed(t,Direction.WEST);
        var normal=owner.east(3);var state=Blocks.PISTON.getDefaultState().with(PistonBlock.FACING,Direction.WEST);
        world.setBlockState(normal,state,Block.NOTIFY_ALL);
        world.setBlockState(normal.north(),Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
        t.assertFalse(Block.shouldDrawSide(Blocks.GOLD_BLOCK.getDefaultState(),world,normal.north(),Direction.SOUTH,normal),
                "ordinary flush piston hides the shared gold face and warms the state-only cache");
        world.setBlockState(owner.north(),Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
        t.assertTrue(Block.shouldDrawSide(Blocks.GOLD_BLOCK.getDefaultState(),world,owner.north(),Direction.SOUTH,owner),
                "cached flush piston must not hide the exposed face beside a lowered piston");
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=40)
    public void groundLeverBesideSlabPistonActivatesItsPhysicalSide(TestContext t) {
        var world=t.getWorld();var owner=placed(t,Direction.EAST);
        var floor=owner.north().down(2);var lever=floor.up();
        world.setBlockState(floor,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(floor.getX()-2,floor.getY()+1,floor.getZ()+.5);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.LEVER));
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofBottomCenter(floor).add(0,1,0),Direction.UP,floor,false))).isAccepted(),
                "real lever is placed on the ground beside the slab");
        t.assertTrue(world.getBlockState(lever).isOf(Blocks.LEVER),"ground lever retains its native owner below the piston owner");
        var hit=new BlockHitResult(Vec3d.ofCenter(lever),Direction.UP,lever,false);
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        t.runAtTick(2,()->world.getBlockState(lever).onUse(world,player,hit));
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"ground lever powers the physical side of the slab piston");
            world.getBlockState(lever).onUse(world,player,hit);
        });
        t.runAtTick(25,()-> {
            t.assertFalse(world.getBlockState(owner).get(PistonBlock.EXTENDED),"ground lever releases piston power");
            t.assertTrue(NativeSeats.offset(world,owner)==-.5,"lever cycle does not move the piston off its slab");
            t.complete();
        });
    }
}
