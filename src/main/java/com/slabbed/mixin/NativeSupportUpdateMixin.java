package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class NativeSupportUpdateMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("RETURN"))
    private void slabbed$physicalSupportChanged(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue()) {
            var world=(World)(Object)this;NativeSupport.revalidateNear(world,pos);
            com.slabbed.cell.PieceSupport.revalidateNear(world,pos);
            com.slabbed.nativeblock.NativePistonSignal.changed(world,pos,state);
        }
    }
}
