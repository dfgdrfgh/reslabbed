package com.slabbed.test;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;

/** Records primitive vertices forwarded into the live render buffer by the test mod. */
public final class CellRenderAudit {
    private static double minY,maxY;
    private static int count;
    public static boolean terrainLighting=true;
    public static int minimumCell=Integer.MAX_VALUE;
    public static void begin(int minCell) { minimumCell=minCell;count=0;minY=Double.POSITIVE_INFINITY;maxY=Double.NEGATIVE_INFINITY; }
    public static VertexConsumer watch(VertexConsumer target) { return new AuditedVertices(target); }
    private record AuditedVertices(VertexConsumer target) implements VertexConsumer {
        @Override public VertexConsumer vertex(float x,float y,float z) {
            double worldY=y+MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y;
            minY=Math.min(minY,worldY);maxY=Math.max(maxY,worldY);count++;
            target.vertex(x,y,z);return this;
        }
        @Override public VertexConsumer color(int red,int green,int blue,int alpha) { target.color(red,green,blue,alpha);return this; }
        @Override public VertexConsumer texture(float u,float v) { target.texture(u,v);return this; }
        @Override public VertexConsumer overlay(int u,int v) { target.overlay(u,v);return this; }
        @Override public VertexConsumer light(int u,int v) { target.light(u,v);return this; }
        @Override public VertexConsumer normal(float x,float y,float z) { target.normal(x,y,z);return this; }
    }
    public static void verify(double top) {
        if(count<4 || Math.abs(maxY-top)>1e-3 || minY<minimumCell-.001 || maxY-minY>2.001)
            throw new AssertionError("Physical renderer buffer mismatch: count="+count+" min="+minY+" max="+maxY+" top="+top);
        System.out.println("PHYSICAL_REAL_VERTEX_BUFFER_PASS vertices="+count+" min="+minY+" max="+maxY);
    }
}
