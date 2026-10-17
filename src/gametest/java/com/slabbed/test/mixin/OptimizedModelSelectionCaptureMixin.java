package com.slabbed.test.mixin;

import com.slabbed.test.NativeRenderAudit;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="fr.madu59.obe.client.util.meshing.SectionMeshingUtil",remap=false,priority=900)
public abstract class OptimizedModelSelectionCaptureMixin {
    @Inject(method="getCorrectedModel",at=@At("RETURN"),remap=false)
    private static void slabbed$observeSelectedModel(BlockState state,BlockEntity entity,BakedModel original,
                                                    CallbackInfoReturnable<BakedModel> result) {
        if(entity!=null)NativeRenderAudit.renderPath(entity.getPos(),result.getReturnValue(),"obe_selection");
    }
}
