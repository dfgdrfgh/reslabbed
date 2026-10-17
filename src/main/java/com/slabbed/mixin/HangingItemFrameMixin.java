package com.slabbed.mixin;

import com.slabbed.hanging.HangingPlacement;
import com.slabbed.hanging.HangingSeat;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Frame bounds and position use one synchronized saved mount, including glow-frame subclasses. */
@Mixin(ItemFrameEntity.class)
public abstract class HangingItemFrameMixin extends AbstractDecorationEntity implements HangingSeat {
    @Unique private static final TrackedData<Long> SLABBED$SEAT=DataTracker.registerData(ItemFrameEntity.class,TrackedDataHandlerRegistry.LONG);
    @Unique private static final String SLABBED$KEY="slabbed:frame_dy";
    @Shadow private boolean fixed;
    protected HangingItemFrameMixin(EntityType<? extends AbstractDecorationEntity> type,World world) {super(type,world);}
    @Override public double slabbed$hangingOffset() {return HangingPlacement.valid(Double.longBitsToDouble(dataTracker.get(SLABBED$SEAT)));}
    @Inject(method="initDataTracker",at=@At("TAIL"))
    private void slabbed$initSeat(DataTracker.Builder builder,CallbackInfo ci) {builder.add(SLABBED$SEAT,Double.doubleToRawLongBits(Double.NaN));}
    @Inject(method="calculateBoundingBox",at=@At("RETURN"),cancellable=true)
    private void slabbed$seatBox(BlockPos attached,Direction facing,CallbackInfoReturnable<Box> cir) {
        double offset=Double.longBitsToDouble(dataTracker.get(SLABBED$SEAT));
        if(!Double.isFinite(offset) && !getWorld().isClient) {
            offset=HangingPlacement.initial(getWorld(),attached,facing);
            dataTracker.set(SLABBED$SEAT,Double.doubleToRawLongBits(offset));
        }
        if(Double.isFinite(offset) && offset!=0)cir.setReturnValue(cir.getReturnValue().offset(0,HangingPlacement.valid(offset),0));
    }
    @Inject(method="canStayAttached",at=@At("HEAD"),cancellable=true)
    private void slabbed$physicalSupport(CallbackInfoReturnable<Boolean> cir) {
        if(!fixed && slabbed$hangingOffset()!=0)cir.setReturnValue(HangingPlacement.supported(this));
    }
    @Inject(method="onTrackedDataSet",at=@At("TAIL"))
    private void slabbed$syncSeat(TrackedData<?> data,CallbackInfo ci) {
        if(SLABBED$SEAT.equals(data) && getWorld().isClient)updateAttachmentPosition();
    }
    @Inject(method="writeCustomDataToNbt",at=@At("TAIL"))
    private void slabbed$saveSeat(NbtCompound nbt,CallbackInfo ci) {nbt.putDouble(SLABBED$KEY,slabbed$hangingOffset());}
    @Inject(method="readCustomDataFromNbt",at=@At("HEAD"))
    private void slabbed$loadSeat(NbtCompound nbt,CallbackInfo ci) {
        double offset=nbt.contains(SLABBED$KEY,99)?HangingPlacement.valid(nbt.getDouble(SLABBED$KEY)):slabbed$hangingOffset();
        dataTracker.set(SLABBED$SEAT,Double.doubleToRawLongBits(offset));
    }
    /** Vanilla frame tracking positions are attachment-cell positions; the tracker carries its pose. */
    @Override public Vec3d getSyncedPos() {return Vec3d.of(attachedBlockPos);}
}
