package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** State-only face caches cannot describe position-dependent geometry (DY_SPEC.md). */
@Mixin(value=Block.class,priority=2600)
public abstract class NativeFaceCullingMixin {
    @Inject(method="shouldDrawSide",at=@At("HEAD"),cancellable=true)
    private static void slabbed$positionedFace(BlockState state,BlockView view,BlockPos pos,Direction side,
                                               BlockPos neighbor,CallbackInfoReturnable<Boolean> result) {
        if(NativeSeats.offset(view,pos,state)!=0 || NativeSeats.offset(view,neighbor)!=0)result.setReturnValue(true);
    }
}
