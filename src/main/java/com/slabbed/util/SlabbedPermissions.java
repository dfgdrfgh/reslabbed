package com.slabbed.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Gamemaster permission check that resolves on every Minecraft version this jar declares.
 *
 * <p>Minecraft 1.21.11 replaced {@code ServerCommandSource.hasPermissionLevel(int)} with the
 * permission-set API. One jar covers 1.21.9 through 1.21.11, so the check is chosen at class
 * initialization: the legacy method is looked up through the loader's mapping resolver (its
 * intermediary name is stable across the covered versions) and used when present; otherwise the
 * permission-set API is used. The modern branch lives in a nested class so that no 1.21.11-only
 * type is resolved on a version that lacks it.
 */
public final class SlabbedPermissions {

    private static final MethodHandle LEGACY_HAS_PERMISSION_LEVEL = resolveLegacy();

    private SlabbedPermissions() {}

    /** True when the source may run maintainer-level commands (vanilla permission level 2). */
    public static boolean isGamemaster(ServerCommandSource source) {
        if (LEGACY_HAS_PERMISSION_LEVEL != null) {
            try {
                return (boolean) LEGACY_HAS_PERMISSION_LEVEL.invoke(source, 2);
            } catch (Throwable t) {
                throw new IllegalStateException("Slabbed: legacy permission check failed", t);
            }
        }
        return ModernCheck.isGamemaster(source);
    }

    private static MethodHandle resolveLegacy() {
        try {
            String runtimeName = FabricLoader.getInstance().getMappingResolver().mapMethodName(
                    "intermediary", "net.minecraft.class_2168", "method_9259", "(I)Z");
            return MethodHandles.publicLookup().findVirtual(ServerCommandSource.class, runtimeName,
                    MethodType.methodType(boolean.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /** Resolved only when the legacy method is absent, i.e. on 1.21.11 and later. */
    private static final class ModernCheck {
        private ModernCheck() {}

        static boolean isGamemaster(ServerCommandSource source) {
            return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS));
        }
    }
}
