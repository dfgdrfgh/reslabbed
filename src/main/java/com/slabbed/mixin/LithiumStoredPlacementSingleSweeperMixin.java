package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.placement.PlacementCollisionShapes;
import com.slabbed.placement.StoredCollisionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Iterator;

/**
 * Supplies stored collision bodies on Lithium 0.19 and 0.20 (Minecraft 1.21.9 and 1.21.10), whose
 * one concrete sweeper owns {@code computeNext} itself. The counterpart for the split layout of
 * Lithium 0.21 is {@link LithiumStoredPlacementShapeMixin}; {@link SlabbedMixinConfigPlugin}
 * admits exactly one of the two.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper", remap = false)
public abstract class LithiumStoredPlacementSingleSweeperMixin {
    @Unique private Iterator<PlacementCollisionShapes.Contact> slabbed$additional;

    @WrapOperation(method = "computeNext", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/lithium/common/entity/movement/ChunkAwareBlockCollisionSweeper;endOfData()Ljava/lang/Object;", remap = false))
    private Object slabbed$finishAfterStoredOwners(@Coerce Object iterator, Operation<Object> original) {
        if (slabbed$additional == null) {
            slabbed$additional = ((StoredCollisionSource) this).slabbed$storedContacts().iterator();
        }
        return slabbed$additional.hasNext() ? slabbed$additional.next().shape() : original.call(iterator);
    }
}
