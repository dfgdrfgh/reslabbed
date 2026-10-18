package com.slabbed.nativeblock;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Vanilla scaffold distance uses contacts at the stored physical base. */
public final class NativeScaffolding {
    private NativeScaffolding() {}

    public static Integer distance(BlockView view,BlockPos pos) {
        var seat=NativeSeats.seat(view,pos);
        if(seat==null || !(view instanceof World world))return null;
        int origin=pos.getY()*2+seat.halfOffset();
        if(NativeSupport.floor(world,pos,origin))return 0;
        var below=below(view,pos,origin);
        int distance=below==null?ScaffoldingBlock.MAX_DISTANCE:below.get(ScaffoldingBlock.DISTANCE);
        for(var direction:Direction.Type.HORIZONTAL) {
            var column=pos.offset(direction);
            for(int y=column.getY()-1;y<=column.getY()+1;y++) {
                var neighbor=new BlockPos(column.getX(),y,column.getZ());
                var state=view.getBlockState(neighbor);
                if(state.isOf(Blocks.SCAFFOLDING) && origin(view,neighbor)==origin)
                    distance=Math.min(distance,state.get(ScaffoldingBlock.DISTANCE)+1);
            }
        }
        return Math.min(distance,ScaffoldingBlock.MAX_DISTANCE);
    }

    public static Boolean bottom(BlockView view,BlockPos pos,int distance) {
        var seat=NativeSeats.seat(view,pos);
        if(seat==null)return null;
        return distance>0 && below(view,pos,pos.getY()*2+seat.halfOffset())==null;
    }

    private static BlockState below(BlockView view,BlockPos pos,int origin) {
        int y=Math.floorDiv(origin-1,2);
        for(int n=-1;n<=1;n++) {
            var candidate=new BlockPos(pos.getX(),y+n,pos.getZ());
            var state=view.getBlockState(candidate);
            if(state.isOf(Blocks.SCAFFOLDING) && origin(view,candidate)+2==origin)return state;
        }
        return null;
    }

    private static int origin(BlockView view,BlockPos pos) {
        var seat=NativeSeats.seat(view,pos);
        return pos.getY()*2+(seat==null?0:seat.halfOffset());
    }
}
