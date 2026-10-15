package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/** Section palettes preserve exact sixteenth-grid heights, including tall stacks (LAW.md).
 * Disk heights retain their existing integer-array representation. The network decoder also
 * accepts the previous signed-byte palette; new packets use ZigZag VarInts for palette values.
 */
final class ChunkPositionDyMapPacketCodec {
    private static final int COMPACT_FORMAT_MARKER = -2;
    private static final int WORDS_PER_SECTION = 64;
    private static final int MAX_SECTION_GROUPS = 4_096;
    private static final int MAX_PALETTE_ENTRIES = 4_096;

    static final PacketCodec<RegistryByteBuf, Long2IntOpenHashMap> INSTANCE =
            PacketCodec.of(ChunkPositionDyMapPacketCodec::encode, ChunkPositionDyMapPacketCodec::decode);

    private ChunkPositionDyMapPacketCodec() {
    }

    record Section(SectionKey key, long[] words, long nonEmptyWords, int[] palette,
                   it.unimi.dsi.fastutil.ints.Int2IntMap indices) {}
    record Prepared(java.util.List<Section> sections, int byteLength) {}

    private static Prepared prepared(Long2IntMap facts) {
        return facts instanceof PlacementHeightMap snapshot ? snapshot.packetPlan() : prepareFresh(facts);
    }

    static Prepared prepareFresh(Long2IntMap facts) {
        var grouped = groupSections(facts);
        var sections = new java.util.ArrayList<Section>(grouped.size());
        int length = varIntLength(COMPACT_FORMAT_MARKER) + varIntLength(grouped.size());
        for (var entry : grouped.entrySet()) {
            SectionKey key = entry.getKey();
            long[] words = entry.getValue();
            var values = new it.unimi.dsi.fastutil.ints.IntArrayList();
            var indices = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(2);
            indices.defaultReturnValue(-1);
            forEachPosition(key, words, packed -> {
                int value = facts.get(packed);
                if (indices.get(value) < 0) {
                    indices.put(value, values.size());
                    values.add(value);
                }
            });
            int[] palette = values.toIntArray();
            long mask = nonEmptyWordMask(words);
            length += varIntLength(key.chunkX()) + varIntLength(key.sectionY()) + varIntLength(key.chunkZ());
            length += Long.BYTES + Long.BYTES * Long.bitCount(mask) + varIntLength(palette.length);
            for (int value : palette) length += varIntLength(zigZag(value));
            if (palette.length > 1) {
                int occupied = 0;
                for (long word : words) occupied += Long.bitCount(word);
                length += (occupied * bitsPerIndex(palette.length) + 7) / 8;
            }
            sections.add(new Section(key, words, mask, palette, palette.length > 1 ? indices : null));
        }
        return new Prepared(java.util.List.copyOf(sections), length);
    }

    private static void encode(Long2IntMap facts, RegistryByteBuf buf) {
        Prepared plan = prepared(facts);
        buf.writeVarInt(COMPACT_FORMAT_MARKER);
        buf.writeVarInt(plan.sections().size());
        for (Section section : plan.sections()) {
            SectionKey key = section.key();
            buf.writeVarInt(key.chunkX());
            buf.writeVarInt(key.sectionY());
            buf.writeVarInt(key.chunkZ());
            buf.writeLong(section.nonEmptyWords());
            for (int i = 0; i < WORDS_PER_SECTION; i++) {
                if ((section.nonEmptyWords() & (1L << i)) != 0L) buf.writeLong(section.words()[i]);
            }
            int[] palette = section.palette();
            buf.writeVarInt(palette.length);
            for (int value : palette) buf.writeVarInt(zigZag(value));
            if (palette.length <= 1) continue;
            BitWriter writer = new BitWriter(buf, bitsPerIndex(palette.length));
            forEachPosition(key, section.words(), packed -> {
                int index = section.indices().get(facts.get(packed));
                if (index < 0) throw new IllegalStateException("Placement snapshot changed during encoding");
                writer.write(index);
            });
            writer.flush();
        }
    }

    static int encodedByteLength(Long2IntMap facts) { return prepared(facts).byteLength(); }

    // ── decode ────────────────────────────────────────────────────────

    private static Long2IntOpenHashMap decode(RegistryByteBuf buf) {
        int marker = buf.readVarInt();
        if (marker != COMPACT_FORMAT_MARKER && marker != -1) {
            throw new IllegalArgumentException(
                    "Unsupported chunk-position-dy attachment packet format " + marker);
        }
        int sectionCount = buf.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTION_GROUPS) {
            throw new IllegalArgumentException(
                    "Invalid chunk-position-dy attachment section count " + sectionCount);
        }

        Long2IntOpenHashMap facts = new PlacementHeightMap();
        for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
            int chunkX = buf.readVarInt();
            int sectionY = buf.readVarInt();
            int chunkZ = buf.readVarInt();
            long nonEmptyWordMask = buf.readLong();

            long[] words = new long[WORDS_PER_SECTION];
            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
                if ((nonEmptyWordMask & (1L << wordIndex)) != 0L) {
                    words[wordIndex] = buf.readLong();
                }
            }

            int paletteSize = marker == -1 ? buf.readUnsignedByte() + 1 : buf.readVarInt();
            if (paletteSize < 1 || paletteSize > MAX_PALETTE_ENTRIES) {
                throw new IllegalArgumentException(
                        "Invalid chunk-position-dy attachment palette size " + paletteSize);
            }
            int[] palette = new int[paletteSize];
            for (int index = 0; index < paletteSize; index++) {
                palette[index] = marker == -1 ? buf.readByte() : unZigZag(buf.readVarInt());
            }

            SectionKey key = new SectionKey(chunkX, sectionY, chunkZ);
            if (paletteSize == 1) {
                int uniform = palette[0];
                forEachPosition(key, words, packed -> facts.put(packed, uniform));
                continue;
            }
            BitReader reader = new BitReader(buf, bitsPerIndex(paletteSize));
            forEachPosition(key, words, packed -> {
                int index = reader.read();
                if (index >= palette.length) {
                    throw new IllegalArgumentException(
                            "chunk-position-dy attachment palette index " + index + " out of range");
                }
                facts.put(packed, palette[index]);
            });
        }
        return facts;
    }

    // ── shared structure ──────────────────────────────────────────────

    private static TreeMap<SectionKey, long[]> groupSections(Long2IntMap facts) {
        TreeMap<SectionKey, long[]> sections = new TreeMap<>();
        for (long packed : facts.keySet()) {
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);
            SectionKey key = new SectionKey(x >> 4, y >> 4, z >> 4);
            long[] words = sections.computeIfAbsent(key, ignored -> new long[WORDS_PER_SECTION]);
            int bitIndex = bitIndex(x, y, z);
            words[bitIndex >>> 6] |= 1L << (bitIndex & 63);
        }
        return sections;
    }

    private static long nonEmptyWordMask(long[] words) {
        long mask = 0L;
        for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
            if (words[wordIndex] != 0L) {
                mask |= 1L << wordIndex;
            }
        }
        return mask;
    }

    /** 1, 2, 4 or 8 — the smallest power-of-two bit width that addresses the palette. */
    private static int bitsPerIndex(int paletteSize) {
        if (paletteSize <= 2) {
            return 1;
        }
        if (paletteSize <= 4) {
            return 2;
        }
        if (paletteSize <= 16) {
            return 4;
        }
        return paletteSize <= 256 ? 8 : 16;
    }

    /**
     * Walks a section's occupied positions in THE canonical order — word index ascending, then bit
     * index ascending inside the word. Encoder, decoder, palette construction and size computation
     * all use this one walk, so the packed value stream can never be misaligned against the
     * occupancy bitmap.
     */
    private static void forEachPosition(SectionKey key, long[] words, PositionSink sink) {
        for (int wordIndex = 0; wordIndex < WORDS_PER_SECTION; wordIndex++) {
            long word = words[wordIndex];
            while (word != 0L) {
                int bitInWord = Long.numberOfTrailingZeros(word);
                int bitIndex = (wordIndex << 6) | bitInWord;
                sink.accept(BlockPos.asLong(
                        (key.chunkX() << 4) + (bitIndex & 15),
                        (key.sectionY() << 4) + ((bitIndex >>> 8) & 15),
                        (key.chunkZ() << 4) + ((bitIndex >>> 4) & 15)));
                word &= word - 1;
            }
        }
    }

    private static int bitIndex(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    /** Vanilla's own VarInt width formula, inlined so the size prediction needs no buffer. */
    private static int zigZag(int value) { return (value << 1) ^ (value >> 31); }
    private static int unZigZag(int value) { return (value >>> 1) ^ -(value & 1); }

    private static int varIntLength(int value) {
        for (int width = 1; width < 5; width++) {
            if ((value & (-1 << (width * 7))) == 0) {
                return width;
            }
        }
        return 5;
    }

    @FunctionalInterface
    private interface PositionSink {
        void accept(long packedPosition);
    }

    /** Least-significant-bit-first packer for 1/2/4/8-bit palette indices. */
    private static final class BitWriter {
        private final RegistryByteBuf buf;
        private final int bits;
        private int accumulator;
        private int filled;

        BitWriter(RegistryByteBuf buf, int bits) {
            this.buf = buf;
            this.bits = bits;
        }

        void write(int value) {
            if (bits == 16) { buf.writeShort(value); return; }
            accumulator |= (value & ((1 << bits) - 1)) << filled;
            filled += bits;
            if (filled == 8) {
                buf.writeByte(accumulator);
                accumulator = 0;
                filled = 0;
            }
        }

        void flush() {
            if (filled > 0) {
                buf.writeByte(accumulator);
                accumulator = 0;
                filled = 0;
            }
        }
    }

    /** The {@link BitWriter} mirror. */
    private static final class BitReader {
        private final RegistryByteBuf buf;
        private final int bits;
        private int accumulator;
        private int remaining;

        BitReader(RegistryByteBuf buf, int bits) {
            this.buf = buf;
            this.bits = bits;
        }

        int read() {
            if (bits == 16) return buf.readUnsignedShort();
            if (remaining == 0) {
                accumulator = buf.readUnsignedByte();
                remaining = 8;
            }
            int value = accumulator & ((1 << bits) - 1);
            accumulator >>>= bits;
            remaining -= bits;
            return value;
        }
    }

    private record SectionKey(int chunkX, int sectionY, int chunkZ)
            implements Comparable<SectionKey> {
        @Override
        public int compareTo(SectionKey other) {
            int byChunkX = Integer.compare(chunkX, other.chunkX);
            if (byChunkX != 0) {
                return byChunkX;
            }
            int byChunkZ = Integer.compare(chunkZ, other.chunkZ);
            if (byChunkZ != 0) {
                return byChunkZ;
            }
            return Integer.compare(sectionY, other.sectionY);
        }
    }
}
