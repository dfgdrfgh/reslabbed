package com.slabbed.mixin;

import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightSourceView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkLightProvider.class)
public interface NativeLightingAccess {
    @Invoker("getChunk") LightSourceView slabbed$lightingChunk(int x,int z);
}
