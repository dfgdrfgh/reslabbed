package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.EmptyBlockView;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Native plant outlines and model offsets must agree with the single remembered seat (LAW.md). */
public final class TerrainSlabsVegetationTest {
    public static boolean nativeOffsetsEnabled;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void naturalVegetationUsesOneVerticalOffset(TestContext ctx) {
        boolean previous = nativeOffsetsEnabled;
        nativeOffsetsEnabled = true;
        try {
            BlockPos pos = new BlockPos(2, 3, 2);
            Map<BlockPos, BlockState> states = new HashMap<>();
            BlockRenderView view = view(states);
            for (BlockState support : new BlockState[]{
                    TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState(),
                    Blocks.STONE_SLAB.getDefaultState()}) {
                states.put(pos.down(), support);
                for (BlockState plant : new BlockState[]{Blocks.SHORT_GRASS.getDefaultState(),
                        Blocks.FERN.getDefaultState(), Blocks.DANDELION.getDefaultState()}) {
                    states.put(pos, plant);
                    assertOneSeat(ctx, view, pos, plant, -0.5d);
                    Vec3d nativeJitter = plant.getModelOffset(EmptyBlockView.INSTANCE, pos);
                    Vec3d combinedJitter = plant.getModelOffset(view, pos);
                    ctx.assertTrue(nativeJitter.x == combinedJitter.x && nativeJitter.z == combinedJitter.z,
                            "compatibility changed horizontal plant jitter");
                }
            }
            BlockState tall = Blocks.TALL_GRASS.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
            states.put(pos, tall);
            states.put(pos.up(), tall.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            assertOneSeat(ctx, view, pos, tall, -0.5d);
            assertOneSeat(ctx, view, pos.up(), states.get(pos.up()), -0.5d);
            states.put(pos.down(), Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
            states.put(pos, Blocks.SHORT_GRASS.getDefaultState());
            assertOneSeat(ctx, view, pos, states.get(pos), 0.0d);
        } finally {
            nativeOffsetsEnabled = previous;
        }
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void frozenVegetationSeatSurvivesSupportChanges(TestContext ctx) {
        boolean oldNative = nativeOffsetsEnabled;
        boolean oldFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        var oldLookup = SlabAnchorAttachment.clientPlacementDyLookup;
        var oldOverlay = SlabAnchorAttachment.clientEffectivePlacementDyLookup;
        nativeOffsetsEnabled = true;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        SlabAnchorAttachment.clientEffectivePlacementDyLookup = null;
        try {
            BlockPos pos = new BlockPos(2, 3, 2);
            BlockState grass = Blocks.SHORT_GRASS.getDefaultState();
            Map<BlockPos, BlockState> states = new HashMap<>();
            states.put(pos, grass);
            BlockRenderView view = view(states);
            for (int sixteenths : new int[]{0, -8, -16}) {
                SlabAnchorAttachment.clientPlacementDyLookup = queried ->
                        queried.equals(pos) ? SlabAnchorAttachment.PlacementDyFact.fromStoredSixteenths((byte) sixteenths)
                                : SlabAnchorAttachment.PlacementDyFact.absent();
                for (BlockState support : new BlockState[]{TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState(),
                        Blocks.STONE.getDefaultState(), Blocks.AIR.getDefaultState()}) {
                    states.put(pos.down(), support);
                    assertOneSeat(ctx, view, pos, grass, sixteenths / 16.0d);
                }
            }
        } finally {
            nativeOffsetsEnabled = oldNative;
            SlabAnchorAttachment.FROZEN_DY_ENABLED = oldFrozen;
            SlabAnchorAttachment.clientPlacementDyLookup = oldLookup;
            SlabAnchorAttachment.clientEffectivePlacementDyLookup = oldOverlay;
        }
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void meshCullMaskReadsEachNeighborSeatOnce(TestContext ctx) {
        boolean oldFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        var oldLookup = SlabAnchorAttachment.clientPlacementDyLookup;
        var oldOverlay = SlabAnchorAttachment.clientEffectivePlacementDyLookup;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        SlabAnchorAttachment.clientEffectivePlacementDyLookup = null;
        try {
            AtomicInteger reads = new AtomicInteger();
            BlockPos pos = new BlockPos(2, 3, 2);
            Map<BlockPos, BlockState> states = new HashMap<>();
            for (Direction side : Direction.Type.HORIZONTAL) {
                states.put(pos.offset(side), Blocks.STONE.getDefaultState());
            }
            SlabAnchorAttachment.clientPlacementDyLookup = queried -> {
                reads.incrementAndGet();
                return SlabAnchorAttachment.PlacementDyFact.fromStoredSixteenths(
                        (byte) (queried.equals(pos.north()) || queried.equals(pos.east()) ? -8 : 0));
            };
            int mask = SlabSupport.slabHeightStepFaceMask(view(states), pos, Blocks.STONE.getDefaultState(), 0.0d);
            ctx.assertTrue(mask == ((1 << Direction.NORTH.ordinal()) | (1 << Direction.EAST.ordinal())),
                    "cull mask omitted an exposed seam or included a flat/vertical face");
            ctx.assertTrue(reads.get() == 4, "mesh cull classification repeated a neighbor seat lookup");
            reads.set(0);
            states.put(pos.south(), Blocks.AIR.getDefaultState());
            SlabSupport.slabHeightStepFaceMask(view(states), pos, Blocks.STONE.getDefaultState(), 0.0d);
            ctx.assertTrue(reads.get() == 3, "air neighbors should not read chunk attachments");
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = oldFrozen;
            SlabAnchorAttachment.clientPlacementDyLookup = oldLookup;
            SlabAnchorAttachment.clientEffectivePlacementDyLookup = oldOverlay;
        }
        ctx.complete();
    }

    private static void assertOneSeat(TestContext ctx, BlockRenderView view, BlockPos pos,
                                      BlockState plant, double expected) {
        double seat = SlabSupport.getYOffset(view, pos, plant);
        ctx.assertTrue(seat == expected, "plant seat differs from expected placement height");
        ctx.assertTrue(plant.getModelOffset(view, pos).y + seat == expected,
                "plant model applied the vegetation seat twice");
        double baseMin = plant.getOutlineShape(EmptyBlockView.INSTANCE, pos, ShapeContext.absent()).getMin(Direction.Axis.Y);
        double actualMin = plant.getOutlineShape(view, pos, ShapeContext.absent()).getMin(Direction.Axis.Y);
        ctx.assertTrue(actualMin == baseMin + expected, "plant outline disagrees with its model seat");
    }

    private static BlockRenderView view(Map<BlockPos, BlockState> states) {
        return (BlockRenderView) Proxy.newProxyInstance(BlockRenderView.class.getClassLoader(),
                new Class<?>[]{BlockRenderView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockState")) {
                        return states.getOrDefault((BlockPos) args[0], Blocks.AIR.getDefaultState());
                    }
                    if (method.getName().equals("getFluidState")) {
                        return states.getOrDefault((BlockPos) args[0], Blocks.AIR.getDefaultState()).getFluidState();
                    }
                    if (method.getDeclaringClass().isInstance(EmptyBlockView.INSTANCE)) {
                        return method.invoke(EmptyBlockView.INSTANCE, args);
                    }
                    throw new AssertionError("unexpected render view access: " + method.getName());
                });
    }
}
