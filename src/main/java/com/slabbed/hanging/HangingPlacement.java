package com.slabbed.hanging;

import com.slabbed.cell.CellWorld;
import com.slabbed.cell.PieceRules;
import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeShapes;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.function.Supplier;

/** The clicked face chooses a mount once. Later support checks may remove it, never move it. */
public final class HangingPlacement {
    private record Mount(World world,BlockPos attached,Direction facing,double offset) {}
    private static final ThreadLocal<Mount> ACTIVE=new ThreadLocal<>();
    private static final double EPS=1e-6;
    private HangingPlacement() {}

    public static ActionResult place(ItemUsageContext context,Supplier<ActionResult> vanilla) {
        var previous=ACTIVE.get();var world=context.getWorld();var backing=context.getBlockPos();
        var hit=new BlockHitResult(context.getHitPos(),context.getSide(),backing,false);
        var part=CellWorld.pick(world,backing,hit);
        double offset=part!=null && part.length()==2 && PieceRules.supports(part.value())
                ?part.offset()*.5:NativeSeats.offset(world,backing);
        ACTIVE.set(new Mount(world,backing.offset(context.getSide()).toImmutable(),context.getSide(),offset));
        try {return vanilla.get();}
        finally {if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }

    /** Called only while constructing an entity; missing saved mounts remain at vanilla height. */
    public static double initial(World world,BlockPos attached,Direction facing) {
        var mount=ACTIVE.get();
        if(mount==null)return 0;
        return mount.world()==world && mount.attached().equals(attached) && mount.facing()==facing
                ?mount.offset():Double.NaN;
    }

    /** Saved data is a bounded local pose, not a request to inspect or load a wall. */
    public static double valid(double offset) {
        return Double.isFinite(offset) && offset>=-.5 && offset<=.5 && offset*2==Math.rint(offset*2)?offset:0;
    }

    /** Checks the entire remembered backing rectangle, including every tile of a painting. */
    public static boolean supported(AbstractDecorationEntity entity) {
        var world=entity.getWorld();var box=entity.getBoundingBox();var face=entity.getHorizontalFacing();
        double depth=1e-4;
        var strip=switch(face) {
            case EAST -> new Box(box.minX-depth,box.minY+EPS,box.minZ+EPS,box.minX,box.maxY-EPS,box.maxZ-EPS);
            case WEST -> new Box(box.maxX,box.minY+EPS,box.minZ+EPS,box.maxX+depth,box.maxY-EPS,box.maxZ-EPS);
            case SOUTH -> new Box(box.minX+EPS,box.minY+EPS,box.minZ-depth,box.maxX-EPS,box.maxY-EPS,box.minZ);
            case NORTH -> new Box(box.minX+EPS,box.minY+EPS,box.maxZ,box.maxX-EPS,box.maxY-EPS,box.maxZ+depth);
            case UP -> new Box(box.minX+EPS,box.minY-depth,box.minZ+EPS,box.maxX-EPS,box.minY,box.maxZ-EPS);
            case DOWN -> new Box(box.minX+EPS,box.maxY,box.minZ+EPS,box.maxX-EPS,box.maxY+depth,box.maxZ-EPS);
        };
        var backing=collision(world,strip);
        // Adjacent unloaded chunks are not a reason to destroy a decoration or wait for generation.
        if(backing==null)return true;
        if(VoxelShapes.matchesAnywhere(VoxelShapes.cuboid(strip),backing,BooleanBiFunction.ONLY_FIRST))return false;
        var occupied=collision(world,box.contract(EPS));
        if(occupied==null)return true;
        if(VoxelShapes.matchesAnywhere(VoxelShapes.cuboid(box.contract(EPS)),occupied,BooleanBiFunction.AND))return false;
        return world.getOtherEntities(entity,box,other->other instanceof AbstractDecorationEntity).isEmpty();
    }

    private static VoxelShape collision(World world,Box bounds) {
        var shape=VoxelShapes.empty();
        int minX=(int)Math.floor(bounds.minX+EPS),maxX=(int)Math.floor(bounds.maxX-EPS);
        int minY=(int)Math.floor(bounds.minY+EPS),maxY=(int)Math.floor(bounds.maxY-EPS);
        int minZ=(int)Math.floor(bounds.minZ+EPS),maxZ=(int)Math.floor(bounds.maxZ-EPS);
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++) {
            var cell=new BlockPos(x,y,z);
            if(!world.isChunkLoaded(cell))return null;
            var state=world.getBlockState(cell);
            var seat=NativeSeats.seat(world,cell);
            if(seat==null)shape=VoxelShapes.union(shape,NativeShapes.collision(world,cell,state).offset(x,y,z));
            for(int n=-1;n<=1;n++) {
                var owner=cell.up(n);
                if(!world.isChunkLoaded(owner))return null;
                var stored=NativeSeats.seat(world,owner);
                if(stored!=null)shape=VoxelShapes.union(shape,NativeShapes.collision(world,owner,world.getBlockState(owner))
                        .offset(owner.getX(),owner.getY()+stored.offset(),owner.getZ()));
            }
        }
        return shape;
    }
}
