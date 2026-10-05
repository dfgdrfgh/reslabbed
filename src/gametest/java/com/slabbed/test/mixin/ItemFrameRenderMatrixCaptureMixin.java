package com.slabbed.test.mixin;

import com.slabbed.test.AttachedEntityRenderAudit;
import com.slabbed.test.SlabbedRenderStateEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameRenderMatrixCaptureMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/BlockModelRenderer;render(Lnet/minecraft/client/util/math/MatrixStack$Entry;"
                            + "Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;"
                            + "Lnet/minecraft/client/render/model/BakedModel;FFFII)V"
            )
    )
    private void slabbed$captureItemFrameModelMatrix(ItemFrameEntityRenderState state, MatrixStack matrices,
                                                     VertexConsumerProvider vertexConsumers, int light,
                                                     CallbackInfo ci) {
        AttachedEntityRenderAudit.record(((SlabbedRenderStateEntity) state).slabbed$entity(), matrices);
    }
}
