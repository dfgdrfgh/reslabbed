package com.slabbed.core;

/** Integer half-block addresses keep physical storage independent of stack depth. */
public record HalfGrid(int cellY, int half) {
    public HalfGrid {
        if (half < 0 || half > 1) throw new IllegalArgumentException("half must be zero or one");
    }
    public static HalfGrid at(int halfY) {
        return new HalfGrid(Math.floorDiv(halfY, 2), Math.floorMod(halfY, 2));
    }
    public static HalfGrid fromY(double y) {
        double halfY = Math.rint(y * 2);
        if (!Double.isFinite(y) || Math.abs(halfY - y * 2) > 1e-6
                || halfY < Integer.MIN_VALUE || halfY > Integer.MAX_VALUE)
            throw new IllegalArgumentException("height must be an integer half block");
        return at((int) halfY);
    }
}
