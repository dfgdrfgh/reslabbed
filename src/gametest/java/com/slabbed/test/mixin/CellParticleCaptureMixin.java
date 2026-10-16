package com.slabbed.test.mixin;

import com.slabbed.test.CellFeedbackAudit;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public abstract class CellParticleCaptureMixin {
    @Inject(method="addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",at=@At("HEAD"))
    private void slabbed$recordMaterial(ParticleEffect effect,double x,double y,double z,double vx,double vy,double vz,CallbackInfoReturnable<Particle> cir) {
        if(effect instanceof BlockStateParticleEffect block && effect.getType()==ParticleTypes.BLOCK) {
            CellFeedbackAudit.breaks++;CellFeedbackAudit.material=block.getBlockState();
            CellFeedbackAudit.minParticleY=Math.min(CellFeedbackAudit.minParticleY,y);
            CellFeedbackAudit.maxParticleY=Math.max(CellFeedbackAudit.maxParticleY,y);
        }
    }
}
