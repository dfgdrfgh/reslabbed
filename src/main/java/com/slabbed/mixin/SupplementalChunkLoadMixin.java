package com.slabbed.mixin;

import com.slabbed.cell.SupplementalCells;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.StorageKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Publish saved supplemental emitters after attachment decoding and before lighting starts. */
@Mixin(ChunkSerializer.class)
public abstract class SupplementalChunkLoadMixin {
    @Inject(method="deserialize",at=@At("RETURN"))
    private static void slabbed$loadedSources(ServerWorld world,PointOfInterestStorage poi,StorageKey storageKey,
                                              ChunkPos pos,NbtCompound nbt,CallbackInfoReturnable<ProtoChunk> cir) {
        SupplementalCells.publishLoaded(world,cir.getReturnValue());
    }
}
