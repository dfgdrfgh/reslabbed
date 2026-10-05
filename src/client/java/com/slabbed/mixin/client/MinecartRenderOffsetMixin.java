package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.SlabbedRenderStateDy;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.entity.AbstractMinecartEntityRenderer;
import net.minecraft.client.render.entity.state.MinecartEntityRenderState;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A cart on a legacy rail (no stored seat) is drawn half a block lower when the rail is lowered. A
 * cart on a rail with modern provenance is positioned physically (MinecartPhysicalOffsetMixin) and
 * gets no render offset. Decided at render-state update, applied in the renderer's position offset.
 */
@Mixin(AbstractMinecartEntityRenderer.class)
public abstract class MinecartRenderOffsetMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;"
            + "Lnet/minecraft/client/render/entity/state/MinecartEntityRenderState;F)V", at = @At("TAIL"))
    private void slabbed$decideLegacyOffset(AbstractMinecartEntity entity, MinecartEntityRenderState state,
                                            float tickDelta, CallbackInfo ci) {
        double dy = 0.0d;
        World world = entity.getEntityWorld();
        if (world != null) {
            BlockPos pos = entity.getBlockPos();
            BlockState blockState = world.getBlockState(pos);
            if (blockState.getBlock() instanceof AbstractRailBlock
                    && !SlabAnchorAttachment.usesFrozenPlacementHeight(world, pos)
                    && SlabSupport.shouldOffset(world, pos, blockState)) {
                dy = -0.5d;
            }
        }
        ((SlabbedRenderStateDy) state).slabbed$setRenderDy(dy);
    }

    @Inject(method = "getPositionOffset(Lnet/minecraft/client/render/entity/state/MinecartEntityRenderState;)"
            + "Lnet/minecraft/util/math/Vec3d;", at = @At("RETURN"), cancellable = true)
    private void slabbed$applyLegacyOffset(MinecartEntityRenderState state, CallbackInfoReturnable<Vec3d> cir) {
        double dy = ((SlabbedRenderStateDy) state).slabbed$renderDy();
        if (dy == 0.0d) {
            return;
        }
        Vec3d current = cir.getReturnValue();
        cir.setReturnValue((current == null ? Vec3d.ZERO : current).add(0.0d, dy, 0.0d));
    }
}
