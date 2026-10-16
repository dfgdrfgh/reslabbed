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
    private static net.minecraft.entity.player.PlayerEntity builder(TestContext t,BlockPos base,net.minecraft.item.Item item,int count) {
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);player.setYaw(0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item,count));return player;
    }
    private static net.minecraft.util.ActionResult place(net.minecraft.entity.player.PlayerEntity player,BlockPos base,Direction direction,double y) {
        var hit=new BlockHitResult(new Vec3d(base.getX()+.5,y,base.getZ()+.5),direction,base,false);
        return player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void nativeBedSharesOneFrozenSeatAndSleepsAtPhysicalHeight(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        world.setBlockState(base.south(),Blocks.OAK_SLAB.getDefaultState());
        var player=builder(t,base,Items.RED_BED,2);
        t.assertTrue(place(player,base,Direction.UP,base.getY()+.5).isAccepted(),"native bed placed");
        var foot=base.up();var head=foot.south();
        t.assertTrue(world.getBlockState(foot).isOf(Blocks.RED_BED) && world.getBlockState(head).isOf(Blocks.RED_BED),"both native bed halves exist");
        var seat=com.slabbed.nativeblock.NativeSeats.seat(world,foot);
        t.assertTrue(seat!=null && seat.halfOffset()==-1 && seat.equals(com.slabbed.nativeblock.NativeSeats.seat(world,head)),"bed halves share frozen seat");
        t.assertFalse(world.isSpaceEmpty(new net.minecraft.util.math.Box(base.getX()+.3,base.getY()+.65,base.getZ()+.3,base.getX()+.7,base.getY()+.8,base.getZ()+.7)),"bed collision lives above slab");
        t.assertTrue(world.isSpaceEmpty(new net.minecraft.util.math.Box(base.getX()+.3,base.getY()+1.2,base.getZ()+.3,base.getX()+.7,base.getY()+1.4,base.getZ()+.7)),"native bed cell has no ghost mattress");
        player.sleep(head);
        t.assertTrue(Math.abs(player.getY()-(head.getY()+.1875))<1e-7,"sleeper rests on physical bed: "+player.getY());
        player.wakeUp();
        world.setBlockState(base.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        world.removeBlock(base.east(),false);
        t.assertTrue(seat.equals(com.slabbed.nativeblock.NativeSeats.seat(world,foot)) && seat.equals(com.slabbed.nativeblock.NativeSeats.seat(world,head)),"neighbor edits do not change bed seat");
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void blockedPhysicalBedHeadLeavesNoFootOrConsumedItem(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        world.setBlockState(base.south(),Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE,net.minecraft.block.enums.SlabType.TOP));
        var player=builder(t,base,Items.BLUE_BED,2);
        t.assertFalse(place(player,base,Direction.UP,base.getY()+.5).isAccepted(),"physical head obstruction rejected");
        t.assertTrue(world.getBlockState(base.up()).isAir() && world.getBlockState(base.south().up()).isAir(),"failed bed wrote neither owner");
        t.assertTrue(player.getMainHandStack().getCount()==2,"failed bed consumed nothing");
        t.assertTrue(com.slabbed.nativeblock.NativeSeats.seat(world,base.up())==null,"failed bed stored no seat");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void hangingSignFitsUnderTopSlabWithoutReplacingSupport(TestContext t) {
        var world=t.getWorld();var ceiling=t.getAbsolutePos(new BlockPos(3,5,3));
        var slab=Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE,net.minecraft.block.enums.SlabType.TOP);
        world.setBlockState(ceiling,slab);
        var player=builder(t,ceiling.down(2),Items.OAK_HANGING_SIGN,2);
        t.assertTrue(place(player,ceiling,Direction.DOWN,ceiling.getY()+.5).isAccepted(),"hanging sign placed under physical underside");
        var owner=ceiling.down();
        t.assertTrue(world.getBlockEntity(owner) instanceof net.minecraft.block.entity.HangingSignBlockEntity,"hanging sign remains native");
        t.assertTrue(world.getBlockState(ceiling)==slab,"ceiling slab retained");
        var seat=com.slabbed.nativeblock.NativeSeats.seat(world,owner);
        t.assertTrue(seat!=null && seat.halfOffset()==1,"ceiling seat is bounded positive half");
        var raw=com.slabbed.nativeblock.NativeShapes.outline(world,owner,world.getBlockState(owner));
        var actual=world.getBlockState(owner).getOutlineShape(world,owner);
        t.assertTrue(Math.abs(actual.getBoundingBox().minY-raw.getBoundingBox().minY-.5)<1e-7,"hanging sign outline uses remembered ceiling seat");
        world.setBlockState(ceiling.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);world.removeBlock(ceiling.east(),false);
        t.assertTrue(seat.equals(com.slabbed.nativeblock.NativeSeats.seat(world,owner)),"ceiling neighbors do not change seat");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void nativeSignDataSurvivesOrdinaryEntityNbt(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=builder(t,base,Items.OAK_SIGN,1);t.assertTrue(place(player,base,Direction.UP,base.getY()+.5).isAccepted(),"sign placed");
        var owner=base.up();var sign=(SignBlockEntity)world.getBlockEntity(owner);
        var lines=new net.minecraft.text.Text[]{net.minecraft.text.Text.literal("Native text"),net.minecraft.text.Text.empty(),net.minecraft.text.Text.empty(),net.minecraft.text.Text.empty()};
        sign.setText(new net.minecraft.block.entity.SignText(lines,lines,net.minecraft.util.DyeColor.BLUE,true),true);sign.setWaxed(true);
        var copy=new SignBlockEntity(owner,world.getBlockState(owner));copy.read(sign.createNbt(world.getRegistryManager()),world.getRegistryManager());
        t.assertTrue(copy.getFrontText().getMessages(false)[0].getString().equals("Native text") && copy.getFrontText().isGlowing() && copy.isWaxed(),"native text glow and wax retained through NBT");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void nativeCraftingTableKeepsItsUsableMenu(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=builder(t,base,Items.CRAFTING_TABLE,1);t.assertTrue(place(player,base,Direction.UP,base.getY()+.5).isAccepted(),"crafting table placed");
        var owner=base.up();t.assertTrue(world.getBlockState(owner).isOf(Blocks.CRAFTING_TABLE),"crafting table retains native identity");
        var factory=world.getBlockState(owner).createScreenHandlerFactory(world,owner);
        var menu=factory.createMenu(1,player.getInventory(),player);
        t.assertTrue(menu instanceof net.minecraft.screen.CraftingScreenHandler && menu.canUse(player),"native crafting menu remains usable");
        t.assertTrue(com.slabbed.nativeblock.NativeSeats.offset(world,owner)==-.5,"workstation stores exact half seat");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void nativeSignCanSitOnLoweredWorkstation(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=builder(t,base,Items.CRAFTING_TABLE,1);t.assertTrue(place(player,base,Direction.UP,base.getY()+.5).isAccepted(),"table placed");
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.OAK_SIGN));
        t.assertTrue(place(player,base.up(),Direction.UP,base.getY()+1.5).isAccepted(),"sign fits physical top of lowered table");
        t.assertTrue(world.getBlockEntity(base.up(2)) instanceof SignBlockEntity,"sign has its own native owner");
        t.assertTrue(com.slabbed.nativeblock.NativeSeats.offset(world,base.up(2))==-.5,"sign stores bounded seat rather than cumulative displacement");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void wallSignStoresWallSeatIndependentlyOfFloor(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));var wall=base.up();
        world.setBlockState(wall,Blocks.STONE.getDefaultState());world.setBlockState(base.north(),Blocks.OAK_SLAB.getDefaultState());
        var player=builder(t,base,Items.OAK_SIGN,2);
        var hit=new BlockHitResult(new Vec3d(wall.getX()+.5,wall.getY()+.5,wall.getZ()),Direction.NORTH,wall,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"wall sign placed");
        var owner=wall.north();t.assertTrue(world.getBlockState(owner).getBlock() instanceof net.minecraft.block.WallSignBlock,"native wall variant selected");
        t.assertTrue(com.slabbed.nativeblock.NativeSeats.offset(world,owner)==0,"unrelated slab under sign does not lower a flush wall sign");
        world.removeBlock(base.north(),false);
        t.assertTrue(world.getBlockEntity(owner) instanceof SignBlockEntity && com.slabbed.nativeblock.NativeSeats.offset(world,owner)==0,"floor changes preserve wall sign seat");
        var lower=base.east(4);world.setBlockState(lower,Blocks.OAK_SLAB.getDefaultState());
        t.assertTrue(com.slabbed.cell.CellWorld.place(world,lower.getX(),lower.getZ(),lower.getY()*2+1,Blocks.STONE.getDefaultState()),"lowered carrier wall placed");
        var upper=lower.up();player.setPosition(lower.getX()+.5,lower.getY(),lower.getZ()-3);
        var lowerHit=new BlockHitResult(new Vec3d(lower.getX()+.5,lower.getY()+1,lower.getZ()),Direction.NORTH,upper,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,lowerHit)).isAccepted(),"sign attaches to physical carrier wall");
        t.assertTrue(world.getBlockState(upper.north()).getBlock() instanceof net.minecraft.block.WallSignBlock
                && com.slabbed.nativeblock.NativeSeats.offset(world,upper.north())==-.5,"lowered wall seat is remembered by native wall sign");t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void bannersAndSkullsRetainNativeDataOwners(TestContext t) {
        var world=t.getWorld();var items=new net.minecraft.item.Item[]{Items.WHITE_BANNER,Items.CREEPER_HEAD};
        for(int n=0;n<items.length;n++) {
            var base=t.getAbsolutePos(new BlockPos(3+n*3,3,3));world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
            var player=builder(t,base,items[n],1);
            t.assertTrue(place(player,base,Direction.UP,base.getY()+.5).isAccepted(),"decoration placed: "+items[n]);
            var entity=world.getBlockEntity(base.up());
            t.assertTrue(n==0?entity instanceof net.minecraft.block.entity.BannerBlockEntity:entity instanceof net.minecraft.block.entity.SkullBlockEntity,"decoration retains its native block entity: "+items[n]);
            t.assertTrue(com.slabbed.nativeblock.NativeSeats.offset(world,base.up())==-.5,"native decoration stores exact physical seat: "+items[n]);
        }
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void diagonalWallAndCeilingSupportLossRemoveNativeDecorations(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=builder(t,base,Items.OAK_SIGN,1);
        var sideHit=new BlockHitResult(new Vec3d(base.getX()+1,base.getY()+.25,base.getZ()+.5),Direction.EAST,base,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,sideHit)).isAccepted(),"wall sign on slab side placed");
        var sign=base.east();t.assertTrue(world.getBlockEntity(sign) instanceof SignBlockEntity,"native wall sign exists");
        world.removeBlock(base,false);
        t.assertTrue(world.getBlockState(sign).isAir(),"vanilla support loss removed native wall sign");
        var lower=base.east(4);world.setBlockState(lower,Blocks.OAK_SLAB.getDefaultState());
        t.assertTrue(com.slabbed.cell.CellWorld.place(world,lower.getX(),lower.getZ(),lower.getY()*2+1,Blocks.STONE.getDefaultState()),"carrier wall placed");
        player.setPosition(lower.getX()+.5,lower.getY(),lower.getZ()-3);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.OAK_SIGN));
        var wallHit=new BlockHitResult(new Vec3d(lower.getX()+.5,lower.getY()+.75,lower.getZ()),Direction.NORTH,lower,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,wallHit)).isAccepted(),"sign mounted on physical lower carrier half");
        var diagonal=lower.up().north();t.assertTrue(world.getBlockEntity(diagonal) instanceof SignBlockEntity,"diagonal native owner exists");
        com.slabbed.cell.CellWorld.remove(world,lower,1,null,false);
        t.assertTrue(world.getBlockState(diagonal).isAir(),"carrier contents change removed diagonal unsupported sign");
        var ceiling=base.up(3);world.setBlockState(ceiling,Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE,net.minecraft.block.enums.SlabType.TOP));
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.OAK_HANGING_SIGN));
        t.assertTrue(place(player,ceiling,Direction.DOWN,ceiling.getY()+.5).isAccepted(),"ceiling sign placed");
        world.removeBlock(ceiling,false);
        t.assertTrue(world.getBlockState(ceiling.down()).isAir(),"removed ceiling dropped native hanging sign");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void projectedPlantsAndWaterRejectPlacementAtomically(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,3,3));world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        t.assertTrue(com.slabbed.cell.CellWorld.place(world,base.getX(),base.getZ(),base.getY()*2+1,Blocks.STONE.getDefaultState()),"carrier support placed");
        var target=base.east();world.setBlockState(target.down(),Blocks.DIRT.getDefaultState());world.setBlockState(target,Blocks.DANDELION.getDefaultState());
        var player=builder(t,base,Items.CRAFTING_TABLE,2);
        var hit=new BlockHitResult(new Vec3d(base.getX()+1,base.getY()+.75,base.getZ()+.5),Direction.EAST,base,false);
        t.assertFalse(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"projected flower obstruction rejected");
        t.assertTrue(world.getBlockState(target).isOf(Blocks.DANDELION) && world.getBlockState(target.up()).isAir(),"flower and empty owner retained");
        t.assertTrue(player.getMainHandStack().getCount()==2,"flower rejection consumed nothing");
        world.setBlockState(target,Blocks.WATER.getDefaultState());
        t.assertFalse(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"projected water obstruction rejected");
        t.assertTrue(world.getBlockState(target).isOf(Blocks.WATER) && world.getBlockState(target.up()).isAir() && player.getMainHandStack().getCount()==2,"water rejection left no partial placement or consumption");t.complete();
    }

}
