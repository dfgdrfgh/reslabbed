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

    private static void stickyCycles(TestContext t,Direction facing) {
        var owner=piston(t,5,5,3,true,facing);var world=t.getWorld();
        if(facing==Direction.DOWN)world.setBlockState(owner.down(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        var source=owner.offset(facing);var destination=owner.offset(facing,2);
        int half=owner.getY()*2-1+facing.getOffsetY()*2;
        int movedHalf=half+facing.getOffsetY()*2;
        place(t,source.getX(),source.getZ(),half,Blocks.STONE);
        for(int cycle=0;cycle<3;cycle++) {
            int start=2+cycle*24;
            t.runAtTick(start,()->power(t,owner,true));
            t.runAtTick(start+1,()->t.assertTrue(world.getBlockEntity(source) instanceof PistonBlockEntity,
                    "each sticky cycle creates an actual vanilla moving head toward "+facing));
            t.runAtTick(start+8,()-> {
                t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"sticky cycle reaches extension toward "+facing);
                piece(t,source.getX(),source.getZ(),half,2,Blocks.STONE,false);
                piece(t,destination.getX(),destination.getZ(),movedHalf,2,Blocks.STONE,true);
                t.assertTrue(world.getBlockState(source).isOf(Blocks.PISTON_HEAD),"settled head retains native identity");
                power(t,owner,false);
            });
            int completed=cycle;
            t.runAtTick(start+18,()-> {
                t.assertTrue(world.getBlockState(owner).isOf(Blocks.STICKY_PISTON)
                        && !world.getBlockState(owner).get(PistonBlock.EXTENDED),"sticky cycle restores native retracted base");
                t.assertTrue(NativeSeats.seat(world,owner)!=null && NativeSeats.seat(world,owner).halfOffset()==-1,
                        "repeated cycles retain the original piston seat");
                piece(t,source.getX(),source.getZ(),half,2,Blocks.STONE,true);
                piece(t,destination.getX(),destination.getZ(),movedHalf,2,Blocks.STONE,false);
                t.assertFalse(world.getBlockState(source).isOf(Blocks.PISTON_HEAD),"retraction leaves no native head residue");
                if(completed==2)t.complete();
            });
        }
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=85)
    public void westStickyPistonRepeatsThreePhysicalCycles(TestContext t) {
        stickyCycles(t,Direction.WEST);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=85)
    public void downStickyPistonRepeatsThreePhysicalCycles(TestContext t) {
        stickyCycles(t,Direction.DOWN);
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
        var owner=piston(t,3,3,3,true,Direction.UP);int half=owner.getY()*2+1;
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
            t.assertTrue(t.getWorld().getBlockState(owner.up()).isOf(Blocks.PISTON_HEAD),"vanilla physical head remains native");
            power(t,owner,false);
        });
        t.runAtTick(24,()-> {
            t.assertFalse(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"vertical sticky piston retracts");
            piece(t,owner.getX(),owner.getZ(),half,2,Blocks.STONE,true);
            piece(t,owner.getX(),owner.getZ(),half+2,2,Blocks.STONE,false);
            t.assertTrue(SupplementalCells.has(t.getWorld(),owner),"returned companion shares the original native piston owner");
            t.complete();
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
        var protectedPos=thirteen.up(14);var world=t.getWorld();
        world.setBlockState(protectedPos,Blocks.BARREL.getDefaultState());
        NativeSeats.put(world,protectedPos,new NativeSeats.Seat(1,"minecraft:barrel"));
        var protectedBarrel=(BarrelBlockEntity)world.getBlockEntity(protectedPos);
        protectedBarrel.setStack(0,new ItemStack(Items.DIAMOND,7));
        var unchanged=new java.util.LinkedHashMap<BlockPos,com.slabbed.core.CellContents<net.minecraft.block.BlockState>>();
        for(int n=0;n<12;n++) place(t,twelve.getX(),twelve.getZ(),half+2*n,Blocks.STONE);
        for(int n=0;n<13;n++) place(t,thirteen.getX(),thirteen.getZ(),half+2*n,Blocks.STONE);
        for(int y=thirteen.getY();y<=protectedPos.getY();y++) {
            var pos=new BlockPos(thirteen.getX(),y,thirteen.getZ());unchanged.put(pos,CellWorld.contents(world,pos));
        }
        t.runAtTick(2,()-> {power(t,twelve,true);power(t,thirteen,true);});
        t.runAtTick(12,()-> {
            t.assertTrue(t.getWorld().getBlockState(twelve).get(PistonBlock.EXTENDED),"twelve logical pieces extend despite thirteen occupied physical cells");
            piece(t,twelve.getX(),twelve.getZ(),half,2,Blocks.STONE,false);
            for(int n=0;n<12;n++) piece(t,twelve.getX(),twelve.getZ(),half+2+2*n,2,Blocks.STONE,true);
            t.assertFalse(t.getWorld().getBlockState(thirteen).get(PistonBlock.EXTENDED),"thirteenth logical piece rejects extension");
            for(int n=0;n<13;n++) piece(t,thirteen.getX(),thirteen.getZ(),half+2*n,2,Blocks.STONE,true);
            piece(t,thirteen.getX(),thirteen.getZ(),half+26,2,Blocks.STONE,false);
            unchanged.forEach((pos,contents)->t.assertTrue(java.util.Objects.equals(contents,CellWorld.contents(world,pos)),
                    "thirteen-piece refusal preserves every stored fragment at "+pos));
            t.assertTrue(world.getBlockEntity(protectedPos)==protectedBarrel,"refusal retains the destination native barrel entity");
            t.assertTrue(protectedBarrel.getStack(0).isOf(Items.DIAMOND) && protectedBarrel.getStack(0).getCount()==7,
                    "refusal retains the destination barrel inventory");
            t.assertTrue(NativeSeats.seat(world,protectedPos)!=null && NativeSeats.seat(world,protectedPos).halfOffset()==1,
                    "refusal retains the destination barrel seat");t.complete();
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
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=45)
    public void obstructedPhysicalPushPreservesFragmentsAndNativeInventories(TestContext t) {
        var owner=piston(t,2,4,3,true,Direction.EAST);var world=t.getWorld();
        var source=owner.east();var destination=owner.east(2);int half=owner.getY()*2-1;
        var a=source.down();var b=destination;
        for(var pos:new BlockPos[]{a,b}) {
            world.setBlockState(pos,Blocks.BARREL.getDefaultState());
            NativeSeats.put(world,pos,new NativeSeats.Seat(-1,"minecraft:barrel"));
        }
        var sourceBarrel=(BarrelBlockEntity)world.getBlockEntity(a);var blockerBarrel=(BarrelBlockEntity)world.getBlockEntity(b);
        sourceBarrel.setStack(0,new ItemStack(Items.DIAMOND,7));blockerBarrel.setStack(0,new ItemStack(Items.EMERALD,5));
        place(t,source.getX(),source.getZ(),half,Blocks.STONE);
        place(t,destination.getX(),destination.getZ(),half-1,Blocks.BRICK_SLAB);
        var unchanged=new java.util.LinkedHashMap<BlockPos,com.slabbed.core.CellContents<net.minecraft.block.BlockState>>();
        for(var pos:new BlockPos[]{a,source,destination.down(),destination})unchanged.put(pos,CellWorld.contents(world,pos));
        t.assertTrue(SupplementalCells.has(world,a),"blocked source has a real supplemental fragment beside its inventory");
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()->power(t,owner,false));
        t.runAtTick(22,()->power(t,owner,true));
        t.runAtTick(34,()-> {
            t.assertFalse(world.getBlockState(owner).get(PistonBlock.EXTENDED),"native obstruction rejects both physical attempts");
            unchanged.forEach((pos,contents)->t.assertTrue(java.util.Objects.equals(contents,CellWorld.contents(world,pos)),
                    "obstruction refusal preserves every stored fragment at "+pos));
            piece(t,source.getX(),source.getZ(),half,2,Blocks.STONE,true);
            piece(t,destination.getX(),destination.getZ(),half,2,Blocks.STONE,false);
            piece(t,destination.getX(),destination.getZ(),half-1,1,Blocks.BRICK_SLAB,true);
            t.assertTrue(world.getBlockEntity(a)==sourceBarrel && world.getBlockEntity(b)==blockerBarrel,
                    "rejected physical attempts preserve both original native barrel entities");
            t.assertTrue(sourceBarrel.getStack(0).isOf(Items.DIAMOND) && sourceBarrel.getStack(0).getCount()==7,
                    "blocked source retains all seven diamonds");
            t.assertTrue(blockerBarrel.getStack(0).isOf(Items.EMERALD) && blockerBarrel.getStack(0).getCount()==5,
                    "native obstruction retains all five emeralds");
            t.assertTrue(NativeSeats.seat(world,a)!=null && NativeSeats.seat(world,a).halfOffset()==-1
                    && NativeSeats.seat(world,b)!=null && NativeSeats.seat(world,b).halfOffset()==-1,
                    "both inventories retain their frozen seats after rejection");
            t.complete();
        });
    }

    private static void adhesive(TestContext t,BlockPos owner,Block block,int offset) {
        t.getWorld().setBlockState(owner,block.getDefaultState(),Block.NOTIFY_LISTENERS);
        NativeSeats.put(t.getWorld(),owner,new NativeSeats.Seat(offset,
                net.minecraft.registry.Registries.BLOCK.getId(block).toString()));
    }

    private static void adhesiveCycle(TestContext t,Block glue) {
        var owner=piston(t,2,4,3,true,Direction.EAST);var world=t.getWorld();
        var source=owner.east();int half=owner.getY()*2-1;
        adhesive(t,source,glue,-1);
        var branch=source.south();place(t,branch.getX(),branch.getZ(),half,Blocks.STONE);
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"adhesive physical branch permits extension");
            t.assertTrue(world.getBlockState(source.east()).isOf(glue),"adhesive retains its native block identity");
            t.assertTrue(NativeSeats.seat(world,source.east())!=null && NativeSeats.seat(world,source.east()).halfOffset()==-1,
                    "moved adhesive retains its physical seat");
            piece(t,branch.getX(),branch.getZ(),half,2,Blocks.STONE,false);
            piece(t,branch.east().getX(),branch.getZ(),half,2,Blocks.STONE,true);
            power(t,owner,false);
        });
        t.runAtTick(26,()-> {
            t.assertFalse(world.getBlockState(owner).get(PistonBlock.EXTENDED),"adhesive branch retracts");
            t.assertTrue(world.getBlockState(source).isOf(glue),"native adhesive returns with sticky pull");
            t.assertTrue(NativeSeats.seat(world,source)!=null && NativeSeats.seat(world,source).halfOffset()==-1,
                    "returned adhesive keeps its original half offset");
            piece(t,branch.getX(),branch.getZ(),half,2,Blocks.STONE,true);
            piece(t,branch.east().getX(),branch.getZ(),half,2,Blocks.STONE,false);t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=45)
    public void loweredSlimePushesAndPullsPhysicalSideBranch(TestContext t) {
        adhesiveCycle(t,Blocks.SLIME_BLOCK);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=45)
    public void loweredHoneyPushesAndPullsPhysicalSideBranch(TestContext t) {
        adhesiveCycle(t,Blocks.HONEY_BLOCK);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void blockedAdhesiveSideBranchPreservesPiecesAndInventory(TestContext t) {
        var owner=piston(t,2,4,3,true,Direction.EAST);var world=t.getWorld();
        var source=owner.east();var branch=source.south();var blocker=branch.east();int half=owner.getY()*2-1;
        adhesive(t,source,Blocks.SLIME_BLOCK,-1);
        place(t,branch.getX(),branch.getZ(),half,Blocks.STONE);
        adhesive(t,blocker,Blocks.BARREL,-1);
        var barrel=(BarrelBlockEntity)world.getBlockEntity(blocker);barrel.setStack(0,new ItemStack(Items.DIAMOND,7));
        var before=CellWorld.contents(world,branch);var companion=CellWorld.contents(world,branch.down());
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertFalse(world.getBlockState(owner).get(PistonBlock.EXTENDED),"blocked adhesive branch refuses whole push");
            t.assertTrue(world.getBlockState(source).isOf(Blocks.SLIME_BLOCK),"refusal leaves native slime in place");
            t.assertTrue(NativeSeats.seat(world,source).halfOffset()==-1,"refusal preserves slime seat");
            t.assertTrue(java.util.Objects.equals(before,CellWorld.contents(world,branch))
                    && java.util.Objects.equals(companion,CellWorld.contents(world,branch.down())),"refusal preserves both exact branch fragments");
            t.assertTrue(world.getBlockEntity(blocker)==barrel && barrel.getStack(0).isOf(Items.DIAMOND)
                    && barrel.getStack(0).getCount()==7,"refusal preserves native barrel identity and inventory");
            t.assertTrue(NativeSeats.seat(world,blocker).halfOffset()==-1,"refusal preserves blocker seat");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void slimeDoesNotDragHoneyOrItsPhysicalBranch(TestContext t) {
        var owner=piston(t,2,4,3,false,Direction.EAST);var world=t.getWorld();var source=owner.east();
        int half=owner.getY()*2-1;adhesive(t,source,Blocks.SLIME_BLOCK,-1);
        var honey=source.south();adhesive(t,honey,Blocks.HONEY_BLOCK,-1);
        var branch=honey.south();place(t,branch.getX(),branch.getZ(),half,Blocks.STONE);
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"slime separates from honey");
            t.assertTrue(world.getBlockState(source.east()).isOf(Blocks.SLIME_BLOCK),"slime moves normally");
            t.assertTrue(world.getBlockState(honey).isOf(Blocks.HONEY_BLOCK) && NativeSeats.seat(world,honey).halfOffset()==-1,
                    "honey keeps native identity and seat");
            piece(t,branch.getX(),branch.getZ(),half,2,Blocks.STONE,true);t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void adhesiveIgnoresPhysicalSidePieceWithoutVerticalOverlap(TestContext t) {
        var owner=piston(t,2,4,3,false,Direction.EAST);var world=t.getWorld();var source=owner.east();
        int half=owner.getY()*2-1;adhesive(t,source,Blocks.SLIME_BLOCK,-1);
        var branch=source.south();place(t,branch.getX(),branch.getZ(),half+2,Blocks.STONE);
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"vertical edge contact does not block movement");
            piece(t,branch.getX(),branch.getZ(),half+2,2,Blocks.STONE,true);
            piece(t,branch.east().getX(),branch.getZ(),half+2,2,Blocks.STONE,false);t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void adhesiveIgnoresImmovableSideNeighborButMovesPhysicalBranch(TestContext t) {
        var owner=piston(t,2,4,3,false,Direction.EAST);var world=t.getWorld();var source=owner.east();
        int half=owner.getY()*2-1;adhesive(t,source,Blocks.SLIME_BLOCK,-1);
        adhesive(t,source.north(),Blocks.OBSIDIAN,-1);
        var branch=source.south();place(t,branch.getX(),branch.getZ(),half,Blocks.STONE);
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"immovable side neighbor does not adhere");
            t.assertTrue(world.getBlockState(source.north()).isOf(Blocks.OBSIDIAN),"obsidian stays in place");
            piece(t,branch.east().getX(),branch.getZ(),half,2,Blocks.STONE,true);t.complete();
        });
    }

    private static void adhesiveLimit(TestContext t,int branchCount,boolean accepted) {
        var owner=piston(t,3,3,3,false,Direction.UP);var world=t.getWorld();var source=owner.up();
        int half=source.getY()*2-1;adhesive(t,source,Blocks.SLIME_BLOCK,-1);
        var branch=source.south();
        for(int y=owner.getY();y<=owner.getY()+16;y++)
            world.setBlockState(new BlockPos(branch.getX(),y,branch.getZ()),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        for(int n=0;n<branchCount;n++)place(t,branch.getX(),branch.getZ(),half+2*n,Blocks.STONE);
        var before=new java.util.LinkedHashMap<BlockPos,com.slabbed.core.CellContents<net.minecraft.block.BlockState>>();
        for(int y=owner.getY();y<=owner.getY()+branchCount+1;y++) {
            var pos=new BlockPos(branch.getX(),y,branch.getZ());before.put(pos,CellWorld.contents(world,pos));
        }
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(14,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED)==accepted,
                    "native adhesive counts once toward the twelve whole-piece limit");
            if(accepted) {
                t.assertTrue(world.getBlockState(source.up()).isOf(Blocks.SLIME_BLOCK),"twelve-piece branch moves native slime");
                piece(t,branch.getX(),branch.getZ(),half,2,Blocks.STONE,false);
                for(int n=0;n<branchCount;n++)piece(t,branch.getX(),branch.getZ(),half+2+2*n,2,Blocks.STONE,true);
            } else {
                t.assertTrue(world.getBlockState(source).isOf(Blocks.SLIME_BLOCK),"thirteen-piece refusal leaves native slime in place");
                before.forEach((pos,contents)->t.assertTrue(java.util.Objects.equals(contents,CellWorld.contents(world,pos)),
                        "thirteen-piece adhesion refusal preserves exact branch contents"));
            }
            t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void nativeSlimeAndElevenPhysicalBranchPiecesCanMove(TestContext t) {
        adhesiveLimit(t,11,true);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void nativeSlimeAndTwelvePhysicalBranchPiecesRejectWithoutChanges(TestContext t) {
        adhesiveLimit(t,12,false);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void nativeGraphMismatchRestoresRemovedPhysicalBranch(TestContext t) {
        var owner=piston(t,2,4,3,false,Direction.EAST);var world=t.getWorld();var source=owner.east();
        int half=owner.getY()*2-1;adhesive(t,source,Blocks.SLIME_BLOCK,-1);
        var unrelated=source.north();adhesive(t,unrelated,Blocks.SLIME_BLOCK,1);
        var branch=source.south();place(t,branch.getX(),branch.getZ(),half,Blocks.STONE);
        var before=CellWorld.contents(world,branch);var companion=CellWorld.contents(world,branch.down());
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(14,()-> {
            t.assertFalse(world.getBlockState(owner).get(PistonBlock.EXTENDED),"different vanilla and physical graphs refuse safely");
            t.assertTrue(world.getBlockState(source).isOf(Blocks.SLIME_BLOCK) && NativeSeats.seat(world,source).halfOffset()==-1,
                    "mismatch preserves source slime and seat");
            t.assertTrue(world.getBlockState(unrelated).isOf(Blocks.SLIME_BLOCK) && NativeSeats.seat(world,unrelated).halfOffset()==1,
                    "mismatch cannot move a noncontacting native neighbor");
            t.assertTrue(java.util.Objects.equals(before,CellWorld.contents(world,branch))
                    && java.util.Objects.equals(companion,CellWorld.contents(world,branch.down())),"mismatch restores both removed branch fragments");
            piece(t,branch.east().getX(),branch.getZ(),half,2,Blocks.STONE,false);t.complete();
        });
    }

    private static void unseatedAxialCycle(TestContext t,Block glue) {
        var world=t.getWorld();var owner=t.getAbsolutePos(new BlockPos(2,4,3));var source=owner.east();
        world.setBlockState(owner,Blocks.STICKY_PISTON.getDefaultState().with(PistonBlock.FACING,Direction.EAST));
        world.setBlockState(source,glue.getDefaultState());
        int half=owner.getY()*2+1;var branch=source.east();
        place(t,branch.getX(),branch.getZ(),half,Blocks.STONE);
        t.assertTrue(NativeSeats.seat(world,source)==null,"ordinary native glue starts without a saved seat");
        t.runAtTick(2,()->power(t,owner,true));
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(owner).get(PistonBlock.EXTENDED),"ordinary glue pushes crossing axial branch");
            t.assertTrue(world.getBlockState(source.east()).isOf(glue),"ordinary native glue moves to axial branch source");
            piece(t,branch.east().getX(),branch.getZ(),half,2,Blocks.STONE,true);
            power(t,owner,false);
        });
        t.runAtTick(26,()-> {
            t.assertFalse(world.getBlockState(owner).get(PistonBlock.EXTENDED),"ordinary glue retracts with axial branch");
            t.assertTrue(world.getBlockState(source).isOf(glue),"ordinary glue returns to original position");
            piece(t,branch.getX(),branch.getZ(),half,2,Blocks.STONE,true);
            piece(t,branch.east().getX(),branch.getZ(),half,2,Blocks.STONE,false);t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=45)
    public void unseatedSlimePushesAndPullsAxialPhysicalBranch(TestContext t) {
        unseatedAxialCycle(t,Blocks.SLIME_BLOCK);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=45)
    public void unseatedHoneyPushesAndPullsAxialPhysicalBranch(TestContext t) {
        unseatedAxialCycle(t,Blocks.HONEY_BLOCK);
    }

}
