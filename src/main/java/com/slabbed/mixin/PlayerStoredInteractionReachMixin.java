package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.anchor.PlacementGeometry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native interaction distance uses the same stored body as collision and targeting (LAW.md). */
@Mixin(PlayerEntity.class)
public abstract class PlayerStoredInteractionReachMixin {
    @ModifyExpressionValue(method = "canInteractWithBlockAt", at = @At(value = "NEW",
            target = "net/minecraft/util/math/Box"))
    private Box slabbed$storedInteractionBody(Box bounds, BlockPos pos, double additionalRange) {
        var world = ((PlayerEntity) (Object) this).getWorld();
        double dy = PlacementGeometry.offsetY(world, pos, world.getBlockState(pos));
        return dy == 0.0d ? bounds : bounds.offset(0.0d, dy, 0.0d);
    }
}
