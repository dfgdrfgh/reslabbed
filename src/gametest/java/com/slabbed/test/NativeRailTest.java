package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Actual rail/cart items pin the original fitted-rail and physical-power behavior. */
public final class NativeRailTest {
    private static final double EPS=1e-6;
    private static final double CART_LIFT=1.0/16;
    private enum Support { SLAB, FULL, CROSSING }

    private static PlayerEntity builder(TestContext t) {
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        var origin=t.getAbsolutePos(new BlockPos(3,3,1));
        player.setPosition(Vec3d.ofBottomCenter(origin));player.setYaw(0);
        return player;
    }
    private static void place(TestContext t,PlayerEntity player,Item item,BlockPos hitCell,double y) {
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item,2));
        var hit=new BlockHitResult(new Vec3d(hitCell.getX()+.5,y,hitCell.getZ()+.5),Direction.UP,hitCell,false);
        t.assertTrue(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),
                "real item accepted: "+item+" at "+hitCell);
        t.assertTrue(player.getMainHandStack().getCount()==1,"real item consumed exactly once: "+item);
    }
    private static BlockPos rail(TestContext t,PlayerEntity player,int x,int z,Support support,Item item,Block expected) {
        var world=t.getWorld();var floor=t.getAbsolutePos(new BlockPos(x,2,z));
        world.setBlockState(floor,Blocks.BEDROCK.getDefaultState());
        place(t,player,support==Support.FULL?Items.STONE:Items.OAK_SLAB,floor,floor.getY()+1);
        var base=floor.up();double top=base.getY()+(support==Support.FULL?1:.5);
        if(support==Support.CROSSING) {
            place(t,player,Items.STONE,base,top);top+=1;
            t.assertTrue(world.getBlockState(base).isOf(Slabbed.CELL),"real stone placement created shared physical support");
            base=base.up();
        }
        place(t,player,item,base,top);
        var owner=base.up();
        t.assertTrue(world.getBlockState(owner).isOf(expected),"rail retains native block identity");
        int half=support==Support.FULL?0:-1;
        t.assertTrue(Math.abs(NativeSeats.offset(world,owner)-half*.5)<EPS,"rail remembers bounded physical seat");
        if(half!=0)t.assertTrue(NativeSeats.seat(world,owner)!=null,"shifted rail stores authored seat");
        return owner;
    }
    private static RailShape shape(TestContext t,BlockPos rail) {
        var state=t.getWorld().getBlockState(rail);
        return state.get(((AbstractRailBlock)state.getBlock()).getShapeProperty());
    }
    private static void outline(TestContext t,BlockPos rail,double min,double max) {
        var box=t.getWorld().getBlockState(rail).getOutlineShape(t.getWorld(),rail).getBoundingBox();
        t.assertTrue(Math.abs(box.minY-min)<EPS && Math.abs(box.maxY-max)<EPS,
                "rail outline follows physical fitted slope: expected "+min+".."+max+", got "+box);
    }
    private static AbstractMinecartEntity cart(TestContext t,PlayerEntity player,BlockPos rail) {
        place(t,player,Items.MINECART,rail,rail.getY()+NativeSeats.offset(t.getWorld(),rail)+CART_LIFT);
        var carts=t.getWorld().getEntitiesByClass(AbstractMinecartEntity.class,new Box(rail).expand(1),entity->!entity.isRemoved());
        t.assertTrue(carts.size()==1,"real minecart item spawns exactly one native cart");
        return carts.getFirst();
    }
    private static void cartHeight(TestContext t,AbstractMinecartEntity cart,double expected) {
        t.assertTrue(Math.abs(cart.getY()-expected)<EPS,"cart rides physical rail: expected "+expected+", got "+cart.getY());
    }
    private static BlockPos[] chain(TestContext t,PlayerEntity player,Item item,Block expected,boolean dip) {
        var rails=new BlockPos[6];
        for(int n=0;n<rails.length;n++)rails[n]=rail(t,player,n+1,3,
                dip && n==2?Support.SLAB:Support.FULL,item,expected);
        for(var rail:rails)t.assertTrue(shape(t,rail)==RailShape.EAST_WEST,"native rail chain chooses east-west connection");
        return rails;
    }
    private static BlockPos lever(TestContext t,PlayerEntity player,BlockPos rail) {
        var floor=rail.north().down(2);
        t.getWorld().setBlockState(floor,Blocks.BEDROCK.getDefaultState());
        place(t,player,Items.LEVER,floor,floor.getY()+1);
        var lever=floor.up();
        t.assertTrue(t.getWorld().getBlockState(lever).isOf(Blocks.LEVER),"native lever stands beside slab, below rail owner");
        return lever;
    }
    private static void toggle(TestContext t,PlayerEntity player,BlockPos lever) {
        player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        var hit=new BlockHitResult(Vec3d.ofCenter(lever),Direction.UP,lever,false);
        t.assertTrue(t.getWorld().getBlockState(lever).onUse(t.getWorld(),player,hit).isAccepted(),"real lever interaction accepted");
    }
    private static void powered(TestContext t,BlockPos[] rails,boolean expected) {
        for(var rail:rails)t.assertTrue(t.getWorld().getBlockState(rail).get(Properties.POWERED)==expected,
                "native powered rail chain state is "+expected+" at "+rail);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void minecartItemSpawnsOnShiftedFlatRail(TestContext t) {
        var player=builder(t);var rail=rail(t,player,3,3,Support.SLAB,Items.RAIL,Blocks.RAIL);
        var cart=cart(t,player,rail);
        try {cartHeight(t,cart,rail.getY()-.5+CART_LIFT);} finally {cart.discard();}
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void minecartTicksAndNbtKeepPhysicalFlatRailHeight(TestContext t) {
        var player=builder(t);BlockPos start=null;
        for(int z=2;z<=5;z++) {
            var next=rail(t,player,3,z,Support.SLAB,Items.RAIL,Blocks.RAIL);if(z==3)start=next;
        }
        var cart=cart(t,player,start);var seat=NativeSeats.seat(t.getWorld(),start);
        try {
            double expected=start.getY()-.5+CART_LIFT;
            for(int tick=0;tick<12;tick++) {cart.setVelocity(Vec3d.ZERO);cart.tick();cartHeight(t,cart,expected);}
            var saved=new NbtCompound();cart.writeNbt(saved);
            var restored=new net.minecraft.entity.vehicle.MinecartEntity(net.minecraft.entity.EntityType.MINECART,t.getWorld());
            restored.readNbt(saved);
            t.assertTrue(restored.getPos().equals(cart.getPos()),"native cart NBT preserves physical position");
            restored.setVelocity(Vec3d.ZERO);restored.tick();cartHeight(t,restored,expected);restored.discard();
            t.assertTrue(seat.equals(NativeSeats.seat(t.getWorld(),start)),"cart movement never changes rail seat");
        } finally {cart.discard();}
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void crossingSupportRailUsesBoundedSeatAndCartHeight(TestContext t) {
        var player=builder(t);var rail=rail(t,player,3,3,Support.CROSSING,Items.RAIL,Blocks.RAIL);
        var cart=cart(t,player,rail);
        try {cart.tick();cartHeight(t,cart,rail.getY()-.5+CART_LIFT);} finally {cart.discard();}
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void halfStepRailOutlineChangesWithoutReseatingRail(TestContext t) {
        var player=builder(t);var low=rail(t,player,3,3,Support.SLAB,Items.RAIL,Blocks.RAIL);
        var seat=NativeSeats.seat(t.getWorld(),low);
        outline(t,low,-.5,-.375);
        var high=rail(t,player,4,3,Support.FULL,Items.RAIL,Blocks.RAIL);
        t.assertTrue(shape(t,low)==RailShape.EAST_WEST,"same-row half step retains native flat rail connection");
        outline(t,low,-.5,-.1875);outline(t,high,0,.125);
        t.assertTrue(seat.equals(NativeSeats.seat(t.getWorld(),low)),"connecting higher neighbor changes slope without changing seat");
        t.getWorld().breakBlock(high,false);outline(t,low,-.5,-.375);
        t.assertTrue(seat.equals(NativeSeats.seat(t.getWorld(),low)),"neighbor removal preserves rail placement");
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void cartRidesEachHalfOfFittedDip(TestContext t) {
        var player=builder(t);var rails=chain(t,player,Items.POWERED_RAIL,Blocks.POWERED_RAIL,true);
        var dip=rails[2];var seat=NativeSeats.seat(t.getWorld(),dip);var cart=cart(t,player,dip);
        try {
            double[][] samples={{.5,-.5},{.1,-.1},{.9,-.1},{.3,-.3}};
            for(var sample:samples) {
                double x=dip.getX()+sample[0];cart.setPosition(x,cart.getY(),dip.getZ()+.5);
                cart.setVelocity(Vec3d.ZERO);cart.tick();
                t.assertTrue(Math.abs(cart.getX()-x)<EPS,"unpowered stationary cart stays at sampled rail fraction");
                cartHeight(t,cart,dip.getY()+CART_LIFT+sample[1]);
            }
            t.assertTrue(seat.equals(NativeSeats.seat(t.getWorld(),dip)),"cart follows fitted slope without reseating dip rail");
        } finally {cart.discard();}
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void nativeRampShortensToHalfStepWithoutMovingEitherSeat(TestContext t) {
        var player=builder(t);var low=rail(t,player,3,3,Support.FULL,Items.RAIL,Blocks.RAIL);
        var high=rail(t,player,4,3,Support.CROSSING,Items.RAIL,Blocks.RAIL);
        t.assertTrue(high.getY()==low.getY()+1 && shape(t,low)==RailShape.ASCENDING_EAST,"native ramp climbs to adjacent owner one cell up");
        outline(t,low,0,.3125);outline(t,high,-.5,-.375);
        t.assertTrue(NativeSeats.offset(t.getWorld(),low)==0 && NativeSeats.offset(t.getWorld(),high)==-.5,"ramp profile never rewrites either seat");
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void everyFunctionalRailKindFitsHalfStepAtNativeSeat(TestContext t) {
        var player=builder(t);
        Item[] items={Items.POWERED_RAIL,Items.DETECTOR_RAIL,Items.ACTIVATOR_RAIL};
        Block[] blocks={Blocks.POWERED_RAIL,Blocks.DETECTOR_RAIL,Blocks.ACTIVATOR_RAIL};
        for(int n=0;n<items.length;n++) {
            var low=rail(t,player,3,2+n*2,Support.SLAB,items[n],blocks[n]);
            var seat=NativeSeats.seat(t.getWorld(),low);
            var high=rail(t,player,4,2+n*2,Support.FULL,items[n],blocks[n]);
            t.assertTrue(shape(t,low)==RailShape.EAST_WEST,"functional rail retains vanilla shape property");
            outline(t,low,-.5,-.1875);outline(t,high,0,.125);
            t.assertTrue(seat.equals(NativeSeats.seat(t.getWorld(),low)),"functional rail slope fitting preserves authored seat");
        }
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void leverBesideSlabPowersEntireMixedHeightRailChain(TestContext t) {
        var player=builder(t);var rails=chain(t,player,Items.POWERED_RAIL,Blocks.POWERED_RAIL,true);
        var seat=NativeSeats.seat(t.getWorld(),rails[2]);var lever=lever(t,player,rails[2]);
        powered(t,rails,false);toggle(t,player,lever);powered(t,rails,true);
        toggle(t,player,lever);powered(t,rails,false);
        toggle(t,player,lever);powered(t,rails,true);
        t.getWorld().breakBlock(lever,false);powered(t,rails,false);
        t.assertTrue(seat.equals(NativeSeats.seat(t.getWorld(),rails[2])),"physical feed updates retain rail seat");
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void leverBelowFlushRailKeepsVanillaDiagonalReach(TestContext t) {
        var player=builder(t);var rails=chain(t,player,Items.POWERED_RAIL,Blocks.POWERED_RAIL,false);
        var lever=lever(t,player,rails[2]);toggle(t,player,lever);
        t.assertTrue(t.getWorld().getBlockState(lever).get(Properties.POWERED),"control source is actually on");
        powered(t,rails,false);t.complete();
    }

    private static void roll(TestContext t,boolean feed) {
        var player=builder(t);var rails=chain(t,player,Items.POWERED_RAIL,Blocks.POWERED_RAIL,true);
        if(feed)toggle(t,player,lever(t,player,rails[2]));
        var cart=cart(t,player,rails[0]);double farthest=cart.getX();
        try {
            cart.setVelocity(new Vec3d(.3,0,0));
            for(int tick=0;tick<60 && farthest<rails[5].getX()+.3;tick++) {
                cart.tick();farthest=Math.max(farthest,cart.getX());
            }
            if(feed)t.assertTrue(farthest>=rails[5].getX()+.3,"powered cart traverses fitted dip and its higher support: "+farthest);
            else t.assertTrue(farthest<rails[5].getX(),"unpowered rail control brakes cart before end: "+farthest);
        } finally {cart.discard();}
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void poweredCartCrossesHalfStepDip(TestContext t) {roll(t,true);}
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void unpoweredRailChainBrakesActualCart(TestContext t) {roll(t,false);}
}
