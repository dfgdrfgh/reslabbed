package com.slabbed.harness;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.SlabBlock;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production boot proof for one covered Minecraft version: a real client built from the
 * intermediary jars joins a world with the release jar loaded, so every client mixin must have
 * applied (the mixin configs use {@code defaultRequire 1}), the model wrapper meshes a lowered
 * block, the client reads the stored height, and the version-selected crosshair-pick mixin
 * targets the lowered block where it is drawn. Server-side behaviour is proven by the server
 * legs; this leg exists because those never load client classes.
 */
public final class HarnessBootClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("slabbed/harness");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        BlockPos slab = new BlockPos(0, 200, 0);
        BlockPos torch = slab.up();
        try (TestSingleplayerContext game = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runOnServer(server -> {
                var w = server.getOverworld();
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        w.setBlockState(slab.add(x, -1, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
                w.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        Block.NOTIFY_LISTENERS);
                w.setBlockState(torch, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
                SlabPlacementDyAttachment.record(w, torch, -0.5);
            });
            for (int i = 0; i < 40; i++) ctx.waitTick();
            game.getClientWorld().waitForChunksRender();

            // Stand on the stone platform two blocks south of the torch, eyes above the lowered torch body
            // (which spans roughly y 200.5 .. 201.1), looking straight at it. The server owns the
            // player's position, so the move goes through a command rather than the client entity.
            double feetY = 200.0, eyeY = feetY + 1.62;
            double eyeZ = torch.getZ() + 2.5, targetY = 200.8, targetZ = torch.getZ() + 0.5;
            double pitch = Math.toDegrees(Math.atan2(eyeY - targetY, eyeZ - targetZ));
            String tp = String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 180 %.2f",
                    torch.getX() + 0.5, feetY, eyeZ, pitch);
            game.getServer().runOnServer(server -> {
                // Brigadier's own dispatcher call: stable across the Yarn renames of the vanilla
                // helper between the covered versions.
                try {
                    server.getCommandManager().getDispatcher().execute(tp, server.getCommandSource());
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new IllegalStateException("HARNESS_BOOT | FAIL | teleport command rejected: " + tp, e);
                }
            });
            double[] dy = new double[1];
            ctx.runOnClient(mc -> {
                mc.options.hudHidden = true;
                dy[0] = SlabSupport.getVisualYOffset(mc.world, torch, mc.world.getBlockState(torch));
            });
            for (int i = 0; i < 10; i++) ctx.waitTick();
            if (dy[0] != -0.5) {
                throw new AssertionError("HARNESS_BOOT | FAIL | client did not read the stored height: dy=" + dy[0]);
            }
            String[] pick = new String[1];
            ctx.runOnClient(mc -> {
                if (mc.crosshairTarget instanceof BlockHitResult hit) {
                    pick[0] = hit.getBlockPos().toShortString();
                    if (!hit.getBlockPos().equals(torch)) {
                        throw new AssertionError("HARNESS_BOOT | FAIL | crosshair pick landed on " + pick[0]
                                + " instead of the lowered torch at " + torch.toShortString());
                    }
                } else {
                    throw new AssertionError("HARNESS_BOOT | FAIL | crosshair pick is not a block hit: " + mc.crosshairTarget);
                }
            });
            ctx.takeScreenshot("harness-boot");
            LOGGER.info("HARNESS_BOOT | PASS | lowered torch dy={} crosshair pick={}", dy[0], pick[0]);
        }
    }
}
