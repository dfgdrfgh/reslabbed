package com.slabbed.client;

import com.slabbed.core.CellContents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Material feedback uses the entire selected piece at its permanent physical origin. */
public final class CellBreakEffects {
    private CellBreakEffects() {}
    public static void play(ClientWorld world,BlockPos pos,CellContents.Fragment<BlockState> piece) {
        var state=piece.value();var shape=state.getOutlineShape(world,pos);
        var center=shape.isEmpty()?new Vec3d(.5,.5,.5):shape.getBoundingBox().getCenter();
        center=center.add(pos.getX(),pos.getY()+piece.offset()*.5,pos.getZ());
        var sound=state.getSoundGroup();
        world.playSound(center.x,center.y,center.z,sound.getBreakSound(),SoundCategory.BLOCKS,
                (sound.getVolume()+1)*.5f,sound.getPitch()*.8f,false);
        var effect=new BlockStateParticleEffect(ParticleTypes.BLOCK,state);
        for(var box:shape.getBoundingBoxes()) {
            double dx=Math.min(1,box.maxX-box.minX),dy=Math.min(1,box.maxY-box.minY),dz=Math.min(1,box.maxZ-box.minZ);
            int nx=Math.max(2,MathHelper.ceil(dx/.25)),ny=Math.max(2,MathHelper.ceil(dy/.25)),nz=Math.max(2,MathHelper.ceil(dz/.25));
            for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++) {
                double u=(x+.5)/nx,v=(y+.5)/ny,w=(z+.5)/nz;
                MinecraftClient.getInstance().particleManager.addParticle(effect,
                        pos.getX()+box.minX+u*dx,pos.getY()+piece.offset()*.5+box.minY+v*dy,pos.getZ()+box.minZ+w*dz,
                        u-.5,v-.5,w-.5);
            }
        }
    }
}
