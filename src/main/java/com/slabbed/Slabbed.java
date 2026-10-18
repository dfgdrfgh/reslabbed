package com.slabbed;

import com.slabbed.cell.CellBlock;
import com.slabbed.cell.CellBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class Slabbed implements ModInitializer {
    public static final CellBlock CELL = new CellBlock(AbstractBlock.Settings.create().strength(2).nonOpaque().dynamicBounds()
            .luminance(state -> state.get(CellBlock.LIGHT)));
    public static final BlockEntityType<CellBlockEntity> CELL_ENTITY = FabricBlockEntityTypeBuilder.create(CellBlockEntity::new, CELL).build();
    @Override public void onInitialize() {
        Registry.register(Registries.BLOCK, Identifier.of("slabbed", "cell"), CELL);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of("slabbed", "cell"), CELL_ENTITY);
        com.slabbed.nativeblock.NativeSeats.init();
        com.slabbed.cell.SupplementalCells.init();
        com.slabbed.cell.CellInteractions.register();
        com.slabbed.cell.PieceTarget.register();
    }
}
