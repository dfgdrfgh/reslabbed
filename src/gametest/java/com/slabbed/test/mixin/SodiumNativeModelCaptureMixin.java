package com.slabbed.test.mixin;

import com.slabbed.test.NativeRenderAudit;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",remap=false)
public abstract class SodiumNativeModelCaptureMixin {
    @Inject(method="renderModel",at=@At("HEAD"),remap=false)
    private void slabbed$observeTerrainModel(BakedModel model,BlockState state,BlockPos pos,BlockPos origin,CallbackInfo ci) {
        NativeRenderAudit.renderPath(pos,model,"sodium_render");
    }
}
