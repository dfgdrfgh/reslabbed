package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeSupport;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's placement-direction probe must use the same physical mount as survival. */
@Mixin(PointedDripstoneBlock.class)
public abstract class NativeDripstoneSupportMixin {
    @Inject(method="canPlaceAtWithDirection",at=@At("HEAD"),cancellable=true)
    private static void slabbed$physicalDirection(WorldView world,BlockPos pos,Direction direction,CallbackInfoReturnable<Boolean> cir) {
        if(NativeSeats.seat(world,pos)==null)return;
        var state=Blocks.POINTED_DRIPSTONE.getDefaultState().with(PointedDripstoneBlock.VERTICAL_DIRECTION,direction);
        var supported=NativeSupport.canPlace(world,pos,state);
        if(supported!=null)cir.setReturnValue(supported);
    }
}
