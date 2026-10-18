package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.cell.SupplementalCells;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
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

/** Server piston events move whole logical static pieces, including native-owner companions. */
public final class NativeCellPistonTest {
    private static BlockPos piston(TestContext t,int x,int y,int z,boolean sticky,Direction facing) {
        var support=t.getAbsolutePos(new BlockPos(x,y,z));var world=t.getWorld();
        world.setBlockState(support,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(support.getX()-3,support.getY(),support.getZ()+.5);
        var stack=new ItemStack(sticky?Items.STICKY_PISTON:Items.PISTON,2);
        // A vanilla item state component makes the fixture independent of mock-player look overrides.
        stack.set(DataComponentTypes.BLOCK_STATE,BlockStateComponent.DEFAULT.with(PistonBlock.FACING,facing));
        player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(support).add(0,.5,0),Direction.UP,support,false);
        var result=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        var owner=support.up();var expected=sticky?Blocks.STICKY_PISTON:Blocks.PISTON;
        t.assertTrue(result.isAccepted() && world.getBlockState(owner).isOf(expected),"actual piston item placed");
        t.assertTrue(stack.getCount()==1,"actual piston item consumed once");
        t.assertTrue(world.getBlockState(owner).get(PistonBlock.FACING)==facing,"vanilla item state component retains requested facing");
        var seat=NativeSeats.seat(world,owner);
        t.assertTrue(seat!=null && seat.halfOffset()==-1,"piston item stores its actual lowered seat");
        return owner;
    }

    private static void place(TestContext t,int x,int z,int half,Block block) {
        t.assertTrue(CellWorld.place(t.getWorld(),x,z,half,block.getDefaultState()),"static piece fixture fits at half "+half+"; lower="+t.getWorld().getBlockState(new BlockPos(x,Math.floorDiv(half,2),z))+"; upper="+t.getWorld().getBlockState(new BlockPos(x,Math.floorDiv(half+1,2),z)));
    }

    private static void piece(TestContext t,int x,int z,int half,int length,Block block,boolean present) {
        for(int y=Math.floorDiv(half,2);y<=Math.floorDiv(half+length-1,2);y++) {
            var contents=CellWorld.contents(t.getWorld(),new BlockPos(x,y,z));
            int offset=half-y*2;
            boolean found=contents!=null && contents.fragments().stream().anyMatch(p->p.offset()==offset && p.length()==length && p.value().isOf(block));
            t.assertTrue(found==present,(present?"whole piece retains companion":"old companion removed")+" at "+x+","+y+","+z+" originHalf="+half);
        }
    }

    private static void power(TestContext t,BlockPos piston,boolean powered) {
        t.getWorld().setBlockState(piston.north(),(powered?Blocks.REDSTONE_BLOCK:Blocks.AIR).getDefaultState(),Block.NOTIFY_ALL);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=60)
    public void horizontalPistonPushesAndStickyPullsOneCrossingPiece(TestContext t) {
        var owner=piston(t,2,3,3,true,Direction.EAST);int half=owner.getY()*2-1;
        var source=owner.east();var destination=owner.east(2);
        place(t,source.getX(),source.getZ(),half,Blocks.STONE);
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(3,()->t.assertTrue(t.getWorld().getBlockEntity(owner.east()) instanceof PistonBlockEntity,
                "real extension animates a vanilla moving piston head"));
        t.runAtTick(10,()-> {
            t.assertTrue(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"carrier piece permits actual extension");
            piece(t,source.getX(),source.getZ(),half,2,Blocks.STONE,false);
            piece(t,destination.getX(),destination.getZ(),half,2,Blocks.STONE,true);
            power(t,owner,false);
        });
        t.runAtTick(20,()-> {
            t.assertFalse(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"sticky piston retracts");
            piece(t,source.getX(),source.getZ(),half,2,Blocks.STONE,true);
            piece(t,destination.getX(),destination.getZ(),half,2,Blocks.STONE,false);t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=40)
    public void verticalPushMovesOneLogicalPieceAcrossItsCompanions(TestContext t) {
        var owner=piston(t,3,3,3,false,Direction.UP);int half=owner.getY()*2+1;
        place(t,owner.getX(),owner.getZ(),half,Blocks.STONE);
        t.assertTrue(SupplementalCells.has(t.getWorld(),owner),"crossing piece initially shares piston owner as supplemental geometry");
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(3,()->t.assertTrue(t.getWorld().getBlockEntity(owner.up()) instanceof PistonBlockEntity,
                "vertical carrier push uses actual vanilla head animation"));
        t.runAtTick(12,()-> {
            t.assertTrue(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"vertical carrier push extends");
            piece(t,owner.getX(),owner.getZ(),half,2,Blocks.STONE,false);
            piece(t,owner.getX(),owner.getZ(),half+2,2,Blocks.STONE,true);
            t.assertFalse(SupplementalCells.has(t.getWorld(),owner),"vacated piston owner has no companion residue");
            t.assertTrue(t.getWorld().getBlockState(owner.up()).isOf(Blocks.PISTON_HEAD),"vanilla physical head remains native");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=40)
    public void pushingUpperPiecePreservesIndependentLowerSlabs(TestContext t) {
        var owner=piston(t,2,3,3,false,Direction.EAST);var source=owner.east();var destination=owner.east(2);
        int half=owner.getY()*2-1;
        place(t,source.getX(),source.getZ(),half-1,Blocks.OAK_SLAB);
        place(t,destination.getX(),destination.getZ(),half-1,Blocks.BRICK_SLAB);
        place(t,source.getX(),source.getZ(),half,Blocks.STONE);
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertTrue(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"only intersecting upper piece is pushed");
            piece(t,source.getX(),source.getZ(),half,2,Blocks.STONE,false);
            piece(t,destination.getX(),destination.getZ(),half,2,Blocks.STONE,true);
            piece(t,source.getX(),source.getZ(),half-1,1,Blocks.OAK_SLAB,true);
            piece(t,destination.getX(),destination.getZ(),half-1,1,Blocks.BRICK_SLAB,true);t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=45)
    public void twelveWholeCrossingPiecesMoveButThirteenRejectAtomically(TestContext t) {
        var twelve=piston(t,2,3,2,false,Direction.UP);var thirteen=piston(t,5,3,5,false,Direction.UP);
        int half=twelve.getY()*2+1;
        // The empty template has a roof above the small fixture; the column extends beyond it.
        for(var base:new BlockPos[]{twelve,thirteen}) for(int y=base.getY()+1;y<=base.getY()+15;y++)
            t.getWorld().setBlockState(new BlockPos(base.getX(),y,base.getZ()),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        for(int n=0;n<12;n++) place(t,twelve.getX(),twelve.getZ(),half+2*n,Blocks.STONE);
        for(int n=0;n<13;n++) place(t,thirteen.getX(),thirteen.getZ(),half+2*n,Blocks.STONE);
        t.runAtTick(2,()-> {power(t,twelve,true);power(t,thirteen,true);});
        t.runAtTick(12,()-> {
            t.assertTrue(t.getWorld().getBlockState(twelve).get(PistonBlock.EXTENDED),"twelve logical pieces extend despite thirteen occupied physical cells");
            piece(t,twelve.getX(),twelve.getZ(),half,2,Blocks.STONE,false);
            for(int n=0;n<12;n++) piece(t,twelve.getX(),twelve.getZ(),half+2+2*n,2,Blocks.STONE,true);
            t.assertFalse(t.getWorld().getBlockState(thirteen).get(PistonBlock.EXTENDED),"thirteenth logical piece rejects extension");
            for(int n=0;n<13;n++) piece(t,thirteen.getX(),thirteen.getZ(),half+2*n,2,Blocks.STONE,true);
            piece(t,thirteen.getX(),thirteen.getZ(),half+26,2,Blocks.STONE,false);t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=45)
    public void movingSupplementalPiecePreservesBothNativeBarrelInventories(TestContext t) {
        var owner=piston(t,2,4,3,false,Direction.EAST);int half=owner.getY()*2-1;
        var source=owner.east();var destination=owner.east(2);var world=t.getWorld();
        var a=source.down();var b=destination.down();
        for(var pos:new BlockPos[]{a,b}) {
            world.setBlockState(pos,Blocks.BARREL.getDefaultState());
            NativeSeats.put(world,pos,new NativeSeats.Seat(-1,"minecraft:barrel"));
        }
        var sourceBarrel=(BarrelBlockEntity)world.getBlockEntity(a);var destinationBarrel=(BarrelBlockEntity)world.getBlockEntity(b);
        sourceBarrel.setStack(0,new ItemStack(Items.DIAMOND,7));destinationBarrel.setStack(0,new ItemStack(Items.EMERALD,5));
        place(t,source.getX(),source.getZ(),half,Blocks.STONE);
        t.assertTrue(SupplementalCells.has(world,a),"source piece genuinely has a supplemental companion beside native inventory");
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"supplemental piece can move above stationary native inventories");
            piece(t,source.getX(),source.getZ(),half,2,Blocks.STONE,false);
            piece(t,destination.getX(),destination.getZ(),half,2,Blocks.STONE,true);
            t.assertTrue(world.getBlockEntity(a)==sourceBarrel && world.getBlockEntity(b)==destinationBarrel,"piston never replaces either native barrel block entity");
            t.assertTrue(sourceBarrel.getStack(0).isOf(Items.DIAMOND) && sourceBarrel.getStack(0).getCount()==7,"source barrel retains seven diamonds");
            t.assertTrue(destinationBarrel.getStack(0).isOf(Items.EMERALD) && destinationBarrel.getStack(0).getCount()==5,"destination barrel retains five emeralds");
            t.assertTrue(!SupplementalCells.has(world,a) && SupplementalCells.has(world,b),"supplemental ownership follows only moving static piece");
            t.assertTrue(NativeSeats.seat(world,a).halfOffset()==-1 && NativeSeats.seat(world,b).halfOffset()==-1,"both immovable native barrel seats remain frozen");t.complete();
        });
    }
}
