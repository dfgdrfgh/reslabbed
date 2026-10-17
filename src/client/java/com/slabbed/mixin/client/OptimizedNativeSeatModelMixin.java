package com.slabbed.mixin.client;

import com.slabbed.client.NativeSeatModel;
import com.slabbed.nativeblock.NativeRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** OBE selects terrain models after model baking, on each rendering pipeline. */
@Pseudo
@Mixin(targets="fr.madu59.obe.client.util.meshing.SectionMeshingUtil",remap=false)
public abstract class OptimizedNativeSeatModelMixin {
    @Inject(method="getCorrectedModel",at=@At("RETURN"),cancellable=true,remap=false)
    private static void slabbed$seatOptimizedModel(BlockState state,BlockEntity entity,BakedModel original,
                                                   CallbackInfoReturnable<BakedModel> result) {
        var model=result.getReturnValue();
        if(entity!=null && model!=null && NativeRules.accepts(state) && !(model instanceof NativeSeatModel))
            result.setReturnValue(new NativeSeatModel(model));
    }
}
