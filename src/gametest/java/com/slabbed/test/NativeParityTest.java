package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeShapes;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Native gameplay must survive real item placement at a stored physical seat. */
public final class NativeParityTest {
    private static BlockPos slab(TestContext t,int x,int z) {
        var base=t.getAbsolutePos(new BlockPos(x,3,z));
        t.getWorld().setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        return base;
    }
    private static PlayerEntity builder(TestContext t,BlockPos base,Item item,int count) {
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY(),base.getZ()-3);player.setYaw(0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item,count));return player;
    }
    private static ActionResult place(PlayerEntity player,BlockPos base) {
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(base).add(0,.5,0),Direction.UP,base,false);
        return player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
    }
    private static NativeSeats.Seat shifted(TestContext t,BlockPos owner,Block expected) {
        t.assertTrue(t.getWorld().getBlockState(owner).isOf(expected),"native block retained at "+owner+": "+expected);
        var seat=NativeSeats.seat(t.getWorld(),owner);
        t.assertTrue(seat!=null && seat.halfOffset()==-1,"real item placement stores fixed -0.5 seat at "+owner);
        return seat;
    }
    private static ActionResult use(TestContext t,PlayerEntity player,BlockPos owner) {
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(owner),Direction.NORTH,owner,false);
        return t.getWorld().getBlockState(owner).onUse(t.getWorld(),player,hit);
    }
    private static void tallCollision(TestContext t,BlockPos owner) {
        var world=t.getWorld();var state=world.getBlockState(owner);
        var raw=NativeShapes.collision(world,owner,state).getBoundingBox();
        var actual=state.getCollisionShape(world,owner).getBoundingBox();
        t.assertTrue(Math.abs(raw.maxY-1.5)<1e-7,"native barrier keeps vanilla 1.5-high collision");
        t.assertTrue(Math.abs(actual.minY+.5)<1e-7 && Math.abs(actual.maxY-1)<1e-7,"barrier collision applies stored seat exactly once");
        t.assertFalse(world.isSpaceEmpty(new Box(owner.getX()+.45,owner.getY()+.75,owner.getZ()+.45,
                owner.getX()+.55,owner.getY()+.9,owner.getZ()+.55)),"world collision finds tall projected native barrier");
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void barrelInventoryMenuAndNbtRemainNative(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var player=builder(t,base,Items.BARREL,2);
        t.assertTrue(place(player,base).isAccepted(),"barrel item placed");var owner=base.up();
        var seat=shifted(t,owner,Blocks.BARREL);
        t.assertTrue(player.getMainHandStack().getCount()==1,"barrel item consumed exactly once");
        t.assertTrue(world.getBlockEntity(owner) instanceof BarrelBlockEntity,"native barrel inventory entity retained");
        var barrel=(BarrelBlockEntity)world.getBlockEntity(owner);
        barrel.setStack(0,new ItemStack(Items.DIAMOND,7));
        var factory=world.getBlockState(owner).createScreenHandlerFactory(world,owner);
        t.assertTrue(factory!=null,"barrel exposes vanilla menu factory");
        var menu=factory.createMenu(1,player.getInventory(),player);
        t.assertTrue(menu!=null && menu.canUse(player) && menu.getSlot(0).getStack().getCount()==7,"native menu reads actual barrel inventory");
        menu.getSlot(0).setStack(new ItemStack(Items.EMERALD,3));
        t.assertTrue(barrel.getStack(0).isOf(Items.EMERALD) && barrel.getStack(0).getCount()==3,"menu writes actual inventory");
        var copy=new BarrelBlockEntity(owner,world.getBlockState(owner));
        copy.read(barrel.createNbt(world.getRegistryManager()),world.getRegistryManager());
        t.assertTrue(copy.getStack(0).isOf(Items.EMERALD) && copy.getStack(0).getCount()==3,"vanilla barrel NBT preserves inventory");
        world.setBlockState(owner,world.getBlockState(owner).with(Properties.OPEN,true),Block.NOTIFY_ALL);
        t.assertTrue(seat.equals(NativeSeats.seat(world,owner)),"barrel OPEN transition preserves seat");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void alignedChestsJoinNativeInventoryAtOnePhysicalHeight(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var other=slab(t,4,3);
        var player=builder(t,base,Items.CHEST,2);
        t.assertTrue(place(player,base).isAccepted() && place(player,other).isAccepted(),"both chest items placed");
        var left=base.up();var right=other.up();var seat=shifted(t,left,Blocks.CHEST);shifted(t,right,Blocks.CHEST);
        t.assertTrue(seat.equals(NativeSeats.seat(world,right)),"joined chest members keep equal fixed seats");
        var a=world.getBlockState(left).get(ChestBlock.CHEST_TYPE);var b=world.getBlockState(right).get(ChestBlock.CHEST_TYPE);
        t.assertTrue(a!=ChestType.SINGLE && b!=ChestType.SINGLE && a!=b,"native chest types form complementary pair");
        ((ChestBlockEntity)world.getBlockEntity(left)).setStack(0,new ItemStack(Items.DIAMOND,5));
        ((ChestBlockEntity)world.getBlockEntity(right)).setStack(0,new ItemStack(Items.EMERALD,6));
        var factory=world.getBlockState(left).createScreenHandlerFactory(world,left);
        t.assertTrue(factory!=null,"joined chest exposes menu");var menu=factory.createMenu(1,player.getInventory(),player);
        t.assertTrue(menu!=null && menu.canUse(player) && menu.slots.size()==90,"native pair opens 54 storage slots plus player inventory");
        boolean diamond=false,emerald=false;
        for(int slot=0;slot<54;slot++) {var stack=menu.getSlot(slot).getStack();diamond|=stack.isOf(Items.DIAMOND)&&stack.getCount()==5;emerald|=stack.isOf(Items.EMERALD)&&stack.getCount()==6;}
        t.assertTrue(diamond && emerald,"joined menu contains both real inventories");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void doorMembersShareSeatThroughToggleAndRemoval(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var player=builder(t,base,Items.OAK_DOOR,2);
        t.assertTrue(place(player,base).isAccepted(),"door item placed");var lower=base.up();var upper=lower.up();
        var seat=shifted(t,lower,Blocks.OAK_DOOR);shifted(t,upper,Blocks.OAK_DOOR);
        t.assertTrue(seat.equals(NativeSeats.seat(world,upper)),"door members share one frozen seat");
        t.assertTrue(world.getBlockState(lower).get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER
                && world.getBlockState(upper).get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER,"native door half properties retained");
        t.assertTrue(player.getMainHandStack().getCount()==1,"paired door consumes one item");
        t.assertTrue(use(t,player,lower).isAccepted(),"native door use accepted");
        t.assertTrue(world.getBlockState(lower).get(Properties.OPEN) && world.getBlockState(upper).get(Properties.OPEN),"native door use opens both members");
        t.assertTrue(seat.equals(NativeSeats.seat(world,lower)) && seat.equals(NativeSeats.seat(world,upper)),"OPEN transition moves neither door member");
        world.breakBlock(upper,false);
        t.assertTrue(world.getBlockState(lower).isAir() && world.getBlockState(upper).isAir(),"breaking upper door removes native pair");
        t.assertTrue(NativeSeats.seat(world,lower)==null && NativeSeats.seat(world,upper)==null,"removed door clears both seats");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void blockedUpperDoorWritesNothingAndConsumesNothing(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var upper=base.up(2);
        world.setBlockState(upper,Blocks.BEDROCK.getDefaultState());var player=builder(t,base,Items.OAK_DOOR,2);
        t.assertFalse(place(player,base).isAccepted(),"blocked upper member rejects door");
        t.assertTrue(world.getBlockState(base.up()).isAir() && world.getBlockState(upper).isOf(Blocks.BEDROCK),"failed pair leaves no lower member and preserves obstruction");
        t.assertTrue(world.getBlockState(base).isOf(Blocks.OAK_SLAB) && player.getMainHandStack().getCount()==2,"failed door preserves support and item count");
        t.assertTrue(NativeSeats.seat(world,base.up())==null && NativeSeats.seat(world,upper)==null,"failed door stores no seat");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void fenceConnectionsAndTallCollisionKeepSeat(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var other=slab(t,4,3);var player=builder(t,base,Items.OAK_FENCE,2);
        t.assertTrue(place(player,base).isAccepted() && place(player,other).isAccepted(),"fence items placed");
        var owner=base.up();var seat=shifted(t,owner,Blocks.OAK_FENCE);shifted(t,other.up(),Blocks.OAK_FENCE);
        t.assertTrue(world.getBlockState(owner).get(Properties.EAST) && world.getBlockState(other.up()).get(Properties.WEST),"native fence neighbors connect");tallCollision(t,owner);
        world.breakBlock(other.up(),false);
        t.assertFalse(world.getBlockState(owner).get(Properties.EAST),"native fence disconnects after neighbor removal");
        world.removeBlock(base,false);
        t.assertTrue(seat.equals(NativeSeats.seat(world,owner)),"fence connection and floor changes preserve seat");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void wallConnectionsAndTallCollisionKeepSeat(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var other=slab(t,4,3);var player=builder(t,base,Items.COBBLESTONE_WALL,2);
        t.assertTrue(place(player,base).isAccepted() && place(player,other).isAccepted(),"wall items placed");
        var owner=base.up();var seat=shifted(t,owner,Blocks.COBBLESTONE_WALL);shifted(t,other.up(),Blocks.COBBLESTONE_WALL);
        t.assertTrue(world.getBlockState(owner).get(Properties.EAST_WALL_SHAPE)!=WallShape.NONE
                && world.getBlockState(other.up()).get(Properties.WEST_WALL_SHAPE)!=WallShape.NONE,"native wall neighbors connect");tallCollision(t,owner);
        world.breakBlock(other.up(),false);
        t.assertTrue(world.getBlockState(owner).get(Properties.EAST_WALL_SHAPE)==WallShape.NONE,"native wall disconnects after neighbor removal");
        t.assertTrue(seat.equals(NativeSeats.seat(world,owner)),"wall connection changes preserve seat");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void paneConnectionsUpdateAtFixedSeat(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var other=slab(t,4,3);var player=builder(t,base,Items.GLASS_PANE,2);
        t.assertTrue(place(player,base).isAccepted() && place(player,other).isAccepted(),"pane items placed");
        var owner=base.up();var seat=shifted(t,owner,Blocks.GLASS_PANE);shifted(t,other.up(),Blocks.GLASS_PANE);
        t.assertTrue(world.getBlockState(owner).get(Properties.EAST) && world.getBlockState(other.up()).get(Properties.WEST),"native pane neighbors connect");
        var box=world.getBlockState(owner).getCollisionShape(world,owner).getBoundingBox();
        t.assertTrue(Math.abs(box.minY+.5)<1e-7 && Math.abs(box.maxY-.5)<1e-7,"pane collision occupies remembered physical height");
        world.breakBlock(other.up(),false);t.assertFalse(world.getBlockState(owner).get(Properties.EAST),"native pane disconnects");
        t.assertTrue(seat.equals(NativeSeats.seat(world,owner)),"pane connection update preserves seat");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void gateUseChangesCollisionWithoutChangingSeat(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var player=builder(t,base,Items.OAK_FENCE_GATE,1);
        t.assertTrue(place(player,base).isAccepted(),"gate item placed");var owner=base.up();var seat=shifted(t,owner,Blocks.OAK_FENCE_GATE);tallCollision(t,owner);
        t.assertTrue(use(t,player,owner).isAccepted() && world.getBlockState(owner).get(Properties.OPEN),"native gate opens on use");
        t.assertTrue(world.getBlockState(owner).getCollisionShape(world,owner).isEmpty(),"open native gate releases collision");
        t.assertTrue(use(t,player,owner).isAccepted() && !world.getBlockState(owner).get(Properties.OPEN),"native gate closes on second use");
        tallCollision(t,owner);t.assertTrue(seat.equals(NativeSeats.seat(world,owner)),"gate OPEN changes preserve seat");t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void leverAndButtonUseRetainNativePowerAndSupportRules(TestContext t) {
        var world=t.getWorld();var leverBase=slab(t,3,3);var buttonBase=slab(t,6,3);
        var player=builder(t,leverBase,Items.LEVER,1);
        t.assertTrue(place(player,leverBase).isAccepted(),"lever item placed on physical slab");
        var lever=leverBase.up();var leverSeat=shifted(t,lever,Blocks.LEVER);
        t.assertTrue(use(t,player,lever).isAccepted() && world.getBlockState(lever).get(Properties.POWERED),"native lever switches power on");
        t.assertTrue(world.getBlockState(lever).getWeakRedstonePower(world,lever,Direction.NORTH)==15,"powered lever exposes vanilla redstone output");
        t.assertTrue(use(t,player,lever).isAccepted() && !world.getBlockState(lever).get(Properties.POWERED),"native lever switches power off");
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.STONE_BUTTON));
        t.assertTrue(place(player,buttonBase).isAccepted(),"button item placed on physical slab");
        var button=buttonBase.up();var buttonSeat=shifted(t,button,Blocks.STONE_BUTTON);
        t.assertTrue(use(t,player,button).isAccepted() && world.getBlockState(button).get(Properties.POWERED),"native button press powers block");
        t.runAtTick(30,()-> {
            t.assertFalse(world.getBlockState(button).get(Properties.POWERED),"native scheduled tick releases button");
            t.assertTrue(leverSeat.equals(NativeSeats.seat(world,lever)) && buttonSeat.equals(NativeSeats.seat(world,button)),"power updates preserve both switch seats");
            world.removeBlock(leverBase,false);world.removeBlock(buttonBase,false);
            t.assertTrue(world.getBlockState(lever).isAir() && world.getBlockState(button).isAir(),"native support loss removes unsupported switches");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=70)
    public void pressurePlateDetectsShortEntityAtPhysicalHeight(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var player=builder(t,base,Items.STONE_PRESSURE_PLATE,1);
        t.assertTrue(place(player,base).isAccepted(),"pressure plate item placed");var owner=base.up();var seat=shifted(t,owner,Blocks.STONE_PRESSURE_PLATE);
        var entity=t.spawnEntity(EntityType.SILVERFISH,new Vec3d(3.5,3.5,3.5));entity.setAiDisabled(true);
        t.runAtTick(5,()-> {
            t.assertTrue(entity.getBoundingBox().maxY<owner.getY(),"activation entity remains wholly below native owner cell");
            boolean active=world.getBlockState(owner).get(Properties.POWERED);
            if(!active) {
                System.out.println("NATIVE_PLATE_CONTACT entity="+entity.getBoundingBox()+" owner="+owner+" outline="+world.getBlockState(owner).getOutlineShape(world,owner).getBoundingBoxes()+" seat="+NativeSeats.seat(world,owner));
                world.getBlockState(owner).onEntityCollision(world,owner,entity);
                System.out.println("NATIVE_PLATE_DIRECT_DETECTION powered="+world.getBlockState(owner).get(Properties.POWERED));
            }
            t.assertTrue(active,"actual short entity activates lowered physical plate");
            t.assertTrue(seat.equals(NativeSeats.seat(world,owner)),"plate activation preserves seat");entity.discard();
        });
        t.runAtTick(35,()-> {
            t.assertFalse(world.getBlockState(owner).get(Properties.POWERED),"native scheduled tick releases empty plate");
            world.removeBlock(base,false);t.assertTrue(world.getBlockState(owner).isAir(),"plate loses removed physical support");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=50)
    public void wireAndRepeaterKeepNativePowerUpdatesAtFixedSeat(TestContext t) {
        var world=t.getWorld();var wireBase=slab(t,3,3);var repeaterBase=slab(t,6,3);
        var player=builder(t,wireBase,Items.REDSTONE,1);
        t.assertTrue(place(player,wireBase).isAccepted(),"wire item placed on physical slab");var wire=wireBase.up();var wireSeat=shifted(t,wire,Blocks.REDSTONE_WIRE);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.REPEATER));
        t.assertTrue(place(player,repeaterBase).isAccepted(),"repeater item placed on physical slab");var repeater=repeaterBase.up();var repeaterSeat=shifted(t,repeater,Blocks.REPEATER);
        var wireSource=wire.west();var repeaterSource=repeater.offset(world.getBlockState(repeater).get(Properties.HORIZONTAL_FACING));
        world.setBlockState(wireSource.down(),Blocks.OAK_SLAB.getDefaultState());
        world.setBlockState(repeaterSource.down(),Blocks.OAK_SLAB.getDefaultState());
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.REDSTONE_BLOCK,2));
        t.assertTrue(place(player,wireSource.down()).isAccepted() && place(player,repeaterSource.down()).isAccepted(),"real source items placed at matching physical height");
        shifted(t,wireSource,Blocks.REDSTONE_BLOCK);shifted(t,repeaterSource,Blocks.REDSTONE_BLOCK);
        t.runAtTick(5,()-> {
            t.assertTrue(world.getBlockState(wire).get(Properties.POWER)==15,"native wire receives full source power");
            t.assertTrue(world.getBlockState(repeater).get(Properties.POWERED),"native repeater scheduled tick receives input");
            world.removeBlock(wireSource,false);world.removeBlock(repeaterSource,false);
        });
        t.runAtTick(12,()-> {
            t.assertTrue(world.getBlockState(wire).get(Properties.POWER)==0 && !world.getBlockState(repeater).get(Properties.POWERED),"native components clear removed input power");
            t.assertTrue(wireSeat.equals(NativeSeats.seat(world,wire)) && repeaterSeat.equals(NativeSeats.seat(world,repeater)),"native power state updates preserve seats");
            world.removeBlock(wireBase,false);world.removeBlock(repeaterBase,false);
            t.assertTrue(world.getBlockState(wire).isAir() && world.getBlockState(repeater).isAir(),"native components lose removed physical support");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void skullRetainsVanillaFloatingBehaviorAfterSupportRemoval(TestContext t) {
        var world=t.getWorld();var base=slab(t,3,3);var player=builder(t,base,Items.CREEPER_HEAD,1);
        t.assertTrue(place(player,base).isAccepted(),"skull item placed");var owner=base.up();var seat=shifted(t,owner,Blocks.CREEPER_HEAD);
        var entity=world.getBlockEntity(owner);t.assertTrue(entity instanceof SkullBlockEntity,"skull keeps native entity");
        world.removeBlock(base,false);
        t.assertTrue(world.getBlockEntity(owner)==entity && world.getBlockState(owner).isOf(Blocks.CREEPER_HEAD),"vanilla skull remains after support removal");
        t.assertTrue(seat.equals(NativeSeats.seat(world,owner)),"floating skull retains original seat");t.complete();
    }
}
