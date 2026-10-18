package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerBreakMixin {
    @Shadow protected ServerWorld world;
    @Shadow @Final protected ServerPlayerEntity player;
    @Inject(method="tryBreakBlock",at=@At("HEAD"),cancellable=true)
    private void slabbed$breakPiece(BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(!world.getBlockState(pos).isOf(Slabbed.CELL) && !com.slabbed.cell.SupplementalCells.has(world,pos)) return;
        var part=com.slabbed.cell.PieceTarget.selected(player,pos);
        if(part==null && !world.getBlockState(pos).isOf(Slabbed.CELL))return;
        if(!world.canPlayerModifyAt(player,pos) || player.isBlockBreakingRestricted(world,pos,player.interactionManager.getGameMode())) { cir.setReturnValue(false); return; }
        if(part==null) { cir.setReturnValue(false); return; }
        var tool=player.getMainHandStack();
        if(!tool.getItem().canMine(part.value(),world,pos,player)) { cir.setReturnValue(false); return; }
        boolean harvest=player.canHarvest(part.value());
        var usedTool=tool.copy();
        part.value().getBlock().onBreak(world,pos,part.value(),player);
        CellWorld.remove(world,pos,part.offset(),player,false);
        if(!player.isCreative()) {
            tool.postMine(world,part.value(),pos,player);
            if(harvest) part.value().getBlock().afterBreak(world,player,pos,part.value(),null,usedTool);
        }
        cir.setReturnValue(true);
    }
}
