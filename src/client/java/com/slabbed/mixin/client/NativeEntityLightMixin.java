package com.slabbed.mixin.client;

import com.slabbed.client.NativeLight;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Exposed halves of shifted opaque owners use the adjacent air cell's ordinary light. */
@Mixin(EntityRenderer.class)
public abstract class NativeEntityLightMixin {
    @ModifyVariable(method="getLight",at=@At("STORE"),ordinal=0)
    private BlockPos slabbed$physicalLightSample(BlockPos sample,Entity entity,float tickDelta) {
        return NativeLight.sample(entity.getWorld(),sample,entity.getClientCameraPosVec(tickDelta).y);
    }
}
