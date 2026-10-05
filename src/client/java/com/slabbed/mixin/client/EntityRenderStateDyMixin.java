package com.slabbed.mixin.client;

import com.slabbed.client.SlabbedRenderStateDy;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the render-only legacy height on every entity render state (see the two offset mixins). */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateDyMixin implements SlabbedRenderStateDy {
    @Unique
    private double slabbed$renderDy;

    @Override
    public double slabbed$renderDy() {
        return slabbed$renderDy;
    }

    @Override
    public void slabbed$setRenderDy(double dy) {
        slabbed$renderDy = dy;
    }
}
