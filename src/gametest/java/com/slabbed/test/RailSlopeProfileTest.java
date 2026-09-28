package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.RailSlopeProfile;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A straight rail's drawn slope fits the seat of the rail it connects to (maintainer ruling,
 * 2026-09-28) — {@link RailSlopeProfile}, the pure rule the client geometry and the outline box
 * are both computed from.
 *
 * <p>Every row builds its scene with {@code setBlock} so vanilla's own rail-shape update decides
 * each rail's shape exactly as in play, then AUTHORS the seats as stored placement facts, the
 * first height authority on this line. The fact is written on the rail AND its stone support, as
 * a lowered rail rides a lowered support in play: a flat rail follows its support's top face, a
 * ramp reads its own fact. Every support column stands on stone down to {@link #FLOOR_Y}, so no
 * support is ever cantilevered over air. Each row checks, as premises, the rail shapes it relies
 * on and every rail's seat read back through {@code SlabSupport.getYOffset} exactly as the model
 * and the outline read it, so a scene that came out differently fails loudly instead of proving
 * the wrong thing.
 *
 * <p>MUTATIONS that must redden this class: make {@code flatLift} ignore the neighbour's seat
 * (rows 1, 5, 9, 11, 13, 14, 16, 17), make {@code refitRamp} return 1.0 unconditionally (rows 2, 3,
 * 8, 12, 15, 16), make {@code kinked} answer false (rows 5, 13), drop the flat-reach test (row 7),
 * let a ramp's flat end lift (row 10), drop the ramp floor (row 15), drop the rise cap (row 16),
 * narrow {@code isRail} to the plain rail (row 17), or withhold
 * {@code BaseRailBlockSlopeShapeMixin} (rows 9, 11, 12, 15).
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class RailSlopeProfileTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;
    private static final double LOWERED = -0.5d;
    /** Vanilla's outline boxes: flat 2/16, full ramp 8/16 — the two anchors the fitted box interpolates. */
    private static final double FLAT_BOX = 2.0d / 16.0d;
    private static final double RAMP_BOX = 8.0d / 16.0d;

    /** Every support column rises from this relative layer. */
    private static final int FLOOR_Y = 1;
    private static final BlockPos A = new BlockPos(2, 3, 2);

    private static GameTestAssertException fail(String message) {
        return new GameTestAssertException(message);
    }

    private static double seatOf(ServerLevel level, BlockPos abs) {
        return SlabSupport.getYOffset(level, abs, level.getBlockState(abs));
    }

    /**
     * A rail on a stone support at {@code rel}, seated at {@code dy} (0 authors flush: the control).
     * The seat is written on the rail AND its support. Vanilla's shape update runs on placement, so
     * the resulting shape is vanilla's decision.
     *
     * @return the ABSOLUTE rail position
     */
    private static BlockPos rail(GameTestHelper helper, BlockPos rel, double dy) {
        return rail(helper, rel, dy, Blocks.RAIL);
    }

    private static BlockPos rail(GameTestHelper helper, BlockPos rel, double dy, Block kind) {
        ServerLevel level = helper.getLevel();
        for (int y = FLOOR_Y; y < rel.getY(); y++) {
            helper.setBlock(new BlockPos(rel.getX(), y, rel.getZ()), Blocks.STONE.defaultBlockState());
        }
        helper.setBlock(rel, kind.defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        if (!(level.getBlockState(abs).getBlock() instanceof BaseRailBlock)) {
            throw fail("premise: the rail at " + abs + " did not survive placement, found "
                    + level.getBlockState(abs));
        }
        authorSeat(helper, abs.below(), dy);
        authorSeat(helper, abs, dy);
        requireSeat(helper, abs, dy);
        return abs;
    }

    /** Writes {@code dy} as the cell's stored fact; flush clears any fact the cell still carries. */
    private static void authorSeat(GameTestHelper helper, BlockPos abs, double dy) {
        LevelChunk chunk = helper.getLevel().getChunkAt(abs);
        if (dy == 0.0d) {
            SlabPlacementHeightAttachment.remove(chunk, abs);
            return;
        }
        int halfSteps = (int) Math.round(dy / 0.5d);
        boolean written = SlabPlacementHeightAttachment.putHalfSteps(chunk, abs, halfSteps)
                || SlabPlacementHeightAttachment.storedHalfSteps(chunk, abs).orElse(Integer.MIN_VALUE)
                        == halfSteps;
        if (!written) {
            throw fail("premise: the cell at " + abs + " must accept its seat fact " + dy);
        }
    }

    private static void requireSeat(GameTestHelper helper, BlockPos abs, double expected) {
        double actual = seatOf(helper.getLevel(), abs);
        if (Math.abs(actual - expected) > EPS) {
            throw fail("premise: the rail at " + abs + " must read its authored seat " + expected
                    + ", got " + actual);
        }
    }

    private static RailShape shapeAt(GameTestHelper helper, BlockPos abs) {
        BlockState state = helper.getLevel().getBlockState(abs);
        RailShape shape = RailSlopeProfile.shapeOf(state);
        if (shape == null) {
            throw fail("premise: expected a rail at " + abs + ", found " + state);
        }
        return shape;
    }

    private static void requireShape(GameTestHelper helper, BlockPos abs, RailShape expected) {
        RailShape actual = shapeAt(helper, abs);
        if (actual != expected) {
            throw fail("premise: vanilla shaped the rail at " + abs + " as " + actual
                    + ", this row needs " + expected);
        }
    }

    private static RailSlopeProfile.Profile profileAt(GameTestHelper helper, BlockPos abs) {
        ServerLevel level = helper.getLevel();
        return RailSlopeProfile.resolve(level, abs, level.getBlockState(abs));
    }

    private static void expectProfile(GameTestHelper helper, BlockPos abs, Direction.Axis axis,
                                      double negativeEnd, double positiveEnd,
                                      double vanillaNegative, double vanillaPositive) {
        RailSlopeProfile.Profile profile = profileAt(helper, abs);
        if (profile == null) {
            throw fail("the rail at " + abs + " resolved no profile");
        }
        if (profile.axis() != axis
                || Math.abs(profile.negativeEnd() - negativeEnd) > EPS
                || Math.abs(profile.positiveEnd() - positiveEnd) > EPS
                || Math.abs(profile.vanillaNegativeEnd() - vanillaNegative) > EPS
                || Math.abs(profile.vanillaPositiveEnd() - vanillaPositive) > EPS) {
            throw fail("the rail at " + abs + " (seat " + seatOf(helper.getLevel(), abs)
                    + ") must draw ends (" + negativeEnd + ", " + positiveEnd + ") over vanilla ("
                    + vanillaNegative + ", " + vanillaPositive + ") on axis " + axis + ", got " + profile);
        }
    }

    private static void expectVanilla(GameTestHelper helper, BlockPos abs) {
        RailSlopeProfile.Profile profile = profileAt(helper, abs);
        if (profile == null || !profile.isVanilla()) {
            throw fail("the rail at " + abs + " must keep vanilla geometry, got " + profile);
        }
    }

    private static void expectKinked(GameTestHelper helper, BlockPos abs, boolean kinked) {
        RailSlopeProfile.Profile profile = profileAt(helper, abs);
        if (profile == null || profile.kinked() != kinked) {
            throw fail("the rail at " + abs + (kinked ? " must" : " must not")
                    + " be drawn as a V, got " + profile);
        }
    }

    private static AABB outlineAt(GameTestHelper helper, BlockPos abs) {
        ServerLevel level = helper.getLevel();
        return level.getBlockState(abs).getShape(level, abs).bounds();
    }

    private static void expectOutlineY(GameTestHelper helper, BlockPos abs, double minY, double maxY) {
        AABB box = outlineAt(helper, abs);
        if (Math.abs(box.minY - minY) > EPS || Math.abs(box.maxY - maxY) > EPS) {
            throw fail("the outline of the rail at " + abs + " must span Y " + minY + ".." + maxY
                    + " (cell-relative), got " + box.minY + ".." + box.maxY);
        }
    }

    // ── row 1: a flat rail lifts toward a higher neighbour; the higher one stays ─────────────────

    @GameTest(template = TEMPLATE)
    public void flatRailLiftsTowardAHigherNeighbour(GameTestHelper helper) {
        BlockPos a = rail(helper, A, LOWERED);
        BlockPos b = rail(helper, A.south(), 0.0d);
        requireShape(helper, a, RailShape.NORTH_SOUTH);
        requireShape(helper, b, RailShape.NORTH_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
        expectVanilla(helper, b);
        helper.succeed();
    }

    // ── row 2: a ramp climbing onto a lowered rail is refitted shorter ───────────────────────────

    @GameTest(template = TEMPLATE)
    public void rampRefitsShorterOntoALoweredRail(GameTestHelper helper) {
        BlockPos b = rail(helper, A.south().above(), LOWERED);
        BlockPos a = rail(helper, A, 0.0d);
        requireShape(helper, a, RailShape.ASCENDING_SOUTH);
        requireShape(helper, b, RailShape.NORTH_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 1.0d);
        expectVanilla(helper, b);
        helper.succeed();
    }

    // ── row 3: a lowered ramp climbing onto a flush rail is refitted longer ─────────────────────

    @GameTest(template = TEMPLATE)
    public void rampRefitsLongerOntoAFlushRail(GameTestHelper helper) {
        BlockPos b = rail(helper, A.south().above(), 0.0d);
        BlockPos a = rail(helper, A, LOWERED);
        requireShape(helper, a, RailShape.ASCENDING_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.5d, 0.0d, 1.0d);
        expectVanilla(helper, b);
        helper.succeed();
    }

    // ── row 4: a ramp that already meets its neighbour is left exactly vanilla ──────────────────

    @GameTest(template = TEMPLATE)
    public void rampMeetingItsNeighbourStaysVanilla(GameTestHelper helper) {
        BlockPos b = rail(helper, A.south().above(), LOWERED);
        BlockPos a = rail(helper, A, LOWERED);
        requireShape(helper, a, RailShape.ASCENDING_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 1.0d);
        expectVanilla(helper, a);
        expectVanilla(helper, b);
        helper.succeed();
    }

    // ── row 5: a rail in a dip rises to both neighbours as a V — each seam is its own ──────────

    @GameTest(template = TEMPLATE)
    public void aRailInADipRisesToBothNeighboursAsAV(GameTestHelper helper) {
        BlockPos a = rail(helper, A, LOWERED);
        BlockPos n = rail(helper, A.north(), 0.0d);
        BlockPos s = rail(helper, A.south(), 0.0d);
        requireShape(helper, a, RailShape.NORTH_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.5d, 0.5d, 0.0d, 0.0d);
        expectKinked(helper, a, true);
        // The middle of the V rests on the support; a single lifted end is one plane, not a V.
        RailSlopeProfile.Profile profile = profileAt(helper, a);
        if (Math.abs(profile.heightAt(0.5d)) > EPS || Math.abs(profile.heightAt(0.25d) - 0.25d) > EPS) {
            throw fail("a V must rest on its support at the middle, got " + profile);
        }
        expectVanilla(helper, n);
        expectVanilla(helper, s);
        helper.succeed();
    }

    // ── row 6: a curve keeps vanilla geometry, box included ──────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public void aCurveKeepsVanillaGeometry(GameTestHelper helper) {
        BlockPos a = rail(helper, A, LOWERED);
        rail(helper, A.south(), 0.0d);
        rail(helper, A.east(), 0.0d);
        requireShape(helper, a, RailShape.SOUTH_EAST);
        if (profileAt(helper, a) != null) {
            throw fail("a curve must resolve no profile, got " + profileAt(helper, a));
        }
        // Vanilla's flat box, moved by the seat and nothing else.
        expectOutlineY(helper, a, LOWERED, LOWERED + FLAT_BOX);
        helper.succeed();
    }

    // ── row 7: a neighbour whose shape does not reach back is not a seam ─────────────────────────

    @GameTest(template = TEMPLATE)
    public void anUnconnectedNeighbourIsIgnored(GameTestHelper helper) {
        // An east-west run south of A: its middle rail touches A's cell but reaches east and west.
        rail(helper, A.south().west(), 0.0d);
        rail(helper, A.south().east(), 0.0d);
        BlockPos b = rail(helper, A.south(), 0.0d);
        BlockPos a = rail(helper, A, LOWERED);
        requireShape(helper, b, RailShape.EAST_WEST);
        requireShape(helper, a, RailShape.NORTH_SOUTH);
        expectVanilla(helper, a);
        helper.succeed();
    }

    // ── row 8: a lower ramp meets a lowered rail from its own side ───────────────────────────────

    @GameTest(template = TEMPLATE)
    public void aLowerRampRefitsFromItsOwnSide(GameTestHelper helper) {
        BlockPos a = rail(helper, A.above(), LOWERED);
        BlockPos b = rail(helper, A.south(), 0.0d);
        requireShape(helper, a, RailShape.NORTH_SOUTH);
        requireShape(helper, b, RailShape.ASCENDING_NORTH);
        // A's south end stays on its support: the ramp below it does the meeting.
        expectVanilla(helper, a);
        expectProfile(helper, b, Direction.Axis.Z, 0.5d, 0.0d, 1.0d, 0.0d);
        helper.succeed();
    }

    // ── row 9: deeper seats follow the same rule ─────────────────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public void deeperSeatsFollowTheSameRule(GameTestHelper helper) {
        BlockPos a = rail(helper, A, -1.0d);
        BlockPos b = rail(helper, A.south(), 0.0d);
        requireShape(helper, a, RailShape.NORTH_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 0.0d);
        expectVanilla(helper, b);
        // A full-block lift on a flat rail gets exactly vanilla's ramp box, moved by the seat.
        expectOutlineY(helper, a, -1.0d, -1.0d + RAMP_BOX);
        helper.succeed();
    }

    // ── row 10: a ramp's flat end never lifts, and the flush rail above its foot keeps its step ─

    @GameTest(template = TEMPLATE)
    public void aRampsFlatEndNeverLifts(GameTestHelper helper) {
        BlockPos b = rail(helper, A.south().above(), LOWERED);
        BlockPos c = rail(helper, A.north(), 0.0d);
        BlockPos a = rail(helper, A, LOWERED);
        requireShape(helper, a, RailShape.ASCENDING_SOUTH);
        requireShape(helper, c, RailShape.NORTH_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 1.0d, 0.0d, 1.0d);
        expectVanilla(helper, b);
        // The higher flat rail cannot dip below its own support either: this seam stays a step,
        // exactly as vanilla drew it (a documented exception, not a gap).
        expectVanilla(helper, c);
        helper.succeed();
    }

    // ── row 11: the outline box follows the profile ──────────────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public void theOutlineFollowsTheProfile(GameTestHelper helper) {
        BlockPos a = rail(helper, A, LOWERED);
        BlockPos b = rail(helper, A.south(), 0.0d);
        requireShape(helper, a, RailShape.NORTH_SOUTH);
        // Half a lift: halfway between vanilla's flat and ramp boxes, moved by the seat.
        expectOutlineY(helper, a, LOWERED, LOWERED + FLAT_BOX + (RAMP_BOX - FLAT_BOX) * 0.5d);
        // The flush neighbour keeps vanilla's flat box.
        expectOutlineY(helper, b, 0.0d, FLAT_BOX);
        helper.succeed();
    }

    // ── row 12: a refitted ramp's box follows too ────────────────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public void aRefittedRampsOutlineFollowsToo(GameTestHelper helper) {
        rail(helper, A.south().above(), 0.0d);
        BlockPos a = rail(helper, A, LOWERED);
        requireShape(helper, a, RailShape.ASCENDING_SOUTH);
        expectOutlineY(helper, a, LOWERED, LOWERED + FLAT_BOX + (RAMP_BOX - FLAT_BOX) * 1.5d);
        helper.succeed();
    }

    // ── row 13: the east-west axis mirrors north-south, dip included ─────────────────────────────

    @GameTest(template = TEMPLATE)
    public void eastWestAxisMirrorsNorthSouth(GameTestHelper helper) {
        BlockPos a = rail(helper, A, LOWERED);
        BlockPos b = rail(helper, A.east(), 0.0d);
        requireShape(helper, a, RailShape.EAST_WEST);
        requireShape(helper, b, RailShape.EAST_WEST);
        expectProfile(helper, a, Direction.Axis.X, 0.0d, 0.5d, 0.0d, 0.0d);
        rail(helper, A.west(), 0.0d);
        requireShape(helper, a, RailShape.EAST_WEST);
        expectProfile(helper, a, Direction.Axis.X, 0.5d, 0.5d, 0.0d, 0.0d);
        expectKinked(helper, a, true);
        helper.succeed();
    }

    // ── row 15: a ramp never sinks below its support ─────────────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public void aRampNeverSinksBelowItsSupport(GameTestHelper helper) {
        // The rail one cell up is seated a block and a half down: drawn BELOW this rail's base.
        BlockPos b = rail(helper, A.south().above(), -1.5d);
        BlockPos a = rail(helper, A, 0.0d);
        requireShape(helper, a, RailShape.ASCENDING_SOUTH);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.0d, 0.0d, 1.0d);
        expectOutlineY(helper, a, 0.0d, FLAT_BOX);
        expectVanilla(helper, b);
        helper.succeed();
    }

    // ── row 16: a rise is capped where a rail would become a wall ────────────────────────────────

    @GameTest(template = TEMPLATE)
    public void aRiseIsCappedWhereARailWouldBeAWall(GameTestHelper helper) {
        BlockPos flat = rail(helper, A, -2.0d);
        BlockPos flatNeighbour = rail(helper, A.south(), 0.0d);
        requireShape(helper, flat, RailShape.NORTH_SOUTH);
        expectProfile(helper, flat, Direction.Axis.Z, 0.0d, RailSlopeProfile.MAX_RISE, 0.0d, 0.0d);
        expectVanilla(helper, flatNeighbour);

        BlockPos rampTop = rail(helper, A.east(3).south().above(), 0.0d);
        BlockPos ramp = rail(helper, A.east(3), -3.0d);
        requireShape(helper, ramp, RailShape.ASCENDING_SOUTH);
        expectProfile(helper, ramp, Direction.Axis.Z, 0.0d, RailSlopeProfile.MAX_RISE, 0.0d, 1.0d);
        expectVanilla(helper, rampTop);
        helper.succeed();
    }

    // ── row 17: every rail kind fits the same ────────────────────────────────────────────────────

    @GameTest(template = TEMPLATE)
    public void everyRailKindFitsTheSame(GameTestHelper helper) {
        Block[] kinds = {Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL};
        for (int i = 0; i < kinds.length; i++) {
            BlockPos at = A.east(i * 2);
            BlockPos a = rail(helper, at, LOWERED, kinds[i]);
            BlockPos b = rail(helper, at.south(), 0.0d, kinds[i]);
            requireShape(helper, a, RailShape.NORTH_SOUTH);
            requireShape(helper, b, RailShape.NORTH_SOUTH);
            expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
            expectVanilla(helper, b);
        }
        helper.succeed();
    }

    // ── row 14 (LAW.md pin): the slope follows the neighbour, the seat never does ────────────────

    @GameTest(template = TEMPLATE)
    public void aNeighbourEditNeverMovesTheSeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = rail(helper, A, LOWERED);
        double before = seatOf(level, a);
        BlockPos b = rail(helper, A.south(), 0.0d);
        expectProfile(helper, a, Direction.Axis.Z, 0.0d, 0.5d, 0.0d, 0.0d);
        double withNeighbour = seatOf(level, a);
        helper.setBlock(A.south(), Blocks.AIR.defaultBlockState());
        expectVanilla(helper, a);
        double afterRemoval = seatOf(level, a);
        if (Math.abs(before - LOWERED) > EPS
                || Math.abs(withNeighbour - LOWERED) > EPS
                || Math.abs(afterRemoval - LOWERED) > EPS) {
            throw fail("LAW.md: a neighbour edit moved the rail's seat: " + before + " -> "
                    + withNeighbour + " -> " + afterRemoval + " (b was " + b + ")");
        }
        helper.succeed();
    }
}
