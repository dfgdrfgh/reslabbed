package com.slabbed.dev;

import com.slabbed.anchor.SlabAnchorAttachment;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Explicit saved-marker fixtures; excluded from production artifacts (LAW.md). */
public final class LegacyPlacementFixtures {
    private LegacyPlacementFixtures() {}
    private static void mark(World world, BlockPos pos, AttachmentType<LongOpenHashSet> type) {
        if (world==null || world.isClient() || pos==null) return;
        var chunk=SlabAnchorAttachment.loadedAttachmentChunk(world,pos);
        if (chunk==null) return;
        var original=chunk.getAttached(type);
        if (original!=null && original.contains(pos.asLong())) return;
        var copy=original==null ? new LongOpenHashSet() : new LongOpenHashSet(original);
        copy.add(pos.asLong());
        chunk.setAttached(type,copy);
    }
    public static void addAnchor(World w,BlockPos p,BlockState s) { mark(w,p,SlabAnchorAttachment.ANCHOR_TYPE); }
    public static void addCompoundFullBlockAnchor(World w,BlockPos p,BlockState s) {
        mark(w,p,SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
    }
    public static void freezeLoweredOnPlace(World w,BlockPos p,BlockState s) {
        mark(w,p,com.slabbed.anchor.PlacementGeometry.offsetY(w,p,s)<0
                ? SlabAnchorAttachment.ANCHOR_TYPE : SlabAnchorAttachment.FROZEN_FLAT_TYPE);
    }
    public static void updatePersistentLoweredSlabCarrier(World w,BlockPos p,BlockState s) {
        mark(w,p,SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
    }
    public static void addSideAdjacentLoweredFullAnchor(World w,BlockPos p,BlockState s,BlockPos source,BlockState sourceState) {}
    public static void addTopOfCompoundFullAnchor(World w,BlockPos p,BlockState s,BlockPos source,BlockState sourceState) {
        mark(w,p,SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
    }
    public static void addCompoundVisibleSideLowerSlab(World w,BlockPos p,BlockState s,BlockPos source,BlockState sourceState) {
        mark(w,p,SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
    }
    public static void addCompoundVisibleSideUpperSlab(World w,BlockPos p,BlockState s,BlockPos source,BlockState sourceState) {
        mark(w,p,SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
    }
    public static void addCompoundVisibleSideDoubleSlab(World w,BlockPos p,BlockState s,BlockPos source,BlockState sourceState) {
        mark(w,p,SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
    }
    public static void addCompoundVisibleOwnerTopSlab(World w,BlockPos p,BlockState s,BlockPos source,BlockState sourceState) {
        mark(w,p,SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);
    }
}
