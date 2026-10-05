package com.slabbed.gametest;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

/** The test mod: runs the test-content initialisers, registers their blocks, registers the suite. */
@Mod(SlabbedGameTests.MOD_ID)
public final class SlabbedGameTestMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public SlabbedGameTestMod(IEventBus modEventBus) {
        for (String className : SlabbedGameTestClasses.initializers()) {
            try {
                Object initializer = Class.forName(className).getDeclaredConstructor().newInstance();
                ((TestModInitializer) initializer).onInitialize();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("test initializer failed: " + className, e);
            }
        }
        // Every registered test class is initialised now, before the register event, so each
        // lazy test block has queued its holder by the time the registries open.
        SlabbedGameTests.prepare();
        modEventBus.addListener((RegisterEvent event) -> TestBlocks.register(event));
        modEventBus.addListener((RegisterGameTestsEvent event) -> SlabbedGameTests.registerTests(event));
        LOGGER.info("[slabbed_gametest] test mod constructed");
    }
}
