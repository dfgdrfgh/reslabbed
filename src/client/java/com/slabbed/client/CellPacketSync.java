package com.slabbed.client;

import com.slabbed.Slabbed;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import java.util.HashMap;
import java.util.Map;

/** A carrier becomes visible only when its authoritative physical contents are available. */
public final class CellPacketSync {
    public interface Access { CellPacketSync slabbed$cellPackets(); }
    private record Pending(BlockState state,int flags) {}
    private final Map<BlockPos,Pending> pending=new HashMap<>();
    private boolean applying;
    public boolean defer(ClientWorld world,BlockPos pos,BlockState state,int flags) {
        if(applying)return false;
        if(!state.isOf(Slabbed.CELL)) { pending.remove(pos);return false; }
        if(!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos) || world.getBlockState(pos).isOf(Slabbed.CELL))return false;
        pending.put(pos.toImmutable(),new Pending(state,flags));
        return true;
    }
    public void receive(ClientWorld world,BlockEntityUpdateS2CPacket packet) {
        if(packet.getBlockEntityType()!=Slabbed.CELL_ENTITY || packet.getNbt()==null
                || !packet.getNbt().contains("parts",NbtElement.LIST_TYPE))return;
        var next=pending.remove(packet.getPos());
        if(next==null || !world.isChunkLoaded(packet.getPos()))return;
        applying=true;
        try {
            world.handleBlockUpdate(packet.getPos(),next.state(),next.flags());
            // Pending vanilla predictions retain server states until their acknowledgement.
            // Record that state above, and create the carrier now so vanilla can read its NBT.
            if(!world.getBlockState(packet.getPos()).isOf(Slabbed.CELL))
                world.setBlockState(packet.getPos(),next.state(),next.flags());
        } finally { applying=false; }
    }
    public void unload(ChunkPos chunk) {
        pending.keySet().removeIf(pos->(pos.getX()>>4)==chunk.x && (pos.getZ()>>4)==chunk.z);
    }
    public void clear() { pending.clear(); }
}
