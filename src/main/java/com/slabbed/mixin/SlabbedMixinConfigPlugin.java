package com.slabbed.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Config plugin for {@code slabbed.mixins.json} and {@code slabbed.client.mixins.json}: the one
 * place a mixin may be withheld at load time because of the layout of a third-party mod. This jar
 * serves Minecraft 1.21.6 through 1.21.8; the crosshair pick lives in {@code GameRenderer.findCrosshairTarget} on
 * every version here, so the only seam is Lithium's:
 *
 * <ul>
 *   <li><b>Lithium's block-collision sweeper.</b> Lithium 0.21 (1.21.11) splits the sweeper into
 *       {@code ...SweeperVoxelShape} and {@code ...SweeperBlockPos}, each with its own
 *       {@code computeNext}; Lithium 0.19 and 0.20 (1.21.9, 1.21.10) have one concrete sweeper.
 *       The split mixins are {@code @Pseudo} and skip themselves when the split classes are
 *       absent. The single-sweeper mixin is withheld whenever the split classes exist, and
 *       otherwise admitted only after a byte probe finds the {@code endOfData} call its
 *       {@code @WrapOperation} will demand inside the base class's own {@code computeNext}.</li>
 * </ul>
 *
 * <p>Invariants: {@code require = 1} stays on every injector, so a mismatch is loud; the probe
 * reads bytes as resources and loads no Minecraft, Mixin or Lithium class; the version test
 * falls back to the newest layout if the loader cannot parse the version string.
 */
public final class SlabbedMixinConfigPlugin implements IMixinConfigPlugin {

    static final String LEGACY_PICK_MIXIN = "com.slabbed.mixin.client.GameRendererPickOffsetRaycastMixin";
    static final String LITHIUM_SINGLE_SWEEPER_MIXIN = "com.slabbed.mixin.LithiumStoredPlacementSingleSweeperMixin";

    static final String LITHIUM_SWEEPER_CLASS =
            "net/caffeinemc/mods/lithium/common/entity/movement/ChunkAwareBlockCollisionSweeper";
    static final String LITHIUM_SPLIT_SWEEPER_CLASS =
            "net/caffeinemc/mods/lithium/common/entity/movement/ChunkAwareBlockCollisionSweeperVoxelShape";
    static final String SWEEPER_ENTRY_METHOD = "computeNext";
    static final String END_OF_DATA = "endOfData";
    static final String END_OF_DATA_DESC = "()Ljava/lang/Object;";

    private static final Logger LOGGER = LoggerFactory.getLogger("slabbed/mixin");

    private boolean lithiumSingleSweeper;

    @Override
    public void onLoad(String mixinPackage) {
        boolean splitPresent = resourcePresent(LITHIUM_SPLIT_SWEEPER_CLASS);
        this.lithiumSingleSweeper = !splitPresent
                && methodCallsEndOfData(LITHIUM_SWEEPER_CLASS, SWEEPER_ENTRY_METHOD);
        LOGGER.info("Slabbed mixin selection: crosshair pick = GameRenderer, Lithium sweeper layout = {}",
                splitPresent ? "split (0.21+)" : (this.lithiumSingleSweeper ? "single" : "absent"));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !withheld(mixinClassName, this.lithiumSingleSweeper);
    }

    /** The whole decision as a pure function, so it can be unit-tested without Mixin. */
    static boolean withheld(String mixinClassName, boolean lithiumSingleSweeper) {
        if (LITHIUM_SINGLE_SWEEPER_MIXIN.equals(mixinClassName)) {
            return !lithiumSingleSweeper;
        }
        return false;
    }

    static boolean minecraftMatches(String range) {
        try {
            Version version = FabricLoader.getInstance().getModContainer("minecraft")
                    .orElseThrow(() -> new IllegalStateException("minecraft mod container absent"))
                    .getMetadata().getVersion();
            return VersionPredicate.parse(range).test(version);
        } catch (VersionParsingException | RuntimeException e) {
            LOGGER.warn("Could not evaluate Minecraft version against {}; assuming the newest layout: {}",
                    range, e.toString());
            return true;
        }
    }

    static boolean resourcePresent(String internalName) {
        return SlabbedMixinConfigPlugin.class.getClassLoader().getResource(internalName + ".class") != null;
    }

    /**
     * Reads a class's ORIGINAL bytes as a resource (never loading it) and answers whether a method
     * named {@code entryName} declared on that class itself invokes {@code endOfData()} on the
     * same class. Absent class, unreadable bytes, or no such call all answer {@code false}, so an
     * unknown layout is withheld from, never admitted and crashed on.
     */
    public static boolean methodCallsEndOfData(String internalName, String entryName) {
        try (InputStream in = SlabbedMixinConfigPlugin.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                return false;
            }
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                if (!entryName.equals(method.name)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode call
                            && internalName.equals(call.owner)
                            && END_OF_DATA.equals(call.name)
                            && END_OF_DATA_DESC.equals(call.desc)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Could not inspect {}: {}", internalName, t.toString());
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
