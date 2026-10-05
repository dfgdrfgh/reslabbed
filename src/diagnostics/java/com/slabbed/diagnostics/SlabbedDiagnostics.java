package com.slabbed.diagnostics;

import net.neoforged.fml.common.Mod;
import com.slabbed.diagnostics.util.LiveCursorIntentRecorder;
import com.slabbed.diagnostics.util.SlabModelStaleSentinel;
import com.slabbed.util.SlabbedDiagnosticsBridge;

@Mod("slabbed_diagnostics")
public final class SlabbedDiagnostics {
    public SlabbedDiagnostics() {
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            initClient();
        }
        SlabbedDiagnosticsBridge.install(new SlabbedDiagnosticsBridge.Provider() {
            @Override
            public boolean enabled() {
                return LiveCursorIntentRecorder.enabled();
            }

            @Override
            public SlabbedDiagnosticsBridge.PacketScope openUsePacketScope(
                    String side,
                    int sequence,
                    String playerId,
                    String dimensionId) {
                LiveCursorIntentRecorder.UsePacketScope scope =
                        LiveCursorIntentRecorder.openUsePacketScope(
                                side, sequence, playerId, dimensionId);
                return new SlabbedDiagnosticsBridge.PacketScope() {
                    @Override
                    public boolean claimed() {
                        return scope.claimed();
                    }

                    @Override
                    public void close() {
                        scope.close();
                    }
                };
            }

            @Override
            public void recordAction(java.util.LinkedHashMap<String, String> fields) {
                LiveCursorIntentRecorder.recordAction(fields);
            }

            @Override
            public void recordCursor(java.util.LinkedHashMap<String, String> fields) {
                LiveCursorIntentRecorder.recordCursor(fields);
            }

            @Override
            public void recordRenderedOutline(java.util.LinkedHashMap<String, String> fields) {
                LiveCursorIntentRecorder.recordRenderedOutline(fields);
            }

            @Override
            public void recordBreakEvent(
                    net.minecraft.world.level.Level world,
                    net.minecraft.core.BlockPos pos,
                    net.minecraft.world.level.block.state.BlockState state,
                    String playerName) {
                LiveCursorIntentRecorder.recordBreakEvent(world, pos, state, playerName);
            }

            @Override
            public void armBreakNeighborhood(
                    net.minecraft.world.level.BlockGetter world,
                    net.minecraft.core.BlockPos pos,
                    long nowTick) {
                SlabModelStaleSentinel.armBreakNeighborhood(world, pos, nowTick);
            }

            @Override
            public void armPlacement(
                    net.minecraft.world.level.BlockGetter world,
                    net.minecraft.core.BlockPos pos,
                    long nowTick) {
                SlabModelStaleSentinel.armPlacement(world, pos, nowTick);
            }

            @Override
            public boolean shouldCaptureModelBake() {
                return SlabModelStaleSentinel.shouldCapture();
            }

            @Override
            public boolean isModelBakeArmed(long posKey) {
                return SlabModelStaleSentinel.isArmed(posKey);
            }

            @Override
            public void recordModelBake(net.minecraft.core.BlockPos pos, float bakedDy) {
                SlabModelStaleSentinel.recordBake(pos, bakedDy);
            }

            @Override
            public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(String origin) {
                LiveCursorIntentRecorder.ActionOriginScope scope =
                        LiveCursorIntentRecorder.enterActionOrigin(
                                LiveCursorIntentRecorder.ActionOrigin.valueOf(origin));
                return scope::close;
            }
        });
    }

    private static void initClient() {
        try {
            Class.forName("com.slabbed.diagnostics.client.SlabbedDiagnosticsClient").getMethod("init").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("diagnostics client hook failed", e);
        }
    }
}
