package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.HangingSeatMechanics;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An item frame's remembered seat survives a save and reload.
 *
 * <p>The seat itself — minted once when the frame is hung, carried in entity data, applied to the
 * bounding box — lives in {@code HangingEntityRememberedSeatMixin}, shared with paintings. This
 * class only persists it: {@code AbstractDecorationEntity} declares no save-data hooks, so each hung
 * class writes and reads the number itself. A frame saved before the seat existed has no key and
 * mints from its wall on its first server layout (one-time migration).
 *
 * <p>The entity's real position stays at grid height (the box moves, the position does not);
 * moving it corrupts the derived grid cell and {@code canStayAttached} judges the wrong support.
 */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameWysiwygMixin extends AbstractDecorationEntity implements HangingSeatDyHolder {
    @Unique
    private static final String SLABBED$HANG_DY_KEY = "slabbed:hang_dy";
    // The seat value is registered on the concrete class: on 1.21.5 the decoration base class owns no
    // tracked data and this class defines its own tracker with nothing to chain to.
    @Unique
    private static final TrackedData<Long> SLABBED$HANG_DY =
            DataTracker.registerData(ItemFrameEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique
    private static final long SLABBED$UNSET = Double.doubleToRawLongBits(Double.NaN);

    protected ItemFrameWysiwygMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
        super(type, world);
    }

    @Override
    public double slabbed$hangSeatDy() {
        double dy = Double.longBitsToDouble(this.getDataTracker().get(SLABBED$HANG_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Override
    public boolean slabbed$hasHangSeat() {
        return Double.isFinite(Double.longBitsToDouble(this.getDataTracker().get(SLABBED$HANG_DY)));
    }

    @Override
    public void slabbed$restoreHangSeatDy(double dy) {
        this.getDataTracker().set(SLABBED$HANG_DY, Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }

    @Inject(method = "initDataTracker(Lnet/minecraft/entity/data/DataTracker$Builder;)V", at = @At("TAIL"))
    private void slabbed$defineHangSeat(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SLABBED$HANG_DY, SLABBED$UNSET);
    }

    @Inject(method = "onTrackedDataSet(Lnet/minecraft/entity/data/TrackedData;)V", at = @At("TAIL"))
    private void slabbed$relayoutOnClientSeatSync(TrackedData<?> data, CallbackInfo ci) {
        if (SLABBED$HANG_DY.equals(data) && this.getWorld() != null && this.getWorld().isClient()
                && this.getAttachedBlockPos() != null && this.getHorizontalFacing() != null) {
            this.updateAttachmentPosition();
        }
    }

    // The frame overrides setFacing and canStayAttached without chaining to the base class, so the
    // seat mint and the grid-cell survival shift the base mixin provides are driven from here.
    @Inject(method = "setFacing(Lnet/minecraft/util/math/Direction;)V", at = @At("TAIL"))
    private void slabbed$mintSeatOnDirection(CallbackInfo ci) {
        if (((HangingSeatMechanics) this).slabbed$mintSeatIfMissing()) {
            this.updateAttachmentPosition();
        }
    }

    @Inject(method = "canStayAttached()Z", at = @At("HEAD"))
    private void slabbed$judgeSurvivalOnGridCells(CallbackInfoReturnable<Boolean> cir) {
        ((HangingSeatMechanics) this).slabbed$beginSurvivalOnGrid();
    }

    @Inject(method = "canStayAttached()Z", at = @At("RETURN"))
    private void slabbed$restoreShiftedBoxAfterSurvival(CallbackInfoReturnable<Boolean> cir) {
        ((HangingSeatMechanics) this).slabbed$endSurvivalOnGrid();
    }

    @Inject(method = "writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
    private void slabbed$saveHangSeat(NbtCompound output, CallbackInfo ci) {
        if (this.slabbed$hasHangSeat()) {
            output.putDouble(SLABBED$HANG_DY_KEY, this.slabbed$hangSeatDy());
        }
    }

    @Inject(method = "readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
    private void slabbed$loadHangSeat(NbtCompound input, CallbackInfo ci) {
        double dy = input.getDouble(SLABBED$HANG_DY_KEY, Double.NaN);
        if (Double.isFinite(dy)) {
            this.slabbed$restoreHangSeatDy(dy);
            this.updateAttachmentPosition();
        }
    }
}
