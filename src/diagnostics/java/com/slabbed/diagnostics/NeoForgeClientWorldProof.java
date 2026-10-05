package com.slabbed.diagnostics;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default-off NeoForge dev-client proof of the dy triad on a real integrated world: a full block placed
 * on a bottom slab through the real item-use path must be STORED lowered on the server, SYNCED lowered
 * to the client, and DRAWN, OUTLINED and TARGETED at that same height; its seam toward a flat neighbour
 * must keep its step face. Enabled by {@code -Dslabbed.neoforge.clientWorldProof=true} on the client
 * run; the class lives in the dev-only diagnostics mod and never ships.
 */
public final class NeoForgeClientWorldProof {
    private static final String ENABLE_PROPERTY = "slabbed.neoforge.clientWorldProof";
    private static final String TAG = "NF26_CLIENT_WORLD_PROOF";
    private static final int TITLE_SETTLE_TICKS = 100;
    private static final int CLIENT_SYNC_DELAY_TICKS = 80;
    private static final int MAX_TICKS = 3_600;
    private static final double EXPECTED_DY = -0.5d;
    private static final double EPSILON = 1.0e-6d;

    private static boolean initialized;
    private static boolean freshWorldRequested;
    private static boolean fixtureRequestQueued;
    private static boolean terminalLogged;
    private static int ticks;
    private static int loadFinishedTick = -1;
    private static int fixtureAuthoredTick;
    private static volatile ServerMeasurement serverMeasurement;
    private static volatile Throwable serverFailure;

    private NeoForgeClientWorldProof() {
    }

    /** Called from the diagnostics client entrypoint; a no-op unless the property is set. */
    public static void init() {
        if (initialized || !Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        initialized = true;
        Slabbed.LOGGER.info("[{}_START] enabled=true route=runClient diagnosticsOnly=true", TAG);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTickPost());
    }

    private static void onClientTickPost() {
        if (terminalLogged) {
            return;
        }
        ticks++;
        Minecraft client = Minecraft.getInstance();
        if (serverFailure != null) {
            logSummaryAndStop(client, "RED", "server_fixture_exception_" + serverFailure.getClass().getSimpleName()
                    + ":" + serverFailure.getMessage());
            return;
        }
        if (ticks > MAX_TICKS) {
            logSummaryAndStop(client, "RED", "timeout_waiting_for_client_world_or_sync");
            return;
        }
        if (client.level == null || client.player == null || !client.hasSingleplayerServer()) {
            maybeCreateFreshWorld(client);
            return;
        }
        if (!fixtureRequestQueued) {
            queueServerFixture(client);
            return;
        }
        ServerMeasurement measured = serverMeasurement;
        if (measured == null || ticks - fixtureAuthoredTick < CLIENT_SYNC_DELAY_TICKS) {
            return;
        }
        if (!measured.green()) {
            logRow(measured, null, null, null, "RED", measured.reason());
            logSummaryAndStop(client, "RED", measured.reason());
            return;
        }
        ClientMeasurement cm = measureClientState(client.level, measured);
        TriadMeasurement triad = measureTriad(client.level, measured.objectPos(), cm.objectState());
        ModelMeasurement model = measureModel(client, client.level, measured);
        String reason = mismatchReason(cm, triad, model);
        String result = reason == null ? "GREEN" : "RED";
        logRow(measured, cm, triad, model, result, reason == null ? "client_dy_triad_model_and_seam_match" : reason);
        logSummaryAndStop(client, result, reason == null ? "client_dy_triad_model_and_seam_match" : reason);
    }

    private static void maybeCreateFreshWorld(Minecraft client) {
        if (freshWorldRequested) {
            return;
        }
        if (!client.isGameLoadFinished()) {
            return;
        }
        if (loadFinishedTick < 0) {
            loadFinishedTick = ticks;
            Slabbed.LOGGER.info("[{}_WAIT] tick={} screen={}", TAG, ticks, screenName(client.gui.screen()));
            return;
        }
        if (ticks - loadFinishedTick < TITLE_SETTLE_TICKS) {
            return;
        }
        freshWorldRequested = true;
        String worldId = "slabbed-nf26-client-world-proof-" + Long.toHexString(System.currentTimeMillis());
        LevelSettings settings = new LevelSettings(
                "Slabbed NeoForge Client World Proof",
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                true,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(0L, false, false);
        Slabbed.LOGGER.info("[{}_WORLD_START] worldId={} screen={}", TAG, worldId, screenName(client.gui.screen()));
        try {
            Screen lastScreen = client.gui.screen();
            client.createWorldOpenFlows().createFreshLevel(worldId, settings, options,
                    NeoForgeClientWorldProof::flatDimensions, lastScreen);
        } catch (RuntimeException e) {
            serverFailure = e;
        }
    }

    private static WorldDimensions flatDimensions(HolderLookup.Provider provider) {
        return provider.lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(WorldPresets.FLAT)
                .value()
                .createWorldDimensions();
    }

    private static void queueServerFixture(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        fixtureRequestQueued = true;
        server.executeIfPossible(() -> {
            try {
                ServerLevel world = server.overworld();
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    serverFailure = new IllegalStateException("no server player");
                    return;
                }
                ServerPlayer player = players.get(0);
                BlockPos supportPos = chooseSupportPos(world, player);
                BlockPos objectPos = supportPos.above();
                BlockPos flatSupportPos = supportPos.east();
                BlockPos flatObjectPos = objectPos.east();
                clearProofVolume(world, supportPos);

                BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                world.setBlock(supportPos, slab, Block.UPDATE_ALL);
                world.setBlock(flatSupportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                // The REAL placement path: the player uses a stone item on the top face of each support.
                ItemStack stone = new ItemStack(Items.STONE, 64);
                player.setItemInHand(InteractionHand.MAIN_HAND, stone);
                useOnTop(player, stone, supportPos, 0.5d);
                useOnTop(player, stone, flatSupportPos, 1.0d);

                BlockState measuredSupport = world.getBlockState(supportPos);
                BlockState measuredObject = world.getBlockState(objectPos);
                BlockState measuredFlatObject = world.getBlockState(flatObjectPos);
                double serverDy = SlabSupport.getYOffset(world, objectPos, measuredObject);
                double storedDy = SlabAnchorAttachment.storedPlacementDy(world, objectPos);
                double flatServerDy = SlabSupport.getYOffset(world, flatObjectPos, measuredFlatObject);
                double flatStoredDy = SlabAnchorAttachment.storedPlacementDy(world, flatObjectPos);
                boolean anchored = SlabAnchorAttachment.isAnchored(world, objectPos);
                String reason = null;
                if (!measuredSupport.is(Blocks.STONE_SLAB)) {
                    reason = "server_support_not_slab";
                } else if (!measuredObject.is(Blocks.STONE)) {
                    reason = "server_object_not_placed:" + measuredObject;
                } else if (!measuredFlatObject.is(Blocks.STONE)) {
                    reason = "server_flat_object_not_placed:" + measuredFlatObject;
                } else if (!dyMatches(serverDy)) {
                    reason = "server_live_dy_not_lowered";
                } else if (!dyMatches(storedDy)) {
                    reason = "server_stored_dy_not_lowered";
                } else if (!dyZero(flatServerDy) || !dyZero(flatStoredDy)) {
                    reason = "server_flat_neighbor_not_flat";
                } else if (!anchored) {
                    reason = "server_object_not_anchored";
                }
                serverMeasurement = new ServerMeasurement(supportPos, objectPos, flatObjectPos, measuredSupport,
                        measuredObject, measuredFlatObject, anchored, serverDy, storedDy, flatServerDy, flatStoredDy,
                        reason == null, reason == null ? "server_green" : reason);
                fixtureAuthoredTick = ticks;
                // Park the player facing the fixture so the section is rendered too.
                player.teleportTo(objectPos.getX() + 0.5d, objectPos.getY() + 0.5d, objectPos.getZ() + 4.5d);
                player.setYRot(180.0f);
                player.setXRot(10.0f);
            } catch (RuntimeException e) {
                serverFailure = e;
            }
        });
    }

    private static void useOnTop(ServerPlayer player, ItemStack stack, BlockPos clicked, double topHeight) {
        Vec3 hit = Vec3.atLowerCornerOf(clicked).add(0.5d, topHeight, 0.5d);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, clicked, false)));
    }

    private static BlockPos chooseSupportPos(ServerLevel world, ServerPlayer player) {
        BlockPos preferred = player.blockPosition().offset(4, 0, 4);
        for (int up = 0; up < 8; up++) {
            BlockPos candidate = preferred.above(up);
            if (world.getBlockState(candidate).isAir() && world.getBlockState(candidate.above()).isAir()) {
                return candidate.immutable();
            }
        }
        return preferred.above(4).immutable();
    }

    private static void clearProofVolume(ServerLevel world, BlockPos supportPos) {
        for (int dx = -2; dx <= 3; dx++) {
            for (int dz = -3; dz <= 6; dz++) {
                for (int y = -1; y <= 3; y++) {
                    BlockPos p = supportPos.offset(dx, y, dz);
                    if (y == -1) {
                        world.setBlock(p, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                    } else {
                        world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static ClientMeasurement measureClientState(ClientLevel level, ServerMeasurement m) {
        BlockState support = level.getBlockState(m.supportPos());
        BlockState object = level.getBlockState(m.objectPos());
        BlockState flat = level.getBlockState(m.flatObjectPos());
        return new ClientMeasurement(support, object, flat,
                SlabAnchorAttachment.isAnchored(level, m.objectPos()),
                SlabAnchorAttachment.storedPlacementDy(level, m.objectPos()),
                ClientDy.dyFor(level, m.objectPos(), object),
                ClientDy.dyFor(level, m.flatObjectPos(), flat));
    }

    private static TriadMeasurement measureTriad(ClientLevel level, BlockPos objectPos, BlockState objectState) {
        if (objectState == null || objectState.isAir()) {
            return TriadMeasurement.missing("client_object_missing");
        }
        double modelDy = OffsetBlockStateModel.appliedModelDy(level, objectPos, objectState);
        VoxelShape outline = objectState.getShape(level, objectPos, CollisionContext.empty());
        AABB box = outline.isEmpty() ? null : outline.bounds();
        double outlineDy = box == null ? Double.NaN : box.minY;
        Vec3 visualCenter = Vec3.atLowerCornerOf(objectPos).add(0.5d, modelDy + 0.5d, 0.5d);
        Vec3 start = visualCenter.add(0.0d, 0.0d, 3.0d);
        Vec3 end = visualCenter.add(0.0d, 0.0d, -3.0d);
        BlockHitResult outlineHit = outline.clip(start, end, objectPos);
        BlockHitResult finalHit = SlabbedOffsetRaycast.raycast(level, start, end, CollisionContext.empty());
        String outlineOwner = ownerOf(outlineHit);
        String finalOwner = ownerOf(finalHit);
        double targetDy = "MISS".equals(finalOwner) ? Double.NaN
                : ClientDy.dyFor(level, finalHit.getBlockPos(), level.getBlockState(finalHit.getBlockPos()));
        boolean green = dyMatches(modelDy) && dyMatches(outlineDy) && dyMatches(targetDy)
                && formatPos(objectPos).equals(outlineOwner) && formatPos(objectPos).equals(finalOwner);
        return new TriadMeasurement(modelDy, outlineDy, targetDy, box == null ? "empty" : formatBox(box),
                outlineOwner, finalOwner, finalHit == null ? "null" : finalHit.getType().name(),
                finalHit == null ? "none" : formatVec(finalHit.getLocation()), green);
    }

    /**
     * The model layer itself: the wrapped block-state model's parts for the lowered object must carry
     * vertices moved by dy, with the east seam quads (toward the flat neighbour) kept in the unculled
     * bucket; the flat neighbour's west seam quads likewise, while its east face (toward air at the same
     * height) stays in its own cull bucket.
     */
    private static ModelMeasurement measureModel(Minecraft client, ClientLevel level, ServerMeasurement m) {
        BlockState object = level.getBlockState(m.objectPos());
        BlockState flat = level.getBlockState(m.flatObjectPos());
        BlockStateModel objectModel = client.getModelManager().getBlockStateModelSet().get(object);
        BlockStateModel flatModel = client.getModelManager().getBlockStateModelSet().get(flat);
        boolean wrapped = objectModel instanceof OffsetBlockStateModel && flatModel instanceof OffsetBlockStateModel;
        List<BlockStateModelPart> objectParts = new ArrayList<>();
        List<BlockStateModelPart> flatParts = new ArrayList<>();
        objectModel.collectParts(level, m.objectPos(), object, RandomSource.create(42L), objectParts);
        flatModel.collectParts(level, m.flatObjectPos(), flat, RandomSource.create(42L), flatParts);
        double objectMinY = minVertexY(objectParts);
        double flatMinY = minVertexY(flatParts);
        int objectEastCulled = count(objectParts, Direction.EAST, Direction.EAST);
        int objectEastUnculled = count(objectParts, null, Direction.EAST);
        int flatWestCulled = count(flatParts, Direction.WEST, Direction.WEST);
        int flatWestUnculled = count(flatParts, null, Direction.WEST);
        int flatEastCulled = count(flatParts, Direction.EAST, Direction.EAST);
        boolean green = wrapped
                && dyMatches(objectMinY) && dyZero(flatMinY)
                && objectEastCulled == 0 && objectEastUnculled > 0
                && flatWestCulled == 0 && flatWestUnculled > 0
                && flatEastCulled > 0;
        return new ModelMeasurement(objectModel.getClass().getName(), wrapped, objectMinY, flatMinY,
                objectEastCulled, objectEastUnculled, flatWestCulled, flatWestUnculled, flatEastCulled, green);
    }

    private static double minVertexY(List<BlockStateModelPart> parts) {
        double min = Double.POSITIVE_INFINITY;
        for (BlockStateModelPart part : parts) {
            for (Direction d : Direction.values()) {
                min = Math.min(min, minQuadY(part.getQuads(d)));
            }
            min = Math.min(min, minQuadY(part.getQuads(null)));
        }
        return min;
    }

    private static double minQuadY(List<BakedQuad> quads) {
        double min = Double.POSITIVE_INFINITY;
        for (BakedQuad quad : quads) {
            for (int i = 0; i < 4; i++) {
                min = Math.min(min, quad.position(i).y());
            }
        }
        return min;
    }

    private static int count(List<BlockStateModelPart> parts, Direction bucket, Direction facing) {
        int n = 0;
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(bucket)) {
                if (quad.direction() == facing) {
                    n++;
                }
            }
        }
        return n;
    }

    private static String mismatchReason(ClientMeasurement cm, TriadMeasurement triad, ModelMeasurement model) {
        if (!cm.supportState().is(Blocks.STONE_SLAB)) {
            return "client_support_state_not_synced";
        }
        if (!cm.objectState().is(Blocks.STONE) || !cm.flatObjectState().is(Blocks.STONE)) {
            return "client_object_state_not_synced";
        }
        if (!dyMatches(cm.clientStoredDy())) {
            return "client_stored_dy_not_synced";
        }
        if (!cm.clientAnchor()) {
            return "client_anchor_not_synced";
        }
        if (!dyMatches(cm.clientDy())) {
            return "client_dy_not_lowered";
        }
        if (!dyZero(cm.flatClientDy())) {
            return "client_flat_neighbor_not_flat";
        }
        if (!triad.green()) {
            return "client_visual_triad_not_aligned";
        }
        if (!model.wrapped()) {
            return "model_not_wrapped";
        }
        if (!model.green()) {
            return "model_vertices_or_seam_buckets_mismatch";
        }
        return null;
    }

    private static void logRow(ServerMeasurement s, ClientMeasurement c, TriadMeasurement t, ModelMeasurement m,
                               String result, String reason) {
        Slabbed.LOGGER.info(
                "[{}_ROW] scenario=DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK supportPos={} objectPos={} flatPos={} "
                        + "serverSupport={} serverObject={} serverFlat={} serverAnchor={} serverDy={} serverStoredDy={} "
                        + "serverFlatDy={} serverFlatStoredDy={} clientSupport={} clientObject={} clientAnchor={} "
                        + "clientStoredDy={} clientDy={} clientFlatDy={} modelDy={} outlineDy={} targetDy={} outlineBox={} "
                        + "outlineOwner={} finalOwner={} finalHitType={} finalHitVec={} modelClass={} modelWrapped={} "
                        + "objectMinVertexY={} flatMinVertexY={} objectEastCulled={} objectEastUnculled={} "
                        + "flatWestCulled={} flatWestUnculled={} flatEastCulled={} result={} reason={}",
                TAG, formatPos(s.supportPos()), formatPos(s.objectPos()), formatPos(s.flatObjectPos()),
                s.supportState(), s.objectState(), s.flatObjectState(), s.serverAnchor(), s.serverDy(),
                s.serverStoredDy(), s.flatServerDy(), s.flatStoredDy(),
                c == null ? "n/a" : c.supportState(), c == null ? "n/a" : c.objectState(),
                c == null ? "n/a" : c.clientAnchor(), c == null ? "n/a" : c.clientStoredDy(),
                c == null ? "n/a" : c.clientDy(), c == null ? "n/a" : c.flatClientDy(),
                t == null ? "n/a" : t.modelDy(), t == null ? "n/a" : t.outlineDy(), t == null ? "n/a" : t.targetDy(),
                t == null ? "n/a" : t.outlineBox(), t == null ? "n/a" : t.outlineOwner(),
                t == null ? "n/a" : t.finalOwner(), t == null ? "n/a" : t.finalHitType(),
                t == null ? "n/a" : t.finalHitVec(),
                m == null ? "n/a" : m.modelClass(), m == null ? "n/a" : m.wrapped(),
                m == null ? "n/a" : m.objectMinY(), m == null ? "n/a" : m.flatMinY(),
                m == null ? "n/a" : m.objectEastCulled(), m == null ? "n/a" : m.objectEastUnculled(),
                m == null ? "n/a" : m.flatWestCulled(), m == null ? "n/a" : m.flatWestUnculled(),
                m == null ? "n/a" : m.flatEastCulled(), result, reason);
    }

    private static void logSummaryAndStop(Minecraft client, String result, String reason) {
        if (terminalLogged) {
            return;
        }
        terminalLogged = true;
        Slabbed.LOGGER.info("[{}_SUMMARY] result={} reason={} rows=1 green={} red={} diagnosticsOnly=true releaseReady=false",
                TAG, result, reason, "GREEN".equals(result) ? 1 : 0, "GREEN".equals(result) ? 0 : 1);
        // Leave the frame on screen briefly so a screenshot of the rendered seam can be taken.
        if (Boolean.getBoolean("slabbed.neoforge.clientWorldProof.keepOpen")) {
            return;
        }
        client.stop();
    }

    private static boolean dyMatches(double dy) {
        return Math.abs(dy - EXPECTED_DY) <= EPSILON;
    }

    private static boolean dyZero(double dy) {
        return Math.abs(dy) <= EPSILON;
    }

    private static String screenName(Screen screen) {
        return screen == null ? "none" : screen.getClass().getName();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String ownerOf(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return "MISS";
        }
        return formatPos(blockHit.getBlockPos());
    }

    private static String formatBox(AABB box) {
        return String.format(Locale.ROOT, "[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private record ServerMeasurement(BlockPos supportPos, BlockPos objectPos, BlockPos flatObjectPos,
                                     BlockState supportState, BlockState objectState, BlockState flatObjectState,
                                     boolean serverAnchor, double serverDy, double serverStoredDy,
                                     double flatServerDy, double flatStoredDy, boolean green, String reason) {
    }

    private record ClientMeasurement(BlockState supportState, BlockState objectState, BlockState flatObjectState,
                                     boolean clientAnchor, double clientStoredDy, double clientDy, double flatClientDy) {
    }

    private record TriadMeasurement(double modelDy, double outlineDy, double targetDy, String outlineBox,
                                    String outlineOwner, String finalOwner, String finalHitType, String finalHitVec,
                                    boolean green) {
        static TriadMeasurement missing(String reason) {
            return new TriadMeasurement(Double.NaN, Double.NaN, Double.NaN, reason, "MISS", "MISS", "MISS", "none", false);
        }
    }

    private record ModelMeasurement(String modelClass, boolean wrapped, double objectMinY, double flatMinY,
                                    int objectEastCulled, int objectEastUnculled, int flatWestCulled,
                                    int flatWestUnculled, int flatEastCulled, boolean green) {
    }
}
