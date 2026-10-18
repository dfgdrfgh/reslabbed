package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.CampfireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(CampfireBlock.class)
public abstract class NativeCampfireParticleMixin {
    @ModifyArgs(method="spawnSmokeParticle",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;addImportantParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V"))
    private static void slabbed$smokeOrigin(Args args,World world,BlockPos pos,boolean signal,boolean smoke) {
        args.set(3,(double)args.get(3)+NativeSeats.offset(world,pos));
    }
    @ModifyArgs(method="spawnSmokeParticle",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"))
    private static void slabbed$extinguishOrigin(Args args,World world,BlockPos pos,boolean signal,boolean smoke) {
        args.set(2,(double)args.get(2)+NativeSeats.offset(world,pos));
    }
}
