package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
            Blocks.MYCELIUM.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState()};
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
                    || NativeSeats.offset(client.world,fixture.pos())!=-.5)return false;
            for(int i=0;i<SURFACES.length;i++)if(client.world.getBlockState(surface(i))!=SURFACES[i]
                    || NativeSeats.offset(client.world,surface(i))!=(i==3?0:-.5))return false;
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
            for(var fixture:MODELS)if(!NativeRenderAudit.readyModel(fixture.pos())) {
                check(ticks-started<220,"redstone model never emitted seated geometry: "+fixture
                        +" selected="+client.getBlockRenderManager().getModel(fixture.state()).getClass().getName());
                return false;
            }
            for(var fixture:MODELS)NativeRenderAudit.verifyModel(fixture.pos());
            System.out.println("PHYSICAL_REDSTONE_MODEL_HEIGHT_PASS torch_lit_unlit repeater_all_delays_powered_unpowered");
            System.out.println("PHYSICAL_DROPPED_ITEM_LIGHT_PASS grass podzol mycelium flush_control");
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
            world.setBlockState(fixture.pos(),fixture.state(),Block.NOTIFY_LISTENERS);
            NativeSeats.put(world,fixture.pos(),new NativeSeats.Seat(-1,Registries.BLOCK.getId(fixture.state().getBlock()).toString()));
        }
        world.getEntitiesByClass(ItemEntity.class,new Box(47,63,23,61,68,26),entity->true).forEach(ItemEntity::discard);
        for(int i=0;i<SURFACES.length;i++) {
            var pos=surface(i);var state=SURFACES[i];
            world.setBlockState(pos.down(),Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_LISTENERS);
            world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);
            NativeSeats.put(world,pos,new NativeSeats.Seat(i==3?0:-1,Registries.BLOCK.getId(state.getBlock()).toString()));
            var item=new ItemEntity(world,pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5,new ItemStack(Items.DIAMOND));
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
            check(expected==15,"outdoor item fixture must have open sky");
            check(sky==expected,"item above shifted "+SURFACES[i]+" samples the opaque owner instead of exposed air: "+sky+" expected="+expected);
        }
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
