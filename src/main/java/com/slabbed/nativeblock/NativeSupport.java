package com.slabbed.nativeblock;

import com.slabbed.cell.CellWorld;
import com.slabbed.Slabbed;
import com.slabbed.cell.PieceRules;
import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/** Support can remove a decoration under its vanilla rule, but never changes its stored seat. */
public final class NativeSupport {
    private static final ThreadLocal<Boolean> REVALIDATING=ThreadLocal.withInitial(()->false);
    private NativeSupport() {}
    public static Boolean canPlace(WorldView view,BlockPos owner,BlockState state) {
        if(!NativeRules.accepts(state) || !(view instanceof World world))return null;
        var seat=NativeSeats.seat(view,owner);if(seat==null)return null;
        int origin=owner.getY()*2+seat.halfOffset();var block=state.getBlock();
        if(seat.halfOffset()==0 && !physicalSupportNeeded(world,owner,state))return null;
        if(block instanceof WallHangingSignBlock) {
            var side=state.get(Properties.HORIZONTAL_FACING).rotateYClockwise();
            return side(world,owner,side,origin) || side(world,owner,side.getOpposite(),origin);
        }
        if(block instanceof WallSignBlock || block instanceof WallBannerBlock)
            return side(world,owner,state.get(Properties.HORIZONTAL_FACING).getOpposite(),origin);
        if(block instanceof HangingSignBlock)return horizontal(world,owner,origin+2,Direction.UP);
        if(block instanceof SignBlock || block instanceof BannerBlock)return horizontal(world,owner,origin,Direction.DOWN);
        return null;
    }
    private static boolean physicalSupportNeeded(World world,BlockPos owner,BlockState state) {
        var block=state.getBlock();
        if(block instanceof WallHangingSignBlock) {
            var side=state.get(Properties.HORIZONTAL_FACING).rotateYClockwise();
            return projected(world,owner.offset(side)) || projected(world,owner.offset(side.getOpposite()));
        }
        if(block instanceof WallSignBlock || block instanceof WallBannerBlock)
            return projected(world,owner.offset(state.get(Properties.HORIZONTAL_FACING).getOpposite()));
        if(block instanceof HangingSignBlock)return projected(world,owner.up());
        if(block instanceof SignBlock || block instanceof BannerBlock)return projected(world,owner.down());
        return false;
    }
    private static boolean projected(World world,BlockPos cell) {
        if(!world.isChunkLoaded(cell))return false;
        if(world.getBlockState(cell).isOf(Slabbed.CELL))return true;
        for(int n=-1;n<=1;n++) {
            var seat=NativeSeats.seat(world,cell.up(n));
            if(seat!=null && seat.halfOffset()!=0)return true;
        }
        return false;
    }
    /** Physical contact can be diagonal to the native owner, so inspect a fixed seventeen owners. */
    public static void revalidateNear(World world,BlockPos changed) {
        if(world.isClient || REVALIDATING.get() || CellWorld.mutating())return;
        REVALIDATING.set(true);
        try {
            for(int y=-2;y<=2;y++)revalidate(world,changed.up(y));
            for(var direction:Direction.Type.HORIZONTAL)
                for(int y=-1;y<=1;y++)revalidate(world,changed.offset(direction).up(y));
        } finally {REVALIDATING.set(false);}
    }
    private static void revalidate(World world,BlockPos owner) {
        if(!world.isChunkLoaded(owner) || NativePlacement.active(world,owner) || NativeSeats.seat(world,owner)==null)return;
        var answer=canPlace(world,owner,world.getBlockState(owner));
        if(Boolean.FALSE.equals(answer))world.breakBlock(owner,true);
    }
    private static boolean horizontal(World world,BlockPos owner,int contact,Direction direction) {
        var pos=new BlockPos(owner.getX(),Math.floorDiv(direction==Direction.DOWN?contact-1:contact,2),owner.getZ());
        if(!world.isChunkLoaded(pos))return true;
        var contents=CellWorld.contents(world,pos);
        if(contents!=null) {
            for(var part:contents.fragments()) {
                if(!PieceRules.supports(part.value()))continue;
                int start=pos.getY()*2+part.offset(),end=start+part.length();
                if(direction==Direction.DOWN?end==contact:start==contact)return true;
            }
        }
        for(int n=-1;n<=1;n++) {
            var nativePos=pos.up(n);var state=world.getBlockState(nativePos);
            if((state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE)
                    || !PieceRules.supports(state))continue;
            int start=nativePos.getY()*2+(int)Math.round(NativeSeats.offset(world,nativePos)*2);
            if(direction==Direction.DOWN?start+2==contact:start==contact)return true;
        }
        return false;
    }
    private static boolean side(World world,BlockPos owner,Direction direction,int origin) {
        var pos=new BlockPos(owner.getX()+direction.getOffsetX(),Math.floorDiv(origin,2),owner.getZ()+direction.getOffsetZ());
        if(!world.isChunkLoaded(pos))return true;
        var contents=CellWorld.contents(world,pos);
        if(contents!=null) {
            for(var part:contents.fragments()) {
                int start=pos.getY()*2+part.offset();
                if(PieceRules.supports(part.value()) && start<=origin && start+part.length()>origin)return true;
            }
        }
        for(int n=-1;n<=1;n++) {
            var nativePos=pos.up(n);var state=world.getBlockState(nativePos);
            if((state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE)
                    || !PieceRules.supports(state))continue;
            int start=nativePos.getY()*2+(int)Math.round(NativeSeats.offset(world,nativePos)*2);
            if(start<=origin && start+2>origin)return true;
        }
        return false;
    }
}
