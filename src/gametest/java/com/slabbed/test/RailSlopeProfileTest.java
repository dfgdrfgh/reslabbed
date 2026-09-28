package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.RailSlopeProfile;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.RailShape;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * A straight rail's drawn slope fits the seat of the rail it connects to (maintainer ruling,
 * 2026-09-28) — {@link RailSlopeProfile}, the pure rule the client geometry and the outline box
 * are both computed from.
 *
 * <p>Every row builds its scene with {@code setBlockState} so vanilla's own rail-shape update
 * decides each rail's shape exactly as in play, then AUTHORS the seats directly through the
 * placement-height store this line already reads first, unconditionally, on every
 * {@code getYOffset} call — no frozen-store toggle exists on this line, so the write is picked up
 * immediately. Each row checks the shapes it relies on as a premise, so a scene vanilla shaped
 * differently fails loudly instead of proving the wrong thing.
 *
 * <p>MUTATIONS that must redden this class: make {@code flatLift} ignore the neighbour's seat
 * (rows 1, 5, 9, 11, 13, 14, 16, 17), make {@code refitRamp} return 1.0 unconditionally (rows 2, 3,
 * 8, 12, 15, 16), make {@code kinked} answer false (rows 5, 13), drop the flat-reach test (row 7),
 * let a ramp's flat end lift (row 10), drop the ramp floor (row 15), drop the rise cap (row 16),
 * narrow {@code isRail} to the plain rail (row 17), or withhold
 * {@code BaseRailBlockSlopeShapeMixin} (rows 9, 11, 12, 15).
 */
public final class RailSlopeProfileTest {

    private static final double EPS = 1.0e-6d;
    private static final double LOWERED = -0.5d;
    /** Vanilla's outline boxes: flat 2/16, full ramp 8/16 — the two anchors the fitted box interpolates. */
    private static final double FLAT_BOX = 2.0d / 16.0d;
    private static final double RAMP_BOX = 8.0d / 16.0d;

    private static final BlockPos A = new BlockPos(2, 2, 2);

    /**
     * A rail on a stone support at {@code rel}, seated at {@code dy} (0 authors nothing: the control).
     * The seat is written on the rail AND its support, as a lowered rail rides a lowered support in
     * play. Vanilla's shape update runs on placement, so the resulting shape is vanilla's decision.
     *
     * @return the ABSOLUTE rail position
     */
    private static BlockPos rail(TestContext ctx, BlockPos rel, double dy) {
        return rail(ctx, rel, dy, Blocks.RAIL);
    }

    private static BlockPos rail(TestContext ctx, BlockPos rel, double dy, Block kind) {
        ServerWorld world = ctx.getWorld();
        ctx.setBlockState(rel.down(), Blocks.STONE.getDefaultState());
        ctx.setBlockState(rel, kind.getDefaultState());
        BlockPos abs = ctx.getAbsolutePos(rel);
        if (dy != 0.0d) {
            SlabPlacementDyAttachment.record(world, abs.down(), dy);
            SlabPlacementDyAttachment.record(world, abs, dy);
        }
        return abs;
    }

    private static RailShape shapeAt(TestContext ctx, BlockPos abs) {
        BlockState state = ctx.getWorld().getBlockState(abs);
        RailShape shape = RailSlopeProfile.shapeOf(state);
        if (shape == null) {
            throw ctx.createError("premise: expected a rail at " + abs + ", found " + state);
        }
        return shape;
    }

    private static void requireShape(TestContext ctx, BlockPos abs, RailShape expected) {
        RailShape actual = shapeAt(ctx, abs);
        if (actual != expected) {
            throw ctx.createError("premise: vanilla shaped the rail at " + abs + " as "
                    + actual + ", this row needs " + expected);
        }
    }

    private static RailSlopeProfile.Profile profileAt(TestContext ctx, BlockPos abs) {
        ServerWorld world = ctx.getWorld();
        return RailSlopeProfile.resolve(world, abs, world.getBlockState(abs));
    }

    private static void expectProfile(TestContext ctx, BlockPos abs, Direction.Axis axis,
                                      double negativeEnd, double positiveEnd,
                                      double vanillaNegative, double vanillaPositive) {
        RailSlopeProfile.Profile profile = profileAt(ctx, abs);
        if (profile == null) {
            throw ctx.createError("the rail at " + abs + " resolved no profile");
        }
        if (profile.axis() != axis
                || Math.abs(profile.negativeEnd() - negativeEnd) > EPS
                || Math.abs(profile.positiveEnd() - positiveEnd) > EPS
                || Math.abs(profile.vanillaNegativeEnd() - vanillaNegative) > EPS
                || Math.abs(profile.vanillaPositiveEnd() - vanillaPositive) > EPS) {
            throw ctx.createError("the rail at " + abs + " must draw ends (" + negativeEnd
                    + ", " + positiveEnd + ") over vanilla (" + vanillaNegative + ", " + vanillaPositive
                    + ") on axis " + axis + ", got " + profile);
        }
    }

    private static void expectVanilla(TestContext ctx, BlockPos abs) {
        RailSlopeProfile.Profile profile = profileAt(ctx, abs);
        if (profile == null || !profile.isVanilla()) {
            throw ctx.createError("the rail at " + abs + " must keep vanilla geometry, got "
                    + profile);
        }
    }

    private static void expectKinked(TestContext ctx, BlockPos abs, boolean kinked) {
        RailSlopeProfile.Profile profile = profileAt(ctx, abs);
        if (profile == null || profile.kinked() != kinked) {
            throw ctx.createError("the rail at " + abs + (kinked ? " must" : " must not")
                    + " be drawn as a V, got " + profile);
        }
    }

    private static Box outlineAt(TestContext ctx, BlockPos abs) {
        ServerWorld world = ctx.getWorld();
        return world.getBlockState(abs).getOutlineShape(world, abs).getBoundingBox();
    }

    private static void expectOutlineY(TestContext ctx, BlockPos abs, double minY, double maxY) {
        Box box = outlineAt(ctx, abs);
        if (Math.abs(box.minY - minY) > EPS || Math.abs(box.maxY - maxY) > EPS) {
            throw ctx.createError("the outline of the rail at " + abs + " must span Y "
                    + minY + ".." + maxY + " (cell-relative), got " + box.minY + ".." + box.maxY);
        }
    }

    // ── row 1: a flat rail lifts toward a higher neighbour; the higher one stays ─────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatRailLiftsTowardAHigherNeighbour(TestContext ctx) {
        BlockPos a = rail(ctx, A, LOWERED);
        BlockPos b = rail(ctx, A.south(), 0.0d);
        requireShape(ctx, a, RailShape.NORTH_SOUTH);
        requireShape(ctx, b, RailShape.NORTH_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
        expectVanilla(ctx, b);
        ctx.complete();
    }

    // ── row 2: a ramp climbing onto a lowered rail is refitted shorter ───────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rampRefitsShorterOntoALoweredRail(TestContext ctx) {
        BlockPos b = rail(ctx, A.south().up(), LOWERED);
        BlockPos a = rail(ctx, A, 0.0d);
        requireShape(ctx, a, RailShape.ASCENDING_SOUTH);
        requireShape(ctx, b, RailShape.NORTH_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 1.0d);
        expectVanilla(ctx, b);
        ctx.complete();
    }

    // ── row 3: a lowered ramp climbing onto a flush rail is refitted longer ─────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rampRefitsLongerOntoAFlushRail(TestContext ctx) {
        BlockPos b = rail(ctx, A.south().up(), 0.0d);
        BlockPos a = rail(ctx, A, LOWERED);
        requireShape(ctx, a, RailShape.ASCENDING_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 1.5d, 0.0d, 1.0d);
        expectVanilla(ctx, b);
        ctx.complete();
    }

    // ── row 4: a ramp that already meets its neighbour is left exactly vanilla ──────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rampMeetingItsNeighbourStaysVanilla(TestContext ctx) {
        BlockPos b = rail(ctx, A.south().up(), LOWERED);
        BlockPos a = rail(ctx, A, LOWERED);
        requireShape(ctx, a, RailShape.ASCENDING_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 1.0d);
        expectVanilla(ctx, a);
        expectVanilla(ctx, b);
        ctx.complete();
    }

    // ── row 5: a rail in a dip rises to both neighbours as a V — each seam is its own ──────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRailInADipRisesToBothNeighboursAsAV(TestContext ctx) {
        BlockPos a = rail(ctx, A, LOWERED);
        BlockPos n = rail(ctx, A.north(), 0.0d);
        BlockPos s = rail(ctx, A.south(), 0.0d);
        requireShape(ctx, a, RailShape.NORTH_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.5d, 0.5d, 0.0d, 0.0d);
        expectKinked(ctx, a, true);
        // The middle of the V rests on the support; a single lifted end is one plane, not a V.
        RailSlopeProfile.Profile profile = profileAt(ctx, a);
        if (Math.abs(profile.heightAt(0.5d)) > EPS || Math.abs(profile.heightAt(0.25d) - 0.25d) > EPS) {
            throw ctx.createError("a V must rest on its support at the middle, got " + profile);
        }
        expectVanilla(ctx, n);
        expectVanilla(ctx, s);
        ctx.complete();
    }

    // ── row 6: a curve keeps vanilla geometry, box included ──────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCurveKeepsVanillaGeometry(TestContext ctx) {
        BlockPos a = rail(ctx, A, LOWERED);
        rail(ctx, A.south(), 0.0d);
        rail(ctx, A.east(), 0.0d);
        requireShape(ctx, a, RailShape.SOUTH_EAST);
        RailSlopeProfile.Profile profile = profileAt(ctx, a);
        if (profile != null) {
            throw ctx.createError("a curve must resolve no profile, got " + profile);
        }
        // Vanilla's flat box, moved by the seat and nothing else.
        expectOutlineY(ctx, a, LOWERED, LOWERED + FLAT_BOX);
        ctx.complete();
    }

    // ── row 7: a neighbour whose shape does not reach back is not a seam ─────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anUnconnectedNeighbourIsIgnored(TestContext ctx) {
        // An east-west run south of A: its middle rail touches A's cell but reaches east and west.
        rail(ctx, A.south().west(), 0.0d);
        rail(ctx, A.south().east(), 0.0d);
        BlockPos b = rail(ctx, A.south(), 0.0d);
        BlockPos a = rail(ctx, A, LOWERED);
        requireShape(ctx, b, RailShape.EAST_WEST);
        requireShape(ctx, a, RailShape.NORTH_SOUTH);
        expectVanilla(ctx, a);
        ctx.complete();
    }

    // ── row 8: a lower ramp meets a lowered rail from its own side ───────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aLowerRampRefitsFromItsOwnSide(TestContext ctx) {
        BlockPos a = rail(ctx, A.up(), LOWERED);
        BlockPos b = rail(ctx, A.south(), 0.0d);
        requireShape(ctx, a, RailShape.NORTH_SOUTH);
        requireShape(ctx, b, RailShape.ASCENDING_NORTH);
        // A's south end stays on its support: the ramp below it does the meeting.
        expectVanilla(ctx, a);
        expectProfile(ctx, b, Direction.Axis.Z, 0.5d, 0.0d, 1.0d, 0.0d);
        ctx.complete();
    }

    // ── row 9: deeper seats follow the same rule ─────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deeperSeatsFollowTheSameRule(TestContext ctx) {
        BlockPos a = rail(ctx, A, -1.0d);
        BlockPos b = rail(ctx, A.south(), 0.0d);
        requireShape(ctx, a, RailShape.NORTH_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 0.0d);
        expectVanilla(ctx, b);
        // A full-block lift on a flat rail gets exactly vanilla's ramp box, moved by the seat.
        expectOutlineY(ctx, a, -1.0d, -1.0d + RAMP_BOX);
        ctx.complete();
    }

    // ── row 10: a ramp's flat end never lifts, and the flush rail above its foot keeps its step ─

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRampsFlatEndNeverLifts(TestContext ctx) {
        BlockPos b = rail(ctx, A.south().up(), LOWERED);
        BlockPos c = rail(ctx, A.north(), 0.0d);
        BlockPos a = rail(ctx, A, LOWERED);
        requireShape(ctx, a, RailShape.ASCENDING_SOUTH);
        requireShape(ctx, c, RailShape.NORTH_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 1.0d);
        expectVanilla(ctx, b);
        // The higher flat rail cannot dip below its own support either: this seam stays a step,
        // exactly as vanilla drew it (a documented exception, not a gap).
        expectVanilla(ctx, c);
        ctx.complete();
    }

    // ── row 11: the outline box follows the profile ──────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theOutlineFollowsTheProfile(TestContext ctx) {
        BlockPos a = rail(ctx, A, LOWERED);
        BlockPos b = rail(ctx, A.south(), 0.0d);
        requireShape(ctx, a, RailShape.NORTH_SOUTH);
        // Half a lift: halfway between vanilla's flat and ramp boxes, moved by the seat.
        expectOutlineY(ctx, a, LOWERED, LOWERED + FLAT_BOX + (RAMP_BOX - FLAT_BOX) * 0.5d);
        // The flush neighbour keeps vanilla's flat box.
        expectOutlineY(ctx, b, 0.0d, FLAT_BOX);
        ctx.complete();
    }

    // ── row 12: a refitted ramp's box follows too ────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRefittedRampsOutlineFollowsToo(TestContext ctx) {
        rail(ctx, A.south().up(), 0.0d);
        BlockPos a = rail(ctx, A, LOWERED);
        requireShape(ctx, a, RailShape.ASCENDING_SOUTH);
        expectOutlineY(ctx, a, LOWERED, LOWERED + FLAT_BOX + (RAMP_BOX - FLAT_BOX) * 1.5d);
        ctx.complete();
    }

    // ── row 13: the east-west axis mirrors north-south, dip included ─────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void eastWestAxisMirrorsNorthSouth(TestContext ctx) {
        BlockPos a = rail(ctx, A, LOWERED);
        BlockPos b = rail(ctx, A.east(), 0.0d);
        requireShape(ctx, a, RailShape.EAST_WEST);
        requireShape(ctx, b, RailShape.EAST_WEST);
        expectProfile(ctx, a, Direction.Axis.X, 0.0d, 0.5d, 0.0d, 0.0d);
        rail(ctx, A.west(), 0.0d);
        requireShape(ctx, a, RailShape.EAST_WEST);
        expectProfile(ctx, a, Direction.Axis.X, 0.5d, 0.5d, 0.0d, 0.0d);
        expectKinked(ctx, a, true);
        ctx.complete();
    }

    // ── row 15: a ramp never sinks below its support ─────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRampNeverSinksBelowItsSupport(TestContext ctx) {
        // The rail one cell up is seated a block and a half down: drawn BELOW this rail's base.
        BlockPos b = rail(ctx, A.south().up(), -1.5d);
        BlockPos a = rail(ctx, A, 0.0d);
        requireShape(ctx, a, RailShape.ASCENDING_SOUTH);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 0.0d, 0.0d, 1.0d);
        expectOutlineY(ctx, a, 0.0d, FLAT_BOX);
        expectVanilla(ctx, b);
        ctx.complete();
    }

    // ── row 16: a rise is capped where a rail would become a wall ────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRiseIsCappedWhereARailWouldBeAWall(TestContext ctx) {
        BlockPos flat = rail(ctx, A, -2.0d);
        BlockPos flatNeighbour = rail(ctx, A.south(), 0.0d);
        requireShape(ctx, flat, RailShape.NORTH_SOUTH);
        expectProfile(ctx, flat, Direction.Axis.Z, 0.0d, RailSlopeProfile.MAX_RISE, 0.0d, 0.0d);
        expectVanilla(ctx, flatNeighbour);

        BlockPos rampTop = rail(ctx, A.east(3).south().up(), 0.0d);
        BlockPos ramp = rail(ctx, A.east(3), -3.0d);
        requireShape(ctx, ramp, RailShape.ASCENDING_SOUTH);
        expectProfile(ctx, ramp, Direction.Axis.Z, 0.0d, RailSlopeProfile.MAX_RISE, 0.0d, 1.0d);
        expectVanilla(ctx, rampTop);
        ctx.complete();
    }

    // ── row 17: every rail kind fits the same ────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void everyRailKindFitsTheSame(TestContext ctx) {
        Block[] kinds = {Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL};
        for (int i = 0; i < kinds.length; i++) {
            BlockPos at = A.east(i * 2);
            BlockPos a = rail(ctx, at, LOWERED, kinds[i]);
            BlockPos b = rail(ctx, at.south(), 0.0d, kinds[i]);
            requireShape(ctx, a, RailShape.NORTH_SOUTH);
            requireShape(ctx, b, RailShape.NORTH_SOUTH);
            expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
            expectVanilla(ctx, b);
        }
        ctx.complete();
    }

    // ── row 14 (LAW.md pin): the slope follows the neighbour, the seat never does ────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aNeighbourEditNeverMovesTheSeat(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos a = rail(ctx, A, LOWERED);
        double before = SlabSupport.getYOffset(world, a, world.getBlockState(a));
        if (Math.abs(before - LOWERED) > EPS) {
            throw ctx.createError("premise: the rail must read its authored seat " + LOWERED
                    + ", got " + before);
        }
        BlockPos b = rail(ctx, A.south(), 0.0d);
        expectProfile(ctx, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
        double withNeighbour = SlabSupport.getYOffset(world, a, world.getBlockState(a));
        ctx.setBlockState(A.south(), Blocks.AIR.getDefaultState());
        expectVanilla(ctx, a);
        double afterRemoval = SlabSupport.getYOffset(world, a, world.getBlockState(a));
        if (Math.abs(withNeighbour - LOWERED) > EPS || Math.abs(afterRemoval - LOWERED) > EPS) {
            throw ctx.createError("LAW.md: a neighbour edit moved the rail's seat: "
                    + before + " -> " + withNeighbour + " -> " + afterRemoval + " (b was " + b + ")");
        }
        ctx.complete();
    }
}
