package com.slabbed.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.HashSet;
import java.util.Set;

/** Sections whose native mesh must agree with immediate cell rendering in this frame. */
public final class CellRenderSync {
    private static final Set<Long> changedSections=new HashSet<>();
    private CellRenderSync() {}
    public static void changed(BlockPos pos) {
        for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++)
            changedSections.add(ChunkSectionPos.asLong((pos.getX()+x)>>4,(pos.getY()+y)>>4,(pos.getZ()+z)>>4));
    }
    public static boolean changed(int x,int y,int z) { return changedSections.contains(ChunkSectionPos.asLong(x,y,z)); }
    public static void endFrame() { changedSections.clear(); }
}
