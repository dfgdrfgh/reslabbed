package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.nativeblock.NativePistons;
import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.PhysicalPistons;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vanilla chooses and moves blocks; only its accepted source poses are copied (DY_SPEC.md). */
@Mixin(PistonBlock.class)
public abstract class NativePistonTransferMixin {
    @Unique private static final ThreadLocal<ArrayDeque<Map<BlockPos,Integer>>> SLABBED$MOVES=
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method="shouldExtend",at=@At("RETURN"),cancellable=true)
    private void slabbed$physicalPower(net.minecraft.world.RedstoneView world,BlockPos pos,Direction facing,
                                       CallbackInfoReturnable<Boolean> result) {
        if(!result.getReturnValue() && com.slabbed.nativeblock.NativePistonSignal.physicalPower(world,pos,facing))
            result.setReturnValue(true);
        if(com.slabbed.debug.DiagnosticRecorder.isActive() && world instanceof World actual)
            com.slabbed.debug.DiagnosticWorld.piston(actual,pos,"piston_power","facing="+facing+" powered="+result.getReturnValue()
                    +" half="+NativePistons.baseHalf(actual,pos));
    }

    @WrapMethod(method="move")
    private boolean slabbed$move(World world,BlockPos pos,Direction direction,boolean extending,Operation<Boolean> original) {
        var stack=SLABBED$MOVES.get();
        var destinations=new LinkedHashMap<BlockPos,Integer>();stack.push(destinations);
        try {
            var plan=PhysicalPistons.prepare(world,pos,direction,extending);
            java.util.function.BooleanSupplier vanilla=()-> {
                boolean moved=original.call(world,pos,direction,extending);
                if(moved) destinations.forEach((destination,half)->NativePistons.publish(world,destination,half));
                return moved;
            };
            boolean moved=plan==null?vanilla.getAsBoolean():PhysicalPistons.move(world,plan,vanilla);
            if(com.slabbed.debug.DiagnosticRecorder.isActive())com.slabbed.debug.DiagnosticWorld.piston(world,pos,"piston_move",
                    "facing="+direction+" extending="+extending+" accepted="+moved+" path="+(plan==null?"vanilla":"physical"));
            return moved;
        } finally {
            stack.pop();
            if (stack.isEmpty()) SLABBED$MOVES.remove();
        }
    }

    @WrapOperation(method="tryMove",at=@At(value="INVOKE",target="Lnet/minecraft/block/piston/PistonHandler;calculatePush()Z"))
    private boolean slabbed$physicalPreflight(PistonHandler handler,Operation<Boolean> original,
            @Local(argsOnly=true) World world,@Local(argsOnly=true) BlockPos pos,
            @Local(argsOnly=true) BlockState state) {
        var plan=PhysicalPistons.prepare(world,pos,state.get(PistonBlock.FACING),true);
        boolean accepted=plan==null?original.call(handler):plan.accepted();
        if(plan==null && !accepted)com.slabbed.debug.DiagnosticWorld.piston(world,pos,"piston_plan","VANILLA_REJECTED");
        return accepted;
    }

    @WrapOperation(method="onSyncedBlockEvent",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean slabbed$retractingBasePose(World world,BlockPos target,BlockState next,int flags,Operation<Boolean> original,
            @Local(argsOnly=true) BlockPos piston) {
        int half=NativePistons.baseHalf(world,piston);
        boolean changed=original.call(world,target,next,flags);
        if(changed && target.equals(piston) && next.isOf(net.minecraft.block.Blocks.MOVING_PISTON))
            NativePistons.publish(world,target,half);
        return changed;
    }

    @WrapOperation(method="onSyncedBlockEvent",at=@At(value="INVOKE",target="Lnet/minecraft/block/PistonBlock;isMovable(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;ZLnet/minecraft/util/math/Direction;)Z"))
    private boolean slabbed$physicalPull(BlockState target,World world,BlockPos targetPos,Direction motion,
            boolean canBreak,Direction facing,Operation<Boolean> original,@Local(argsOnly=true) BlockPos pos) {
        var plan=PhysicalPistons.prepare(world,pos,facing,false);
        return plan==null?original.call(target,world,targetPos,motion,canBreak,facing):plan.accepted();
    }

    @WrapOperation(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/block/piston/PistonHandler;calculatePush()Z"))
    private boolean slabbed$capture(PistonHandler handler,Operation<Boolean> original,
            @Local(argsOnly=true) World world,@Local(argsOnly=true) BlockPos pos,
            @Local(argsOnly=true) Direction direction,@Local(argsOnly=true) boolean extending) {
        boolean accepted=PhysicalPistons.calculatePush(handler,()->original.call(handler));
        if (!accepted) {
            com.slabbed.debug.DiagnosticWorld.piston(world,pos,"piston_plan","VANILLA_REJECTED");return false;
        }
        if (!PhysicalPistons.vanillaMatches(handler.getMovedBlocks())) {
            com.slabbed.debug.DiagnosticWorld.piston(world,pos,"piston_plan","NATIVE_GRAPH_MISMATCH");return false;
        }
        var destinations=SLABBED$MOVES.get().peek();
        if (destinations==null) return true;
        var motion=extending?direction:direction.getOpposite();
        for (var source:handler.getMovedBlocks()) slabbed$copy(world,source,source.offset(motion),destinations);
        if (extending) slabbed$copy(world,pos,pos.offset(direction),destinations);
        return true;
    }

    @WrapMethod(method="onSyncedBlockEvent")
    private boolean slabbed$base(BlockState state,World world,BlockPos pos,int type,int data,Operation<Boolean> original) {
        var seat=NativeSeats.seat(world,pos);
        return com.slabbed.cell.CellWorld.pistonTransaction(world,()->NativePistons.withBasePose(world,pos,seat,()-> {
            boolean accepted=original.call(state,world,pos,type,data);
            if(accepted && seat!=null)NativePistons.publish(world,pos,seat.halfOffset());
            return accepted;
        }));
    }

    @Unique private static void slabbed$copy(World world,BlockPos source,BlockPos destination,Map<BlockPos,Integer> destinations) {
        var seat=NativeSeats.seat(world,source);
        if (seat!=null) destinations.put(destination.toImmutable(),seat.halfOffset());
    }
}
