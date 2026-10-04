package com.slabbed.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places a bed's head half on the client as soon as the foot is placed, exactly as Minecraft 26.2 does.
 *
 * <p>On 26.1.2 vanilla writes the head only on the server, so the client's copy of the level holds a
 * lone foot until the server's block update arrives. Slabbed's client-side height prediction publishes a
 * bed only as a complete pair (LAW.md: a malformed pair writes neither cell), so without this the bed is
 * drawn at grid height for one round trip and then drops to its stored height. The server remains the
 * authority: its own head placement and the usual block updates overwrite whatever the client predicted.
 */
@Mixin(BedBlock.class)
abstract class BedBlockClientHeadPlacementMixin {

    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void slabbed$placeHeadOnClient(Level level, BlockPos pos, BlockState state, LivingEntity placer,
                                           ItemStack stack, CallbackInfo ci) {
        if (!level.isClientSide()
                || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || !state.hasProperty(BlockStateProperties.BED_PART)
                || state.getValue(BlockStateProperties.BED_PART) != BedPart.FOOT) {
            return;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos headPos = pos.relative(facing);
        if (!level.getBlockState(headPos).canBeReplaced()) {
            return;
        }
        level.setBlockAndUpdate(headPos, state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
    }
}
