package com.slabbed.test.mixin;

import com.slabbed.test.SlabFeedbackAudit;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public abstract class DustMotionCaptureMixin {
    @Shadow protected ClientWorld world;
    @Shadow protected int age;
    @Shadow protected int maxAge;
    @Shadow private boolean stopped;
    @Shadow protected double velocityY;
    @Inject(method="tick",at=@At("HEAD"))
    private void slabbed$motion(CallbackInfo ci) {
        SlabFeedbackAudit.motion((Particle)(Object)this,world,age,maxAge,stopped,velocityY);
    }
}
