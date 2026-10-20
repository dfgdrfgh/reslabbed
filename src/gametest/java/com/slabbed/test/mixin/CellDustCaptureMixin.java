package com.slabbed.test.mixin;

import com.slabbed.test.CellClientLifecycleRegression;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockDustParticle.class)
public abstract class CellDustCaptureMixin {
    @Inject(method="<init>(Lnet/minecraft/client/world/ClientWorld;DDDDDDLnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)V",at=@At("RETURN"))
    private void slabbed$actualDust(ClientWorld world,double x,double y,double z,double vx,double vy,double vz,
                                    BlockState material,BlockPos pos,CallbackInfo ci) {
        CellClientLifecycleRegression.dust(material,x,y,z);
        com.slabbed.test.SlabFeedbackAudit.record((BlockDustParticle)(Object)this,material,y);
    }
}
