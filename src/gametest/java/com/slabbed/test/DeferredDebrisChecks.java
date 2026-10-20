package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellWorld;
import com.slabbed.cell.SupplementalCells;
import com.slabbed.client.CellBreakEffects;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

/** Controlled client packet-order states, using real geometry and production debris constructors. */
public final class DeferredDebrisChecks {
    private static final BlockPos POS=new BlockPos(80,100,18);
    private DeferredDebrisChecks() {}
    public static void run(MinecraftClient client) {
        var world=client.world;
        check(world.isChunkLoaded(POS),"deferred debris fixture chunk is loaded");
        for(boolean shared:new boolean[]{false,true})for(boolean lowerFirst:new boolean[]{false,true}) {
            clear(world);fixture(world,shared);
            var lower=CellWorld.contents(world,POS).fragments().getFirst();
            var upper=CellWorld.contents(world,POS.up()).fragments().getFirst();
            CellFeedbackAudit.reset();
            CellBreakEffects.play(world,POS,lower);
            CellBreakEffects.play(world,POS.up(),upper);
            tick(world);
            check(CellFeedbackAudit.breaks==0,"unconfirmed piece must not emit debris");
            removeHalf(world,shared,lowerFirst);tick(world);
            check(CellFeedbackAudit.breaks==0,"one remaining fragment must delay the entire burst");
            removeHalf(world,shared,!lowerFirst);tick(world);
            check(CellFeedbackAudit.breaks==64 && CellFeedbackAudit.material.isOf(Blocks.STONE),"one complete snapshotted burst after both fragments disappear");
            tick(world);check(CellFeedbackAudit.breaks==64,"duplicate fragment requests must emit only once");
            if(shared)check(world.getBlockState(POS).isOf(Blocks.GRASS_BLOCK) && NativeSeats.offset(world,POS)==-.5,"debris must preserve native owner pose");
        }
        clear(world);fixture(world,false);
        var piece=CellWorld.contents(world,POS).fragments().getFirst();
        CellFeedbackAudit.reset();CellBreakEffects.play(world,POS,piece);
        removeHalf(world,false,true);removeHalf(world,false,false);
        world.setBlockState(POS,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
        for(int i=0;i<101;i++)tick(world);
        check(CellFeedbackAudit.breaks==0,"solid replacement must block debris until the request expires");
        clear(world);tick(world);check(CellFeedbackAudit.breaks==0,"expired obstructed effect must not reappear");

        fixture(world,false);piece=CellWorld.contents(world,POS).fragments().getFirst();
        CellBreakEffects.play(world,POS,piece);
        for(int i=0;i<101;i++)tick(world);
        clear(world);tick(world);
        check(CellFeedbackAudit.breaks==0,"denied break must expire without later debris");

        fixture(world,false);piece=CellWorld.contents(world,POS).fragments().getFirst();
        CellBreakEffects.play(world,POS,piece);tick(null);clear(world);tick(world);
        check(CellFeedbackAudit.breaks==0,"world change must discard pending effects");

        fixture(world,false);piece=CellWorld.contents(world,POS).fragments().getFirst();
        CellBreakEffects.play(world,POS,piece);clear(world);
        world.setBlockState(POS,Slabbed.CELL.getDefaultState(),Block.NOTIFY_LISTENERS);
        world.removeBlockEntity(POS);tick(world);
        check(CellFeedbackAudit.breaks==0,"carrier without contents must wait for its geometry data");
        clear(world);tick(world);
        check(CellFeedbackAudit.breaks==64,"cleared incomplete carrier permits the saved burst");

        fixture(world,false);piece=CellWorld.contents(world,POS).fragments().getFirst();
        CellFeedbackAudit.reset();CellBreakEffects.play(world,POS,piece);
        var chunk=world.getWorldChunk(POS);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(world,chunk);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(world,chunk);
        clear(world);tick(world);
        check(CellFeedbackAudit.breaks==0,"chunk unload callback must discard pending effects");

        fixture(world,false);piece=CellWorld.contents(world,POS).fragments().getFirst();
        CellBreakEffects.play(world,POS,piece);clear(world);
        check(CellWorld.place(world,POS.getX(),POS.getZ(),POS.getY()*2+1,Blocks.BIRCH_SLAB.getDefaultState()),"replacement slab fixture placed");
        var replacement=CellWorld.contents(world,POS).fragments().getFirst();
        CellBreakEffects.play(world,POS,replacement);
        CellWorld.remove(world,POS,replacement.offset(),null,false);tick(world);
        check(CellFeedbackAudit.breaks==32 && CellFeedbackAudit.material.isOf(Blocks.BIRCH_SLAB),"replacement request must use its new material and size");
        clear(world);
        System.out.println("PHYSICAL_DEFERRED_DEBRIS_ORDER_PASS carrier supplemental both_fragment_orders duplicate_requests solid_replacement denial_expiry world_change missing_carrier_data chunk_unload material_replacement");
    }
    private static void fixture(ClientWorld world,boolean shared) {
        if(shared) {
            world.setBlockState(POS,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);
            NativeSeats.put(world,POS,new NativeSeats.Seat(-1,"minecraft:grass_block"));
        }
        check(CellWorld.place(world,POS.getX(),POS.getZ(),POS.getY()*2+1,Blocks.STONE.getDefaultState()),"crossing debris fixture placed");
    }
    private static void removeHalf(ClientWorld world,boolean shared,boolean lower) {
        if(lower && shared)SupplementalCells.remove(world,POS);
        else world.setBlockState(lower?POS:POS.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
    }
    private static void clear(ClientWorld world) {
        SupplementalCells.remove(world,POS);
        NativeSeats.remove(world,POS);
        world.setBlockState(POS,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        world.setBlockState(POS.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
    }
    private static void tick(ClientWorld world) {
        try {
            var method=CellBreakEffects.class.getDeclaredMethod("tick",ClientWorld.class);
            method.setAccessible(true);method.invoke(null,world);
        } catch(ReflectiveOperationException e) {throw new AssertionError("production debris tick failed",e);}
    }
    private static void check(boolean valid,String message) {if(!valid)throw new AssertionError(message);}
}
