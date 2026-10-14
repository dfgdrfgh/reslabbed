package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Model, outline and carpet support share one height authority (LAW.md). */
public final class ClientDy {
    private ClientDy() {}

    public static double dyFor(BlockView world, BlockPos pos, BlockState state) {
        return SlabSupport.getYOffset(world, pos, state);
    }
}
