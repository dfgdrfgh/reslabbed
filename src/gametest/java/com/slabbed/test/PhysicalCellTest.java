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
        var p=t.getAbsolutePos(new BlockPos(2,24,2)); var w=t.getWorld();
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
            t.assertTrue(ray.getType()==HitResult.Type.BLOCK && Math.abs(ray.getPos().y-top)<1e-6,"vanilla ray hit physical top "+n+" expected="+top+" hit="+ray.getPos()+" state="+w.getBlockState(pos.up())+" below="+w.getBlockState(pos)+" cells="+CellWorld.contents(w,pos));
            t.assertTrue(!w.isSpaceEmpty(new Box(p.getX()+.2,top-.2,p.getZ()+.2,p.getX()+.8,top-.01,p.getZ()+.8)),"standing collision "+n);
        }
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void externalRemovalClearsTheOtherFragment(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2)); var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState());
        w.setBlockState(p.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        t.assertTrue(w.getBlockState(p).isOf(Blocks.OAK_SLAB),"external cap removal restored native support");
        t.assertTrue(w.getBlockState(p.up()).isAir(),"external removal stayed removed");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void physicalCellNbtRoundTrip(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2)); var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState());
        var original=(CellBlockEntity)w.getBlockEntity(p);
        var copy=new CellBlockEntity(p,w.getBlockState(p));
        copy.read(original.createNbt(w.getRegistryManager()),w.getRegistryManager());
        t.assertTrue(original.contents().equals(copy.contents()),"NBT retained exact physical fragments");
        t.assertTrue(original.outline().getBoundingBox().equals(copy.outline().getBoundingBox()),"NBT retained outline");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void shiftedTorchLosesRemovedSupport(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2));var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.TORCH.getDefaultState());
        CellWorld.remove(w,p,0,null,false);
        t.assertTrue(w.getBlockState(p).isAir() && w.getBlockState(p.up()).isAir(),"unsupported torch removed both fragments");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void externalReplacementDoesNotDeleteReplacement(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2));var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState());
        w.setBlockState(p,Blocks.BRICK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
        t.assertTrue(w.getBlockState(p).isOf(Blocks.BRICK_SLAB),"replacement remained intact");
        t.assertTrue(w.getBlockState(p.up()).isAir(),"old cap cleared");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void shiftedMaterialKeepsVanillaMiningSpeed(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2));var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.IRON_BLOCK.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(p.getX()+.5,p.getY()+2,p.getZ()-2);
        float vanilla=Blocks.IRON_BLOCK.getDefaultState().calcBlockBreakingDelta(player,w,p);
        float shifted=w.getBlockState(p).calcBlockBreakingDelta(player,w,p);
        t.assertTrue(Math.abs(vanilla-shifted)<1e-7,"carrier used vanilla material mining speed: "+vanilla+" vs "+shifted);t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void shiftedPotCanBePlantedAndEmptied(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2));var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.FLOWER_POT.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DANDELION));
        var hit=new BlockHitResult(new Vec3d(p.getX()+.5,p.getY()+.875,p.getZ()+.5),Direction.UP,p,false);
        t.assertTrue(com.slabbed.cell.CellInteractions.use(player,w,Hand.MAIN_HAND,hit).isAccepted(),"pot planted");
        t.assertTrue(player.getMainHandStack().isEmpty(),"plant consumed once");
        t.assertTrue(CellWorld.contents(w,p).fragments().stream().anyMatch(f->f.value().isOf(Blocks.POTTED_DANDELION)),"potted state saved");
        t.assertTrue(com.slabbed.cell.CellInteractions.use(player,w,Hand.MAIN_HAND,hit).isAccepted(),"pot emptied");
        t.assertTrue(player.getMainHandStack().isOf(Items.DANDELION) && player.getMainHandStack().getCount()==1,"one plant returned");
        t.assertTrue(CellWorld.contents(w,p).fragments().stream().anyMatch(f->f.value().isOf(Blocks.FLOWER_POT)),"empty state saved");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void carrierPreservesMaterialExplosionResistance(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2));var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.OBSIDIAN.getDefaultState());
        float actual=new net.minecraft.world.explosion.ExplosionBehavior().getBlastResistance(null,w,p,w.getBlockState(p),w.getFluidState(p)).orElseThrow();
        t.assertTrue(actual==Blocks.OBSIDIAN.getBlastResistance(),"obsidian kept blast resistance");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void externalBreakDropsThePieceOnce(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2));var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());
        CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.IRON_BLOCK.getDefaultState());
        int expected=Block.getDroppedStacks(Blocks.IRON_BLOCK.getDefaultState(),w,p,null).stream().mapToInt(ItemStack::getCount).sum();
        w.breakBlock(p.up(),true);
        int dropped=w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(p).expand(3),e->true).stream().mapToInt(e->e.getStack().getCount()).sum();
        t.assertTrue(expected>0 && dropped==expected,"external break dropped once: "+dropped+" expected="+expected);
        t.assertTrue(w.getBlockState(p).isOf(Blocks.OAK_SLAB) && w.getBlockState(p.up()).isAir(),"external break left no cap or ghost piece");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void blockedPlacementPreservesInventory(TestContext t) {
        var p=t.getAbsolutePos(new BlockPos(2,2,2));var w=t.getWorld();
        w.setBlockState(p,Blocks.OAK_SLAB.getDefaultState());w.setBlockState(p.up(),Blocks.CHEST.getDefaultState());
        var inventory=(net.minecraft.inventory.Inventory)w.getBlockEntity(p.up());inventory.setStack(0,new ItemStack(Items.DIAMOND,3));
        t.assertFalse(CellWorld.place(w,p.getX(),p.getZ(),p.getY()*2+1,Blocks.STONE.getDefaultState()),"occupied inventory cell rejected");
        t.assertTrue(w.getBlockEntity(p.up())==inventory && inventory.getStack(0).getCount()==3,"inventory preserved");
        t.assertTrue(w.getBlockState(p).isOf(Blocks.OAK_SLAB),"slab preserved");t.complete();
    }

    private static BlockPos sideFixture(TestContext t,boolean nativeCube) {
        var world=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(3,3,3));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=2;y++) {
            var clear=pos.add(x,y,z);
            com.slabbed.nativeblock.NativeSeats.remove(world,clear);
            world.setBlockState(clear,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        world.setBlockState(pos,Blocks.STONE_SLAB.getDefaultState(),Block.NOTIFY_LISTENERS);
        if(nativeCube) {
            world.setBlockState(pos.up(),Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);
            com.slabbed.nativeblock.NativeSeats.put(world,pos.up(),new com.slabbed.nativeblock.NativeSeats.Seat(-1,"minecraft:grass_block"));
        } else t.assertTrue(CellWorld.place(world,pos.getX(),pos.getZ(),pos.getY()*2+1,Blocks.STONE.getDefaultState()),"crossing side support placed");
        return pos;
    }
    private static net.minecraft.util.ActionResult sideUse(net.minecraft.entity.player.PlayerEntity player,BlockPos base,Direction face,boolean upper,boolean nativeCube) {
        var point=Vec3d.ofBottomCenter(base).add(face.getOffsetX()*.5,upper?1.25:.75,face.getOffsetZ()*.5);
        var owner=nativeCube?base.up():BlockPos.ofFloored(point.subtract(Vec3d.of(face.getVector()).multiply(1e-5)));
        return player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(point,face,owner,false)));
    }
    private static net.minecraft.entity.player.PlayerEntity sideBuilder(TestContext t,BlockPos base) {
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY()+3,base.getZ()-3);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.OAK_SLAB,4));return player;
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void sideSlabUsesAimedPhysicalHalf(TestContext t) {
        for(boolean nativeCube:new boolean[]{false,true})for(var face:Direction.Type.HORIZONTAL)for(boolean upper:new boolean[]{false,true}) {
            var pos=sideFixture(t,nativeCube);var world=t.getWorld();var player=sideBuilder(t,pos);
            t.assertTrue(sideUse(player,pos,face,upper,nativeCube).isAccepted(),"side slab accepted: native="+nativeCube+" face="+face+" upper="+upper);
            int origin=pos.getY()*2+1+(upper?1:0);var target=new BlockPos(pos.getX()+face.getOffsetX(),Math.floorDiv(origin,2),pos.getZ()+face.getOffsetZ());
            var contents=CellWorld.contents(world,target);
            t.assertTrue(contents!=null && contents.fragments().stream().anyMatch(p->p.offset()==origin-target.getY()*2 && p.length()==1 && p.value().isOf(Blocks.OAK_SLAB)),
                    "side slab must occupy aimed half: native="+nativeCube+" face="+face+" upper="+upper+" expectedOrigin="+origin+" actual="+contents);
            t.assertTrue(player.getMainHandStack().getCount()==3,"side placement consumes once");
        }
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void sideSlabsFillOneDoubleInEitherOrder(TestContext t) {
        for(boolean nativeCube:new boolean[]{false,true})for(var face:Direction.Type.HORIZONTAL)for(boolean upperFirst:new boolean[]{false,true}) {
            var pos=sideFixture(t,nativeCube);var world=t.getWorld();var player=sideBuilder(t,pos);
            t.assertTrue(sideUse(player,pos,face,upperFirst,nativeCube).isAccepted(),"first side half accepted");
            t.assertTrue(sideUse(player,pos,face,!upperFirst,nativeCube).isAccepted(),"opposite side half must complete slab: native="+nativeCube+" face="+face+" upperFirst="+upperFirst);
            var target=pos.offset(face);
            for(int y=0;y<=1;y++) {
                var contents=CellWorld.contents(world,target.up(y));
                t.assertTrue(contents!=null && contents.fragments().size()==1 && contents.fragments().getFirst().offset()==1-y*2
                        && contents.fragments().getFirst().length()==2 && contents.fragments().getFirst().value().get(SlabBlock.TYPE)==SlabType.DOUBLE,"side pair retains one double at its original origin");
            }
            t.assertTrue(player.getMainHandStack().getCount()==2,"two side halves consume two slabs");
            if(nativeCube)t.assertTrue(com.slabbed.nativeblock.NativeSeats.offset(world,pos.up())==-.5,"side placement preserves native support pose");
        }
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void sideSlabPairPreservesObstructionAndInventory(TestContext t) {
        var pos=sideFixture(t,true);var world=t.getWorld();var player=sideBuilder(t,pos);var target=pos.east();
        t.assertTrue(sideUse(player,pos,Direction.EAST,false,true).isAccepted(),"lower side half accepted");
        var before=CellWorld.contents(world,target);
        world.setBlockState(target.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
        t.assertFalse(sideUse(player,pos,Direction.EAST,true,true).isAccepted(),"occupied upper half rejected");
        t.assertTrue(before.equals(CellWorld.contents(world,target)) && world.getBlockState(target.up()).isOf(Blocks.STONE),"failed side pair preserves both pieces");
        t.assertTrue(player.getMainHandStack().getCount()==3,"failed side pair consumes nothing");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void differentSideSlabMaterialsRemainIndependent(TestContext t) {
        var pos=sideFixture(t,false);var world=t.getWorld();var player=sideBuilder(t,pos);
        t.assertTrue(sideUse(player,pos,Direction.EAST,false,false).isAccepted(),"lower oak side half accepted");
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BIRCH_SLAB,2));
        t.assertTrue(sideUse(player,pos,Direction.EAST,true,false).isAccepted(),"upper birch side half accepted");
        t.assertTrue(world.getBlockState(pos.east()).isOf(Blocks.OAK_SLAB) && world.getBlockState(pos.east().up()).isOf(Blocks.BIRCH_SLAB),"different side halves remain separate materials");
        t.assertTrue(player.getMainHandStack().getCount()==1,"upper birch consumes once");t.complete();
    }
}
