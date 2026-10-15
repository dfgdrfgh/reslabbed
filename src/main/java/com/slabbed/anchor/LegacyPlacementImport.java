package com.slabbed.anchor;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.Set;

/** Plans explicit saved-data conversion separately from live geometry reads (LAW.md). */
public final class LegacyPlacementImport {
    private LegacyPlacementImport() {}

    public record ImportResult(Map<BlockPos, Long> exactHeights, Set<BlockPos> unresolved,
                               NbtCompound preservedInput) {
        public ImportResult {
            exactHeights = Map.copyOf(exactHeights);
            unresolved = Set.copyOf(unresolved);
            preservedInput = preservedInput.copy();
        }

        @Override public NbtCompound preservedInput() { return preservedInput.copy(); }
    }

    public static ImportResult plan(NbtCompound input) {
        return new ImportResult(Map.of(), Set.of(), input);
    }
}
