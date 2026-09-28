package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A redstone wire beside a lowered half-step reads the wire drawn on the slab next to it (maintainer
 * ruling, 2026-09-28) — {@link com.slabbed.util.WireVisualSignal} and its hook in the wire's
 * incoming-signal read.
 *
 * <p>The scene is a stone floor at row 1. The reader wire sits on stone at row 2 (wire at row 3);
 * the column beside it carries a bottom slab at row 3 with a wire at row 4 seated -0.5, drawn inside
 * the slab's cell half a block above the reader. A redstone block beside the lowered wire feeds it.
 * Wires are placed and then re-shaped from their surroundings, the way placement does in play, so
 * connection arms are the game's own answer. The reader's arm toward the slab IS vanilla's own
 * answer: a bottom slab's top counts as a surface a wire can stand on, so the wire beside it already
 * connected toward the slab — only the power read was missing. The arm rows pin that no connection
 * hook is needed on this line; a port whose slab top is not such a surface must add one.
 *
 * <p>Seats follow this line's rail test. Flush slabs carry no stored fact; a slab lowered onto
 * another slab carries its lowered seat as a stored placement fact, the first height authority. A
 * wire is a thin floor layer that keeps no fact in play: its seat follows its support's resolved top
 * face, so a wire on a flush bottom slab reads -0.5 and one on a lowered bottom slab reads -1.0. The
 * fact written on each wire mirrors the rail test and is not what the wire reads while it stays
 * thin. Every wire's seat is read back through {@code SlabSupport.getYOffset} — the read the drawn
 * cell is named from — as a premise, so a scene that came out differently fails loudly instead of
 * proving the wrong thing.
 *
 * <p>MUTATIONS that must redden this class: withhold {@code RedstoneWireVisualStepMixin} (rows 1, 4,
 * 5, 7, 8); drop the drawn-cell test in {@code WireVisualSignal.wireDrawnIn} (row 2, the top-slab
 * control); drop its one-block bound (row 9).
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class RedstoneWireVisualStepTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;
    private static final double FLUSH = 0.0d;
    private static final double LOWERED = -0.5d;
    private static final double LOWERED_TWICE = -1.0d;
    private static final int FLOOR_Y = 1;
    private static final int Z = 2;

    private static GameTestAssertException fail(String message) {
        return new GameTestAssertException(message);
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

    private static BlockState bottomSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static void floor(GameTestHelper helper, int minX, int maxX) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = Z - 1; z <= Z + 1; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static void stone(GameTestHelper helper, int x, int y) {
        helper.setBlock(new BlockPos(x, y, Z), Blocks.STONE.defaultBlockState());
    }

    private static void slab(GameTestHelper helper, int x, int y, BlockState slab, double seat) {
        BlockPos rel = new BlockPos(x, y, Z);
        helper.setBlock(rel, slab);
        authorSeat(helper, helper.absolutePos(rel), seat);
    }

    private static void source(GameTestHelper helper, int x, int y) {
        helper.setBlock(new BlockPos(x, y, Z), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    /**
     * Places a wire at the given seat and re-shapes it from its surroundings, as placement does.
     *
     * @return the ABSOLUTE wire position
     */
    private static BlockPos wire(GameTestHelper helper, int x, int y, double seat) {
        return wire(helper, x, y, Z, seat);
    }

    /**
     * A flush wire one cell north of ({@code x}, {@code y}) on its own stone. An isolated wire is
     * drawn by vanilla as a straight line, with an arm on BOTH sides of one axis whether or not
     * anything is there; a real branch on the other axis switches that rule off, so the arm toward
     * the slab then reports the game's actual connection and nothing else.
     */
    private static BlockPos branchNorth(GameTestHelper helper, int x, int y) {
        helper.setBlock(new BlockPos(x, y - 1, Z - 1), Blocks.STONE.defaultBlockState());
        return wire(helper, x, y, Z - 1, FLUSH);
    }

    private static BlockPos wire(GameTestHelper helper, int x, int y, int z, double seat) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(x, y, z);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, Blocks.REDSTONE_WIRE.defaultBlockState());
        authorSeat(helper, abs, seat);
        level.setBlock(abs, Block.updateFromNeighbourShapes(level.getBlockState(abs), level, abs), 3);
        authorSeat(helper, abs, seat);
        BlockState placed = level.getBlockState(abs);
        if (!(placed.getBlock() instanceof RedStoneWireBlock)) {
            throw fail("premise: the wire at " + abs + " did not survive placement, found " + placed);
        }
        double read = SlabSupport.getYOffset(level, abs, placed);
        if (Math.abs(read - seat) > EPS) {
            throw fail("premise: the wire at " + abs + " must read seat " + seat + ", got " + read);
        }
        return abs;
    }

    private static BlockState wireState(GameTestHelper helper, BlockPos abs) {
        BlockState state = helper.getLevel().getBlockState(abs);
        if (!(state.getBlock() instanceof RedStoneWireBlock)) {
            throw fail("premise: expected a wire at " + abs + ", found " + state);
        }
        return state;
    }

    private static void expectPower(GameTestHelper helper, BlockPos abs, int expected, String why) {
        int power = wireState(helper, abs).getValue(RedStoneWireBlock.POWER);
        if (power != expected) {
            throw fail("the wire at " + abs + " must carry power " + expected + ", got " + power
                    + ": " + why);
        }
    }

    private static void expectSide(GameTestHelper helper, BlockPos abs, Direction toward, RedstoneSide expected, String why) {
        RedstoneSide side = wireState(helper, abs).getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(toward));
        if (side != expected) {
            throw fail("the wire at " + abs + " must connect " + expected + " toward " + toward
                    + ", got " + side + ": " + why);
        }
    }

    /**
     * Reader on stone at x=2 (row 3) with a branch to its north; a bottom slab at x=3 row 3 with the
     * lowered wire at row 4.
     */
    private static BlockPos[] halfStep(GameTestHelper helper, double readerSeat) {
        floor(helper, 0, 5);
        if (readerSeat < 0.0d) {
            slab(helper, 2, 2, bottomSlab(), FLUSH);
        } else {
            stone(helper, 2, 2);
        }
        BlockPos reader = wire(helper, 2, 3, readerSeat);
        branchNorth(helper, 2, 3);
        stone(helper, 3, 2);
        slab(helper, 3, 3, bottomSlab(), FLUSH);
        BlockPos lowered = wire(helper, 3, 4, LOWERED);
        expectSide(helper, reader, Direction.NORTH, RedstoneSide.SIDE, "premise: the branch switches the line rule off");
        expectSide(helper, reader, Direction.WEST, RedstoneSide.NONE, "premise: nothing west of the reader");
        return new BlockPos[] {reader, lowered};
    }

    // ── row 1: the wire beside the slab reads the wire drawn on it ───────────────────────────────

    @GameTest(template = TEMPLATE)
    public void aWireBesideAHalfStepReadsTheWireDrawnOnTheSlab(GameTestHelper helper) {
        BlockPos[] wires = halfStep(helper, FLUSH);
        expectPower(helper, wires[0], 0, "nothing feeds either wire yet");
        stone(helper, 4, 3);
        source(helper, 4, 4);
        expectPower(helper, wires[1], 15, "premise: the redstone block feeds the lowered wire");
        expectPower(helper, wires[0], 14, "the wire beside the slab reads the wire drawn half a block above it");
        expectSide(helper, wires[0], Direction.EAST, RedstoneSide.SIDE, "the reader's arm reaches the slab");
        expectSide(helper, wires[1], Direction.WEST, RedstoneSide.SIDE, "vanilla's own step-down arm");
        helper.succeed();
    }

    // ── row 2: a wire on a top slab is drawn in its own cell — vanilla's one-way step is kept ────

    @GameTest(template = TEMPLATE)
    public void aWireOnATopSlabKeepsVanillasOneWayStep(GameTestHelper helper) {
        floor(helper, 0, 5);
        stone(helper, 2, 2);
        BlockPos reader = wire(helper, 2, 3, FLUSH);
        stone(helper, 3, 2);
        slab(helper, 3, 3, topSlab(), FLUSH);
        BlockPos upper = wire(helper, 3, 4, FLUSH);
        stone(helper, 4, 3);
        source(helper, 4, 4);
        expectPower(helper, upper, 15, "premise: the redstone block feeds the wire on the top slab");
        expectPower(helper, reader, 0, "a wire on a top slab is a full step up and drawn in its own cell:"
                + " vanilla never steps up onto a non-conductor, and that stays");
        helper.succeed();
    }

    // ── row 3: power still climbs the half-step, as it always did ───────────────────────────────

    @GameTest(template = TEMPLATE)
    public void powerStillClimbsTheHalfStep(GameTestHelper helper) {
        BlockPos[] wires = halfStep(helper, FLUSH);
        stone(helper, 1, 2);
        source(helper, 1, 3);
        expectPower(helper, wires[0], 15, "premise: the redstone block feeds the reader");
        expectPower(helper, wires[1], 14, "the lowered wire reads down past the air over the reader, as in vanilla");
        helper.succeed();
    }

    // ── row 4: the read follows the source and the lowered wire away again ──────────────────────

    @GameTest(template = TEMPLATE)
    public void theReadFollowsTheSourceAndTheLoweredWireAway(GameTestHelper helper) {
        BlockPos[] wires = halfStep(helper, FLUSH);
        stone(helper, 4, 3);
        source(helper, 4, 4);
        expectPower(helper, wires[0], 14, "premise: the reader reads the lowered wire");
        expectSide(helper, wires[0], Direction.EAST, RedstoneSide.SIDE, "premise: the reader's arm reaches the slab");
        helper.setBlock(new BlockPos(4, 4, Z), Blocks.AIR.defaultBlockState());
        expectPower(helper, wires[1], 0, "premise: the source is gone");
        expectPower(helper, wires[0], 0, "the reader follows the lowered wire down to zero");
        source(helper, 4, 4);
        expectPower(helper, wires[0], 14, "and back up when the source returns");
        helper.setBlock(new BlockPos(3, 4, Z), Blocks.AIR.defaultBlockState());
        expectPower(helper, wires[0], 0, "the lowered wire is gone");
        expectSide(helper, wires[0], Direction.EAST, RedstoneSide.NONE, "nothing is drawn beside the reader any more");
        expectSide(helper, wires[0], Direction.NORTH, RedstoneSide.SIDE, "the branch is still there");
        helper.succeed();
    }

    /** Four wires descending in half-steps from x=1 (row 3, flush) to x=4 (row 2, lowered). */
    private static BlockPos[] staircase(GameTestHelper helper) {
        floor(helper, 0, 6);
        stone(helper, 1, 2);
        BlockPos first = wire(helper, 1, 3, FLUSH);
        slab(helper, 2, 2, bottomSlab(), FLUSH);
        BlockPos second = wire(helper, 2, 3, LOWERED);
        BlockPos third = wire(helper, 3, 2, FLUSH);
        branchNorth(helper, 3, 2);
        slab(helper, 4, 1, bottomSlab(), FLUSH);
        BlockPos fourth = wire(helper, 4, 2, LOWERED);
        expectSide(helper, third, Direction.NORTH, RedstoneSide.SIDE, "premise: the branch switches the line rule off");
        return new BlockPos[] {first, second, third, fourth};
    }

    // ── rows 5 and 6: a staircase of half-steps carries power down as well as up ────────────────

    @GameTest(template = TEMPLATE)
    public void aStaircaseOfHalfStepsCarriesPowerDown(GameTestHelper helper) {
        BlockPos[] wires = staircase(helper);
        stone(helper, 0, 2);
        source(helper, 0, 3);
        expectPower(helper, wires[0], 15, "premise: the top of the stairs is fed");
        expectPower(helper, wires[1], 14, "flat onto the first slab");
        expectPower(helper, wires[2], 13, "down the half-step: the wire below reads the wire drawn on the slab");
        expectPower(helper, wires[3], 12, "flat onto the second slab");
        expectSide(helper, wires[2], Direction.WEST, RedstoneSide.SIDE, "the arm below the half-step reaches the slab");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void aStaircaseOfHalfStepsCarriesPowerUp(GameTestHelper helper) {
        BlockPos[] wires = staircase(helper);
        source(helper, 5, 2);
        expectPower(helper, wires[3], 15, "premise: the foot of the stairs is fed");
        expectPower(helper, wires[2], 14, "flat off the second slab");
        expectPower(helper, wires[1], 13, "up the half-step, as vanilla always allowed");
        expectPower(helper, wires[0], 12, "flat off the first slab");
        helper.succeed();
    }

    // ── row 7: a wire lowered a whole block, drawn level with the reader, connects flat ─────────

    @GameTest(template = TEMPLATE)
    public void aWireLoweredAWholeBlockConnectsFlatWithTheWireBesideIt(GameTestHelper helper) {
        floor(helper, 0, 5);
        stone(helper, 2, 2);
        BlockPos reader = wire(helper, 2, 3, FLUSH);
        branchNorth(helper, 2, 3);
        slab(helper, 3, 2, bottomSlab(), FLUSH);
        slab(helper, 3, 3, bottomSlab(), LOWERED);
        BlockPos lowered = wire(helper, 3, 4, LOWERED_TWICE);
        expectSide(helper, reader, Direction.NORTH, RedstoneSide.SIDE, "premise: the branch switches the line rule off");
        stone(helper, 4, 3);
        source(helper, 4, 4);
        expectPower(helper, lowered, 15, "premise: the redstone block feeds the lowered wire");
        expectPower(helper, reader, 14, "a wire drawn level with the reader is read like a wire in that cell");
        expectSide(helper, reader, Direction.EAST, RedstoneSide.SIDE, "the reader's arm reaches the slab");
        helper.succeed();
    }

    // ── row 8: two lowered wires a whole drawn block apart connect like a vanilla full step ─────

    @GameTest(template = TEMPLATE)
    public void twoLoweredWiresAWholeBlockApartConnectLikeAFullStep(GameTestHelper helper) {
        BlockPos[] wires = halfStep(helper, LOWERED);
        stone(helper, 4, 3);
        source(helper, 4, 4);
        expectPower(helper, wires[1], 15, "premise: the redstone block feeds the upper lowered wire");
        expectPower(helper, wires[0], 14, "a whole drawn block is vanilla's own step, so the lower wire reads it");
        expectSide(helper, wires[0], Direction.EAST, RedstoneSide.SIDE, "the lower wire's arm reaches the slab");
        helper.succeed();
    }

    // ── row 9: a wire drawn a block and a half below the lowered wire does not read it ──────────

    @GameTest(template = TEMPLATE)
    public void aWireDrawnMoreThanABlockBelowDoesNotReadTheLoweredWire(GameTestHelper helper) {
        floor(helper, 0, 5);
        slab(helper, 2, 1, bottomSlab(), FLUSH);
        slab(helper, 2, 2, bottomSlab(), LOWERED);
        BlockPos reader = wire(helper, 2, 3, LOWERED_TWICE);
        stone(helper, 3, 2);
        slab(helper, 3, 3, bottomSlab(), FLUSH);
        BlockPos lowered = wire(helper, 3, 4, LOWERED);
        stone(helper, 4, 3);
        source(helper, 4, 4);
        expectPower(helper, lowered, 15, "premise: the redstone block feeds the lowered wire");
        expectPower(helper, reader, 0, "a wire drawn a block and a half below the lowered wire is out of reach:"
                + " vanilla's own step is one block");
        expectPower(helper, lowered, 15, "and the lowered wire is not disturbed by the wire below it");
        helper.succeed();
    }
}
