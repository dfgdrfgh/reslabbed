package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.cell.SupplementalCells;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Light reads the lighting provider's existing chunk cache, never a server chunk request. */
@Mixin(ChunkBlockLightProvider.class)
public abstract class SupplementalLightMixin {
    @ModifyExpressionValue(method="getLightSourceLuminance",at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getLuminance()I"))
    private int slabbed$supplementalEmission(int original,long packed,BlockState state) {
        return slabbed$source(original,BlockPos.fromLong(packed));
    }
    @ModifyExpressionValue(method="method_51532",at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getLuminance()I"))
    private int slabbed$loadedEmission(int original,BlockPos pos,BlockState state) {
        return slabbed$source(original,pos);
    }
    @org.spongepowered.asm.mixin.Unique
    private int slabbed$source(int original,BlockPos pos) {
        var view=((NativeLightingAccess)this).slabbed$lightingChunk(pos.getX()>>4,pos.getZ()>>4);
        return view==null?original:Math.max(original,SupplementalCells.luminance(view,pos));
    }
}
