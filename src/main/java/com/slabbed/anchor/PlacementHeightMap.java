package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.math.BlockPos;

/** Private mutable copies become stable geometry and packet snapshots when published (LAW.md). */
final class PlacementHeightMap extends Long2IntOpenHashMap {
    private transient volatile int[] columnRadii;
    private transient volatile ChunkPositionDyMapPacketCodec.Prepared packetPlan;

    PlacementHeightMap() { super(); }
    PlacementHeightMap(Long2IntMap source) { super(source); }

    private void invalidate() {
        if (columnRadii != null) columnRadii = null;
        if (packetPlan != null) packetPlan = null;
    }

    @Override public int put(long key, int value) { invalidate(); return super.put(key, value); }
    @Override public int remove(long key) { invalidate(); return super.remove(key); }
    @Override public void clear() { invalidate(); super.clear(); }

    ChunkPositionDyMapPacketCodec.Prepared packetPlan() {
        var result = packetPlan;
        if (result == null) packetPlan = result = ChunkPositionDyMapPacketCodec.prepareFresh(this);
        return result;
    }

    private int[] columnRadii() {
        int[] result = columnRadii;
        if (result == null) {
            result = new int[256];
            for (var entry : long2IntEntrySet()) {
                long pos = entry.getLongKey();
                int column = column(BlockPos.unpackLongX(pos), BlockPos.unpackLongZ(pos));
                result[column] = Math.max(result[column], radius(entry.getIntValue()));
            }
            columnRadii = result;
        }
        return result;
    }

    static int ownerRadius(Long2IntMap map, int x, int z) {
        int column = column(x, z);
        if (map instanceof PlacementHeightMap snapshot) return snapshot.columnRadii()[column];
        int result = 0;
        for (var entry : map.long2IntEntrySet()) {
            long pos = entry.getLongKey();
            if (column(BlockPos.unpackLongX(pos), BlockPos.unpackLongZ(pos)) == column) {
                result = Math.max(result, radius(entry.getIntValue()));
            }
        }
        return result;
    }

    private static int column(int x, int z) { return (x & 15) | ((z & 15) << 4); }
    private static int radius(int height) { return (int) ((Math.abs((long) height) + 15L) / 16L + 1L); }
}
