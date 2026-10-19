package com.slabbed.test.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the real state/attachment ordering at the client piston identity boundary. */
@Mixin(value=WorldChunk.class,priority=900)
public abstract class NativePistonPacketTraceMixin {
    @Inject(method="setBlockState",at=@At("HEAD"))
    private void slabbed$before(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> result) {
        trace("before",pos,state);
    }
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void slabbed$after(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> result) {
        trace("after",pos,state);
    }
    private void trace(String stage,BlockPos pos,BlockState next) {
        var chunk=(WorldChunk)(Object)this;
        boolean base=pos.getX()==42 && (pos.getZ()==8 || pos.getZ()==28);
        boolean stone=pos.getX()>=34 && pos.getX()<=36 && pos.getZ()==12;
        if(!chunk.getWorld().isClient || (!base && !stone) || pos.getY()!=64)return;
        var fact=NativeSeats.snapshot(chunk).get(Long.toString(pos.asLong()));
        System.out.println("PHYSICAL_PISTON_PACKET "+stage+" pos="+pos+" current="+chunk.getBlockState(pos)+" next="+next+" fact="+fact);
    }
}
