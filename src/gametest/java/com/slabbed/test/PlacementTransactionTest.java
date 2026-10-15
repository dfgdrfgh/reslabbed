package com.slabbed.test;

import com.slabbed.anchor.ChunkPositionDyMapPacketCodec;
import com.slabbed.anchor.SlabAnchorAttachment;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import java.util.Map;

/** Refused storage must refuse the real placement before block or item mutation (LAW.md). */
public final class PlacementTransactionTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void saturatedChunkRefusesRealPlacementWithoutLosingTheItem(TestContext h) {
        var world = h.getWorld();
        BlockPos support = h.getAbsolutePos(new BlockPos(3, 3, 3));
        BlockPos target = support.up();
        var chunk = world.getWorldChunk(target);
        var before = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        try {
            world.setBlockState(support, Blocks.STONE_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
            world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            BlockPos section = new BlockPos(chunk.getPos().x << 4,
                    ((target.getY() >> 4) << 4) + 32, chunk.getPos().z << 4);
            int low = 0, high = 4096;
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (ChunkPositionDyMapPacketCodec.encodedByteLength(distinct(section, middle)) <= 16_383) low = middle;
                else high = middle - 1;
            }
            var full = distinct(section, low);
            chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, full);
            h.assertTrue(SlabAnchorAttachment.preparePlacementDyBatch(world,
                            Map.of(target, Double.doubleToRawLongBits(-0.5d))) == null,
                    "premise: the target height must exceed the saturated chunk budget");
            var player = h.createMockPlayer(GameMode.SURVIVAL);
            player.setPosition(support.getX() + 3.5d, support.getY(), support.getZ() + 0.5d);
            ItemStack stack = new ItemStack(Items.STONE, 8);
            var result = PlacementCaptureBoundaryGameTest.useOn(player, stack, support, Direction.UP);
            h.assertTrue(!result.isAccepted() && world.getBlockState(target).isAir()
                            && stack.getCount() == 8
                            && chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE) == full,
                    "REFUSED_HEIGHT_ACCEPTED_BLOCK: placement survived a missing saved position: result="
                            + result + " block=" + world.getBlockState(target) + " items=" + stack.getCount());
        } finally {
            if (before == null) chunk.removeAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
            else chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, before);
        }
        h.complete();
    }

    private static Long2IntOpenHashMap distinct(BlockPos section, int count) {
        var map = new Long2IntOpenHashMap();
        for (int i = 0; i < count; i++) {
            map.put(section.add(i & 15, i >> 8, (i >> 4) & 15).asLong(), -1024 - i);
        }
        return map;
    }
}
