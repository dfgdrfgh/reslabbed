package com.slabbed.client.model;

import com.slabbed.util.FenceCeilingConnection;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import java.util.function.Supplier;

/** Adds a same-texture post segment without moving the seat or horizontal rails. */
public final class FenceCeilingGeometry {
    private static final float EPS=1.0e-5f;
    private FenceCeilingGeometry() { }

    public static boolean emitIfConnected(BakedModel wrapped, BlockRenderView view, BlockState state,
            BlockPos pos, Supplier<Random> randomSupplier, QuadEmitter out, float seat) {
        if (!(state.getBlock() instanceof FenceBlock)) return false;
        float extension;
        try { extension=(float)FenceCeilingConnection.postTop(view,pos,state,seat)-1.0f; }
        catch (IndexOutOfBoundsException outsideRenderRegion) { return false; }
        Renderer renderer=Renderer.get();
        if (extension<=EPS || renderer==null) return false;
        // Capture the wrapped model's own quads unculled (every face is needed to find the post), then
        // re-emit each one on its seat, the post top extended up to the ceiling support.
        MutableMesh captured=renderer.mutableMesh();
        wrapped.emitBlockQuads(captured.emitter(),view,state,pos,randomSupplier,face -> false);
        captured.forEach(quad -> emit(out,quad,seat,extension));
        return true;
    }
    private static void emit(QuadEmitter out, QuadView quad, float seat, float extension) {
        boolean post = true;
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            post &= quad.x(i) >= 0.375f-EPS && quad.x(i) <= 0.625f+EPS
                    && quad.z(i) >= 0.375f-EPS && quad.z(i) <= 0.625f+EPS;
            low = Math.min(low, quad.y(i));
            high = Math.max(high, quad.y(i));
        }
        boolean top = post && Math.abs(low-1.0f) <= EPS && Math.abs(high-1.0f) <= EPS;
        out.copyFrom(quad);
        for (int i = 0; i < 4; i++) {
            out.pos(i, quad.x(i), quad.y(i)+seat+(top ? extension : 0.0f), quad.z(i));
        }
        if (top) out.cullFace(null);
        out.emit();
        if (!post || Math.abs(low) > EPS || Math.abs(high-1.0f) > EPS) {
            return;
        }
        out.copyFrom(quad);
        out.cullFace(null);
        for (int i = 0; i < 4; i++) {
            out.pos(i, quad.x(i), 1.0f+seat+quad.y(i)*extension, quad.z(i));
            if (quad.y(i) > EPS) {
                for (int j = 0; j < 4; j++) {
                    if (quad.y(j) <= EPS && Math.abs(quad.x(i)-quad.x(j)) <= EPS
                            && Math.abs(quad.z(i)-quad.z(j)) <= EPS) {
                        out.uv(i, quad.u(j)+(quad.u(i)-quad.u(j))*extension,
                                quad.v(j)+(quad.v(i)-quad.v(j))*extension);
                        break;
                    }
                }
            }
        }
        out.emit();
    }
}
