package com.slabbed.mixin;

import com.slabbed.anchor.PlacementDyOverlay;
import com.slabbed.compat.CompatHooks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Departed client bodies retire their whole prediction group (LAW.md). */
@Mixin(WorldChunk.class)
public abstract class WorldChunkPredictionLifecycleMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void slabbed$retireDepartedPrediction(BlockPos pos, BlockState next, boolean moved,
                                                 CallbackInfoReturnable<BlockState> cir) {
        var chunk=(WorldChunk)(Object)this;
        var previous=cir.getReturnValue();
        if (chunk.getWorld().isClient() && previous!=null
                && !CompatHooks.keepsPlacementHeight(previous,next)) {
            PlacementDyOverlay.onBlockRemoved(chunk.getWorld(),pos);
        }
    }
}
