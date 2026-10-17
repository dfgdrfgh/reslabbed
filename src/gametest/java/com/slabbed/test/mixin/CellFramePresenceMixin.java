package com.slabbed.test.mixin;

import com.slabbed.cell.CellBlockEntity;
import com.slabbed.client.CellRenderer;
import com.slabbed.test.CellClientLifecycleRegression;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CellRenderer.class)
public abstract class CellFramePresenceMixin {
    @Shadow static Frustum frustum;
    @Inject(method="render(Lcom/slabbed/cell/CellBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",at=@At("HEAD"))
    private void slabbed$observeEntry(CellBlockEntity cell,float delta,MatrixStack matrices,VertexConsumerProvider vertices,
                                      int light,int overlay,CallbackInfo ci) {
        CellClientLifecycleRegression.renderer(cell,cell.renderBounds()!=null && (frustum==null || frustum.isVisible(cell.renderBounds())));
    }
}
