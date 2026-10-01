package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The hung-decoration seat is a synced slot allocated on {@code HangingEntity}, so it sits inside
 * the id range of EVERY hung class, including third-party ones that never forward the declaration
 * (their synched-data hook does not call into Slabbed). Building synched data with a hole in that
 * range throws, and the decoration cannot be created at all. The hole is filled with the unset
 * value just before the build; the seat stays undeclared, so such a class keeps vanilla behaviour.
 * Do not narrow this to Slabbed's own hung classes: the hole appears in the classes it does not know.
 */
@Mixin(SynchedEntityData.Builder.class)
public abstract class SynchedEntityDataBuilderHangSeatMixin {
    @Shadow
    @Final
    private SyncedDataHolder entity;

    @Shadow
    @Final
    private SynchedEntityData.DataItem<?>[] itemsById;

    @Inject(method = "build()Lnet/minecraft/network/syncher/SynchedEntityData;", at = @At("HEAD"))
    private void slabbed$reserveHangSeatSlot(CallbackInfoReturnable<SynchedEntityData> cir) {
        if (this.entity instanceof HangingSeatDyHolder holder) {
            holder.slabbed$reserveUndeclaredHangSeatSlot((SynchedEntityData.Builder) (Object) this, this.itemsById);
        }
    }
}
