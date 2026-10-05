package com.slabbed.test.mixin;

import com.slabbed.test.SlabbedRenderStateEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only: remembers which entity a render state came from, so the matrix audits can name it. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererStateCaptureMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;"
            + "Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void slabbed$rememberEntity(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((SlabbedRenderStateEntity) state).slabbed$setEntity(entity);
    }
}
