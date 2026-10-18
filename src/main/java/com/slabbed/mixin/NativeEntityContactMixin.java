package com.slabbed.mixin;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatch physical contacts whose native owner is outside vanilla's touched cells. */
@Mixin(Entity.class)
public abstract class NativeEntityContactMixin {
    @Inject(method="baseTick",at=@At("TAIL"))
    private void slabbed$projectedContacts(CallbackInfo ci) {
        var entity=(Entity)(Object)this;var world=entity.getWorld();var box=entity.getBoundingBox().contract(.001);
        int minX=MathHelper.floor(box.minX),maxX=MathHelper.floor(box.maxX);
        int minY=MathHelper.floor(box.minY),maxY=MathHelper.floor(box.maxY);
        int minZ=MathHelper.floor(box.minZ),maxZ=MathHelper.floor(box.maxZ);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY-1;y<=maxY+1;y++) {
            if(y>=minY && y<=maxY)continue;
            var pos=new BlockPos(x,y,z);if(!world.isChunkLoaded(pos))continue;
            var seat=NativeSeats.seat(world,pos);if(seat==null || seat.halfOffset()==0)continue;
            var state=world.getBlockState(pos);var shape=state.getOutlineShape(world,pos);
            if(!shape.isEmpty() && shape.getBoundingBox().offset(pos).intersects(box))state.onEntityCollision(world,pos,entity);
        }
    }
}
