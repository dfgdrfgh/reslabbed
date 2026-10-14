package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.world.BlockRenderView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Uses real item placement, synced heights and client-baked renderer quads (LAW.md). */
public final class VerticalStepClientProbe implements ClientModInitializer {
    private static final BlockPos GROUND = new BlockPos(8, 198, 8);
    private static final Map<String, MeshFace> MESH_FACES = new ConcurrentHashMap<>();
    private record MeshFace(float minY, float maxY, Direction cull, boolean visible, String view) {}
    public static void recordMeshedQuad(BlockRenderView view, BlockPos pos, MutableQuadView quad, RenderContext context) {
        if (Math.abs(pos.getX() - GROUND.getX()) > 12 || Math.abs(pos.getZ() - GROUND.getZ()) > 12
                || pos.getY() < 198 || pos.getY() > 212) return;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) { minY = Math.min(minY, quad.y(i)); maxY = Math.max(maxY, quad.y(i)); }
        MESH_FACES.put(pos.asLong() + ":" + quad.nominalFace(), new MeshFace(minY, maxY,
                quad.cullFace(), !context.isFaceCulled(quad.cullFace()), view.getClass().getName()));
    }
    private static void checkMeshedFace(BlockPos pos, Direction face, double plane, boolean exposed) {
        MeshFace actual = MESH_FACES.get(pos.asLong() + ":" + face);
        System.out.println("SECTION_MESH_FACE pos=" + pos + " face=" + face + " actual=" + actual);
        require(actual != null, "Actual section mesh omitted watched face " + pos + " " + face);
        require(!actual.view.contains("ClientWorld"), "Expected a real section render view");
        require(Math.abs(actual.minY - plane) < 1.0e-6 && Math.abs(actual.maxY - plane) < 1.0e-6,
                "Section mesh used stale height: " + actual);
        if (exposed) require(actual.visible && actual.cull == null, "Actual section mesh hid exposed face: " + actual);
    }
    private static final BlockPos EDGE = new BlockPos(15, 205, 8);
    private static final BlockPos NATURAL_BASE = new BlockPos(12, 191, 12);
    private it.unimi.dsi.fastutil.longs.LongOpenHashSet terrainMarkers;
    private Long2ByteOpenHashMap edgeFacts;
    private int ticks, phase, started, reloads;
    private boolean requested;
    private volatile boolean prepared;
    private volatile Throwable failure;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("slabbed.verticalStepProbe")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try { tick(client); }
            catch (Throwable error) { error.printStackTrace(); System.exit(1); }
        });
    }

    private void tick(MinecraftClient client) throws Exception {
        if (phase == 99) return;
        require(++ticks < 2400, "Client face proof timed out");
        if (failure != null) throw new AssertionError("Server scene failed", failure);
        if (!requested && client.isFinishedLoading()) {
            require(client.world == null, "Client probe requires an unopened world");
            requested = true;
            client.createIntegratedServerLoader().createAndStart("vertical-step-proof",
                    new LevelInfo("Vertical Step Proof", GameMode.CREATIVE, false,
                            Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(0L, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                            .createDimensionsRegistryHolder(), null);
            return;
        }
        if (client.world == null || client.player == null || client.getServer() == null) return;
        client.options.pauseOnLostFocus = false;
        client.options.hudHidden = true;
        if (client.currentScreen != null) client.setScreen(null);
        client.player.getAbilities().flying = true;
        client.player.setVelocity(Vec3d.ZERO);
        client.player.refreshPositionAndAngles(8.5, 201.2 - client.player.getStandingEyeHeight(), 3.0, 0, 8);
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        if (phase == 0) {
            phase = 1; started = ticks;
            client.getServer().execute(() -> {
                try {
                    ServerWorld world = client.getServer().getOverworld();
                    ServerPlayerEntity player = world.getPlayers().getFirst();
                    player.setPosition(12.5, 198, 8.5);
                    world.setBlockState(GROUND, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    for (int i = 0; i < 3; i++) place(world, player, Items.RED_CONCRETE, GROUND.up(i));
                    long original = SlabAnchorAttachment.rawPlacementDyFact(world, GROUND.up(3)).rawBits();
                    world.breakBlock(GROUND.up(), false);
                    world.breakBlock(GROUND.up(2), false);
                    place(world, player, Items.STONE_SLAB, GROUND);
                    place(world, player, Items.RED_CONCRETE, GROUND.up());
                    require(original == SlabAnchorAttachment.rawPlacementDyFact(world, GROUND.up(3)).rawBits(),
                            "Rebuilding moved the floating block");
                    BlockPos control = GROUND.east(4);
                    world.setBlockState(control, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    player.setPosition(16.5, 198, 8.5);
                    place(world, player, Items.STONE_SLAB, control);
                    place(world, player, Items.GRAY_CONCRETE, control.up());
                    place(world, player, Items.GRAY_CONCRETE, control.up(2));
                    world.setBlockState(EDGE, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    player.setPosition(18.5, 205, 8.5);
                    for (int i = 0; i < 3; i++) place(world, player, Items.OAK_LOG, EDGE.up(i));
                    world.breakBlock(EDGE.up(), false); world.breakBlock(EDGE.up(2), false);
                    place(world, player, Items.STONE_SLAB, EDGE);
                    place(world, player, Items.OAK_LOG, EDGE.up());
                    BlockPos predict = GROUND.north(2);
                    world.setBlockState(predict, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    player.setPosition(11.5, 198, 6.5);
                    place(world, player, Items.STONE_SLAB, predict);
                    place(world, player, Items.OAK_LOG, predict.up());
                    BlockPos natural = GROUND.west(4);
                    world.setBlockState(natural, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    for (int x : new int[]{-1, 1}) for (int y = 1; y <= 4; y++)
                        world.setBlockState(natural.add(x, y, 0), Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
                    for (int i = 1; i <= 3; i++) world.setBlockState(natural.up(i), Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
                    world.breakBlock(natural.up(), false);
                    world.breakBlock(natural.up(2), false);
                    player.setPosition(natural.getX() + 3.5, 198, natural.getZ() + 0.5);
                    place(world, player, Items.STONE_SLAB, natural);
                    place(world, player, Items.OAK_LOG, natural.up());
                    BlockPos carpet = GROUND.south(4);
                    world.setBlockState(carpet, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    player.setPosition(carpet.getX() + 3.5, 198, carpet.getZ() + 0.5);
                    place(world, player, Items.STONE_SLAB, carpet);
                    place(world, player, Items.OAK_LOG, carpet.up());
                    world.setBlockState(carpet.up(3), Blocks.RED_CARPET.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(NATURAL_BASE, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    player.setPosition(15.5, 191, 12.5);
                    place(world, player, terrainSlab().asItem(), NATURAL_BASE);
                    for (int y = 2; y <= SlabSupport.SUPPORT_COLUMN_DEPTH + 1; y++)
                        world.setBlockState(NATURAL_BASE.up(y), Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_ALL);
                    player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                    prepared = true;
                } catch (Throwable error) { failure = error; }
            });
            return;
        }
        if (!prepared || ticks - started < 100) return;
        if (phase == 1) { phase = 2; started = ticks; return; }
        if (ticks - started < 80) return;
        if (phase == 2) {
            checkMeshedFace(GROUND.up(2), Direction.UP, 0.5, true);
            BlockPos natural = GROUND.west(4);
            double lowerDy = SlabSupport.getYOffset(client.world, natural.up(2), client.world.getBlockState(natural.up(2)));
            double upperDy = SlabSupport.getYOffset(client.world, natural.up(3), client.world.getBlockState(natural.up(3)));
            System.out.println("NATURAL_LOG_HEIGHTS lower=" + lowerDy + " upper=" + upperDy);
            require(upperDy == 0, "Natural trunk must retain its original grid seat");
            checkMeshedFace(natural.up(2), Direction.UP, 1 + lowerDy, lowerDy < upperDy);
            checkMeshedFace(natural.up(3), Direction.DOWN, upperDy, lowerDy < upperDy);
            checkMeshedFace(GROUND.south(4).up(3), Direction.UP, -0.5 + 1.0 / 16, false);
            if (reloads++ < 2) {
                MESH_FACES.clear(); client.worldRenderer.reload(); started = ticks; return;
            }
            System.out.println("REPEATED_SECTION_RELOAD_PASS count=2");
            checkMeshedFace(EDGE.up(3), Direction.DOWN, 0, true);
            var chunk = client.world.getWorldChunk(EDGE.up(2));
            edgeFacts = new Long2ByteOpenHashMap(chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE));
            var delayed = new Long2ByteOpenHashMap(edgeFacts); delayed.put(EDGE.up(2).asLong(), (byte)0);
            chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, delayed);
            MESH_FACES.clear(); client.worldRenderer.reload(); phase = 3; started = ticks; return;
        }
        if (phase == 3) {
            MeshFace hidden = MESH_FACES.get(EDGE.up(3).asLong() + ":" + Direction.DOWN);
            require(hidden != null && hidden.cull != null && !hidden.visible,
                    "Delayed-height control must hide the ceiling face");
            MESH_FACES.remove(EDGE.up(3).asLong() + ":" + Direction.DOWN);
            client.world.getWorldChunk(EDGE.up(2)).setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, edgeFacts);
            phase = 4; started = ticks; return;
        }
        if (phase == 4) {
            checkMeshedFace(EDGE.up(3), Direction.DOWN, 0, true);
            System.out.println("CROSS_SECTION_HEIGHT_SYNC_PASS");
            BlockPos owner = GROUND.north(2).up(2);
            ItemStack slab = new ItemStack(terrainSlab().asItem(), 16);
            client.player.setStackInHand(Hand.MAIN_HAND, slab);
            client.interactionManager.clickCreativeStack(slab.copy(), 36 + client.player.getInventory().selectedSlot);
            var hit = new BlockHitResult(new Vec3d(owner.getX() + .5, owner.getY() + .5, owner.getZ() + .5),
                    Direction.UP, owner, false);
            require(client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit).isAccepted(),
                    "Client slab prediction failed");
            requirePredictedSlab(client, owner.up());
            System.out.println("IMMEDIATE_SLAB_PREDICTION_PASS");
            phase = 5; started = ticks; return;
        }
        requirePredictedSlab(client, GROUND.north(2).up(3));
        if (phase == 5) System.out.println("CONFIRMED_SLAB_PREDICTION_PASS");
        BlockPos predicted = GROUND.north(2).up(3);
        var outline = client.world.getBlockState(predicted).getOutlineShape(client.world, predicted);
        require(outline.getMin(Direction.Axis.Y) == -.5 && outline.getMax(Direction.Axis.Y) == 0,
                "Terrain slab outline contains geometry outside its visible half slab");
        checkMeshedFace(predicted, Direction.DOWN, -.5, false);
        checkSlabTarget(client, predicted, -.25, true);
        checkSlabTarget(client, predicted, .25, false);
        if (phase == 5) {
            MESH_FACES.clear(); client.worldRenderer.reload();
            phase = 6; started = ticks; return;
        }
        if (phase == 6) {
            System.out.println("WEIGHTED_TERRAIN_SLAB_MODEL_OUTLINE_RELOAD_PASS");
            checkMeshedFace(NATURAL_BASE.up(17), Direction.DOWN, 0, false);
            var chunk = client.world.getWorldChunk(NATURAL_BASE.up());
            terrainMarkers = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(
                    chunk.getAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE));
            var delayed = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(terrainMarkers);
            delayed.remove(NATURAL_BASE.up().asLong());
            chunk.setAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE, delayed);
            MESH_FACES.clear(); client.worldRenderer.reload();
            phase = 7; started = ticks; return;
        }
        if (phase == 7) {
            checkMeshedFace(NATURAL_BASE.up(17), Direction.DOWN, -.5, false);
            MESH_FACES.remove(NATURAL_BASE.up(17).asLong() + ":" + Direction.DOWN);
            client.world.getWorldChunk(NATURAL_BASE.up()).setAttached(
                    SlabAnchorAttachment.MODERN_PLACEMENT_TYPE, terrainMarkers);
            phase = 8; started = ticks; return;
        }
        checkMeshedFace(NATURAL_BASE.up(17), Direction.DOWN, 0, false);
        System.out.println("NATURAL_COLUMN_PROVENANCE_REFRESH_PASS");

        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        require(renderer != null, "No active native renderer");
        String name = renderer.getClass().getName();
        boolean sodium = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
        require(name.toLowerCase().contains(sodium ? "sodium" : "indigo"), "Wrong active renderer: " + name);
        check(client, renderer, GROUND.up(2), Direction.UP, true, -0.5);
        check(client, renderer, GROUND.up(3), Direction.DOWN, true, 0.0);
        check(client, renderer, GROUND.east(4).up(2), Direction.UP, false, -0.5);
        check(client, renderer, GROUND.east(4).up(3), Direction.DOWN, false, -0.5);
        try (var screenshot = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            screenshot.writeTo(client.runDirectory.toPath().resolve("vertical-step.png"));
        }
        Files.writeString(Path.of("vertical-step-pass.txt"), "Native vertical faces and shared-face controls passed: " + name + "\n");
        System.out.println("VERTICAL_STEP_CLIENT_PASS renderer=" + name);
        phase = 99;
        client.scheduleStop();
    }

    private static Block terrainSlab() {
        var id = net.minecraft.util.Identifier.of("terrain_slabs", "grass_slab");
        return net.minecraft.registry.Registries.BLOCK.containsId(id)
                ? net.minecraft.registry.Registries.BLOCK.get(id) : TerrainSlabsTestShim.TEST_TS_SLAB;
    }

    private static void checkSlabTarget(MinecraftClient client, BlockPos pos, double localY, boolean visible) {
        Vec3d start = new Vec3d(pos.getX() + .5, pos.getY() + localY, pos.getZ() - 2);
        var hit = com.slabbed.util.SlabbedOffsetRaycast.raycast(client.world, start,
                start.add(0, 0, 2.75), net.minecraft.block.ShapeContext.of(client.player));
        require(visible ? hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                        : hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS,
                "Slab targeting must hit its visible body and leave the duplicate-frame area empty: " + hit);
    }

    private static void requirePredictedSlab(MinecraftClient client, BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        require(state.isOf(terrainSlab()) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "Upward placement must remain a bottom slab: " + state);
        require(SlabSupport.getYOffset(client.world, pos, state) == -0.5,
                "Slab height must be correct before and after the server reply");
    }

    private static void place(ServerWorld world, ServerPlayerEntity player, Item item, BlockPos owner) {
        ItemStack stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var hit = new BlockHitResult(new Vec3d(owner.getX() + 0.5,
                owner.getY() + 1.0, owner.getZ() + 0.5), Direction.UP, owner, false);
        require(stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(),
                "Native placement failed: " + item);
    }

    private static void check(MinecraftClient client, Renderer renderer, BlockPos pos,
                              Direction face, boolean exposed, double dy) {
        BlockState state = client.world.getBlockState(pos);
        require(SlabSupport.getYOffset(client.world, pos, state) == dy, "Client height not synchronized");
        BakedModel model = client.getBlockRenderManager().getModel(state);
        require(model instanceof OffsetBlockStateModel, "Installed offset model missing: " + model.getClass());
        List<Face> emitted;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            emitted = sodiumFaces(client, model, pos, state);
        } else {
            Capture capture = new Capture(client, renderer, pos, state);
            ((FabricBakedModel)model).emitBlockQuads(client.world, state, pos, () -> Random.create(42), capture);
            emitted = capture.faces;
        }
        List<Face> faces = emitted.stream().filter(q -> q.normal == face).toList();
        require(faces.size() == 1, "Expected one real cube face: " + face + " got " + faces.size());
        Face quad = faces.getFirst();
        require(quad.visible == exposed, "Face visibility mismatch: " + face + " cull=" + quad.cull);
        require((quad.cull == null) == exposed, "Exposed face must bypass grid culling");
        double plane = (face == Direction.UP ? 1.0 : 0.0) + dy;
        require(Math.abs(quad.minY - plane) < 1.0e-6 && Math.abs(quad.maxY - plane) < 1.0e-6,
                "Face moved away from its stored surface");
        require(!Block.shouldDrawSide(state, client.world, pos, face, pos.offset(face)),
                "Grid-culling control did not reproduce the hidden face");
    }

    /** Observe Sodium's own transform and occlusion pipeline without replacing its context. */
    private static List<Face> sodiumFaces(MinecraftClient client, BakedModel model,
                                           BlockPos pos, BlockState state) {
        try {
            Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.frapi.render.NonTerrainBlockRenderContext");
            RenderContext context = (RenderContext)type.getConstructors()[0].newInstance(client.getBlockColors());
            var culling = type.getMethod("isFaceCulled", Direction.class);
            List<Face> faces = new ArrayList<>();
            context.pushTransform(quad -> {
                float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < 4; i++) { minY = Math.min(minY, quad.y(i)); maxY = Math.max(maxY, quad.y(i)); }
                try {
                    boolean visible = !(boolean)culling.invoke(context, quad.cullFace());
                    faces.add(new Face(quad.nominalFace(), quad.cullFace(), visible, minY, maxY));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                return true;
            });
            VertexConsumer sink = (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, (proxy, method, args) ->
                            method.getReturnType() == VertexConsumer.class ? proxy : null);
            try {
                var render = java.util.Arrays.stream(type.getMethods())
                        .filter(method -> method.getName().equals("renderModel") && method.getParameterCount() == 10)
                        .findFirst().orElseThrow();
                render.invoke(context, client.world, model, state, pos, new MatrixStack(), sink,
                        true, Random.create(42), 42L, 0);
            } finally { context.popTransform(); }
            return faces;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Native Sodium pipeline check failed", error);
        }
    }

    private record Face(Direction normal, Direction cull, boolean visible, float minY, float maxY) {}

    private static final class Capture implements RenderContext {
        private final ArrayDeque<QuadTransform> transforms = new ArrayDeque<>();
        private final List<Face> faces = new ArrayList<>();
        private final QuadEmitter emitter;
        private final Renderer renderer;
        private final BlockState state;

        Capture(MinecraftClient client, Renderer renderer, BlockPos pos, BlockState state) {
            this.renderer = renderer;
            this.state = state;
            QuadEmitter nativeEmitter = renderer.meshBuilder().getEmitter();
            emitter = (QuadEmitter)Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(),
                    new Class<?>[]{QuadEmitter.class}, (proxy, method, args) -> {
                        if (method.getName().equals("emit")) {
                            for (QuadTransform transform : transforms) if (!transform.transform(nativeEmitter)) return proxy;
                            Direction cull = nativeEmitter.cullFace();
                            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
                            for (int i = 0; i < 4; i++) { minY = Math.min(minY, nativeEmitter.y(i)); maxY = Math.max(maxY, nativeEmitter.y(i)); }
                            boolean visible = cull == null || Block.shouldDrawSide(state, client.world, pos, cull, pos.offset(cull));
                            faces.add(new Face(nativeEmitter.nominalFace(), cull, visible, minY, maxY));
                        }
                        Object value = method.invoke(nativeEmitter, args);
                        return value == nativeEmitter ? proxy : value;
                    });
        }
        @Override public QuadEmitter getEmitter() { return emitter; }
        @Override public void pushTransform(QuadTransform transform) { transforms.addFirst(transform); }
        @Override public void popTransform() { transforms.removeFirst(); }
        @Override public BakedModelConsumer bakedModelConsumer() {
            return new BakedModelConsumer() {
                @Override public void accept(BakedModel model) { accept(model, state); }
                @Override public void accept(BakedModel model, BlockState blockState) {
                    for (int i = 0; i <= 6; i++) {
                        Direction face = i == 6 ? null : Direction.values()[i];
                        for (var quad : model.getQuads(blockState, face, Random.create(42))) {
                            emitter.fromVanilla(quad, renderer.materialFinder().find(), face).emit();
                        }
                    }
                }
            };
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
