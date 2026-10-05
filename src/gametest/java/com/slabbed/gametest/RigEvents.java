package com.slabbed.gametest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * The rig tests drive the entity lifecycle events by hand (the Fabric lines invoke the event's
 * invoker directly). On NeoForge the same is a post on the game bus.
 */
public final class RigEvents {
    private RigEvents() {
    }

    /** Posts the join event and reports whether the load was allowed (not cancelled). */
    public static boolean allowLoad(Entity entity, ServerLevel level, boolean loadedFromDisk) {
        return !NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(entity, level, loadedFromDisk)).isCanceled();
    }

    public static void entityLoad(Entity entity, ServerLevel level) {
        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(entity, level, false));
    }

    public static void entityUnload(Entity entity, ServerLevel level) {
        NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(entity, level));
    }
}
