package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.client.CellRenderSync;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientWorld.class)
public abstract class CellUpdatesMixin {
    @Redirect(method="updateListeners",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;updateBlock(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;I)V"))
    private void slabbed$synchronizeMesh(WorldRenderer renderer,BlockView view,BlockPos pos,BlockState before,BlockState after,int flags) {
        if(before.isOf(Slabbed.CELL) || after.isOf(Slabbed.CELL)) {
            flags|=Block.REDRAW_ON_MAIN_THREAD;
            CellRenderSync.changed(pos);
        }
        renderer.updateBlock(view,pos,before,after,flags);
    }
}
