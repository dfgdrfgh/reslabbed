package com.slabbed.mixin.client;

import com.slabbed.cell.CellBlock;
import com.slabbed.cell.CellWorld;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CellBlock.class)
public abstract class CellPickMixin {
    @Inject(method="getPickStack",at=@At("HEAD"),cancellable=true)
    private void slabbed$pickAimedMaterial(WorldView view,BlockPos pos,BlockState state,CallbackInfoReturnable<ItemStack> cir) {
        if(view instanceof World world && MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)) {
            var part=CellWorld.pick(world,pos,hit);
            if(part!=null)cir.setReturnValue(part.value().getBlock().getPickStack(view,pos,part.value()));
        }
    }
}
