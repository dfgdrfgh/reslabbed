package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(CampfireBlockEntity.class)
public abstract class NativeCampfireCookingParticleMixin {
    @ModifyArgs(method="clientTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"))
    private static void slabbed$foodSmokeOrigin(Args args,World world,BlockPos pos,BlockState state,CampfireBlockEntity campfire) {
        args.set(2,(double)args.get(2)+NativeSeats.offset(world,pos,state));
    }
}
