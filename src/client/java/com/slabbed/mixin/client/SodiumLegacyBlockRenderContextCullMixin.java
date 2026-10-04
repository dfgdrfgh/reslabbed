package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Preserves exposed height-step faces under Sodium 0.6 and 0.7 (Minecraft 1.21.9 and 1.21.10),
 * where the block render context lives in {@code render.frapi.render} and exposes its face test
 * as {@code isFaceCulled}. Sodium 0.8 moved the class and the test (see
 * {@link SodiumBlockRenderContextCullMixin}); each twin is {@code @Pseudo} and skips itself when
 * its class is absent, so the two can never apply together.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext", remap = false)
public abstract class SodiumLegacyBlockRenderContextCullMixin {
    @Shadow(remap = false)
    protected BlockRenderView level;

    @Shadow(remap = false)
    protected BlockPos pos;

    @Shadow(remap = false)
    protected BlockState state;

    // Sodium 0.7 answers "is this face culled" (true = hidden); the 0.8 twin answers
    // "should this side draw". Reuse the Indigo predicate so only exposed height-step faces
    // become visible.
    @Inject(method = "isFaceCulled", at = @At("RETURN"), cancellable = true, remap = false)
    private void slabbed$keepStepFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                && SlabSupport.isSlabHeightStepFace(level, pos, state, direction)) {
            cir.setReturnValue(false);
        }
    }
}
