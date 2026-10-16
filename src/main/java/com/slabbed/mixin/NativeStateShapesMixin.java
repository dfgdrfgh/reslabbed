package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeRules;
import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeShapes;
import com.slabbed.nativeblock.NativeSupport;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class NativeStateShapesMixin {
    @Inject(method="getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",at=@At("RETURN"),cancellable=true)
    private void slabbed$nativeOutline(BlockView world,BlockPos pos,ShapeContext context,CallbackInfoReturnable<VoxelShape> cir) {slabbed$translate(world,pos,cir);}
    @Inject(method="getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",at=@At("RETURN"),cancellable=true)
    private void slabbed$nativeCollision(BlockView world,BlockPos pos,ShapeContext context,CallbackInfoReturnable<VoxelShape> cir) {slabbed$translate(world,pos,cir);}
    @Inject(method="getRaycastShape",at=@At("RETURN"),cancellable=true)
    private void slabbed$nativeRayShape(BlockView world,BlockPos pos,CallbackInfoReturnable<VoxelShape> cir) {slabbed$translate(world,pos,cir);}
    private void slabbed$translate(BlockView world,BlockPos pos,CallbackInfoReturnable<VoxelShape> cir) {
        if(NativeShapes.raw() || !NativeRules.accepts((BlockState)(Object)this))return;
        double offset=NativeSeats.offset(world,pos);if(offset!=0)cir.setReturnValue(cir.getReturnValue().offset(0,offset,0));
    }
    @Inject(method="getCullingFace",at=@At("RETURN"),cancellable=true)
    private void slabbed$nativeCullingFace(BlockView world,BlockPos pos,Direction direction,CallbackInfoReturnable<VoxelShape> cir) {
        if(NativeRules.accepts((BlockState)(Object)this) && NativeSeats.offset(world,pos)!=0)
            cir.setReturnValue(net.minecraft.util.shape.VoxelShapes.empty());
    }
    /** Vanilla collision's one-cell fringe must examine native blocks projecting above or below their owner. */
    @Inject(method="exceedsCube",at=@At("RETURN"),cancellable=true)
    private void slabbed$boundedCollisionFringe(CallbackInfoReturnable<Boolean> cir) {
        if(NativeRules.accepts((BlockState)(Object)this))cir.setReturnValue(true);
    }
    @Inject(method="canPlaceAt",at=@At("HEAD"),cancellable=true)
    private void slabbed$nativeSupport(WorldView world,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        var answer=NativeSupport.canPlace(world,pos,(BlockState)(Object)this);if(answer!=null)cir.setReturnValue(answer);
    }
    @Inject(method="getStateForNeighborUpdate",at=@At("RETURN"),cancellable=true)
    private void slabbed$nativeSupportUpdate(Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos,CallbackInfoReturnable<BlockState> cir) {
        var state=(BlockState)(Object)this;var answer=NativeSupport.canPlace(world,pos,state);
        if(answer!=null) {
            if(!answer)cir.setReturnValue(Blocks.AIR.getDefaultState());
            else if(cir.getReturnValue().isAir())cir.setReturnValue(state);
        }
    }
}
