package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class NativeBlockEntityRenderMixin {
    @WrapMethod(method="render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V")
    private <E extends BlockEntity> void slabbed$physicalNativeRender(E entity,float delta,MatrixStack matrices,VertexConsumerProvider vertices,Operation<Void> original) {
        double offset=entity.hasWorld()?NativeSeats.offset(entity.getWorld(),entity.getPos()):0;
        if(entity instanceof net.minecraft.block.entity.PistonBlockEntity piston) {
            com.slabbed.nativeblock.NativePistons.capture(piston);
            var half=((com.slabbed.nativeblock.NativePistonPose)piston).slabbed$halfOffset();
            if(half!=null)offset=half*.5;
        }
        if(offset==0) {original.call(entity,delta,matrices,vertices);return;}
        matrices.push();
        try {matrices.translate(0,offset,0);original.call(entity,delta,matrices,vertices);}
        finally {matrices.pop();}
    }
}
