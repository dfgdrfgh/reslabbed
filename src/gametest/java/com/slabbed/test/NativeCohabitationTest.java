package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Independent physical pieces can share a native owner's unused volume without losing native data. */
public final class NativeCohabitationTest {
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void matchingSlabsAboveLoweredGrassBecomeOneDoubleSlab(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));var owner=base.up();
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()-3,base.getY(),base.getZ()+.5);
        t.assertTrue(place(player,Items.GRASS_BLOCK,base,base.getY()+.5).isAccepted(),"grass placed on bottom slab");
        var seat=NativeSeats.seat(world,owner);
        t.assertTrue(place(player,Items.OAK_SLAB,owner,base.getY()+1.5).isAccepted(),"first slab shares grass owner");
        t.assertTrue(place(player,Items.OAK_SLAB,owner,base.getY()+2).isAccepted(),"second matching slab placed");
        t.assertTrue(player.getMainHandStack().getCount()==1,"second slab consumes exactly one item");
        var parts=CellWorld.contents(world,owner);
        t.assertTrue(parts!=null && parts.fragments().size()==1,"one logical slab piece in shared owner");
        var part=parts.fragments().getFirst();
        t.assertTrue(part.offset()==1 && part.length()==2
                && part.value().get(net.minecraft.block.SlabBlock.TYPE)==net.minecraft.block.enums.SlabType.DOUBLE,
                "matching supplemental slabs must become one double slab at their original bottom: "+parts);
        var cap=CellWorld.contents(world,owner.up());
        t.assertTrue(cap!=null && cap.fragments().size()==1 && cap.fragments().getFirst().offset()==-1
                && cap.fragments().getFirst().value().equals(part.value()),"double slab has matching upper companion");
        var entity=(com.slabbed.cell.CellBlockEntity)world.getBlockEntity(owner.up());
        var copy=new com.slabbed.cell.CellBlockEntity(owner.up(),world.getBlockState(owner.up()));
        copy.read(entity.createNbt(world.getRegistryManager()),world.getRegistryManager());
        t.assertTrue(copy.contents().equals(entity.contents()),"crossing double slab survives saved cell data");
        CellWorld.remove(world,owner.up(),-1,player,true);
        int drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(owner).expand(2),
                e->e.getStack().isOf(Items.OAK_SLAB)).stream().mapToInt(e->e.getStack().getCount()).sum();
        t.assertTrue(drops==2,"breaking either half drops both slab items exactly once: "+drops);
        t.assertTrue(CellWorld.contents(world,owner).fragments().isEmpty() && world.getBlockState(owner.up()).isAir(),"breaking double removes both halves");
        t.assertTrue(world.getBlockState(owner).isOf(Blocks.GRASS_BLOCK) && seat.equals(NativeSeats.seat(world,owner))
                && world.getBlockState(base).isOf(Blocks.OAK_SLAB),"double placement and breaking preserve grass pose and original support");
        t.complete();
    }
    private record Fixture(BlockPos base,PlayerEntity player,BarrelBlockEntity barrel,NativeSeats.Seat seat) {}
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void supplementalSlabPairRejectsBlockedCapWithoutConsumingItem(TestContext t) {
        var f=barrel(t);var world=t.getWorld();var owner=f.base().up();
        t.assertTrue(place(f.player(),Items.OAK_SLAB,owner,owner.getY()+.5).isAccepted(),"first supplemental slab placed");
        var before=CellWorld.contents(world,owner);
        world.setBlockState(owner.up(),Blocks.OBSIDIAN.getDefaultState());
        t.assertFalse(place(f.player(),Items.OAK_SLAB,owner,owner.getY()+1).isAccepted(),"obstructed second half rejected");
        t.assertTrue(f.player().getMainHandStack().getCount()==2 && before.equals(CellWorld.contents(world,owner)),"rejected pair preserves first slab and item");
        t.assertTrue(world.getBlockState(owner.up()).isOf(Blocks.OBSIDIAN),"obstruction preserved");
        nativeIntact(t,f);t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void differentSlabMaterialsRemainIndependentAboveNativeOwner(TestContext t) {
        var f=barrel(t);var owner=f.base().up();var world=t.getWorld();
        t.assertTrue(place(f.player(),Items.OAK_SLAB,owner,owner.getY()+.5).isAccepted(),"oak slab placed");
        t.assertTrue(place(f.player(),Items.BIRCH_SLAB,owner,owner.getY()+1).isAccepted(),"different slab placed above oak");
        CellWorld.remove(world,owner.up(),0,f.player(),false);
        piece(t,owner,Blocks.OAK_SLAB,owner.getY()*2+1);
        t.assertTrue(world.getBlockState(owner.up()).isAir(),"only birch slab removed");
        nativeIntact(t,f);t.complete();
    }
    private static ActionResult place(PlayerEntity player,Item item,BlockPos hitCell,double y) {
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item,2));
        var hit=new BlockHitResult(new Vec3d(hitCell.getX()+.5,y,hitCell.getZ()+.5),Direction.UP,hitCell,false);
        return player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
    }
    private static Fixture barrel(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);player.setYaw(0);
        t.assertTrue(place(player,Items.BARREL,base,base.getY()+.5).isAccepted(),"real barrel item placed on bottom slab");
        t.assertTrue(world.getBlockEntity(base.up()) instanceof BarrelBlockEntity,"barrel retains native block entity");
        var barrel=(BarrelBlockEntity)world.getBlockEntity(base.up());barrel.setStack(0,new ItemStack(Items.DIAMOND,7));
        var seat=NativeSeats.seat(world,base.up());
        t.assertTrue(seat!=null && seat.halfOffset()==-1,"barrel remembers exact half seat");
        return new Fixture(base,player,barrel,seat);
    }
    private static void piece(TestContext t,BlockPos cell,Block block,int origin) {
        var contents=CellWorld.contents(t.getWorld(),cell);
        t.assertTrue(contents!=null && contents.fragments().stream().anyMatch(p->p.value().isOf(block)
                && cell.getY()*2+p.offset()==origin),"physical "+block+" has exact origin "+origin+" at "+cell);
    }
    private static void nativeIntact(TestContext t,Fixture f) {
        t.assertTrue(t.getWorld().getBlockState(f.base().up()).isOf(Blocks.BARREL)
                && t.getWorld().getBlockEntity(f.base().up())==f.barrel(),"static edit preserves actual native barrel and entity identity");
        t.assertTrue(f.barrel().getStack(0).isOf(Items.DIAMOND) && f.barrel().getStack(0).getCount()==7,"static edit preserves barrel inventory");
        t.assertTrue(f.seat().equals(NativeSeats.seat(t.getWorld(),f.base().up())),"static edit preserves frozen barrel seat");
    }
    private static void stack(TestContext t,Fixture f) {
        var base=f.base();
        t.assertTrue(place(f.player(),Items.STONE,base.up(),base.getY()+1.5).isAccepted(),"full stone item fills free physical half of native barrel owner");
        piece(t,base.up(),Blocks.STONE,base.getY()*2+3);piece(t,base.up(2),Blocks.STONE,base.getY()*2+3);
        t.assertTrue(place(f.player(),Items.BIRCH_SLAB,base.up(2),base.getY()+2.5).isAccepted(),"real slab item continues stack from shifted stone");
        piece(t,base.up(2),Blocks.BIRCH_SLAB,base.getY()*2+5);
        t.assertTrue(place(f.player(),Items.GRANITE,base.up(2),base.getY()+3).isAccepted(),"full item continues stack from supplemental slab");
        t.assertTrue(t.getWorld().getBlockState(base.up(3)).isOf(Blocks.GRANITE),"integer-height full piece canonicalizes at exact stack origin");
        nativeIntact(t,f);
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void realFullSlabFullStackSharesNativeBarrelOwner(TestContext t) {
        var f=barrel(t);stack(t,f);t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void removingStaticCrossingPiecePreservesNativeBarrelAndOtherPieces(TestContext t) {
        var f=barrel(t);stack(t,f);var base=f.base();
        CellWorld.remove(t.getWorld(),base.up(),1,f.player(),false);
        nativeIntact(t,f);
        var local=CellWorld.contents(t.getWorld(),base.up());
        t.assertTrue(local==null || local.fragments().stream().noneMatch(p->p.value().isOf(Blocks.STONE)),"removed static origin leaves no owner-cell fragment");
        piece(t,base.up(2),Blocks.BIRCH_SLAB,base.getY()*2+5);
        t.assertTrue(t.getWorld().getBlockState(base.up(3)).isOf(Blocks.GRANITE),"independent full piece survives static removal");
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void breakingNativeOwnerPromotesSurvivingStaticFragmentsWithoutMovingThem(TestContext t) {
        var f=barrel(t);stack(t,f);var base=f.base();
        t.getWorld().breakBlock(base.up(),false);
        t.assertFalse(t.getWorld().getBlockState(base.up()).isOf(Blocks.BARREL),"native owner removed");
        t.assertTrue(NativeSeats.seat(t.getWorld(),base.up())==null,"removed native owner clears its seat");
        piece(t,base.up(),Blocks.STONE,base.getY()*2+3);piece(t,base.up(2),Blocks.STONE,base.getY()*2+3);
        piece(t,base.up(2),Blocks.BIRCH_SLAB,base.getY()*2+5);
        t.assertTrue(t.getWorld().getBlockState(base.up(3)).isOf(Blocks.GRANITE),"native removal preserves independent full piece");
        t.complete();
    }
    private static void rejectedPairedPlacement(TestContext t,Item item,BlockPos obstructed) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));var foot=base.up();
        world.setBlockState(base,Blocks.STONE.getDefaultState());world.setBlockState(base.south(),Blocks.STONE.getDefaultState());
        world.setBlockState(obstructed,Blocks.LIGHT.getDefaultState(),Block.NOTIFY_LISTENERS);
        NativeSeats.put(world,obstructed,new NativeSeats.Seat(-1,"minecraft:light"));
        int origin=obstructed.getY()*2+1;
        t.assertTrue(CellWorld.place(world,obstructed.getX(),obstructed.getZ(),origin,Blocks.BIRCH_SLAB.getDefaultState()),"static slab occupies replaceable native member's free physical half");
        var player=t.createMockPlayer(GameMode.SURVIVAL);player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);player.setYaw(0);
        t.assertFalse(place(player,item,base,base.getY()+1).isAccepted(),"paired native placement rejects existing supplemental geometry");
        t.assertTrue(player.getMainHandStack().getCount()==2,"rejected paired placement consumes no item");
        t.assertTrue(world.getBlockState(foot).isAir(),"rejected paired placement leaves no first member");
        t.assertTrue(world.getBlockState(obstructed).isOf(Blocks.LIGHT),"rejected paired placement preserves outgoing native member");
        piece(t,obstructed,Blocks.BIRCH_SLAB,origin);t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void bedHeadCandidateRejectsExistingSupplementalGeometry(TestContext t) {
        rejectedPairedPlacement(t,Items.RED_BED,t.getAbsolutePos(new BlockPos(3,4,4)));
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void doorUpperCandidateRejectsExistingSupplementalGeometry(TestContext t) {
        rejectedPairedPlacement(t,Items.OAK_DOOR,t.getAbsolutePos(new BlockPos(3,5,3)));
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=100)
    public void supplementalLightEmitsFromReservedNativeOwnerCell(TestContext t) {
        var f=barrel(t);var base=f.base();
        t.assertTrue(place(f.player(),Items.GLOWSTONE,base.up(),base.getY()+1.5).isAccepted(),"real glowstone fills native free half");
        var control=base.add(3,1,0);
        t.getWorld().setBlockState(control,Blocks.GLOWSTONE.getDefaultState(),Block.NOTIFY_ALL);
        t.runAtTick(10,()->System.out.println("SUPPLEMENTAL_LIGHT_DIAG source="
                +com.slabbed.cell.SupplementalCells.luminance(t.getWorld().getWorldChunk(base.up()),base.up())
                +" supplemental="+t.getWorld().getLightLevel(net.minecraft.world.LightType.BLOCK,base.up())
                +" nativeControl="+t.getWorld().getLightLevel(net.minecraft.world.LightType.BLOCK,control)));
        t.addInstantFinalTask(()-> {
            t.assertTrue(t.getWorld().getLightLevel(net.minecraft.world.LightType.BLOCK,control)==15,"vanilla light control propagates");
            t.assertTrue(t.getWorld().getLightLevel(net.minecraft.world.LightType.BLOCK,base.up())==15,"supplemental source emits full block light in its actual storage cell");
            nativeIntact(t,f);
        });
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void supplementalMiningUsesSelectedStoneHardness(TestContext t) {
        var f=barrel(t);var base=f.base();stack(t,f);var owner=base.up();var player=f.player();
        player.setPosition(base.getX()+.5,base.getY()+.13,base.getZ()-2.5);player.setYaw(0);player.setPitch(0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        var ray=player.raycast(player.getBlockInteractionRange(),1,false);
        System.out.println("SUPPLEMENTAL_MINING_RAY eye="+player.getEyePos()+" direction="+player.getRotationVec(1)+" ray="+ray+" owner="+owner+" shape="+t.getWorld().getBlockState(owner).getOutlineShape(t.getWorld(),owner).getBoundingBoxes());
        t.assertTrue(ray instanceof BlockHitResult hit && hit.getBlockPos().equals(owner)
                && CellWorld.pick(t.getWorld(),owner,hit)!=null,"physical view targets supplemental stone above native barrel");
        float expected=Blocks.STONE.getDefaultState().calcBlockBreakingDelta(player,t.getWorld(),owner);
        float actual=t.getWorld().getBlockState(owner).calcBlockBreakingDelta(player,t.getWorld(),owner);
        t.assertTrue(Math.abs(actual-expected)<1e-7,"mining selected static stone uses its own tool speed and hardness");
        nativeIntact(t,f);t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void sameMaterialSupplementalMiningTerminatesWithNativeHardness(TestContext t) {
        var world=t.getWorld();var owner=t.getAbsolutePos(new BlockPos(3,4,3));var control=owner.east(3);
        world.setBlockState(owner,Blocks.STONE.getDefaultState());world.setBlockState(control,Blocks.STONE.getDefaultState());
        NativeSeats.put(world,owner,new NativeSeats.Seat(-1,"minecraft:stone"));
        t.assertTrue(CellWorld.place(world,owner.getX(),owner.getZ(),owner.getY()*2+1,Blocks.STONE.getDefaultState()),"matching stone piece occupies unused native half");
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(owner.getX()+.5,owner.getY()-.87,owner.getZ()-2.5);player.setYaw(0);player.setPitch(0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        var ray=player.raycast(player.getBlockInteractionRange(),1,false);
        t.assertTrue(ray instanceof BlockHitResult hit && hit.getBlockPos().equals(owner)
                && CellWorld.pick(world,owner,hit)!=null,"ray selects matching supplemental material");
        float expected=world.getBlockState(control).calcBlockBreakingDelta(player,world,control);
        float actual;
        try {actual=world.getBlockState(owner).calcBlockBreakingDelta(player,world,owner);}
        catch(StackOverflowError recursion) {t.assertTrue(false,"same-material mining must terminate without recursive delegation");return;}
        t.assertTrue(Math.abs(actual-expected)<1e-7,"matching supplemental material retains native hardness");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void supplementalWallTorchLosesPhysicalSupportWithoutRemovingBarrel(TestContext t) {
        var f=barrel(t);var base=f.base();var support=base.east().up();int origin=base.getY()*2+3;
        t.assertTrue(CellWorld.place(t.getWorld(),support.getX(),support.getZ(),origin,Blocks.STONE.getDefaultState()),"physical side support spans native owner's unused upper half");
        f.player().setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.TORCH,2));
        var point=new Vec3d(support.getX(),base.getY()+1.75,support.getZ()+.5);
        var hit=new BlockHitResult(point,Direction.WEST,support,false);
        t.assertTrue(f.player().getMainHandStack().useOnBlock(new ItemUsageContext(f.player(),Hand.MAIN_HAND,hit)).isAccepted(),"real wall torch shares native barrel owner's free volume");
        piece(t,base.up(),Blocks.WALL_TORCH,origin);
        CellWorld.remove(t.getWorld(),support,1,f.player(),false);
        var contents=CellWorld.contents(t.getWorld(),base.up());
        t.assertTrue(contents==null || contents.fragments().stream().noneMatch(p->p.value().isOf(Blocks.WALL_TORCH)),"support loss removes supplemental torch through ordinary neighbor update");
        nativeIntact(t,f);t.complete();
    }

}
