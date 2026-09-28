package com.slabbed.client.model;

import com.slabbed.util.RailSlopeProfile;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IQuadTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a straight rail on the profile {@link RailSlopeProfile} fits for it (maintainer ruling,
 * 2026-09-28).
 *
 * <p>Vanilla draws a rail as one plane: flat at 1/16, or a ramp from 1/16 to 17/16. Both are linear
 * along the rail's axis, so subtracting vanilla's rise and adding the fitted one leaves every quad
 * PLANAR — the same sprite, the same UVs, the texture stretched along the slope exactly as vanilla's
 * own ramp stretches it. No model is registered; vanilla's own baked quads are copied with their
 * vertices moved. A {@link RailSlopeProfile.Profile#kinked kinked} profile (a V) is drawn as two
 * planes: every quad is split at the middle of the cell into two quads, each half on its own plane.
 *
 * <p>The seat is applied here, once, for these quads: the chunk-mesh wrapper returns them in place
 * of its own seat translate, so a vertex written here is a vertex drawn, with no second shift.
 *
 * <p>INVARIANT: the baked quads a model hands out are shared by every cell that draws that model,
 * so every quad here is built on a COPY of the vertex data; the source quad is never written.
 * Colour, lightmap and packed normal are carried over from the source vertex unchanged — the
 * baker writes them per face (the normal is the face's axis unless a client option recomputes
 * it), so a moved or split vertex keeps exactly what vanilla's own ramp vertex carries.
 */
public final class RailSlopeGeometry {

    private static final float EDGE = 1.0e-4f;
    private static final float MIDDLE = 0.5f;
    private static final int VERTICES = 4;

    private RailSlopeGeometry() {
    }

    /**
     * True for any rail state: the only states this class can ever draw. Allocation-free, so the
     * chunk-mesh wrapper can ask it for every block to decide whether a cell needs a render
     * context even when its own seat is flush (a flush ramp climbing onto a lowered rail).
     */
    public static boolean mayFit(BlockState state) {
        return state != null && state.getBlock() instanceof BaseRailBlock;
    }

    /**
     * The fitted profile for the rail at {@code pos}, or {@code null} when the ordinary path must
     * draw it: not a straight rail, or a profile vanilla already draws. The allocation-free block
     * test runs first so every non-rail block pays nothing; the profile read is render-region
     * guarded, and a read that steps outside the region border falls back to vanilla geometry for
     * this bake, exactly like the seat read does (the section re-bakes with fuller bounds later).
     */
    public static RailSlopeProfile.Profile fittedProfile(BlockAndTintGetter view, BlockPos pos,
                                                         BlockState state) {
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
     * Copies of {@code quads} on the fitted profile, seat {@code dy} included: every vertex Y
     * becomes vanilla's Y + seat + {@code profile.liftAt(t)}, {@code t} the vertex's block-local
     * coordinate along the rail's axis. The source list and its quads are left untouched.
     */
    public static List<BakedQuad> fit(List<BakedQuad> quads, RailSlopeProfile.Profile profile, float dy) {
        boolean alongZ = profile.axis() == Direction.Axis.Z;
        boolean kinked = profile.kinked();
        ArrayList<BakedQuad> fitted = new ArrayList<>(kinked ? quads.size() * 2 : quads.size());
        for (BakedQuad quad : quads) {
            emitFitted(fitted, quad, profile, alongZ, kinked, dy);
        }
        return fitted;
    }

    private static void emitFitted(List<BakedQuad> out, BakedQuad quad, RailSlopeProfile.Profile profile,
                                   boolean alongZ, boolean kinked, float dy) {
        int[] source = quad.getVertices();
        if (source.length < VERTICES * IQuadTransformer.STRIDE) {
            // Not a four-vertex quad in the block format: only the seat can be applied safely.
            int[] shifted = source.clone();
            for (int i = 0; i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1 < shifted.length; i++) {
                setY(shifted, i, y(source, i) + dy);
            }
            out.add(rebuild(quad, shifted));
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
        if (kinked && low && high) {
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
        int[] moved = source.clone();
        for (int i = 0; i < VERTICES; i++) {
            setY(moved, i, y(source, i) + dy + (float) profile.liftAt(t[i]));
        }
        out.add(rebuild(quad, moved));
    }

    /**
     * One half of a split quad: vertices inside the half keep their place; each vertex outside it
     * slides along the quad's edge toward its partner until it reaches the middle of the cell, its
     * texture coordinate sliding with it. The vertex order is unchanged, so the winding is too.
     */
    private static BakedQuad half(BakedQuad quad, int[] source, RailSlopeProfile.Profile profile,
                                  float dy, float[] t, boolean lowHalf, int[] partners) {
        int[] split = source.clone();
        for (int i = 0; i < VERTICES; i++) {
            boolean inside = lowHalf ? t[i] <= MIDDLE + EDGE : t[i] >= MIDDLE - EDGE;
            if (inside) {
                setY(split, i, y(source, i) + dy + (float) profile.liftAt(t[i]));
                continue;
            }
            int j = partners[i];
            float s = (t[i] - MIDDLE) / (t[i] - t[j]);
            int base = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            split[base] = Float.floatToRawIntBits(lerp(x(source, i), x(source, j), s));
            split[base + 1] = Float.floatToRawIntBits(
                    lerp(y(source, i), y(source, j), s) + dy + (float) profile.liftAt(MIDDLE));
            split[base + 2] = Float.floatToRawIntBits(lerp(z(source, i), z(source, j), s));
            int uv = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            split[uv] = Float.floatToRawIntBits(lerp(u(source, i), u(source, j), s));
            split[uv + 1] = Float.floatToRawIntBits(lerp(v(source, i), v(source, j), s));
        }
        return rebuild(quad, split);
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
            boolean inside = lowHalf ? t[i] <= MIDDLE + EDGE : t[i] >= MIDDLE - EDGE;
            if (inside) {
                partners[i] = -1;
                continue;
            }
            float cross = alongZ ? x(source, i) : z(source, i);
            int found = -1;
            for (int step = 1; step <= 3; step += 2) {
                int j = (i + step) & 3;
                boolean jInside = lowHalf ? t[j] <= MIDDLE + EDGE : t[j] >= MIDDLE - EDGE;
                float jCross = alongZ ? x(source, j) : z(source, j);
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

    private static BakedQuad rebuild(BakedQuad quad, int[] vertices) {
        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                quad.hasAmbientOcclusion());
    }

    private static float x(int[] vertices, int i) {
        return Float.intBitsToFloat(vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION]);
    }

    private static float y(int[] vertices, int i) {
        return Float.intBitsToFloat(vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1]);
    }

    private static float z(int[] vertices, int i) {
        return Float.intBitsToFloat(vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 2]);
    }

    private static float u(int[] vertices, int i) {
        return Float.intBitsToFloat(vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.UV0]);
    }

    private static float v(int[] vertices, int i) {
        return Float.intBitsToFloat(vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.UV0 + 1]);
    }

    private static void setY(int[] vertices, int i, float y) {
        vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(y);
    }

    private static float lerp(float from, float to, float s) {
        return from + (to - from) * s;
    }
}
