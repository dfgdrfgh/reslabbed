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
    private static int settled,dark,observations;
    private static double hitY=Double.NaN;
    private static double breakFloor=Double.NaN;
    private static final List<String> ERRORS=new ArrayList<>();
    private SlabFeedbackAudit() {}
    public static void begin() {DUST.clear();capture=true;settled=0;dark=0;observations=0;hitY=Double.NaN;breakFloor=Double.NaN;}
    public static void beginHit(double expectedY) {begin();hitY=expectedY;}
    public static void beginBreak(double supportTop) {begin();breakFloor=supportTop;}
    public static void end() {capture=false;}
    public static boolean hasDust() {return !DUST.isEmpty();}
    public static List<BlockDustParticle> dust() {return List.copyOf(DUST);}
    public static void contact(BlockPos owner) {contact(owner,15);}
    public static void contact(BlockPos owner,int expectedSky) {
        error(!DUST.isEmpty(),"actual mining effect created no dust");
        if(DUST.isEmpty())return;
        var dust=DUST.getFirst();
        dust.setPos(owner.getX()+.5,Math.nextDown(owner.getY()+.5),owner.getZ()+.5);
        int sky=(dust.getBrightness(1)>>20)&15;
        System.out.println("PHYSICAL_REAL_SLAB_DUST_CONTACT y="+Math.nextDown(owner.getY()+.5)+" sky="+sky+" expected="+expectedSky);
        error(sky==expectedSky,"debris at the soil contact must retain source light "+expectedSky+", got "+sky);
    }
    /** Use the emitted particle's own movement path, including a footprint straddling each edge. */
    public static void collision(BlockPos owner,int expectedSky) {
        error(!DUST.isEmpty(),"no emitted debris available for collision check");
        if(DUST.isEmpty())return;
        var dust=DUST.getFirst();
        double top=owner.getY()+.5,minY=Double.POSITIVE_INFINITY;
        int samples=0;
        for(double x:new double[]{.02,.5,.98})for(double z:new double[]{.02,.5,.98}) {
            dust.setPos(owner.getX()+x,top+.2,owner.getZ()+z);
            dust.move(0,-.45,0);
            double y=dust.getBoundingBox().minY;
            minY=Math.min(minY,y);samples++;
            error(Math.abs(y-top)<1e-7,"actual dust penetrated the soil: y="+y+" surface="+top+" x="+x+" z="+z);
            int sky=(dust.getBrightness(1)>>20)&15;
            error(sky==expectedSky,"collision-settled dust sampled dark soil: sky="+sky+" expected="+expectedSky+" y="+y);
        }
        System.out.println("PHYSICAL_REAL_SLAB_DUST_COLLISION samples="+samples+" minimum_y="+minY+" surface="+top+" expected_sky="+expectedSky);
    }
    public static void record(BlockDustParticle dust,BlockState material,double y) {
        if(!capture)return;
        DUST.add(dust);
        error(Double.isNaN(hitY) || Math.abs(y-hitY)<1e-5,"slab mining dust has wrong physical height: "+y+" expected="+hitY);
        error(Double.isNaN(breakFloor) || y>=breakFloor,"slab break dust spawned inside its support: y="+y+" surface="+breakFloor);
        if(DUST.size()==1)System.out.println("PHYSICAL_REAL_SLAB_DUST_CREATED material="+material+" y="+y+" light="+dust.getBrightness(1));
    }
    public static void error(boolean valid,String message) {if(!valid)ERRORS.add(message);}
    public static void observe(MinecraftClient client,BlockPos owner) {
        observations++;
        // The first object is reserved for the explicit contact and movement checks above.
        for(int i=1;i<DUST.size();i++) {
            var dust=DUST.get(i);
            if(i<=2 && observations%5==0)System.out.println("PHYSICAL_REAL_SLAB_DUST_MOTION step="+observations+" particle="+i+" alive="+dust.isAlive()+" box="+dust.getBoundingBox()+" light="+dust.getBrightness(1));
            if(!dust.isAlive())continue;
            var box=dust.getBoundingBox();var center=box.getCenter();double y=box.minY;
            if(center.x<owner.getX()+.02 || center.x>owner.getX()+.98
                    || center.z<owner.getZ()+.02 || center.z>owner.getZ()+.98)continue;
            error(y>=owner.getY()+.5-1e-7 || box.maxY<=owner.getY()-.5,
                    "naturally moving slab dust entered its soil support: box="+box);
            if(Math.abs(y-(owner.getY()+.5))>1e-4)continue;
            int sky=(dust.getBrightness(1)>>20)&15,expected=client.world.getLightLevel(LightType.SKY,owner.up());
            settled++;if(sky!=expected)dark++;
            if(settled<=2 || sky!=expected)System.out.println("PHYSICAL_REAL_SLAB_DUST_SETTLED y="+y+" sky="+sky+" expected="+expected);
        }
    }
    public static void verify() {
        error(!DUST.isEmpty(),"actual slab break created no block dust");
        error(settled>0,"no untouched breaking debris reached the soil surface");
        error(dark==0,"actual settled slab dust had "+dark+" dark samples of "+settled);
        if(!ERRORS.isEmpty())throw new AssertionError(String.join("; ",ERRORS));
        System.out.println("PHYSICAL_REAL_SLAB_FEEDBACK_PASS dust="+DUST.size()+" settled_samples="+settled);
        DUST.clear();
    }
}
