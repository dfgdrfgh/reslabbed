package com.slabbed.client;

import com.slabbed.network.FrozenDyModePayload;
import com.slabbed.network.PlacementDyCorrectionPayload;
import com.slabbed.network.PlacementDyPredictionBridge;
import net.minecraft.client.Minecraft;

/** Client-side payload receivers (invoked on the client main thread by the network layer). */
public final class SlabbedClientReceivers {
    private SlabbedClientReceivers() {
    }

    public static void onCorrection(PlacementDyCorrectionPayload payload) {
        PlacementDyPredictionBridge.traceCorrectionWire("RECEIVE", payload.signature());
        PlacementDyPredictionJournal.onCorrection(Minecraft.getInstance().level, payload);
    }

    public static void onFrozenDyMode(FrozenDyModePayload payload) {
        FrozenDyModeClient.onPayload(payload);
    }
}
