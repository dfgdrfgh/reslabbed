package com.slabbed.anchor;

import net.minecraft.nbt.NbtCompound;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        Objects.requireNonNull(input, "input");
        NbtCompound attachments = input.getCompound(AttachmentTarget.NBT_ATTACHMENT_KEY);
        Map<BlockPos, Long> exact = new HashMap<>();
        Set<BlockPos> unresolved = new HashSet<>();
        NbtCompound heights = attachments.getCompound("slabbed:placement_dy");
        long[] positions = heights.getLongArray("positions");
        int[] values = heights.getIntArray("dy_sixteenths");
        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = BlockPos.fromLong(positions[i]);
            if (positions.length != values.length || values[i] == SlabAnchorAttachment.UNREPRESENTABLE) {
                unresolved.add(pos);
                exact.remove(pos);
                continue;
            }
            if (unresolved.contains(pos)) continue;
            long bits = Double.doubleToRawLongBits(SlabAnchorAttachment.dequantiseDy(values[i]));
            Long before = exact.putIfAbsent(pos, bits);
            if (before != null && before.longValue() != bits) {
                exact.remove(pos);
                unresolved.add(pos);
            }
        }

        List<String> compounds = List.of("compound_full_block_anchors", "compound_visible_side_lower_slabs",
                "compound_visible_side_upper_slabs", "compound_visible_side_double_slabs",
                "compound_visible_owner_top_slabs");
        Map<String, Set<BlockPos>> markers = new HashMap<>();
        Set<BlockPos> markedPositions = new HashSet<>();
        var names = new java.util.ArrayList<>(compounds);
        names.addAll(List.of("slab_anchors", "frozen_flat", "lowered_slab_carriers"));
        for (String name : names) {
            Set<BlockPos> cells = new HashSet<>();
            for (long packed : attachments.getLongArray("slabbed:" + name)) {
                cells.add(BlockPos.fromLong(packed));
            }
            markers.put(name, cells);
            markedPositions.addAll(cells);
        }
        for (BlockPos pos : markedPositions) {
            if (exact.containsKey(pos) || unresolved.contains(pos)) continue;
            boolean compound = compounds.stream().anyMatch(name -> markers.get(name).contains(pos));
            boolean flat = markers.get("frozen_flat").contains(pos);
            boolean anchor = markers.get("slab_anchors").contains(pos);
            boolean carrier = markers.get("lowered_slab_carriers").contains(pos);
            if (flat && (compound || anchor || carrier)) {
                unresolved.add(pos);
            } else if (compound) {
                exact.put(pos, Double.doubleToRawLongBits(-1.0d));
            } else if (flat) {
                exact.put(pos, 0L);
            } else if (carrier) {
                exact.put(pos, Double.doubleToRawLongBits(-0.5d));
            } else {
                unresolved.add(pos);
            }
        }
        return new ImportResult(exact, unresolved, input);
    }
}
