package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Chunk queries must neither generate terrain nor cross from server views into client state. */
public final class ChunkAccessSafetyTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void unloadedAttachmentReadsAndRemovalsNeverLoadChunks(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos pos = new BlockPos(12_000_000, 80, -12_000_000);
        BlockState slab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        ctx.assertTrue(SlabAnchorAttachment.loadedAttachmentChunk(world, pos) == null,
                "the distant test chunk must start unloaded");
        ctx.assertTrue(!SlabAnchorAttachment.isModernPlacement(world, pos)
                        && !SlabAnchorAttachment.rawPlacementDyFact(world, pos).present()
                        && !SlabAnchorAttachment.isAnchored(world, pos)
                        && !SlabAnchorAttachment.isFrozenFlat(world, pos)
                        && !SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                        && !SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, slab)
                        && !SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos,
                                slab.with(SlabBlock.TYPE, SlabType.TOP))
                        && !SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos,
                                slab.with(SlabBlock.TYPE, SlabType.DOUBLE))
                        && !SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos,
                                slab.with(SlabBlock.TYPE, SlabType.TOP))
                        && !SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, slab)
                        && !SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, slab),
                "unloaded chunks must answer absent without requesting generation");
        SlabAnchorAttachment.removeAnchor(world, pos);
        SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
        ctx.assertTrue(SlabAnchorAttachment.loadedAttachmentChunk(world, pos) == null,
                "attachment reads and no-op removals loaded a distant chunk");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void generationAndEmptyViewsNeverConsultClientBridges(TestContext ctx) throws Exception {
        Map<Field, Object> previous = poisonClientLookups();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            BlockPos pos = BlockPos.ORIGIN;
            BlockState stone = Blocks.STONE.getDefaultState();
            for (BlockView view : new BlockView[]{EmptyBlockView.INSTANCE, generationView()}) {
                ctx.assertTrue(!SlabAnchorAttachment.usesFrozenPlacementHeight(view, pos)
                                && !SlabAnchorAttachment.isAnchored(view, pos)
                                && !SlabAnchorAttachment.isFrozenFlat(view, pos)
                                && !SlabAnchorAttachment.rawPlacementDyFact(view, pos).present()
                                && Double.isNaN(SlabAnchorAttachment.storedPlacementDy(view, pos))
                                && SlabSupport.getYOffset(view, pos, stone) == 0.0d
                                && SlabSupport.getUnstoredYOffset(view, pos, stone) == 0.0d,
                        "non-render views must retain base geometry without any client lookup");
                ctx.assertTrue(stone.getCollisionShape(view, pos, ShapeContext.absent()).getMax(
                                net.minecraft.util.math.Direction.Axis.Y) == 1.0d
                                && stone.getOutlineShape(view, pos).getMax(
                                net.minecraft.util.math.Direction.Axis.Y) == 1.0d,
                        "generation and shape-cache queries must keep vanilla stone geometry");
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
            restoreClientLookups(previous);
        }
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void arbitrarilyNamedServerWorkerDoesNotWaitForMainThread(TestContext ctx) throws Exception {
        ServerWorld world = ctx.getWorld();
        BlockPos loaded = ctx.getAbsolutePos(BlockPos.ORIGIN);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                BlockState stone = Blocks.STONE.getDefaultState();
                BlockState slab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
                if (SlabAnchorAttachment.loadedAttachmentChunk(world, loaded) != null
                        || SlabAnchorAttachment.isModernPlacement(world, loaded)
                        || SlabAnchorAttachment.rawPlacementDyFact(world, loaded).present()
                        || SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, loaded, slab)
                        || SlabSupport.getYOffset(world, loaded, stone) != 0.0d
                        || SlabSupport.getUnstoredYOffset(world, loaded, stone) != 0.0d) {
                    throw new AssertionError("a server worker entered the live placement/chunk path");
                }
                stone.getCollisionShape(world, loaded, ShapeContext.absent());
                stone.getOutlineShape(world, loaded);
                stone.getRaycastShape(world, loaded);
                SlabAnchorAttachment.removeAnchor(world, loaded);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "C2ME-terrain-query");
        worker.setDaemon(true);
        worker.start();
        // Deliberately keep the server task queue paused: a chunk future join cannot finish here.
        worker.join(2_000L);
        ctx.assertTrue(!worker.isAlive(), "server worker blocked waiting for the main thread");
        ctx.assertTrue(failure.get() == null, "unsafe worker query: " + failure.get());
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loadedFactsAndPublishedMarkerSnapshotsRemainExact(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
        world.setBlockState(pos.down(), Blocks.STONE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, pos, world.getBlockState(pos));
        SlabAnchorAttachment.markPostPolicyPlacements(world, java.util.List.of(pos));
        SlabAnchorAttachment.writePlacementDyBatch(world,
                Map.of(pos, Double.doubleToRawLongBits(-0.5d)));
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        LongOpenHashSet markers = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
        ctx.assertTrue(markers != null && markers.contains(pos.asLong()), "missing anchor fixture");
        SlabAnchorAttachment.addAnchor(world, pos, world.getBlockState(pos));
        ctx.assertTrue(chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE) == markers,
                "an unchanged marker write must keep the published snapshot");
        world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, pos, world.getBlockState(pos)) == -0.5d,
                "loaded placement height changed when its support was removed (LAW.md)");
        SlabAnchorAttachment.removeAnchor(world, pos);
        ctx.assertTrue(markers.contains(pos.asLong()) && !SlabAnchorAttachment.isAnchored(world, pos)
                        && !SlabAnchorAttachment.rawPlacementDyFact(world, pos).present(),
                "removal must clear current facts without mutating a mesh worker's old snapshot");
        ctx.complete();
    }

    public static BlockRenderView renderView() {
        return boundedView(BlockRenderView.class);
    }

    private static WorldView generationView() {
        return boundedView(WorldView.class);
    }

    private static <T extends BlockView> T boundedView(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getName().equals("isClient")) {
                        return false;
                    }
                    throw new AssertionError("unexpected world access: " + method.getName());
                }));
    }

    private static Map<Field, Object> poisonClientLookups() throws IllegalAccessException {
        Map<Field, Object> previous = new LinkedHashMap<>();
        for (Field field : SlabAnchorAttachment.class.getFields()) {
            if (!field.getName().startsWith("client") || !field.getName().endsWith("Lookup")) {
                continue;
            }
            previous.put(field, field.get(null));
            if (field.getType() == Predicate.class) {
                field.set(null, (Predicate<BlockPos>) pos -> {
                    throw new AssertionError("server/empty view entered " + field.getName());
                });
            } else if (field.getType() == SlabAnchorAttachment.ClientPlacementDyFactLookup.class) {
                field.set(null, (SlabAnchorAttachment.ClientPlacementDyFactLookup) pos -> {
                    throw new AssertionError("server/empty view entered " + field.getName());
                });
            }
        }
        return previous;
    }

    private static void restoreClientLookups(Map<Field, Object> previous) throws IllegalAccessException {
        for (Map.Entry<Field, Object> entry : previous.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }
}
