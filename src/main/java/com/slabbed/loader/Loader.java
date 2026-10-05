package com.slabbed.loader;

import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

/**
 * The loader facade: every question shared code asks the mod loader goes through here, so the
 * code that answers them is the only place that names the loader.
 */
public final class Loader {
    private Loader() {
    }

    /** True when the mod with this id is loaded. Safe before mod construction (loading list). */
    public static boolean isModLoaded(String modId) {
        LoadingModList loading = FMLLoader.getCurrentOrNull() == null ? null : FMLLoader.getCurrent().getLoadingModList();
        if (loading != null && loading.getModFileById(modId) != null) {
            return true;
        }
        try {
            return ModList.get() != null && ModList.get().isLoaded(modId);
        } catch (IllegalStateException notReady) {
            return false;
        }
    }

    /** True in a development environment (the run configurations), false on a shipped jar. */
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /** The running version of the mod with this id, as a string, if the mod is loaded. */
    public static Optional<String> modVersion(String modId) {
        try {
            return ModList.get().getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString());
        } catch (IllegalStateException notReady) {
            return Optional.empty();
        }
    }

    /** The Minecraft version the game reports, for example {@code 26.2}. */
    public static String minecraftVersion() {
        return FMLLoader.getCurrent().getVersionInfo().mcVersion();
    }
}
