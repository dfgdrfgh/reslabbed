package com.slabbed.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable, validated occupancy for exactly one physical Minecraft cell. */
public record CellContents<T>(List<Fragment<T>> fragments) {
    public CellContents {
        fragments = List.copyOf(fragments);
        int used = 0;
        for (var part : fragments) {
            if ((used & part.mask()) != 0) throw new IllegalArgumentException("overlapping pieces");
            used |= part.mask();
        }
    }
    public static <T> CellContents<T> empty() { return new CellContents<>(List.of()); }
    public int occupiedMask() {
        int mask = 0;
        for (var part : fragments) mask |= part.mask();
        return mask;
    }
    public CellContents<T> add(Fragment<T> fragment) {
        var next = new ArrayList<>(fragments);
        next.add(fragment);
        return new CellContents<>(next);
    }
    public CellContents<T> remove(int origin) {
        return new CellContents<>(fragments.stream().filter(p -> p.offset() != origin).toList());
    }
    public record Fragment<T>(int offset, int length, T value) {
        public Fragment {
            Objects.requireNonNull(value);
            if (length < 1 || length > 2 || offset < -1 || offset > 1
                    || offset + length <= 0)
                throw new IllegalArgumentException("fragment must intersect this cell and span at most a block");
        }
        public int mask() {
            int result = 0;
            for (int i = Math.max(0, offset); i < Math.min(2, offset + length); i++) result |= 1 << i;
            return result;
        }
    }
}
