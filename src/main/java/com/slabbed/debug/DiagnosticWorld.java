package com.slabbed.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.slabbed.cell.CellWorld;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/** Bounded read-only evidence from loaded cells; placement facts are never modified (LAW.md). */
public final class DiagnosticWorld {
    private DiagnosticWorld() {}
    public static void piston(World world,BlockPos pos,String kind,String decision) {
        if(!DiagnosticRecorder.isActive())return;
        DiagnosticRecorder.record(world.isClient?"client":"server",kind,
                "dimension="+world.getRegistryKey().getValue()+" pos="+pos.toShortString()+" "+decision);
    }
    /** Called only on this world's owner thread. Does not load chunks or serialize block entities. */
    public static JsonObject snapshot(World world,BlockPos center) {
        var out=new JsonObject();out.addProperty("dimension",world.getRegistryKey().getValue().toString());
        out.addProperty("worldTick",world.getTime());out.addProperty("center",center.toShortString());
        var blocks=new JsonArray();
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++) {
            var pos=center.add(x,y,z);var cell=new JsonObject();cell.addProperty("pos",pos.toShortString());blocks.add(cell);
            if(!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) {cell.addProperty("loaded",false);continue;}
            cell.addProperty("loaded",true);
            try {
                var state=world.getBlockState(pos);cell.addProperty("state",state.toString());
                var seat=NativeSeats.seat(world,pos);if(seat!=null) {
                    var fact=new JsonObject();fact.addProperty("halfOffset",seat.halfOffset());fact.addProperty("block",seat.block());cell.add("seat",fact);
                }
                var raw=NativeSeats.snapshot(world.getWorldChunk(pos)).get(Long.toString(pos.asLong()));
                if(raw!=null) {var fact=new JsonObject();fact.addProperty("halfOffset",raw.halfOffset());fact.addProperty("block",raw.block());cell.add("rawSeat",fact);}
                var contents=CellWorld.contents(world,pos);var pieces=new JsonArray();
                if(contents!=null)for(var piece:contents.fragments()) {
                    var part=new JsonObject();part.addProperty("offset",piece.offset());part.addProperty("length",piece.length());
                    part.addProperty("state",piece.value().toString());pieces.add(part);
                }
                cell.add("fragments",pieces);cell.add("outline",boxes(state.getOutlineShape(world,pos)));
                cell.add("collision",boxes(state.getCollisionShape(world,pos)));
                cell.addProperty("blockLight",world.getLightLevel(LightType.BLOCK,pos));cell.addProperty("skyLight",world.getLightLevel(LightType.SKY,pos));
                if(world.getBlockEntity(pos) instanceof net.minecraft.block.entity.PistonBlockEntity moving) {
                    cell.addProperty("pushedState",moving.getPushedBlock().toString());
                    cell.addProperty("movingHalfOffset",((com.slabbed.nativeblock.NativePistonPose)moving).slabbed$halfOffset());
                }
            } catch(RuntimeException failure) {cell.addProperty("captureFailure",failure.getClass().getSimpleName());}
        }
        out.add("blocks",blocks);return out;
    }
    private static JsonArray boxes(net.minecraft.util.shape.VoxelShape shape) {
        var out=new JsonArray();int count=0;
        for(var box:shape.getBoundingBoxes()) {
            if(++count>64)break;var bounds=new JsonArray();
            for(double n:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})bounds.add(n);
            out.add(bounds);
        }
        return out;
    }
}
