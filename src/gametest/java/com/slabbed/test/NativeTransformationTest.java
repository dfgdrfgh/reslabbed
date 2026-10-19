package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Blocks;
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

/** Native item transformations retain the chosen physical origin and ordinary item behavior. */
public final class NativeTransformationTest {
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void coveredGrassDecayPreservesStackHeightAndSharedSlab(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()-3,base.getY(),base.getZ()+.5);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.GRASS_BLOCK));
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(base).add(0,.5,0),Direction.UP,base,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"grass placed on slab");
        var owner=base.up();
        t.assertTrue(com.slabbed.cell.CellWorld.place(world,owner.getX(),owner.getZ(),owner.getY()*2+1,Blocks.OAK_SLAB.getDefaultState()),"upper slab shares the native grass owner");
        var slab=com.slabbed.cell.SupplementalCells.contents(world,owner);
        world.setBlockState(owner.up(),Blocks.STONE.getDefaultState());
        world.getBlockState(owner).randomTick(world,owner,net.minecraft.util.math.random.Random.create(1));
        t.assertTrue(world.getBlockState(owner).isOf(Blocks.DIRT),"actual vanilla grass random tick turns covered grass into dirt");
        t.assertTrue(NativeSeats.offset(world,owner)==-.5,"soil decay must not raise the block off its slab");
        t.assertTrue(slab.equals(com.slabbed.cell.SupplementalCells.contents(world,owner)),"soil decay must preserve the slab above it");
        t.assertTrue(Math.abs(world.getBlockState(owner).getOutlineShape(world,owner).getBoundingBox().minY+.5)<1e-7,"transformed soil geometry keeps its bottom");
        t.complete();
    }
    private static void tillOrFlatten(TestContext t,net.minecraft.item.Item tool,net.minecraft.block.Block result) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.GRASS_BLOCK,2));
        var floor=new BlockHitResult(Vec3d.ofBottomCenter(base).add(0,.5,0),Direction.UP,base,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,floor)).isAccepted(),"native grass item placed");
        var owner=base.up();var before=NativeSeats.seat(world,owner);
        t.assertTrue(world.getBlockState(owner).isOf(Blocks.GRASS_BLOCK) && before!=null && before.halfOffset()==-1,"grass retains real native lifecycle and half seat");
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(tool));
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(owner).add(0,.5,0),Direction.UP,owner,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"actual ground tool transforms native grass");
        t.assertTrue(world.getBlockState(owner).isOf(result),"native transformation installs expected ground state");
        var after=NativeSeats.seat(world,owner);
        t.assertTrue(after!=null && after.halfOffset()==before.halfOffset(),"ground transformation retains original frozen origin");
        t.assertTrue(after.block().equals(net.minecraft.registry.Registries.BLOCK.getId(result).toString()),"ground transformation updates native seat identity");
        t.assertTrue(player.getMainHandStack().getDamage()==1,"actual survival ground tool wears once");
        t.assertTrue(Math.abs(world.getBlockState(owner).getCollisionShape(world,owner).getBoundingBox().minY+.5)<1e-7,"transformed ground collision retains its physical bottom");
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void actualHoeRetainsNativeGrassOrigin(TestContext t) {tillOrFlatten(t,Items.IRON_HOE,Blocks.FARMLAND);}
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void actualShovelRetainsNativeGrassOrigin(TestContext t) {tillOrFlatten(t,Items.IRON_SHOVEL,Blocks.DIRT_PATH);}
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void actualAxeStripsNativeLogWithoutChangingItsPhysicalOrigin(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.OAK_LOG,2));
        var floor=new BlockHitResult(Vec3d.ofBottomCenter(base).add(0,.5,0),Direction.UP,base,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,floor)).isAccepted(),"native log item placed");
        var owner=base.up();var before=NativeSeats.seat(world,owner);
        t.assertTrue(world.getBlockState(owner).isOf(Blocks.OAK_LOG) && before!=null && before.halfOffset()==-1,"actual log stores chosen half seat");
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
        var log=new BlockHitResult(Vec3d.ofBottomCenter(owner).add(.5,0,0),Direction.EAST,owner,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,log)).isAccepted(),"actual axe strips log");
        t.assertTrue(world.getBlockState(owner).isOf(Blocks.STRIPPED_OAK_LOG),"native stripping changes block identity");
        var after=NativeSeats.seat(world,owner);
        t.assertTrue(after!=null && after.halfOffset()==before.halfOffset(),"stripping preserves frozen physical origin");
        t.assertTrue(after.block().equals("minecraft:stripped_oak_log"),"seat identity follows explicit vanilla transformation");
        t.assertTrue(player.getMainHandStack().getDamage()==1,"actual survival axe wears once");
        t.assertTrue(Math.abs(world.getBlockState(owner).getCollisionShape(world,owner).getBoundingBox().minY+.5)<1e-7,"stripped native collision stays at its visible origin");
        t.complete();
    }
}
