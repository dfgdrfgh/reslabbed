package com.slabbed.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * The /slabrig drop-capture mixin is cataloged against the 26.3 painting drop signature; the rig
 * config is registered on every build and this plugin applies the mixin only on 26.3 and newer. The
 * withheld mixin is the justification for the mixin-target scan's skip of it on 26.2. Dev-only: the
 * class is excluded from the release jar with the mixin it gates.
 */
public final class PaintingRigDropCaptureMixinPlugin implements IMixinConfigPlugin {
    static final String RIG_MIXIN = "com.slabbed.mixin.PaintingRigDropCaptureMixin";

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
        return !RIG_MIXIN.equals(mixinClassName) || modernMinecraft;
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
