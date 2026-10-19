package com.slabbed.mixin.client;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MoreCulling also calls its state-only cache directly from the Sodium mesher. */
@Pseudo
@Mixin(targets="ca.fxco.moreculling.utils.CullingUtils",remap=false)
public abstract class MoreCullingNativeFaceMixin {
    @Inject(method="shouldDrawSideCulling",at=@At("HEAD"),cancellable=true,remap=false)
    private static void slabbed$positionedFace(BlockState state,BlockView view,BlockPos pos,Direction side,
                                               BlockPos neighbor,CallbackInfoReturnable<Boolean> result) {
        if(NativeSeats.offset(view,pos,state)!=0 || NativeSeats.offset(view,neighbor)!=0)result.setReturnValue(true);
    }
}
