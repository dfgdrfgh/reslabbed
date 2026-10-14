package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** A free grid cell can contain another block's displaced body. Seats remain unchanged (LAW.md). */
public final class PlacementOccupancy {
    private PlacementOccupancy() {}

    public static boolean intersectsDisplacedBody(World world, BlockPos target, BlockState state,
                                                   LandingResolver.PlacementAim aim) {
        LandingResolver.PlacementResolution resolution = LandingResolver.resolve(
                world, aim, target, state, LandingResolver.classify(state));
        double candidateDy = resolution == null
                ? SlabSupport.getUnstoredYOffset(world, target, state) : resolution.landingDy();
        BlockState current = world.getBlockState(target);
        // A same-cell second slab retains the first slab's seat, including the factless legacy lane.
        if (state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.DOUBLE
                && current.isOf(state.getBlock()) && current.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
            candidateDy = LandingResolver.visibleOwnerDy(world, target, current);
        }
        if (!Double.isFinite(candidateDy)) return false;
        VoxelShape body = baseBody(state).offset(target.getX(), target.getY() + candidateDy, target.getZ());
        if (body.isEmpty()) return false;

        Box bounds = body.getBoundingBox();
        int depth = (int)Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY);
        // The window follows the proposed body, not its grid cell. Tall shapes and lowered owners
        // can originate outside that cell; every read is restricted to already loaded chunks.
        BlockPos.Mutable owner = new BlockPos.Mutable();
        for (int x = MathHelper.floor(bounds.minX) - 1; x <= MathHelper.ceil(bounds.maxX); x++) {
            for (int z = MathHelper.floor(bounds.minZ) - 1; z <= MathHelper.ceil(bounds.maxZ); z++) {
                for (int y = MathHelper.floor(bounds.minY) - 2; y <= MathHelper.ceil(bounds.maxY) + depth; y++) {
                    owner.set(x, y, z);
                    if (owner.equals(target) || world.isOutOfHeightLimit(owner)) continue;
                    WorldChunk chunk = SlabAnchorAttachment.loadedAttachmentChunk(world, owner);
                    if (chunk == null) continue;
                    BlockState existing = chunk.getBlockState(owner);
                    if (existing.isAir()) continue;
                    double dy = SlabSupport.getVisualYOffset(world, owner, existing);
                    // Vanilla owns flush-cell replaceability and ordinary attachment geometry.
                    // This guard adds the bodies that have left their cells, which vanilla misses.
                    if (!Double.isFinite(dy) || dy == 0.0d) continue;
                    VoxelShape occupied = baseBody(existing).offset(x, y + dy, z);
                    if (VoxelShapes.matchesAnywhere(body, occupied, BooleanBiFunction.AND)) return true;
                }
            }
        }
        return false;
    }

    private static VoxelShape baseBody(BlockState state) {
        VoxelShape collision = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        return collision.isEmpty()
                ? state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) : collision;
    }
}
