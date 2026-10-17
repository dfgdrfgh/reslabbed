package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.hanging.HangingPlacement;
import net.minecraft.item.DecorationItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DecorationItem.class)
public abstract class HangingDecorationItemMixin {
    @WrapMethod(method="useOnBlock")
    private ActionResult slabbed$rememberMount(ItemUsageContext context,Operation<ActionResult> original) {
        return HangingPlacement.place(context,()->original.call(context));
    }
}
