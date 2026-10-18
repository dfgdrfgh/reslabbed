package com.slabbed.test.mixin;

import com.slabbed.test.NativeParticleAudit;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class NativeClientParticleCaptureMixin {
    // TAIL sees the coordinates after production HEAD argument modifications.
    @Inject(method="addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",at=@At("TAIL"))
    private void slabbed$normal(ParticleEffect effect,double x,double y,double z,double vx,double vy,double vz,CallbackInfo ci) {
        NativeParticleAudit.record("normal",effect,x,y,z,1,vx,vy,vz,0);
    }
    @Inject(method="addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V",at=@At("TAIL"))
    private void slabbed$normalForced(ParticleEffect effect,boolean forced,double x,double y,double z,double vx,double vy,double vz,CallbackInfo ci) {
        NativeParticleAudit.record("normal:"+forced,effect,x,y,z,1,vx,vy,vz,0);
    }
    @Inject(method="addImportantParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",at=@At("TAIL"))
    private void slabbed$important(ParticleEffect effect,double x,double y,double z,double vx,double vy,double vz,CallbackInfo ci) {
        NativeParticleAudit.record("important",effect,x,y,z,1,vx,vy,vz,0);
    }
    @Inject(method="addImportantParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V",at=@At("TAIL"))
    private void slabbed$importantForced(ParticleEffect effect,boolean forced,double x,double y,double z,double vx,double vy,double vz,CallbackInfo ci) {
        NativeParticleAudit.record("important:"+forced,effect,x,y,z,1,vx,vy,vz,0);
    }
}
