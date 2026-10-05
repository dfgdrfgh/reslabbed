package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.RailSlopeProfile;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedRailSeatCarrier;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rail calculations use cell coordinates; the entity and its passengers remain physical (LAW.md).
 *
 * <p>THE SEAT FOLLOWS THE DRAWN RAIL (maintainer ruling, 2026-09-28). A rail's drawn slope is fitted
 * to its neighbours ({@link RailSlopeProfile}), so the seat is not one number per cell: it is the
 * rail's stored seat plus the fitted slope's lift at the cart's place along the rail. A cart
 * therefore rides the slope the player sees instead of dropping half a block at the cell edge, where
 * its body would jam against the next rail's higher support.
 *
 * <p>THE COLLISION PASS RIDES THE TOP. {@code moveOnRail} writes the cart's position four times:
 * the pre-sweep placement (ordinal 0), two ramp re-snaps after the sweep (1, 2) and the final snap
 * onto the rail (3). Vanilla lifts a cart to the high end of a ramp cell before its collision sweep
 * and snaps it onto the slope afterwards; a fitted rail gets the same treatment, so write 0 uses the
 * top of the drawn profile and write 3 the drawn slope at the cart's new place. Each of those binds
 * the seat it applied, so every read after it in the same tick converts in that write's frame.
 *
 * <p>INVARIANT: convert a POSITION, never a DIFFERENCE. A seat that changed without a matching
 * position write would convert one side of a delta and drift the cart by the seat every tick.
 *
 * <p>INVARIANT (LAW.md): a read of stored seats, never a write, and never a re-derivation of any
 * rail's own height. A rail with no modern provenance keeps the cart at its vanilla height.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartPhysicalOffsetMixin extends VehicleEntity implements SlabbedRailSeatCarrier {
    @Unique
    private static final TrackedData<Long> SLABBED_RAIL_DY = DataTracker.registerData(
            AbstractMinecartEntity.class, TrackedDataHandlerRegistry.LONG);
    @Unique
    private static final String SLABBED_RAIL_DY_KEY = "slabbed:rail_dy";
    @Unique
    private int slabbed$logicalRailQueries;

    protected MinecartPhysicalOffsetMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void slabbed$initRailDy(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SLABBED_RAIL_DY, Double.doubleToRawLongBits(0.0d));
    }

    /**
     * A cart placed at a position (the positioning constructor and the 1.21.2+ static factory both
     * end in {@code initPosition}) binds the seat of the rail it lands on and sits physically on it.
     */
    @Inject(method = "initPosition", at = @At("TAIL"))
    private void slabbed$placeAtRailHeight(double x, double y, double z, CallbackInfo ci) {
        World world = getWorld();
        if (world == null || world.isClient) return;
        // Structure generation constructs carts off-thread; rail lookup waits for the server.
        // Defer binding until the existing server-tick hook can safely read the finished chunk.
        if (world instanceof ServerWorld serverWorld && !serverWorld.getServer().isOnThread()) return;
        BlockPos rail = slabbed$railAt(x, y, z);
        if (rail != null && SlabAnchorAttachment.usesFrozenPlacementHeight(world, rail)) {
            double dy = slabbed$drawnSeat(rail, x, z, false);
            if (Double.isFinite(dy)) {
                dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(dy));
                setPosition(x, y + dy, z);
                prevY = y + dy;
            }
        }
    }

    @Override
    public double slabbed$railDy() {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_RAIL_DY));
        return Double.isFinite(dy) ? dy : 0.0d;
    }

    @Unique
    private BlockPos slabbed$railAt(double x, double y, double z) {
        BlockPos cell = BlockPos.ofFloored(x, y, z);
        if (AbstractRailBlock.isRail(getWorld().getBlockState(cell.down()))) return cell.down();
        return AbstractRailBlock.isRail(getWorld().getBlockState(cell)) ? cell : null;
    }

    /**
     * The seat of a cart at ({@code x}, {@code z}) on {@code rail}: the rail's stored seat plus the
     * fitted slope's lift there. With {@code top}, the seat that puts the cart at the TOP of the
     * rail's drawn profile instead — the height the collision sweep runs at. A rail vanilla already
     * draws correctly (no fit) gives its stored seat either way, and a rail without modern
     * provenance gives 0. NaN when the stored seat is not a number.
     */
    @Unique
    private double slabbed$drawnSeat(BlockPos rail, double x, double z, boolean top) {
        World world = getWorld();
        if (!SlabAnchorAttachment.usesFrozenPlacementHeight(world, rail)) return 0.0d;
        BlockState state = world.getBlockState(rail);
        double seat = SlabSupport.getYOffset(world, rail, state);
        if (!Double.isFinite(seat)) return Double.NaN;
        RailSlopeProfile.Profile profile = RailSlopeProfile.resolve(world, rail, state);
        if (profile == null || profile.isVanilla()) return seat;
        if (top) {
            double vanillaTop = Math.max(profile.vanillaNegativeEnd(), profile.vanillaPositiveEnd());
            return seat + (profile.highestEnd() - vanillaTop);
        }
        double t = profile.axis() == Direction.Axis.Z ? z - rail.getZ() : x - rail.getX();
        return seat + profile.liftAt(MathHelper.clamp(t, 0.0d, 1.0d));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void slabbed$bindCurrentRail(CallbackInfo ci) {
        if (getWorld().isClient) return;
        double previousDy = slabbed$railDy();
        BlockPos rail = slabbed$railAt(getX(), getY() - previousDy, getZ());
        if (rail != null) {
            // A legacy rail (no modern provenance) keeps the cart at its vanilla physical height.
            double dy = slabbed$drawnSeat(rail, getX(), getZ(), false);
            if (Double.isFinite(dy) && dy != previousDy) {
                dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(dy));
                setPosition(getX(), getY() + dy - previousDy, getZ());
            }
            return;
        }
        // Re-entry finds the rail's visible band without moving the entity into its logical cell.
        int radius = (int) Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY);
        BlockPos physicalCell = getBlockPos();
        for (int above = -1; above <= radius + 1; above++) {
            BlockPos candidate = physicalCell.up(above);
            BlockState state = getWorld().getBlockState(candidate);
            if (!AbstractRailBlock.isRail(state)) continue;
            double dy = slabbed$drawnSeat(candidate, getX(), getZ(), false);
            if (Double.isFinite(dy) && candidate.equals(slabbed$railAt(getX(), getY() - dy, getZ()))) {
                dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(dy));
                return;
            }
        }
        dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(0.0d));
    }

    /**
     * The rail cell a cart stands on is derived from its LOGICAL height: the physical cart sits lower
     * on a lowered rail, so vanilla's rail lookup reads the entity's Y minus the bound seat. The
     * movement itself is kept logical by {@link DefaultMinecartControllerPhysicalOffsetMixin}.
     */
    @Redirect(method = "getRailOrMinecartPos", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getY()D"))
    private double slabbed$railCoordinateY(AbstractMinecartEntity entity) {
        return entity.getY() - slabbed$railDy();
    }

    @Override
    public boolean slabbed$inLogicalRailQuery() {
        return slabbed$logicalRailQueries > 0;
    }

    @Override
    public void slabbed$enterLogicalRailQuery() {
        slabbed$logicalRailQueries++;
    }

    @Override
    public void slabbed$exitLogicalRailQuery() {
        slabbed$logicalRailQueries--;
    }

    @Override
    public void slabbed$bindAndPlace(double x, double logicalY, double z, boolean top) {
        BlockPos rail = slabbed$railAt(x, logicalY, z);
        double seat = rail == null ? Double.NaN : slabbed$drawnSeat(rail, x, z, top);
        if (Double.isFinite(seat)) {
            dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(seat));
        }
        setPosition(x, logicalY + slabbed$railDy(), z);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void slabbed$writeRailDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = Double.longBitsToDouble(dataTracker.get(SLABBED_RAIL_DY));
        if (Double.isFinite(dy) && dy != 0.0d) nbt.putDouble(SLABBED_RAIL_DY_KEY, dy);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    private void slabbed$readRailDy(NbtCompound nbt, CallbackInfo ci) {
        double dy = nbt.getDouble(SLABBED_RAIL_DY_KEY);
        dataTracker.set(SLABBED_RAIL_DY, Double.doubleToRawLongBits(Double.isFinite(dy) ? dy : 0.0d));
    }
}
