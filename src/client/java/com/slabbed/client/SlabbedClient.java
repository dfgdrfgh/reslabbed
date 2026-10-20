package com.slabbed.client;

import com.slabbed.Slabbed;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class SlabbedClient implements ClientModInitializer {
    @Override public void onInitializeClient() { BlockEntityRendererFactories.register(Slabbed.CELL_ENTITY,CellRenderer::new);
        NativeSeatClient.init();
        CellBreakEffects.init();
        com.slabbed.client.debug.DiagnosticClient.init();
        SupplementalCellClient.init();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->CellRenderSync.unload(chunk.getPos()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->CellRenderSync.reset());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context->CellRenderSync.beginFrame());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.register(context->CellRenderer.frustum=context.frustum());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(CellRenderSync::renderPending);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context->CellRenderSync.endFrame()); }
}
