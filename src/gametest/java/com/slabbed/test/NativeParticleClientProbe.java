package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.ParticleUtil;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Real vanilla emission requests in a connected production client, with no test-side translation. */
public final class NativeParticleClientProbe {
    private record Fixture(String name,BlockState state) {}
    private static final Fixture[] FIXTURES={
            new Fixture("campfire",Blocks.CAMPFIRE.getDefaultState()),
            new Fixture("candle",Blocks.CANDLE.getDefaultState().with(Properties.LIT,true)),
            new Fixture("lever_click",Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE,BlockFace.FLOOR)),
            new Fixture("lever_ambient",Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE,BlockFace.FLOOR).with(Properties.POWERED,true)),
            new Fixture("torch",Blocks.TORCH.getDefaultState()),
            new Fixture("wall_torch",Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH)),
            new Fixture("distributed",Blocks.STONE.getDefaultState()),
            new Fixture("pot",Blocks.DECORATED_POT.getDefaultState())
    };
    private static final long SEED=7181026L;
    private static final List<String> FAILURES=new ArrayList<>();
    private static int stage,ticks;
    private static volatile boolean pending,ready;
    private static volatile Throwable serverFailure;
    private NativeParticleClientProbe() {}

    /** Returns false while this probe owns setup; call before the native functional probe's stage zero. */
    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        check(++ticks<360,"native particle probe timed out at stage "+stage);
        if(serverFailure!=null)throw new AssertionError("native particle server fixture failed",serverFailure);
        if(pending)return false;
        if(stage==0) {
            pending=true;
            client.getServer().execute(()->{
                try {setup(client);ready=true;}
                catch(Throwable failure) {serverFailure=failure;}
                finally {pending=false;}
            });
            stage=1;return false;
        }
        if(stage==1) {
            if(!ready || !received(client))return false;
            runClientChecks(client);
            pending=true;
            client.getServer().execute(()->{
                try {attempt("pot_insertion",()->potInsertion(client));}
                catch(Throwable failure) {serverFailure=failure;}
                finally {pending=false;}
            });
            stage=2;return false;
        }
        if(stage==2) {
            check(FAILURES.isEmpty(),"native particle parity failures: "+String.join("; ",FAILURES));
            System.out.println("PHYSICAL_NATIVE_PARTICLES_PASS");stage=99;return true;
        }
        return false;
    }

    private static BlockPos position(int fixture,boolean shifted) {
        return new BlockPos(shifted?42:34,64,8+fixture*3);
    }
    private static void setup(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        for(int i=0;i<FIXTURES.length;i++)for(boolean shifted:new boolean[]{false,true}) {
            var pos=position(i,shifted);
            for(int y=63;y<=66;y++)for(int z=-1;z<=1;z++)
                world.setBlockState(new BlockPos(pos.getX(),y,pos.getZ()+z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
            world.setBlockState(pos.down(),(shifted?Blocks.OAK_SLAB:Blocks.STONE).getDefaultState(),Block.NOTIFY_LISTENERS);
            if(i==5)world.setBlockState(pos.south(),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            var state=FIXTURES[i].state();
            world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);
            NativeSeats.put(world,pos,new NativeSeats.Seat(shifted?-1:0,Registries.BLOCK.getId(state.getBlock()).toString()));
            if(i==0) {
                var fire=(CampfireBlockEntity)world.getBlockEntity(pos);
                check(fire!=null && fire.addItem(null,new ItemStack(Items.BEEF),100000),"campfire accepts real cooking inventory");
            }
            world.getChunkManager().markForUpdate(pos);
        }
        var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        player.getAbilities().flying=true;player.sendAbilitiesUpdate();
        player.networkHandler.requestTeleport(38.5,66,18.5,0,35);
    }
    private static boolean received(MinecraftClient client) {
        for(int i=0;i<FIXTURES.length;i++)for(boolean shifted:new boolean[]{false,true}) {
            var pos=position(i,shifted);
            if(!client.world.getBlockState(pos).isOf(FIXTURES[i].state().getBlock()))return false;
            var seat=NativeSeats.seat(client.world,pos);
            if(seat==null || seat.halfOffset()!=(shifted?-1:0))return false;
            if(i==0 && (!(client.world.getBlockEntity(pos) instanceof CampfireBlockEntity fire)
                    || fire.getItemsBeingCooked().get(0).isEmpty()))return false;
        }
        return true;
    }
    private static void runClientChecks(MinecraftClient client) {
        var world=client.world;
        compare(client,"campfire_smoke_direct",0,pos->{
            for(int n=0;n<8;n++)CampfireBlock.spawnSmokeParticle(world,pos,false,true);
        });
        compare(client,"campfire_cooking",0,pos->{
            var fire=(CampfireBlockEntity)world.getBlockEntity(pos);
            for(int n=0;n<64;n++)CampfireBlockEntity.clientTick(world,pos,world.getBlockState(pos),fire);
        });
        compare(client,"campfire_ambient",0,pos->ambient(client,pos));
        compare(client,"candle_extinguish",1,pos->AbstractCandleBlock.extinguish(client.player,world.getBlockState(pos),world,pos));
        compare(client,"lever_click",2,pos->{
            var hit=new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
            check(world.getBlockState(pos).onUse(world,client.player,hit).isAccepted(),"vanilla lever click accepted");
        });
        compare(client,"lever_ambient",3,pos->ambient(client,pos));
        compare(client,"torch_direct",4,pos->display(client,pos));
        compare(client,"torch_ambient",4,pos->ambient(client,pos));
        compare(client,"wall_torch_direct",5,pos->display(client,pos));
        compare(client,"wall_torch_ambient",5,pos->ambient(client,pos));
        compare(client,"distributed_shape",6,pos->ParticleUtil.spawnParticlesAround(world,pos,64,ParticleTypes.HAPPY_VILLAGER));
    }
    private static void display(MinecraftClient client,BlockPos pos) {
        var state=client.world.getBlockState(pos);
        state.getBlock().randomDisplayTick(state,client.world,pos,Random.create(SEED));
    }
    private static void ambient(MinecraftClient client,BlockPos pos) {
        var random=Random.create(SEED);
        // Radius one always selects the requested owner, through vanilla's real display dispatcher.
        for(int n=0;n<64;n++)client.world.randomBlockDisplayTick(pos.getX(),pos.getY(),pos.getZ(),1,random,null,new BlockPos.Mutable());
    }
    private static void compare(MinecraftClient client,String name,int fixture,Consumer<BlockPos> action) {
        attempt(name,()->{
            var flat=position(fixture,false);var shifted=position(fixture,true);
            client.world.getRandom().setSeed(SEED);
            var a=NativeParticleAudit.capture(()->action.accept(flat));
            client.world.getRandom().setSeed(SEED);
            var b=NativeParticleAudit.capture(()->action.accept(shifted));
            compareRequests(name,flat,shifted,a,b);
            if(name.equals("campfire_cooking"))check(a.stream().anyMatch(e->e.effect().getType()==ParticleTypes.SMOKE),"cooking probe must observe food smoke");
        });
    }
    private static void potInsertion(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        var flat=position(7,false);var shifted=position(7,true);
        Consumer<BlockPos> insert=pos->{
            var hit=new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
            world.getBlockState(pos).onUseWithItem(new ItemStack(Items.DIAMOND),world,player,Hand.MAIN_HAND,hit);
        };
        var a=NativeParticleAudit.capture(()->insert.accept(flat));
        var b=NativeParticleAudit.capture(()->insert.accept(shifted));
        check(a.size()==1 && a.getFirst().count()==7 && a.getFirst().effect().getType()==ParticleTypes.DUST_PLUME,
                "real decorated-pot insertion emits one vanilla seven-particle plume");
        compareRequests("pot_insertion",flat,shifted,a,b);
    }
    private static void compareRequests(String name,BlockPos flat,BlockPos shifted,
            List<NativeParticleAudit.Emission> a,List<NativeParticleAudit.Emission> b) {
        check(!a.isEmpty(),name+" emitted no particles; capture or effect premise is broken");
        check(a.size()==b.size(),name+" changed request count: "+a.size()+" versus "+b.size());
        for(int n=0;n<a.size();n++) {
            var first=a.get(n);var second=b.get(n);
            check(first.route().equals(second.route()) && first.effect().getType()==second.effect().getType()
                    && first.count()==second.count(),name+" changed particle type, route or count at "+n);
            check(close(first.x()-flat.getX(),second.x()-shifted.getX())
                    && close(first.z()-flat.getZ(),second.z()-shifted.getZ()),name+" changed local X/Z at "+n);
            check(close(first.a(),second.a()) && close(first.b(),second.b()) && close(first.c(),second.c())
                    && close(first.speed(),second.speed()),name+" changed velocity/spread/speed at "+n);
            double delta=(second.y()-shifted.getY())-(first.y()-flat.getY());
            check(close(delta,-.5),name+" Y must move exactly -0.5 at "+n+"; actual delta="+delta);
        }
    }
    private static void attempt(String name,Runnable test) {
        try {test.run();System.out.println("PHYSICAL_NATIVE_PARTICLE_PASS "+name);}
        catch(AssertionError failure) {
            FAILURES.add(name+": "+failure.getMessage());
            System.out.println("PHYSICAL_NATIVE_PARTICLE_FAIL "+name+" "+failure.getMessage());
        }
    }
    private static boolean close(double a,double b) {return Math.abs(a-b)<1e-9;}
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
