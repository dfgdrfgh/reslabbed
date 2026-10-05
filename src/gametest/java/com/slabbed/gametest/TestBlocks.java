package com.slabbed.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registry entries the test content needs. NeoForge creates and registers blocks and items only
 * inside the register event (a block constructed outside it fails with "registry is already
 * frozen"), so a test declares a lazy holder with its id and constructor, and the test mod
 * constructs and registers it when the event fires. The first holder for an id wins (several test
 * classes share one shim id, as on the Fabric lines); later holders resolve to that instance.
 */
public final class TestBlocks {
    /** A registry entry that exists once the register event has run. */
    public static final class Lazy<T> implements Supplier<T> {
        private final Identifier id;
        private final Supplier<? extends T> constructor;
        private T value;

        private Lazy(Identifier id, Supplier<? extends T> constructor) {
            this.id = id;
            this.constructor = constructor;
        }

        public Identifier id() {
            return id;
        }

        @Override
        public T get() {
            if (value == null) {
                throw new IllegalStateException("test registry entry " + id + " is not registered yet");
            }
            return value;
        }
    }

    private static final List<Lazy<? extends Block>> BLOCKS = new ArrayList<>();
    private static final List<Lazy<? extends Item>> ITEMS = new ArrayList<>();

    private TestBlocks() {
    }

    public static synchronized <T extends Block> Lazy<T> block(Identifier id, Supplier<T> constructor) {
        Lazy<T> lazy = new Lazy<>(id, constructor);
        BLOCKS.add(lazy);
        return lazy;
    }

    public static synchronized <T extends Item> Lazy<T> item(Identifier id, Supplier<T> constructor) {
        Lazy<T> lazy = new Lazy<>(id, constructor);
        ITEMS.add(lazy);
        return lazy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static synchronized void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            java.util.Map<Identifier, Block> first = new java.util.LinkedHashMap<>();
            for (Lazy lazy : BLOCKS) {
                Block existing = first.get(lazy.id);
                if (existing == null) {
                    existing = (Block) lazy.constructor.get();
                    first.put(lazy.id, existing);
                    helper.register(lazy.id, existing);
                }
                lazy.value = existing;
            }
            com.mojang.logging.LogUtils.getLogger().info("[slabbed_gametest] registered {} test blocks: {}", first.size(), first.keySet());
        });
        event.register(Registries.ITEM, helper -> {
            java.util.Map<Identifier, Item> first = new java.util.LinkedHashMap<>();
            for (Lazy lazy : ITEMS) {
                Item existing = first.get(lazy.id);
                if (existing == null) {
                    existing = (Item) lazy.constructor.get();
                    first.put(lazy.id, existing);
                    helper.register(lazy.id, existing);
                }
                lazy.value = existing;
            }
            com.mojang.logging.LogUtils.getLogger().info("[slabbed_gametest] registered {} test items: {}", first.size(), first.keySet());
        });
    }
}
