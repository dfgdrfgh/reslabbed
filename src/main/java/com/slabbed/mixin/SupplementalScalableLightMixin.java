package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.cell.SupplementalCells;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WrapperProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** The optional lighting engine reads the same persistent sources as vanilla lighting. */
@Pseudo
@Mixin(targets="ca.spottedleaf.starlight.common.light.BlockStarLightEngine",remap=false)
public abstract class SupplementalScalableLightMixin {
    @ModifyExpressionValue(method="checkBlock",remap=false,
            at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getLuminance()I",remap=true))
    private int slabbed$changedEmission(int original,ChunkProvider provider,int x,int y,int z) {
        return slabbed$emission(original,x,y,z);
    }
    @ModifyExpressionValue(method="calculateLightValue",remap=false,
            at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getLuminance()I",remap=true))
    private int slabbed$recalculatedEmission(int original,ChunkProvider provider,int x,int y,int z,int expected) {
        return slabbed$emission(original,x,y,z);
    }
    @ModifyExpressionValue(method="lightChunk",remap=false,
            at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getLuminance()I",remap=true))
    private int slabbed$loadedEmission(int original,@Local BlockPos pos) {
        return slabbed$emission(original,pos.getX(),pos.getY(),pos.getZ());
    }
    @Inject(method="getSources",at=@At("RETURN"),remap=false)
    private void slabbed$storedSources(ChunkProvider provider,Chunk source,CallbackInfoReturnable<List<BlockPos>> result) {
        var chunk=source instanceof WrapperProtoChunk wrapper?wrapper.getWrappedChunk():source instanceof WorldChunk world?world:null;
        if(chunk==null)return;
        var sources=result.getReturnValue();
        SupplementalCells.lightingSnapshot(chunk).forEach((key,cell)-> {
            if(cell.luminance()>0) {
                var pos=BlockPos.fromLong(Long.parseLong(key));
                if(chunk.getBlockState(pos).getLuminance()==0)sources.add(pos);
            }
        });
    }
    @Unique
    private int slabbed$emission(int original,int x,int y,int z) {
        if(original>=15)return original;
        var source=((ScalableLightAccess)this).slabbed$cachedLightChunk(x>>4,z>>4);
        var chunk=source instanceof WrapperProtoChunk wrapper?wrapper.getWrappedChunk():source instanceof WorldChunk world?world:null;
        if(chunk==null)return original;
        var cells=SupplementalCells.lightingSnapshot(chunk);
        if(cells.isEmpty())return original;
        var cell=cells.get(Long.toString(BlockPos.asLong(x,y,z)));
        return cell==null?original:Math.max(original,cell.luminance());
    }
}
