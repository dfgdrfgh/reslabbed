package com.slabbed.nativeblock;

import net.minecraft.block.*;

/** Families whose native identity provides their gameplay and data. */
public final class NativeRules {
    private NativeRules() {}
    public static boolean accepts(BlockState state) {
        var block=state.getBlock();
        return block instanceof AbstractSignBlock || block instanceof BedBlock
                || block instanceof AbstractBannerBlock || block instanceof AbstractSkullBlock || workstation(state);
    }
    public static boolean workstation(BlockState state) {
        var block=state.getBlock();
        return block instanceof CraftingTableBlock || block instanceof SmithingTableBlock
                || block instanceof CartographyTableBlock || block instanceof LoomBlock
                || block instanceof StonecutterBlock || block instanceof GrindstoneBlock;
    }
}
