package com.slabbed.test.mixin;

import com.slabbed.Slabbed;
import com.slabbed.test.CellFeedbackAudit;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A cell's immediate renderer must not race a deferred native chunk mesh. */
@Mixin(WorldRenderer.class)
public abstract class CellUpdateCaptureMixin {
    @Inject(method="updateBlock",at=@At("HEAD"))
    private void slabbed$recordRebuild(BlockView view,BlockPos pos,BlockState before,BlockState after,int flags,CallbackInfo ci) {
        if(before.isOf(Slabbed.CELL) || after.isOf(Slabbed.CELL)) {
            CellFeedbackAudit.cellUpdates++;
            CellFeedbackAudit.changedSections.add(net.minecraft.util.math.ChunkSectionPos.from(pos).asLong());
            if((flags & Block.REDRAW_ON_MAIN_THREAD)==0) CellFeedbackAudit.deferredCellUpdates++;
        }
    }
}
