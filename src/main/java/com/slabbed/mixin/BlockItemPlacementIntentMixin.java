package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.anchor.C3TestPhaseTrace;
import com.slabbed.anchor.PlacementDyOverlay;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.ConnectorPlacementSettle;
import com.slabbed.placement.LandingResolver;
import com.slabbed.placement.PlacementAdapters;
import com.slabbed.placement.PlacementPlan;
import com.slabbed.placement.PlacementTransaction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayDeque;

/** One captured aim, one preflight, and one publication for an accepted placement (LAW.md). */
@Mixin(BlockItem.class)
public abstract class BlockItemPlacementIntentMixin {
    @Unique
    private static final ThreadLocal<LandingResolver.PlacementAim> ROOT_AIM = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<ArrayDeque<PlacementFrame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private static final class PlacementFrame {
        final LandingResolver.PlacementAim aim;
        final ItemStack stack;
        final int initialCount;
        final net.minecraft.world.World world;
        PlacementPlan plan;
        PlacementTransaction.Prepared prepared;
        boolean refused;
        boolean published;

        PlacementFrame(ItemPlacementContext context) {
            aim = ROOT_AIM.get();
            stack = context.getStack();
            initialCount = stack.getCount();
            world = context.getWorld();
        }
    }

    @Unique
    private static PlacementFrame frame() { return FRAMES.get().peek(); }

    @WrapMethod(method = "useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;")
    private ActionResult slabbed$capturedAim(ItemUsageContext context, Operation<ActionResult> original) {
        var previous = ROOT_AIM.get();
        boolean root = previous == null;
        var aim = root ? LandingResolver.captureAim(context) : previous;
        if (root) ROOT_AIM.set(aim);
        try {
            if (!root || aim.ownerVisibleDy() == 0.0d) return original.call(context);
            // Vanilla interprets hit fractions in the owner's native cell. The immutable root aim
            // retains the physical hit; only the context handed to vanilla changes coordinate frame.
            Vec3d physical = context.getHitPos();
            var hit = new BlockHitResult(new Vec3d(physical.x, physical.y - aim.ownerVisibleDy(), physical.z),
                    context.getSide(), context.getBlockPos(), context.hitsInsideBlock());
            var nativeContext = new ItemUsageContext(context.getWorld(), context.getPlayer(), context.getHand(),
                    context.getStack(), hit) {};
            return original.call(nativeContext);
        } finally {
            if (root) ROOT_AIM.remove();
        }
    }

    @WrapMethod(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;")
    private ActionResult slabbed$placementScope(ItemPlacementContext context, Operation<ActionResult> original) {
        var current = new PlacementFrame(context);
        var frames = FRAMES.get();
        frames.push(current);
        boolean success = false;
        try {
            ActionResult result = original.call(context);
            success = result != null && result.isAccepted() && !current.refused && current.published;
            if (!success && current.prepared != null) {
                current.prepared.abort();
                current.stack.setCount(current.initialCount);
                return result != null && !result.isAccepted() ? result : ActionResult.FAIL;
            }
            return result;
        } finally {
            if (current.prepared != null) {
                if (success) current.prepared.finish();
                else {
                    current.prepared.abort();
                    current.stack.setCount(current.initialCount);
                }
            }
            frames.pop();
            if (frames.isEmpty()) FRAMES.remove();
        }
    }

    @Unique
    private static boolean prepare(PlacementFrame current, ItemPlacementContext context, BlockState state) {
        if (current == null || state == null) return false;
        if (current.prepared != null) return current.prepared.accepted();
        current.plan = PlacementAdapters.plan(context.getWorld(), current.aim, context, state);
        current.prepared = PlacementTransaction.prepare(context.getWorld(), current.plan);
        if (!current.prepared.accepted()) {
            current.refused = true;
            return false;
        }
        C3TestPhaseTrace.markTestPhase("prepare");
        return true;
    }

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void slabbed$preflightBeforeMutation(ItemPlacementContext context, BlockState state,
                                                CallbackInfoReturnable<Boolean> cir) {
        var current = frame();
        if (current != null && !prepare(current, context, state)) cir.setReturnValue(false);
    }

    @WrapOperation(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BlockItem;getPlacementState(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/block/BlockState;"))
    private BlockState slabbed$preflightTransformedTarget(BlockItem item, ItemPlacementContext context,
                                                         Operation<BlockState> original) {
        BlockState state = original.call(item, context);
        return state != null && prepare(frame(), context, state) ? state : null;
    }

    @WrapOperation(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrementUnlessCreative(ILnet/minecraft/entity/LivingEntity;)V"))
    private void slabbed$publishBeforeConsumption(ItemStack stack, int amount, LivingEntity entity,
                                                 Operation<Void> original) {
        var current = frame();
        if (current == null || current.prepared == null || !current.prepared.commit()) {
            if (current != null) current.refused = true;
            return;
        }
        current.published = true;
        C3TestPhaseTrace.markTestPhase("publish");
        var world = current.world;
        if (current.aim != null && world != null && world.isClient()) {
            int sequence = PlacementDyOverlay.currentSequence();
            if (sequence >= 0) PlacementDyOverlay.installPredictedPlacement(world,
                    current.aim.ownerPos(), current.aim.clickedFace(), sequence, current.plan.rawHeights());
        }
        if (world != null && !world.isClient()) {
            SlabAnchorAttachment.markPostPolicyPlacements(world, current.plan.rawHeights().keySet());
            ConnectorPlacementSettle.settlePublishedPlacements(world, current.plan.rawHeights().keySet());
        }
        original.call(stack, amount, entity);
    }
}
