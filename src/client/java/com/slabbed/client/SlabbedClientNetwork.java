package com.slabbed.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-side sends to the server; the connection must have negotiated the payload. */
public final class SlabbedClientNetwork {
    private SlabbedClientNetwork() {
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    /**
     * Sends only when the server negotiated the payload: NeoForge checks the channel at send time and
     * throws on the client tick otherwise, so a server without Slabbed must simply be ignored.
     */
    public static void send(CustomPacketPayload payload) {
        if (!canSend(payload.type())) {
            return;
        }
        ClientPacketDistributor.sendToServer(payload);
    }
}
