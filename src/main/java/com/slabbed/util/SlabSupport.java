package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CaveVinesBodyBlock;
import net.minecraft.block.CaveVinesHeadBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HangingRootsBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SporeBlossomBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.WorldView;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Central helper for slab support semantics.
 */
public final class SlabSupport {
    private static final String BOTTOM_PERSISTENT_TRACE_OPT_IN = "slabbed.bottomPersistentTrace";
    private static final Identifier PALE_MOSS_CARPET_ID = Identifier.of("minecraft", "pale_moss_carpet");

    private SlabSupport() {
    }

    /**
     * Empty shape-cache views and server generation regions have no live placement geometry.
     * Server worlds may be queried only by their owning thread; client render regions remain
     * valid on mesh workers. Stored placement facts are never changed by this read guard (LAW.md).
     */
    public static boolean isUnsafeGeometryView(BlockView view) {
        return view == null || view instanceof EmptyBlockView
                || view instanceof WorldView worldView && !worldView.isClient()
                && !(view instanceof ServerWorld serverWorld && serverWorld.getServer().isOnThread());
    }

    /**
     * Returns true if the block is a thin top-layer block (snow layers, carpet,
     * pale moss carpet) that should never be visually offset by slab logic.
     * These blocks sit flush on the surface and are not structural.
     */
    public static boolean isThinTopLayer(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SnowBlock || isCarpetLike(state);
    }

    private static boolean isPaleMossCarpet(Block block) {
        return block == Registries.BLOCK.get(PALE_MOSS_CARPET_ID);
    }

    /** Identifies thin carpet geometry; positions come only from saved facts (LAW.md). */
    public static boolean isCarpetLike(BlockState state) {
        return state != null && (state.getBlock() instanceof CarpetBlock || isPaleMossCarpet(state.getBlock()));
    }

    /**
     * Returns true if the block at {@code pos} is a slab whose top face can provide support.
     */
    public static boolean isSupportingSlab(WorldView world, BlockPos pos) {
        return isSupportingSlab(world.getBlockState(pos));
    }

    /** Overload for BlockView contexts (shapes). */
    public static boolean isSupportingSlab(BlockView world, BlockPos pos) {
        return isSupportingSlab(world.getBlockState(pos));
    }

    /**
     * Returns true if the state is a slab with a defined type.
     */
    public static boolean isSupportingSlab(BlockState state) {
        if (CompatHooks.shouldSkipSlabSupport(state)) {
            return false;
        }
        return state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE);
    }

    /** True if this state is a bottom slab. */
    public static boolean isBottomSlab(BlockState state) {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * GH #39 parity: bottom(-like) slab surfaces whose Slabbed placement-support widening must
     * NOT make them valid ON_GROUND mob-spawn floors. Vanilla bottom slabs plus custom
     * BOTTOM_LIKE surfaces (Terrain Slabs bottom). Top-like and double surfaces, including
     * generated doubles, are deliberately excluded — they are legitimately spawnable.
     */
    public static boolean isSpawnProofBottomLikeSurface(BlockState state) {
        return isBottomSlab(state)
                || CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    /** True if this state is a top slab. */
    public static boolean isTopSlab(BlockState state) {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * Single source of truth: returns true iff the state is a TOP slab
     * and the queried face is DOWN (i.e. the underside of a top slab).
     */
    public static boolean isTopSlabUndersideSupport(BlockState state, Direction face) {
        return face == Direction.DOWN && isTopSlab(state);
    }

    /** True if the block at {@code posAbove} is a top or double slab that can provide ceiling support. */
    public static boolean isCeilingSupportBottomSurface(WorldView world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.get(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** Overload for shape/world views. */
    public static boolean isCeilingSupportBottomSurface(BlockView world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.get(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** True if the block immediately below {@code pos} is a bottom slab providing its top face. */
    public static boolean hasBottomSlabBelow(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isBottomSlab(world.getBlockState(pos.down()));
    }

    /**
     * Effective Y offset of the slab's top face relative to the slab block position.
     * 0.5 for bottom slabs, 1.0 for top/double.
     */
    public static double getSupportYOffset(BlockState state) {
        if (!isSupportingSlab(state)) {
            throw new IllegalArgumentException("Not a supporting slab: " + state);
        }
        SlabType type = state.get(SlabBlock.TYPE);
        return switch (type) {
            case BOTTOM -> 0.5;
            case TOP, DOUBLE -> 1.0;
        };
    }

    /**
     * Primary query: should this slab top face count as solid support.
     */
    public static boolean canTreatAsSolidTopFace(WorldView world, BlockPos pos) {
        return isSupportingSlab(world, pos);
    }

    /** Overload for shape/world views. */
    public static boolean canTreatAsSolidTopFace(BlockView world, BlockPos pos) {
        return isSupportingSlab(world, pos);
    }

    public static boolean isFloorTorch(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof net.minecraft.block.TorchBlock && !(block instanceof WallTorchBlock);
    }

    public static boolean isBeta35FloorButtonContactObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof ButtonBlock
                && state.contains(Properties.BLOCK_FACE)
                && state.get(Properties.BLOCK_FACE) == BlockFace.FLOOR;
    }

    public static boolean isBeta35BottomTrapdoorVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof TrapdoorBlock
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.BOTTOM;
    }

    public static boolean isBeta35VerticalChainVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof ChainBlock
                && state.contains(Properties.AXIS)
                && state.get(Properties.AXIS) == Direction.Axis.Y;
    }

    /**
     * Selects the elongated 0..24 chain geometry that bridges the half-block underside of a flat
     * TOP slab. For a chain cell with a stored placement height, both the TOP support's visible dy
     * and the chain's stored dy must be exactly zero; lowered TOP slabs and DOUBLE slabs already meet
     * a normal translated chain at their underside and must keep the standard model. A legacy chain
     * cell retains the existing top/double support rule.
     */
    public static boolean isVerticalChainDirectlyUnderCeilingSupport(
            BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35VerticalChainVisibleOwnerObject(state)) {
            return false;
        }
        if (!SlabAnchorAttachment.usesFrozenPlacementHeight(world, pos)) {
            return isCeilingSupportBottomSurface(world, pos.up());
        }

        BlockPos supportPos = pos.up();
        BlockState supportState = world.getBlockState(supportPos);
        if (!isTopSlab(supportState)
                || Double.doubleToRawLongBits(getYOffset(world, supportPos, supportState))
                != Double.doubleToRawLongBits(0.0d)) {
            return false;
        }
        double chainDy = SlabAnchorAttachment.storedPlacementDy(world, pos);
        return Double.doubleToRawLongBits(chainDy) == Double.doubleToRawLongBits(0.0d);
    }

    /**
     * Selection (outline/raycast) shape for a vertical chain bridged under a ceiling support.
     * Keeps the provided {@code fallback} base and unions vertically-moved copies so the wire
     * box spans the elongated chain (0..1.5 blocks) and agrees with the rendered geometry.
     * Mirrors the live-confirmed NeoForge behaviour (tag lantern-chain-live-confirmed).
     */
    public static VoxelShape ceilingBridgedVerticalChainSelectionShape(
            BlockView world, BlockPos pos, BlockState state, VoxelShape fallback) {
        VoxelShape base = fallback == null ? VoxelShapes.empty() : fallback;
        if (!isVerticalChainDirectlyUnderCeilingSupport(world, pos, state)) {
            return base;
        }
        if (base.isEmpty()) {
            return Block.createCuboidShape(6.5d, 0.0d, 6.5d, 9.5d, 24.0d, 9.5d);
        }

        VoxelShape selection = base;
        Box bounds = base.getBoundingBox();
        if (bounds.minY > 0.0d) {
            selection = VoxelShapes.union(selection, base.offset(0.0d, -bounds.minY, 0.0d));
        }
        if (bounds.maxY < 1.5d) {
            selection = VoxelShapes.union(selection, base.offset(0.0d, 1.5d - bounds.maxY, 0.0d));
        }
        return selection;
    }

    public static boolean isBeta35RegularDoorVisibleOwnerObject(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof DoorBlock) || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35LoweredRegularDoorServerHitTarget(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (!isBeta35RegularDoorVisibleOwnerObject(world, pos, state)) {
            return false;
        }
        if (!hasConsistentBeta35RegularDoorPair(world, pos, state)) {
            return false;
        }
        double targetDy = getBeta35ShiftedServerValidationYOffset(world, pos, state);
        return Double.isFinite(targetDy) && targetDy < -1.0e-6d;
    }

    private static boolean hasConsistentBeta35RegularDoorPair(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof DoorBlock)
                || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        BlockPos pairedPos = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
        BlockState pairedState = world.getBlockState(pairedPos);
        if (pairedState == null
                || pairedState.getBlock() != state.getBlock()
                || !pairedState.contains(Properties.DOUBLE_BLOCK_HALF)
                || pairedState.get(Properties.DOUBLE_BLOCK_HALF) == half) {
            return false;
        }
        if (state.contains(Properties.HORIZONTAL_FACING)
                && pairedState.contains(Properties.HORIZONTAL_FACING)
                && state.get(Properties.HORIZONTAL_FACING) != pairedState.get(Properties.HORIZONTAL_FACING)) {
            return false;
        }
        if (state.contains(Properties.DOOR_HINGE)
                && pairedState.contains(Properties.DOOR_HINGE)
                && state.get(Properties.DOOR_HINGE) != pairedState.get(Properties.DOOR_HINGE)) {
            return false;
        }
        if (state.contains(Properties.OPEN)
                && pairedState.contains(Properties.OPEN)
                && state.get(Properties.OPEN) != pairedState.get(Properties.OPEN)) {
            return false;
        }
        return !state.contains(Properties.POWERED)
                || !pairedState.contains(Properties.POWERED)
                || state.get(Properties.POWERED) == pairedState.get(Properties.POWERED);
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonVisibleTarget(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!isBeta35FloorButtonContactObject(state) && !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        if (!Double.isFinite(objectDy) || objectDy >= -1.0e-6d) {
            return false;
        }
        return true;
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!isBeta35FloorButtonContactObject(state) && !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return false;
        }
        return Double.isFinite(beta35FloorButtonContactDy(world, pos, state))
                || Double.isFinite(beta35BottomTrapdoorVisibleOwnerDy(world, pos, state));
    }

    public static boolean isBeta35LoweredBottomTrapdoorServerHitTarget(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return false;
        }
        double objectDy = getBeta35ShiftedServerValidationYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonServerHitTarget(
            BlockView world, BlockPos pos, BlockState state
    ) {
        return isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget(world, pos, state)
                || isBeta35LoweredBottomTrapdoorServerHitTarget(world, pos, state);
    }

    public static double getBeta35ShiftedServerValidationYOffset(BlockView world, BlockPos pos, BlockState state) {
        return getYOffset(world, pos, state);
    }

    /**
     * True when the block at {@code pos} is a pointed dripstone that Slabbed renders LOWERED.
     * Its render/outline is offset down, but vanilla server hit-validation expects the un-offset
     * cell, so a use-on (e.g. combining/extending the dripstone) is rejected except where the
     * lowered tip still overlaps the vanilla cell. Whitelisting it lets the server shift validation
     * to the lowered position so the whole rendered tip is interactable.
     */
    public static boolean isLoweredPointedDripstoneServerHitTarget(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof PointedDripstoneBlock)) {
            return false;
        }
        double dy = getBeta35ShiftedServerValidationYOffset(world, pos, state);
        return Double.isFinite(dy) && dy < -1.0e-6d;
    }

    private static double beta35BottomTrapdoorVisibleOwnerDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        if (isAnchoredLoweredFullBlock(world, supportPos, supportState)) {
            return SlabAnchorAttachment.isCompoundFullBlockAnchor(world, supportPos) ? -1.0d : -0.5d;
        }
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    public static boolean isBeta35SlabHeightVisibleOwnerObject(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!isBeta35FloorButtonContactObject(state)
                && !isBeta35BottomTrapdoorVisibleOwnerObject(state)
                && !isBeta35VerticalChainVisibleOwnerObject(state)
                && !isBeta35RegularDoorVisibleOwnerObject(world, pos, state)
                && !isBeta35LoweredSlabUndersideVisibleOwnerObject(world, pos, state)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    private static boolean isPaleHangingMossBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return "pale_hanging_moss".equals(path) || "pale_hanging_moss_tip".equals(path);
    }

    private static boolean isBeta35LoweredSlabUndersideVisibleOwnerObject(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        boolean supportedCeilingObject = state.isOf(Blocks.LANTERN)
                || state.isOf(Blocks.SOUL_LANTERN)
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || isPaleHangingMossBlock(state);
        if (!supportedCeilingObject) {
            return false;
        }
        BlockPos supportPos = pos.up();
        BlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof SlabBlock) || !supportState.contains(SlabBlock.TYPE)) {
            return false;
        }
        double supportDy = loweredSlabUndersideSupportDy(world, supportPos, supportState);
        return Double.isFinite(supportDy) && supportDy < -1.0e-6d;
    }

    /**
     * Full-block analogue of {@link #isBeta35LoweredSlabUndersideVisibleOwnerObject}.
     * True when a decorative hanger (lantern / soul lantern / spore blossom /
     * hanging roots / pale hanging moss — NOT chains) hangs directly beneath a
     * SOLID, NON-SLAB full block that itself renders lowered. Such a hanger must
     * follow the support down so it stays flush instead of clipping up into the
     * lowered block. Chains are intentionally excluded so they keep extending to
     * connect to the support.
     */


    public static boolean isBeta35FenceWallVariantContactObject(BlockState state) {
        return state != null
                && (state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock
                        || state.getBlock() instanceof PaneBlock);
    }

    /**
     * Visual dy of a connecting block (fence/wall/pane), as used by the stepped-connection
     * check. Connecting blocks are only ever treated as visually lowered on a custom Terrain
     * Slabs direct-support surface — which this branch does not have yet — so any slab-relative
     * offset collapses to 0 here. (When TS compat lands on canonical, gate the collapse on the
     * TS direct-support predicate so the check activates, exactly as on the TS branch.)
     */
    public static double connectingBlockVisualDy(BlockView world, BlockPos pos, BlockState state) {
        double dy = getYOffset(world, pos, state);
        if (dy != 0.0) {
            return 0.0;
        }
        return dy;
    }

    /**
     * True if {@code neighborState} is a fence/wall/pane sitting at a different visual height
     * than {@code state} — i.e. one was lowered onto a slab and the other was not. Such a pair
     * must stay as single posts instead of drawing a connector arm across the height step
     * (the "split" / illegal fence connection). Cross-family joins are left alone because the
     * neighbour is not a connecting block here.
     */
    public static boolean isSteppedConnectingNeighbor(BlockView world, BlockPos pos, BlockState state,
                                                      BlockPos neighborPos, BlockState neighborState) {
        Block neighbor = neighborState.getBlock();
        if (!(neighbor instanceof FenceBlock || neighbor instanceof WallBlock || neighbor instanceof PaneBlock)) {
            return false;
        }
        double selfDy = connectingBlockVisualDy(world, pos, state);
        double neighborDy = connectingBlockVisualDy(world, neighborPos, neighborState);
        return Math.abs(selfDy - neighborDy) > 1.0e-6;
    }

    public static boolean isBeta35FenceGateContactObject(BlockState state) {
        return state != null && state.getBlock() instanceof FenceGateBlock;
    }

    private static double loweredSlabUndersideSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE) || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0d;
        }
        SlabType type = state.get(SlabBlock.TYPE);
        if (type == SlabType.BOTTOM) {
            return floorTorchBottomSlabSupportDy(world, pos, state);
        }
        if (isAdjacentSideSlabLowered(world, pos, state)) {
            return -0.5d;
        }
        return 0.0d;
    }

    private static double beta35FenceWallVariantContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FenceWallVariantContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = beta35FenceWallVisibleSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            double supportTopOffset = isSupportingSlab(supportState) ? getSupportYOffset(supportState) : 1.0d;
            return supportDy + supportTopOffset - 1.0d;
        }
        return Double.NaN;
    }

    private static double beta35FenceWallVisibleSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (isBottomSlab(state)) {
            return floorTorchBottomSlabSupportDy(world, pos, state);
        }
        if (isTopSlab(state) && SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
            return -1.0d;
        }
        if (state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE)
                && (state.get(SlabBlock.TYPE) == SlabType.TOP || state.get(SlabBlock.TYPE) == SlabType.DOUBLE)) {
            if (state.get(SlabBlock.TYPE) == SlabType.DOUBLE
                    && SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)) {
                return -1.0d;
            }
            BlockPos belowPos = pos.down();
            BlockState below = world.getBlockState(belowPos);
            if (below.getBlock() instanceof SlabBlock && isLoweredDoubleSlabCarrier(world, belowPos, below)) {
                return -0.5d;
            }
            if (hasLoweredCarrierBelow(world, pos) || isAdjacentSideSlabLowered(world, pos, state)) {
                return -0.5d;
            }
        }
        if (isOrdinaryFullBlockWithCompoundDy(world, pos, state)) {
            return -1.0d;
        }
        if (isBeta35FenceWallVariantContactObject(state)) {
            double dy = beta35FenceWallVariantContactDy(world, pos, state);
            if (Double.isFinite(dy) && dy < -1.0e-6d) {
                return dy;
            }
        }
        return Double.NaN;
    }

    private static double beta35FloorButtonContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FloorButtonContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        if (!isSupportingSlab(supportState)) {
            return Double.NaN;
        }
        double supportDy = beta35FenceWallVisibleSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy + getSupportYOffset(supportState) - 1.0d;
        }
        return Double.NaN;
    }

    public static boolean canTreatAsFloorTorchTopFace(BlockView world, BlockPos supportPos, BlockState torchState) {
        if (isRejectedFloorTorchTopFace(world, supportPos, torchState)) {
            return false;
        }
        if (!isFloorTorch(torchState)) {
            return canTreatAsSolidTopFace(world, supportPos);
        }
        if (world == null || supportPos == null) {
            return false;
        }
        return isLegalFloorTorchLoweredBottomSlabSupport(world, supportPos, torchState)
                || isSupportingSlab(world.getBlockState(supportPos));
    }

    public static boolean isLegalFloorTorchLoweredBottomSlabSupport(
            BlockView world,
            BlockPos supportPos,
            BlockState torchState
    ) {
        if (!isFloorTorch(torchState) || world == null || supportPos == null) {
            return false;
        }
        BlockState supportState = world.getBlockState(supportPos);
        if (!isBottomSlab(supportState)) {
            return false;
        }
        boolean namedLoweredBottomSupport =
                SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, supportPos, supportState)
                        || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, supportPos, supportState);
        return namedLoweredBottomSupport
                && Math.abs(getYOffset(world, supportPos, supportState) - (-1.0d)) <= 1.0e-6;
    }

    public static boolean isRejectedFloorTorchTopFace(BlockView world, BlockPos supportPos, BlockState torchState) {
        if (!isFloorTorch(torchState)) {
            return false;
        }
        if (world == null || supportPos == null) {
            return false;
        }
        BlockState supportState = world.getBlockState(supportPos);
        if (isLegalFloorTorchLoweredBottomSlabSupport(world, supportPos, torchState)) {
            return false;
        }
        return isBottomSlab(supportState)
                && SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, supportPos, supportState);
    }

    /**
     * Absolute Y of the slab's top surface.
     */
    public static double getEffectiveTopY(BlockState state, BlockPos pos) {
        return pos.getY() + getSupportYOffset(state);
    }

    /** Shared dependency window for legacy support reads and client mesh refreshes (LAW.md). */
    public static final int SUPPORT_COLUMN_DEPTH = 16;
    private static final int MAX_CHAIN_DEPTH = SUPPORT_COLUMN_DEPTH;

    /** Recursion guard: prevents StackOverflow when isSolidBlock triggers getOutlineShape → getYOffset. */

    /** Kill switch for the slab-height step-face cull relaxation ({@link #isSlabHeightStepFace}). */
    private static final boolean STEP_CULL_DISABLED = Boolean.getBoolean("slabbed.disableStepCull");

    /**
     * Returns true if the block state represents a ceiling-attached block —
     * one that hangs from the block above it by nature.
     */
    public static boolean isCeilingAttached(BlockState state) {
        // HANGING property (lanterns, etc.)
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING)) {
            return true;
        }
        // Y-axis chains
        if (state.getBlock() instanceof ChainBlock
                && state.contains(Properties.AXIS)
                && state.get(Properties.AXIS) == Direction.Axis.Y) {
            return true;
        }
        // Hanging signs
        if (state.getBlock() instanceof HangingSignBlock) {
            return true;
        }
        // Top-half trapdoors (ceiling-mounted)
        if (state.getBlock() instanceof TrapdoorBlock
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.TOP) {
            return true;
        }
        // Bells, levers, buttons (can all be ceiling-mounted)
        Block block = state.getBlock();
        if (block instanceof BellBlock
                || block instanceof LeverBlock
                || block instanceof ButtonBlock) {
            return true;
        }
        // Specific ceiling-only block types
        if (block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || block instanceof PointedDripstoneBlock
                || block instanceof CaveVinesHeadBlock
                || block instanceof CaveVinesBodyBlock) {
            return true;
        }
        return false;
    }

    /**
     * Decorations that have NO floor variant — they ALWAYS hang from the ceiling
     * regardless of any property — so they must NEVER be lowered onto a slab
     * below by the directCustom (object-on-TS) lane. Identity-based, mirroring the
     * NeoForge / 1.21.11-compat live-confirmed {@code isAlwaysCeilingHungDecoration}
     * (HangingRoots / SporeBlossom / ceiling HangingSign). This closes the edge
     * where such a block sits over a TS slab without a top slab directly above it,
     * which the up-walk in {@link #isCeilingHungFromAbove} would otherwise miss.
     */

    /**
     * Master check: should the block at {@code pos} with state {@code state}
     * be visually offset by −0.5 Y?
     *
     * <p>Handles:
     * <ul>
     *   <li><b>Direct</b> — block directly above a bottom slab.</li>
     *   <li><b>Chain (recursive)</b> — block on top of a stack of non-air,
     *       non-slab blocks that ultimately sits on a bottom slab. Fixes
     *       stacking at any height (signs on fences on slabs, 3+ towers).</li>
     *   <li><b>Double-block</b> — upper half checks 2 blocks down.</li>
     *   <li><b>Bed</b> — both halves offset when <em>either</em> half has a
     *       bottom slab below.</li>
     * </ul>
     *
     * @return true if the block should receive a −0.5 Y offset
     */
    public static boolean shouldOffset(BlockView world, BlockPos pos, BlockState state) {
        // never offset slabs themselves
        if (state.getBlock() instanceof SlabBlock) {
            return false;
        }

        // never offset thin top-layer blocks (snow layers, carpet) or powder snow — they are
        // natural surface/terrain fill, not structural objects. Powder snow is a full cube so it
        // is NOT an isThinTopLayer (which keys on SnowBlock), hence the explicit guard.
        if (isThinTopLayer(state) || state.getBlock() instanceof PowderSnowBlock) {
            return false;
        }

        if (CompatHooks.shouldSkipOffset(state)) {
            return false;
        }

        // blocks under a top slab that get +0.5 UP via getYOffset should not
        // also get -0.5 DOWN. Use isCeilingAttached here (safe, no shape calcs)
        // since shouldOffset is called from paths outside the recursion guard.
        if (isCeilingAttached(state) && isTopSlab(world.getBlockState(pos.up()))) {
            return false;
        }

        // ceiling-attached blocks further down a chain of ceiling blocks
        // leading to a top slab also get +0.5 UP; exclude from -0.5
        if (isCeilingAttached(state)) {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopSlab(cur)) {
                    return false;
                }
                if (isCeilingAttached(cur)) {
                    cursor = cursor.up();
                    continue;
                }
                break;
            }
        }

        // blocks hanging from above (lanterns, etc.) — don't offset DOWN by slab below
        // (they may get a separate +0.5 UP offset via getYOffset)
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING)) {
            return false;
        }

        // ── bed: either half has a slab ───────────────────────────────
        if (state.contains(Properties.BED_PART)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BedPart part = state.get(Properties.BED_PART);
            BlockPos otherPos;
            if (part == BedPart.FOOT) {
                otherPos = pos.offset(facing);
            } else {
                otherPos = pos.offset(facing.getOpposite());
            }
            return hasSlabInColumn(world, pos) || hasSlabInColumn(world, otherPos);
        }

        // ── double-block: upper half checks two blocks down ───────────
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.UPPER) {
                return isBottomSlab(world.getBlockState(pos.down(2)));
            }
            return hasBottomSlabBelow(world, pos);
        }

        // ── direct + recursive chain ──────────────────────────────────
        // Intended product behavior: ordinary full blocks may anchor to slab
        // columns as long as this remains in slab context.
        if (hasSlabInColumn(world, pos)) {
            return true;
        }

        // ── wall-attached blocks: check the block they're mounted on ──
        Block block = state.getBlock();
        if ((block instanceof WallSignBlock || block instanceof WallBannerBlock
                || block instanceof WallTorchBlock || block instanceof WallHangingSignBlock)
                && state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BlockPos attachedPos = pos.offset(facing.getOpposite());
            if (hasSlabInColumn(world, attachedPos)) {
                return true;
            }
        }

        return false;
    }

    /** Returns the exact stored height, or native zero when absent (LAW.md). */
    public static double getYOffset(BlockView world, BlockPos pos, BlockState state) {
        return com.slabbed.anchor.PlacementGeometry.offsetY(world, pos, state);
    }

    /** Read-only provenance boundary for existing terrain above a new player support (LAW.md). */

    /**
     * Exposed faces between blocks with different stored seats must remain in the mesh.
     * Horizontal differences expose a side strip. An UP face opens when its neighbor is
     * higher; a DOWN face opens when its neighbor is lower. Touching vertical faces retain
     * ordinary culling. This affects emitted faces only and never writes a seat (LAW.md).
     */
    private static final Direction[] STEP_CULL_DIRECTIONS = Direction.values();

    public static boolean isSlabHeightStepFace(BlockView world, BlockPos pos, BlockState state, Direction direction) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null || direction == null) {
            return false;
        }
        return slabHeightStepFaceAgainst(world, pos, state, direction, getYOffset(world, pos, state));
    }

    /**
     * Variant that reuses an already-computed self dy (== {@code getYOffset(world,pos,state)}) so the
     * per-block render path does not repeat the block's own offset calculation for each face.
     * The caller must pass exactly {@code getYOffset(world,pos,state)}; behaviour is
     * otherwise identical to the 4-arg overload.
     */
    public static boolean isSlabHeightStepFace(BlockView world, BlockPos pos, BlockState state, Direction direction, double selfDy) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null || direction == null) {
            return false;
        }
        return slabHeightStepFaceAgainst(world, pos, state, direction, selfDy);
    }

    /** Resolves exposed cull faces once per emitted model, using its existing total seat. */
    public static int slabHeightStepFaceMask(BlockView world, BlockPos pos, BlockState state, double selfDy) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null) {
            return 0;
        }
        int mask = 0;
        for (Direction direction : STEP_CULL_DIRECTIONS) {
            if (slabHeightStepFaceAgainst(world, pos, state, direction, selfDy)) {
                mask |= 1 << direction.ordinal();
            }
        }
        return mask;
    }

    private static boolean slabHeightStepFaceAgainst(BlockView world, BlockPos pos, BlockState state, Direction direction, double selfDy) {
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighbor = world.getBlockState(neighborPos);
        if (neighbor.isAir()) {
            return false;
        }
        double neighborDy = getYOffset(world, neighborPos, neighbor);
        double difference = selfDy - neighborDy;
        if (direction == Direction.UP) return difference < -1.0e-6;
        if (direction == Direction.DOWN) return difference > 1.0e-6;
        return Math.abs(difference) > 1.0e-6;
    }


    public static boolean isCompatibleLoweredSlabLane(SlabType existingType, SlabType incomingType) {
        return existingType == incomingType
                || existingType == SlabType.DOUBLE
                || incomingType == SlabType.DOUBLE
                || (existingType == SlabType.TOP && incomingType == SlabType.BOTTOM)
                || (existingType == SlabType.BOTTOM && incomingType == SlabType.TOP);
    }

    public record CompoundSlabRemapDecision(
            boolean legal,
            BlockPos sourcePos,
            BlockPos legalLanePos,
            BlockPos candidatePlacementPos,
            SlabType resultType,
            String reason
    ) {
        private static CompoundSlabRemapDecision rejected(
                BlockPos sourcePos,
                BlockPos legalLanePos,
                BlockPos candidatePlacementPos,
                String reason
        ) {
            return new CompoundSlabRemapDecision(false, sourcePos, legalLanePos, candidatePlacementPos, null, reason);
        }
    }

    public static CompoundSlabRemapDecision findLegalCompoundSlabRemap(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3d hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || intendedDirection == null) {
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    CompoundSlabRemapDecision.rejected(sourcePos, null, null, "missing_context"));
        }
        boolean compoundFullBlockSource = !(sourceState.getBlock() instanceof SlabBlock)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, sourcePos)
                && Math.abs(getYOffset(world, sourcePos, sourceState) + 1.0d) <= 1.0e-6d;
        boolean compoundVisibleSlabLaneSource = sourceState.getBlock() instanceof SlabBlock
                && isCompoundVisibleSlabLaneOwner(world, sourcePos, sourceState)
                && Math.abs(getYOffset(world, sourcePos, sourceState) + 1.0d) <= 1.0e-6d;
        if (!compoundFullBlockSource && !compoundVisibleSlabLaneSource) {
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    CompoundSlabRemapDecision.rejected(sourcePos, null, null, "source_not_compound_full_block_dy_-1"));
        }
        if (intendedDirection == Direction.UP) {
            if (!compoundFullBlockSource) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(sourcePos, null, null, "source_not_compound_full_block_dy_-1"));
            }
            BlockPos candidatePlacementPos = sourcePos.up();
            BlockState candidateState = world.getBlockState(candidatePlacementPos);
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        candidatePlacementPos,
                        "compound_visible_owner_top_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    candidatePlacementPos,
                    SlabType.BOTTOM,
                    "COMPOUND_VISIBLE_OWNER_TOP_SLAB"));
        }
        if (intendedDirection.getAxis().isVertical()) {
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    CompoundSlabRemapDecision.rejected(sourcePos, null, null, "direction_not_horizontal"));
        }

        BlockPos intendedLanePos = sourcePos.offset(intendedDirection);
        int legalLaneCount = 0;
        BlockPos legalLanePos = null;
        BlockState legalLaneState = null;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos lanePos = sourcePos.offset(direction);
            BlockState laneState = world.getBlockState(lanePos);
            if (isLegalCompoundRemapLane(world, lanePos, laneState)) {
                legalLaneCount++;
                legalLanePos = lanePos;
                legalLaneState = laneState;
            }
        }

        if (legalLaneCount == 1 && intendedLanePos.equals(legalLanePos)) {
            BlockPos candidatePlacementPos = legalLanePos.offset(intendedDirection);
            BlockState candidateState = world.getBlockState(candidatePlacementPos);
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        legalLanePos,
                        candidatePlacementPos,
                        "candidate_not_air"));
            }

            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    legalLanePos,
                    candidatePlacementPos,
                    legalLaneState.get(SlabBlock.TYPE),
                    "COMPOUND_HORIZONTAL_CONTINUATION_LANE"));
        }

        if (isCompoundVisibleSideLowerHit(world, sourcePos, sourceState, hitPos)) {
            BlockState candidateState = world.getBlockState(intendedLanePos);
            if (isMarkedCompoundVisibleSideSlab(world, intendedLanePos, candidateState)) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        new CompoundSlabRemapDecision(
                        true,
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        SlabType.DOUBLE,
                        "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB"));
            }
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        "compound_visible_side_lower_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    intendedLanePos,
                    SlabType.BOTTOM,
                    "COMPOUND_VISIBLE_SIDE_LOWER_SLAB"));
        }
        if (isCompoundVisibleSideUpperHit(world, sourcePos, sourceState, hitPos)) {
            BlockState candidateState = world.getBlockState(intendedLanePos);
            if (isMarkedCompoundVisibleSideSlab(world, intendedLanePos, candidateState)) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        new CompoundSlabRemapDecision(
                        true,
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        SlabType.DOUBLE,
                        "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB"));
            }
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        "compound_visible_side_upper_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    intendedLanePos,
                    SlabType.TOP,
                    "COMPOUND_VISIBLE_SIDE_UPPER_SLAB"));
        }

        BlockState belowSourceState = world.getBlockState(sourcePos.down());
        BlockPos candidatePlacementPos = intendedLanePos;
        BlockState candidateState = world.getBlockState(candidatePlacementPos);
        if (legalLaneCount == 0 && isLegalCompoundRemapLane(world, sourcePos.down(), belowSourceState)) {
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        candidatePlacementPos,
                        "below_lane_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    candidatePlacementPos,
                    compoundBelowLaneResultType(sourcePos, hitPos),
                    "COMPOUND_BELOW_LANE_SIDE_SLAB"));
        }

        if (legalLaneCount == 0 && isPersistentVisibleCompoundOwner(world, sourcePos, sourceState)) {
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        candidatePlacementPos,
                        "persistent_visible_owner_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    candidatePlacementPos,
                    compoundBelowLaneResultType(sourcePos, hitPos),
                    "COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB"));
        }

        return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                CompoundSlabRemapDecision.rejected(
                sourcePos,
                legalLanePos,
                legalLanePos == null ? intendedLanePos : legalLanePos.offset(intendedDirection),
                "legal_lane_count_" + legalLaneCount + "_or_not_in_intended_direction"));
    }

    private static CompoundSlabRemapDecision traceCompoundSlabRemap(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3d hitPos,
            CompoundSlabRemapDecision decision
    ) {
        RuntimeDiagnostics.logSlabSupportDecision(world, sourcePos, sourceState, intendedDirection, hitPos, decision);
        return decision;
    }

    private static SlabType compoundBelowLaneResultType(BlockPos sourcePos, Vec3d hitPos) {
        if (sourcePos != null && hitPos != null && hitPos.y >= sourcePos.getY()) {
            return SlabType.TOP;
        }
        return SlabType.BOTTOM;
    }

    private static boolean isCompoundVisibleSideLowerHit(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Vec3d hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || hitPos == null) {
            return false;
        }
        double sourceDy = getYOffset(world, sourcePos, sourceState);
        double localVisibleY = hitPos.y - (sourcePos.getY() + sourceDy);
        return localVisibleY >= -1.0e-6d && localVisibleY < 0.5d - 1.0e-6d;
    }

    private static boolean isCompoundVisibleSideUpperHit(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Vec3d hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || hitPos == null) {
            return false;
        }
        double sourceDy = getYOffset(world, sourcePos, sourceState);
        double localVisibleY = hitPos.y - (sourcePos.getY() + sourceDy);
        return localVisibleY >= 0.5d - 1.0e-6d && localVisibleY <= 1.0d + 1.0e-6d;
    }

    private static boolean isMarkedCompoundVisibleSideSlab(BlockView world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state);
    }

    private static boolean isPersistentVisibleCompoundOwner(BlockView world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                && Math.abs(getYOffset(world, pos, state) + 1.0d) <= 1.0e-6d;
    }

    private static boolean isLegalCompoundRemapLane(BlockView world, BlockPos lanePos, BlockState laneState) {
        return laneState != null
                && laneState.getBlock() instanceof SlabBlock
                && laneState.contains(SlabBlock.TYPE)
                && laneState.get(SlabBlock.TYPE) != SlabType.DOUBLE
                && laneState.getFluidState().isEmpty()
                && Math.abs(getYOffset(world, lanePos, laneState) + 0.5d) <= 1.0e-6d
                && isLoweredSideLaneSlabCarrier(world, lanePos, laneState);
    }

    private static boolean isCompatibleLoweredSlabLane(BlockState a, BlockState b) {
        if (!a.contains(SlabBlock.TYPE) || !b.contains(SlabBlock.TYPE)) {
            return false;
        }
        SlabType aType = a.get(SlabBlock.TYPE);
        SlabType bType = b.get(SlabBlock.TYPE);
        return isCompatibleLoweredSlabLane(aType, bType);
    }

    private static boolean hasLoweredSolidSideSupport(BlockView world, BlockPos slabPos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = slabPos.offset(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (isFullHeightLoweredCarrierForSideSupport(world, neighborPos, neighbor)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnchoredLoweredFullBlock(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
            return false;
        }
        return SlabAnchorAttachment.isAnchored(world, pos);
    }

    public static boolean isLoweredDoubleSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return true;
        }
        return isLoweredCarrier(world, pos, state, MAX_CHAIN_DEPTH);
    }

    public static boolean isFullHeightLoweredCarrier(BlockView world, BlockPos pos, BlockState state) {
        return isLoweredFullBlockCarrier(world, pos, state)
                || isLoweredDoubleSlabCarrier(world, pos, state);
    }

    public static boolean isLoweredSideLaneDoubleCarrier(BlockView world, BlockPos pos, BlockState state) {
        return isLoweredSideLaneSlabCarrier(world, pos, state)
                && state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    public static boolean isLoweredSideLaneSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && isAdjacentSideSlabLowered(world, pos, state);
    }


    public static boolean isLoweredCompoundSourceSlab(BlockView world, BlockPos pos, BlockState state) {
        return state != null
                && isBottomSlab(state)
                && isAdjacentSideSlabLowered(world, pos, state);
    }

    public static boolean isBottomSlabLoweredByCarrierBelow(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()) {
            return false;
        }

        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        boolean backedByLoweredCarrier = below.getBlock() instanceof SlabBlock
                ? isLoweredDoubleSlabCarrier(world, belowPos, below)
                : hasLoweredCarrierBelow(world, pos);
        return backedByLoweredCarrier && getYOffset(world, pos, state) == -0.5d;
    }

    private static boolean isLoweredCarrier(BlockView world, BlockPos pos, BlockState state, int depth) {
        return isLoweredCarrier(world, pos, state, depth, true);
    }

    private static boolean isLoweredCarrier(
            BlockView world,
            BlockPos pos,
            BlockState state,
            int depth,
            boolean allowSideLane
    ) {
        if (world == null || pos == null || state == null || depth <= 0) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            if (!state.contains(SlabBlock.TYPE)
                    || state.get(SlabBlock.TYPE) != SlabType.DOUBLE
                    || !state.getFluidState().isEmpty()) {
                return false;
            }
            if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
                return true;
            }
            if (allowSideLane && isAdjacentSideSlabLowered(world, pos, state)) {
                return true;
            }
            BlockPos belowPos = pos.down();
            return isLoweredCarrier(world, belowPos, world.getBlockState(belowPos), depth - 1, allowSideLane);
        }
        return isLoweredFullBlockCarrier(world, pos, state);
    }

    private static boolean isLoweredDoubleSlabCarrierForSideSupport(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return true;
        }
        return isLoweredCarrier(world, pos.down(), world.getBlockState(pos.down()), MAX_CHAIN_DEPTH, false);
    }

    private static boolean isFullHeightLoweredCarrierForSideSupport(BlockView world, BlockPos pos, BlockState state) {
        return isLoweredFullBlockCarrier(world, pos, state)
                || isLoweredDoubleSlabCarrierForSideSupport(world, pos, state);
    }

    private static boolean isLoweredFullBlockCarrier(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
            return false;
        }
        boolean hasBottomBelow = hasBottomSlabBelow(world, pos);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        return hasBottomBelow || anchored;
    }

    private static boolean isOrdinaryFullBlockWithCompoundDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
            return false;
        }
        if (!SlabAnchorAttachment.isAnchored(world, pos)) {
            return false;
        }
        if (SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            return true;
        }
        BlockState below = world.getBlockState(pos.down());
        return isBottomSlab(below) && isAdjacentSideSlabLowered(world, pos.down(), below);
    }

    private static boolean hasLoweredCarrierBelow(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos belowPos = pos.down();
        return isLoweredCarrier(world, belowPos, world.getBlockState(belowPos), MAX_CHAIN_DEPTH);
    }

    private static boolean isCompoundVisibleOwnerTopSlab(BlockView world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static boolean hasLoweredSlabLaneSupport(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!(slabState.getBlock() instanceof SlabBlock) || !slabState.contains(SlabBlock.TYPE)) {
            return false;
        }
        if (hasVanillaFullBlockSupportBelow(world, slabPos)) {
            return false;
        }
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(slabPos);
        queue.add(slabPos);
        int explored = 0;

        while (!queue.isEmpty() && explored < MAX_CHAIN_DEPTH) {
            BlockPos cursor = queue.removeFirst();
            explored++;
            if (hasLoweredSolidSideSupport(world, cursor)) {
                return true;
            }

            BlockState cursorState = world.getBlockState(cursor);
            if (!(cursorState.getBlock() instanceof SlabBlock) || !cursorState.contains(SlabBlock.TYPE)) {
                continue;
            }
            if (hasVanillaFullBlockSupportBelow(world, cursor)) {
                continue;
            }
            if (!cursor.equals(slabPos)
                    && isCompatibleLoweredSlabLane(slabState, cursorState)
                    && isLoweredSlabLaneOwnerForSideInheritance(world, cursor, cursorState)) {
                return true;
            }

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighborPos = cursor.offset(dir);
                if (!visited.add(neighborPos)) {
                    continue;
                }
                BlockState neighborState = world.getBlockState(neighborPos);
                if (!(neighborState.getBlock() instanceof SlabBlock)) {
                    continue;
                }
                if (!isCompatibleLoweredSlabLane(cursorState, neighborState)) {
                    continue;
                }
                queue.add(neighborPos);
            }
        }
        return false;
    }

    private static boolean hasVanillaFullBlockSupportBelow(BlockView world, BlockPos slabPos) {
        if (world == null || slabPos == null) {
            return false;
        }
        BlockPos belowPos = slabPos.down();
        BlockState below = world.getBlockState(belowPos);
        return below != null
                && !below.isAir()
                && !(below.getBlock() instanceof SlabBlock)
                && below.getFluidState().isEmpty()
                && below.isSolidBlock(world, belowPos)
                && !isFullHeightLoweredCarrierForSideSupport(world, belowPos, below);
    }

    private static boolean isLoweredSlabLaneOwnerForSideInheritance(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        return world != null
                && pos != null
                && state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && !isCompoundVisibleOwnerTopSlab(world, pos, state)
                && (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                        || hasLoweredSolidSideSupport(world, pos)
                        || hasLoweredCarrierBelow(world, pos));
    }

    private static boolean isAdjacentSideSlabLowered(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!slabState.contains(SlabBlock.TYPE)) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, slabPos, slabState)) {
            return true;
        }
        return hasLoweredSlabLaneSupport(world, slabPos, slabState);
    }

    private static double floorTorchBottomSlabSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || !isBottomSlab(state) || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0d;
        }
        if (SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
            return -0.5d;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        if (below.getBlock() instanceof SlabBlock) {
            if (isLoweredDoubleSlabCarrier(world, belowPos, below)) {
                return -0.5d;
            }
        } else if (!isCompoundVisibleOwnerTopSlab(world, pos, state)
                && hasLoweredCarrierBelow(world, pos)) {
            return -0.5d;
        }
        if (!isCompoundVisibleOwnerTopSlab(world, pos, state)
                && isAdjacentSideSlabLowered(world, pos, state)) {
            return -0.5d;
        }
        return 0.0d;
    }

    /**
     * Shared ownership rule for client raycast/outline retargeting of lowered
     * block-entity-style blocks (e.g. chests) sitting above a bottom slab.
     *
     * <p>When a block-entity block is visually lowered by -0.5 (its model, via
     * {@code BlockEntityOffsetMixin}, and its outline/raycast shapes, via
     * {@code SlabSupportStateMixin}), the lower half of its visible footprint
     * overflows into {@code pos.down()}'s voxel. Vanilla DDA raycast traversal
     * cannot see that overflowed portion at {@code pos} and instead hits the
     * slab below. This helper is the single source of truth for detecting
     * that case so raycast retarget and outline agree.
     *
     * @return true iff {@code state} is a {@link BlockEntityProvider} block
     *         at {@code pos} whose {@link #getYOffset} is exactly {@code -0.5}.
     */
    public static boolean isLoweredBlockEntityVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof BlockEntityProvider)) {
            return false;
        }
        return getYOffset(world, pos, state) == -0.5;
    }

    public static boolean isLoweredTorchVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (!(block instanceof net.minecraft.block.TorchBlock
                || block instanceof net.minecraft.block.WallTorchBlock)) {
            return false;
        }
        // compound dy (-1.0) also qualifies: torch above an adjacent-lowered bottom slab
        return getYOffset(world, pos, state) < 0.0;
    }

    public static boolean isCompoundVisibleSlabLaneOwner(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (getYOffset(world, pos, state) != -1.0d) {
            return false;
        }
        return SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    public static BlockHitResult findCompoundVisibleSlabLaneOwnerTarget(
            BlockView world, Entity entity, Vec3d eye, Vec3d end
    ) {
        if (world == null || eye == null || end == null) {
            return null;
        }
        Vec3d ray = end.subtract(eye);
        double reach = ray.length();
        if (reach <= 0.0d) {
            return null;
        }
        Vec3d dir = ray.normalize();
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05d));

        BlockHitResult best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        double maxDist2 = reach * reach + 1.0e-6d;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            Vec3d sample = eye.add(dir.multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockHitResult candidate = raycastCompoundVisibleSlabLaneOwner(world, entity, eye, end, samplePos);
            if (candidate != null && candidate.getPos().squaredDistanceTo(eye) <= maxDist2
                    && candidate.getPos().squaredDistanceTo(eye) < bestDist2 - 1.0e-6d) {
                best = candidate;
                bestDist2 = candidate.getPos().squaredDistanceTo(eye);
            }

            candidate = raycastCompoundVisibleSlabLaneOwner(world, entity, eye, end, samplePos.up());
            if (candidate != null && candidate.getPos().squaredDistanceTo(eye) <= maxDist2
                    && candidate.getPos().squaredDistanceTo(eye) < bestDist2 - 1.0e-6d) {
                best = candidate;
                bestDist2 = candidate.getPos().squaredDistanceTo(eye);
            }
        }
        return best;
    }

    private static BlockHitResult raycastCompoundVisibleSlabLaneOwner(
            BlockView world, Entity entity, Vec3d eye, Vec3d end, BlockPos pos
    ) {
        BlockState state = world.getBlockState(pos);
        if (!isCompoundVisibleSlabLaneOwner(world, pos, state)) {
            return null;
        }
        ShapeContext context = entity == null ? ShapeContext.absent() : ShapeContext.of(entity);
        VoxelShape outline = state.getOutlineShape(world, pos, context);
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        return outline.raycast(eye, end, pos);
    }

    public static boolean isLoweredBedVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        return state.getBlock() instanceof net.minecraft.block.BedBlock
                && state.contains(Properties.BED_PART)
                && getYOffset(world, pos, state) == -0.5;
    }

    /**
     * Redstone dust support surface — treat slab tops like valid ground for placement/survival.
     */
    public static boolean isRedstoneSupportTopSurface(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (state.isSideSolidFullSquare(world, pos, Direction.UP)) {
            return true;
        }

        return isSupportingSlab(state) && (isBottomSlab(state) || isTopSlab(state));
    }

    /**
     * Category predicate for the generic slab-column lowering fallback in
     * {@link #shouldOffset}.
     *
     * <p>Returns {@code true} for blocks that should visually sit on slabs
     * when a bottom slab exists somewhere in their column:
     * <ul>
     *   <li>Every {@link BlockEntityProvider} block — chests, hoppers,
     *       furnaces, jukeboxes, spawners, end portal frames, beacons,
     *       banners, signs (standing), etc. This matches the
     *       {@link #isLoweredBlockEntityVisual} contract and ensures
     *       full-cube BE blocks (jukebox, spawner, …) lower alongside
     *       non-full-cube BE blocks (chest, hopper, …).</li>
     *   <li>Any block that is not a full solid cube — fences, walls, panes,
     *       torches, buttons, pressure plates, wall signs, etc.
     *       ({@code !state.isSolidBlock}).</li>
     * </ul>
     *
     * <p>Explicitly excludes plain solid world cubes (stone, dirt, planks,
     * cobblestone, sand, gravel, terracotta, …) so natural terrain does not
     * visually drop when a slab happens to sit below it.
     */
    private static boolean isSlabSitCandidate(BlockView world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockEntityProvider) {
            return true;
        }
        if (block instanceof CraftingTableBlock) {
            return true;
        }
        if (SLAB_SIT_OBJECT_CUBES.contains(block)) {
            return true;
        }
        // Opaque full cubes always stay flush: lowering an opaque cube -0.5 while the chunk mesher
        // still culls at its grid voxel tears see-through holes across Terrain Slabs terrain (the
        // pulled-hotfix world-hole DODO). Use the view-INDEPENDENT opaque-full-cube test — NOT
        // isSolidBlock(world,pos), which is view-dependent: under a ChunkRendererRegion on the
        // render thread it can report a plain stone cube as NOT solid, lowering natural terrain on
        // the worker mesh while main-thread culling stays put → holes. Non-opaque / non-cube objects
        // (fences, panes, torches, slabs-on-objects, …) are not opaque full cubes, so they still
        // fall through to the !isSolidBlock candidate test and lower as before.
        if (isOpaqueFullCubeViewIndependent(state)) {
            return false;
        }
        return !state.isSolidBlock(world, pos);
    }

    /**
     * View-INDEPENDENT "opaque full cube" probe — the 1.21.1 equivalent of 1.21.11's precomputed
     * no-arg {@code state.isOpaqueFullCube()}, which does not exist in Yarn 1.21.1+build.3 (only the
     * view-DEPENDENT {@code isOpaqueFullCube(BlockView, BlockPos)} overload is mapped). Reads the
     * precomputed {@code isOpaque()} flag and probes the collision shape against
     * {@link EmptyBlockView#INSTANCE} at {@link BlockPos#ORIGIN} — exactly the idiom vanilla uses to
     * precompute its own {@code isFullCube} shape cache (AbstractBlock.AbstractBlockState ctor) and
     * its navigation full-cube checks. No real {@code world}/{@code pos} is consulted, so the result
     * is deterministic across client / render-region views and cannot trigger the world-hole DODO.
     */
    public static boolean isOpaqueFullCubeViewIndependent(BlockState state) {
        return state.isOpaque()
                && state.isFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    /**
     * Terrain Slabs WYSIWYG (build-only): -0.5 when {@code state} is an opaque full cube resting
     * directly on a player-placed Terrain Slabs BOTTOM half-slab, else NaN. Uses the view-independent
     * full-cube test (never isSolidBlock(world,pos)) and {@link CompatHooks#isPlacedBottomHalfTerrainSlab}
     * which restricts to type=BOTTOM + generated!=true, so natural terrain (generated doubles/slabs) is
     * never lowered — keeping the world-hole DODO closed. Ceiling-attached blocks are excluded (they
     * hang from above). Returns NaN fast for non-cubes / cubes not on a TS bottom slab.
     */
    private static double placedTerrainSlabBottomFullCubeDy(BlockView world, BlockPos pos, BlockState state) {
        // Cheap gate first: zero cost when TS is absent (the common case), and skip the per-block
        // down-lookup + the relatively expensive view-independent full-cube test unless the block is
        // actually resting on a placed TS bottom slab.
        if (!CompatHooks.isTerrainSlabsLoaded()
                || world == null || pos == null || state == null
                || isCeilingAttached(state)) {
            return Double.NaN;
        }
        BlockState below = world.getBlockState(pos.down());
        if (!CompatHooks.isPlacedBottomHalfTerrainSlab(below)) {
            return Double.NaN;
        }
        return isOpaqueFullCubeViewIndependent(state) ? -0.5 : Double.NaN;
    }


    private static boolean isTerrainSlabLoweredFullCube(BlockView world, BlockPos pos, BlockState state) {
        double dy = placedTerrainSlabBottomFullCubeDy(world, pos, state);
        return Double.isFinite(dy) && dy < -1.0e-6;
    }

    /**
     * Curated full-cube "objects" that should sit on a slab even though they are
     * opaque full cubes (which are otherwise treated as terrain). Block entities
     * and the crafting table are already covered by {@link #isSlabSitCandidate};
     * this set is for the remaining non-BE object cubes. Building/terrain cubes
     * (wool, concrete, terracotta, planks, …) are intentionally NOT included to
     * keep them at grid height and avoid see-through terrain.
     */
    private static final Set<Block> SLAB_SIT_OBJECT_CUBES = Set.of(
            // functional full-cube workstations (consistent with the crafting table)
            Blocks.SMITHING_TABLE, Blocks.FLETCHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.LOOM,
            // decorative full cubes commonly placed as objects
            Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN, Blocks.MELON,
            Blocks.BOOKSHELF, Blocks.HAY_BLOCK, Blocks.NOTE_BLOCK, Blocks.DRIED_KELP_BLOCK,
            Blocks.SPONGE, Blocks.WET_SPONGE, Blocks.TARGET, Blocks.REDSTONE_LAMP);

    /**
     * True if the object/full-block/vanilla-slab at {@code pos} rests (directly, or through a
     * column of already-lowered slab-sit objects) on a Terrain Slabs BOTTOM_LIKE surface. The
     * walk stops at the first non-object (terrain cube / air), which keeps anything resting
     * above natural ground at grid height. Mirrors COMPAT_REF's isDirectCustomSlabSupportedObject.
     */
    public static boolean isDirectCustomSlabSupportedObject(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isDirectCustomSlabSupportSubject(world, pos, state)) {
            return false;
        }

        BlockPos supportPos = pos.down();
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            BlockState lowerState = world.getBlockState(pos.down());
            if (lowerState.getBlock() != state.getBlock()
                    || !lowerState.contains(Properties.DOUBLE_BLOCK_HALF)
                    || lowerState.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
                return false;
            }
            supportPos = pos.down(2);
        }

        // Walk down the support column. A custom bottom-like Terrain Slabs surface
        // lowers the object; a lowered "slab-sit" object below (a fence/torch/crafting
        // table/... already lowered onto the slab) carries the same -0.5 up the stack,
        // so stacked objects keep sitting correctly. The walk stops at the first
        // non-object (terrain cube / air), keeping anything above that at grid height.
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState supportState = world.getBlockState(supportPos);
            if (CompatHooks.customSlabSurfaceKind(supportState) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
                return true;
            }
            if (isDirectCustomSlabSupportSubject(world, supportPos, supportState)) {
                supportPos = supportPos.down();
                continue;
            }
            return false;
        }
        return false;
    }

    /**
     * Recursion-safe rendered dy of a VANILLA bottom-slab support directly beneath an object,
     * used to compound a mixed-slab lowering (a vanilla bottom slab capping a Terrain Slabs
     * surface). Returns -0.5 if that vanilla support is itself lowered (anchored or
     * mixed-on-TS), 0.0 if it is a flush vanilla bottom slab, and NaN if the support is not a
     * vanilla bottom slab. Never re-enters {@link #getYOffset}. Mirrors COMPAT_REF.
     */


    /**
     * True for a VANILLA (minecraft-namespace) slab — the only slab kind allowed to be a
     * directCustom subject. This lets a vanilla slab placed on a Terrain Slabs surface lower
     * onto it ("combine into a flush double"), while Terrain Slabs' own slabs are excluded so
     * the existing skip-offset asymmetry is unchanged. Mirrors COMPAT_REF.
     */
    private static boolean isVanillaDirectCustomSlabSubject(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.contains(SlabBlock.TYPE)) {
            return false;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private static boolean isDirectCustomSlabSupportSubject(BlockView world, BlockPos pos, BlockState state) {
        if (state.isAir()
                || state.getBlock() instanceof SlabBlock && !isVanillaDirectCustomSlabSubject(state)
                || isThinTopLayer(state)
                || !state.getFluidState().isEmpty()
                || CompatHooks.shouldSkipOffset(state)) {
            return false;
        }

        // Terrain Slabs compat: lower the same "objects" that sit on vanilla slabs — block
        // entities, the crafting table, and every non-full-cube block. isSlabSitCandidate
        // deliberately EXCLUDES plain opaque world cubes (dirt, grass, stone, …): lowering those
        // would tear see-through holes (the half-height TS side faces are cullface-tagged), which
        // is the world-hole DODO. Terrain-Slabs-only: vanilla slab behaviour and generic Slabbed
        // support are untouched.
        return isVanillaDirectCustomSlabSubject(state) || isSlabSitCandidate(world, pos, state);
    }

    /**
     * Walks down from {@code pos} through non-air, non-slab blocks looking
     * for a bottom slab. Returns true as soon as one is found.
     *
     * <p>An anchored full block in the column also terminates the walk as a
     * positive — the anchor records that this block is itself lowered by -0.5
     * (its visible top sits at slab height), so anything stacked on top of it
     * inherits the same lowered surface even after the original BS support
     * has been broken.
     *
     * <p>A slab encountered anywhere in the bounded column walk that is itself
     * a lowered adjacent-side slab — i.e. a 1S/0.5S/double slab horizontally
     * beside an anchored or BS-supported FB — also counts as a positive: its
     * visible top face sits at the lowered support height, so anything stacked
     * above it (directly or through intermediate full blocks) must inherit the
     * same -0.5 dy. Vanilla top slabs that are not lowered still terminate the
     * walk false via the slab terminator below. Walk remains bounded by
     * {@link #MAX_CHAIN_DEPTH}.
     */
    private static boolean hasSlabInColumn(BlockView world, BlockPos pos) {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            if (isBottomSlab(cur)) {
                return true;
            }
            if (SlabAnchorAttachment.isAnchored(world, cursor)) {
                return true;
            }
            if (cur.getBlock() instanceof SlabBlock
                    && isAdjacentSideSlabLowered(world, cursor, cur)) {
                return true;
            }
            // A full cube lowered by the Terrain Slabs full-cube lane is a lowering support even
            // when it is frozen-flat / un-anchored, so blocks/objects stacked on it inherit the
            // drop (no gap / no float). Checked before the full-block walk-down continues.
            if (isTerrainSlabLoweredFullCube(world, cursor, cur)) {
                return true;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return false;
            }
            cursor = cursor.down();
        }
        return false;
    }


}

