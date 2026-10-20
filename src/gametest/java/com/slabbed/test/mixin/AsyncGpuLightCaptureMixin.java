package com.slabbed.test.mixin;

import com.slabbed.test.StackDustLightChecks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteBillboardParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the optional renderer's actual light upload without changing particle state. */
@Mixin(value=SpriteBillboardParticle.class,priority=500)
public abstract class AsyncGpuLightCaptureMixin {
    @Inject(method="asyncparticles$getGpuLightCoords",at=@At("RETURN"),remap=false,require=0)
    private void slabbed$uploadedLight(float tickDelta,CallbackInfoReturnable<Integer> cir) {
        StackDustLightChecks.gpuLight((Particle)(Object)this,cir.getReturnValue());
    }
}
