package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeShapes;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ParticleUtil.class)
public abstract class NativeDistributedParticleMixin {
    /** The spread is a local height; an already translated shape would scale it by the seat. */
    @WrapOperation(method="spawnParticlesAround(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/particle/ParticleEffect;)V",at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    private static VoxelShape slabbed$localSpread(BlockState state,BlockView world,BlockPos pos,Operation<VoxelShape> original) {
        return NativeSeats.offset(world,pos,state)==0?original.call(state,world,pos)
                :NativeShapes.withRaw(()->original.call(state,world,pos));
    }
    @ModifyArgs(method="spawnParticlesAround(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;IDDZLnet/minecraft/particle/ParticleEffect;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/WorldAccess;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"))
    private static void slabbed$distributedOrigin(Args args,WorldAccess world,BlockPos pos,int count,double horizontal,double vertical,boolean force,ParticleEffect effect) {
        args.set(2,(double)args.get(2)+NativeSeats.offset(world,pos));
    }
}
