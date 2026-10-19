package com.slabbed.debug;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/** Action metadata excludes item NBT, player identity, chat and command text. */
public final class DiagnosticActions {
    private DiagnosticActions() {}
    public static String item(ItemStack stack) {
        return DiagnosticRecorder.isActive()?Registries.ITEM.getId(stack.getItem())+" count="+stack.getCount():"";
    }
    public static void block(String side,World world,BlockHitResult hit,String item,Object result) {
        if(!DiagnosticRecorder.isActive())return;
        DiagnosticRecorder.record(side,"block_use","dimension="+world.getRegistryKey().getValue()+" pos="+hit.getBlockPos().toShortString()
                +" face="+hit.getSide()+" hit="+hit.getPos()+" item="+item+" result="+result);
    }
}
