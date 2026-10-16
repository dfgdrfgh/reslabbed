package com.slabbed.test;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Compares live emitted geometry to independent unshifted vanilla renderer geometry. */
public final class NativeRenderAudit {
    private static final Map<BlockPos,Observation> ENTITIES=new ConcurrentHashMap<>();
    private static final Map<BlockPos,Observation> MODELS=new ConcurrentHashMap<>();
    private static final class Observation {
        final double expectedMin,expectedMax;
        int vertices;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        Observation(double expectedMin,double expectedMax) {this.expectedMin=expectedMin;this.expectedMax=expectedMax;}
        synchronized void vertex(double y) {vertices++;min=Math.min(min,y);max=Math.max(max,y);}
        synchronized void verify(String name) {
            if(vertices<4 || Math.abs(min-expectedMin)>.002 || Math.abs(max-expectedMax)>.002)
                throw new AssertionError(name+" emitted geometry mismatch: vertices="+vertices+" range="+min+".."+max+" expected="+expectedMin+".."+expectedMax);
            System.out.println("PHYSICAL_NATIVE_RENDER_VERTICES_PASS "+name+" vertices="+vertices+" min="+min+" max="+max);
        }
    }
    private NativeRenderAudit() {}

    /** Arm while the entity is visible, then allow several world frames before verifyBlockEntity. */
    public static void beginBlockEntity(MinecraftClient client,BlockPos owner) {
        var entity=client.world.getBlockEntity(owner);
        if(entity==null)throw new AssertionError("native render fixture has no block entity at "+owner);
        var renderer=client.getBlockEntityRenderDispatcher().get(entity);
        if(renderer==null)throw new AssertionError("native render fixture has no renderer at "+owner);
        var raw=new Observation(0,0);
        VertexConsumerProvider sink=layer->new Captured(null,raw,0);
        renderer.render(entity,1,new MatrixStack(),sink,0x00F000F0,OverlayTexture.DEFAULT_UV);
        if(raw.vertices<4)throw new AssertionError("unshifted vanilla renderer emitted no reference vertices at "+owner);
        ENTITIES.put(owner.toImmutable(),new Observation(owner.getY()-.5+raw.min,owner.getY()-.5+raw.max));
    }
    public static VertexConsumerProvider watch(BlockEntity entity,VertexConsumerProvider target) {
        var observation=ENTITIES.get(entity.getPos());
        if(observation==null)return target;
        double cameraY=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y;
        return layer->new Captured(target.getBuffer(layer),observation,cameraY);
    }
    public static void verifyBlockEntity(BlockPos owner) {
        var observation=ENTITIES.remove(owner);
        if(observation==null)throw new AssertionError("native render fixture was not armed at "+owner);
        observation.verify("block_entity="+owner);
    }
    public static void beginModel(MinecraftClient client,BlockPos owner) {
        MODELS.put(owner.toImmutable(),new Observation(owner.getY()-.5,owner.getY()+.5));
        client.worldRenderer.scheduleBlockRenders(owner.getX()-1,owner.getY()-1,owner.getZ()-1,
                owner.getX()+1,owner.getY()+1,owner.getZ()+1);
    }
    public static void quad(BlockPos owner,QuadView quad) {
        var observation=MODELS.get(owner);if(observation==null)return;
        for(int n=0;n<4;n++)observation.vertex(owner.getY()+quad.y(n));
    }
    public static void verifyModel(BlockPos owner) {
        var observation=MODELS.remove(owner);
        if(observation==null)throw new AssertionError("native model fixture was not armed at "+owner);
        observation.verify("model="+owner);
    }
    private record Captured(VertexConsumer target,Observation observation,double worldY) implements VertexConsumer {
        @Override public VertexConsumer vertex(float x,float y,float z) {observation.vertex(y+worldY);if(target!=null)target.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a) {if(target!=null)target.color(r,g,b,a);return this;}
        @Override public VertexConsumer texture(float u,float v) {if(target!=null)target.texture(u,v);return this;}
        @Override public VertexConsumer overlay(int u,int v) {if(target!=null)target.overlay(u,v);return this;}
        @Override public VertexConsumer light(int u,int v) {if(target!=null)target.light(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z) {if(target!=null)target.normal(x,y,z);return this;}
    }
}
