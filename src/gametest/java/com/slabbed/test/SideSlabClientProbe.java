package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** Actual placement packets join aimed side halves beside crossing static and native cubes. */
public final class SideSlabClientProbe {
    private record Fixture(BlockPos base,Direction face,boolean nativeCube,boolean upperFirst) {
        BlockPos target() {return base.offset(face);}
        String name() {return (nativeCube?"native_grass":"carrier")+"_"+face+"_"+(upperFirst?"upper_first":"lower_first");}
    }
    private static final List<Fixture> FIXTURES=List.of(
            new Fixture(new BlockPos(84,70,18),Direction.EAST,false,false),
            new Fixture(new BlockPos(88,70,18),Direction.WEST,false,true),
            new Fixture(new BlockPos(92,70,18),Direction.NORTH,true,false),
            new Fixture(new BlockPos(96,70,18),Direction.SOUTH,true,true));
    private static int stage,ticks,index;
    private static volatile boolean pending,confirmed;
    private static volatile Throwable failure;
    private static Vec3d arrival;
    private SideSlabClientProbe() {}

    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        check(++ticks<400,"side slab packet timeout stage="+stage+" case="+index);
        if(failure!=null)throw new AssertionError("side slab server fixture failed",failure);
        if(pending)return false;
        if(stage==0) {
            server(client,()->{
                var world=client.getServer().getOverworld();
                for(var fixture:FIXTURES) {
                    clear(world,fixture);
                    var base=fixture.base();
                    world.setBlockState(base,Blocks.STONE_SLAB.getDefaultState(),Block.NOTIFY_ALL);
                    if(fixture.nativeCube()) {
                        world.setBlockState(base.up(),Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                        NativeSeats.put(world,base.up(),new NativeSeats.Seat(-1,"minecraft:grass_block"));
                    } else check(CellWorld.place(world,base.getX(),base.getZ(),base.getY()*2+1,Blocks.STONE.getDefaultState()),"crossing side source placed");
                    sourceIntact(world,fixture);
                }
                teleport(client,FIXTURES.getFirst(),FIXTURES.getFirst().upperFirst());
                confirmed=true;
            });stage=1;return false;
        }
        if(stage==8) {
            if(!confirmed)return false;
            for(var complete:FIXTURES)if(!client.world.getBlockState(complete.base()).isAir()
                    || !client.world.getBlockState(complete.base().up()).isAir()
                    || !empty(client.world,complete.target()) || !empty(client.world,complete.target().up()))return false;
            System.out.println("PHYSICAL_SIDE_SLAB_PACKET_PASS carrier native_grass lower_first upper_first");stage=99;return true;
        }
        var fixture=FIXTURES.get(index);
        client.player.setVelocity(Vec3d.ZERO);
        if(stage==1 || stage==7) {
            if(!confirmed || !arrived(client))return false;
            for(var existing:FIXTURES)if(!sourceReady(client.world,existing))return false;
            if(!empty(client.world,fixture.target()) || !empty(client.world,fixture.target().up()))return false;
            equip(client);stage=2;return false;
        }
        if(stage==2) {
            place(client,fixture,fixture.upperFirst());stage=3;return false;
        }
        if(stage==3) {
            if(!single(client.world,fixture,fixture.upperFirst()))return false;
            sourceIntact(client.world,fixture);confirmed=false;
            server(client,()->{
                var world=client.getServer().getOverworld();
                check(single(world,fixture,fixture.upperFirst()),"server received first aimed half: "+fixture.name());
                sourceIntact(world,fixture);
                System.out.println("PHYSICAL_SIDE_SLAB_FIRST_PACKET_CONFIRMED fixture="+fixture.name()
                        +" origin="+(fixture.base().getY()*2+1+(fixture.upperFirst()?1:0)));
                teleport(client,fixture,!fixture.upperFirst());confirmed=true;
            });stage=4;return false;
        }
        if(stage==4) {
            if(!confirmed || !arrived(client))return false;
            check(single(client.world,fixture,fixture.upperFirst()),"first half remains fixed while aiming at opposite source half: "+fixture.name());
            place(client,fixture,!fixture.upperFirst());stage=5;return false;
        }
        if(stage==5) {
            if(!doubled(client.world,fixture))return false;
            sourceIntact(client.world,fixture);confirmed=false;
            server(client,()->{
                var world=client.getServer().getOverworld();
                check(doubled(world,fixture),"server confirmed one double spanning both physical cells: "+fixture.name());
                sourceIntact(world,fixture);
                System.out.println("PHYSICAL_SIDE_SLAB_DOUBLE_PACKET_CONFIRMED fixture="+fixture.name()
                        +" origin="+(fixture.base().getY()*2+1)+" lower="+fixture.target()+" upper="+fixture.target().up());
                confirmed=true;
            });stage=6;return false;
        }
        if(stage==6) {
            if(!confirmed)return false;
            check(doubled(client.world,fixture),"client retains confirmed whole double: "+fixture.name());
            index++;
            if(index<FIXTURES.size()) {
                var next=FIXTURES.get(index);confirmed=false;
                server(client,()->{teleport(client,next,next.upperFirst());confirmed=true;});stage=7;return false;
            }
            confirmed=false;
            server(client,()->{
                var world=client.getServer().getOverworld();
                for(var complete:FIXTURES) {
                    check(doubled(world,complete),"earlier pair remains fixed: "+complete.name());sourceIntact(world,complete);
                }
                for(var complete:FIXTURES)clear(world,complete);
                confirmed=true;
            });stage=8;return false;
        }
        return false;
    }
    private static void equip(MinecraftClient client) {
        var stack=new ItemStack(Items.OAK_SLAB,16);client.player.setStackInHand(Hand.MAIN_HAND,stack);
        client.interactionManager.clickCreativeStack(stack.copy(),36+client.player.getInventory().selectedSlot);
    }
    private static BlockHitResult hit(Fixture fixture,boolean upper) {
        var face=fixture.face();
        var point=Vec3d.ofBottomCenter(fixture.base()).add(face.getOffsetX()*.5,upper?1.25:.75,face.getOffsetZ()*.5);
        var owner=fixture.nativeCube()?fixture.base().up():BlockPos.ofFloored(point.subtract(Vec3d.of(face.getVector()).multiply(1e-5)));
        return new BlockHitResult(point,face,owner,false);
    }
    private static void place(MinecraftClient client,Fixture fixture,boolean upper) {
        var hit=hit(fixture,upper);client.crosshairTarget=hit;
        var delta=hit.getPos().subtract(client.player.getEyePos());
        client.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        client.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        check(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit).isAccepted(),
                "client accepted actual side placement packet: "+fixture.name()+" upper="+upper);
    }
    private static void teleport(MinecraftClient client,Fixture fixture,boolean upper) {
        var hit=hit(fixture,upper);var face=fixture.face();
        var feet=hit.getPos().add(face.getOffsetX()*2.5,-1.62,face.getOffsetZ()*2.5);
        var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        player.teleport(client.getServer().getOverworld(),feet.x,feet.y,feet.z,0,0);
        player.getAbilities().flying=true;player.sendAbilitiesUpdate();arrival=feet;
    }
    private static boolean arrived(MinecraftClient client) {
        return arrival!=null && client.player.getPos().squaredDistanceTo(arrival)<.02;
    }
    private static boolean single(World world,Fixture fixture,boolean upper) {
        int origin=fixture.base().getY()*2+1+(upper?1:0);
        var pos=new BlockPos(fixture.target().getX(),Math.floorDiv(origin,2),fixture.target().getZ());
        var contents=CellWorld.contents(world,pos);
        if(contents==null || contents.fragments().size()!=1)return false;
        var part=contents.fragments().getFirst();
        return part.offset()==origin-pos.getY()*2 && part.length()==1 && part.value().isOf(Blocks.OAK_SLAB)
                && empty(world,upper?fixture.target():fixture.target().up());
    }
    private static boolean doubled(World world,Fixture fixture) {
        for(int y=0;y<=1;y++) {
            var contents=CellWorld.contents(world,fixture.target().up(y));
            if(contents==null || contents.fragments().size()!=1)return false;
            var part=contents.fragments().getFirst();
            if(part.offset()!=1-y*2 || part.length()!=2 || !part.value().isOf(Blocks.OAK_SLAB)
                    || part.value().get(SlabBlock.TYPE)!=SlabType.DOUBLE)return false;
        }
        return empty(world,fixture.target().down()) && empty(world,fixture.target().up(2));
    }
    private static boolean empty(World world,BlockPos pos) {
        var contents=CellWorld.contents(world,pos);return contents!=null && contents.fragments().isEmpty() && world.getBlockState(pos).isAir();
    }
    private static boolean sourceReady(World world,Fixture fixture) {
        var base=fixture.base();
        if(fixture.nativeCube())return world.getBlockState(base).isOf(Blocks.STONE_SLAB)
                && world.getBlockState(base.up()).isOf(Blocks.GRASS_BLOCK) && NativeSeats.offset(world,base.up())==-.5;
        var lower=CellWorld.contents(world,base);var upper=CellWorld.contents(world,base.up());
        return lower!=null && lower.fragments().size()==2 && upper!=null && upper.fragments().size()==1;
    }
    private static void sourceIntact(World world,Fixture fixture) {
        check(sourceReady(world,fixture),"original source remains: "+fixture.name());
        if(fixture.nativeCube())return;
        var base=fixture.base();
        var lower=CellWorld.contents(world,base);var upper=CellWorld.contents(world,base.up());
        check(lower.fragments().stream().anyMatch(p->p.offset()==0 && p.length()==1 && p.value().isOf(Blocks.STONE_SLAB))
                && lower.fragments().stream().anyMatch(p->p.offset()==1 && p.length()==2 && p.value().isOf(Blocks.STONE))
                && upper.fragments().getFirst().offset()==-1 && upper.fragments().getFirst().length()==2
                && upper.fragments().getFirst().value().isOf(Blocks.STONE),"source geometry retains its exact original half-grid origin: "+fixture.name());
    }
    private static void clear(World world,Fixture fixture) {
        var base=fixture.base();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=2;y++) {
            var pos=base.add(x,y,z);com.slabbed.cell.SupplementalCells.remove(world,pos);NativeSeats.remove(world,pos);
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
    }
    private static void server(MinecraftClient client,Runnable action) {
        pending=true;client.getServer().execute(()->{
            try {action.run();}catch(Throwable error) {failure=error;}finally {pending=false;}
        });
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
