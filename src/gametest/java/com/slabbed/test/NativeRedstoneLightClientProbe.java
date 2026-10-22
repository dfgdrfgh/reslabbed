package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

/** Live terrain vertices and settled item light, including native grass families. */
public final class NativeRedstoneLightClientProbe {
    private record Fixture(BlockPos pos,BlockState state) {}
    private static final List<Fixture> MODELS=new ArrayList<>();
    private static final BlockState[] SURFACES={Blocks.GRASS_BLOCK.getDefaultState(),Blocks.PODZOL.getDefaultState(),
            Blocks.MYCELIUM.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState(),Blocks.PODZOL.getDefaultState()};
    private static int stage,ticks,started;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private NativeRedstoneLightClientProbe() {}
    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        if(failure!=null)throw new AssertionError("redstone/light fixture setup failed",failure);
        check(++ticks<500,"redstone/light probe timed out stage="+stage);
        NativeRenderAudit.tick(client);
        if(pending)return false;
        if(stage==0) {
            pending=true;
            client.getServer().execute(()->{
                try {setup(client);}
                catch(Throwable error) {failure=error;}
                finally {pending=false;}
            });
            stage=1;return false;
        }
        if(stage==1) {
            for(var fixture:MODELS)if(client.world.getBlockState(fixture.pos())!=fixture.state()
                    || NativeSeats.offset(client.world,fixture.pos())!=-.5) {
                check(ticks<160,"redstone fixture mismatch: "+fixture+" actual="+client.world.getBlockState(fixture.pos())
                        +" seat="+NativeSeats.seat(client.world,fixture.pos()));return false;
            }
            for(int i=0;i<SURFACES.length;i++)if(client.world.getBlockState(surface(i))!=SURFACES[i]
                    || NativeSeats.offset(client.world,surface(i))!=(i==3?0:-.5)) {
                check(ticks<160,"surface fixture mismatch at "+surface(i)+" actual="+client.world.getBlockState(surface(i))
                        +" seat="+NativeSeats.seat(client.world,surface(i)));return false;
            }
            for(var fixture:MODELS) {
                var shape=fixture.state().getOutlineShape(client.world,fixture.pos()).getBoundingBox();
                check(shape.minY<0,"redstone outline must use slab height: "+fixture);
                NativeRenderAudit.beginPistonModel(client,fixture.pos());
            }
            started=ticks;stage=2;return false;
        }
        if(stage==2) {
            if(ticks-started<60)return false;
            // Check light even if a model never reached the seated render path.
            verifyItems(client);
            verifyDust(client);
            for(var fixture:MODELS)if(!NativeRenderAudit.readyModel(fixture.pos())) {
                check(ticks-started<220,"redstone model never emitted seated geometry: "+fixture
                        +" selected="+client.getBlockRenderManager().getModel(fixture.state()).getClass().getName());
                return false;
            }
            for(var fixture:MODELS)NativeRenderAudit.verifyModel(fixture.pos());
            System.out.println("PHYSICAL_REDSTONE_MODEL_HEIGHT_PASS torch_lit_unlit repeater_all_delays_powered_unpowered");
            System.out.println("PHYSICAL_DROPPED_ITEM_LIGHT_PASS grass podzol mycelium flush_control enclosed_control");
            System.out.println("PHYSICAL_SLAB_DUST_LIGHT_PASS grass podzol mycelium flush_control enclosed_control");
            stage=99;return true;
        }
        return false;
    }
    private static BlockPos surface(int i) {return new BlockPos(48+i*3,64,24);}
    private static void setup(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        player.teleport(world,54.5,66,7.5,0,35);
        world.setTimeOfDay(1000);
        for(int x=47;x<=62;x++)for(int z=11;z<=25;z++)for(int y=63;y<=68;y++)
            world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        MODELS.clear();
        MODELS.add(new Fixture(new BlockPos(48,64,12),Blocks.REDSTONE_TORCH.getDefaultState()));
        MODELS.add(new Fixture(new BlockPos(51,64,12),Blocks.REDSTONE_TORCH.getDefaultState().with(Properties.LIT,false)));
        for(int delay=1;delay<=4;delay++)for(boolean powered:new boolean[]{false,true}) {
            int index=MODELS.size();
            MODELS.add(new Fixture(new BlockPos(48+(index%5)*3,64,12+(index/5)*4),
                    Blocks.REPEATER.getDefaultState().with(Properties.DELAY,delay).with(Properties.POWERED,powered)));
        }
        for(var fixture:MODELS) {
            world.setBlockState(fixture.pos().down(),Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_LISTENERS);
            if(fixture.state().isOf(Blocks.REDSTONE_TORCH) && !fixture.state().get(Properties.LIT)) {
                world.setBlockState(fixture.pos().down(),Blocks.REDSTONE_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);
                NativeSeats.put(world,fixture.pos().down(),new NativeSeats.Seat(-1,"minecraft:redstone_block"));
            }
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(fixture.state().getBlock()));
            var hit=new BlockHitResult(Vec3d.ofBottomCenter(fixture.pos().down()).add(0,.5,0),Direction.UP,fixture.pos().down(),false);
            check(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"redstone item placement accepted: "+fixture);
            world.setBlockState(fixture.pos(),fixture.state(),Block.NOTIFY_LISTENERS);
            check(NativeSeats.offset(world,fixture.pos())==-.5,"real redstone item retains slab seat");
        }
        for(var fixture:MODELS)if(fixture.state().isOf(Blocks.REPEATER) && fixture.state().get(Properties.POWERED)) {
            var source=fixture.pos().offset(fixture.state().get(Properties.HORIZONTAL_FACING));
            world.setBlockState(source,Blocks.REDSTONE_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);
            NativeSeats.put(world,source,new NativeSeats.Seat(-1,"minecraft:redstone_block"));
        }
        world.getEntitiesByClass(ItemEntity.class,new Box(47,63,23,61,68,26),entity->true).forEach(ItemEntity::discard);
        for(int i=0;i<SURFACES.length;i++) {
            var pos=surface(i);var state=SURFACES[i];
            world.setBlockState(pos.down(),Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_LISTENERS);
            world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);
            NativeSeats.put(world,pos,new NativeSeats.Seat(i==3?0:-1,Registries.BLOCK.getId(state.getBlock()).toString()));
            if(i==4) {
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                    world.setBlockState(pos.add(x,1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
                    if(x!=0 || z!=0)world.setBlockState(pos.add(x,0,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
                }
            }
            var item=new ItemEntity(world,pos.getX()+.5,pos.getY()+(i==4?.75:1.5),pos.getZ()+.5,new ItemStack(Items.DIAMOND));
            item.setVelocity(0,0,0);item.setPickupDelayInfinite();world.spawnEntity(item);
        }
    }
    private static void verifyItems(MinecraftClient client) {
        for(int i=0;i<SURFACES.length;i++) {
            var pos=surface(i);
            var items=client.world.getEntitiesByClass(ItemEntity.class,new Box(pos).expand(.1,2,.1),entity->entity.getStack().isOf(Items.DIAMOND));
            check(items.size()==1,"one settled test item at "+pos+": "+items.size());
            var item=items.getFirst();double top=pos.getY()+(i==3?1:.5);
            check(item.isOnGround() && Math.abs(item.getY()-top)<.002,"item must settle on actual top of "+SURFACES[i]+": "+item.getPos());
            int light=client.getEntityRenderDispatcher().getRenderer(item).getLight(item,1);
            int sky=(light>>20)&15,expected=client.world.getLightLevel(LightType.SKY,pos.up());
            System.out.println("PHYSICAL_DROPPED_ITEM_LIGHT_OBSERVED surface="+SURFACES[i]+" y="+item.getY()+" light="+light+" sky="+sky+" expectedSky="+expected);
            check(expected==(i==4?0:15),"item fixture must retain its outdoor or enclosed light level");
            check(sky==expected,"item above shifted "+SURFACES[i]+" samples the opaque owner instead of exposed air: "+sky+" expected="+expected);
        }
    }
    private static void verifyDust(MinecraftClient client) {
        for(int i=0;i<SURFACES.length;i++) {
            var pos=surface(i);
            double top=pos.getY()+(i==3?1:.5);
            var dust=new net.minecraft.client.particle.BlockDustParticle(client.world,pos.getX()+.5,top+.05,pos.getZ()+.5,
                    0,0,0,Blocks.OAK_SLAB.getDefaultState(),BlockPos.ofFloored(pos.getX()+.5,top+.05,pos.getZ()+.5));
            int light=dust.getBrightness(1),sky=(light>>20)&15;
            int expected=client.world.getLightLevel(LightType.SKY,pos.up());
            System.out.println("PHYSICAL_SLAB_DUST_LIGHT_OBSERVED surface="+SURFACES[i]+" sky="+sky+" expected="+expected);
            check(sky==expected,"slab dust above shifted "+SURFACES[i]+" must sample exposed air: "+sky+" expected="+expected);
            cachedLight(dust,light,"surface="+SURFACES[i]);
            if(i==4) {
                check(client.world.getLightLevel(LightType.BLOCK,pos.up())==0,"enclosed dust fixture has no block light source");
                check(light==0,"enclosed dust must retain darkness in both light channels: "+light);
            }
            for(double x:new double[]{.02,.5,.98})for(double z:new double[]{.02,.5,.98}) {
                dust.setPos(pos.getX()+x,top+.2,pos.getZ()+z);
                dust.move(0,-.45,0);
                double settled=dust.getBoundingBox().minY;
                check(Math.abs(settled-top)<1e-7,"dust penetrated "+SURFACES[i]+": y="+settled+" surface="+top+" x="+x+" z="+z);
                check(((dust.getBrightness(1)>>20)&15)==expected,"collision-settled dust sampled opaque "+SURFACES[i]+" at y="+settled);
                if(i==4)check(dust.getBrightness(1)==0,"settled enclosed dust leaked light at x="+x+" z="+z);
                cachedLight(dust,dust.getBrightness(1),"settled surface="+SURFACES[i]+" x="+x+" z="+z);
            }
            System.out.println("PHYSICAL_SLAB_DUST_COLLISION_PASS surface="+SURFACES[i]+" samples=9 top="+top+" sky="+expected);
            if(i!=3) {
                var inside=new net.minecraft.client.particle.BlockDustParticle(client.world,pos.getX()+.5,pos.getY()+.25,pos.getZ()+.5,
                        0,0,0,Blocks.OAK_SLAB.getDefaultState(),pos);
                check(((inside.getBrightness(1)>>20)&15)==0,"particles inside physical soil must retain its dark sample");
                cachedLight(inside,inside.getBrightness(1),"inside surface="+SURFACES[i]);
            }
        }
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("asyncparticles"))
            System.out.println("PHYSICAL_ASYNC_DUST_CACHE_PASS outdoor enclosed interior first_gpu_upload edge_contacts");
    }
    private static void cachedLight(net.minecraft.client.particle.BlockDustParticle dust,int expected,String fixture) {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("asyncparticles"))return;
        try {
            dust.getClass().getMethod("asyncparticles$refresh").invoke(dust);
            int cached=(Integer)dust.getClass().getMethod("asyncparticles$getCachedLight").invoke(dust);
            check(cached==expected,"AsyncParticles cached physical light differs: "+fixture+" cached="+cached+" expected="+expected);
            int gpu=(Integer)dust.getClass().getMethod("asyncparticles$getGpuLightCoords",float.class).invoke(dust,0f);
            check(gpu==expected,"AsyncParticles GPU physical light differs: "+fixture+" gpu="+gpu+" expected="+expected);
        } catch(ReflectiveOperationException error) {throw new AssertionError("AsyncParticles light API unavailable",error);}
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
