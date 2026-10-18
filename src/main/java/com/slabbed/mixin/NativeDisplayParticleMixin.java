package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Display emissions use the same fixed origin as the native model. */
@Mixin({CampfireBlock.class,TorchBlock.class,WallTorchBlock.class})
public abstract class NativeDisplayParticleMixin {
    @ModifyArgs(method="randomDisplayTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"))
    private void slabbed$displayOrigin(Args args,BlockState state,World world,BlockPos pos,Random random) {
        args.set(2,(double)args.get(2)+NativeSeats.offset(world,pos,state));
    }
}
