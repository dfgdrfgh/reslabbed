package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.nativeblock.NativePlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockItem.class)
public abstract class NativeBlockItemMixin {
    @WrapMethod(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;")
    private ActionResult slabbed$nativePlacement(ItemPlacementContext context,Operation<ActionResult> original) {
        return NativePlacement.place((BlockItem)(Object)this,context,next->original.call(next));
    }
}
