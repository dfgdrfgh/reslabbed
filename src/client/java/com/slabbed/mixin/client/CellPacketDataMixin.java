package com.slabbed.mixin.client;

import com.slabbed.client.CellPacketSync;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class CellPacketDataMixin {
    @Shadow private ClientWorld world;
    // Vanilla reaches the first position lookup only after forceMainThread has completed.
    @Inject(method="onBlockEntityUpdate",at=@At(value="INVOKE",
            target="Lnet/minecraft/network/packet/s2c/play/BlockEntityUpdateS2CPacket;getPos()Lnet/minecraft/util/math/BlockPos;",ordinal=0))
    private void slabbed$publishCompleteCarrier(BlockEntityUpdateS2CPacket packet,CallbackInfo ci) {
        ((CellPacketSync.Access)world).slabbed$cellPackets().receive(world,packet);
    }
    @Inject(method="onChunkData",at=@At(value="INVOKE",
            target="Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;getChunkX()I",ordinal=0))
    private void slabbed$replaceChunkSnapshot(ChunkDataS2CPacket packet,CallbackInfo ci) {
        ((CellPacketSync.Access)world).slabbed$cellPackets().unload(new ChunkPos(packet.getChunkX(),packet.getChunkZ()));
    }

}
