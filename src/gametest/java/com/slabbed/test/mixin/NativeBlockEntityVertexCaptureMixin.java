package com.slabbed.test.mixin;

import com.slabbed.test.NativeRenderAudit;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class NativeBlockEntityVertexCaptureMixin {
    @ModifyVariable(method="render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at=@At("HEAD"),argsOnly=true)
    private VertexConsumerProvider slabbed$actualNativeVertices(VertexConsumerProvider target,BlockEntity entity,float delta,
                                                               MatrixStack matrices,VertexConsumerProvider original) {
        return NativeRenderAudit.watch(entity,target);
    }
}
