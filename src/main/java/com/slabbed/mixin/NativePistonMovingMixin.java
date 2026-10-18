package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.nativeblock.NativePistonPose;
import com.slabbed.nativeblock.NativePistons;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The moving entity keeps its bounded frozen pose through animation, save and settlement. */
@Mixin(PistonBlockEntity.class)
public abstract class NativePistonMovingMixin implements NativePistonPose {
    @Unique private Integer slabbed$halfOffset;
    @Unique private static final ThreadLocal<Integer> SLABBED$ESCAPE_HALF=new ThreadLocal<>();
    @Override public Integer slabbed$halfOffset() { return slabbed$halfOffset; }
    @Override public void slabbed$halfOffset(Integer halfOffset) {
        if (halfOffset!=null && (halfOffset < -1 || halfOffset > 1))
            throw new IllegalArgumentException("moving piston seat must stay bounded");
        slabbed$halfOffset=halfOffset;
    }

    @WrapMethod(method="tick")
    private static void slabbed$tick(World world,BlockPos pos,BlockState state,PistonBlockEntity entity,Operation<Void> original) {
        NativePistons.capture(entity);
        NativePistons.animate(entity,()->original.call(world,pos,state,entity));
        NativePistons.settle(entity);
    }

    @WrapMethod(method="finish")
    private void slabbed$finish(Operation<Void> original) {
        var entity=(PistonBlockEntity)(Object)this;
        NativePistons.capture(entity);
        NativePistons.animate(entity,()->original.call());
        NativePistons.settle(entity);
    }

    @Inject(method="readNbt",at=@At("RETURN"))
    private void slabbed$readPose(NbtCompound nbt,RegistryWrapper.WrapperLookup registries,CallbackInfo ci) {
        slabbed$halfOffset=null;
        if (nbt.contains("slabbed:piston_half",net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            int half=nbt.getInt("slabbed:piston_half");
            if (half>=-1 && half<=1) slabbed$halfOffset=half;
        }
    }

    @Inject(method="writeNbt",at=@At("RETURN"))
    private void slabbed$writePose(NbtCompound nbt,RegistryWrapper.WrapperLookup registries,CallbackInfo ci) {
        NativePistons.capture((PistonBlockEntity)(Object)this);
        if (slabbed$halfOffset!=null) nbt.putInt("slabbed:piston_half",slabbed$halfOffset);
    }

    @Inject(method="offsetHeadBox",at=@At("RETURN"),cancellable=true)
    private static void slabbed$physicalPushBox(BlockPos pos,Box box,PistonBlockEntity entity,CallbackInfoReturnable<Box> cir) {
        NativePistons.capture(entity);
        var half=((NativePistonPose)entity).slabbed$halfOffset();
        if (half!=null && half!=0) cir.setReturnValue(cir.getReturnValue().offset(0,half*.5,0));
    }

    @WrapOperation(method="pushEntities",at=@At(value="INVOKE",target="Lnet/minecraft/block/entity/PistonBlockEntity;push(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Direction;D)V"))
    private static void slabbed$retractionEscape(BlockPos pos,Entity entity,Direction direction,double amount,Operation<Void> original,
            @Local(argsOnly=true) PistonBlockEntity moving) {
        var previous=SLABBED$ESCAPE_HALF.get();
        var half=((NativePistonPose)moving).slabbed$halfOffset();
        if (half==null) SLABBED$ESCAPE_HALF.remove();else SLABBED$ESCAPE_HALF.set(half);
        try { original.call(pos,entity,direction,amount); }
        finally { if (previous==null) SLABBED$ESCAPE_HALF.remove();else SLABBED$ESCAPE_HALF.set(previous); }
    }

    @ModifyExpressionValue(method="push",at=@At(value="INVOKE",target="Lnet/minecraft/util/math/Box;offset(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Box;"))
    private static Box slabbed$physicalEscapeCube(Box box) {
        var half=SLABBED$ESCAPE_HALF.get();
        return half==null || half==0?box:box.offset(0,half*.5,0);
    }
}
