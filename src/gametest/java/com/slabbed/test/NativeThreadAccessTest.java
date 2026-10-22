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
    public void cachedChunkLightingReadsPublishedSourceLifecycle(TestContext t) {
        var world=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(pos,Blocks.BARREL.getDefaultState());
        NativeSeats.put(world,pos,new NativeSeats.Seat(-1,"minecraft:barrel"));
        t.assertTrue(CellWorld.place(world,pos.getX(),pos.getZ(),pos.getY()*2+1,Blocks.GLOWSTONE.getDefaultState()),"supplemental emitter placed");
        var chunk=world.getWorldChunk(pos);var key=Long.toString(pos.asLong());
        try {
            var snapshot=CompletableFuture.supplyAsync(()->SupplementalCells.lightingSnapshot(chunk)).get(2,TimeUnit.SECONDS);
            t.assertTrue(snapshot.get(key).luminance()==15,"lighting worker sees the published cached-chunk source");
            CellWorld.remove(world,pos,1,null,false);
            t.assertTrue(CompletableFuture.supplyAsync(()->!SupplementalCells.lightingSnapshot(chunk).containsKey(key)).get(2,TimeUnit.SECONDS),
                    "lighting worker sees source removal without a server task");
            t.assertTrue(snapshot.get(key).luminance()==15,"previously published source snapshot remains immutable");
        } catch(Exception error) {throw new AssertionError("cached-chunk lighting worker stalled",error);}
        t.complete();
    }
    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void contextFreeNativeSeatsNeverReadClientFacts(TestContext t) {
        var previous=NativeSeats.clientSnapshot;
        NativeSeats.clientSnapshot=pos->{throw new AssertionError("context-free query consulted client native seats");};
        try {
            t.assertTrue(NativeSeats.seat(net.minecraft.world.EmptyBlockView.INSTANCE,null)==null,"positionless shape query has no native seat");
            t.assertTrue(NativeSeats.offset(net.minecraft.world.EmptyBlockView.INSTANCE,BlockPos.ORIGIN)==0,"empty view cannot acquire a live client origin seat");
            t.assertTrue(NativeSeats.offset(null,BlockPos.ORIGIN)==0,"absent view has no native seat");
        } finally {NativeSeats.clientSnapshot=previous;}
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void contextFreeSupplementalCellsNeverReadClientFacts(TestContext t) {
        var previous=SupplementalCells.clientSnapshot;
        SupplementalCells.clientSnapshot=pos->{throw new AssertionError("context-free query consulted client supplemental cells");};
        try {
            t.assertTrue(SupplementalCells.contents(net.minecraft.world.EmptyBlockView.INSTANCE,null)==null,"positionless query has no supplemental fragments");
            t.assertTrue(SupplementalCells.collision(net.minecraft.world.EmptyBlockView.INSTANCE,BlockPos.ORIGIN).isEmpty(),"empty view has no live supplemental collision");
            t.assertTrue(SupplementalCells.outline(null,BlockPos.ORIGIN).isEmpty(),"absent view has no supplemental outline");
        } finally {SupplementalCells.clientSnapshot=previous;}
        t.complete();
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void cachedPistonShapesStayIndependentOfClientFacts(TestContext t) {
        var previousNative=NativeSeats.clientSnapshot;var previousSupplemental=SupplementalCells.clientSnapshot;
        NativeSeats.clientSnapshot=pos->{throw new AssertionError("cached collision queried a client native seat");};
        SupplementalCells.clientSnapshot=pos->{throw new AssertionError("cached collision queried client supplemental geometry");};
        try {
            for(var block:new net.minecraft.block.Block[]{Blocks.PISTON,Blocks.STICKY_PISTON,Blocks.PISTON_HEAD,Blocks.STONE})
                for(var state:block.getStateManager().getStates()) {
                    var expected=com.slabbed.nativeblock.NativeShapes.withRaw(()->state.getCollisionShape(net.minecraft.world.EmptyBlockView.INSTANCE,null));
                    var actual=state.getCollisionShape(net.minecraft.world.EmptyBlockView.INSTANCE,null);
                    t.assertFalse(net.minecraft.util.shape.VoxelShapes.matchesAnywhere(expected,actual,net.minecraft.util.function.BooleanBiFunction.NOT_SAME),
                            "positionless cached collision retains vanilla shape for "+state);
                }
        } finally {NativeSeats.clientSnapshot=previousNative;SupplementalCells.clientSnapshot=previousSupplemental;}
        t.complete();
    }

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
    public void lightingWrapperReadsFrozenSeatAndEnumeratesSupplementalSource(TestContext t) {
        var world=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(3,4,3));
        world.setBlockState(pos,Blocks.BARREL.getDefaultState());
        NativeSeats.put(world,pos,new NativeSeats.Seat(-1,"minecraft:barrel"));
        t.assertTrue(CellWorld.place(world,pos.getX(),pos.getZ(),pos.getY()*2+1,Blocks.GLOWSTONE.getDefaultState()),"source occupies free half above native barrel");
        var wrapper=new net.minecraft.world.chunk.WrapperProtoChunk(world.getWorldChunk(pos),false);
        t.assertTrue(NativeSeats.offset(wrapper,pos)==-.5 && SupplementalCells.luminance(wrapper,pos)==15,"lighting wrapper reads actual frozen chunk facts");
        var sources=new java.util.HashSet<BlockPos>();wrapper.forEachLightSource((source,state)->sources.add(source.toImmutable()));
        t.assertTrue(sources.contains(pos),"chunk relighting enumerates native owner supplemental source");
        CellWorld.remove(world,pos,1,null,false);
        sources.clear();wrapper.forEachLightSource((source,state)->sources.add(source.toImmutable()));
        t.assertTrue(SupplementalCells.luminance(wrapper,pos)==0 && !sources.contains(pos),"removed supplemental source leaves no relight residue");t.complete();
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
