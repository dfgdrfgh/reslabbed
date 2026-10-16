package com.slabbed.test.mixin;

import com.slabbed.test.CellClientLifecycleRegression;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockRenderManager.class)
public abstract class CellLifecycleRenderCaptureMixin {
    @ModifyVariable(method="renderBlock",at=@At("HEAD"),argsOnly=true)
    private VertexConsumer slabbed$liveFrame(VertexConsumer target,BlockState state,BlockPos pos,BlockRenderView world,
                                             MatrixStack matrices,VertexConsumer vertices,boolean cull,Random random) {
        return CellClientLifecycleRegression.watches(pos)?new Watched(target):target;
    }
    private record Watched(VertexConsumer target) implements VertexConsumer {
        @Override public VertexConsumer vertex(float x,float y,float z) { CellClientLifecycleRegression.vertex();target.vertex(x,y,z);return this; }
        @Override public VertexConsumer color(int r,int g,int b,int a) { target.color(r,g,b,a);return this; }
        @Override public VertexConsumer texture(float u,float v) { target.texture(u,v);return this; }
        @Override public VertexConsumer overlay(int u,int v) { target.overlay(u,v);return this; }
        @Override public VertexConsumer light(int u,int v) { target.light(u,v);return this; }
        @Override public VertexConsumer normal(float x,float y,float z) { target.normal(x,y,z);return this; }
    }
}
