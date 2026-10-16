package com.slabbed.test.mixin;

import com.slabbed.cell.CellBlockEntity;
import com.slabbed.client.CellRenderer;
import com.slabbed.test.CellRenderAudit;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CellRenderer.class)
public abstract class CellRenderCaptureMixin {
    @Redirect(method="render(Lcom/slabbed/cell/CellBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"))
    private VertexConsumer slabbed$recordLighting(VertexConsumerProvider provider,RenderLayer layer) {
        CellRenderAudit.terrainLighting &= layer==RenderLayer.getSolid() || layer==RenderLayer.getCutout()
                || layer==RenderLayer.getCutoutMipped() || layer==RenderLayer.getTranslucent();
        return provider.getBuffer(layer);
    }
    @Redirect(method="render(Lcom/slabbed/cell/CellBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/render/block/BlockRenderManager;renderBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;)V"))
    private void slabbed$capture(BlockRenderManager renderer,BlockState state,BlockPos pos,BlockRenderView world,MatrixStack matrices,VertexConsumer vertices,boolean cull,Random random) {
        boolean watched=pos.getX()==8 && pos.getZ()==8 && pos.getY()>=CellRenderAudit.minimumCell;
        renderer.renderBlock(state,pos,world,matrices,watched?CellRenderAudit.watch(vertices):vertices,cull,random);
    }
}
