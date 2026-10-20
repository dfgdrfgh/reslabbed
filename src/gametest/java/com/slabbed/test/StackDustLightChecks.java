package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.cell.SupplementalCells;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Break packets compare untouched debris at odd and even levels of actual item-built stacks. */
public final class StackDustLightChecks {
    private record Fixture(BlockPos base,boolean grass,boolean slab,int pair) {
        int selected() {return (pair-1)*2+(slab?0:1);}
        int origin(int piece) {return base.getY()*2+(piece/2)*3+(piece%2);}
        String name() {return (grass?"grass":"stone")+"_"+(slab?"slab":"block")+"_"+pair;}
    }
    private static final List<Fixture> FIXTURES=new ArrayList<>();
    private static final List<String> ERRORS=new ArrayList<>();
    private static List<BlockDustParticle> dust=List.of();
    private static int stage,index,ticks,started,dark,free,vertices;
    private static volatile boolean pending,confirmed;
    private static volatile Throwable failure;
    private static Vec3d arrival;
    static {
        for(int i=0;i<16;i++)FIXTURES.add(new Fixture(new BlockPos(80+(i%4)*4,130,40+(i/4)*4),
                i>=8,i%8<4,i%4+1));
    }
    private StackDustLightChecks() {}
    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        check(++ticks<1800,"stack dust timeout stage="+stage+" fixture="+index);
        if(failure!=null)throw new AssertionError("stack dust server fixture failed",failure);
        if(pending)return false;
        if(stage==0) {
            server(client,()->{
                var world=client.getServer().getOverworld();
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                for(var fixture:FIXTURES) {
                    var base=fixture.base();
                    for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=8;y++) {
                        var pos=base.add(x,y,z);NativeSeats.remove(world,pos);SupplementalCells.remove(world,pos);
                        world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    }
                    world.setBlockState(base.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                    for(int piece=0;piece<8;piece++) {
                        var block=piece%2==0?Blocks.OAK_SLAB:fixture.grass()?Blocks.GRASS_BLOCK:Blocks.STONE;
                        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(block,16));
                        double y=fixture.origin(piece)*.5;
                        var point=new Vec3d(base.getX()+.5,y,base.getZ()+.5);
                        var support=BlockPos.ofFloored(point.add(0,-1e-5,0));
                        var hit=new BlockHitResult(point,Direction.UP,support,false);
                        check(player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),
                                "stack item placement accepted "+fixture.name()+" piece="+piece);
                        check(present(world,fixture,piece),"stack retained placed origin "+fixture.name()+" piece="+piece);
                    }
                }
                teleport(client,FIXTURES.getFirst());confirmed=true;
            });stage=1;return false;
        }
        var fixture=FIXTURES.get(index);
        client.player.setVelocity(Vec3d.ZERO);
        if(stage==1) {
            if(!confirmed || client.player.getPos().squaredDistanceTo(arrival)>.02)return false;
            for(int piece=0;piece<8;piece++)if(!present(client.world,fixture,piece))return false;
            started=ticks;stage=2;return false;
        }
        if(stage==2) {
            if(ticks-started<30)return false;
            var point=point(fixture);
            var delta=point.subtract(client.player.getEyePos());
            client.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            client.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            client.gameRenderer.updateCrosshairTarget(1);
            check(client.crosshairTarget instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK
                    && hit.getPos().squaredDistanceTo(point)<.01,"real ray selected stack level "+fixture.name()+" hit="+client.crosshairTarget);
            var hit=(BlockHitResult)client.crosshairTarget;
            SlabFeedbackAudit.begin();
            check(client.interactionManager.attackBlock(hit.getBlockPos(),hit.getSide()),"stack break packet accepted "+fixture.name());
            started=ticks;stage=3;return false;
        }
        if(stage==3) {
            check(ticks-started<50,"stack break did not remove selected piece or emit dust "+fixture.name());
            if(present(client.world,fixture,fixture.selected()) || !SlabFeedbackAudit.hasDust())return false;
            dust=SlabFeedbackAudit.dust();SlabFeedbackAudit.end();
            check(dust.size()==(fixture.slab()?32:64),"actual stack break dust count "+fixture.name()+" count="+dust.size());
            dark=0;free=0;vertices=0;
            observe(client,fixture,0);started=ticks;stage=4;return false;
        }
        if(stage==4) {
            observe(client,fixture,ticks-started);
            if(ticks-started<30)return false;
            check(free>0,"stack debris never occupied vacated physical volume "+fixture.name());
            System.out.println("PHYSICAL_STACK_DUST_CASE fixture="+fixture.name()+" origin="+fixture.origin(fixture.selected())
                    +" particles="+dust.size()+" free="+free+" dark="+dark+" vertices="+vertices);
            if(dark>0)ERRORS.add(fixture.name()+" dark="+dark+"/"+free);
            confirmed=false;
            server(client,()->{
                var world=client.getServer().getOverworld();
                check(!present(world,fixture,fixture.selected()),"server received real stack break packet "+fixture.name());
                for(int piece=0;piece<8;piece++)if(piece!=fixture.selected())
                    check(present(world,fixture,piece),"neighbor retained stack origin "+fixture.name()+" piece="+piece);
                confirmed=true;
            });stage=5;return false;
        }
        if(stage==5) {
            if(!confirmed)return false;
            if(++index==FIXTURES.size()) {
                check(ERRORS.isEmpty(),"odd/even stack dust failures: "+String.join("; ",ERRORS));
                System.out.println("PHYSICAL_STACK_DUST_PASS stone grass slab block levels_1_2_3_4 actual_packets");
                stage=99;return true;
            }
            confirmed=false;server(client,()->{teleport(client,FIXTURES.get(index));confirmed=true;});stage=1;return false;
        }
        return false;
    }
    private static void observe(MinecraftClient client,Fixture fixture,int age) {
        double bottom=fixture.origin(fixture.selected())*.5,top=bottom+(fixture.slab()?.5:1);
        for(int i=0;i<dust.size();i++) {
            var particle=dust.get(i);if(!particle.isAlive())continue;
            var box=particle.getBoundingBox();var center=box.getCenter();var base=fixture.base();
            boolean vacant=center.x>base.getX() && center.x<base.getX()+1 && center.z>base.getZ() && center.z<base.getZ()+1
                    && box.minY>=bottom-1e-7 && box.minY<top-1e-7;
            int light=particle.getBrightness(1);
            boolean failed=age>=3 && vacant && light==0;
            if(age>=3 && vacant) {free++;if(failed)dark++;}
            if((age==0 && i%16==0) || (age==10 && i%16==0) || (failed && dark==1)) {
                var capture=new Captured();particle.buildGeometry(capture,client.gameRenderer.getCamera(),1);
                vertices+=capture.lights.size();
                var raw=BlockPos.ofFloored(center.x,box.minY,center.z);
                System.out.println("PHYSICAL_STACK_DUST_TRACE fixture="+fixture.name()+" age="+age+" particle="+i
                        +" box="+box+" vacant="+vacant+" packed="+light+" sky="+((light>>20)&15)+" block="+((light>>4)&15)
                        +" raw="+raw+" state="+client.world.getBlockState(raw)+" native="+NativeSeats.offset(client.world,raw)
                        +" parts="+CellWorld.contents(client.world,raw)+" vertexLights="+capture.lights);
            }
        }
    }
    private static boolean present(World world,Fixture fixture,int piece) {
        int origin=fixture.origin(piece),length=piece%2==0?1:2;
        var base=fixture.base();var pos=new BlockPos(base.getX(),Math.floorDiv(origin,2),base.getZ());
        var contents=CellWorld.contents(world,pos);
        if(contents!=null)for(var part:contents.fragments())
            if(pos.getY()*2+part.offset()==origin && part.length()==length)return true;
        var owner=new BlockPos(base.getX(),-Math.floorDiv(-origin,2),base.getZ());
        var seat=NativeSeats.seat(world,owner);
        if(piece%2==1 && fixture.grass())return seat!=null && owner.getY()*2+seat.halfOffset()==origin
                && (world.getBlockState(owner).isOf(Blocks.GRASS_BLOCK) || world.getBlockState(owner).isOf(Blocks.DIRT));
        return piece%2==1 && origin%2==0 && world.getBlockState(pos).isOf(Blocks.STONE);
    }
    private static Vec3d point(Fixture fixture) {
        return new Vec3d(fixture.base().getX()+.5,fixture.origin(fixture.selected())*.5+.25,fixture.base().getZ());
    }
    private static void teleport(MinecraftClient client,Fixture fixture) {
        var point=point(fixture);arrival=point.add(0,-1.62,-2.5);
        var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        player.teleport(client.getServer().getOverworld(),arrival.x,arrival.y,arrival.z,0,0);
        player.getAbilities().flying=true;player.sendAbilitiesUpdate();
    }
    private static void server(MinecraftClient client,Runnable action) {
        pending=true;client.getServer().execute(()->{
            try {action.run();}catch(Throwable error) {failure=error;}finally {pending=false;}
        });
    }
    private static final class Captured implements VertexConsumer {
        private final List<Integer> lights=new ArrayList<>();
        @Override public VertexConsumer vertex(float x,float y,float z) {return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a) {return this;}
        @Override public VertexConsumer texture(float u,float v) {return this;}
        @Override public VertexConsumer overlay(int u,int v) {return this;}
        @Override public VertexConsumer light(int u,int v) {lights.add((v<<16)|u);return this;}
        @Override public VertexConsumer normal(float x,float y,float z) {return this;}
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
