package com.slabbed.test.mixin;

import com.slabbed.test.CellClearancePacketAudit;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class CellClearancePacketCaptureMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onPlayerInteractBlock",at=@At("RETURN"))
    private void slabbed$observeBlockedPlacement(PlayerInteractBlockC2SPacket packet,CallbackInfo ci) {
        CellClearancePacketAudit.handled(player.getWorld(),packet);
    }
}
