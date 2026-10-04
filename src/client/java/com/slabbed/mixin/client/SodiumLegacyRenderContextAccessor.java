package com.slabbed.mixin.client;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The block a Sodium 0.6/0.7 render context is emitting (the {@code frapi.render} layout those
 * versions ship for 1.21.6 through 1.21.8). Supplies the source block for optional renderer
 * integrations such as the Better Block Entities mesh adapter.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext", remap = false)
public interface SodiumLegacyRenderContextAccessor {
    @Accessor("level") BlockRenderView slabbed$getLevel();
    @Accessor("pos") BlockPos slabbed$getPos();
    @Accessor("state") BlockState slabbed$getState();
}
