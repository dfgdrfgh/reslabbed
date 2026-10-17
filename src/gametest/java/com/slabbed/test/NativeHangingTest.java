package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
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

/** Hanging item use must seat the actual entity on the selected physical wall once. */
public final class NativeHangingTest {
    private static final double EPS=1e-7;
    private record Mounted(BlockPos wall,PlayerEntity player,AbstractDecorationEntity entity) {}

    private static Mounted mount(TestContext t,Item item) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(4,4,4));
        world.setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
        t.assertTrue(CellWorld.place(world,base.getX(),base.getZ(),base.getY()*2+1,Blocks.STONE.getDefaultState()),
                "fixture creates a whole physical wall half a block above its cell");
        var wall=base.up();
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+3,base.getY(),base.getZ()+.5);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item,2));
        var point=new Vec3d(base.getX()+1,base.getY()+1.25,base.getZ()+.5);
        var result=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,
                new BlockHitResult(point,Direction.EAST,wall,false)));
        t.assertTrue(result.isAccepted(),"real hanging item use accepted on the physical wall: "+item+" "+result);
        var entities=world.getEntitiesByClass(AbstractDecorationEntity.class,new Box(wall.east()).expand(2),
                entity->!entity.isRemoved() && (item==Items.PAINTING?entity instanceof PaintingEntity:
                        entity instanceof ItemFrameEntity && (item==Items.GLOW_ITEM_FRAME)==(entity instanceof GlowItemFrameEntity)));
        t.assertTrue(entities.size()==1,"one hanging entity spawned by item use: "+item+" count="+entities.size());
        t.assertTrue(player.getMainHandStack().getCount()==1,"hanging placement consumes exactly once");
        var entity=entities.getFirst();
        t.assertTrue(Math.abs(entity.getY()-(base.getY()+1))<EPS,
                "entity position is centered on the whole physical wall, not the upper fragment: "+entity.getPos());
        t.assertTrue(Math.abs(entity.getBoundingBox().getCenter().y-(base.getY()+1))<EPS,
                "physical bounding box uses the same remembered wall seat");
        t.assertTrue(entity.canStayAttached(),"physical backing face supports the hanging entity");
        return new Mounted(wall,player,entity);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void itemFrameUsesPhysicalWallAndKeepsItsContents(TestContext t) {
        frame(t,Items.ITEM_FRAME);t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void glowFrameUsesPhysicalWallAndKeepsItsContents(TestContext t) {
        frame(t,Items.GLOW_ITEM_FRAME);t.complete();
    }

    private static void frame(TestContext t,Item item) {
        var mounted=mount(t,item);var frame=(ItemFrameEntity)mounted.entity();
        mounted.player().setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DIAMOND,2));
        t.assertTrue(frame.interact(mounted.player(),Hand.MAIN_HAND).isAccepted(),"native frame accepts a held item");
        t.assertTrue(frame.getHeldItemStack().isOf(Items.DIAMOND),"frame retains its native item content");
        t.assertTrue(mounted.player().getMainHandStack().getCount()==1,"frame content consumes exactly once");
        var box=frame.getBoundingBox();var center=box.getCenter();
        var start=center.add(2,0,0);var end=center.add(-2,0,0);
        var hit=ProjectileUtil.raycast(mounted.player(),start,end,new Box(start,end).expand(.25),
                entity->entity==frame,start.squaredDistanceTo(end));
        t.assertTrue(hit!=null && hit.getEntity()==frame,"entity ray targets the frame at its physical position");
        rememberAndReload(t,mounted);
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void paintingUsesPhysicalBackingAndRemembersItsSavedSeat(TestContext t) {
        rememberAndReload(t,mount(t,Items.PAINTING));t.complete();
    }

    private static void rememberAndReload(TestContext t,Mounted mounted) {
        var world=t.getWorld();var entity=mounted.entity();var before=entity.getBoundingBox();var pos=entity.getPos();
        world.setBlockState(mounted.wall().north(),Blocks.DIRT.getDefaultState(),Block.NOTIFY_ALL);
        world.removeBlock(mounted.wall().north(),false);
        t.assertTrue(entity.getBoundingBox().equals(before) && entity.getPos().equals(pos),"neighbor changes never reseat the hanging entity");
        // Rebuild the backing at a different physical height. The entity remembers its own seat.
        world.removeBlock(mounted.wall(),false);
        world.setBlockState(mounted.wall(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        var saved=new NbtCompound();entity.writeNbt(saved);
        AbstractDecorationEntity restored=entity instanceof PaintingEntity
                ?new PaintingEntity(EntityType.PAINTING,world):entity instanceof GlowItemFrameEntity
                ?new GlowItemFrameEntity(EntityType.GLOW_ITEM_FRAME,world):new ItemFrameEntity(EntityType.ITEM_FRAME,world);
        restored.readNbt(saved);
        t.assertTrue(restored.getBoundingBox().equals(before) && restored.getPos().equals(pos),
                "entity NBT restores its own physical seat after the wall is rebuilt at another height");
        if(restored instanceof ItemFrameEntity frame)
            t.assertTrue(frame.getHeldItemStack().isOf(Items.DIAMOND),"frame content survives entity NBT round trip");
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void losingPhysicalBackingFailsVanillaHangingSurvivalWithoutReseating(TestContext t) {
        var mounted=mount(t,Items.ITEM_FRAME);var entity=mounted.entity();var box=entity.getBoundingBox();
        t.getWorld().removeBlock(mounted.wall(),false);
        t.assertFalse(entity.canStayAttached(),"removing the whole wall removes physical hanging support");
        t.assertTrue(entity.getBoundingBox().equals(box),"support loss changes survival, never the entity seat");
        t.complete();
    }
}
