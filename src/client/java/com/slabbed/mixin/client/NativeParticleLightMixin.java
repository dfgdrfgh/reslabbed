package com.slabbed.mixin.client;

import com.slabbed.client.NativeLight;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Preserve particle light overrides while sampling the physical exposed half of native owners. */
@Mixin(Particle.class)
public abstract class NativeParticleLightMixin {
    @Shadow protected ClientWorld world;
    @Shadow protected double y;
    @ModifyVariable(method="getBrightness",at=@At("STORE"),ordinal=0)
    private BlockPos slabbed$physicalLightSample(BlockPos sample) {
        return NativeLight.sample(world,sample,y);
    }
}
