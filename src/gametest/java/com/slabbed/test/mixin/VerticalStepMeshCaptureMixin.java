package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.test.VerticalStepClientProbe;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;

/** Observe actual section-mesh output without substituting the renderer's context. */
@Mixin(value = OffsetBlockStateModel.class, remap = false)
public abstract class VerticalStepMeshCaptureMixin {
    @WrapMethod(method = "emitBlockQuads")
    private void capture(BlockRenderView view, BlockState state, BlockPos pos,
                         Supplier<Random> random, RenderContext context, Operation<Void> original) {
        if (!Boolean.getBoolean("slabbed.verticalStepProbe")) {
            original.call(view, state, pos, random, context);
            return;
        }
        context.pushTransform(quad -> {
            VerticalStepClientProbe.recordMeshedQuad(view, pos, quad, context);
            return true;
        });
        try { original.call(view, state, pos, random, context); }
        finally { context.popTransform(); }
    }
}
