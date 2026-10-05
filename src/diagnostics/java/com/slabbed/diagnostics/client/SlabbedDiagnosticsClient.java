package com.slabbed.diagnostics.client;

import com.slabbed.client.SlabbedDebugCommands;
import com.slabbed.client.SlabbedDebugToolBridge;
import com.slabbed.diagnostics.util.LiveCursorIntentRecorder;

public final class SlabbedDiagnosticsClient {
    public static void init() {
        installDebugToolProvider();
        SlabModelStaleSentinelClient.init();
        // Default-off dev-client dy-triad proof on a fresh integrated world (-Dslabbed.neoforge.clientWorldProof).
        com.slabbed.diagnostics.NeoForgeClientWorldProof.init();
    }

    /**
     * The {@code /slabdev} tree used to be registered right here, which is precisely why the command
     * did not exist on a shipped jar: this class is in the development-only diagnostics companion.
     * Registration now lives in the shipped {@code SlabbedDebugCommands}, and this class supplies
     * the implementations behind {@link SlabbedDebugToolBridge}. The command surface is therefore
     * identical in dev and in release — the only difference is whether this provider was installed —
     * and every dev use of {@code /slabdev debug on} exercises the shipped registration path.
     */
    private static void installDebugToolProvider() {
        SlabbedDebugToolBridge.install(new SlabbedDebugToolBridge.Provider() {
            @Override
            public boolean overlayEnabled() {
                return TargetDyOverlay.isEnabled();
            }

            @Override
            public void setOverlayEnabled(boolean enabled) {
                TargetDyOverlay.setEnabled(enabled);
            }

            @Override
            public boolean recorderEnabled() {
                return LiveCursorIntentRecorder.enabled();
            }

            @Override
            public void setRecorderEnabled(boolean enabled) {
                if (LiveCursorIntentRecorder.enabled() != enabled) {
                    LiveCursorIntentRecorder.toggle();
                } else if (enabled) {
                    // Already on: re-open the log so a repeated "on" still yields a usable session.
                    LiveCursorIntentRecorder.bootstrap();
                }
            }

            @Override
            public String recorderStatus() {
                return LiveCursorIntentRecorder.currentLogPathDisplay();
            }
        });
    }
}
