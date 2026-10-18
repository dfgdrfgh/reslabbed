package com.slabbed.mixin;

import com.slabbed.cell.SupplementalCells;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WrapperProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BiConsumer;

/** Chunk relighting includes persistent sources stored beside ordinary native owners. */
@Mixin(Chunk.class)
public abstract class SupplementalLightSourcesMixin {
    @Inject(method="forEachLightSource",at=@At("TAIL"))
    private void slabbed$storedSources(BiConsumer<BlockPos,BlockState> consumer,CallbackInfo ci) {
        var self=(Chunk)(Object)this;
        var chunk=self instanceof WrapperProtoChunk wrapper?wrapper.getWrappedChunk():self instanceof WorldChunk world?world:null;
        if(chunk==null)return;
        SupplementalCells.snapshot(chunk).forEach((key,cell)-> {
            if(cell.luminance()>0) {
                var pos=BlockPos.fromLong(Long.parseLong(key));
                if(chunk.getBlockState(pos).getLuminance()==0)consumer.accept(pos,chunk.getBlockState(pos));
            }
        });
    }
}
