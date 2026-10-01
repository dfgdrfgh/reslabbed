package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** A departed occupant cannot leave a stored height for a replacement (LAW.md). */
public final class NonNotifyingRemovalHeightTest {
    @GameTest(structure="fabric-gametest-api-v1:empty")
    public void directChunkRemovalClearsTheStoredHeight(TestContext h) {
        var world=h.getWorld();var pos=h.getAbsolutePos(new BlockPos(2,2,2));
        world.setBlockState(pos,Blocks.OAK_PLANKS.getDefaultState(),3);
        SlabPlacementDyAttachment.record(world,pos,-0.5d);
        if (SlabPlacementDyAttachment.storedDy(world,pos)!=-0.5d) throw h.createError("premise: stored lowered height required");
        world.getWorldChunk(pos).setBlockState(pos,Blocks.AIR.getDefaultState(),0);
        if (!Double.isNaN(SlabPlacementDyAttachment.storedDy(world,pos))) throw h.createError("direct removal retained a departed height");
        world.getWorldChunk(pos).setBlockState(pos,Blocks.STONE.getDefaultState(),0);
        if (!Double.isNaN(SlabPlacementDyAttachment.storedDy(world,pos))) throw h.createError("replacement inherited a departed height");
        h.complete();
    }
}
