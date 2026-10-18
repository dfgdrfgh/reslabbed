package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Entity items use visible support faces and keep native entity persistence. */
public final class NativeEntityItemTest {
    private static void armorStand(TestContext t,boolean crossing) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        if(crossing)t.assertTrue(CellWorld.place(world,base.getX(),base.getZ(),base.getY()*2+1,Blocks.STONE.getDefaultState()),"crossing support fixture");
        double y=base.getY()+(crossing?1.5:.5);var hitCell=crossing?base.up():base;
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.ARMOR_STAND,2));
        var hit=new BlockHitResult(new Vec3d(base.getX()+.5,y,base.getZ()+.5),Direction.UP,hitCell,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"real armor stand item accepted");
        var entities=world.getEntitiesByClass(ArmorStandEntity.class,new Box(base).expand(3),entity->!entity.isRemoved());
        t.assertTrue(entities.size()==1,"one actual armor stand spawned");var stand=entities.getFirst();
        t.assertTrue(Math.abs(stand.getY()-y)<1e-7,"armor stand feet rest on the clicked physical face: "+stand.getPos());
        t.assertTrue(player.getMainHandStack().getCount()==1,"armor stand item consumed once");
        var before=stand.getPos();world.setBlockState(base.east(),Blocks.GLASS.getDefaultState(),Block.NOTIFY_ALL);world.removeBlock(base.east(),false);
        t.assertTrue(stand.getPos().equals(before),"neighbor edit never reseats entity");
        var saved=new NbtCompound();stand.writeNbt(saved);
        var restored=new ArmorStandEntity(net.minecraft.entity.EntityType.ARMOR_STAND,world);restored.readNbt(saved);
        t.assertTrue(restored.getPos().equals(before),"ordinary entity NBT retains exact physical feet position");
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void armorStandItemUsesBottomSlabTop(TestContext t) {armorStand(t,false);}
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void armorStandItemUsesCrossingWholeBlockTop(TestContext t) {armorStand(t,true);}
}
