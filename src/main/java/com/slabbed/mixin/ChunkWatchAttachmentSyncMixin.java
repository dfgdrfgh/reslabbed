package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorSync;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a player starts watching a chunk, Slabbed's chunk attachments follow the chunk data on the
 * same connection (TAIL: always after the chunk packet itself), so the client never renders a
 * freshly received chunk without its stored heights. Since 1.20.2 the chunk packet is sent by
 * {@code ChunkDataSender.sendChunkData} (1.20.1 used {@code ThreadedAnvilChunkStorage}).
 */
@Mixin(ChunkDataSender.class)
public abstract class ChunkWatchAttachmentSyncMixin {
    @Inject(method = "sendChunkData", at = @At("TAIL"))
    private static void slabbed$sendChunkAttachments(ServerPlayNetworkHandler handler,
                                                     ServerWorld world,
                                                     WorldChunk chunk, CallbackInfo ci) {
        SlabAnchorSync.sendChunk(handler.player, chunk);
    }
}
