package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.HangingSeatMechanics;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hung decoration REMEMBERS the height of the face it was hung on (LAW 1; maintainer ruling,
 * 2026-09-13: where it is placed is where it stays, for everything hung on a wall).
 *
 * <p>The seat is MINTED ONCE — when the decoration is first told which way it faces on the server,
 * with its support loaded, which is the moment the item hangs it — from the support's stored height, and is
 * then carried in synced entity data and in save data. Every later layout reads the remembered
 * number verbatim; nothing here re-reads the support. Rebuilding the wall behind a hung frame at a
 * different height is a change to the WALL, not to the frame (LAW 1 §3).
 *
 * <p>The entity's REAL position deliberately stays at grid height: moving it corrupts the derived
 * grid cell and {@code canStayAttached} judges the wrong support. Only the bounding box shifts here,
 * and the render layer applies the same remembered seat to the drawing. Vanilla's {@code
 * updateAttachmentPosition} sets the position from the unshifted box first, so the shift is applied
 * at its TAIL and fires exactly once per layout for frames (whose override calls into this one) and
 * paintings alike.
 *
 * <p>A decoration saved before this seat existed carries no number; after its data is restored,
 * available chunks allow a mint from its wall, otherwise a later tick retries. That is a one-time
 * migration, not a re-derivation.
 */
@Mixin(AbstractDecorationEntity.class)
public abstract class HangingEntityRememberedSeatMixin extends BlockAttachedEntity implements HangingSeatMechanics {

    // 1.21.5 shape of the remembered seat. The seat VALUE (a synced long) lives in the two concrete
    // classes (ItemFrameWysiwygMixin, PaintingRememberedSeatMixin): on this version the base class
    // declares no tracked data of its own, and frames and paintings each define their own trackers
    // with nothing in between to chain to. The MECHANICS live here, where every decoration shares
    // them: the seat is minted when the facing is set (setFacing here; the frame's own override
    // calls in through HangingSeatMechanics) — never on an earlier layout, because the base class
    // starts with a default facing and a painting's variant lays the box out before the real facing
    // arrives — the box is offset by it, survival is judged on the grid cell, and a loaded entity
    // without a saved seat mints one once its chunk is present, never waiting on a chunk (cross-port law).
    @Unique
    private boolean slabbed$readingData;

    protected HangingEntityRememberedSeatMixin(EntityType<? extends BlockAttachedEntity> type, World world) {
        super(type, world);
    }

    @Shadow
    public abstract Direction getHorizontalFacing();

    @Shadow
    protected abstract void updateAttachmentPosition();

    @Unique
    private HangingSeatDyHolder slabbed$seat() {
        return (HangingSeatDyHolder) this;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        this.slabbed$readingData = true;
        try {
            super.readNbt(nbt);
        } finally {
            this.slabbed$readingData = false;
        }
        BlockPos entityPos = this.getBlockPos();
        if (this.getWorld() instanceof ServerWorld world && world.getServer().isOnThread()
                && world.getChunkManager().getWorldChunk(entityPos.getX() >> 4, entityPos.getZ() >> 4) != null
                && this.slabbed$tryMintHangSeat()) {
            this.updateAttachmentPosition();
        }
    }

    @Override
    public void tick() {
        if (this.slabbed$tryMintHangSeat()) {
            this.updateAttachmentPosition();
        }
        super.tick();
    }

    @Override
    public boolean slabbed$mintSeatIfMissing() {
        return this.slabbed$tryMintHangSeat();
    }

    @Inject(method = "setFacing(Lnet/minecraft/util/math/Direction;)V", at = @At("TAIL"))
    private void slabbed$mintSeatOnDirection(CallbackInfo ci) {
        if (this.slabbed$tryMintHangSeat()) {
            this.updateAttachmentPosition();
        }
    }

    @Unique
    private boolean slabbed$tryMintHangSeat() {
        if (this.slabbed$readingData || this.slabbed$seat().slabbed$hasHangSeat()
                || this.getAttachedBlockPos() == null || this.getHorizontalFacing() == null) {
            return false;
        }
        if (!(this.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread()) {
            return false;
        }
        BlockPos attachedPos = this.getAttachedBlockPos();
        if (world.getChunkManager().getWorldChunk(attachedPos.getX() >> 4, attachedPos.getZ() >> 4) == null) {
            return false;
        }
        BlockPos supportPos = attachedPos.offset(this.getHorizontalFacing().getOpposite());
        WorldChunk supportChunk = world.getChunkManager().getWorldChunk(supportPos.getX() >> 4, supportPos.getZ() >> 4);
        if (supportChunk == null) {
            return false;
        }
        BlockState support = supportChunk.getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(world, supportPos, support);
        this.slabbed$seat().slabbed$restoreHangSeatDy(Double.isFinite(dy) ? dy : 0.0d);
        return true;
    }

    @Inject(method = "updateAttachmentPosition()V", at = @At("TAIL"))
    private void slabbed$hangBoxOnRememberedSeat(CallbackInfo ci) {
        double dy = this.slabbed$seat().slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            this.setBoundingBox(this.getBoundingBox().offset(0.0d, dy, 0.0d));
        }
    }

    @Unique
    private Box slabbed$shiftedBoxDuringSurvival;

    @Override
    public void slabbed$beginSurvivalOnGrid() {
        double dy = this.slabbed$seat().slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            slabbed$shiftedBoxDuringSurvival = this.getBoundingBox();
            this.setBoundingBox(slabbed$shiftedBoxDuringSurvival.offset(0.0d, -dy, 0.0d));
        }
    }

    @Override
    public void slabbed$endSurvivalOnGrid() {
        if (slabbed$shiftedBoxDuringSurvival != null) {
            this.setBoundingBox(slabbed$shiftedBoxDuringSurvival);
            slabbed$shiftedBoxDuringSurvival = null;
        }
    }

    // Paintings reach the base-class survival check; item frames override it without chaining and
    // call the same pair from their own hooks (ItemFrameWysiwygMixin).
    @Inject(method = "canStayAttached()Z", at = @At("HEAD"))
    private void slabbed$judgeSurvivalOnGridCells(CallbackInfoReturnable<Boolean> cir) {
        this.slabbed$beginSurvivalOnGrid();
    }

    @Inject(method = "canStayAttached()Z", at = @At("RETURN"))
    private void slabbed$restoreShiftedBoxAfterSurvival(CallbackInfoReturnable<Boolean> cir) {
        this.slabbed$endSurvivalOnGrid();
    }
}
