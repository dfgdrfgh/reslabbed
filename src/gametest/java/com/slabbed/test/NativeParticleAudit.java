package com.slabbed.test;

import net.minecraft.particle.ParticleEffect;

import java.util.ArrayList;
import java.util.List;

/** Observes vanilla particle requests; capture never changes an emission. */
public final class NativeParticleAudit {
    public record Emission(String route,ParticleEffect effect,double x,double y,double z,
                           int count,double a,double b,double c,double speed) {}
    private static final ThreadLocal<List<Emission>> ACTIVE=new ThreadLocal<>();
    private NativeParticleAudit() {}
    public static List<Emission> capture(Runnable action) {
        if(ACTIVE.get()!=null)throw new IllegalStateException("nested particle capture");
        var emissions=new ArrayList<Emission>();ACTIVE.set(emissions);
        try {action.run();return List.copyOf(emissions);}
        finally {ACTIVE.remove();}
    }
    public static void record(String route,ParticleEffect effect,double x,double y,double z,
                              int count,double a,double b,double c,double speed) {
        var emissions=ACTIVE.get();
        if(emissions!=null)emissions.add(new Emission(route,effect,x,y,z,count,a,b,c,speed));
    }
}
