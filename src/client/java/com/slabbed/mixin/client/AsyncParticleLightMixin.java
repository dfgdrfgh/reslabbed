package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.client.NativeLight;
import com.slabbed.client.NativeParticleLight;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The optional particle cache must sample the same physical air as ordinary particle lighting. */
@Mixin(value=Particle.class,priority=1100)
public abstract class AsyncParticleLightMixin {
    @Shadow protected double y;

    @WrapOperation(method="asyncparticles$refresh",remap=false,require=0,
            at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",remap=true))
    private int slabbed$physicalCachedLight(BlockRenderView view,BlockPos pos,Operation<Integer> original) {
        if(!(view instanceof World world))return original.call(view,pos);
        int light=original.call(view,NativeLight.sample(world,pos,y));
        return NativeParticleLight.light(world,pos,y,light);
    }
}
