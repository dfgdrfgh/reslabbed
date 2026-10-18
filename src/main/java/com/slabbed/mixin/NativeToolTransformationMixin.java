package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.nativeblock.NativeTransformations;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({AxeItem.class,HoeItem.class,ShovelItem.class})
public abstract class NativeToolTransformationMixin {
    @WrapMethod(method="useOnBlock")
    private ActionResult slabbed$keepToolOrigin(ItemUsageContext context,Operation<ActionResult> original) {
        return NativeTransformations.use(context,()->original.call(context));
    }
}
