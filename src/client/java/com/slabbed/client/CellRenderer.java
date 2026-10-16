package com.slabbed.client;

import com.slabbed.cell.CellBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/** Each piece is rendered once with an offset of zero or half a block. */
public final class CellRenderer implements BlockEntityRenderer<CellBlockEntity> {
    public CellRenderer(BlockEntityRendererFactory.Context context) {}
    @Override public void render(CellBlockEntity cell,float tickDelta,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay) {
        var renderer=MinecraftClient.getInstance().getBlockRenderManager();
        for(var part:cell.contents().fragments()) {
            if(part.offset()<0) continue;
            matrices.push(); matrices.translate(0,part.offset()*.5,0);
            renderer.renderBlockAsEntity(part.value(),matrices,vertices,light,overlay);
            matrices.pop();
        }
    }
}
