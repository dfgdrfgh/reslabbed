package com.slabbed.mixin;

import com.slabbed.network.PlacementDyCorrectionServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 26.2 twin of {@link UseItemAckArmMixin}: NeoForge's patched 26.2 use handler writes the
 * sequence straight into the {@code ackBlockChangesUpTo} field instead of calling the method, so this
 * hooks right after that field write. The config plugin applies exactly one of the two.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LegacyUseItemAckArmMixin {
    @Shadow @Final public ServerPlayer player;

    @Inject(
            method = "handleUseItemOn",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;ackBlockChangesUpTo:I",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER)
    )
    private void slabbed$c3ArmAuthorCorrectionAfterAck(CallbackInfo ci) {
        PlacementDyCorrectionServer.armForCurrentUsePacket(player);
    }
}
