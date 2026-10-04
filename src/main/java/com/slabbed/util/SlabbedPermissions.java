package com.slabbed.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Gamemaster permission check that resolves on every Minecraft version this jar declares.
 *
 * <p>Every version from 1.21.6 to 1.21.8 has {@code ServerCommandSource.hasPermissionLevel(int)}; the
 * method is looked up once through the loader's mapping resolver by its intermediary name, which is
 * stable across the covered versions, so the check never names a Yarn spelling that could drift.
 * (1.21.11 replaced this method with the permission-set API; that version is served by another jar.)
 */
public final class SlabbedPermissions {

    private static final MethodHandle HAS_PERMISSION_LEVEL = resolve();

    private SlabbedPermissions() {}

    /** True when the source may run maintainer-level commands (vanilla permission level 2). */
    public static boolean isGamemaster(ServerCommandSource source) {
        try {
            return (boolean) HAS_PERMISSION_LEVEL.invoke(source, 2);
        } catch (Throwable t) {
            throw new IllegalStateException("Slabbed: permission check failed", t);
        }
    }

    private static MethodHandle resolve() {
        try {
            String runtimeName = FabricLoader.getInstance().getMappingResolver().mapMethodName(
                    "intermediary", "net.minecraft.class_2168", "method_9259", "(I)Z");
            return MethodHandles.publicLookup().findVirtual(ServerCommandSource.class, runtimeName,
                    MethodType.methodType(boolean.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Slabbed: ServerCommandSource.hasPermissionLevel(int) is missing", e);
        }
    }
}
