package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.render.model.BakedModel;

public final class SlabbedModelLoadingPlugin {
    private SlabbedModelLoadingPlugin() {
    }

    public static void init() {
        Slabbed.LOGGER.info("[Slabbed] ModelLoadingPlugin init: registering baked model wrapper");
        ModelLoadingPlugin.register(plugin -> {
            // Force-load and bake the elongated chain model used as alternate geometry for a
            // vertical chain hanging directly under a slab ceiling support. Retrieved at render
            // time via BakedModelManager#getModel(Identifier) (Fabric-injected overload).
            plugin.addModels(ChainCeilingGeometry.MODEL_ID);

            // 1.21.4: block-state models have their own after-bake event; the top-level model of every
            // block state (multipart and weighted included) is wrapped once.
            plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE,
                    (model, context) -> wrapModel(model));
        });
    }

    static BakedModel wrapModel(BakedModel model) {
        if (model == null || model instanceof OffsetBlockStateModel) {
            return model;
        }
        return new OffsetBlockStateModel(model);
    }
}
