package com.slabbed.test.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DebugHud.class)
public interface DebugHudAccess {
    @Accessor("blockHit") void slabbed$blockHit(HitResult hit);
    @Accessor("fluidHit") void slabbed$fluidHit(HitResult hit);
    @Invoker("getRightText") java.util.List<String> slabbed$rightText();
}
