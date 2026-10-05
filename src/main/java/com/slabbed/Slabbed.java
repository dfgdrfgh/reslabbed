package com.slabbed;

import com.slabbed.loader.Loader;
import java.lang.reflect.InvocationTargetException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Slabbed.MOD_ID)
public class Slabbed {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    public Slabbed(IEventBus modEventBus) {
        LOGGER.info("Slabbed initialized");
        // One file read, before anything can consult a setting. Unconditional on purpose: the config
        // governs a placement mint that runs on the logical server, so it must be loaded in every
        // environment, not only a development one.
        com.slabbed.config.SlabbedConfig.init();
        com.slabbed.anchor.SlabAnchorAttachment.register(modEventBus);
        com.slabbed.network.SlabbedNetwork.init(modEventBus);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                com.slabbed.network.PlacementDyCorrectionServer.clearPlayer(player);
                com.slabbed.network.ManualDyAdjustServer.clearPlayer(player);
            }
        });
        // Join-time notice only: reports which way this side's stored-height compatibility flag is
        // set so a client can warn about a disagreement (maintainer ruling, 2026-09-06).
        com.slabbed.network.FrozenDyModeServer.register();
        // Recorder break capture: observation only, never cancels the break; gated on the recorder
        // flag in one volatile read.
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
            if (com.slabbed.util.SlabbedDiagnosticsBridge.enabled()
                    && event.getLevel() instanceof net.minecraft.world.level.Level world) {
                com.slabbed.util.SlabbedDiagnosticsBridge.recordBreakEvent(world, event.getPos(), event.getState(),
                        event.getPlayer() == null ? "none" : event.getPlayer().getName().getString());
                com.slabbed.util.SlabbedDiagnosticsBridge.armBreakNeighborhood(
                        world, event.getPos(), world.getGameTime());
            }
        });
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            initClientFeatures(modEventBus);
        }
        if (Loader.isDevelopmentEnvironment()) {
            initDevFeatures();
        }
    }

    private static void initClientFeatures(IEventBus modEventBus) {
        try {
            Class<?> hookClass = Class.forName("com.slabbed.client.SlabbedClient");
            hookClass.getMethod("init", IEventBus.class).invoke(null, modEventBus);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Client hook is unavailable in this environment");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            LOGGER.warn("Failed to initialize client hook", e);
        }
    }

    private static void initDevFeatures() {
        // The /slabrig live-test rig family is DEV-ONLY (release-allowlist ruling): registration is
        // gated here and the classes are excluded from the release artifacts. Reflective hooks keep
        // the release Slabbed.class free of any hard link to classes absent from the release jar.
        registerDevHook("com.slabbed.command.SlabRigCommand", "register");
        registerDevHook("com.slabbed.command.SlabKitCommand", "register");
        registerDevHook("com.slabbed.command.SlabCheckCommand", "register");
        registerDevHook("com.slabbed.command.SlabbedVerCommand", "register");
    }

    private static void registerDevHook(String className, String methodName) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Dev hook {} is unavailable in this environment", className);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            LOGGER.warn("Failed to initialize dev hook {}", className, e);
        }
    }
}
