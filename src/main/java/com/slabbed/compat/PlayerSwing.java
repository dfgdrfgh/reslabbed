package com.slabbed.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Finishes a server-side block use the way the running game version's own use-item path does:
 * consume one item unless the player is creative, then swing the arm.
 *
 * <p>26.3 swings with the held item's own interact animation, read before the consume can empty a
 * last stack, and resets attack strength ({@code swingAndResetAttackStrength}). 26.2 has neither the
 * animation component nor that method and swings with {@code Player.swing(hand, true)}. The 26.3 code
 * lives in a nested class that is loaded only there, so the 26.2 runtime never links the missing
 * members; the 26.2 call goes through a method handle looked up once by name.
 */
public final class PlayerSwing {

    private PlayerSwing() {
    }

    public static void consumeAndSwing(ServerPlayer player, InteractionHand hand) {
        if (MinecraftVersions.AT_LEAST_26_3) {
            Modern.consumeAndSwing(player, hand);
        } else {
            Legacy.consumeAndSwing(player, hand);
        }
    }

    /** 26.3 and newer, by name: this build's game version may predate both members. */
    static final class Modern {
        private static final MethodHandle INTERACT_ANIMATION;
        private static final MethodHandle SWING_AND_RESET;
        static {
            try {
                Class<?> animation = Class.forName("net.minecraft.world.item.component.SwingAnimation");
                INTERACT_ANIMATION = MethodHandles.publicLookup().findVirtual(ItemStack.class, "getInteractAnimation",
                        MethodType.methodType(animation));
                SWING_AND_RESET = MethodHandles.publicLookup().findVirtual(Player.class, "swingAndResetAttackStrength",
                        MethodType.methodType(void.class, InteractionHand.class, animation, boolean.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("26.3 swing members are missing", e);
            }
        }
        private Modern() {
        }
        static void consumeAndSwing(ServerPlayer player, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            try {
                Object animation = INTERACT_ANIMATION.invoke(held);
                if (!player.isCreative()) {
                    held.consume(1, player);
                }
                SWING_AND_RESET.invoke((Player) player, hand, animation, true);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
    }
    /** 26.2: the two-argument swing, by name, since the 26.3 build has no such method to compile against. */
    static final class Legacy {
        private static final MethodHandle SWING;

        static {
            try {
                SWING = MethodHandles.publicLookup().findVirtual(Player.class, "swing",
                        MethodType.methodType(void.class, InteractionHand.class, boolean.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException("Player.swing(InteractionHand, boolean) is missing", e);
            }
        }

        private Legacy() {
        }

        static void consumeAndSwing(ServerPlayer player, InteractionHand hand) {
            if (!player.isCreative()) {
                player.getItemInHand(hand).consume(1, player);
            }
            try {
                SWING.invokeExact((Player) player, hand, true);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
    }
}
