package com.slabbed.client;

import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import java.util.Map;

/**
 * Wraps every baked block-state model so lowered blocks are drawn at their stored height, and
 * registers the standalone chain-bridge models. NeoForge's model events replace the Fabric
 * model-loading plugin of the Fabric lines; the wrapper is the same design.
 */
public final class SlabbedModelLoadingPlugin {
    private SlabbedModelLoadingPlugin() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ChainCeilingGeometry::registerStandalone);
        modEventBus.addListener(SlabbedModelLoadingPlugin::modifyBakingResult);
    }

    private static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        models.replaceAll((state, model) -> wrap(model));
    }

    static BlockStateModel wrap(BlockStateModel model) {
        if (model == null || model instanceof OffsetBlockStateModel) {
            return model;
        }
        return new OffsetBlockStateModel(model);
    }
}
