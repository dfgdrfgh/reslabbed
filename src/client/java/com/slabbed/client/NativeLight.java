package com.slabbed.client;

import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Light probes in a shifted opaque owner's exposed half use adjacent ordinary world light. */
public final class NativeLight {
    private NativeLight() {}
    public static BlockPos sample(World world,BlockPos sample,double physicalY) {
        double offset=NativeSeats.offset(world,sample);
        if(offset==0 || !world.getBlockState(sample).isOpaqueFullCube(world,sample))return sample;
        double y=physicalY-sample.getY();
        // Collision contact can land one representable double inside the physical surface.
        if(offset<0 && y>=1+offset-1e-7)return sample.up();
        if(offset>0 && y<=offset+1e-7)return sample.down();
        return sample;
    }
}
