package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.YOffsetEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Better Block Entities 1.0 to 1.2 (the builds for 1.21.6 through 1.21.8) mesh chests, shulker boxes,
 * bells and decorated pots itself: from a Sodium hook it hands the block's model parts straight to its
 * {@code BlockRenderHelper}, so Slabbed's offset model never sees those quads and the static mesh would
 * sit at grid height while the animated fallback (drawn through the block-entity dispatcher) is lowered.
 * Both helper entry points are wrapped so the emitter they write into is the Y-offset emitter for the
 * block Sodium's render context is currently emitting. Signs do not pass through the helper (BBE emits
 * their model directly, which is Slabbed's offset model already). Absent BBE or Sodium, nothing applies.
 */
@Pseudo
@Mixin(targets = "betterblockentities.util.BlockRenderHelper", remap = false)
public abstract class BetterBlockEntitiesLegacyMeshOffsetMixin {
    @WrapMethod(method = "emitQuads", remap = false)
    private static void slabbed$offsetParts(List<BlockModelPart> parts, QuadEmitter emitter,
                                            Predicate<Direction> cullTest, Operation<Void> original) {
        original.call(parts, slabbed$offsetEmitter(emitter), cullTest);
    }

    // require = 0: Better Block Entities 1.0.0 and 1.1.0 have no decorated-pot helper; only 1.2 does.
    @WrapMethod(method = "emitDecoratedPotQuads", remap = false, require = 0)
    private static void slabbed$offsetDecoratedPot(BlockStateModel model, BlockState state,
                                                   QuadEmitter emitter, DecoratedPotBlockEntity pot,
                                                   Random random, Predicate<Direction> cullTest,
                                                   Operation<Void> original) {
        original.call(model, state, slabbed$offsetEmitter(emitter), pot, random, cullTest);
    }

    private static QuadEmitter slabbed$offsetEmitter(QuadEmitter emitter) {
        if (!(emitter instanceof SodiumBlockEmitterContextAccessor source)
                || !(source.slabbed$getRenderContext() instanceof SodiumLegacyRenderContextAccessor context)) {
            return emitter;
        }
        BlockRenderView view = context.slabbed$getLevel();
        BlockPos pos = context.slabbed$getPos();
        BlockState state = context.slabbed$getState();
        if (view == null || pos == null || state == null) {
            return emitter;
        }
        float dy = (float) ClientDy.dyFor(view, pos, state);
        return dy == 0.0f ? emitter : YOffsetEmitter.wrap(emitter, dy);
    }
}
