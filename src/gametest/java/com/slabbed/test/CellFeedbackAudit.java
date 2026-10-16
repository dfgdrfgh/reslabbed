package com.slabbed.test;

import net.minecraft.block.BlockState;

/** Observes the production particle entry point, including material selection. */
public final class CellFeedbackAudit {
    public static int breaks;
    public static BlockState material;
    public static int sounds;
    public static net.minecraft.util.Identifier sound;
    public static int cellUpdates,deferredCellUpdates;
    public static int queuedCellSections,delayedCellSections;
    public static final java.util.Set<Long> changedSections=new java.util.HashSet<>();
    public static void reset() { breaks=0;material=null;sounds=0;sound=null; }
}
