package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractCandleBlock.class)
public abstract class NativeCandleParticleMixin {
    @ModifyExpressionValue(method="randomDisplayTick",at=@At(value="INVOKE",target="Lnet/minecraft/block/AbstractCandleBlock;getParticleOffsets(Lnet/minecraft/block/BlockState;)Ljava/lang/Iterable;"))
    private Iterable<Vec3d> slabbed$flameOrigins(Iterable<Vec3d> offsets,BlockState state,World world,BlockPos pos,Random random) {
        return slabbed$offsets(offsets,NativeSeats.offset(world,pos,state));
    }
    @ModifyExpressionValue(method="extinguish",at=@At(value="INVOKE",target="Lnet/minecraft/block/AbstractCandleBlock;getParticleOffsets(Lnet/minecraft/block/BlockState;)Ljava/lang/Iterable;"))
    private static Iterable<Vec3d> slabbed$extinguishOrigins(Iterable<Vec3d> offsets,PlayerEntity player,BlockState state,WorldAccess world,BlockPos pos) {
        return slabbed$offsets(offsets,NativeSeats.offset(world,pos,state));
    }
    @Unique
    private static Iterable<Vec3d> slabbed$offsets(Iterable<Vec3d> offsets,double dy) {
        if(dy==0)return offsets;
        var shifted=new java.util.ArrayList<Vec3d>();
        for(var offset:offsets)shifted.add(offset.add(0,dy,0));
        return shifted;
    }
}
