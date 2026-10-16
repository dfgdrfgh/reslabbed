package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import com.slabbed.client.CellBreakEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class CellHitParticlesMixin {
    @Inject(method="addBlockBreakingParticles",at=@At("HEAD"),cancellable=true)
    private void slabbed$selectedMaterial(BlockPos pos,Direction side,CallbackInfo ci) {
        var client=MinecraftClient.getInstance();var world=client.world;
        if(world==null || !world.getBlockState(pos).isOf(Slabbed.CELL))return;
        ci.cancel();
        var hit=client.crosshairTarget instanceof BlockHitResult h && h.getBlockPos().equals(pos)?h:null;
        var piece=CellWorld.pick(world,pos,hit);
        if(piece!=null)CellBreakEffects.hit(world,pos,piece,side);
    }
}
