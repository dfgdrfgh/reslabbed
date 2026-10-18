package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellBlockEntity;
import com.slabbed.cell.SupplementalCells;
import com.slabbed.nativeblock.NativeShapes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Published attachment snapshots and virtual static renderers never replace native block entities. */
public final class SupplementalCellClient {
    private record RenderCell(SupplementalCells.StoredCell stored,CellBlockEntity entity) {}
    private static final Set<WorldChunk> CHUNKS=new HashSet<>();
    private static final Map<WorldChunk,Map<String,SupplementalCells.StoredCell>> SEEN=new HashMap<>();
    private static final Map<Long,RenderCell> RENDERED=new HashMap<>();
    private static volatile Map<Long,SupplementalCells.StoredCell> published=Map.of();
    private static ClientWorld world;
    private static CellRenderer renderer;
    private SupplementalCellClient() {}
    public static void init() {
        SupplementalCells.clientSnapshot=pos->published.get(pos.asLong());
        SupplementalCells.changed=(changedWorld,pos)-> {if(changedWorld.isClient)refresh();};
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientChunkEvents.CHUNK_LOAD.register((loadedWorld,chunk)-> {useWorld(loadedWorld);CHUNKS.add(chunk);refresh();});
        ClientChunkEvents.CHUNK_UNLOAD.register((loadedWorld,chunk)-> {CHUNKS.remove(chunk);SEEN.remove(chunk);publish();});
        ClientTickEvents.END_CLIENT_TICK.register(client->refresh());
        WorldRenderEvents.AFTER_ENTITIES.register(SupplementalCellClient::render);
    }
    private static void useWorld(ClientWorld next) {
        if(world==next)return;
        reset();world=next;
    }
    private static void reset() {CHUNKS.clear();SEEN.clear();RENDERED.clear();published=Map.of();world=null;}
    private static void refresh() {
        var client=MinecraftClient.getInstance();useWorld(client.world);if(world==null)return;
        CHUNKS.removeIf(chunk->chunk.getWorld()!=world);SEEN.keySet().retainAll(CHUNKS);
        boolean dirty=false;
        for(var chunk:CHUNKS) {
            var before=SEEN.get(chunk);var after=SupplementalCells.snapshot(chunk);
            if(before==after)continue;
            SEEN.put(chunk,after);dirty=true;
            Set<String> keys=new HashSet<>(after.keySet());if(before!=null)keys.addAll(before.keySet());
            for(var key:keys) {
                if(before!=null && java.util.Objects.equals(before.get(key),after.get(key)))continue;
                var pos=BlockPos.fromLong(Long.parseLong(key));
                client.worldRenderer.scheduleBlockRenders(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);
                world.getChunkManager().getLightingProvider().checkBlock(pos);
            }
        }
        if(dirty)publish();
    }
    private static void publish() {
        var next=new HashMap<Long,SupplementalCells.StoredCell>();
        for(var chunk:CHUNKS)SupplementalCells.snapshot(chunk).forEach((key,cell)->next.put(Long.parseLong(key),cell));
        published=Map.copyOf(next);RENDERED.keySet().retainAll(published.keySet());
    }
    private static void render(WorldRenderContext context) {
        refresh();
        if(world==null || context.matrixStack()==null || context.consumers()==null)return;
        if(renderer==null)renderer=new CellRenderer(null);
        var camera=context.camera().getPos();var matrices=context.matrixStack();
        for(var entry:published.entrySet()) {
            var pos=BlockPos.fromLong(entry.getKey());if(!world.isChunkLoaded(pos))continue;
            var cached=RENDERED.get(entry.getKey());
            if(cached==null || cached.stored()!=entry.getValue()) {
                var cell=new CellBlockEntity(pos,Slabbed.CELL.getDefaultState());cell.setWorld(world);
                NativeShapes.withRaw(()-> {cell.setContents(entry.getValue().contents());return VoxelShapes.empty();});
                cached=new RenderCell(entry.getValue(),cell);RENDERED.put(entry.getKey(),cached);
            }
            var cell=cached.entity();
            if(cell.renderBounds()==null || (context.frustum()!=null && !context.frustum().isVisible(cell.renderBounds())))continue;
            matrices.push();
            try {
                matrices.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
                renderer.renderSupplemental(cell,context.tickCounter().getTickDelta(false),matrices,context.consumers(),
                        WorldRenderer.getLightmapCoordinates(world,pos),OverlayTexture.DEFAULT_UV);
            } finally {matrices.pop();}
        }
    }
}
