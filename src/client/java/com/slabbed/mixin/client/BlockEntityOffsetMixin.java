package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets block-entity rendering (signs, banners, heads, beds, etc.) down by
 * 0.5 when the block entity sits above a bottom slab.
 *
 * <p>Block entities are rendered via {@link BlockEntityRenderDispatcher}, NOT
 * through chunk meshing, so the {@code getModelOffset} mixin has no effect
 * on them. The caller ({@code WorldRenderer.renderBlockEntities}) already
 * wraps each render call in {@code push/pop}, so we only need to add an
 * extra translate — no cleanup required.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityOffsetMixin {

    // 1.21.6-1.21.8 render block entities through this dispatcher method (the 1.21.9 submit/render-state
    // pipeline does not exist here); the caller wraps each call in push/pop, so one translate suffices.
    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"))
    private <E extends BlockEntity> void slabbed$offsetBlockEntity(
            E blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
            CallbackInfo ci
    ) {
        if (blockEntity == null) {
            return;
        }
        BlockPos pos = blockEntity.getPos();
        BlockState blockState = blockEntity.getCachedState();

        if (pos == null || blockState == null) {
            return;
        }

        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }

        double yOff = SlabSupport.getVisualYOffset(world, pos, blockState);
        if (yOff != 0.0) {
            matrices.translate(0.0, yOff, 0.0);
        }
    }
}
