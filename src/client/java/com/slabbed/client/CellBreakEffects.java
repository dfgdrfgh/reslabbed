package com.slabbed.client;

import com.slabbed.core.CellContents;
import com.slabbed.cell.CellWorld;
import com.slabbed.core.HalfGrid;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;
import java.util.ArrayList;
import java.util.List;

/** Material feedback uses the entire selected piece at its permanent physical origin. */
public final class CellBreakEffects {
    private record Pending(ClientWorld world,BlockPos pos,CellContents.Fragment<BlockState> piece,VoxelShape shape,long deadline) {
        int origin() {return pos.getY()*2+piece.offset();}
    }
    private static final List<Pending> PENDING=new ArrayList<>();
    private static long ticks;
    private CellBreakEffects() {}
    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->tick(client.world));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->PENDING.clear());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->
                PENDING.removeIf(p->p.world()==world && (p.pos().getX()>>4)==chunk.getPos().x && (p.pos().getZ()>>4)==chunk.getPos().z));
    }
    private static void tick(ClientWorld world) {
        ticks++;
        var iterator=PENDING.iterator();
        while(iterator.hasNext()) {
            var pending=iterator.next();
            if(pending.world()!=world || ticks>=pending.deadline()) {iterator.remove();continue;}
            if(!world.isChunkLoaded(pending.pos())) {iterator.remove();continue;}
            boolean present=false;
            for(int y=HalfGrid.at(pending.origin()).cellY();y<=HalfGrid.at(pending.origin()+pending.piece().length()-1).cellY();y++) {
                var pos=new BlockPos(pending.pos().getX(),y,pending.pos().getZ());
                var contents=CellWorld.contents(world,pos);
                if(contents==null && world.getBlockState(pos).isOf(com.slabbed.Slabbed.CELL)) {present=true;break;}
                if(contents!=null && contents.fragments().stream().anyMatch(p->pos.getY()*2+p.offset()==pending.origin())) {present=true;break;}
            }
            if(present)continue;
            var volume=pending.shape().offset(pending.pos().getX(),pending.pos().getY()+pending.piece().offset()*.5,pending.pos().getZ());
            boolean obstructed=false;
            for(var collision:world.getBlockCollisions(null,volume.getBoundingBox()))
                if(VoxelShapes.matchesAnywhere(volume,collision,BooleanBiFunction.AND)) {obstructed=true;break;}
            if(obstructed)continue;
            iterator.remove();
            debris(world,pending.pos(),pending.piece(),pending.shape());
        }
    }
    public static void hit(ClientWorld world,BlockPos pos,CellContents.Fragment<BlockState> piece,net.minecraft.util.math.Direction side) {
        var state=piece.value();var shape=com.slabbed.nativeblock.NativeShapes.outline(world,pos,state);
        if(shape.isEmpty() || state.getRenderType()==net.minecraft.block.BlockRenderType.INVISIBLE)return;
        var box=shape.getBoundingBox().offset(pos.getX(),pos.getY()+piece.offset()*.5,pos.getZ());
        double x=box.minX+.1+world.random.nextDouble()*(box.maxX-box.minX-.2);
        double y=box.minY+.1+world.random.nextDouble()*(box.maxY-box.minY-.2);
        double z=box.minZ+.1+world.random.nextDouble()*(box.maxZ-box.minZ-.2);
        switch(side) {
            case DOWN -> y=box.minY-.1;
            case UP -> y=box.maxY+.1;
            case NORTH -> z=box.minZ-.1;
            case SOUTH -> z=box.maxZ+.1;
            case WEST -> x=box.minX-.1;
            case EAST -> x=box.maxX+.1;
        }
        MinecraftClient.getInstance().particleManager.addParticle(new net.minecraft.client.particle.BlockDustParticle(
                world,x,y,z,0,0,0,state,pos).move(.2f).scale(.6f));
    }
    public static void play(ClientWorld world,BlockPos pos,CellContents.Fragment<BlockState> piece) {
        var state=piece.value();var shape=com.slabbed.nativeblock.NativeShapes.outline(world,pos,state);
        if(shape.isEmpty())return;
        int origin=pos.getY()*2+piece.offset();
        for(var pending:PENDING)if(pending.world()==world && pending.pos().getX()==pos.getX() && pending.pos().getZ()==pos.getZ() && pending.origin()==origin
                && pending.piece().length()==piece.length() && pending.piece().value()==piece.value())return;
        PENDING.removeIf(p->p.world()==world && p.pos().getX()==pos.getX() && p.pos().getZ()==pos.getZ() && p.origin()==origin);
        var center=shape.getBoundingBox().getCenter();
        center=center.add(pos.getX(),pos.getY()+piece.offset()*.5,pos.getZ());
        var sound=state.getSoundGroup();
        world.playSound(center.x,center.y,center.z,sound.getBreakSound(),SoundCategory.BLOCKS,
                (sound.getVolume()+1)*.5f,sound.getPitch()*.8f,false);
        // The client retains geometry until confirmation; debris must not start inside that solid.
        if(PENDING.size()>=256)PENDING.removeFirst();
        PENDING.add(new Pending(world,pos.toImmutable(),piece,shape,ticks+100));
    }
    private static void debris(ClientWorld world,BlockPos pos,CellContents.Fragment<BlockState> piece,VoxelShape shape) {
        var state=piece.value();
        var effect=new BlockStateParticleEffect(ParticleTypes.BLOCK,state);
        for(var box:shape.getBoundingBoxes()) {
            double dx=Math.min(1,box.maxX-box.minX),dy=Math.min(1,box.maxY-box.minY),dz=Math.min(1,box.maxZ-box.minZ);
            int nx=Math.max(2,MathHelper.ceil(dx/.25)),ny=Math.max(2,MathHelper.ceil(dy/.25)),nz=Math.max(2,MathHelper.ceil(dz/.25));
            for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++) {
                double u=(x+.5)/nx,v=(y+.5)/ny,w=(z+.5)/nz;
                MinecraftClient.getInstance().particleManager.addParticle(effect,
                        pos.getX()+box.minX+u*dx,pos.getY()+piece.offset()*.5+box.minY+v*dy,pos.getZ()+box.minZ+w*dz,
                        u-.5,v-.5,w-.5);
            }
        }
    }
}
