package com.slabbed.test;

import net.minecraft.block.BlockState;

/** Observes the production particle entry point, including material selection. */
public final class CellFeedbackAudit {
    public static int breaks;
    public static BlockState material;
    public static void reset() { breaks=0;material=null; }
}
