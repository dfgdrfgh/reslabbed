package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.nativeblock.NativeScaffolding;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScaffoldingBlock.class)
public abstract class NativeScaffoldingMixin {
    @Inject(method="calculateDistance",at=@At("HEAD"),cancellable=true)
    private static void slabbed$physicalDistance(BlockView world,BlockPos pos,CallbackInfoReturnable<Integer> cir) {
        var distance=NativeScaffolding.distance(world,pos);
        if(distance!=null)cir.setReturnValue(distance);
    }

    @Inject(method="shouldBeBottom",at=@At("HEAD"),cancellable=true)
    private void slabbed$physicalBottom(BlockView world,BlockPos pos,int distance,CallbackInfoReturnable<Boolean> cir) {
        var bottom=NativeScaffolding.bottom(world,pos,distance);
        if(bottom!=null)cir.setReturnValue(bottom);
    }

    @WrapOperation(method="getCollisionShape",at=@At(value="INVOKE",
            target="Lnet/minecraft/block/ShapeContext;isAbove(Lnet/minecraft/util/shape/VoxelShape;Lnet/minecraft/util/math/BlockPos;Z)Z"))
    private boolean slabbed$physicalStandingTop(ShapeContext context,VoxelShape shape,BlockPos pos,
            boolean defaultValue,Operation<Boolean> original,BlockState state,BlockView world,
            BlockPos owner,ShapeContext ignored) {
        double offset=NativeSeats.offset(world,owner,state);
        // Only the comparison changes here; the native shape wrapper translates the result once.
        return original.call(context,offset==0?shape:shape.offset(0,offset,0),pos,defaultValue);
    }
}
