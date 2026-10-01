package com.slabbed.client.model;

import com.slabbed.util.FenceCeilingConnection;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IQuadTransformer;

import java.util.ArrayList;
import java.util.List;

/** Extends only central-post quads; all shared model vertex arrays remain untouched. */
public final class FenceCeilingGeometry {
    private static final float EPS = 1.0e-5f;
    private FenceCeilingGeometry() { }

    public static boolean mayConnect(BlockState state) {
        return state != null && state.getBlock() instanceof FenceBlock;
    }

    public static float postTop(BlockAndTintGetter view, BlockPos pos, BlockState state, float seat) {
        if (!mayConnect(state)) return 1.0f;
        try {
            return (float) FenceCeilingConnection.postTop(view, pos, state, seat);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return 1.0f;
        }
    }

    public static List<BakedQuad> connect(List<BakedQuad> quads, float seat, float top) {
        float extension = top-1.0f;
        ArrayList<BakedQuad> out=new ArrayList<>(quads.size()+4);
        for (BakedQuad quad : quads) {
            int[] src=quad.getVertices();
            boolean post=src.length==4*IQuadTransformer.STRIDE;
            float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;
            for (int i=0;i<4 && post;i++) {
                float x=component(src,i,IQuadTransformer.POSITION);
                float y=component(src,i,IQuadTransformer.POSITION+1);
                float z=component(src,i,IQuadTransformer.POSITION+2);
                post &= x>=0.375f-EPS && x<=0.625f+EPS && z>=0.375f-EPS && z<=0.625f+EPS;
                low=Math.min(low,y);high=Math.max(high,y);
            }
            boolean cap=post && Math.abs(low-1.0f)<=EPS && Math.abs(high-1.0f)<=EPS;
            int[] moved=src.clone();
            for (int i=0;i<4;i++) {
                int offset=i*IQuadTransformer.STRIDE+IQuadTransformer.POSITION+1;
                if (offset<moved.length) {
                    moved[offset]=Float.floatToRawIntBits(Float.intBitsToFloat(src[offset])+seat+(cap?extension:0.0f));
                }
            }
            out.add(rebuilt(quad,moved));
            if (!post || Math.abs(low)>EPS || Math.abs(high-1.0f)>EPS) continue;
            int[] band=src.clone();
            for (int i=0;i<4;i++) {
                int base=i*IQuadTransformer.STRIDE;
                float y=component(src,i,IQuadTransformer.POSITION+1);
                band[base+IQuadTransformer.POSITION+1]=Float.floatToRawIntBits(1.0f+seat+y*extension);
                if (y<=EPS) continue;
                for (int j=0;j<4;j++) {
                    if (component(src,j,IQuadTransformer.POSITION+1)<=EPS
                            && Math.abs(component(src,i,IQuadTransformer.POSITION)-component(src,j,IQuadTransformer.POSITION))<=EPS
                            && Math.abs(component(src,i,IQuadTransformer.POSITION+2)-component(src,j,IQuadTransformer.POSITION+2))<=EPS) {
                        for (int uv=0;uv<2;uv++) {
                            int field=IQuadTransformer.UV0+uv;
                            float a=component(src,j,field),b=component(src,i,field);
                            band[base+field]=Float.floatToRawIntBits(a+(b-a)*extension);
                        }
                        break;
                    }
                }
            }
            out.add(rebuilt(quad,band));
        }
        return out;
    }

    private static float component(int[] vertices,int vertex,int field) {
        return Float.intBitsToFloat(vertices[vertex*IQuadTransformer.STRIDE+field]);
    }

    private static BakedQuad rebuilt(BakedQuad quad,int[] vertices) {
        return new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion());
    }
}
