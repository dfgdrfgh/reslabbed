package com.slabbed.test;

import com.slabbed.cell.CellBlockEntity;
import com.slabbed.cell.CellWorld;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

/** Physical geometry remains fixed after placement; see LAW.md. */
public final class PhysicalCellTest {
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void slabAndFullBlockShareTheirPhysicalCell(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2)); var w=t.getWorld();
        w.setBlockState(p,Blocks.STONE_SLAB.getDefaultState());
        t.assertTrue(CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState()),"piece placed");
        t.assertTrue(w.getBlockEntity(p) instanceof CellBlockEntity,"slab retained in cell");
        t.assertTrue(w.getBlockEntity(p.up()) instanceof CellBlockEntity,"upper cap lives in physical cell");
        t.assertTrue(!w.isSpaceEmpty(new Box(p).contract(.1)),"cell collision");
        t.assertTrue(w.isSpaceEmpty(new Box(p.up(2)).contract(.1)),"no distant ghost collision");
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void failedPlacementIsAtomic(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2)); var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState()); w.setBlockState(p.up(),Blocks.BEDROCK.getDefaultState());
        t.assertFalse(CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState()),"blocked cap rejected");
        t.assertTrue(w.getBlockState(p).isOf(Blocks.OAK_SLAB),"failed placement kept slab");
        t.assertTrue(w.getBlockState(p.up()).isOf(Blocks.BEDROCK),"failed placement kept obstruction");
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void breakingCapRemovesOnePiece(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2)); var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState());
        CellWorld.remove(w,p.up(),-1,null,false);
        t.assertTrue(w.getBlockState(p).isOf(Blocks.OAK_SLAB),"support restored exactly");
        t.assertTrue(w.getBlockState(p.up()).isAir(),"cap removed"); t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void neighborsCannotMovePlacedGeometry(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2)); var w=t.getWorld();
        w.setBlockState(p,Blocks.STONE_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState());
        var before=CellWorld.contents(w,p);
        for (var d:Direction.Type.HORIZONTAL) {
            w.setBlockState(p.offset(d),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            w.removeBlock(p.offset(d),false);
        }
        t.assertTrue(before.equals(CellWorld.contents(w,p)),"neighbor changes preserved stored origin"); t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void normalItemUseBuildsDeepPhysicalStack(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2)); var w=t.getWorld();
        w.setBlockState(p,Blocks.STONE_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.CREATIVE); double top=p.getY()+.5;
        for(int n=0;n<128;n++) {
            var pos=BlockPos.ofFloored(p.getX(),top-1e-4,p.getZ());
            var stack=new ItemStack(n%2==0?Items.STONE:Items.OAK_SLAB); player.setStackInHand(Hand.MAIN_HAND,stack);
            player.setPosition(p.getX()+.5,top+1,p.getZ()+.5);
            var hit=new BlockHitResult(new Vec3d(p.getX()+.5,top,p.getZ()+.5),Direction.UP,pos,false);
            var result=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
            t.assertTrue(result.isAccepted(),"item use step "+n);
            top+=n%2==0?1:.5;
            var ray=w.raycast(new RaycastContext(new Vec3d(p.getX()+.5,top+2,p.getZ()+.5),new Vec3d(p.getX()+.5,top-.25,p.getZ()+.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
            t.assertTrue(ray.getType()==HitResult.Type.BLOCK && Math.abs(ray.getPos().y-top)<1e-6,"vanilla ray hit physical top "+n);
            t.assertTrue(!w.isSpaceEmpty(new Box(p.getX()+.2,top-.2,p.getZ()+.2,p.getX()+.8,top-.01,p.getZ()+.8)),"standing collision "+n);
        }
        t.complete();
    }
}
