package com.slabbed.test.mixin;

import com.slabbed.test.CellFeedbackAudit;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayDeque;
import java.util.Map;

/** Checks the real submitted queues after production synchronization. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",remap=false,priority=900)
public abstract class SodiumCellQueueCaptureMixin {
    @Shadow private Map<TaskQueueType,ArrayDeque<RenderSection>> taskLists;
    @Inject(method="createTerrainRenderList",at=@At("RETURN"))
    private void slabbed$recordQueues(CallbackInfoReturnable<Boolean> cir) {
        for(var entry:taskLists.entrySet()) {
            if(entry.getKey()==TaskQueueType.INITIAL_BUILD)continue;
            for(var section:entry.getValue()) if(CellFeedbackAudit.changedSections.contains(
                    net.minecraft.util.math.ChunkSectionPos.asLong(section.getChunkX(),section.getChunkY(),section.getChunkZ()))) {
                CellFeedbackAudit.queuedCellSections++;
                if(entry.getKey()!=TaskQueueType.ZERO_FRAME_DEFER)CellFeedbackAudit.delayedCellSections++;
            }
        }
    }
}
