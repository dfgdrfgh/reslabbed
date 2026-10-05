package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.SlabbedRenderStateDy;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A frame on a legacy cell (no stored seat) is drawn half a block lower when its support is lowered.
 * From 1.21.2 the renderer works from a render state: the height is decided where the entity is at
 * hand and applied where the renderer computes its position offset. A frame whose backing cell has
 * modern provenance is positioned physically (ItemFramePhysicalOffsetMixin) and gets no render offset.
 */
@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameRenderOffsetMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/ItemFrameEntity;"
            + "Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;F)V", at = @At("TAIL"))
    private void slabbed$decideLegacyOffset(ItemFrameEntity entity, ItemFrameEntityRenderState state,
                                            float tickDelta, CallbackInfo ci) {
        ((SlabbedRenderStateDy) state).slabbed$setRenderDy(slabbed$legacyDy(entity));
    }

    @Inject(method = "getPositionOffset(Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;)"
            + "Lnet/minecraft/util/math/Vec3d;", at = @At("RETURN"), cancellable = true)
    private void slabbed$applyLegacyOffset(ItemFrameEntityRenderState state, CallbackInfoReturnable<Vec3d> cir) {
        double dy = ((SlabbedRenderStateDy) state).slabbed$renderDy();
        if (dy == 0.0d) {
            return;
        }
        Vec3d current = cir.getReturnValue();
        cir.setReturnValue((current == null ? Vec3d.ZERO : current).add(0.0d, dy, 0.0d));
    }

    private static double slabbed$legacyDy(ItemFrameEntity entity) {
        World world = entity.getEntityWorld();
        if (world == null) {
            return 0.0d;
        }
        BlockPos attachedPos = entity.getAttachedBlockPos();
        if (attachedPos == null) {
            return 0.0d;
        }
        BlockPos backing = attachedPos.offset(entity.getHorizontalFacing().getOpposite());
        if (SlabAnchorAttachment.usesFrozenPlacementHeight(world, backing)) {
            return 0.0d;
        }
        BlockState attachedState = world.getBlockState(attachedPos);
        // Donor parity (Fabric 1.21.1): a frame's own cell is air, and air never earns a legacy render
        // drop there; this line's pale-moss check no longer treats air as a thin top layer, so say it here.
        if (attachedState.isAir()) {
            return 0.0d;
        }
        return SlabSupport.shouldOffset(world, attachedPos, attachedState) ? -0.5d : 0.0d;
    }
}
