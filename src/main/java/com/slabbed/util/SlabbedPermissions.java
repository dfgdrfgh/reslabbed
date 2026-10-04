package com.slabbed.util;

import net.minecraft.server.command.ServerCommandSource;

/**
 * Gamemaster permission check. Every Minecraft version this jar declares has
 * {@code ServerCommandSource.hasPermissionLevel(int)} at compile time, so the check is a direct call
 * that the remapper carries into production and the development client resolves under its own
 * names. (The 1.21.9–1.21.11 jar, compiled against 1.21.11 where the method is gone, looks it up by
 * intermediary name instead; that lookup cannot resolve in a Yarn-named development runtime.)
 */
public final class SlabbedPermissions {

    private SlabbedPermissions() {}

    /** True when the source may run maintainer-level commands (vanilla permission level 2). */
    public static boolean isGamemaster(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }
}
