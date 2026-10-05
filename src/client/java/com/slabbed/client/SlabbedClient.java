package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.loader.Loader;
import java.lang.reflect.InvocationTargetException;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModLoadingContext;

/** Client-side initialisation, reached only through the reflective hook in {@code Slabbed}. */
public final class SlabbedClient {
    private SlabbedClient() {
    }

    public static void init(IEventBus modEventBus) {
        PlacementDyPredictionJournal.init();
        initRuntimeDiagnostics("logInspectSessionStart", "inspect diagnostics",
                Boolean.getBoolean("slabbed.inspect") || Boolean.getBoolean("slabbed.b2.live.trace"));
        SlabbedModelLoadingPlugin.init(modEventBus);
        SlabAnchorClientSync.init();
        initRuntimeDiagnostics("initBsFbLiveTraceClient", "BS/FB live trace client",
                Boolean.getBoolean("slabbed.bsfb.live.trace"));
        initDyFingerprintDump();
        BetaNoticeClient.init();
        FrozenDyModeClient.init();
        // Ships in EVERY jar, default off — the standing debug-tooling rule: /slabdy and /slabdev
        // must be invocable on a shipped jar. Unconditional on purpose.
        SlabbedDebugCommands.register();
        // Two UNBOUND key mappings for the manual height nudge, registered on the mod bus event.
        ManualDyKeybind.init(modEventBus);
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new SlabbedSettingsScreen());
    }

    private static void initDyFingerprintDump() {
        // Tier-2 client dy-fingerprint dump (RELEASE_SANITY_CHECKLIST §3); dev-only, excluded from the jar.
        if (!Loader.isDevelopmentEnvironment()) {
            return;
        }
        invokeStaticInit("com.slabbed.client.DyFingerprintDump", "dy fingerprint dump");
    }

    private static void initRuntimeDiagnostics(String methodName, String label, boolean enabled) {
        if (!enabled) {
            return;
        }
        invokeStaticNoArg("com.slabbed.util.RuntimeDiagnostics", methodName, label);
    }

    private static void invokeStaticInit(String className, String label) {
        invokeStaticNoArg(className, "init", label);
    }

    private static void invokeStaticNoArg(String className, String methodName, String label) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("{} is unavailable in this environment", label);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to initialize {}", label, e);
        }
    }
}
