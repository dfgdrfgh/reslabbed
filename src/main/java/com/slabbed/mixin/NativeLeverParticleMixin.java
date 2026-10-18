package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeverBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LeverBlock.class)
public abstract class NativeLeverParticleMixin {
    @ModifyArgs(method="spawnParticles",at=@At(value="INVOKE",target="Lnet/minecraft/world/WorldAccess;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"))
    private static void slabbed$leverOrigin(Args args,BlockState state,WorldAccess world,BlockPos pos,float alpha) {
        args.set(2,(double)args.get(2)+NativeSeats.offset(world,pos,state));
    }
}
