package com.slabbed.client;

import com.slabbed.cell.CellBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.random.Random;

/** Pieces render once, with bounded offsets and the world's original model colors. */
public final class CellRenderer implements BlockEntityRenderer<CellBlockEntity> {
    private final Random random=Random.create();
    static net.minecraft.client.render.Frustum frustum;
    @Override public int getRenderDistance() { return Math.max(512,MinecraftClient.getInstance().options.getViewDistance().getValue()*32); }
    @Override public boolean rendersOutsideBoundingBox(CellBlockEntity cell) {
        var bounds=cell.renderBounds();return bounds!=null && bounds.maxY>cell.getPos().getY()+1.01;
    }
    public CellRenderer(BlockEntityRendererFactory.Context context) {}
    @Override public void render(CellBlockEntity cell,float tickDelta,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay) {
        CellRenderSync.registered(cell);
        if(cell.getWorld()==null || cell.renderBounds()==null || (frustum!=null && !frustum.isVisible(cell.renderBounds())))return;
        if(!CellRenderSync.draw(cell))return;
        var renderer=MinecraftClient.getInstance().getBlockRenderManager();
        for(var part:cell.contents().fragments()) {
            if(part.offset()<0) continue;
            matrices.push();
            try {
                matrices.translate(0,part.offset()*.5,0);
                renderer.renderBlock(part.value(),cell.getPos(),cell.getWorld(),matrices,
                        vertices.getBuffer(RenderLayers.getBlockLayer(part.value())),false,random);
            } finally { matrices.pop(); }
        }
    }
}
