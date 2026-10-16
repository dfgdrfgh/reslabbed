package com.slabbed.client;

import com.slabbed.nativeblock.NativeSeats;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Publishes immutable pose facts for mesh workers and refreshes sections when chunk facts arrive. */
public final class NativeSeatClient {
    private static final Set<WorldChunk> CHUNKS=new HashSet<>();
    private static final Map<WorldChunk,Map<String,NativeSeats.Seat>> SEEN=new HashMap<>();
    private static volatile Map<Long,NativeSeats.Seat> published=Map.of();
    private NativeSeatClient() {}
    public static void init() {
        NativeSeats.clientSnapshot=pos->published.get(pos.asLong());
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        NativeSeats.changed=(world,pos)-> {if(world.isClient)refresh();};
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk)-> {CHUNKS.add(chunk);refresh();});
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)-> {CHUNKS.remove(chunk);SEEN.remove(chunk);publish();});
        ClientTickEvents.END_CLIENT_TICK.register(client-> {
            if(client.world==null) {CHUNKS.clear();SEEN.clear();published=Map.of();}
            else refresh();
        });
        ModelLoadingPlugin.register(plugin->plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)-> {
            var id=context.topLevelId();
            if(model==null || id==null || ModelIdentifier.INVENTORY_VARIANT.equals(id.getVariant()))return model;
            return NativeSeatModel.workstationModel(id.id())?new NativeSeatModel(model):model;
        }));
    }
    private static void reset() {CHUNKS.clear();SEEN.clear();published=Map.of();}
    private static void refresh() {
        var client=MinecraftClient.getInstance();if(client.world==null)return;
        CHUNKS.removeIf(chunk->chunk.getWorld()!=client.world);
        SEEN.keySet().retainAll(CHUNKS);
        boolean dirty=false;
        for(var chunk:CHUNKS) {
            var before=SEEN.get(chunk);var after=NativeSeats.snapshot(chunk);
            if(before==after)continue;
            SEEN.put(chunk,after);dirty=true;
            Set<String> keys=new HashSet<>(after.keySet());if(before!=null)keys.addAll(before.keySet());
            for(var key:keys) {
                if(before!=null && java.util.Objects.equals(before.get(key),after.get(key)))continue;
                var pos=BlockPos.fromLong(Long.parseLong(key));
                client.worldRenderer.scheduleBlockRenders(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);
            }
        }
        if(dirty)publish();
    }
    private static void publish() {
        var result=new HashMap<Long,NativeSeats.Seat>();
        for(var chunk:CHUNKS)NativeSeats.snapshot(chunk).forEach((pos,seat)->result.put(Long.parseLong(pos),seat));
        published=Map.copyOf(result);
    }
}
