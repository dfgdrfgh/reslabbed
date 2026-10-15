package com.slabbed.anchor;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Geometry reads only authored positions; absence retains native geometry (LAW.md). */
public final class PlacementGeometry {
    private PlacementGeometry() {}

    public static SlabAnchorAttachment.PlacementDyFact fact(BlockView view, BlockPos pos) {
        if (pos == null || SlabSupport.isUnsafeGeometryView(view)) {
            return SlabAnchorAttachment.PlacementDyFact.absent();
        }
        return SlabAnchorAttachment.effectivePlacementDyFact(view, pos);
    }

    public static double offsetY(BlockView view, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) return 0.0d;
        var fact = fact(view, pos);
        double value = fact.valueOrNaN();
        return fact.present() && Double.isFinite(value) ? value : 0.0d;
    }
}
