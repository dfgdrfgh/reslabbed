package com.slabbed.diagnostics.mixin;

import com.slabbed.diagnostics.util.SlabModelStaleSentinel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostics only: a full chunk-render reload (F3+A, resource reload) resets the stale-mesh sentinel. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererAllChangedMixin {
    // 26.3 names the full-reload entry allChanged; the 26.2 twin is
    // LevelRendererInvalidateCompiledGeometryMixin and the plugin applies exactly one of the pair.
    @Inject(method = "allChanged", at = @At("HEAD"))
    private void slabbed$onFullRenderInvalidate(CallbackInfo ci) {
        SlabModelStaleSentinel.onFullRenderInvalidate();
    }
}
