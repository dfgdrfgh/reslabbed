package com.slabbed.test.mixin;

import com.slabbed.test.NativeParticleAudit;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class NativeServerParticleCaptureMixin {
    @Inject(method="spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I",at=@At("HEAD"))
    private <T extends ParticleEffect> void slabbed$spawn(T effect,double x,double y,double z,int count,
            double spreadX,double spreadY,double spreadZ,double speed,CallbackInfoReturnable<Integer> cir) {
        NativeParticleAudit.record("server",effect,x,y,z,count,spreadX,spreadY,spreadZ,speed);
    }
}
