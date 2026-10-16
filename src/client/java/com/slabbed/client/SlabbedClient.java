package com.slabbed.client;

import com.slabbed.Slabbed;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class SlabbedClient implements ClientModInitializer {
    @Override public void onInitializeClient() { BlockEntityRendererFactories.register(Slabbed.CELL_ENTITY,CellRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.register(context->CellRenderer.frustum=context.frustum());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context->CellRenderSync.endFrame()); }
}
