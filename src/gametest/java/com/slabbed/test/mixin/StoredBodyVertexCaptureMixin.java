package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.test.VerticalStepClientProbe;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;

/** Observes camera-relative vertices reaching the native world buffer. */
@Mixin(value = BlockModelRenderer.class, priority = 500)
public abstract class StoredBodyVertexCaptureMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V")
    private void capture(BlockRenderView world, BakedModel model, BlockState state, BlockPos pos,
                         MatrixStack matrices, VertexConsumer vertices, boolean cull, Random random,
                         long seed, int overlay, Operation<Void> original) {
        if (!Boolean.getBoolean("slabbed.verticalStepProbe") || !(world instanceof ClientWorld)
                || !VerticalStepClientProbe.watchesWorldVertices(pos)) {
            original.call(world, model, state, pos, matrices, vertices, cull, random, seed, overlay);
            return;
        }
        double[] bounds = {0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double cameraY = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y;
        var captured = new CapturingVertices(vertices, bounds, cameraY);
        original.call(world, model, state, pos, matrices, captured, cull, random, seed, overlay);
        VerticalStepClientProbe.recordWorldVertices((int) bounds[0], bounds[1], bounds[2]);
    }

    private record CapturingVertices(VertexConsumer delegate, double[] bounds, double cameraY) implements VertexConsumer {
        public VertexConsumer vertex(float x, float y, float z) {
            bounds[0]++; bounds[1] = Math.min(bounds[1], y + cameraY); bounds[2] = Math.max(bounds[2], y + cameraY);
            delegate.vertex(x, y, z); return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, a); return this; }
        public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
        public VertexConsumer overlay(int u, int v) { delegate.overlay(u, v); return this; }
        public VertexConsumer light(int u, int v) { delegate.light(u, v); return this; }
        public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    }
}
