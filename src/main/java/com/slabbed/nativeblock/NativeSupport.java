package com.slabbed.nativeblock;

import com.slabbed.cell.CellWorld;
import com.slabbed.Slabbed;
import com.slabbed.cell.PieceRules;
import com.slabbed.mixin.NativePlantAccess;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import java.util.function.BiPredicate;
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
        if(block instanceof WallSignBlock || block instanceof WallBannerBlock || block instanceof LadderBlock
                || block instanceof WallRedstoneTorchBlock)
            return side(world,owner,state.get(Properties.HORIZONTAL_FACING).getOpposite(),origin);
        if(block instanceof HangingSignBlock)return horizontal(world,owner,origin+2,Direction.UP);
        if(block instanceof SignBlock || block instanceof BannerBlock)return horizontal(world,owner,origin,Direction.DOWN);
        if(block instanceof WallMountedBlock) {
            var face=state.get(Properties.BLOCK_FACE);
            if(face==net.minecraft.block.enums.BlockFace.WALL)
                return side(world,owner,state.get(Properties.HORIZONTAL_FACING).getOpposite(),origin);
            return horizontal(world,owner,face==net.minecraft.block.enums.BlockFace.CEILING?origin+2:origin,
                    face==net.minecraft.block.enums.BlockFace.CEILING?Direction.UP:Direction.DOWN);
        }
        if(block instanceof BellBlock)return bell(world,owner,state,origin);
        if(block instanceof PointedDripstoneBlock) {
            var direction=state.get(PointedDripstoneBlock.VERTICAL_DIRECTION).getOpposite();
            int contact=direction==Direction.DOWN?origin:origin+2;
            return contact(world,owner,contact,direction,(floor,pos)->PieceRules.supports(floor)
                    || floor.isOf(Blocks.POINTED_DRIPSTONE)
                    && floor.get(PointedDripstoneBlock.VERTICAL_DIRECTION)==state.get(PointedDripstoneBlock.VERTICAL_DIRECTION));
        }
        if(block instanceof CactusBlock)return cactus(world,owner,origin);
        if(block instanceof SeaPickleBlock)return horizontal(world,owner,origin,Direction.DOWN);
        if(block instanceof PlantBlock) {
            if(state.contains(Properties.DOUBLE_BLOCK_HALF)
                    && state.get(Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER)return null;
            if((block instanceof CropBlock || block instanceof StemBlock)
                    && world.getBaseLightLevel(owner,0)<8 && !world.isSkyVisible(owner))return false;
            return contact(world,owner,origin,Direction.DOWN,
                    (floor,pos)->((NativePlantAccess)block).slabbed$canPlantOnTop(floor,world,pos));
        }
        if(block instanceof SnowBlock)return contact(world,owner,origin,Direction.DOWN,(floor,pos)->
                !floor.isIn(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)
                && (floor.isIn(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON) || PieceRules.supports(floor)
                || floor.isOf(Blocks.SNOW) && floor.get(SnowBlock.LAYERS)==8));
        if(block instanceof CakeBlock)return contact(world,owner,origin,Direction.DOWN,(floor,pos)->floor.isSolid());
        if(block instanceof DoorBlock && state.get(Properties.DOUBLE_BLOCK_HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER)return null;
        if(floorAttached(block))return horizontal(world,owner,origin,Direction.DOWN);
        return null;
    }
    private static boolean floorAttached(Block block) {
        return block instanceof DoorBlock || block instanceof AbstractPressurePlateBlock || block instanceof RedstoneWireBlock
                || block instanceof AbstractRedstoneGateBlock || block instanceof AbstractRailBlock || block instanceof RedstoneTorchBlock
                || block instanceof CandleBlock || block instanceof SeaPickleBlock;
    }
    private static boolean physicalSupportNeeded(World world,BlockPos owner,BlockState state) {
        var block=state.getBlock();
        if(block instanceof WallHangingSignBlock) {
            var side=state.get(Properties.HORIZONTAL_FACING).rotateYClockwise();
            return projected(world,owner.offset(side)) || projected(world,owner.offset(side.getOpposite()));
        }
        if(block instanceof WallSignBlock || block instanceof WallBannerBlock || block instanceof LadderBlock
                || block instanceof WallRedstoneTorchBlock)
            return projected(world,owner.offset(state.get(Properties.HORIZONTAL_FACING).getOpposite()));
        if(block instanceof HangingSignBlock)return projected(world,owner.up());
        if(block instanceof SignBlock || block instanceof BannerBlock)return projected(world,owner.down());
        if(block instanceof WallMountedBlock) {
            var face=state.get(Properties.BLOCK_FACE);
            return projected(world,face==net.minecraft.block.enums.BlockFace.WALL
                    ?owner.offset(state.get(Properties.HORIZONTAL_FACING).getOpposite())
                    :face==net.minecraft.block.enums.BlockFace.CEILING?owner.up():owner.down());
        }
        if(block instanceof BellBlock) {
            return switch(state.get(BellBlock.ATTACHMENT)) {
                case FLOOR -> projected(world,owner.down());
                case CEILING -> projected(world,owner.up());
                case SINGLE_WALL, DOUBLE_WALL -> projected(world,owner.offset(state.get(BellBlock.FACING)))
                        || projected(world,owner.offset(state.get(BellBlock.FACING).getOpposite()));
            };
        }
        if(block instanceof PointedDripstoneBlock)
            return projected(world,owner.offset(state.get(PointedDripstoneBlock.VERTICAL_DIRECTION).getOpposite()));
        if(floorAttached(block) || block instanceof PlantBlock || block instanceof CactusBlock
                || block instanceof SnowBlock || block instanceof CakeBlock)return projected(world,owner.down());
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
        if(world instanceof net.minecraft.server.world.ServerWorld server && !server.getServer().isOnThread())return;
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
    private static boolean bell(World world,BlockPos owner,BlockState state,int origin) {
        return switch(state.get(BellBlock.ATTACHMENT)) {
            case FLOOR -> horizontal(world,owner,origin,Direction.DOWN);
            case CEILING -> horizontal(world,owner,origin+2,Direction.UP);
            case SINGLE_WALL -> side(world,owner,state.get(BellBlock.FACING),origin);
            case DOUBLE_WALL -> side(world,owner,state.get(BellBlock.FACING),origin)
                    || side(world,owner,state.get(BellBlock.FACING).getOpposite(),origin);
        };
    }
    public static boolean floor(World world,BlockPos owner,int origin) {return horizontal(world,owner,origin,Direction.DOWN);}
    private static boolean horizontal(World world,BlockPos owner,int contact,Direction direction) {
        return contact(world,owner,contact,direction,(floor,pos)->PieceRules.supports(floor));
    }
    /** Examine the actual state whose physical boundary meets the permanent placement origin. */
    private static boolean contact(World world,BlockPos owner,int contact,Direction direction,
                                   BiPredicate<BlockState,BlockPos> accepts) {
        var pos=new BlockPos(owner.getX(),Math.floorDiv(direction==Direction.DOWN?contact-1:contact,2),owner.getZ());
        if(!world.isChunkLoaded(pos))return true;
        var contents=CellWorld.contents(world,pos);
        if(contents!=null) {
            for(var part:contents.fragments()) {
                int start=pos.getY()*2+part.offset(),end=start+part.length();
                if((direction==Direction.DOWN?end==contact:start==contact) && accepts.test(part.value(),pos))return true;
            }
        }
        for(int n=-1;n<=1;n++) {
            var nativePos=pos.up(n);var state=world.getBlockState(nativePos);
            if(state.isAir() || state.isOf(Slabbed.CELL))continue;
            var seat=NativeSeats.seat(world,nativePos);
            // Unshifted partial slabs were already examined as physical fragments.
            if(seat==null && state.getBlock() instanceof SlabBlock
                    && state.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE)continue;
            int start=nativePos.getY()*2+(seat==null?0:seat.halfOffset());
            int length=state.getBlock() instanceof SlabBlock
                    && state.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE?1:2;
            if((direction==Direction.DOWN?start+length==contact:start==contact) && accepts.test(state,nativePos))return true;
        }
        return false;
    }
    private static boolean cactus(World world,BlockPos owner,int origin) {
        if(!contact(world,owner,origin,Direction.DOWN,(floor,pos)->floor.isOf(Blocks.CACTUS) || floor.isIn(BlockTags.SAND)))return false;
        for(var direction:Direction.Type.HORIZONTAL) {
            var column=owner.offset(direction);
            if(occupies(world,column,origin,origin+2,(state,pos)->state.isSolid()
                    || state.getFluidState().isIn(FluidTags.LAVA)))return false;
        }
        return !occupies(world,owner,origin+2,origin+4,(state,pos)->state.isLiquid());
    }
    private static boolean occupies(World world,BlockPos column,int low,int high,BiPredicate<BlockState,BlockPos> predicate) {
        for(int y=Math.floorDiv(low,2)-1;y<=Math.floorDiv(high-1,2)+1;y++) {
            var pos=new BlockPos(column.getX(),y,column.getZ());
            if(!world.isChunkLoaded(pos))continue;
            var parts=CellWorld.contents(world,pos);
            if(parts!=null)for(var part:parts.fragments()) {
                int start=y*2+part.offset();
                if(start<high && start+part.length()>low && predicate.test(part.value(),pos))return true;
            }
            var state=world.getBlockState(pos);
            if(state.isAir() || state.isOf(Slabbed.CELL))continue;
            var seat=NativeSeats.seat(world,pos);
            if(seat==null && state.getBlock() instanceof SlabBlock
                    && state.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE)continue;
            int start=y*2+(seat==null?0:seat.halfOffset());
            int length=state.getBlock() instanceof SlabBlock
                    && state.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE?1:2;
            if(start<high && start+length>low && predicate.test(state,pos))return true;
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
