package com.slabbed.mixin;

import com.slabbed.cell.CellBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(ExplosionBehavior.class)
public abstract class ExplosionResistanceMixin {
    @Inject(method="getBlastResistance",at=@At("HEAD"),cancellable=true)
    private void slabbed$materialResistance(Explosion explosion,BlockView world,BlockPos pos,BlockState state,FluidState fluid,CallbackInfoReturnable<Optional<Float>> cir) {
        if(world.getBlockEntity(pos) instanceof CellBlockEntity cell) {
            float resistance=fluid.getBlastResistance();
            for(var part:cell.contents().fragments())resistance=Math.max(resistance,part.value().getBlock().getBlastResistance());
            cir.setReturnValue(Optional.of(resistance));
        } else {
            var parts=com.slabbed.cell.SupplementalCells.contents(world,pos);
            if(parts!=null) {
                float resistance=Math.max(fluid.getBlastResistance(),state.getBlock().getBlastResistance());
                for(var part:parts.fragments())resistance=Math.max(resistance,part.value().getBlock().getBlastResistance());
                cir.setReturnValue(Optional.of(resistance));
            }
        }
    }
}
