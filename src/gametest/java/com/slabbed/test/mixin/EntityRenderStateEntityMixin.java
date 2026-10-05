package com.slabbed.test.mixin;

import com.slabbed.test.SlabbedRenderStateEntity;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateEntityMixin implements SlabbedRenderStateEntity {
    @Unique
    private Entity slabbed$entity;

    @Override
    public Entity slabbed$entity() {
        return slabbed$entity;
    }

    @Override
    public void slabbed$setEntity(Entity entity) {
        slabbed$entity = entity;
    }
}
