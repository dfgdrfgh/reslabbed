package com.slabbed.mixin.client;

import com.slabbed.client.CellPacketSync;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class CellPacketWorldMixin implements CellPacketSync.Access {
    @Unique private final CellPacketSync slabbed$cellPackets=new CellPacketSync();
    @Override public CellPacketSync slabbed$cellPackets() { return slabbed$cellPackets; }
    @Inject(method="handleBlockUpdate",at=@At("HEAD"),cancellable=true)
    private void slabbed$awaitContents(BlockPos pos,BlockState state,int flags,CallbackInfo ci) {
        if(slabbed$cellPackets.defer((ClientWorld)(Object)this,pos,state,flags))ci.cancel();
    }
    @Inject(method="unloadBlockEntities",at=@At("HEAD"))
    private void slabbed$forgetChunk(WorldChunk chunk,CallbackInfo ci) { slabbed$cellPackets.unload(chunk.getPos()); }
    @Inject(method="disconnect",at=@At("HEAD"))
    private void slabbed$forgetWorld(CallbackInfo ci) { slabbed$cellPackets.clear(); }
}
