package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientBreakMixin {
    @Inject(method="breakBlock",at=@At("HEAD"),cancellable=true)
    private void slabbed$awaitConfirmedPieceBreak(BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        var world=MinecraftClient.getInstance().world;
        if(world!=null && world.getBlockState(pos).isOf(Slabbed.CELL)) cir.setReturnValue(true);
    }
}
