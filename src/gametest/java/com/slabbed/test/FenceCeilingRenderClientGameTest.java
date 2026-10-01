package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;

/** Checks the actual wrapped fence model, including after its ceiling is removed. */
public final class FenceCeilingRenderClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
            singleplayer.getClientWorld().waitForChunksDownload();
            ctx.waitFor(client -> client.world != null && client.player != null, 400);
            BlockPos post = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                BlockPos p = player.getBlockPos().offset(player.getHorizontalFacing(), 5).up().toImmutable();
                var world = server.getOverworld();
                world.setBlockState(p, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(p.up(), Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                        Block.NOTIFY_ALL);
                return p;
            });
            ctx.waitFor(client -> client.world != null && client.world.getBlockState(post).isOf(Blocks.OAK_FENCE)
                    && client.world.getBlockState(post.up()).isOf(Blocks.OAK_SLAB), 400);
            ctx.waitTicks(5);
            ctx.runOnClient(client -> checkModel(client, post, 1.5d));
            ctx.takeScreenshot("fence-ceiling-connected");
            singleplayer.getServer().runOnServer(server ->
                    server.getOverworld().setBlockState(post.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL));
            ctx.waitFor(client -> client.world != null && client.world.getBlockState(post.up()).isAir(), 400);
            ctx.waitTicks(5);
            ctx.runOnClient(client -> {
                checkModel(client, post, 1.0d);
                Slabbed.LOGGER.info("CLIENT_GAMETEST | FenceCeilingRenderClientGameTest | PASS");
            });
        }
    }

    private static void checkModel(MinecraftClient client, BlockPos post, double expectedTop) {
        var state = client.world.getBlockState(post);
        var model = client.getBakedModelManager().getBlockModels().getModel(state);
        if (!(model instanceof FabricBlockStateModel fabric) || Renderer.get() == null) {
            throw new AssertionError("the production fence model and renderer must be available");
        }
        double[] minMax = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        var mesh=Renderer.get().mutableMesh();
        fabric.emitQuads(mesh.emitter(),client.world,post,state,Random.create(42L),direction -> false);
        mesh.forEachMutable(quad -> {
            for (int i = 0; i < 4; i++) {
                if (quad.x(i)>=0.375f && quad.x(i)<=0.625f && quad.z(i)>=0.375f && quad.z(i)<=0.625f) {
                    minMax[0]=Math.min(minMax[0],quad.y(i));
                    minMax[1]=Math.max(minMax[1],quad.y(i));
                }
                if (!Float.isFinite(quad.u(i)) || !Float.isFinite(quad.v(i))) {
                    throw new AssertionError("connection texture coordinates must stay finite");
                }
            }
        });
        if (Math.abs(minMax[0])>1.0e-5d || Math.abs(minMax[1]-expectedTop)>1.0e-5d) {
            throw new AssertionError("post vertices must span 0 to "+expectedTop+", got "+minMax[0]+" to "+minMax[1]);
        }
        var start=new net.minecraft.util.math.Vec3d(post.getX()-1.0d,post.getY()+1.25d,post.getZ()+0.5d);
        var hit=com.slabbed.util.SlabbedOffsetRaycast.raycast(client.world,start,start.add(2.0d,0.0d,0.0d),
                net.minecraft.block.ShapeContext.absent());
        boolean targetsPost=hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(post);
        if (targetsPost!=(expectedTop>1.0d)) {
            throw new AssertionError("extended-post targeting must follow the actual ceiling connection");
        }
        double stored = SlabPlacementDyAttachment.storedDy(client.world, post);
        if (Double.isFinite(stored) && stored!=0.0d) {
            throw new AssertionError("connecting geometry must not move the saved post");
        }
    }
}
