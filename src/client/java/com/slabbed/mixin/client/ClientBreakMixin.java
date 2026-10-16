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
    @Inject(method={"attackBlock","updateBlockBreakingProgress"},at=@At("HEAD"))
    private void slabbed$sendSelectedPiece(BlockPos pos,net.minecraft.util.math.Direction direction,CallbackInfoReturnable<Boolean> cir) {
        var client=MinecraftClient.getInstance();
        if(client.world==null || !client.world.getBlockState(pos).isOf(Slabbed.CELL))return;
        if(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit && hit.getBlockPos().equals(pos))
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.slabbed.cell.PieceTarget(pos,hit.getPos()));
    }

}
