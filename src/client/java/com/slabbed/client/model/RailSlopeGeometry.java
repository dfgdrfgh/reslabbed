package com.slabbed.client.model;

import com.slabbed.util.RailSlopeProfile;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a straight rail on the profile {@link RailSlopeProfile} fits for it (maintainer ruling,
 * 2026-09-28).
 *
 * <p>Vanilla draws a rail as one plane: flat at 1/16, or a ramp from 1/16 to 17/16. Both are linear
 * along the rail's axis, so subtracting vanilla's rise and adding the fitted one leaves every quad
 * PLANAR — the same sprite, the same UVs, the texture stretched along the slope exactly as vanilla's
 * own ramp stretches it. No model is registered; the wrapped model's own baked quads are rebuilt
 * with their vertices moved. A {@link RailSlopeProfile.Profile#kinked kinked} profile (a V) is drawn
 * as two planes: every quad that spans the middle of the cell is split there into two quads, each
 * half on its own plane.
 *
 * <p>The seat is applied here, once, for these quads: the wrapper returns what this class builds
 * instead of passing it through its seat translate, so a vertex written here is a vertex drawn, with
 * no second shift. Rail quads carry no cull face, so they only ever arrive in the unculled pass and
 * the step-seam cull work of the wrapper has nothing to do for them.
 *
 * <p>INVARIANT: the baked quads a model hands out are shared by every cell that draws that model, so
 * this class always writes into a copy of the vertex data and never into the source quad.
 */
public final class RailSlopeGeometry {

    private static final float EDGE = 1.0e-4f;
    private static final float MIDDLE = 0.5f;
    private static final int VERTICES = 4;

    private RailSlopeGeometry() {
    }

    /**
     * True for any rail state: the only states this class can ever fit. Allocation-free, so the
     * chunk-mesh wrapper can ask it for every block when deciding whether a cell keeps a render
     * context — a FLUSH ramp climbing onto a lowered rail is fitted while its own seat is 0.
     */
    public static boolean mayFit(BlockState state) {
        return state != null && state.getBlock() instanceof BaseRailBlock;
    }

    /**
     * The profile the rail at {@code pos} must be drawn on, or {@code null} when the ordinary path
     * draws it: not a straight rail, or a profile vanilla already draws. The allocation-free block
     * test runs first so every non-rail block pays nothing; the profile read is render-region
     * guarded, and a read that steps outside the region border falls back to vanilla geometry for
     * this bake, exactly like the seat read does.
     */
    public static RailSlopeProfile.Profile fittedProfile(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        if (!mayFit(state) || view == null || pos == null) {
            return null;
        }
        RailSlopeProfile.Profile profile;
        try {
            profile = RailSlopeProfile.resolve(view, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return null;
        }
        if (profile == null || profile.isVanilla()) {
            return null;
        }
        return profile;
    }

    /**
     * New quads for {@code quads} drawn on {@code profile} and moved by the seat {@code dy}; the
     * source list and its quads are left untouched.
     */
    public static List<BakedQuad> fit(List<BakedQuad> quads, RailSlopeProfile.Profile profile, float dy) {
        boolean alongZ = profile.axis() == Direction.Axis.Z;
        ArrayList<BakedQuad> fitted = new ArrayList<>(profile.kinked() ? quads.size() * 2 : quads.size());
        for (BakedQuad quad : quads) {
            fitQuad(fitted, quad, profile, alongZ, dy);
        }
        return fitted;
    }

    private static void fitQuad(List<BakedQuad> out, BakedQuad quad, RailSlopeProfile.Profile profile,
                                boolean alongZ, float dy) {
        int[] source = quad.getVertices();
        if (source.length < VERTICES * IQuadTransformer.STRIDE) {
            // Not the block vertex layout: nothing to fit against, so it gets the seat alone, as the
            // ordinary path would draw it.
            int[] vertices = source.clone();
            for (int i = 0; i < VERTICES; i++) {
                int base = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                if (base + 1 < vertices.length) {
                    vertices[base + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[base + 1]) + dy);
                }
            }
            out.add(rebuilt(quad, vertices));
            return;
        }
        float[] t = new float[VERTICES];
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < VERTICES; i++) {
            t[i] = alongZ ? z(source, i) : x(source, i);
            low |= t[i] < MIDDLE - EDGE;
            high |= t[i] > MIDDLE + EDGE;
        }
        if (profile.kinked() && low && high) {
            int[] lowPartners = partners(source, t, alongZ, true);
            int[] highPartners = partners(source, t, alongZ, false);
            if (lowPartners != null && highPartners != null) {
                out.add(half(quad, source, profile, dy, t, true, lowPartners));
                out.add(half(quad, source, profile, dy, t, false, highPartners));
                return;
            }
            // A quad that does not span the cell as a rectangle cannot be split cleanly; draw it
            // whole on the fitted heights instead (only a modded rail model reaches this).
        }
        int[] vertices = source.clone();
        for (int i = 0; i < VERTICES; i++) {
            setY(vertices, i, y(source, i) + dy + (float) profile.liftAt(t[i]));
        }
        out.add(rebuilt(quad, vertices));
    }

    /**
     * One half of a split quad: vertices inside the half keep their place; each vertex outside it
     * slides along the quad's edge toward its partner until it reaches the middle of the cell, its
     * texture coordinate sliding with it. Colour, light and normal stay the vertex's own (the quad is
     * planar, so its partner's are the same). The vertex order is unchanged, so the winding is too.
     */
    private static BakedQuad half(BakedQuad quad, int[] source, RailSlopeProfile.Profile profile,
                                  float dy, float[] t, boolean lowHalf, int[] partners) {
        int[] vertices = source.clone();
        for (int i = 0; i < VERTICES; i++) {
            if (inside(t[i], lowHalf)) {
                setY(vertices, i, y(source, i) + dy + (float) profile.liftAt(t[i]));
                continue;
            }
            int j = partners[i];
            float s = (t[i] - MIDDLE) / (t[i] - t[j]);
            int base = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            vertices[base] = Float.floatToRawIntBits(lerp(x(source, i), x(source, j), s));
            vertices[base + 1] = Float.floatToRawIntBits(
                    lerp(y(source, i), y(source, j), s) + dy + (float) profile.liftAt(MIDDLE));
            vertices[base + 2] = Float.floatToRawIntBits(lerp(z(source, i), z(source, j), s));
            int uv = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            int partnerUv = j * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            vertices[uv] = Float.floatToRawIntBits(lerp(
                    Float.intBitsToFloat(source[uv]), Float.intBitsToFloat(source[partnerUv]), s));
            vertices[uv + 1] = Float.floatToRawIntBits(lerp(
                    Float.intBitsToFloat(source[uv + 1]), Float.intBitsToFloat(source[partnerUv + 1]), s));
        }
        return rebuilt(quad, vertices);
    }

    /**
     * For each vertex outside the half, the adjacent vertex inside it that shares its cross-axis
     * coordinate (the other end of the same edge along the rail); {@code -1} for vertices inside.
     * Null when some outside vertex has no such partner, i.e. the quad is not an axis-aligned
     * rectangle spanning the middle.
     */
    private static int[] partners(int[] source, float[] t, boolean alongZ, boolean lowHalf) {
        int[] partners = new int[VERTICES];
        for (int i = 0; i < VERTICES; i++) {
            if (inside(t[i], lowHalf)) {
                partners[i] = -1;
                continue;
            }
            float cross = alongZ ? x(source, i) : z(source, i);
            int found = -1;
            for (int step = 1; step <= 3; step += 2) {
                int j = (i + step) & 3;
                float jCross = alongZ ? x(source, j) : z(source, j);
                if (inside(t[j], lowHalf) && Math.abs(jCross - cross) <= EDGE && Math.abs(t[i] - t[j]) > EDGE) {
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

    private static boolean inside(float t, boolean lowHalf) {
        return lowHalf ? t <= MIDDLE + EDGE : t >= MIDDLE - EDGE;
    }

    private static BakedQuad rebuilt(BakedQuad quad, int[] vertices) {
        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                quad.hasAmbientOcclusion());
    }

    private static float x(int[] vertices, int vertex) {
        return Float.intBitsToFloat(vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION]);
    }

    private static float y(int[] vertices, int vertex) {
        return Float.intBitsToFloat(vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1]);
    }

    private static float z(int[] vertices, int vertex) {
        return Float.intBitsToFloat(vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 2]);
    }

    private static void setY(int[] vertices, int vertex, float y) {
        vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(y);
    }

    private static float lerp(float from, float to, float s) {
        return from + (to - from) * s;
    }
}
