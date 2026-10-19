package com.slabbed.nativeblock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Native blocks retain native data; this attachment stores only a frozen bounded pose (LAW.md). */
public final class NativeSeats {
    public record Seat(int halfOffset,String block) {
        public Seat {
            if(halfOffset < -1 || halfOffset > 1) throw new IllegalArgumentException("native seat must be within one half block");
        }
        public static final Codec<Seat> CODEC=RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(-1,1).fieldOf("half").forGetter(Seat::halfOffset),
                Codec.STRING.fieldOf("block").forGetter(Seat::block)).apply(instance,Seat::new));
        public double offset() { return halfOffset*.5; }
    }
    private static final Codec<Map<String,Seat>> CODEC=Codec.unboundedMap(Codec.STRING,Seat.CODEC).xmap(Map::copyOf,Map::copyOf);
    private static final PacketCodec<RegistryByteBuf,Map<String,Seat>> PACKET_CODEC=new PacketCodec<>() {
        @Override public Map<String,Seat> decode(RegistryByteBuf buf) {
            int count=buf.readVarInt();
            if(count<0 || count>1048576) throw new IllegalArgumentException("invalid native seat count");
            Map<String,Seat> result=new HashMap<>();
            int chunkX=buf.readInt(),chunkZ=buf.readInt();
            for(int n=0;n<count;n++) {
                int packed=buf.readInt();
                var pos=new BlockPos(chunkX*16+(packed&15),packed>>8,chunkZ*16+((packed>>4)&15));
                var block=Registries.BLOCK.get(buf.readVarInt());
                result.put(Long.toString(pos.asLong()),new Seat(buf.readByte(),Registries.BLOCK.getId(block).toString()));
            }
            return Map.copyOf(result);
        }
        @Override public void encode(RegistryByteBuf buf,Map<String,Seat> value) {
            buf.writeVarInt(value.size());
            var first=value.isEmpty()?BlockPos.ORIGIN:BlockPos.fromLong(Long.parseLong(value.keySet().iterator().next()));
            buf.writeInt(first.getX()>>4);buf.writeInt(first.getZ()>>4);
            value.forEach((key,seat)-> {
                var pos=BlockPos.fromLong(Long.parseLong(key));
                buf.writeInt((pos.getY()<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15));
                buf.writeVarInt(Registries.BLOCK.getRawId(Registries.BLOCK.get(Identifier.of(seat.block()))));
                buf.writeByte(seat.halfOffset());
            });
        }
    };
    public static final AttachmentType<Map<String,Seat>> TYPE=AttachmentRegistry.create(Identifier.of("slabbed","native_seats"),
            builder->builder.persistent(CODEC).syncWith(PACKET_CODEC,AttachmentSyncPredicate.all()));
    public static Function<BlockPos,Seat> clientSnapshot=pos->null;
    public static BiConsumer<World,BlockPos> changed=(world,pos)->{};
    private static final ConcurrentMap<ServerWorld,ConcurrentMap<Long,Map<String,Seat>>> SERVER_SNAPSHOTS=new ConcurrentHashMap<>();
    private NativeSeats() {}
    public static void init() {
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->publish(world,chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)-> {
            var chunks=SERVER_SNAPSHOTS.get(world);if(chunks!=null)chunks.remove(chunk.getPos().toLong());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SERVER_SNAPSHOTS.keySet().removeIf(world->world.getServer()==server));
    }
    private static void publish(World world,WorldChunk chunk) {
        if(world instanceof ServerWorld server)
            SERVER_SNAPSHOTS.computeIfAbsent(server,key->new ConcurrentHashMap<>()).put(chunk.getPos().toLong(),snapshot(chunk));
    }
    public static Map<String,Seat> snapshot(WorldChunk chunk) {
        var map=chunk.getAttached(TYPE);return map==null?Map.of():map;
    }
    public static Seat seat(BlockView view,BlockPos pos) {
        // Shared shape caches have no world placement and must not consume live client facts.
        if(pos==null || view==null || view==net.minecraft.world.EmptyBlockView.INSTANCE)return null;
        if(view instanceof StoredGeometryView stored)return stored.slabbed$nativeSeat(pos);
        if(view instanceof net.minecraft.world.chunk.WrapperProtoChunk wrapper)return seat(wrapper.getWrappedChunk(),pos);
        var pending=NativePlacement.pending(view,pos);
        if(pending!=null) return pending;
        var transformed=NativeTransformations.pending(view,pos);if(transformed!=null)return transformed;
        // Lighting workers must never request a server chunk or wait for the server thread.
        if(view instanceof ServerWorld server && !server.getServer().isOnThread()) {
            var chunks=SERVER_SNAPSHOTS.get(server);
            if(chunks==null)return null;
            var snapshot=chunks.get(ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4));
            return snapshot==null?null:snapshot.get(Long.toString(pos.asLong()));
        }
        Seat seat;boolean clientFacts;
        if(view instanceof World world) {
            if(!world.isChunkLoaded(pos)) return null;
            seat=snapshot(world.getWorldChunk(pos)).get(Long.toString(pos.asLong()));
            clientFacts=world.isClient;
        } else if(view instanceof WorldChunk chunk) {
            seat=snapshot(chunk).get(Long.toString(pos.asLong()));clientFacts=chunk.getWorld().isClient;
        } else {seat=clientSnapshot.apply(pos);clientFacts=true;}
        if(seat==null) return null;
        var state=view.getBlockState(pos);
        if(!NativeRules.accepts(state))return null;
        var identity=Registries.BLOCK.getId(state.getBlock()).toString();
        if(identity.equals(seat.block()))return seat;
        // Client state and pose packets can straddle a temporary piston identity in either order.
        // The remembered offset is returned verbatim; authoritative attachment data is untouched (LAW.md).
        return clientFacts && NativePistons.clientIdentity(seat.block(),identity)?new Seat(seat.halfOffset(),identity):null;
    }
    public static double offset(BlockView view,BlockPos pos) {
        var seat=seat(view,pos);return seat==null?0:seat.offset();
    }
    public static double offset(BlockView view,BlockPos pos,net.minecraft.block.BlockState state) {
        var seat=seat(view,pos);
        return seat!=null && Registries.BLOCK.getId(state.getBlock()).toString().equals(seat.block())?seat.offset():0;
    }
    public static void put(World world,BlockPos pos,Seat seat) {
        var chunk=world.getWorldChunk(pos);var next=new HashMap<>(snapshot(chunk));
        next.put(Long.toString(pos.asLong()),seat);chunk.setAttached(TYPE,Map.copyOf(next));
        publish(world,chunk);chunk.setNeedsSaving(true);changed.accept(world,pos);
    }
    public static void remove(World world,BlockPos pos) {
        if(!world.isChunkLoaded(pos))return;
        var chunk=world.getWorldChunk(pos);var before=snapshot(chunk);var key=Long.toString(pos.asLong());
        if(!before.containsKey(key))return;
        var next=new HashMap<>(before);next.remove(key);chunk.setAttached(TYPE,Map.copyOf(next));
        publish(world,chunk);chunk.setNeedsSaving(true);changed.accept(world,pos);
    }
}
