package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.debug.DiagnosticActions;
import com.slabbed.debug.DiagnosticRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/** Integrated server confirmation is distinct from client prediction. */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class DiagnosticServerActionMixin {
    @WrapMethod(method="interactBlock")
    private ActionResult slabbed$use(ServerPlayerEntity player,World world,ItemStack stack,Hand hand,BlockHitResult hit,Operation<ActionResult> original) {
        var item=DiagnosticActions.item(stack);var result=original.call(player,world,stack,hand,hit);
        DiagnosticActions.block("server",world,hit,item,result);return result;
    }
    @WrapMethod(method="interactItem")
    private ActionResult slabbed$item(ServerPlayerEntity player,World world,ItemStack stack,Hand hand,Operation<ActionResult> original) {
        var item=DiagnosticActions.item(stack);var result=original.call(player,world,stack,hand);
        if(DiagnosticRecorder.isActive())DiagnosticRecorder.record("server","item_use","hand="+hand+" item="+item+" result="+result);
        return result;
    }
    @WrapMethod(method="tryBreakBlock")
    private boolean slabbed$break(BlockPos pos,Operation<Boolean> original) {
        boolean result=original.call(pos);
        if(DiagnosticRecorder.isActive())DiagnosticRecorder.record("server","block_break","pos="+pos.toShortString()+" result="+result);
        return result;
    }
}
