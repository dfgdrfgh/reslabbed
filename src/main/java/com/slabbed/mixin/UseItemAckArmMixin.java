package com.slabbed.mixin;

import com.slabbed.network.PlacementDyCorrectionServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arms the C3 author correction once the use packet's sequence is recorded. On 26.3 NeoForge's use
 * handler calls {@code ackBlockChangesUpTo(int)}, as vanilla does; this hooks right after that call.
 * {@link LegacyUseItemAckArmMixin} is the 26.2 twin and the config plugin applies exactly one of the two.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class UseItemAckArmMixin {
    @Shadow @Final public ServerPlayer player;

    @Inject(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;ackBlockChangesUpTo(I)V",
                    shift = At.Shift.AFTER)
    )
    private void slabbed$c3ArmAuthorCorrectionAfterAck(CallbackInfo ci) {
        PlacementDyCorrectionServer.armForCurrentUsePacket(player);
    }
}
