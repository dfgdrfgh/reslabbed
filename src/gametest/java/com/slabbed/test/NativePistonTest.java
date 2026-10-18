package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativePistonPose;
import com.slabbed.nativeblock.NativeShapes;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.entity.EntityType;
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

/** Actual vanilla piston events and moving block entities must preserve physical seats. */
public final class NativePistonTest {
    private static NativeSeats.Seat seat(Block block) {
        return new NativeSeats.Seat(-1,net.minecraft.registry.Registries.BLOCK.getId(block).toString());
    }

    private static void fixture(TestContext t,BlockPos pos,Block block,Direction facing) {
        var state=block.getDefaultState();
        if (state.contains(PistonBlock.FACING)) state=state.with(PistonBlock.FACING,facing);
        t.getWorld().setBlockState(pos,state,Block.NOTIFY_ALL);
        NativeSeats.put(t.getWorld(),pos,seat(block));
    }

    private static void remembered(TestContext t,BlockPos pos,Block block) {
        t.assertTrue(t.getWorld().getBlockState(pos).isOf(block),"expected native "+block+" at "+pos);
        t.assertTrue(seat(block).equals(NativeSeats.seat(t.getWorld(),pos)),"fixed -0.5 seat retained at "+pos);
    }

    private static void power(TestContext t,BlockPos pos,boolean powered) {
        t.getWorld().setBlockState(pos,(powered?Blocks.REDSTONE_BLOCK:Blocks.AIR).getDefaultState(),Block.NOTIFY_ALL);
    }

    private static void moving(TestContext t,BlockPos pos) {
        var world=t.getWorld();var state=world.getBlockState(pos);
        t.assertTrue(world.getBlockEntity(pos) instanceof PistonBlockEntity,"actual vanilla moving piston block entity exists");
        var entity=(PistonBlockEntity)world.getBlockEntity(pos);
        remembered(t,pos,Blocks.MOVING_PISTON);
        t.assertTrue(Integer.valueOf(-1).equals(((NativePistonPose)entity).slabbed$halfOffset()),"moving entity remembers source pose");
        var copy=new PistonBlockEntity(pos,state);
        copy.read(entity.createNbt(world.getRegistryManager()),world.getRegistryManager());
        t.assertTrue(Integer.valueOf(-1).equals(((NativePistonPose)copy).slabbed$halfOffset()),"moving entity NBT preserves frozen source pose");
        var raw=NativeShapes.collision(world,pos,state).getBoundingBox();
        var physical=state.getCollisionShape(world,pos).getBoundingBox();
        t.assertTrue(Math.abs(physical.minY-raw.minY+.5)<1e-7 && Math.abs(physical.maxY-raw.maxY+.5)<1e-7,
                "in-flight moving piston collision applies physical seat exactly once");
        if (entity.getFacing().getAxis().isHorizontal())
            t.assertTrue(Math.abs(physical.minY+.5)<1e-7 && Math.abs(physical.maxY-.5)<1e-7,
                    "horizontal moving cube and head have absolute Y bounds -0.5 to 0.5");
        else if (!entity.isSource()) {
            double motion=entity.getRenderOffsetY(1);
            t.assertTrue(Math.abs(physical.minY-(motion-.5))<1e-7 && Math.abs(physical.maxY-(motion+.5))<1e-7,
                    "vertical moving cube absolute Y matches vanilla motion plus remembered seat");
        }
    }

    private static BlockPos itemPiston(TestContext t) {
        var base=t.getAbsolutePos(new BlockPos(2,3,3));
        t.getWorld().setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()-3,base.getY(),base.getZ()+.5);
        player.setYaw(90);player.setPitch(0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.PISTON,2));
        var hit=new BlockHitResult(Vec3d.ofBottomCenter(base).add(0,.5,0),Direction.UP,base,false);
        var result=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        t.assertTrue(result.isAccepted(),"real piston item placement accepted");
        var owner=base.up();
        remembered(t,owner,Blocks.PISTON);
        t.assertTrue(player.getMainHandStack().getCount()==1,"piston item consumed once");
        t.assertTrue(t.getWorld().getBlockState(owner).get(PistonBlock.FACING)==Direction.EAST,"item piston faces east");
        return owner;
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=55)
    public void itemPlacedPistonExtendsAndRetractsPhysicalHead(TestContext t) {
        var owner=itemPiston(t);var head=owner.east();var source=owner.north();
        t.runAtTick(2,()->power(t,source,true));
        t.runAtTick(3,()->moving(t,head));
        t.runAtTick(10,()-> {
            remembered(t,owner,Blocks.PISTON);remembered(t,head,Blocks.PISTON_HEAD);
            t.assertTrue(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"vanilla piston reaches extended state");
            var shape=t.getWorld().getBlockState(head).getCollisionShape(t.getWorld(),head).getBoundingBox();
            t.assertTrue(Math.abs(shape.minY+.5)<1e-7 && Math.abs(shape.maxY-.5)<1e-7,
                    "piston head occupies the actual lowered physical height");
            power(t,source,false);
        });
        t.runAtTick(20,()-> {
            remembered(t,owner,Blocks.PISTON);
            t.assertFalse(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"vanilla piston retracts after losing power");
            t.assertTrue(t.getWorld().getBlockState(head).isAir(),"retraction removes physical head");
            t.assertTrue(NativeSeats.seat(t.getWorld(),head)==null,"head removal clears its seat");
            t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=55)
    public void horizontalStickyPistonPushesAndPullsWholeNativeStone(TestContext t) {
        var owner=t.getAbsolutePos(new BlockPos(2,4,3));var original=owner.east();var pushed=owner.east(2);
        fixture(t,owner,Blocks.STICKY_PISTON,Direction.EAST);
        // The fixture deliberately retains native STONE identity and an explicit frozen seat.
        fixture(t,original,Blocks.STONE,Direction.EAST);
        var entity=t.spawnEntity(EntityType.SILVERFISH,new Vec3d(4.35,3.55,3.5));
        entity.setAiDisabled(true);entity.setNoGravity(true);
        t.runAtTick(2,()->power(t,owner.north(),true));
        t.runAtTick(3,()->moving(t,pushed));
        t.runAtTick(10,()-> {
            remembered(t,owner,Blocks.STICKY_PISTON);remembered(t,original,Blocks.PISTON_HEAD);remembered(t,pushed,Blocks.STONE);
            t.assertTrue(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"sticky piston extends");
            t.assertTrue(entity.getBoundingBox().maxY<owner.getY(),"short pushed entity stays wholly below native owner height");
            t.assertTrue(entity.getX()>pushed.getX()+1,"actual moving physical cube pushes the low entity beyond its destination");
            entity.discard();
            power(t,owner.north(),false);
        });
        t.runAtTick(20,()-> {
            remembered(t,owner,Blocks.STICKY_PISTON);remembered(t,original,Blocks.STONE);
            t.assertFalse(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"sticky piston retracts");
            t.assertTrue(t.getWorld().getBlockState(pushed).isAir(),"sticky retraction pulls original piece back");
            t.assertTrue(NativeSeats.seat(t.getWorld(),pushed)==null,"vacated destination has no seat");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=55)
    public void verticalStickyPistonPushesAndPullsWholeNativeStone(TestContext t) {
        var owner=t.getAbsolutePos(new BlockPos(3,3,3));var original=owner.up();var pushed=owner.up(2);
        fixture(t,owner,Blocks.STICKY_PISTON,Direction.UP);fixture(t,original,Blocks.STONE,Direction.UP);
        t.runAtTick(2,()->power(t,owner.west(),true));
        t.runAtTick(3,()->moving(t,pushed));
        t.runAtTick(10,()-> {
            remembered(t,owner,Blocks.STICKY_PISTON);remembered(t,original,Blocks.PISTON_HEAD);remembered(t,pushed,Blocks.STONE);
            t.assertTrue(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"vertical sticky piston extends");
            power(t,owner.west(),false);
        });
        t.runAtTick(20,()-> {
            remembered(t,owner,Blocks.STICKY_PISTON);remembered(t,original,Blocks.STONE);
            t.assertFalse(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"vertical sticky piston retracts");
            t.assertTrue(t.getWorld().getBlockState(pushed).isAir(),"vertical pull returns whole piece to original physical origin");
            t.assertTrue(NativeSeats.seat(t.getWorld(),pushed)==null,"vertical pull clears vacated seat");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=35)
    public void nativeBarrelBlocksPistonWithoutLosingInventoryOrSeat(TestContext t) {
        var owner=t.getAbsolutePos(new BlockPos(2,4,3));var barrelPos=owner.east();
        fixture(t,owner,Blocks.PISTON,Direction.EAST);fixture(t,barrelPos,Blocks.BARREL,Direction.EAST);
        var barrel=(BarrelBlockEntity)t.getWorld().getBlockEntity(barrelPos);
        t.assertTrue(barrel!=null,"barrel fixture has its actual native inventory block entity");
        barrel.setStack(0,new ItemStack(Items.DIAMOND,7));
        t.runAtTick(2,()->power(t,owner.north(),true));
        t.runAtTick(12,()-> {
            remembered(t,owner,Blocks.PISTON);remembered(t,barrelPos,Blocks.BARREL);
            t.assertFalse(t.getWorld().getBlockState(owner).get(PistonBlock.EXTENDED),"vanilla immovable barrel prevents piston extension");
            t.assertTrue(t.getWorld().getBlockEntity(barrelPos)==barrel,"failed push retains original native barrel entity");
            t.assertTrue(barrel.getStack(0).isOf(Items.DIAMOND) && barrel.getStack(0).getCount()==7,"failed push preserves all seven diamonds");
            t.assertTrue(t.getWorld().getBlockState(owner.east(2)).isAir(),"failed push writes no destination block");t.complete();
        });
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty",tickLimit=40)
    public void verticalColumnsRespectActualTwelveAndThirteenPieceLimit(TestContext t) {
        var twelve=t.getAbsolutePos(new BlockPos(2,3,2));var thirteen=t.getAbsolutePos(new BlockPos(5,3,5));
        fixture(t,twelve,Blocks.PISTON,Direction.UP);fixture(t,thirteen,Blocks.PISTON,Direction.UP);
        for(int n=1;n<=12;n++) fixture(t,twelve.up(n),Blocks.STONE,Direction.UP);
        for(int n=1;n<=13;n++) fixture(t,thirteen.up(n),Blocks.STONE,Direction.UP);
        t.runAtTick(2,()-> {power(t,twelve.west(),true);power(t,thirteen.west(),true);});
        t.runAtTick(3,()->moving(t,twelve.up(13)));
        t.runAtTick(12,()-> {
            remembered(t,twelve,Blocks.PISTON);remembered(t,twelve.up(),Blocks.PISTON_HEAD);
            t.assertTrue(t.getWorld().getBlockState(twelve).get(PistonBlock.EXTENDED),"twelve whole pieces may extend");
            for(int n=2;n<=13;n++) remembered(t,twelve.up(n),Blocks.STONE);
            remembered(t,thirteen,Blocks.PISTON);
            t.assertFalse(t.getWorld().getBlockState(thirteen).get(PistonBlock.EXTENDED),"thirteenth whole piece rejects extension");
            for(int n=1;n<=13;n++) remembered(t,thirteen.up(n),Blocks.STONE);
            t.assertTrue(t.getWorld().getBlockState(thirteen.up(14)).isAir(),"thirteen-piece rejection leaves destination empty");
            t.assertTrue(NativeSeats.seat(t.getWorld(),thirteen.up(14))==null,"thirteen-piece rejection writes no destination seat");t.complete();
        });
    }
}
