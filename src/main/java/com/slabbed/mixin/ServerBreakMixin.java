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
        if(!world.getBlockState(pos).isOf(Slabbed.CELL)) return;
        if(!world.canPlayerModifyAt(player,pos) || player.isBlockBreakingRestricted(world,pos,player.interactionManager.getGameMode())) { cir.setReturnValue(false); return; }
        var hit=player.raycast(player.getBlockInteractionRange(),1,false);
        if(!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) { cir.setReturnValue(false); return; }
        var part=CellWorld.pick(world,pos,blockHit);
        if(part==null) { cir.setReturnValue(false); return; }
        CellWorld.remove(world,pos,part.offset(),player,!player.isCreative());
        if(!player.isCreative()) player.getMainHandStack().postMine(world,part.value(),pos,player);
        cir.setReturnValue(true);
    }
}
