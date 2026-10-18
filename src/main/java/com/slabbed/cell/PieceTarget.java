package com.slabbed.cell;

import com.slabbed.Slabbed;
import com.slabbed.core.CellContents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Vanilla break packets identify a cell; this packet identifies the aimed piece inside it. */
public record PieceTarget(BlockPos pos,Vec3d point) implements CustomPayload {
    public static final Id<PieceTarget> ID=new Id<>(Identifier.of("slabbed","piece_target"));
    public static final PacketCodec<RegistryByteBuf,PieceTarget> CODEC=new PacketCodec<>() {
        @Override public PieceTarget decode(RegistryByteBuf buf) { return new PieceTarget(buf.readBlockPos(),new Vec3d(buf.readDouble(),buf.readDouble(),buf.readDouble())); }
        @Override public void encode(RegistryByteBuf buf,PieceTarget value) { buf.writeBlockPos(value.pos);buf.writeDouble(value.point.x);buf.writeDouble(value.point.y);buf.writeDouble(value.point.z); }
    };
    private record Selection(PieceTarget target,long tick) {}
    private static final Map<UUID,Selection> TARGETS=new HashMap<>();
    @Override public Id<? extends CustomPayload> getId() { return ID; }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID,CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID,(packet,context)->{
            var player=context.player();
            if(packet.resolve(player)!=null) TARGETS.put(player.getUuid(),new Selection(packet,player.getServerWorld().getTime()));
            else TARGETS.remove(player.getUuid());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->TARGETS.remove(handler.player.getUuid()));
    }
    public static CellContents.Fragment<BlockState> selected(ServerPlayerEntity player,BlockPos pos) {
        var selected=TARGETS.get(player.getUuid());
        if(selected==null || !selected.target.pos.equals(pos))return null;
        long age=player.getServerWorld().getTime()-selected.tick;
        return age<0 || age>40?null:selected.target.resolve(player);
    }
    private CellContents.Fragment<BlockState> resolve(ServerPlayerEntity player) {
        var world=player.getServerWorld();
        if(!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z)
                || !world.isChunkLoaded(pos) || (!world.getBlockState(pos).isOf(Slabbed.CELL) && !SupplementalCells.has(world,pos)))return null;
        var eye=player.getEyePos();double range=player.getBlockInteractionRange();
        if(eye.squaredDistanceTo(point)>range*range+1e-6 || !new Box(pos).expand(.001).contains(point))return null;
        var delta=point.subtract(eye);
        if(delta.lengthSquared()<1e-8)return null;
        var ray=world.raycast(new RaycastContext(eye,point.add(delta.normalize().multiply(.001)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        if(ray.getType()!=HitResult.Type.BLOCK || !ray.getBlockPos().equals(pos) || ray.getPos().squaredDistanceTo(point)>.0025)return null;
        return CellWorld.pick(world,pos,ray);
    }
}
