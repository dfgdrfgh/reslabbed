package com.slabbed.test.mixin;

import com.slabbed.test.AttachedEntityRenderAudit;
import com.slabbed.test.SlabbedRenderStateEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.AbstractMinecartEntityRenderer;
import net.minecraft.client.render.entity.state.MinecartEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecartEntityRenderer.class)
public abstract class MinecartRenderMatrixCaptureMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/MinecartEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/MinecartEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"
            )
    )
    private void slabbed$captureMinecartModelMatrix(MinecartEntityRenderState state, MatrixStack matrices,
                                                    VertexConsumerProvider vertexConsumers, int light,
                                                    CallbackInfo ci) {
        AttachedEntityRenderAudit.record(((SlabbedRenderStateEntity) state).slabbed$entity(), matrices);
    }
}
