package com.slabbed.test.mixin;

import com.slabbed.cell.CellBlockEntity;
import com.slabbed.client.CellRenderer;
import com.slabbed.test.CellRenderAudit;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CellRenderer.class)
public abstract class CellRenderCaptureMixin {
    @Redirect(method="render(Lcom/slabbed/cell/CellBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/render/block/BlockRenderManager;renderBlockAsEntity(Lnet/minecraft/block/BlockState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"))
    private void slabbed$capture(BlockRenderManager renderer,BlockState state,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay,
                                CellBlockEntity cell,float tick,MatrixStack originalMatrices,VertexConsumerProvider originalVertices,int originalLight,int originalOverlay) {
        if(cell.getPos().getX()==8 && cell.getPos().getZ()==8 && cell.getPos().getY()>=CellRenderAudit.minimumCell)
            renderer.renderBlockAsEntity(state,matrices,layer->CellRenderAudit.watch(vertices.getBuffer(layer)),light,overlay);
        else renderer.renderBlockAsEntity(state,matrices,vertices,light,overlay);
    }
}
