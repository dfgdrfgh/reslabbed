package com.slabbed.mixin;

import com.slabbed.cell.CellPlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",at=@At("HEAD"),cancellable=true)
    private void slabbed$physicalPlace(ItemPlacementContext context,CallbackInfoReturnable<ActionResult> cir) {
        if(com.slabbed.nativeblock.NativePlacement.active(context.getWorld(),context.getBlockPos()))return;
        var result=CellPlacement.place((BlockItem)(Object)this,context);
        if(result!=null) cir.setReturnValue(result);
    }
}
