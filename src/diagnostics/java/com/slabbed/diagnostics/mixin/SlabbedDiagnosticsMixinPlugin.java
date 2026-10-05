package com.slabbed.diagnostics.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Picks the one full-reload hook that exists on the running game: {@code LevelRenderer.allChanged}
 * on 26.3 and newer, {@code LevelRenderer.invalidateCompiledGeometry} on 26.2. Everything else in the
 * diagnostics config always applies. The withheld twin is the justification for the mixin-target
 * scan's skip of that class on the other game version.
 */
public final class SlabbedDiagnosticsMixinPlugin implements IMixinConfigPlugin {
    static final String ALL_CHANGED_MIXIN = "com.slabbed.diagnostics.mixin.LevelRendererAllChangedMixin";
    static final String INVALIDATE_GEOMETRY_MIXIN =
            "com.slabbed.diagnostics.mixin.LevelRendererInvalidateCompiledGeometryMixin";

    private boolean modernMinecraft;

    @Override
    public void onLoad(String mixinPackage) {
        this.modernMinecraft = com.slabbed.compat.MinecraftVersions.AT_LEAST_26_3;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (ALL_CHANGED_MIXIN.equals(mixinClassName)) {
            return modernMinecraft;
        }
        if (INVALIDATE_GEOMETRY_MIXIN.equals(mixinClassName)) {
            return !modernMinecraft;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
