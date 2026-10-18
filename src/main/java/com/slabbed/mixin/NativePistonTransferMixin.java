package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.nativeblock.NativePistons;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vanilla chooses and moves blocks; only its accepted source poses are copied (DY_SPEC.md). */
@Mixin(PistonBlock.class)
public abstract class NativePistonTransferMixin {
    @Unique private static final ThreadLocal<ArrayDeque<Map<BlockPos,Integer>>> SLABBED$MOVES=
            ThreadLocal.withInitial(ArrayDeque::new);

    @WrapMethod(method="move")
    private boolean slabbed$move(World world,BlockPos pos,Direction direction,boolean extending,Operation<Boolean> original) {
        var stack=SLABBED$MOVES.get();
        var destinations=new LinkedHashMap<BlockPos,Integer>();stack.push(destinations);
        try {
            boolean moved=original.call(world,pos,direction,extending);
            if (moved) destinations.forEach((destination,half)->NativePistons.publish(world,destination,half));
            return moved;
        } finally {
            stack.pop();
            if (stack.isEmpty()) SLABBED$MOVES.remove();
        }
    }

    @WrapOperation(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/block/piston/PistonHandler;calculatePush()Z"))
    private boolean slabbed$capture(PistonHandler handler,Operation<Boolean> original,
            @Local(argsOnly=true) World world,@Local(argsOnly=true) BlockPos pos,
            @Local(argsOnly=true) Direction direction,@Local(argsOnly=true) boolean extending) {
        boolean accepted=original.call(handler);
        if (!accepted) return false;
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
        boolean accepted=original.call(state,world,pos,type,data);
        if (accepted && seat!=null) NativePistons.publish(world,pos,seat.halfOffset());
        return accepted;
    }

    @Unique private static void slabbed$copy(World world,BlockPos source,BlockPos destination,Map<BlockPos,Integer> destinations) {
        var seat=NativeSeats.seat(world,source);
        if (seat!=null) destinations.put(destination.toImmutable(),seat.halfOffset());
    }
}
