package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.client.world.WorldEventHandler;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The redstone-torch burnout smoke (world event 1502) rises from the torch's drawn height. From
 * 1.21.2 world events are handled by the world event handler, and the burnout branch is the one
 * particle call that goes through the world rather than the renderer.
 */
@Mixin(WorldEventHandler.class)
public abstract class RedstoneBurnoutParticleMixin {
    @WrapOperation(
            method = "processWorldEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private void slabbed$offsetBurnoutSmoke(
            World world, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) int eventId,
            @Local(argsOnly = true) BlockPos pos
    ) {
        double translatedY = eventId == 1502
                ? BlockDisplayParticleContext.translateY(world, pos, world.getBlockState(pos), y)
                : y;
        original.call(world, effect, x, translatedY, z, velocityX, velocityY, velocityZ);
    }
}
