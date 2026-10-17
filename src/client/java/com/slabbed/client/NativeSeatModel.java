package com.slabbed.client;

import com.slabbed.nativeblock.NativeRules;
import com.slabbed.nativeblock.NativeSeats;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

/** Terrain geometry uses the same frozen seat as the native block entity and menu. */
public final class NativeSeatModel extends ForwardingBakedModel {
    public NativeSeatModel(BakedModel wrapped) {this.wrapped=wrapped;}
    public static boolean workstationModel(Identifier id) {
        return Registries.BLOCK.containsId(id) && NativeRules.accepts(Registries.BLOCK.get(id).getDefaultState());
    }
    @Override public boolean isVanillaAdapter() {return false;}
    @Override public void emitBlockQuads(BlockRenderView view,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context) {
        float offset=(float)NativeSeats.offset(view,pos,state);
        if(offset==0) {super.emitBlockQuads(view,state,pos,random,context);return;}
        context.pushTransform(quad-> {
            quad.cullFace(null);
            for(int n=0;n<4;n++)quad.pos(n,quad.x(n),quad.y(n)+offset,quad.z(n));
            return true;
        });
        try {super.emitBlockQuads(view,state,pos,random,context);}
        finally {context.popTransform();}
    }
}
