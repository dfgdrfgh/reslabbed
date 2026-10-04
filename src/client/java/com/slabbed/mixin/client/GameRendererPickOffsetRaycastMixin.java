package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The single ownership rule for Slabbed crosshair targeting on Minecraft 1.21.6 through 1.21.8.
 *
 * <p>The client pick is {@code GameRenderer.updateCrosshairTarget} → {@code findCrosshairTarget},
 * where the block portion is a single {@code camera.raycast(reach, tickDelta, false)} call. This
 * redirect replaces only that block raycast with the offset-aware nearest-hit raycast and preserves
 * the vanilla block-vs-entity merge and reach clamp.
 *
 * <p>The target is named by its Yarn name: this jar is compiled against a version that still has
 * the method, so the remapper carries the name into production, and the development client (Yarn
 * names at runtime, no refmap for raw intermediary strings) resolves it too. The 1.21.9–1.21.11 jar,
 * compiled against 1.21.11 where the method is gone, has to spell the intermediary name instead.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickOffsetRaycastMixin {

    /** Kill switch: {@code -Dslabbed.offsetRaycast=false} restores the vanilla block raycast. */
    private static final boolean SLABBED_OFFSET_RAYCAST_ENABLED =
            Boolean.parseBoolean(System.getProperty("slabbed.offsetRaycast", "true"));

    @Redirect(
            method = "findCrosshairTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"
            )
    )
    private HitResult slabbed$offsetAwarePick(Entity camera, double maxDistance, float tickDelta, boolean includeFluids) {
        if (!SLABBED_OFFSET_RAYCAST_ENABLED) {
            return camera.raycast(maxDistance, tickDelta, includeFluids);
        }
        Vec3d eye = camera.getCameraPosVec(tickDelta);
        Vec3d look = camera.getRotationVec(tickDelta);
        Vec3d end = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        return SlabbedOffsetRaycast.raycast(camera.getWorld(), eye, end, ShapeContext.of(camera));
    }
}
