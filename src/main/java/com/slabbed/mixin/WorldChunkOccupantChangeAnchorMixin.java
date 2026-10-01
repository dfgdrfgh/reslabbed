package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Departed placement facts clear at the chunk-write funnel, regardless of notifications (LAW.md). */
@Mixin(WorldChunk.class)
public abstract class WorldChunkOccupantChangeAnchorMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void slabbed$clearDepartedOccupant(BlockPos pos,BlockState replacement,int flags,
            CallbackInfoReturnable<BlockState> cir) {
        WorldChunk chunk=(WorldChunk)(Object)this;
        BlockState departed=cir.getReturnValue();
        if (chunk.getWorld().isClient() || departed==null || departed.getBlock()==replacement.getBlock()) return;
        boolean hasFact=Double.isFinite(SlabPlacementDyAttachment.lookup(chunk,pos));
        var anchors=chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
        var flat=chunk.getAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        if (!hasFact && (anchors==null || !anchors.contains(pos.asLong()))
                && (flat==null || !flat.contains(pos.asLong()))) return;
        if ((flags & Block.MOVED)==0 && !replacement.isAir()
                && SlabAnchorAttachment.replacementPreservesAnchor(chunk.getWorld(),pos,departed,replacement)) return;
        SlabAnchorAttachment.removeAnchor(chunk,pos);
    }
}
