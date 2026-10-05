package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabEnsembleCoherence;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import com.slabbed.util.SlabSupport;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.ArrayList;

/**
 * Wraps a block-state model to draw it at the block's stored height. NeoForge's level-aware
 * {@code collectParts} is the one entry point: the chunk mesher calls it per block with the render
 * view, so the dy resolve, the seam cull and the alternate geometries all happen here.
 */
@SuppressWarnings({"RedundantSuppression", "DataFlowIssue"})
public final class OffsetBlockStateModel extends DelegateBlockStateModel {
    private static final boolean CULL_TRACE = Boolean.getBoolean("slabbed.render.offset.cullTrace");
    /** {@code Direction.values()} clones its array on every call; this runs per block per section. */
    private static final Direction[] DIRECTIONS = Direction.values();

    public OffsetBlockStateModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    /** The wrapped model (visible for tests). */
    public BlockStateModel wrapped() {
        return delegate;
    }

    @Override
    public void collectParts(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        // A moving body's reference cell is not the cell it is drawn in (piston head renderer,
        // falling-block renderer). A stored placement height is a fact about a CELL, so it has no
        // meaning on this view; the body's positioner carries the height (maintainer ruling,
        // 2026-09-06). Do not re-add a dy resolve on this path.
        if (slabbed$isMovingBodyView(view)) {
            delegate.collectParts(view, pos, state, random, parts);
            return;
        }
        float dy;
        if (SlabbedDiagnosticsBridge.shouldCaptureModelBake()
                && SlabbedDiagnosticsBridge.isModelBakeArmed(pos.asLong())) {
            dy = slabbed$modelDyCaptured(view, pos, state);
        } else {
            dy = slabbed$modelDy(view, pos, state);
        }
        // A Y-axis chain hanging under a slab ceiling draws extended geometry so the column connects
        // continuously to the slab. Its support probe reads pos.above(), which can step outside the
        // render-region border at a section's top edge (26.x throws): fall through to normal emission.
        try {
            if (ChainCeilingGeometry.collectIfPresent(view, pos, state, dy, random, parts)) {
                return;
            }
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            // fall through to the standard offset emission below (also render-region guarded)
        }
        // ONE seam state per block: memoises the six neighbour dys and is the step-seam probe and
        // the cull-face-clearing rule in one (fix-round F3 on the Fabric lines).
        SeamState seam = new SeamState(view, pos, state, dy);
        if (RailSlopeGeometry.collectIfFitted(delegate, view, pos, state, dy, random, parts)) {
            return;
        }
        if (FenceCeilingGeometry.collectIfConnected(delegate, view, pos, state, dy, random, parts)) {
            return;
        }
        boolean stepSeam = dy != 0.0f || seam.anyMismatchedNeighborDy();
        if (!stepSeam) {
            delegate.collectParts(view, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> raw = new ArrayList<>(4);
        delegate.collectParts(view, pos, state, random, raw);
        for (BlockStateModelPart part : raw) {
            parts.add(slabbed$translate(part, dy, seam));
        }
    }

    /**
     * The offset copy of one part: every vertex moved by {@code dy}; a quad whose cull face points at
     * a neighbour sitting at a different dy moves to the unculled bucket (its step strip is exposed,
     * so it must be drawn), keeping its own direction for lighting. The DODO step-face fix.
     */
    private static BlockStateModelPart slabbed$translate(BlockStateModelPart part, float dy, SeamState seam) {
        QuadPart out = new QuadPart(part);
        for (Direction direction : DIRECTIONS) {
            List<BakedQuad> quads = part.getQuads(direction);
            if (quads.isEmpty()) {
                continue;
            }
            Direction bucket = seam.mismatched(direction) ? null : direction;
            for (BakedQuad quad : quads) {
                out.add(bucket, QuadPart.translated(quad, dy));
            }
        }
        for (BakedQuad quad : part.getQuads(null)) {
            out.add(null, QuadPart.translated(quad, dy));
        }
        return out;
    }

    /**
     * The render-intent dy policy, publicly callable so the MODEL_STALE sentinel judges live dy with the
     * SAME function the capture records. C5 retires carpet's separate render courtesy, so model and
     * logical dy now share the stored {@link SlabSupport#getYOffset} authority.
     */
    public static float liveModelDy(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        return slabbed$modelDy(view, pos, state);
    }

    /**
     * The dy this model path actually applies for {@code view} — the single point of truth shared by
     * {@link #emitQuads} and by the moving-body draw-ownership proof, so the two can never disagree
     * about whether the model contributed an offset.
     *
     * <p>A moving body's view carries none: its reference cell is not the cell it is drawn in, so the
     * height belongs to whoever positions the body (maintainer ruling, 2026-09-06).
     */
    public static float appliedModelDy(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        if (slabbed$isMovingBodyView(view)) {
            return 0.0f;
        }
        return slabbed$modelDy(view, pos, state);
    }

    /** True for the render view of a body in motion, whose {@code blockPos} is a reference cell. */
    private static boolean slabbed$isMovingBodyView(BlockAndTintGetter view) {
        return view instanceof MovingBlockRenderState;
    }

    private static float slabbed$modelDy(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        // Render-region crash guard (26.x): on the chunk-meshing thread `view` is a bounds-limited
        // RenderSectionRegion that THROWS (ArrayIndexOutOfBoundsException) on a read outside its border.
        // SlabSupport.getYOffset does wide column-walk + adjacent-column side-support reads that can reach
        // past that border for a block near the region edge, so tesselating ordinary terrain at a region
        // boundary crashed the game. Treat any out-of-bounds read as "no offset" (0); the section recompiles
        // with fuller bounds once neighbouring sections load, so the real offset settles a frame later.
        // (Older MC render regions clamped OOB reads to air instead of throwing — a 26.x-specific need.)
        try {
            return slabbed$modelDyUnguarded(view, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 0.0f;
        }
    }

    /** Sentinel-armed twin of {@link #slabbed$modelDy}: identical dy, but records what was baked —
     *  except the OOB fallback, which is deliberately not a recordable dy decision. */
    private static float slabbed$modelDyCaptured(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        try {
            float dy = slabbed$modelDyUnguarded(view, pos, state);
            SlabbedDiagnosticsBridge.recordModelBake(pos, dy);
            return dy;
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 0.0f;
        }
    }

    private static float slabbed$modelDyUnguarded(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        float dy = (float) SlabSupport.getYOffset(view, pos, state);
        if (dy == 0.0f) {
            return 0.0f;
        }

        if (state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof IronBarsBlock) {
            if (!SlabSupport.isBeta35FenceWallVariantContactObject(state)) {
                return 0.0f;
            }
        }
        return dy;
    }

    /**
     * Neighbour model dy, bounds-safe for the render region. The neighbour's own block read can be the
     * first thing to step outside the region border, so guard it here too (slabbed$modelDy guards the
     * deeper resolver walk). Returns 0 (no offset) for an out-of-bounds neighbour.
     */
    private static float slabbed$neighborModelDy(BlockAndTintGetter view, BlockPos neighborPos) {
        try {
            return slabbed$modelDy(view, neighborPos, view.getBlockState(neighborPos));
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 0.0f;
        }
    }

    /**
     * Per-block, per-emission neighbour-dy state (fix-round F3). Not thread-safe and not reused: one
     * instance lives for exactly one {@link #emitQuads} call on one mesh worker thread, and the render
     * view it reads is immutable for the duration of a section compile — which is why memoising a
     * neighbour's dy cannot change any answer relative to resolving it afresh at each of the three
     * consumers.
     *
     * <p>As {@link Predicate}: the offset-aware cull test — a face whose neighbour sits at a different
     * model dy is never culled (its step strip is exposed), otherwise vanilla decides.
     *
     * <p>As {@link QuadTransform}: the DODO step-face fix — clear the {@code cullFace} of a quad facing
     * a mismatched neighbour (keeping it as the nominal face) so the strip the neighbour's offset
     * exposes is not culled into a see-through ghost window.
     */
    private static final class SeamState {
        private final BlockAndTintGetter view;
        private final BlockPos pos;
        private final BlockState state;
        private final float dy;
        /** Two 6-bit masks: only the mismatch verdict is ever consumed. */
        private int resolvedMask;
        private int mismatchMask;

        SeamState(BlockAndTintGetter view, BlockPos pos, BlockState state, float dy) {
            this.view = view;
            this.pos = pos;
            this.state = state;
            this.dy = dy;
        }

        /** True if ANY of the 6 neighbours sits at a different model dy than this block. */
        boolean anyMismatchedNeighborDy() {
            for (Direction direction : DIRECTIONS) {
                if (mismatched(direction)) {
                    return true;
                }
            }
            return false;
        }

        boolean mismatched(Direction direction) {
            int bit = 1 << direction.ordinal();
            if ((resolvedMask & bit) == 0) {
                float resolved = slabbed$neighborModelDy(view, pos.relative(direction));
                resolvedMask |= bit;
                if (Math.abs(resolved - dy) > 1.0e-6f) {
                    mismatchMask |= bit;
                    if (CULL_TRACE) {
                        BlockPos neighborPos = pos.relative(direction);
                        BlockState neighborState;
                        try {
                            neighborState = view.getBlockState(neighborPos);
                        } catch (IndexOutOfBoundsException outsideRenderRegion) {
                            neighborState = null;
                        }
                        slabbed$traceCullDecision(pos, direction, dy, neighborPos, resolved, state, neighborState, false);
                    }
                }
            }
            return (mismatchMask & bit) != 0;
        }
    }

    private static void slabbed$traceCullDecision(
            BlockPos pos,
            Direction direction,
            float dy,
            BlockPos neighborPos,
            float neighborDy,
            BlockState state,
            BlockState neighborState,
            boolean vanillaCull
    ) {
        Slabbed.LOGGER.info("[slabbed.render.offset.cullTrace] dyMismatch pos={} state={} face={} dy={} neighborPos={} neighborState={} neighborDy={} vanillaCull={} forcedCull=false",
                pos.toShortString(), state, direction, dy, neighborPos.toShortString(), neighborState, neighborDy, vanillaCull);
    }
}
