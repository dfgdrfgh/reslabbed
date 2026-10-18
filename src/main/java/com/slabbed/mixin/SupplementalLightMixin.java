package com.slabbed.mixin;

import com.slabbed.cell.SupplementalCells;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Light reads the lighting provider's existing chunk cache, never a server chunk request. */
@Mixin(ChunkBlockLightProvider.class)
public abstract class SupplementalLightMixin {
    @Inject(method="getLightSourceLuminance",at=@At("RETURN"),cancellable=true)
    private void slabbed$supplementalEmission(long packed,BlockState state,CallbackInfoReturnable<Integer> cir) {
        var pos=BlockPos.fromLong(packed);
        var view=((NativeLightingAccess)this).slabbed$lightingChunk(pos.getX()>>4,pos.getZ()>>4);
        if(view!=null)cir.setReturnValue(Math.max(cir.getReturnValue(),SupplementalCells.luminance(view,pos)));
    }
}
