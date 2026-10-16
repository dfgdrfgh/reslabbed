package com.slabbed.cell;

import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;

/** Block behavior adapters are explicit; unsupported mechanics stay on the vanilla path. */
public final class PieceRules {
    private PieceRules() {}
    public static boolean supports(BlockState state) {
        return state.getBlock() instanceof SlabBlock || state.isFullCube(EmptyBlockView.INSTANCE,BlockPos.ORIGIN);
    }
    public static boolean accepts(BlockState state) {
        if(state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        var block=state.getBlock();
        if(block instanceof SlabBlock) return state.get(SlabBlock.TYPE)!=SlabType.DOUBLE;
        if(block instanceof TorchBlock || block instanceof WallTorchBlock || block instanceof LanternBlock
                || block instanceof ChainBlock || block instanceof FlowerPotBlock || block instanceof CarpetBlock) return true;
        if(block instanceof FallingBlock || block instanceof PistonBlock || block instanceof TntBlock
                || block instanceof RedstoneBlock || block instanceof ObserverBlock || block instanceof CommandBlock) return false;
        // Stateful and ticking blocks require their own adapter rather than silently losing gameplay.
        return block.getClass()==Block.class || block instanceof PillarBlock || block==Blocks.GLASS
                || block instanceof StainedGlassBlock || block instanceof GlazedTerracottaBlock
                || block instanceof LeavesBlock;
    }
}
