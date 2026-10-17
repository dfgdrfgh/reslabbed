package com.slabbed.test;

import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A blocked placement must finish on the server before its test obstruction is removed. */
public final class CellClearancePacketAudit {
    private static volatile BlockPos watched;
    public static volatile boolean completed,unchanged;
    private CellClearancePacketAudit() {}
    public static void arm(BlockPos pos) {completed=false;unchanged=false;watched=pos.toImmutable();}
    public static void handled(World world,PlayerInteractBlockC2SPacket packet) {
        var pos=watched;
        if(pos==null || !packet.getBlockHitResult().getBlockPos().equals(pos))return;
        unchanged=world.getBlockState(pos).isOf(Blocks.STONE_SLAB) && world.getBlockState(pos.up()).isOf(Blocks.STONE);
        watched=null;completed=true;
    }
}
