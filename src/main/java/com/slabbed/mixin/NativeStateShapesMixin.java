package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeRules;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
    @WrapMethod(method="getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;")
    private VoxelShape slabbed$nativeOutline(BlockView world,BlockPos pos,ShapeContext context,Operation<VoxelShape> original) {
        if(NativeShapes.raw() || !NativeRules.accepts((BlockState)(Object)this))return original.call(world,pos,context);
        var shape=NativeShapes.withRaw(()->original.call(world,pos,context));
        double offset=NativeSeats.offset(world,pos,(BlockState)(Object)this);shape=offset==0?shape:shape.offset(0,offset,0);
        return net.minecraft.util.shape.VoxelShapes.union(shape,com.slabbed.cell.SupplementalCells.outline(world,pos));
    }
    @WrapMethod(method="getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;")
    private VoxelShape slabbed$nativeCollision(BlockView world,BlockPos pos,ShapeContext context,Operation<VoxelShape> original) {
        if(NativeShapes.raw() || !NativeRules.accepts((BlockState)(Object)this))return original.call(world,pos,context);
        var shape=NativeShapes.withRaw(()->original.call(world,pos,context));
        double offset=NativeSeats.offset(world,pos,(BlockState)(Object)this);shape=offset==0?shape:shape.offset(0,offset,0);
        return net.minecraft.util.shape.VoxelShapes.union(shape,com.slabbed.cell.SupplementalCells.collision(world,pos));
    }
    @WrapMethod(method="getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;")
    private VoxelShape slabbed$nativeCachedCollision(BlockView world,BlockPos pos,Operation<VoxelShape> original) {
        if(NativeShapes.raw() || !NativeRules.accepts((BlockState)(Object)this))return original.call(world,pos);
        var shape=NativeShapes.withRaw(()->original.call(world,pos));
        double offset=NativeSeats.offset(world,pos,(BlockState)(Object)this);shape=offset==0?shape:shape.offset(0,offset,0);
        return net.minecraft.util.shape.VoxelShapes.union(shape,com.slabbed.cell.SupplementalCells.collision(world,pos));
    }
    @WrapMethod(method="getRaycastShape")
    private VoxelShape slabbed$nativeRayShape(BlockView world,BlockPos pos,Operation<VoxelShape> original) {
        if(NativeShapes.raw() || !NativeRules.accepts((BlockState)(Object)this))return original.call(world,pos);
        var shape=NativeShapes.withRaw(()->original.call(world,pos));
        double offset=NativeSeats.offset(world,pos,(BlockState)(Object)this);shape=offset==0?shape:shape.offset(0,offset,0);
        return net.minecraft.util.shape.VoxelShapes.union(shape,com.slabbed.cell.SupplementalCells.outline(world,pos));
    }
    @Inject(method="getCullingFace",at=@At("RETURN"),cancellable=true)
    private void slabbed$nativeCullingFace(BlockView world,BlockPos pos,Direction direction,CallbackInfoReturnable<VoxelShape> cir) {
        if(NativeRules.accepts((BlockState)(Object)this) && NativeSeats.offset(world,pos,(BlockState)(Object)this)!=0)
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
            else if(cir.getReturnValue().isAir()
                    && !(state.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF) && direction==Direction.UP))
                cir.setReturnValue(state);
        }
        cir.setReturnValue(com.slabbed.nativeblock.NativeConnections.adjust(world,pos,cir.getReturnValue()));
    }
}
