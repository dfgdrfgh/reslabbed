package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeShapes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockView.class)
public interface NativeRaycastMixin {
    @Inject(method="raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;",at=@At("RETURN"),cancellable=true)
    private void slabbed$physicalNativeHit(RaycastContext context,CallbackInfoReturnable<BlockHitResult> cir) {
        cir.setReturnValue(NativeShapes.raycast((BlockView)this,context,cir.getReturnValue()));
    }
}
