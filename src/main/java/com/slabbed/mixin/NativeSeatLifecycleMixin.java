package com.slabbed.mixin;

import com.slabbed.nativeblock.NativePlacement;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class NativeSeatLifecycleMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void slabbed$clearRemovedSeat(BlockPos pos,BlockState next,boolean moved,CallbackInfoReturnable<BlockState> cir) {
        var before=cir.getReturnValue();var chunk=(WorldChunk)(Object)this;
        com.slabbed.nativeblock.NativePistons.restoreClientIdentity(chunk.getWorld(),pos,next);
        // A matching synchronized pose can precede its block-state packet.
        if(before!=null && before.getBlock()!=next.getBlock() && !NativePlacement.active(chunk.getWorld(),pos) && !com.slabbed.nativeblock.NativeTransformations.active(chunk.getWorld(),pos)
                && NativeSeats.seat(chunk,pos)==null)
            NativeSeats.remove(chunk.getWorld(),pos);
        var world=chunk.getWorld();
        if(before!=null && before.getBlock()!=next.getBlock() && !world.isClient
                && !com.slabbed.cell.CellWorld.mutating() && !com.slabbed.nativeblock.NativeTransformations.active(world,pos)
                && !com.slabbed.nativeblock.NativePistons.settling(world,pos,next)) {
            var supplemental=com.slabbed.cell.SupplementalCells.contents(chunk,pos);
            if(supplemental!=null) {
                com.slabbed.cell.SupplementalCells.remove(world,pos);
                if(next.isAir())com.slabbed.cell.CellWorld.promoteSupplemental(world,pos,supplemental);
                else com.slabbed.cell.CellWorld.clearOutsideCell(world,pos,supplemental);
            }
        }
    }
}
