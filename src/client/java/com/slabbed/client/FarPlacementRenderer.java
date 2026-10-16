package com.slabbed.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;

/** Draws stored bodies outside chunk-mesh bounds through native floating-point world buffers (LAW.md). */
public final class FarPlacementRenderer {
    private static final LongOpenHashSet POSITIONS = new LongOpenHashSet();
    private static final LongOpenHashSet PENDING = new LongOpenHashSet();
    private static final Random RANDOM = Random.create();

    private FarPlacementRenderer() {}

    public static boolean requiresWorldPass(double dy) {
        // Compact chunk vertices allow [-8,24); a native cell already occupies [0,16].
        return dy <= -8.0 || dy >= 8.0;
    }

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(FarPlacementRenderer::render);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clear());
    }

    static void updateChunk(WorldChunk chunk) {
        var map = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        if (map != null) {
            for (var entry : map.long2IntEntrySet()) {
                if (requiresWorldPass(entry.getIntValue() / 16.0)) POSITIONS.add(entry.getLongKey());
            }
        }
    }

    static void refreshPosition(BlockPos pos) { PENDING.add(pos.asLong()); }

    static void removeChunk(long key) {
        POSITIONS.removeIf((long packed) -> net.minecraft.util.math.ChunkPos.toLong(
                BlockPos.unpackLongX(packed) >> 4, BlockPos.unpackLongZ(packed) >> 4) == key);
        PENDING.removeIf((long packed) -> net.minecraft.util.math.ChunkPos.toLong(
                BlockPos.unpackLongX(packed) >> 4, BlockPos.unpackLongZ(packed) >> 4) == key);
    }

    static void clear() { POSITIONS.clear(); PENDING.clear(); }

    private static void render(WorldRenderContext context) {
        var world = context.world();
        var matrices = context.matrixStack();
        var consumers = context.consumers();
        if (world == null || matrices == null || consumers == null) return;
        POSITIONS.addAll(PENDING);
        PENDING.clear();
        if (POSITIONS.isEmpty()) return;
        var client = MinecraftClient.getInstance();
        var camera = context.camera().getPos();
        double distance = client.options.getViewDistance().getValue() * 16.0;
        double distanceSquared = distance * distance;
        var manager = client.getBlockRenderManager();
        var positions = POSITIONS.iterator();
        while (positions.hasNext()) {
            BlockPos pos = BlockPos.fromLong(positions.nextLong());
            var chunk = SlabAnchorAttachment.loadedAttachmentChunk(world, pos);
            if (chunk == null) { positions.remove(); continue; }
            var state = chunk.getBlockState(pos);
            if (state.getRenderType() != BlockRenderType.MODEL) { positions.remove(); continue; }
            double dy = SlabSupport.getYOffset(world, pos, state);
            if (!requiresWorldPass(dy)) { positions.remove(); continue; }
            Box body = new Box(pos).offset(0, dy, 0).expand(1);
            if (body.squaredMagnitude(camera) > distanceSquared
                    || (context.frustum() != null && !context.frustum().isVisible(body))) continue;
            matrices.push();
            try {
                matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                manager.renderBlock(state, pos, world, matrices,
                        consumers.getBuffer(RenderLayers.getEntityBlockLayer(state, false)), false, RANDOM);
            } finally { matrices.pop(); }
        }
    }
}
