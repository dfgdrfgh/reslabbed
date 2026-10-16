package com.slabbed.nativeblock;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Every native seat reaches at most one cell above or below its owner. No stack scan is permitted. */
public final class NativeShapes {
    private static final ThreadLocal<Integer> RAW=ThreadLocal.withInitial(()->0);
    private NativeShapes() {}
    public static boolean raw() {return RAW.get()>0;}
    public static VoxelShape outline(BlockView world,BlockPos pos,BlockState state) {
        RAW.set(RAW.get()+1);
        try {return state.getOutlineShape(world,pos,ShapeContext.absent());}
        finally {RAW.set(RAW.get()-1);}
    }
    public static VoxelShape collision(BlockView world,BlockPos pos,BlockState state) {
        RAW.set(RAW.get()+1);
        try {return state.getCollisionShape(world,pos,ShapeContext.absent());}
        finally {RAW.set(RAW.get()-1);}
    }
    public static VoxelShape occupied(BlockView world,BlockPos owner,BlockState state,int halfOffset) {
        return VoxelShapes.union(outline(world,owner,state),collision(world,owner,state)).offset(0,halfOffset*.5,0);
    }
    public static boolean bounded(VoxelShape shape) {
        if(shape.isEmpty())return true;
        var box=shape.getBoundingBox();
        return box.minX>=-1e-7 && box.minY>=-1e-7 && box.minZ>=-1e-7
                && box.maxX<=1+1e-7 && box.maxY<=1+1e-7 && box.maxZ<=1+1e-7;
    }
    /** Candidate static geometry must not occupy any native object's physical volume. */
    public static boolean intersects(World world,int x,int z,int origin,BlockState state) {
        var base=new BlockPos(x,Math.floorDiv(origin,2),z);
        var shape=VoxelShapes.union(outline(world,base,state),collision(world,base,state))
                .offset(x,origin*.5,z);
        if(shape.isEmpty())return false;
        var box=shape.getBoundingBox();
        int low=(int)Math.floor(box.minY+1e-7),high=(int)Math.floor(box.maxY-1e-7)+1;
        for(int y=low-1;y<=high;y++) {
            var owner=new BlockPos(x,y,z);var seat=NativeSeats.seat(world,owner);
            if(seat==null)continue;
            var other=occupied(world,owner,world.getBlockState(owner),seat.halfOffset()).offset(x,y,z);
            if(VoxelShapes.matchesAnywhere(shape,other,BooleanBiFunction.AND))return true;
        }
        return false;
    }
    /** DDA tests projected fragments in their physical cell and returns the native packet owner. */
    public static BlockHitResult raycast(BlockView world,RaycastContext context,BlockHitResult vanilla) {
        var start=context.getStart();var end=context.getEnd();
        BlockHitResult projected=BlockView.raycast(start,end,context,(ctx,cell)-> {
            BlockHitResult nearest=null;
            for(int n=-1;n<=1;n++) {
                var owner=cell.up(n);var seat=NativeSeats.seat(world,owner);
                if(seat==null || seat.halfOffset()==0)continue;
                var state=world.getBlockState(owner);
                var shape=context.getBlockShape(state,world,owner).offset(0,n,0);
                shape=VoxelShapes.combineAndSimplify(shape,VoxelShapes.fullCube(),BooleanBiFunction.AND);
                var hit=shape.raycast(start,end,cell);
                if(hit!=null && (nearest==null || start.squaredDistanceTo(hit.getPos())<start.squaredDistanceTo(nearest.getPos())))
                    nearest=new BlockHitResult(hit.getPos(),hit.getSide(),owner,hit.isInsideBlock());
            }
            return nearest;
        },ctx->null);
        if(projected==null)return vanilla;
        return vanilla.getType()==HitResult.Type.MISS || start.squaredDistanceTo(projected.getPos())<start.squaredDistanceTo(vanilla.getPos())-1e-10
                ?projected:vanilla;
    }
}
