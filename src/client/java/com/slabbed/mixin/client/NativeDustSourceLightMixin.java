package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.client.NativeParticleLight;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla debris retains source light when it subsequently enters another solid. */
@Mixin(BlockDustParticle.class)
public abstract class NativeDustSourceLightMixin {
    @Unique private double slabbed$sourceY;
    @Inject(method="<init>(Lnet/minecraft/client/world/ClientWorld;DDDDDDLnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)V",at=@At("RETURN"))
    private void slabbed$rememberPhysicalSource(ClientWorld world,double x,double y,double z,double vx,double vy,double vz,BlockState state,BlockPos pos,CallbackInfo ci) {
        slabbed$sourceY=y;
    }
    @WrapOperation(method="getBrightness",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I"))
    private int slabbed$sourceLight(BlockRenderView view,BlockPos pos,Operation<Integer> original) {
        int light=original.call(view,pos);
        return view instanceof net.minecraft.world.World world?NativeParticleLight.light(world,pos,slabbed$sourceY,light):light;
    }
}
