package com.slabbed.client.model;

import com.slabbed.util.FenceCeilingConnection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/** Adds a same-texture post segment while leaving fence rails and the placed seat unchanged. */
public final class FenceCeilingGeometry {
    private static final float EPS = 1.0e-5f;

    private FenceCeilingGeometry() {
    }

    public static boolean collectIfConnected(BlockStateModel model, BlockAndTintGetter view, BlockPos pos,
                                             BlockState state, float seat, RandomSource random,
                                             List<BlockStateModelPart> out) {
        if (!(state.getBlock() instanceof FenceBlock)) {
            return false;
        }
        float extension;
        try {
            extension = (float) FenceCeilingConnection.postTop(view, pos, state, seat) - 1.0f;
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return false;
        }
        if (extension <= EPS) {
            return false;
        }
        List<BlockStateModelPart> captured = new ArrayList<>(4);
        model.collectParts(view, pos, state, random, captured);
        for (BlockStateModelPart part : captured) {
            QuadPart edited = new QuadPart(part);
            QuadPart.forEachQuad(part, (cullFace, quad) -> emit(edited, cullFace, quad, seat, extension));
            out.add(edited);
        }
        return true;
    }

    private static void emit(QuadPart out, net.minecraft.core.Direction cullFace, BakedQuad quad,
                             float seat, float extension) {
        MutableQuad q = new MutableQuad().setFrom(quad);
        boolean post = true;
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            post &= q.x(i) >= 0.375f - EPS && q.x(i) <= 0.625f + EPS
                    && q.z(i) >= 0.375f - EPS && q.z(i) <= 0.625f + EPS;
            low = Math.min(low, q.y(i));
            high = Math.max(high, q.y(i));
        }
        boolean top = post && Math.abs(low - 1.0f) <= EPS && Math.abs(high - 1.0f) <= EPS;
        MutableQuad moved = new MutableQuad().setFrom(quad);
        for (int i = 0; i < 4; i++) {
            moved.setY(i, q.y(i) + seat + (top ? extension : 0.0f));
        }
        out.add(top ? null : cullFace, moved.toBakedQuad());
        if (!post || Math.abs(low) > EPS || Math.abs(high - 1.0f) > EPS) {
            return;
        }
        MutableQuad segment = new MutableQuad().setFrom(quad);
        for (int i = 0; i < 4; i++) {
            segment.setY(i, 1.0f + seat + q.y(i) * extension);
            if (q.y(i) > EPS) {
                for (int j = 0; j < 4; j++) {
                    if (q.y(j) <= EPS && Math.abs(q.x(i) - q.x(j)) <= EPS
                            && Math.abs(q.z(i) - q.z(j)) <= EPS) {
                        segment.setUv(i, q.u(j) + (q.u(i) - q.u(j)) * extension,
                                q.v(j) + (q.v(i) - q.v(j)) * extension);
                        break;
                    }
                }
            }
        }
        out.add(null, segment.toBakedQuad());
    }
}
