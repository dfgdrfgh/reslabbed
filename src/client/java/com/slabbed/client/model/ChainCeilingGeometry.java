package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.ChainBridgeTextureVariant;
import com.slabbed.util.SlabSupport;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Alternate baked geometry for a Y-axis chain hanging under a slab CEILING (a TOP or DOUBLE slab
 * directly above it): the chain is extended by half a block so the column connects continuously
 * to the slab's lowered underside. One standalone model per chain texture (iron plus the four
 * copper weather states), selected from the chain block's registry id, so each chain type bridges
 * with its own texture. Behaviour identical to the Fabric 26.x lines; the model registration goes
 * through NeoForge's standalone-model event.
 */
public final class ChainCeilingGeometry {
    private static final Map<ChainBridgeTextureVariant, StandaloneModelKey<BlockStateModel>> KEYS =
            new EnumMap<>(ChainBridgeTextureVariant.class);
    private static final Map<ChainBridgeTextureVariant, Identifier> MODEL_IDS =
            new EnumMap<>(ChainBridgeTextureVariant.class);

    static {
        for (ChainBridgeTextureVariant variant : ChainBridgeTextureVariant.values()) {
            String path = variant.modelPath();
            MODEL_IDS.put(variant, Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "block/" + path));
            KEYS.put(variant, new StandaloneModelKey<>(() -> "slabbed:" + path));
        }
    }

    public static final Identifier MODEL_ID = MODEL_IDS.get(ChainBridgeTextureVariant.IRON);

    private ChainCeilingGeometry() {
    }

    /** Registers the bridge models (mod bus). */
    public static void registerStandalone(ModelEvent.RegisterStandalone event) {
        for (ChainBridgeTextureVariant variant : ChainBridgeTextureVariant.values()) {
            event.register(KEYS.get(variant),
                    SimpleUnbakedStandaloneModel.blockStateModel(MODEL_IDS.get(variant)));
        }
    }

    public static Identifier modelIdFor(ChainBridgeTextureVariant variant) {
        return MODEL_IDS.get(variant);
    }

    public static ChainBridgeTextureVariant[] variants() {
        return ChainBridgeTextureVariant.values();
    }

    /** True when the frozen chain dy and its ceiling support select the extended bridge route. */
    public static boolean usesAlternateGeometry(BlockGetter world, BlockPos pos, BlockState state, double frozenDy) {
        return SlabSupport.usesCeilingBridgeGeometry(world, pos, state, frozenDy);
    }

    /**
     * Collects the extended chain geometry instead of the wrapped model when this chain hangs under
     * a slab ceiling. Returns true when it handled the collection (caller must then skip the normal
     * path).
     */
    public static boolean collectIfPresent(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                           double frozenDy, RandomSource random,
                                           List<BlockStateModelPart> parts) {
        if (!usesAlternateGeometry(world, pos, state, frozenDy)) {
            return false;
        }
        BlockStateModel alternate = alternateModel(ChainBridgeTextureVariant.forBlock(state));
        if (alternate == null) {
            return false;
        }
        alternate.collectParts(world, pos, state, random, parts);
        return true;
    }

    /** The baked bridge model for a variant, or null before models are baked. Visible for tests. */
    public static BlockStateModel alternateModel(ChainBridgeTextureVariant variant) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getModelManager() == null) {
            return null;
        }
        return client.getModelManager().getStandaloneModel(KEYS.get(variant));
    }
}
