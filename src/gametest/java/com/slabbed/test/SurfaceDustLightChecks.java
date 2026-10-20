package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.client.CellBreakEffects;
import com.slabbed.client.NativeLight;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Untouched production dust in half-block cavities, with an ordinary vanilla ceiling control. */
public final class SurfaceDustLightChecks {
    private record Fixture(String name,BlockPos pos,BlockState floor,boolean ceiling,boolean side,boolean vanilla) {}
    private record Tracked(Fixture fixture,BlockDustParticle dust,int initialLight) {}
    private static final List<Fixture> FIXTURES=List.of(
            new Fixture("grass_open",new BlockPos(80,110,24),Blocks.GRASS_BLOCK.getDefaultState(),false,false,false),
            new Fixture("stone_ceiling",new BlockPos(84,110,24),Blocks.STONE.getDefaultState(),true,false,false),
            new Fixture("dirt_ceiling_side",new BlockPos(88,110,24),Blocks.DIRT.getDefaultState(),true,true,false),
            new Fixture("vanilla_top_slab_ceiling",new BlockPos(92,110,24),Blocks.STONE.getDefaultState(),true,false,true));
    private static final List<Tracked> DUST=new ArrayList<>();
    private static final List<String> ERRORS=new ArrayList<>();
    private static int stage,ticks,started,penetrating,darkOutside;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private SurfaceDustLightChecks() {}

    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        check(++ticks<240,"surface dust timeout stage="+stage);
        if(failure!=null)throw new AssertionError("surface dust server fixture failed",failure);
        if(pending)return false;
        if(stage==0) {
            pending=true;client.getServer().execute(()->{
                try {
                    var world=client.getServer().getOverworld();
                    for(var fixture:FIXTURES) {
                        var pos=fixture.pos();
                        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-2;y<=3;y++) {
                            var p=pos.add(x,y,z);NativeSeats.remove(world,p);
                            world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                        }
                        if(fixture.vanilla()) {
                            world.setBlockState(pos.down(),fixture.floor(),Block.NOTIFY_ALL);
                            world.setBlockState(pos,Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.TOP),Block.NOTIFY_ALL);
                        } else {
                            world.setBlockState(pos,fixture.floor(),Block.NOTIFY_ALL);
                            NativeSeats.put(world,pos,new NativeSeats.Seat(-1,Registries.BLOCK.getId(fixture.floor().getBlock()).toString()));
                            check(CellWorld.place(world,pos.getX(),pos.getZ(),pos.getY()*2+1,Blocks.OAK_SLAB.getDefaultState()),"single slab cavity fixture placed: "+fixture.name());
                        }
                        if(fixture.ceiling())world.setBlockState(pos.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                        if(fixture.side()) {
                            var p=pos.up().east();world.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                            NativeSeats.put(world,p,new NativeSeats.Seat(-1,"minecraft:stone"));
                            world.setBlockState(pos.north(2),Blocks.GLOWSTONE.getDefaultState(),Block.NOTIFY_ALL);
                        }
                    }
                    var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    player.teleport(world,86.5,112,18.5,0,30);
                    player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                } catch(Throwable error) {failure=error;}
                finally {pending=false;}
            });stage=1;return false;
        }
        if(stage==1) {
            for(var fixture:FIXTURES) {
                var pos=fixture.pos();
                if(fixture.vanilla()) {if(!client.world.getBlockState(pos).isOf(Blocks.OAK_SLAB))return false;}
                else if(client.world.getBlockState(pos)!=fixture.floor() || NativeSeats.offset(client.world,pos)!=-.5
                        || CellWorld.contents(client.world,pos)==null || CellWorld.contents(client.world,pos).fragments().size()!=1)return false;
                if(fixture.ceiling() && !client.world.getBlockState(pos.up()).isOf(Blocks.STONE))return false;
                if(fixture.side() && (NativeSeats.offset(client.world,pos.up().east())!=-.5
                        || !client.world.getBlockState(pos.north(2)).isOf(Blocks.GLOWSTONE)
                        || client.world.getLightLevel(LightType.BLOCK,pos.north())==0))return false;
            }
            started=ticks;stage=2;return false;
        }
        if(stage==2) {
            if(ticks-started<30)return false;
            for(var fixture:FIXTURES) {
                SlabFeedbackAudit.begin();
                if(fixture.vanilla()) {
                    client.particleManager.addBlockBreakParticles(fixture.pos(),client.world.getBlockState(fixture.pos()));
                    client.world.setBlockState(fixture.pos(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                } else {
                    var piece=CellWorld.contents(client.world,fixture.pos()).fragments().getFirst();
                    CellBreakEffects.play(client.world,fixture.pos(),piece);
                    CellWorld.remove(client.world,fixture.pos(),piece.offset(),null,false);
                    tickDebris(client);
                }
                var emitted=captured();SlabFeedbackAudit.end();
                check(emitted.size()==32,"single slab must emit 32 actual dust objects: "+fixture.name()+" actual="+emitted.size());
                for(var dust:emitted)DUST.add(new Tracked(fixture,dust,dust.getBrightness(1)));
                System.out.println("PHYSICAL_SURFACE_DUST_EMITTED fixture="+fixture.name()+" count="+emitted.size()
                        +" ownerSky="+client.world.getLightLevel(LightType.SKY,fixture.pos())
                        +" aboveSky="+client.world.getLightLevel(LightType.SKY,fixture.pos().up())
                        +" northSky="+client.world.getLightLevel(LightType.SKY,fixture.pos().north())
                        +" northBlock="+client.world.getLightLevel(LightType.BLOCK,fixture.pos().north()));
            }
            observe(client,0);started=ticks;stage=3;return false;
        }
        if(stage==3) {
            observe(client,ticks-started);
            if(ticks-started<30)return false;
            check(ERRORS.isEmpty(),"surface dust failures: "+String.join("; ",ERRORS));
            check(darkOutside==0,"debris outside physical solids must retain ordinary light: dark samples="+darkOutside);
            System.out.println("PHYSICAL_SURFACE_DUST_PASS particles="+DUST.size()+" penetrating="+penetrating+" darkOutside="+darkOutside);
            stage=99;return true;
        }
        return false;
    }

    private static void observe(MinecraftClient client,int age) {
        for(int i=0;i<DUST.size();i++) {
            var tracked=DUST.get(i);var dust=tracked.dust();if(!dust.isAlive())continue;
            var fixture=tracked.fixture();var pos=fixture.pos();var box=dust.getBoundingBox();var center=box.getCenter();
            var raw=BlockPos.ofFloored(center.x,box.minY,center.z);
            var sample=NativeLight.sample(client.world,raw,box.minY);
            int light=dust.getBrightness(1),worldLight=net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(client.world,sample);
            boolean insideColumn=center.x>pos.getX() && center.x<pos.getX()+1 && center.z>pos.getZ() && center.z<pos.getZ()+1;
            double floor=pos.getY()+(fixture.vanilla()?0:.5);
            boolean floorPenetration=insideColumn && box.minY<floor-1e-7 && box.maxY>floor-1;
            boolean ceilingPoint=fixture.ceiling() && insideColumn && box.minY>=pos.getY()+1+1e-7 && box.minY<pos.getY()+2;
            boolean ceilingBox=fixture.ceiling() && insideColumn && box.maxY>pos.getY()+1+1e-7 && box.minY<pos.getY()+2;
            boolean sidePoint=fixture.side() && center.x>pos.getX()+1 && center.x<pos.getX()+2
                    && center.z>pos.getZ() && center.z<pos.getZ()+1 && box.minY>pos.getY()+.5 && box.minY<pos.getY()+1.5;
            boolean penetration=floorPenetration || ceilingPoint || sidePoint;
            if(penetration) {
                penetrating++;
                // The ordinary top-slab control distinguishes vanilla ceiling overlap from a shifted-floor failure.
                if(floorPenetration && !fixture.vanilla() && ERRORS.size()<8)
                    ERRORS.add(fixture.name()+" untouched particle entered physical floor at age="+age+" box="+box+" light="+light);
            }
            if(!penetration && light==0)darkOutside++;
            if(age>=3 && fixture.side() && insideColumn && box.minY>=floor-1e-7 && box.minY<pos.getY()+1
                    && !penetration && ((light>>4)&15)==0 && ERRORS.size()<8)
                ERRORS.add(fixture.name()+" exposed cavity particle lost visible glowstone block light at age="+age+" box="+box+" light="+light
                        +" adjacentBlock="+client.world.getLightLevel(LightType.BLOCK,pos.north()));
            if((age==0 && i%8==0) || (penetration && penetrating<15) || (age==10 && i%8==0)) {
                var vertices=new Captured();render(client,dust,vertices);
                System.out.println("PHYSICAL_SURFACE_DUST_TRACE fixture="+fixture.name()+" particle="+i+" age="+age
                        +" box="+box+" initialLight="+tracked.initialLight()+" packed="+light+" sky="+((light>>20)&15)+" block="+((light>>4)&15)
                        +" raw="+raw+" sample="+sample+" samplePacked="+worldLight+" ceilingBox="+ceilingBox+" ceilingPoint="+ceilingPoint
                        +" floorInside="+floorPenetration+" sideInside="+sidePoint+" vertexRgb="+vertices.colors+" vertexLights="+vertices.lights);
            }
        }
    }
    private static void render(MinecraftClient client,BlockDustParticle dust,Captured vertices) {
        dust.buildGeometry(vertices,client.gameRenderer.getCamera(),1f);
    }
    @SuppressWarnings("unchecked")
    private static List<BlockDustParticle> captured() {
        try {Field field=SlabFeedbackAudit.class.getDeclaredField("DUST");field.setAccessible(true);return List.copyOf((List<BlockDustParticle>)field.get(null));}
        catch(ReflectiveOperationException error) {throw new AssertionError("actual dust capture failed",error);}
    }
    private static void tickDebris(MinecraftClient client) {
        try {var method=CellBreakEffects.class.getDeclaredMethod("tick",net.minecraft.client.world.ClientWorld.class);method.setAccessible(true);method.invoke(null,client.world);}
        catch(ReflectiveOperationException error) {throw new AssertionError("actual debris queue tick failed",error);}
    }
    private static final class Captured implements VertexConsumer {
        private final List<String> colors=new ArrayList<>();
        private final List<Integer> lights=new ArrayList<>();
        @Override public VertexConsumer vertex(float x,float y,float z) {return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a) {colors.add(r+","+g+","+b+","+a);return this;}
        @Override public VertexConsumer texture(float u,float v) {return this;}
        @Override public VertexConsumer overlay(int u,int v) {return this;}
        @Override public VertexConsumer light(int u,int v) {lights.add((v<<16)|u);return this;}
        @Override public VertexConsumer normal(float x,float y,float z) {return this;}
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
