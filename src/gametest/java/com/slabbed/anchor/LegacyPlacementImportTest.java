package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkSerializer;

import java.util.Map;

/** Saved records are imported without guessing from mutable geometry (LAW.md). */
public final class LegacyPlacementImportTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void explicitFactWinsOverConflictingOldMarkers(TestContext h) {
        BlockPos pos = h.getAbsolutePos(new BlockPos(3, 4, 3));
        withFixture(h, pos, true, true, true, -8.5d, input -> {
            var result = LegacyPlacementImport.plan(input);
            h.assertTrue(result.exactHeights().get(pos) != null
                            && result.exactHeights().get(pos) == Double.doubleToRawLongBits(-8.5d)
                            && !result.unresolved().contains(pos),
                    "IMPORT_EXPLICIT_LOST: exact saved height must win without provenance");
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void compoundMarkerHasAnExplicitOneBlockSeat(TestContext h) {
        BlockPos pos = h.getAbsolutePos(new BlockPos(3, 4, 3));
        withFixture(h, pos, true, true, false, null, input -> {
            var result = LegacyPlacementImport.plan(input);
            h.assertTrue(result.exactHeights().get(pos) != null
                            && result.exactHeights().get(pos) == Double.doubleToRawLongBits(-1.0d),
                    "IMPORT_COMPOUND_LOST: compound record must retain its explicit seat");
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void flatMarkerRetainsAnExplicitZeroFact(TestContext h) {
        BlockPos pos = h.getAbsolutePos(new BlockPos(3, 4, 3));
        withFixture(h, pos, false, false, true, null, input -> {
            var result = LegacyPlacementImport.plan(input);
            h.assertTrue(result.exactHeights().containsKey(pos) && result.exactHeights().get(pos) == 0L,
                    "IMPORT_ZERO_LOST: flat record must retain presence, not become absence");
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void ambiguousAnchorIsRetainedWithoutGuessing(TestContext h) {
        BlockPos pos = h.getAbsolutePos(new BlockPos(3, 4, 3));
        withFixture(h, pos, true, false, false, null, input -> {
            var before = input.copy();
            var result = LegacyPlacementImport.plan(input);
            h.assertTrue(result.unresolved().contains(pos) && !result.exactHeights().containsKey(pos),
                    "IMPORT_ANCHOR_GUESSED: a bare anchor is insufficient to recover every family's seat");
            h.assertTrue(input.equals(before) && result.preservedInput().equals(before),
                    "import planning changed or discarded source NBT");
            result.preservedInput().putString("external-change", "must not alter the retained source");
            h.assertTrue(result.preservedInput().equals(before),
                    "a caller mutated the retained source through the result accessor");
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void conflictingFlatAndCompoundRemainUnresolved(TestContext h) {
        BlockPos pos = h.getAbsolutePos(new BlockPos(3, 4, 3));
        withFixture(h, pos, true, true, true, null, input -> {
            var result = LegacyPlacementImport.plan(input);
            h.assertTrue(result.unresolved().contains(pos) && !result.exactHeights().containsKey(pos),
                    "IMPORT_CONFLICT_GUESSED: conflicting saved seats require an explicit decision");
        });
    }

    private static void withFixture(TestContext h, BlockPos pos, boolean anchor, boolean compound,
                                    boolean flat, Double height,
                                    java.util.function.Consumer<NbtCompound> verify) {
        var world = h.getWorld();
        var chunk = world.getWorldChunk(pos);
        var oldAnchors = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
        var oldCompounds = chunk.getAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        var oldFlat = chunk.getAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        var oldHeights = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        try {
            chunk.setAttached(SlabAnchorAttachment.ANCHOR_TYPE,
                    anchor ? new LongOpenHashSet(new long[]{pos.asLong()}) : new LongOpenHashSet());
            chunk.setAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE,
                    compound ? new LongOpenHashSet(new long[]{pos.asLong()}) : new LongOpenHashSet());
            chunk.setAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE,
                    flat ? new LongOpenHashSet(new long[]{pos.asLong()}) : new LongOpenHashSet());
            chunk.removeAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
            if (height != null) SlabAnchorAttachment.writePlacementDyBatch(world,
                    Map.of(pos, Double.doubleToRawLongBits(height)));
            NbtCompound input = ChunkSerializer.serialize(world, chunk);
            input.putString("unrecognized-data", "preserve");
            verify.accept(input);
        } finally {
            restore(chunk, SlabAnchorAttachment.ANCHOR_TYPE, oldAnchors);
            restore(chunk, SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE, oldCompounds);
            restore(chunk, SlabAnchorAttachment.FROZEN_FLAT_TYPE, oldFlat);
            restore(chunk, SlabAnchorAttachment.PLACEMENT_DY_TYPE, oldHeights);
        }
        h.complete();
    }

    private static <T> void restore(net.minecraft.world.chunk.WorldChunk chunk,
                                    net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> type, T value) {
        if (value == null) chunk.removeAttached(type);
        else chunk.setAttached(type, value);
    }
}
