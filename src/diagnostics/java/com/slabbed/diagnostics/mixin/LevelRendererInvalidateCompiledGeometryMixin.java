package com.slabbed.diagnostics.mixin;

import com.slabbed.diagnostics.util.SlabModelStaleSentinel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostics only: a full chunk-render reload (F3+A, resource reload) resets the stale-mesh sentinel. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererInvalidateCompiledGeometryMixin {
    // 26.x names the full-reload entry invalidateCompiledGeometry (F3+A and resource reload reach it
    // through the level extractor); 1.21.x called it allChanged.
    @Inject(method = "invalidateCompiledGeometry", at = @At("HEAD"))
    private void slabbed$onFullRenderInvalidate(CallbackInfo ci) {
        SlabModelStaleSentinel.onFullRenderInvalidate();
    }
}
