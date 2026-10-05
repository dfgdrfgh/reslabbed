package com.slabbed.client;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.network.FrozenDyModeMessages;
import com.slabbed.network.FrozenDyModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Receives the server's stored-height compatibility flag on join and, only when it disagrees with
 * this client's own flag, logs one warning and tells the player once.
 *
 * <p>Receive-only: this class never sends. Joining a vanilla or non-Slabbed server simply produces
 * no callback.
 *
 * <p><b>Once per CONNECTION, not once per launch.</b> The gate is cleared on both connection init
 * and disconnect, so a player who fixes nothing and reconnects is told again — the mismatch is still
 * there, and a warning shown once and then permanently silenced would read as resolved.
 *
 * <p>No behaviour is changed here or anywhere else by the reported value; the two sides keep running
 * whatever their own launch settings say (maintainer ruling, 2026-09-06).
 */
public final class FrozenDyModeClient {

    private static volatile boolean warnedThisConnection;

    private FrozenDyModeClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> warnedThisConnection = false);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> warnedThisConnection = false);
    }

    /** The clientbound receiver; the network layer runs it on the client main thread. */
    public static void onPayload(FrozenDyModePayload payload) {
        boolean serverEnabled = payload.frozenDyEnabled();
        boolean clientEnabled = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        if (!FrozenDyModeMessages.sidesMismatch(serverEnabled, clientEnabled) || warnedThisConnection) {
            return;
        }
        warnedThisConnection = true;
        Slabbed.LOGGER.warn(
                "Stored-height compatibility flag differs between the sides (server={}, client={}); "
                        + "drawn heights and the heights the server resolves against can disagree",
                serverEnabled, clientEnabled);
        Component message = FrozenDyModeMessages.mismatchMessage(serverEnabled, clientEnabled);
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }
}
