package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.nativeblock.NativeTransformations;
import net.minecraft.block.BlockState;
import net.minecraft.block.SpreadableBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Grass and mycelium decay/spread change material without replacing physical placement (LAW.md). */
@Mixin(SpreadableBlock.class)
public abstract class NativeSoilTransformationMixin {
    @WrapOperation(method="randomTick",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
    private boolean slabbed$soilMaterial(ServerWorld world,BlockPos pos,BlockState next,Operation<Boolean> original) {
        return NativeTransformations.at(world,pos,()->original.call(world,pos,next));
    }
}
