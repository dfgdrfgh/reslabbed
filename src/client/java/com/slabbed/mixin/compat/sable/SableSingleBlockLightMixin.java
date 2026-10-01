package com.slabbed.mixin.compat.sable;

import com.slabbed.compat.sable.SableLightSampling;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps single-block objects lit when their center is above a lowered solid (LAW.md). */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper", remap = false)
public abstract class SableSingleBlockLightMixin {
    @Shadow @Final private BlockPos.MutableBlockPos globalPos;

    @Inject(method = "setup", at = @At("RETURN"))
    private void slabbed$sampleExposedLight(ClientLevel level, double x, double y, double z,
                                          BlockPos localPos, BlockState state, CallbackInfo ci) {
        SableLightSampling.adjust(level, globalPos, y);
    }
}
