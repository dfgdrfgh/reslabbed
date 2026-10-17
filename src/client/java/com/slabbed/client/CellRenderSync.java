package com.slabbed.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import com.slabbed.cell.CellBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import java.util.HashSet;
import java.util.Set;

/** Sections whose native mesh must agree with immediate cell rendering in this frame. */
public final class CellRenderSync {
    private static final Set<Long> changedSections=new HashSet<>();
    private static final Set<BlockPos> pending=new HashSet<>();
    private static final Set<BlockPos> drawn=new HashSet<>();
    private static ClientWorld world;
    private static boolean renderingPending;
    private CellRenderSync() {}
    public static void changed(BlockPos pos) {
        useWorld(MinecraftClient.getInstance().world);
        pending.add(pos.toImmutable());
        for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++)
            changedSections.add(ChunkSectionPos.asLong((pos.getX()+x)>>4,(pos.getY()+y)>>4,(pos.getZ()+z)>>4));
    }
    public static boolean changed(int x,int y,int z) { return changedSections.contains(ChunkSectionPos.asLong(x,y,z)); }
    private static void useWorld(ClientWorld next) {
        if(world==next)return;
        world=next;pending.clear();drawn.clear();changedSections.clear();
    }
    public static void beginFrame() {useWorld(MinecraftClient.getInstance().world);drawn.clear();}
    public static void reset() {useWorld(null);}
    public static void unload(net.minecraft.util.math.ChunkPos chunk) {
        pending.removeIf(pos->(pos.getX()>>4)==chunk.x && (pos.getZ()>>4)==chunk.z);
    }
    /** A newly published carrier can precede its section's compiled block-entity list. */
    public static void renderPending(WorldRenderContext context) {
        if(world==null || context.matrixStack()==null || context.consumers()==null)return;
        var client=MinecraftClient.getInstance();
        var matrices=context.matrixStack();var camera=context.camera().getPos();
        for(var pos:new HashSet<>(pending)) {
            if(!world.isChunkLoaded(pos) || !(world.getBlockEntity(pos) instanceof CellBlockEntity cell)) {
                pending.remove(pos);continue;
            }
            if(cell.renderBounds()==null || (CellRenderer.frustum!=null && !CellRenderer.frustum.isVisible(cell.renderBounds())))continue;
            matrices.push();renderingPending=true;
            try {
                matrices.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
                client.getBlockEntityRenderDispatcher().render(cell,context.tickCounter().getTickDelta(false),matrices,context.consumers());
            } finally {renderingPending=false;matrices.pop();}
        }
    }
    public static void registered(CellBlockEntity cell) {
        if(!renderingPending)pending.remove(cell.getPos());
    }
    public static boolean draw(CellBlockEntity cell) {return drawn.add(cell.getPos());}
    public static void endFrame() { changedSections.clear(); }
}
