package com.slabbed.test;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.lang.reflect.Proxy;

/** Observes the vertices handed to the actual entity buffer, only in the test mod. */
public final class CellRenderAudit {
    private static volatile double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
    private static volatile int count;
    public static volatile int minimumCell=Integer.MAX_VALUE;
    public static void begin(int minCell) { minimumCell=minCell;count=0;minY=Double.POSITIVE_INFINITY;maxY=Double.NEGATIVE_INFINITY; }
    public static VertexConsumer watch(VertexConsumer delegate) {
        return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("vertex") && args!=null) {
                double y=Double.NaN;
                if(args.length==3 && args[0] instanceof Float) y=((Float)args[1]).doubleValue();
                if(args.length==4 && args[0] instanceof Matrix4f matrix) {
                    var p=matrix.transformPosition(new Vector3f((float)args[1],(float)args[2],(float)args[3]));y=p.y;
                }
                if(Double.isFinite(y)) {
                    y+=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y;
                    minY=Math.min(minY,y);maxY=Math.max(maxY,y);count++;
                }
            }
            try { var result=method.invoke(delegate,args);return result==delegate?proxy:result; }
            catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
    }
    public static void verify(double top) {
        if(count<4 || Math.abs(maxY-top)>1e-3 || minY<minimumCell-.001 || maxY-minY>2.001)
            throw new AssertionError("Physical renderer buffer mismatch: count="+count+" min="+minY+" max="+maxY+" top="+top);
        System.out.println("PHYSICAL_REAL_VERTEX_BUFFER_PASS vertices="+count+" min="+minY+" max="+maxY);
    }
}
