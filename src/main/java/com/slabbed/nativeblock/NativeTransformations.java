package com.slabbed.nativeblock;

import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import java.util.function.Supplier;

/** Explicit vanilla tool transformations retain the object's original pose (LAW.md). */
public final class NativeTransformations {
    private record Frame(World world,BlockPos pos,NativeSeats.Seat seat) {}
    private static final ThreadLocal<Frame> ACTIVE=new ThreadLocal<>();
    private NativeTransformations() {}
    public static boolean active(BlockView view,BlockPos pos) {
        var frame=ACTIVE.get();return frame!=null && frame.world()==view && frame.pos().equals(pos);
    }
    public static NativeSeats.Seat pending(BlockView view,BlockPos pos) {
        var frame=ACTIVE.get();
        if(frame==null || frame.world()!=view || !frame.pos().equals(pos))return null;
        var state=view.getBlockState(pos);
        return NativeRules.accepts(state)?new NativeSeats.Seat(frame.seat().halfOffset(),Registries.BLOCK.getId(state.getBlock()).toString()):null;
    }
    public static ActionResult use(ItemUsageContext context,Supplier<ActionResult> vanilla) {
        var world=context.getWorld();var pos=context.getBlockPos().toImmutable();var seat=NativeSeats.seat(world,pos);
        if(seat==null)return vanilla.get();
        var previous=ACTIVE.get();ACTIVE.set(new Frame(world,pos,seat));
        try {return vanilla.get();}
        finally {
            if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);
            var state=world.getBlockState(pos);
            if(NativeRules.accepts(state))NativeSeats.put(world,pos,new NativeSeats.Seat(seat.halfOffset(),Registries.BLOCK.getId(state.getBlock()).toString()));
            else NativeSeats.remove(world,pos);
        }
    }
}
