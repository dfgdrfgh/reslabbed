package com.slabbed.client.model;

import com.slabbed.util.RailSlopeProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/**
 * Draws a straight rail on the profile {@link RailSlopeProfile} fits for it (maintainer ruling,
 * 2026-09-28). Vanilla draws a rail as one plane; subtracting vanilla's rise and adding the fitted
 * one leaves every quad planar with the same sprite and UVs. A kinked profile (a V) is drawn as two
 * planes: every quad is split at the middle of the cell. The seat is applied here, once.
 */
public final class RailSlopeGeometry {
    private static final float EDGE = 1.0e-4f;
    private static final float MIDDLE = 0.5f;

    private RailSlopeGeometry() {
    }

    public static boolean collectIfFitted(BlockStateModel model, BlockAndTintGetter view, BlockPos pos,
                                          BlockState state, float dy, RandomSource random,
                                          List<BlockStateModelPart> out) {
        if (!(state.getBlock() instanceof BaseRailBlock)) {
            return false;
        }
        RailSlopeProfile.Profile profile;
        try {
            profile = RailSlopeProfile.resolve(view, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return false;
        }
        if (profile == null || profile.isVanilla()) {
            return false;
        }
        List<BlockStateModelPart> captured = new ArrayList<>(2);
        model.collectParts(view, pos, state, random, captured);
        boolean alongZ = profile.axis() == Direction.Axis.Z;
        for (BlockStateModelPart part : captured) {
            QuadPart edited = new QuadPart(part);
            QuadPart.forEachQuad(part, (cullFace, quad) -> emitFitted(edited, cullFace, quad, profile, alongZ, dy));
            out.add(edited);
        }
        return true;
    }

    private static void emitFitted(QuadPart out, Direction cullFace, BakedQuad quad,
                                   RailSlopeProfile.Profile profile, boolean alongZ, float dy) {
        MutableQuad q = new MutableQuad().setFrom(quad);
        float[] t = new float[4];
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < 4; i++) {
            t[i] = alongZ ? q.z(i) : q.x(i);
            low |= t[i] < MIDDLE - EDGE;
            high |= t[i] > MIDDLE + EDGE;
        }
        if (profile.kinked() && low && high) {
            int[] lowPartners = partners(q, t, alongZ, true);
            int[] highPartners = partners(q, t, alongZ, false);
            if (lowPartners != null && highPartners != null) {
                out.add(cullFace, half(quad, q, profile, dy, t, true, lowPartners));
                out.add(cullFace, half(quad, q, profile, dy, t, false, highPartners));
                return;
            }
        }
        MutableQuad whole = new MutableQuad().setFrom(quad);
        for (int i = 0; i < 4; i++) {
            whole.setY(i, q.y(i) + dy + (float) profile.liftAt(t[i]));
        }
        out.add(cullFace, whole.toBakedQuad());
    }

    private static BakedQuad half(BakedQuad source, MutableQuad q, RailSlopeProfile.Profile profile,
                                  float dy, float[] t, boolean lowHalf, int[] partners) {
        MutableQuad out = new MutableQuad().setFrom(source);
        for (int i = 0; i < 4; i++) {
            boolean inside = lowHalf ? t[i] <= MIDDLE + EDGE : t[i] >= MIDDLE - EDGE;
            if (inside) {
                out.setY(i, q.y(i) + dy + (float) profile.liftAt(t[i]));
                continue;
            }
            int j = partners[i];
            float s = (t[i] - MIDDLE) / (t[i] - t[j]);
            float x = lerp(q.x(i), q.x(j), s);
            float y = lerp(q.y(i), q.y(j), s);
            float z = lerp(q.z(i), q.z(j), s);
            out.setPosition(i, x, y + dy + (float) profile.liftAt(MIDDLE), z);
            out.setUv(i, lerp(q.u(i), q.u(j), s), lerp(q.v(i), q.v(j), s));
        }
        return out.toBakedQuad();
    }

    private static int[] partners(MutableQuad q, float[] t, boolean alongZ, boolean lowHalf) {
        int[] partners = new int[4];
        for (int i = 0; i < 4; i++) {
            boolean inside = lowHalf ? t[i] <= MIDDLE + EDGE : t[i] >= MIDDLE - EDGE;
            if (inside) {
                partners[i] = -1;
                continue;
            }
            float cross = alongZ ? q.x(i) : q.z(i);
            int found = -1;
            for (int step = 1; step <= 3; step += 2) {
                int j = (i + step) & 3;
                boolean jInside = lowHalf ? t[j] <= MIDDLE + EDGE : t[j] >= MIDDLE - EDGE;
                float jCross = alongZ ? q.x(j) : q.z(j);
                if (jInside && Math.abs(jCross - cross) <= EDGE && Math.abs(t[i] - t[j]) > EDGE) {
                    found = j;
                    break;
                }
            }
            if (found < 0) {
                return null;
            }
            partners[i] = found;
        }
        return partners;
    }

    private static float lerp(float from, float to, float s) {
        return from + (to - from) * s;
    }
}
