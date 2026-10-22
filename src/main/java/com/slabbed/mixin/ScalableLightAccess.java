package com.slabbed.mixin;

import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Supplemental emission uses the optional engine's populated chunk cache. */
@Pseudo
@Mixin(targets="ca.spottedleaf.starlight.common.light.StarLightEngine",remap=false)
public interface ScalableLightAccess {
    @Invoker(value="getChunkInCache",remap=false) Chunk slabbed$cachedLightChunk(int x,int z);
}
