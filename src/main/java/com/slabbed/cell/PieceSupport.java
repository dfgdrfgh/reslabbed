package com.slabbed.cell;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Only vanilla support removal can remove a decoration; stored origins never move (LAW.md). */
public final class PieceSupport {
    private static final ThreadLocal<Boolean> REVALIDATING=ThreadLocal.withInitial(()->false);
    private PieceSupport() {}
    public static void revalidateNear(World world,BlockPos changed) {
        if(world.isClient || CellWorld.mutating() || REVALIDATING.get())return;
        if(world instanceof net.minecraft.server.world.ServerWorld server && !server.getServer().isOnThread())return;
        REVALIDATING.set(true);
        try {
            for(int y=-2;y<=2;y++)revalidateSupplemental(world,changed.up(y));
            for(var direction:Direction.Type.HORIZONTAL)
                for(int y=-1;y<=1;y++)revalidateSupplemental(world,changed.offset(direction).up(y));
        } finally {REVALIDATING.set(false);}
    }
    private static void revalidateSupplemental(World world,BlockPos pos) {
        if(SupplementalCells.has(world,pos))revalidate(world,pos);
    }
    public static void revalidate(World world,BlockPos pos) {
        var contents=CellWorld.contents(world,pos);if(contents==null)return;
        for(var part:contents.fragments()) {
            if(part.offset()<0)continue;
            if(!supported(world,pos,part)) CellWorld.remove(world,pos,part.offset(),null,true);
        }
    }
    public static boolean supported(World world,BlockPos pos,com.slabbed.core.CellContents.Fragment<BlockState> part) {
            var state=part.value();Direction direction=null;
            if(state.getBlock() instanceof WallTorchBlock) direction=state.get(Properties.HORIZONTAL_FACING).getOpposite();
            else if(state.getBlock() instanceof TorchBlock || state.getBlock() instanceof CarpetBlock || state.getBlock() instanceof FlowerPotBlock) direction=Direction.DOWN;
            else if(state.getBlock() instanceof LanternBlock) direction=state.get(Properties.HANGING)?Direction.UP:Direction.DOWN;
            if(direction==null)return true;
            int origin=pos.getY()*2+part.offset();
            int contact=direction==Direction.UP?origin+part.length():origin;
            var adjacent=direction.getAxis().isHorizontal()?pos.offset(direction):new BlockPos(pos.getX(),Math.floorDiv(direction==Direction.DOWN?contact-1:contact,2),pos.getZ());
            if(!world.isChunkLoaded(adjacent))return true;
            boolean supported=false;
            var other=CellWorld.contents(world,adjacent);
            if(other!=null)for(var support:other.fragments()) {
                if(!PieceRules.supports(support.value()))continue;
                int start=adjacent.getY()*2+support.offset(),end=start+support.length();
                if(direction.getAxis().isHorizontal()?start<=origin && end>=origin+part.length()
                        : direction==Direction.DOWN?end==contact:start==contact) supported=true;
            }
            for(int n=-1;n<=1;n++) {
                var owner=adjacent.up(n);var nativeState=world.getBlockState(owner);
                if(nativeState.isOf(com.slabbed.Slabbed.CELL) || !PieceRules.supports(nativeState))continue;
                var seat=com.slabbed.nativeblock.NativeSeats.seat(world,owner);
                if(seat==null && nativeState.getBlock() instanceof SlabBlock
                        && nativeState.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE)continue;
                int start=owner.getY()*2+(seat==null?0:seat.halfOffset());
                int length=nativeState.getBlock() instanceof SlabBlock
                        && nativeState.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE?1:2;
                if(direction.getAxis().isHorizontal()?start<=origin && start+length>=origin+part.length()
                        : direction==Direction.DOWN?start+length==contact:start==contact)supported=true;
            }
            return supported;
    }
}
