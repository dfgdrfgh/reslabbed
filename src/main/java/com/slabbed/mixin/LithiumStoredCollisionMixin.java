package com.slabbed.mixin;

import com.google.common.collect.AbstractIterator;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stored bodies outside their cells require the offset-aware native iterator (LAW.md). */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper", remap = false)
public abstract class LithiumStoredCollisionMixin extends AbstractIterator<VoxelShape> {
    @Shadow @Final private World world;
    @Shadow @Final private Box box;
    @Unique private Entity slabbed$entity;
    @Unique private boolean slabbed$checked;
    @Unique private java.util.Iterator<VoxelShape> slabbed$storedCollisions;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void slabbed$keepShapeContext(CallbackInfo ci, @Local(argsOnly = true) Entity entity) {
        slabbed$entity = entity;
    }

    @Inject(method = "computeNext", at = @At("HEAD"), cancellable = true, remap = false)
    private void slabbed$visitStoredBodies(CallbackInfoReturnable<VoxelShape> cir) {
        if (!slabbed$checked) {
            slabbed$checked = true;
            if (com.slabbed.util.StoredGeometry.hasDisplacement(world, box)) {
                slabbed$storedCollisions = world.getBlockCollisions(slabbed$entity, box).iterator();
            }
        }
        if (slabbed$storedCollisions != null) {
            cir.setReturnValue(slabbed$storedCollisions.hasNext()
                    ? slabbed$storedCollisions.next() : endOfData());
        }
    }
}
