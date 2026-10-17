package com.slabbed.test.mixin;

import com.slabbed.test.NativeRenderAudit;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Supplier;

/** The outer transform observes coordinates after the production model's inner pose transform. */
@Pseudo
@Mixin(targets="com.slabbed.client.NativeSeatModel",remap=false)
public abstract class NativeModelQuadCaptureMixin {
    @Inject(method="emitBlockQuads",at=@At("HEAD"),remap=false)
    private void slabbed$observeEmittedQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,
                                            RenderContext context,CallbackInfo ci) {
        NativeRenderAudit.renderPath(pos,(BakedModel)(Object)this,"native_emit");
        context.pushTransform(quad->{NativeRenderAudit.quad(pos,quad);return true;});
    }
    @Inject(method="emitBlockQuads",at=@At("RETURN"),remap=false)
    private void slabbed$finishEmittedQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,
                                           RenderContext context,CallbackInfo ci) {
        context.popTransform();
    }
}
