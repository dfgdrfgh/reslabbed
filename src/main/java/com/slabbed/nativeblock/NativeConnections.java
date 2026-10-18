package com.slabbed.nativeblock;

import com.slabbed.mixin.NativeWallAccess;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

/** Connections may change with vanilla neighbors; the stored physical origin never changes. */
public final class NativeConnections {
    private NativeConnections() {}

    public static BlockState adjust(WorldView world,BlockPos pos,BlockState state) {
        if(state==null)return null;
        var block=state.getBlock();
        if(block instanceof ChestBlock) {
            if(state.get(ChestBlock.CHEST_TYPE)!=ChestType.SINGLE
                    && !sameBase(world,pos,pos.offset(ChestBlock.getFacing(state))))
                return state.with(ChestBlock.CHEST_TYPE,ChestType.SINGLE);
            return state;
        }
        boolean wall=block instanceof WallBlock;
        if(!wall && !(block instanceof FenceBlock) && !(block instanceof PaneBlock))return state;
        var result=state;
        for(var direction:Direction.Type.HORIZONTAL) {
            if(sameBase(world,pos,pos.offset(direction)))continue;
            if(wall)result=result.with(wallProperty(direction),WallShape.NONE);
            else result=result.with(sideProperty(direction),false);
        }
        if(wall && result!=state) {
            // Vanilla derives both arm heights and the center post from the remaining connections.
            return ((NativeWallAccess)block).slabbed$getStateWith(world,result,pos,world.getBlockState(pos.up()),
                    result.get(Properties.NORTH_WALL_SHAPE)!=WallShape.NONE,
                    result.get(Properties.EAST_WALL_SHAPE)!=WallShape.NONE,
                    result.get(Properties.SOUTH_WALL_SHAPE)!=WallShape.NONE,
                    result.get(Properties.WEST_WALL_SHAPE)!=WallShape.NONE);
        }
        return result;
    }

    private static boolean sameBase(WorldView world,BlockPos first,BlockPos second) {
        return origin(world,first)==origin(world,second);
    }

    private static int origin(WorldView world,BlockPos pos) {
        var seat=NativeSeats.seat(world,pos);
        return pos.getY()*2+(seat==null?0:seat.halfOffset());
    }

    private static BooleanProperty sideProperty(Direction direction) {
        return switch(direction) {
            case NORTH -> Properties.NORTH;
            case EAST -> Properties.EAST;
            case SOUTH -> Properties.SOUTH;
            case WEST -> Properties.WEST;
            default -> throw new IllegalArgumentException("horizontal connection required");
        };
    }

    private static EnumProperty<WallShape> wallProperty(Direction direction) {
        return switch(direction) {
            case NORTH -> Properties.NORTH_WALL_SHAPE;
            case EAST -> Properties.EAST_WALL_SHAPE;
            case SOUTH -> Properties.SOUTH_WALL_SHAPE;
            case WEST -> Properties.WEST_WALL_SHAPE;
            default -> throw new IllegalArgumentException("horizontal connection required");
        };
    }
}
