package com.slabbed.nativeblock;

import com.slabbed.cell.SupplementalCells;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Guarded geometry views expose existing placement facts without consulting unrelated client data (LAW.md). */
public interface StoredGeometryView extends BlockView {
    NativeSeats.Seat slabbed$nativeSeat(BlockPos pos);
    SupplementalCells.StoredCell slabbed$supplementalCell(BlockPos pos);
}
