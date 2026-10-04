package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A painting's remembered seat survives a save and reload (maintainer ruling, 2026-09-13: paintings
 * hang on the drawn face like item frames, and remember it).
 *
 * <p>The seat lives in {@code HangingEntityRememberedSeatMixin}; this class only persists it, the
 * same way {@code ItemFrameWysiwygMixin} does for frames. Keep the two hooks identical.
 */
@Mixin(PaintingEntity.class)
public abstract class PaintingRememberedSeatMixin extends AbstractDecorationEntity implements HangingSeatDyHolder {
    @Unique
    private static final String SLABBED$HANG_DY_KEY = "slabbed:hang_dy";
    // Registered on the concrete class for the same reason as the item frame's (see ItemFrameWysiwygMixin).
    @Unique
    private static final TrackedData<Long> SLABBED$HANG_DY =
            DataTracker.registerData(PaintingEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique
    private static final long SLABBED$UNSET = Double.doubleToRawLongBits(Double.NaN);

    protected PaintingRememberedSeatMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
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
