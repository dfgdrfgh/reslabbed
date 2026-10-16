package com.slabbed.test.mixin;

import com.slabbed.test.CellFeedbackAudit;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class CellParticleCaptureMixin {
    @Inject(method="addBlockBreakParticles",at=@At("HEAD"))
    private void slabbed$recordMaterial(BlockPos pos,BlockState state,CallbackInfo ci) {
        CellFeedbackAudit.breaks++;CellFeedbackAudit.material=state;
    }
}
