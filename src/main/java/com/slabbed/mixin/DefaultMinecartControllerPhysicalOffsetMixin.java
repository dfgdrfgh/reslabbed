package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.util.SlabbedRailSeatCarrier;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.DefaultMinecartController;
import net.minecraft.entity.vehicle.MinecartController;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The movement half of the minecart seat (see {@link MinecartPhysicalOffsetMixin}): from 1.21.2 the
 * rail movement, the rail snaps and the position writes live on the default minecart controller.
 * Rail coordinates stay logical (the entity's Y minus the bound seat); every position write puts the
 * cart physically on the rail's drawn slope. The four writes keep the meaning they had on 1.21.1:
 * write 0 is the placement the collision sweep runs at (top of the drawn profile), writes 1 and 2
 * are the ramp re-snaps after the sweep in that frame, write 3 is the final snap onto the slope.
 */
@Mixin(DefaultMinecartController.class)
public abstract class DefaultMinecartControllerPhysicalOffsetMixin extends MinecartController {
    @Unique
    private static final String SET_POS = "Lnet/minecraft/entity/vehicle/DefaultMinecartController;setPos(DDD)V";

    protected DefaultMinecartControllerPhysicalOffsetMixin(AbstractMinecartEntity minecart) {
        super(minecart);
    }

    @Unique
    private SlabbedRailSeatCarrier slabbed$carrier() {
        return (SlabbedRailSeatCarrier) minecart;
    }

    @Redirect(method = "moveOnRail", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getY()D"))
    private double slabbed$railCoordinateY(AbstractMinecartEntity entity) {
        return entity.getY() - slabbed$carrier().slabbed$railDy();
    }

    @Redirect(method = "moveOnRail", at = @At(value = "INVOKE", target = SET_POS, ordinal = 0))
    private void slabbed$placeAtTheDrawnTopForCollision(DefaultMinecartController controller,
                                                        double x, double y, double z) {
        slabbed$carrier().slabbed$bindAndPlace(x, y, z, true);
    }

    @Redirect(method = "moveOnRail", at = @At(value = "INVOKE", target = SET_POS, ordinal = 1))
    private void slabbed$physicalRailPositionAfterSweep(DefaultMinecartController controller,
                                                       double x, double y, double z) {
        controller.setPos(x, y + slabbed$carrier().slabbed$railDy(), z);
    }

    @Redirect(method = "moveOnRail", at = @At(value = "INVOKE", target = SET_POS, ordinal = 2))
    private void slabbed$physicalRailPositionAfterSweepOther(DefaultMinecartController controller,
                                                            double x, double y, double z) {
        controller.setPos(x, y + slabbed$carrier().slabbed$railDy(), z);
    }

    @Redirect(method = "moveOnRail", at = @At(value = "INVOKE", target = SET_POS, ordinal = 3))
    private void slabbed$snapOntoTheDrawnSlope(DefaultMinecartController controller,
                                               double x, double y, double z) {
        slabbed$carrier().slabbed$bindAndPlace(x, y, z, false);
    }

    @WrapMethod(method = "moveOnRail")
    private void slabbed$railMovement(ServerWorld world, Operation<Void> original) {
        slabbed$carrier().slabbed$enterLogicalRailQuery();
        try {
            original.call(world);
        } finally {
            slabbed$carrier().slabbed$exitLogicalRailQuery();
        }
    }

    @WrapMethod(method = "snapPositionToRail")
    private Vec3d slabbed$physicalRailSnap(double x, double y, double z, Operation<Vec3d> original) {
        SlabbedRailSeatCarrier carrier = slabbed$carrier();
        if (carrier.slabbed$inLogicalRailQuery()) {
            return original.call(x, y, z);
        }
        double dy = carrier.slabbed$railDy();
        carrier.slabbed$enterLogicalRailQuery();
        try {
            Vec3d snapped = original.call(x, y - dy, z);
            return snapped == null ? null : snapped.add(0.0d, dy, 0.0d);
        } finally {
            carrier.slabbed$exitLogicalRailQuery();
        }
    }

    /** The offset snap (unnamed in the mappings; {@code snapPositionToRailWithOffset} on 1.21.1). */
    @WrapMethod(method = "method_61619")
    private Vec3d slabbed$physicalRailOffsetSnap(double x, double y, double z, double offset,
                                               Operation<Vec3d> original) {
        SlabbedRailSeatCarrier carrier = slabbed$carrier();
        if (carrier.slabbed$inLogicalRailQuery()) {
            return original.call(x, y, z, offset);
        }
        double dy = carrier.slabbed$railDy();
        carrier.slabbed$enterLogicalRailQuery();
        try {
            Vec3d snapped = original.call(x, y - dy, z, offset);
            return snapped == null ? null : snapped.add(0.0d, dy, 0.0d);
        } finally {
            carrier.slabbed$exitLogicalRailQuery();
        }
    }
}
