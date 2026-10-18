package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeConnections;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class NativeConnectionMixin {
    @Inject(method="getPlacementState",at=@At("RETURN"),cancellable=true)
    private void slabbed$physicalConnections(ItemPlacementContext context,CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(NativeConnections.adjust(context.getWorld(),context.getBlockPos(),cir.getReturnValue()));
    }
}
