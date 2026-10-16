package com.slabbed.anchor;

import com.slabbed.util.StoredGeometry;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.Arrays;
import java.util.Map;

/** Storage and query refactoring must preserve visible geometry and published facts (LAW.md). */
public final class PlacementCoreOverhaulTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void interactionReachUsesTheStoredBodyAtEveryDepth(TestContext h) {
        var world = h.getWorld();
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 120, 3));
        world.setBlockState(owner, net.minecraft.block.Blocks.STONE.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_ALL);
        var player = h.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        for (double dy : new double[]{-12, -32, -64, 32}) {
            SlabAnchorAttachment.writePlacementDyBatch(world,
                    Map.of(owner, Double.doubleToRawLongBits(dy)));
            player.setPosition(owner.getX() + 2.5, owner.getY() + dy + 1, owner.getZ() + .5);
            h.assertTrue(player.canInteractWithBlockAt(owner, 1),
                    "STORED_BODY_REACH_REJECTED: the visible body is nearby but its logical cell is distant: dy=" + dy);
            player.setPosition(owner.getX() + .5, owner.getY() + 1, owner.getZ() + .5);
            h.assertTrue(!player.canInteractWithBlockAt(owner, 1),
                    "STORED_BODY_REACH_WIDENED: a nearby logical cell must not make a distant visible body reachable");
        }
        SlabAnchorAttachment.removeAnchor(world, owner);
        h.assertTrue(player.canInteractWithBlockAt(owner, 1), "Unshifted vanilla interaction became unreachable");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void deepStackDoesNotExpandUnrelatedColumns(TestContext h) {
        withMap(h, (origin, map) -> {
            BlockPos deep = origin.add(2, 0, 2);
            map.put(deep.asLong(), -384);
            h.assertTrue(StoredGeometry.ownerRadius(h.getWorld(), deep.getX(), deep.getZ()) == 25,
                    "Deep owner must remain discoverable");
            BlockPos ordinary = origin.add(11, 0, 11);
            h.assertTrue(StoredGeometry.ownerRadius(h.getWorld(), ordinary.getX(), ordinary.getZ()) == 3,
                    "A deep stack widened an unrelated column");
            h.assertTrue(!StoredGeometry.hasDisplacement(h.getWorld(), new Box(ordinary).contract(.2)),
                    "An unrelated column lost its normal optimized collision path");
            h.assertTrue(StoredGeometry.hasDisplacement(h.getWorld(), new Box(deep).contract(.2)),
                    "A displaced column incorrectly selected the ordinary collision path");
        });
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void collisionBoundsFindTheDeepCornerAcrossChunkEdges(TestContext h) {
        withMap(h, (origin, map) -> {
            BlockPos deep = origin.add(15, 0, 15);
            map.put(deep.asLong(), -384);
            Box boundary = new Box(deep.getX() + .7, deep.getY() - 24, deep.getZ() + .7,
                    deep.getX() + 1.3, deep.getY() - 23, deep.getZ() + 1.3);
            h.assertTrue(StoredGeometry.queryRadius(h.getWorld(), boundary) == 25,
                    "Collision bounds missed an offset owner across the corner of four chunks");
        });
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void writesPreserveSnapshotsAndSkipUnchangedZero(TestContext h) {
        withMap(h, (origin, original) -> {
            BlockPos pos = origin.add(2, 0, 2);
            original.put(pos.asLong(), -8);
            byte[] before = encode(h, original, false);
            h.assertTrue(SlabAnchorAttachment.writePlacementDyBatch(h.getWorld(),
                    Map.of(pos, Double.doubleToRawLongBits(-1.0))) == 1, "Changed seat was not written");
            h.assertTrue(original.get(pos.asLong()) == -8 && Arrays.equals(before, encode(h, original, false)),
                    "Writing a new seat mutated an already published snapshot");
            SlabAnchorAttachment.writePlacementDyBatch(h.getWorld(), Map.of(pos, 0L));
            var chunk = h.getWorld().getWorldChunk(pos);
            var stable = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
            h.assertTrue(SlabAnchorAttachment.writePlacementDyBatch(h.getWorld(),
                    Map.of(pos, Double.doubleToRawLongBits(-0.0))) == 0,
                    "An unchanged zero seat was republished");
            h.assertTrue(chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE) == stable,
                    "An unchanged write replaced its stable snapshot");
        });
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void refusedCapacityWritePreservesThePublishedMap(TestContext h) {
        BlockPos at = h.getAbsolutePos(BlockPos.ORIGIN);
        var chunk = h.getWorld().getWorldChunk(at);
        var old = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        try {
            int low = 0, high = 4096;
            BlockPos section = new BlockPos(chunk.getPos().x << 4, (at.getY() >> 4) << 4, chunk.getPos().z << 4);
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                var candidate = unique(section, middle);
                if (ChunkPositionDyMapPacketCodec.encodedByteLength(candidate) <= SlabAnchorAttachment.SYNC_SAFE_ENCODED_BYTES) low = middle;
                else high = middle - 1;
            }
            h.assertTrue(low > 0 && low < 4096, "Capacity fixture did not find a refusal boundary");
            var full = unique(section, low);
            chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, full);
            byte[] before = encode(h, full, false);
            BlockPos next = position(section, low);
            int writes = SlabAnchorAttachment.writePlacementDyBatch(h.getWorld(),
                    Map.of(next, Double.doubleToRawLongBits((-1024 - low) / 16.0)));
            h.assertTrue(writes == 0 && chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE) == full,
                    "A refused write replaced the published map");
            h.assertTrue(Arrays.equals(before, encode(h, full, false)),
                    "A refused write changed existing synchronized heights");
        } finally {
            if (old == null) chunk.removeAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
            else chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, old);
        }
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void cachedPacketsMatchThePreviousReleaseAndInvalidateOnEdits(TestContext h) {
        var map = unique(BlockPos.ORIGIN, 600);
        h.assertTrue(Arrays.equals(encode(h, map, true), encode(h, map, false)),
                "Packet refactor changed the existing wire format");
        map.put(position(BlockPos.ORIGIN, 10).asLong(), -16);
        map.remove(position(BlockPos.ORIGIN, 50).asLong());
        h.assertTrue(Arrays.equals(encode(h, map, true), encode(h, map, false)),
                "Packet plan stayed stale after edits");
        map.clear();
        h.assertTrue(Arrays.equals(encode(h, map, true), encode(h, map, false)),
                "Packet plan stayed stale after clearing the map");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void reportsPacketEncodingCostAgainstThePreviousRelease(TestContext h) {
        for (int alphabet : new int[]{1, 512}) {
            var map = new PlacementHeightMap();
            for (int i = 0; i < 4096; i++) map.put(position(BlockPos.ORIGIN, i).asLong(), -1024 - i % alphabet);
            h.assertTrue(Arrays.equals(encode(h, map, true), encode(h, map, false)), "Benchmark packet bytes differ");
            for (int i = 0; i < 4; i++) { encode(h, map, true); encode(h, map, false); }
            var management = java.lang.management.ManagementFactory.getThreadMXBean();
            com.sun.management.ThreadMXBean allocation = management instanceof com.sun.management.ThreadMXBean bean
                    && bean.isThreadAllocatedMemorySupported() ? bean : null;
            if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
            long[] measured = new long[4];
            for (int kind = 0; kind < 2; kind++) {
                long bytes = allocation == null ? 0 : allocation.getCurrentThreadAllocatedBytes();
                long start = System.nanoTime();
                for (int i = 0; i < 20; i++) encode(h, map, kind == 0);
                measured[kind * 2] = System.nanoTime() - start;
                measured[kind * 2 + 1] = allocation == null ? -1 : allocation.getCurrentThreadAllocatedBytes() - bytes;
            }
            com.slabbed.Slabbed.LOGGER.info("CORE_PACKET_BENCHMARK facts=4096 alphabet={} iterations=20 previous_ns={} current_ns={} previous_allocated={} current_allocated={}",
                    alphabet, measured[0], measured[2], measured[1], measured[3]);
        }
        h.complete();
    }

    private static PlacementHeightMap unique(BlockPos origin, int count) {
        var map = new PlacementHeightMap();
        for (int i = 0; i < count; i++) map.put(position(origin, i).asLong(), -1024 - i);
        return map;
    }

    private static BlockPos position(BlockPos origin, int i) { return origin.add(i & 15, i >> 8, (i >> 4) & 15); }

    private static byte[] encode(TestContext h, Long2IntOpenHashMap map, boolean reference) {
        RegistryByteBuf buffer = new RegistryByteBuf(PacketByteBufs.create(), h.getWorld().getRegistryManager());
        try {
            if (reference) ReferenceHeightPacketCodec.INSTANCE.encode(buffer, map);
            else ChunkPositionDyMapPacketCodec.INSTANCE.encode(buffer, map);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static void withMap(TestContext h, java.util.function.BiConsumer<BlockPos, PlacementHeightMap> action) {
        BlockPos at = h.getAbsolutePos(BlockPos.ORIGIN);
        var chunk = h.getWorld().getWorldChunk(at);
        var old = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        var map = new PlacementHeightMap();
        chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, map);
        try { action.accept(new BlockPos(chunk.getPos().x << 4, at.getY() + 24, chunk.getPos().z << 4), map); }
        finally {
            if (old == null) chunk.removeAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
            else chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, old);
        }
    }
}
