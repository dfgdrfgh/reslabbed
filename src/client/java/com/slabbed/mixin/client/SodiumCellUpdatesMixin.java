package com.slabbed.mixin.client;

import com.slabbed.client.CellRenderSync;
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

/** Keeps cell transitions atomic even when ordinary Sodium updates defer one frame. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",remap=false)
public abstract class SodiumCellUpdatesMixin {
    @Shadow private Map<TaskQueueType,ArrayDeque<RenderSection>> taskLists;
    @Inject(method="createTerrainRenderList",at=@At("RETURN"),require=0)
    private void slabbed$synchronizeCells(CallbackInfoReturnable<Boolean> cir) {
        var immediate=taskLists.get(TaskQueueType.ZERO_FRAME_DEFER);
        for(var type:new TaskQueueType[]{TaskQueueType.ONE_FRAME_DEFER,TaskQueueType.ALWAYS_DEFER}) {
            var iterator=taskLists.get(type).iterator();
            while(iterator.hasNext()) {
                var section=iterator.next();
                if(CellRenderSync.changed(section.getChunkX(),section.getChunkY(),section.getChunkZ())) {
                    iterator.remove();immediate.addLast(section);
                }
            }
        }
    }
}
