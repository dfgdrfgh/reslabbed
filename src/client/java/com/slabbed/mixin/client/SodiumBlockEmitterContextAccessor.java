package com.slabbed.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the render context that owns Sodium's quad emitter. {@code BlockEmitter} is a non-static
 * inner class whose methods use the enclosing context, so the compiler keeps its {@code this$0} field.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext$BlockEmitter", remap = false)
public interface SodiumBlockEmitterContextAccessor {
    @Accessor(value = "this$0", remap = false)
    Object slabbed$getRenderContext();
}
