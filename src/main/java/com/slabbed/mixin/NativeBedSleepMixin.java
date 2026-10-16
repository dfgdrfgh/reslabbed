package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class NativeBedSleepMixin {
    @Inject(method="setPositionInBed",at=@At("RETURN"))
    private void slabbed$physicalSleepingPosition(BlockPos pos,CallbackInfo ci) {
        var entity=(LivingEntity)(Object)this;double offset=NativeSeats.offset(entity.getWorld(),pos);
        if(offset!=0)entity.setPosition(entity.getX(),entity.getY()+offset,entity.getZ());
    }
}
