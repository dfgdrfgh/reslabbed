package com.slabbed.test;

import com.slabbed.client.NativeSeatModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Compares live emitted geometry to independent unshifted renderer or terrain model geometry. */
public final class NativeRenderAudit {
    private static final Map<BlockPos,Observation> ENTITIES=new ConcurrentHashMap<>();
    private static final Map<BlockPos,Observation> MODELS=new ConcurrentHashMap<>();
    private static final Set<BlockPos> TERRAIN_ENTITIES=ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> PENDING_REMESH=ConcurrentHashMap.newKeySet();
    private static final class Observation {
        final double expectedMin,expectedMax;
        int vertices;
        boolean completed;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        Observation(double expectedMin,double expectedMax) {this.expectedMin=expectedMin;this.expectedMax=expectedMax;}
        synchronized void vertex(double y) {vertices++;min=Math.min(min,y);max=Math.max(max,y);}
        synchronized boolean ready() {return vertices>=4;}
        synchronized void complete() {completed=true;}
        synchronized boolean modelReady() {return completed && vertices>=4;}
        synchronized void verify(String name) {
            if(vertices<4 || !Double.isFinite(expectedMin) || !Double.isFinite(expectedMax)
                    || Math.abs(min-expectedMin)>.002 || Math.abs(max-expectedMax)>.002)
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
        // These fixtures are placed exactly half a block below their native owners.
        double offset=-.5;
        var terrain=optimizedModel(client,entity);
        boolean terrainBody=terrain!=null && terrain.vertices>=4;
        if(terrainBody) {
            TERRAIN_ENTITIES.add(owner.toImmutable());
            MODELS.put(owner.toImmutable(),new Observation(owner.getY()+offset+terrain.min,owner.getY()+offset+terrain.max));
            remesh(client,owner);
        }
        var raw=new Observation(0,0);
        var camera=client.gameRenderer.getCamera().getPos();
        VertexConsumerProvider sink=layer->new Captured(null,raw,camera.y);
        var matrices=new MatrixStack();
        matrices.translate(owner.getX()-camera.x,owner.getY()-camera.y,owner.getZ()-camera.z);
        renderer.render(entity,1,matrices,sink,0x00F000F0,OverlayTexture.DEFAULT_UV);
        if(raw.vertices<4 && !terrainBody)throw new AssertionError("unshifted vanilla renderer emitted no reference vertices at "+owner);
        ENTITIES.put(owner.toImmutable(),new Observation(raw.vertices==0?Double.NaN:offset+raw.min,
                raw.vertices==0?Double.NaN:offset+raw.max));
    }
    public static VertexConsumerProvider watch(BlockEntity entity,VertexConsumerProvider target) {
        var observation=ENTITIES.get(entity.getPos());
        if(observation==null)return target;
        double cameraY=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y;
        return layer->new Captured(target.getBuffer(layer),observation,cameraY);
    }
    /** Wait for the active body pipeline; terrain sign overlays may be culled independently. */
    public static boolean readyBlockEntity(BlockPos owner) {
        if(TERRAIN_ENTITIES.contains(owner))return readyModel(owner);
        var observation=ENTITIES.get(owner);
        return observation!=null && observation.ready();
    }
    public static void verifyBlockEntity(BlockPos owner) {
        var observation=ENTITIES.remove(owner);
        if(observation==null)throw new AssertionError("native render fixture was not armed at "+owner);
        if(TERRAIN_ENTITIES.remove(owner)) {
            verifyModel(owner);
            // OBE keeps sign text in the BER; camera-facing culling can legitimately omit it.
            if(observation.vertices==0) {
                System.out.println("PHYSICAL_NATIVE_RENDER_OVERLAY_CULLED block_entity="+owner);
                return;
            }
        }
        observation.verify("block_entity="+owner);
    }
    /** OBE's model selector is shared by its actual Sodium, Indigo and vanilla meshing paths. */
    private static Observation optimizedModel(MinecraftClient client,BlockEntity entity) {
        if(!FabricLoader.getInstance().isModLoaded("obe"))return null;
        try {
            var state=entity.getCachedState();
            var selector=Class.forName("fr.madu59.obe.client.util.meshing.SectionMeshingUtil")
                    .getMethod("getCorrectedModel",BlockState.class,BlockEntity.class,BakedModel.class);
            var model=(BakedModel)selector.invoke(null,state,entity,client.getBlockRenderManager().getModel(state));
            System.out.println("PHYSICAL_NATIVE_MODEL_REFERENCE owner="+entity.getPos()+" selected="
                    +(model==null?"null":model.getClass().getName())+" thread="+Thread.currentThread().getName());
            if(model instanceof NativeSeatModel seated)model=seated.getWrappedModel();
            if(model==null)return null;
            if(!model.isVanillaAdapter())throw new AssertionError("OBE reference must expose its unshifted baked quads");
            var raw=new Observation(0,0);
            collectRawModel(model,state,null,raw);
            for(var face:Direction.values())collectRawModel(model,state,face,raw);
            return raw;
        } catch(ReflectiveOperationException error) {
            throw new AssertionError("cannot inspect the active OBE terrain model",error);
        }
    }
    private static void collectRawModel(BakedModel model,BlockState state,Direction face,Observation raw) {
        for(var quad:model.getQuads(state,face,Random.create(42))) {
            var data=quad.getVertexData();int stride=data.length/4;
            for(int n=0;n<4;n++)raw.vertex(Float.intBitsToFloat(data[n*stride+1]));
        }
    }
    public static void beginModel(MinecraftClient client,BlockPos owner) {
        MODELS.put(owner.toImmutable(),new Observation(owner.getY()-.5,owner.getY()+.5));
        remesh(client,owner);
    }
    public static void beginPistonModel(MinecraftClient client,BlockPos owner) {
        var state=client.world.getBlockState(owner);
        BakedModel model=client.getBlockRenderManager().getModel(state);
        while(model instanceof NativeSeatModel seated)model=seated.getWrappedModel();
        var raw=new Observation(0,0);
        collectRawModel(model,state,null,raw);
        for(var face:Direction.values())collectRawModel(model,state,face,raw);
        if(raw.vertices<4)throw new AssertionError("piston reference emitted no geometry at "+owner);
        MODELS.put(owner.toImmutable(),new Observation(owner.getY()-.5+raw.min,owner.getY()-.5+raw.max));
        remesh(client,owner);
    }
    /** Sodium ignores rebuild requests until the initial section mesh has been uploaded. */
    public static void tick(MinecraftClient client) {
        for(var owner:PENDING_REMESH)if(client.worldRenderer.isRenderingReady(owner)) {
            PENDING_REMESH.remove(owner);
            System.out.println("PHYSICAL_NATIVE_MODEL_REMESH_READY owner="+owner);
            remesh(client,owner);
        }
    }
    private static void remesh(MinecraftClient client,BlockPos owner) {
        if(!client.worldRenderer.isRenderingReady(owner)) {
            PENDING_REMESH.add(owner.toImmutable());
            System.out.println("PHYSICAL_NATIVE_MODEL_REMESH_DEFERRED owner="+owner);
            return;
        }
        client.worldRenderer.scheduleBlockRenders(owner.getX()-1,owner.getY()-1,owner.getZ()-1,
                owner.getX()+1,owner.getY()+1,owner.getZ()+1);
    }
    public static void renderPath(BlockPos owner,BakedModel model,String stage) {
        if(!MODELS.containsKey(owner))return;
        System.out.println("PHYSICAL_NATIVE_MODEL_PATH owner="+owner+" stage="+stage+" model="
                +(model==null?"null":model.getClass().getName())+" thread="+Thread.currentThread().getName());
    }
    public static void quad(BlockPos owner,QuadView quad) {
        var observation=MODELS.get(owner);if(observation==null)return;
        for(int n=0;n<4;n++)observation.vertex(owner.getY()+quad.y(n));
    }
    public static boolean readyModel(BlockPos owner) {
        var observation=MODELS.get(owner);
        return observation!=null && observation.modelReady();
    }
    public static void modelComplete(BlockPos owner) {
        var observation=MODELS.get(owner);
        if(observation!=null)observation.complete();
    }
    public static void verifyModel(BlockPos owner) {
        PENDING_REMESH.remove(owner);
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
