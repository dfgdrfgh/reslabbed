package com.slabbed.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers Slabbed's four play payloads. Every one is optional: a vanilla client, or a client
 * without the mod, connects without them and the server simply never sends.
 */
public final class SlabbedNetwork {
    private SlabbedNetwork() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(SlabbedNetwork::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(
                PlacementDyPredictionEnvelopePayload.TYPE,
                PlacementDyPredictionEnvelopePayload.CODEC,
                (payload, context) -> PlacementDyCorrectionServer.receive((ServerPlayer) context.player(), payload));
        registrar.playToServer(
                ManualDyAdjustPayload.TYPE,
                ManualDyAdjustPayload.CODEC,
                (payload, context) -> ManualDyAdjustServer.receive((ServerPlayer) context.player(), payload));
        registrar.playToClient(
                PlacementDyCorrectionPayload.TYPE,
                PlacementDyCorrectionPayload.CODEC,
                (payload, context) -> ClientReceivers.onCorrection(payload));
        registrar.playToClient(
                FrozenDyModePayload.TYPE,
                FrozenDyModePayload.CODEC,
                (payload, context) -> ClientReceivers.onFrozenDyMode(payload));
    }

    /** True when this player's connection negotiated the payload (the client has the mod). */
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player != null && player.connection != null && player.connection.hasChannel(type);
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Client-side receivers live in the client package; this indirection keeps the shared
     * registration free of client classes on a dedicated server (the handler lambdas are only
     * invoked on a client).
     */
    static final class ClientReceivers {
        private ClientReceivers() {
        }

        static void onCorrection(PlacementDyCorrectionPayload payload) {
            com.slabbed.client.SlabbedClientReceivers.onCorrection(payload);
        }

        static void onFrozenDyMode(FrozenDyModePayload payload) {
            com.slabbed.client.SlabbedClientReceivers.onFrozenDyMode(payload);
        }
    }
}
