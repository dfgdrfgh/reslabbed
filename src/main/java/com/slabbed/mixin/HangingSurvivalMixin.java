package com.slabbed.mixin;

import com.slabbed.hanging.HangingPlacement;
import com.slabbed.hanging.HangingSeat;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractDecorationEntity.class)
public abstract class HangingSurvivalMixin {
    @Inject(method="canStayAttached",at=@At("HEAD"),cancellable=true)
    private void slabbed$physicalPaintingSupport(CallbackInfoReturnable<Boolean> cir) {
        if(this instanceof HangingSeat remembered && remembered.slabbed$hangingOffset()!=0)
            cir.setReturnValue(HangingPlacement.supported((AbstractDecorationEntity)(Object)this));
    }
}
