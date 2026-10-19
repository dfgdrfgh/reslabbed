package com.slabbed.cell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.slabbed.core.CellContents;
import com.slabbed.nativeblock.NativeShapes;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Static pieces beside a native owner retain their own frozen origins and leave its data intact (LAW.md). */
public final class SupplementalCells {
    private static final Codec<CellContents.Fragment<BlockState>> PART_CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.intRange(-1,1).fieldOf("offset").forGetter(CellContents.Fragment::offset),
            Codec.intRange(1,2).fieldOf("length").forGetter(CellContents.Fragment::length),
            BlockState.CODEC.fieldOf("state").forGetter(CellContents.Fragment::value)).apply(instance,CellContents.Fragment::new));

    /** Immutable occupancy and clipped geometry are safe to publish to section workers. */
    public static final class StoredCell {
        public static final Codec<StoredCell> CODEC=RecordCodecBuilder.create(instance->instance.group(
                Codec.intRange(1,1).fieldOf("format").forGetter(StoredCell::format),
                PART_CODEC.listOf().fieldOf("parts").forGetter(cell->cell.contents.fragments()))
                .apply(instance,(format,parts)->new StoredCell(new CellContents<>(parts))));
        private final CellContents<BlockState> contents;
        private final VoxelShape outline;
        private final VoxelShape collision;
        private final int luminance;
        public StoredCell(CellContents<BlockState> contents) {
            this.contents=new CellContents<>(contents.fragments());
            VoxelShape outline=VoxelShapes.empty(),collision=VoxelShapes.empty();
            int luminance=0;
            for(var part:this.contents.fragments()) {
                var state=part.value();
                if(!PieceRules.accepts(state) || state.hasBlockEntity() || part.length()!=CellWorld.length(state))
                    throw new IllegalArgumentException("unsupported supplemental piece");
                outline=VoxelShapes.union(outline,clip(NativeShapes.outline(EmptyBlockView.INSTANCE,BlockPos.ORIGIN,state).offset(0,part.offset()*.5,0)));
                collision=VoxelShapes.union(collision,clip(NativeShapes.collision(EmptyBlockView.INSTANCE,BlockPos.ORIGIN,state).offset(0,part.offset()*.5,0)));
                luminance=Math.max(luminance,state.getLuminance());
            }
            this.outline=outline;this.collision=collision;this.luminance=luminance;
        }
        public int format() {return 1;}
        public CellContents<BlockState> contents() {return contents;}
        public VoxelShape outline() {return outline;}
        public VoxelShape collision() {return collision;}
        public int luminance() {return luminance;}
        @Override public boolean equals(Object other) {return other instanceof StoredCell cell && contents.equals(cell.contents);}
        @Override public int hashCode() {return contents.hashCode();}
    }
    private static VoxelShape clip(VoxelShape shape) {
        return VoxelShapes.combineAndSimplify(shape,VoxelShapes.fullCube(),BooleanBiFunction.AND);
    }
    private static final Codec<Map<String,StoredCell>> CODEC=Codec.unboundedMap(Codec.STRING,StoredCell.CODEC).xmap(Map::copyOf,Map::copyOf);
    private static final PacketCodec<RegistryByteBuf,Map<String,StoredCell>> PACKET_CODEC=new PacketCodec<>() {
        @Override public Map<String,StoredCell> decode(RegistryByteBuf buf) {
            if(buf.readVarInt()!=1)throw new IllegalArgumentException("unsupported supplemental cell format");
            int count=buf.readVarInt();
            if(count<0 || count>1048576)throw new IllegalArgumentException("invalid supplemental cell count");
            int chunkX=buf.readInt(),chunkZ=buf.readInt();
            var result=new HashMap<String,StoredCell>();
            for(int n=0;n<count;n++) {
                int packed=buf.readInt();
                var pos=new BlockPos(chunkX*16+(packed&15),packed>>8,chunkZ*16+((packed>>4)&15));
                int size=buf.readVarInt();
                if(size<1 || size>2)throw new IllegalArgumentException("invalid supplemental fragment count");
                var parts=new ArrayList<CellContents.Fragment<BlockState>>(size);
                for(int p=0;p<size;p++) {
                    int offset=buf.readByte(),length=buf.readByte();
                    var state=Block.STATE_IDS.get(buf.readVarInt());
                    if(state==null)throw new IllegalArgumentException("invalid supplemental block state");
                    parts.add(new CellContents.Fragment<>(offset,length,state));
                }
                if(result.put(Long.toString(pos.asLong()),new StoredCell(new CellContents<>(parts)))!=null)
                    throw new IllegalArgumentException("duplicate supplemental cell");
            }
            return Map.copyOf(result);
        }
        @Override public void encode(RegistryByteBuf buf,Map<String,StoredCell> value) {
            buf.writeVarInt(1);buf.writeVarInt(value.size());
            var first=value.isEmpty()?BlockPos.ORIGIN:BlockPos.fromLong(Long.parseLong(value.keySet().iterator().next()));
            buf.writeInt(first.getX()>>4);buf.writeInt(first.getZ()>>4);
            value.forEach((key,cell)-> {
                var pos=BlockPos.fromLong(Long.parseLong(key));
                buf.writeInt((pos.getY()<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15));
                buf.writeVarInt(cell.contents.fragments().size());
                for(var part:cell.contents.fragments()) {
                    buf.writeByte(part.offset());buf.writeByte(part.length());buf.writeVarInt(Block.STATE_IDS.getRawId(part.value()));
                }
            });
        }
    };
    public static final AttachmentType<Map<String,StoredCell>> TYPE=AttachmentRegistry.create(Identifier.of("slabbed","supplemental_cells"),
            builder->builder.persistent(CODEC).syncWith(PACKET_CODEC,AttachmentSyncPredicate.all()));
    public static Function<BlockPos,StoredCell> clientSnapshot=pos->null;
    public static BiConsumer<World,BlockPos> changed=(world,pos)->{};
    private static final ConcurrentMap<ServerWorld,ConcurrentMap<Long,Map<String,StoredCell>>> SERVER_SNAPSHOTS=new ConcurrentHashMap<>();
    private SupplementalCells() {}
    public static void init() {
        ServerChunkEvents.CHUNK_LOAD.register(SupplementalCells::publish);
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)-> {
            var chunks=SERVER_SNAPSHOTS.get(world);if(chunks!=null)chunks.remove(chunk.getPos().toLong());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SERVER_SNAPSHOTS.keySet().removeIf(world->world.getServer()==server));
    }
    private static void publish(ServerWorld world,WorldChunk chunk) {
        SERVER_SNAPSHOTS.computeIfAbsent(world,key->new ConcurrentHashMap<>()).put(chunk.getPos().toLong(),Map.copyOf(snapshot(chunk)));
    }
    private static void publish(World world,WorldChunk chunk) {
        if(world instanceof ServerWorld serverWorld)publish(serverWorld,chunk);
    }
    public static Map<String,StoredCell> snapshot(WorldChunk chunk) {
        var map=chunk.getAttached(TYPE);return map==null?Map.of():map;
    }
    private static StoredCell stored(BlockView view,BlockPos pos) {
        // Shared shape caches have no world placement and must not consume live client geometry.
        if(pos==null || view==null || view==EmptyBlockView.INSTANCE)return null;
        if(view instanceof com.slabbed.nativeblock.StoredGeometryView stored)return stored.slabbed$supplementalCell(pos);
        if(view instanceof net.minecraft.world.chunk.WrapperProtoChunk wrapper)return stored(wrapper.getWrappedChunk(),pos);
        if(view instanceof ServerWorld world && !world.getServer().isOnThread()) {
            var chunks=SERVER_SNAPSHOTS.get(world);if(chunks==null)return null;
            var cells=chunks.get(ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4));
            return cells==null?null:cells.get(Long.toString(pos.asLong()));
        }
        if(view instanceof World world) {
            if(!world.isChunkLoaded(pos))return null;
            return snapshot(world.getWorldChunk(pos)).get(Long.toString(pos.asLong()));
        }
        if(view instanceof WorldChunk chunk)return snapshot(chunk).get(Long.toString(pos.asLong()));
        return clientSnapshot.apply(pos);
    }
    public static CellContents<BlockState> contents(BlockView view,BlockPos pos) {
        var cell=stored(view,pos);return cell==null?null:cell.contents();
    }
    public static boolean has(BlockView view,BlockPos pos) {return stored(view,pos)!=null;}
    public static VoxelShape outline(BlockView view,BlockPos pos) {
        var cell=stored(view,pos);return cell==null?VoxelShapes.empty():cell.outline();
    }
    public static VoxelShape collision(BlockView view,BlockPos pos) {
        var cell=stored(view,pos);return cell==null?VoxelShapes.empty():cell.collision();
    }
    public static int luminance(BlockView view,BlockPos pos) {
        var cell=stored(view,pos);return cell==null?0:cell.luminance();
    }
    public static void put(World world,BlockPos pos,CellContents<BlockState> contents) {
        if(contents.fragments().isEmpty()) {remove(world,pos);return;}
        var cell=new StoredCell(contents);var chunk=world.getWorldChunk(pos);var before=snapshot(chunk);
        var key=Long.toString(pos.asLong());if(cell.equals(before.get(key)))return;
        var next=new HashMap<>(before);next.put(key,cell);chunk.setAttached(TYPE,Map.copyOf(next));
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
