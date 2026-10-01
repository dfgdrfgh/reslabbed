package com.slabbed.compat.sable;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/** Samples the exposed space above a lowered occluder without changing its saved seat (LAW.md). */
public final class SableLightSampling {
    private SableLightSampling() {
    }

    public static void adjust(BlockGetter level, BlockPos.MutableBlockPos sample, double worldY) {
        var state = level.getBlockState(sample);
        if (!state.canOcclude()) {
            return;
        }
        double dy = SlabSupport.getYOffset(level, sample, state);
        if (dy >= 0.0d) {
            return;
        }
        // The outline query already includes Slabbed's saved offset; do not apply dy twice.
        var shape = state.getShape(level, sample);
        if (!shape.isEmpty() && aboveLoweredTop(worldY, sample.getY(), shape.max(Direction.Axis.Y), dy)) {
            // The grid cell still blocks light, but the sample is above its drawn solid.
            // Use the adjacent light cell; genuine interiors keep their original sample.
            sample.move(Direction.UP);
        }
    }

    static boolean aboveLoweredTop(double worldY, int cellY, double shiftedShapeTop, double dy) {
        return dy < 0.0d && worldY >= cellY + shiftedShapeTop;
    }
}
