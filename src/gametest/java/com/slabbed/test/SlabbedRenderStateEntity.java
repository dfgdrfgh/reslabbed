package com.slabbed.test;

import net.minecraft.entity.Entity;

/** Test-only: the entity a render state was last updated from, for the render-matrix audits. */
public interface SlabbedRenderStateEntity {
    Entity slabbed$entity();

    void slabbed$setEntity(Entity entity);
}
