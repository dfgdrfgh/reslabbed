package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.WeightedPressurePlateBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native scheduled detection uses the same physical bounds as initial contact. */
@Mixin({PressurePlateBlock.class,WeightedPressurePlateBlock.class})
public abstract class NativePlateDetectionMixin {
    @ModifyExpressionValue(method="getRedstoneOutput(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I",at=@At(value="INVOKE",target="Lnet/minecraft/util/math/Box;offset(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Box;"))
    private Box slabbed$physicalDetection(Box box,World world,BlockPos pos) {
        return box.offset(0,NativeSeats.offset(world,pos),0);
    }
}
