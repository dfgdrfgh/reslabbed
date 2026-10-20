package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeShapes;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Empty native half-cells receive ordinary light through their physically open neighbors. */
public final class NativeParticleLight {
    private record Cell(boolean blocked,boolean virtual,int sky,int block,int emission) {}
    private record Visit(BlockPos pos,int distance) {}
    private static final Map<Long,Cell> CELLS=new HashMap<>();
    private static final Map<Long,Integer> LIGHT=new HashMap<>();
    private static World cachedWorld;
    private NativeParticleLight() {}
    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->clear());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
    }
    private static void clear() {CELLS.clear();LIGHT.clear();cachedWorld=null;}
    public static int light(World world,BlockPos pos,double physicalY,int original) {
        double offset=NativeSeats.offset(world,pos),y=physicalY-pos.getY();
        if(offset==0 || !world.getBlockState(pos).isOpaqueFullCube(world,pos)
                || (offset<0?y<1+offset-1e-7:y>offset+1e-7))return original;
        if(cachedWorld!=world) {clear();cachedWorld=world;}
        int half=offset<0?1:0;long key=key(pos,half);
        int result=LIGHT.computeIfAbsent(key,ignored->resolve(world,pos,half));
        return Math.max(original&0xf00000,result&0xf00000) | Math.max(original&0xf0,result&0xf0);
    }
    private static long key(BlockPos pos,int half) {return BlockPos.asLong(pos.getX(),pos.getY()*2+half,pos.getZ());}
    private static int resolve(World world,BlockPos start,int half) {
        var queue=new ArrayDeque<Visit>();var seen=new HashSet<Long>();
        queue.add(new Visit(start.toImmutable(),0));seen.add(start.asLong());
        int sky=0,block=0;
        while(!queue.isEmpty()) {
            var visit=queue.removeFirst();var pos=visit.pos();int distance=visit.distance();
            var cell=cell(world,pos,half);
            // An opaque emitter lights its face; propagated light must stop at the same solid.
            block=Math.max(block,cell.emission()-distance);
            if(cell.blocked())continue;
            if(!cell.virtual()) {
                sky=Math.max(sky,cell.sky()-distance);block=Math.max(block,cell.block()-distance);continue;
            }
            block=Math.max(block,cell.block()-distance);
            var vertical=cell(world,half==1?pos.up():pos.down(),1-half);
            block=Math.max(block,vertical.emission()-distance-1);
            if(!vertical.blocked()) {
                int skyLoss=half==1 && vertical.sky()==15?0:1;
                sky=Math.max(sky,vertical.sky()-distance-skyLoss);
                block=Math.max(block,vertical.block()-distance-1);
            }
            // Ordinary light cannot contribute after fifteen horizontal attenuation steps.
            if(distance>=14 || (sky>=15-distance && block>=15-distance))continue;
            for(var direction:Direction.Type.HORIZONTAL) {
                var next=pos.offset(direction);
                if(seen.add(next.asLong()))queue.addLast(new Visit(next,distance+1));
            }
        }
        return Math.max(0,sky)<<20 | Math.max(0,block)<<4;
    }
    private static Cell cell(World world,BlockPos pos,int half) {
        return CELLS.computeIfAbsent(key(pos,half),ignored->{
            if(!world.isChunkLoaded(pos))return new Cell(true,false,0,0,0);
            var opaque=VoxelShapes.empty();int emission=0;
            var space=VoxelShapes.cuboid(0,half*.5,0,1,(half+1)*.5,1);
            for(int n=-1;n<=1;n++) {
                var owner=pos.up(n);var state=world.getBlockState(owner);double offset=NativeSeats.offset(world,owner);
                if(n!=0 && offset==0)continue;
                if(state.isOpaque())opaque=VoxelShapes.union(opaque,NativeShapes.collision(world,owner,state).offset(0,n+offset,0));
                if(!state.isOf(Slabbed.CELL) && state.getLuminance()>0 && VoxelShapes.matchesAnywhere(space,
                        NativeShapes.outline(world,owner,state).offset(0,n+offset,0),BooleanBiFunction.AND))
                    emission=Math.max(emission,state.getLuminance());
            }
            var contents=CellWorld.contents(world,pos);
            if(contents!=null)for(var part:contents.fragments()) {
                if(part.value().isOpaque())opaque=VoxelShapes.union(opaque,NativeShapes.collision(world,pos,part.value()).offset(0,part.offset()*.5,0));
                if(part.value().getLuminance()>0 && VoxelShapes.matchesAnywhere(space,
                        NativeShapes.outline(world,pos,part.value()).offset(0,part.offset()*.5,0),BooleanBiFunction.AND))
                    emission=Math.max(emission,part.value().getLuminance());
            }
            boolean blocked=!VoxelShapes.matchesAnywhere(space,opaque,BooleanBiFunction.ONLY_FIRST);
            var state=world.getBlockState(pos);double offset=NativeSeats.offset(world,pos);
            boolean virtual=state.isOpaqueFullCube(world,pos) && (half==1?offset<0:offset>0);
            return new Cell(blocked,virtual,world.getLightLevel(LightType.SKY,pos),
                    Math.max(world.getLightLevel(LightType.BLOCK,pos),state.getLuminance()),emission);
        });
    }
}
