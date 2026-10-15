package com.slabbed.util;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.util.math.Box;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** One discovery envelope for stored geometry; querying never writes placement heights (LAW.md). */
public final class StoredGeometry {
    private static final int LEGACY_REACH = 3;
    private StoredGeometry() {}

    public static int ownerRadius(BlockView view, int x, int z) {
        return view instanceof World world
                ? Math.min(world.getHeight(), Math.max(LEGACY_REACH,
                        SlabAnchorAttachment.storedOwnerRadius(world, x, z)))
                : LEGACY_REACH;
    }

    public static int queryRadius(BlockView view, Box bounds) {
        return view instanceof World world
                ? Math.min(world.getHeight(), Math.max(LEGACY_REACH, storedReach(world, bounds)))
                : LEGACY_REACH;
    }

    public static boolean hasDisplacement(World world, Box bounds) { return storedReach(world, bounds) > 1; }

    private static int storedReach(World world, Box bounds) {
        int maximum = 0;
        for (int x = (int) Math.floor(bounds.minX) - 1; x <= (int) Math.ceil(bounds.maxX); x++) {
            for (int z = (int) Math.floor(bounds.minZ) - 1; z <= (int) Math.ceil(bounds.maxZ); z++) {
                maximum = Math.max(maximum, SlabAnchorAttachment.storedOwnerRadius(world, x, z));
            }
        }
        return maximum;
    }
}
