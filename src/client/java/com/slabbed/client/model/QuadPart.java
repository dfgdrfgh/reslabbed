package com.slabbed.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.jetbrains.annotations.Nullable;

/**
 * A block model part built from edited copies of another part's quads: seven buckets (six cull
 * faces plus the unculled one), filled by the geometry that owns this part. Cull face is the bucket
 * a quad sits in; a quad's own {@code direction} (its nominal face for lighting) is never changed.
 */
final class QuadPart implements BlockStateModelPart {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockStateModelPart source;
    @SuppressWarnings("unchecked")
    private final List<BakedQuad>[] buckets = new List[7];

    QuadPart(BlockStateModelPart source) {
        this.source = source;
        for (int i = 0; i < 7; i++) {
            buckets[i] = new ArrayList<>(4);
        }
    }

    static int bucket(@Nullable Direction cullFace) {
        return cullFace == null ? 6 : cullFace.ordinal();
    }

    void add(@Nullable Direction cullFace, BakedQuad quad) {
        buckets[bucket(cullFace)].add(quad);
    }

    /** Every quad of {@code part}, visited with its cull face (null for the unculled bucket). */
    static void forEachQuad(BlockStateModelPart part, QuadVisitor visitor) {
        for (Direction direction : DIRECTIONS) {
            for (BakedQuad quad : part.getQuads(direction)) {
                visitor.visit(direction, quad);
            }
        }
        for (BakedQuad quad : part.getQuads(null)) {
            visitor.visit(null, quad);
        }
    }

    interface QuadVisitor {
        void visit(@Nullable Direction cullFace, BakedQuad quad);
    }

    /** A copy of {@code quad} with every vertex moved by {@code dy}. */
    static BakedQuad translated(BakedQuad quad, float dy) {
        if (dy == 0.0f) {
            return quad;
        }
        MutableQuad mutable = new MutableQuad().setFrom(quad);
        for (int i = 0; i < 4; i++) {
            mutable.setY(i, mutable.y(i) + dy);
        }
        return mutable.toBakedQuad();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable Direction direction) {
        return buckets[bucket(direction)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean useAmbientOcclusion() {
        return source.useAmbientOcclusion();
    }

    @Override
    public Material.Baked particleMaterial() {
        return source.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return source.materialFlags();
    }
}
