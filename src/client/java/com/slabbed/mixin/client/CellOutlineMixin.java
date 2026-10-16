package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public abstract class CellOutlineMixin {
    @Redirect(method="drawBlockOutline",at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;"))
    private VoxelShape slabbed$wholeSelectedPiece(BlockState state,BlockView view,BlockPos pos,ShapeContext context) {
        if(state.isOf(Slabbed.CELL) && view instanceof World world
                && MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)) {
            var part=CellWorld.pick(world,pos,hit);
            if(part!=null)return part.value().getOutlineShape(view,pos,context).offset(0,part.offset()*.5,0);
        }
        return state.getOutlineShape(view,pos,context);
    }
}
