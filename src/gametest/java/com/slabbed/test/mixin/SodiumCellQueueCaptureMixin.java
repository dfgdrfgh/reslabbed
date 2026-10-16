package com.slabbed.test.mixin;

import com.slabbed.test.CellFeedbackAudit;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadResourceBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayDeque;
import java.util.Map;

/** Checks the real submitted queues after production synchronization. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",remap=false)
public abstract class SodiumCellQueueCaptureMixin {
    @Shadow private Map<TaskQueueType,ArrayDeque<RenderSection>> taskLists;
    @Inject(method="submitSectionTasks(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/estimation/UploadResourceBudget;Lnet/caffeinemc/mods/sodium/client/render/chunk/TaskQueueType;)V",at=@At("HEAD"))
    private void slabbed$recordQueues(ChunkJobCollector collector,UploadResourceBudget budget,TaskQueueType type,CallbackInfo ci) {
        if(type==TaskQueueType.INITIAL_BUILD)return;
        for(var section:taskLists.get(type)) if(CellFeedbackAudit.changedSections.contains(
                    net.minecraft.util.math.ChunkSectionPos.asLong(section.getChunkX(),section.getChunkY(),section.getChunkZ()))) {
                CellFeedbackAudit.queuedCellSections++;
                if(type!=TaskQueueType.ZERO_FRAME_DEFER)CellFeedbackAudit.delayedCellSections++;
            }
    }
}
