package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeSupport;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rail neighbor callbacks must test physical support before vanilla's full-cube-only drop test. */
@Mixin(AbstractRailBlock.class)
public abstract class NativeRailSupportMixin {
    @Inject(method="canPlaceAt",at=@At("HEAD"),cancellable=true)
    private void slabbed$directPhysicalFloor(BlockState state,WorldView world,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        var physical=NativeSupport.canPlace(world,pos,state);
        if(physical!=null)cir.setReturnValue(physical);
    }
    @Inject(method="shouldDropRail",at=@At("HEAD"),cancellable=true)
    private static void slabbed$physicalRailSupport(BlockPos pos,World world,RailShape shape,CallbackInfoReturnable<Boolean> cir) {
        var state=world.getBlockState(pos);
        boolean projected=slabbed$shifted(world,pos) || world.getBlockState(pos.down()).isOf(Slabbed.CELL);
        Direction high=switch(shape) {
            case ASCENDING_NORTH -> Direction.NORTH;
            case ASCENDING_SOUTH -> Direction.SOUTH;
            case ASCENDING_EAST -> Direction.EAST;
            case ASCENDING_WEST -> Direction.WEST;
            default -> null;
        };
        if(high!=null)projected|=slabbed$shifted(world,pos.offset(high).up());
        if(projected)cir.setReturnValue(!state.canPlaceAt(world,pos));
    }
    @Unique
    private static boolean slabbed$shifted(World world,BlockPos pos) {
        var seat=NativeSeats.seat(world,pos);
        return seat!=null && seat.halfOffset()!=0;
    }
}
