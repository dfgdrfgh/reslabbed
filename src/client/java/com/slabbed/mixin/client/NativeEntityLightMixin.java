package com.slabbed.mixin.client;

import com.slabbed.nativeblock.NativeSeats;
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
        var world=entity.getWorld();
        double offset=NativeSeats.offset(world,sample);
        if(offset==0 || !world.getBlockState(sample).isOpaqueFullCube(world,sample))return sample;
        double y=entity.getClientCameraPosVec(tickDelta).y-sample.getY();
        if(offset<0 && y>=1+offset)return sample.up();
        if(offset>0 && y<offset)return sample.down();
        return sample;
    }
}
