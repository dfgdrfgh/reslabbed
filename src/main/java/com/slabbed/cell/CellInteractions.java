package com.slabbed.cell;

import com.slabbed.Slabbed;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/** Stateful actions run against the selected piece without replacing its neighbors. */
public final class CellInteractions {
    private CellInteractions() {}
    public static void register() { UseBlockCallback.EVENT.register(CellInteractions::use); }
    public static ActionResult use(PlayerEntity player,World world,Hand hand,BlockHitResult hit) {
        if(player.isSpectator() || (!world.getBlockState(hit.getBlockPos()).isOf(Slabbed.CELL) && !SupplementalCells.has(world,hit.getBlockPos()))) return ActionResult.PASS;
        var part=CellWorld.pick(world,hit.getBlockPos(),hit);
        if(part==null || !(part.value().getBlock() instanceof FlowerPotBlock pot)) return ActionResult.PASS;
        if(!player.getAbilities().allowModifyWorld || !world.canPlayerModifyAt(player,hit.getBlockPos())) return ActionResult.FAIL;
        var stack=player.getStackInHand(hand); BlockState next=null; boolean planting=false;
        if(stack.getItem() instanceof BlockItem item) {
            for(var block:Registries.BLOCK) if(block instanceof FlowerPotBlock candidate && candidate.getContent()==item.getBlock()) {
                next=block.getDefaultState(); planting=true;break;
            }
        }
        boolean empty=pot.getContent()==Blocks.AIR;
        if(empty==planting) {
            if(world.isClient)return ActionResult.SUCCESS;
            if(!CellWorld.replacePiece(world,hit.getBlockPos(),part.offset(),empty?next:Blocks.FLOWER_POT.getDefaultState()))return ActionResult.FAIL;
            if(empty) { if(!player.isCreative())stack.decrement(1); }
            else {
                var plant=new ItemStack(pot.getContent());
                if(stack.isEmpty())player.setStackInHand(hand,plant);
                else if(!player.getInventory().insertStack(plant))player.dropItem(plant,false);
            }
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }
}
