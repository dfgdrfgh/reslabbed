package com.slabbed.compat.relativeblocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Relatively Placed Blocks shares one cell between several small blocks by swapping the first
 * block for its own cluster block, which keeps the members in its block entity; removing members
 * swaps the last one back. Both swaps change the block kind while what the player placed is still
 * standing in the cell, so the cell's remembered height stays (LAW.md Law 1: where it is placed is
 * where it stays). This only answers whether a replacement keeps an existing height; it never
 * makes anything eligible to lower. Absent the mod, every answer is false.
 */
public final class RelativeBlocksCompat {
    public static final String MOD_ID = "relativeblocks";
    private static final ResourceLocation CLUSTER_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "block_cluster");
    /** The cluster's baked model, named exactly so that no other wrapper class is adopted by accident. */
    public static final String CLUSTER_MODEL_CLASS = "com.lukeolafp.relativeblocks.blocks.BlockClusterBakedModel";

    /** Lazily latched; an answer taken before the mod list exists is never cached. */
    private static volatile Boolean loaded;
    private static volatile Block cluster;

    private RelativeBlocksCompat() {
    }

    public static boolean isLoaded() {
        Boolean latched = loaded;
        if (latched == null) {
            ModList modList = ModList.get();
            if (modList == null) {
                return false;
            }
            latched = modList.isLoaded(MOD_ID);
            loaded = latched;
        }
        return latched;
    }

    public static boolean isCluster(BlockState state) {
        if (state == null || !isLoaded()) {
            return false;
        }
        Block block = cluster;
        if (block == null) {
            block = BuiltInRegistries.BLOCK.getOptional(CLUSTER_ID).orElse(null);
            if (block == null) {
                return false;
            }
            cluster = block;
        }
        return state.is(block);
    }

    /** A block joining or leaving a cluster in its own cell: either side is the cluster, the other is not air. */
    public static boolean isClusterMembershipChange(BlockState oldState, BlockState newState) {
        if (oldState == null || newState == null || oldState.isAir() || newState.isAir()) {
            return false;
        }
        return isCluster(oldState) != isCluster(newState);
    }
}
