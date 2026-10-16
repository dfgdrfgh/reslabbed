package com.slabbed.core;

public final class HalfGridTest {
    public static void main(String[] args) {
        int checked = 0;
        for (int h = -4096; h <= 4096; h++) {
            HalfGrid a = HalfGrid.at(h);
            check(a.cellY() * 2 + a.half() == h, "coordinate round trip");
            check(a.half() >= 0 && a.half() <= 1, "bounded local half");
            checked += 2;
        }
        var bottom = new CellContents.Fragment<>(0, 1, "slab");
        var upper = new CellContents.Fragment<>(1, 2, "stone");
        var cap = new CellContents.Fragment<>(-1, 2, "stone");
        var empty = CellContents.<String>empty();
        var cell = empty.add(bottom).add(upper);
        check(cell.fragments().size() == 2, "two physical occupants");
        check(cell.occupiedMask() == 3, "both halves occupied");
        check(cap.mask() == 1 && upper.mask() == 2, "clipped fragment masks");
        check(cell.remove(1).equals(empty.add(bottom)), "remove one piece");
        check(empty.fragments().isEmpty(), "immutable updates");
        reject(() -> cell.add(new CellContents.Fragment<>(0, 2, "overlap")));
        reject(() -> new CellContents.Fragment<>(2, 1, "distant"));
        reject(() -> new CellContents.Fragment<>(-1, 1, "outside"));
        reject(() -> new CellContents.Fragment<>(0, 3, "oversize"));
        reject(() -> HalfGrid.fromY(Double.NaN));
        for (int i = -1024; i <= 1024; i++) {
            check(HalfGrid.fromY(i * .5).equals(HalfGrid.at(i)), "exact signed half height");
            checked++;
        }
        System.out.println("HALF_GRID_PASS " + (checked + 10) + " assertions");
    }
    static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
    static void reject(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid input accepted");
    }
}
