package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
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

/** Same-cell upgrades retain native absence or an exact authored position (LAW.md). */
public final class SameCellDoubleUpgradeKeepsLegacySeatTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sameCellDoubleUpgradeKeepsNativeAndSavedSeats(TestContext h) {
        var world = h.getWorld();
        var player = h.createMockPlayer(GameMode.SURVIVAL);
        double[] seats = {0.0d, -0.5d, -1.0d, -0.4375d};
        for (int i=0;i<seats.length;i++) {
            BlockPos cell = h.getAbsolutePos(new BlockPos(1+i, 5, 3));
            world.setBlockState(cell, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM),Block.NOTIFY_ALL);
            if (i!=0) PlacementCaptureBoundaryGameTest.forceStore(world,cell,seats[i]);
            h.assertTrue(SlabSupport.getYOffset(world,cell,world.getBlockState(cell))==seats[i],"Fixture seat differs");
            player.setPosition(cell.getX()+4.5,cell.getY(),cell.getZ()+4.5);
            var stack = new ItemStack(Items.STONE_SLAB,2);
            var result=PlacementCaptureBoundaryGameTest.useOn(player,stack,cell,Direction.UP);
            h.assertTrue(result.isAccepted() && stack.getCount()==1
                    && world.getBlockState(cell).get(SlabBlock.TYPE)==SlabType.DOUBLE
                    && SlabSupport.getYOffset(world,cell,world.getBlockState(cell))==seats[i]
                    && SlabAnchorAttachment.rawPlacementDyFact(world,cell).valueOrNaN()==seats[i],
                    "Same-cell upgrade moved or lost the seat: "+seats[i]);
        }
        h.complete();
    }
}
