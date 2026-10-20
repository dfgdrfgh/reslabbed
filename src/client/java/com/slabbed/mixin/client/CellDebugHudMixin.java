package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import com.slabbed.cell.SupplementalCells;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** F3 reports the selected material and its properties without changing the native world owner. */
@Mixin(DebugHud.class)
public abstract class CellDebugHudMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private HitResult blockHit;
    @ModifyExpressionValue(method="getRightText",at=@At(value="INVOKE",target="Lnet/minecraft/client/world/ClientWorld;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState slabbed$selectedDebugState(BlockState state) {
        if(client.world==null || !(blockHit instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK
                || (!state.isOf(Slabbed.CELL) && !SupplementalCells.has(client.world,hit.getBlockPos())))return state;
        var part=CellWorld.pick(client.world,hit.getBlockPos(),hit);
        return part==null?state:part.value();
    }
}
