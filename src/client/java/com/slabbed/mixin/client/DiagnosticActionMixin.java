package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.debug.DiagnosticActions;
import com.slabbed.debug.DiagnosticRecorder;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;

/** Observe final interaction results, including earlier cancellable mixins. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class DiagnosticActionMixin {
    @WrapMethod(method="interactBlock")
    private ActionResult slabbed$use(ClientPlayerEntity player,Hand hand,BlockHitResult hit,Operation<ActionResult> original) {
        var item=DiagnosticActions.item(player.getStackInHand(hand));
        var result=original.call(player,hand,hit);DiagnosticActions.block("client",player.getWorld(),hit,item,result);return result;
    }
    @WrapMethod(method="interactItem")
    private ActionResult slabbed$item(PlayerEntity player,Hand hand,Operation<ActionResult> original) {
        var item=DiagnosticActions.item(player.getStackInHand(hand));var result=original.call(player,hand);
        if(DiagnosticRecorder.isActive())DiagnosticRecorder.record("client","item_use","hand="+hand+" item="+item+" result="+result);
        return result;
    }
    @WrapMethod(method="attackBlock")
    private boolean slabbed$attack(BlockPos pos,Direction face,Operation<Boolean> original) {
        boolean result=original.call(pos,face);
        if(DiagnosticRecorder.isActive())DiagnosticRecorder.record("client","block_attack","pos="+pos.toShortString()+" face="+face+" result="+result);
        return result;
    }
    @WrapMethod(method="breakBlock")
    private boolean slabbed$break(BlockPos pos,Operation<Boolean> original) {
        boolean result=original.call(pos);
        if(DiagnosticRecorder.isActive())DiagnosticRecorder.record("client","block_break","pos="+pos.toShortString()+" result="+result);
        return result;
    }
}
