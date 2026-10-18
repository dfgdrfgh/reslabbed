package com.slabbed.test;

import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.cell.SupplementalCells;
import com.slabbed.cell.CellWorld;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Lighting workers consume published facts without asking the waiting server thread for chunks. */
public final class NativeThreadAccessTest {
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void workerReadsShiftedGeometryAndSupplementalSnapshotWithoutServerTasks(TestContext t) {
        var world=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(pos,Blocks.STONE.getDefaultState());
        NativeSeats.put(world,pos,new NativeSeats.Seat(-1,"minecraft:stone"));
        t.assertTrue(CellWorld.place(world,pos.getX(),pos.getZ(),pos.getY()*2+1,Blocks.BIRCH_SLAB.getDefaultState()),"supplemental fixture occupies unused physical half");
        try {
            var result=CompletableFuture.supplyAsync(()-> {
                var state=Blocks.STONE.getDefaultState();
                return NativeSeats.offset(world,pos,state)==-.5
                        && !SupplementalCells.outline(world,pos).isEmpty()
                        && Math.abs(state.getCollisionShape(world,pos).getBoundingBox().minY+.5)<1e-7
                        && state.getCullingFace(world,pos,Direction.UP).isEmpty();
            }).get(2,TimeUnit.SECONDS);
            t.assertTrue(result,"worker sees frozen geometry and supplemental facts while server thread waits");
        } catch(Exception failure) {throw new AssertionError("worker read waited for the blocked server thread",failure);}
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void workerQueriesNeverLoadAnAbsentChunk(TestContext t) {
        var world=t.getWorld();var far=t.getAbsolutePos(new BlockPos(8192,4,8192));
        t.assertFalse(world.isChunkLoaded(far),"far worker-query fixture starts unloaded");
        try {
            var absent=CompletableFuture.supplyAsync(()->NativeSeats.offset(world,far,Blocks.STONE.getDefaultState())==0
                    && SupplementalCells.contents(world,far)==null).get(2,TimeUnit.SECONDS);
            t.assertTrue(absent,"absent chunk has no published facts");
        } catch(Exception failure) {throw new AssertionError("absent-chunk read waited for server work",failure);}
        t.assertFalse(world.isChunkLoaded(far),"worker fact query did not request or generate a chunk");
        t.complete();
    }
}
