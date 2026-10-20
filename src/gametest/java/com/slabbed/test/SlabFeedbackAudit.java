package com.slabbed.test;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import java.util.ArrayList;
import java.util.List;

/** Watches actual production dust objects from mining and breaking until they reach the soil surface. */
public final class SlabFeedbackAudit {
    private static final List<BlockDustParticle> DUST=new ArrayList<>();
    private static boolean capture;
    private static int settled,dark;
    private static double hitY=Double.NaN;
    private static final List<String> ERRORS=new ArrayList<>();
    private SlabFeedbackAudit() {}
    public static void begin() {DUST.clear();capture=true;settled=0;dark=0;hitY=Double.NaN;}
    public static void beginHit(double expectedY) {begin();hitY=expectedY;}
    public static void end() {capture=false;}
    public static void contact(BlockPos owner) {
        error(!DUST.isEmpty(),"actual mining effect created no dust");
        if(DUST.isEmpty())return;
        var dust=DUST.getFirst();
        dust.setPos(owner.getX()+.5,Math.nextDown(owner.getY()+.5),owner.getZ()+.5);
        int sky=(dust.getBrightness(1)>>20)&15;
        System.out.println("PHYSICAL_REAL_SLAB_DUST_CONTACT y="+Math.nextDown(owner.getY()+.5)+" sky="+sky+" expected=15");
        error(sky==15,"mining debris at the soil contact must retain exposed-source light, got "+sky);
    }
    public static void record(BlockDustParticle dust,BlockState material,double y) {
        if(!capture)return;
        DUST.add(dust);
        error(Double.isNaN(hitY) || Math.abs(y-hitY)<1e-5,"slab mining dust has wrong physical height: "+y+" expected="+hitY);
        System.out.println("PHYSICAL_REAL_SLAB_DUST_CREATED material="+material+" y="+y+" light="+dust.getBrightness(1));
    }
    public static void error(boolean valid,String message) {if(!valid)ERRORS.add(message);}
    public static void observe(MinecraftClient client,BlockPos owner) {
        for(var dust:DUST) {
            if(!dust.isAlive())continue;
            var box=dust.getBoundingBox();var center=box.getCenter();double y=box.minY;
            if(Math.abs(y-(owner.getY()+.5))>1e-4 || center.x<owner.getX()+.02 || center.x>owner.getX()+.98
                    || center.z<owner.getZ()+.02 || center.z>owner.getZ()+.98)continue;
            int sky=(dust.getBrightness(1)>>20)&15,expected=client.world.getLightLevel(LightType.SKY,owner.up());
            settled++;if(sky!=expected)dark++;
            System.out.println("PHYSICAL_REAL_SLAB_DUST_SETTLED y="+y+" sky="+sky+" expected="+expected);
        }
    }
    public static void verify() {
        error(!DUST.isEmpty(),"actual slab break created no block dust");
        error(dark==0,"actual settled slab dust had "+dark+" dark samples of "+settled);
        if(!ERRORS.isEmpty())throw new AssertionError(String.join("; ",ERRORS));
        System.out.println("PHYSICAL_REAL_SLAB_FEEDBACK_PASS dust="+DUST.size()+" settled_samples="+settled);
        DUST.clear();
    }
}
