package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.debug.DiagnosticRecorder;
import com.slabbed.nativeblock.PhysicalPistons;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Decision observation cannot change the existing physical movement result. */
public final class DiagnosticGameTest {
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void optionalRecorderExplainsRefusalWithoutChangingTheWorld(TestContext t) {
        var world=t.getWorld();var base=t.getAbsolutePos(new BlockPos(3,2,3));
        world.setBlockState(base,Blocks.PISTON.getDefaultState().with(PistonBlock.FACING,Direction.UP));
        for(int y=1;y<=16;y++)world.setBlockState(base.up(y),Blocks.AIR.getDefaultState());
        com.slabbed.nativeblock.NativeSeats.put(world,base,new com.slabbed.nativeblock.NativeSeats.Seat(-1,"minecraft:piston"));
        for(int n=0;n<13;n++)t.assertTrue(CellWorld.place(world,base.getX(),base.getZ(),base.getY()*2+1+2*n,
                Blocks.STONE.getDefaultState()),"limit fixture");
        DiagnosticRecorder.start();
        try {
            var before=CellWorld.contents(world,base.up());
            var result=PhysicalPistons.prepare(world,base,Direction.UP,true);
            t.assertTrue(result!=null && !result.accepted(),"same thirteen-piece refusal");
            t.assertTrue(DiagnosticRecorder.snapshot().events().stream().anyMatch(e->e.kind().equals("piston_plan")
                    && e.details().contains("LIMIT")),"recorder explains actual physical limit rejection");
            t.assertTrue(java.util.Objects.equals(before,CellWorld.contents(world,base.up())),"observation leaves fragments unchanged");
            DiagnosticRecorder.stop();int count=DiagnosticRecorder.snapshot().events().size();
            var disabled=PhysicalPistons.prepare(world,base,Direction.UP,true);
            t.assertTrue(disabled!=null && !disabled.accepted() && DiagnosticRecorder.snapshot().events().size()==count,
                    "disabled recorder changes neither result nor history");
            // A native immovable object intersects a physical piece's destination.
            world.setBlockState(base.up(2),Blocks.OBSIDIAN.getDefaultState());
            DiagnosticRecorder.start();
            var blocked=PhysicalPistons.prepare(world,base,Direction.UP,true);
            t.assertTrue(blocked!=null && !blocked.accepted(),"same immovable refusal");
            t.assertTrue(DiagnosticRecorder.snapshot().events().stream().anyMatch(e->e.details().contains("IMMOVABLE")),
                    "recorder explains actual immovable rejection");
        } finally {DiagnosticRecorder.stop();}
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void reportShapesCannotLoadChunksAndRetainStoredGeometry(TestContext t) {
        var world=t.getWorld();var center=t.getAbsolutePos(new BlockPos(3,2,3));var far=center.add(1600,0,1600);
        t.assertFalse(world.isChunkLoaded(far),"context neighbor starts unloaded");
        world.setBlockState(center,DiagnosticShapeFixture.BLOCK.getDefaultState());
        var piston=center.east();world.setBlockState(piston,Blocks.PISTON.getDefaultState());
        com.slabbed.nativeblock.NativeSeats.put(world,piston,new com.slabbed.nativeblock.NativeSeats.Seat(-1,"minecraft:piston"));
        t.assertTrue(CellWorld.place(world,piston.getX(),piston.getZ(),piston.getY()*2+1,Blocks.STONE.getDefaultState()),"supplemental upper piece");
        var before=CellWorld.contents(world,piston);var seat=com.slabbed.nativeblock.NativeSeats.seat(world,piston);
        DiagnosticShapeFixture.outlines=DiagnosticShapeFixture.collisions=0;DiagnosticShapeFixture.target=far;
        com.google.gson.JsonObject snapshot;
        try {snapshot=com.slabbed.debug.DiagnosticWorld.snapshot(world,center);}
        finally {DiagnosticShapeFixture.target=null;}
        t.assertTrue(DiagnosticShapeFixture.outlines>0 && DiagnosticShapeFixture.collisions>0,"both contextual shapes exercised");
        t.assertFalse(world.isChunkLoaded(far),"outline/collision/block-entity reads cannot load a context neighbor");
        t.assertTrue(java.util.Objects.equals(before,CellWorld.contents(world,piston))
                && seat.equals(com.slabbed.nativeblock.NativeSeats.seat(world,piston)),"snapshot preserves all stored geometry");
        var blocks=snapshot.getAsJsonArray("blocks");boolean found=false;
        for(var raw:blocks) {
            var cell=raw.getAsJsonObject();if(!cell.get("pos").getAsString().equals(piston.toShortString()))continue;
            found=true;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(var box:cell.getAsJsonArray("outline")) {var coordinates=box.getAsJsonArray();min=Math.min(min,coordinates.get(1).getAsDouble());max=Math.max(max,coordinates.get(4).getAsDouble());}
            t.assertTrue(min==-.5 && max==1.0,"report includes frozen lowered native shape and clipped supplemental shape");
        }
        t.assertTrue(found,"piston context present");t.complete();
    }

}
