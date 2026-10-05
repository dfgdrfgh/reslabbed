package com.slabbed.client;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.util.Locale;

/**
 * A brief, one-time pre-release chat notice ("Slabbed is in {alpha,beta}...") with a
 * click-to-dismiss link. The channel word is derived from the running mod version — see
 * {@link #preReleaseChannel} — so it can never contradict the build it is shown on.
 *
 * <p>Shown at most once per client session PER WORLD (hopping back into the SAME world in one
 * sitting doesn't repeat it — see {@link BetaNoticeSessionGate}), AND permanently skipped for
 * any specific world/server the player has clicked "don't show this again" for — but a brand
 * new world/server the player hasn't dismissed will still show it, even if they've dismissed it
 * elsewhere. That's a deliberate choice (the maintainer's explicit request): this is a per-world
 * preference, not a single global switch.
 */
public final class BetaNoticeClient {

    private BetaNoticeClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(Commands.literal("slabbed_dismiss_beta_notice")
                        .executes(BetaNoticeClient::runDismiss)));

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            Minecraft client = Minecraft.getInstance();
            // Channel first: a build with no pre-release marker shows nothing, and must not burn
            // the per-world session gate deciding that.
            String channel = preReleaseChannel(runningModVersion());
            if (channel == null) {
                return;
            }
            String worldKey = currentWorldKey(client);
            if (!BetaNoticeSessionGate.shouldShow(worldKey)) {
                return;
            }
            if (client.player == null) {
                return;
            }
            BetaNoticeSessionGate.markShown(worldKey);
            client.player.sendSystemMessage(noticeMessage(channel));
        });
    }

    /**
     * The pre-release channel this build is actually on, read from the mod version, or {@code null}
     * when the version carries no pre-release marker (a stable build warns about nothing).
     *
     * <p><b>Do not re-add a hardcoded channel word to {@link #noticeMessage}.</b> The notice greeted
     * players with "beta" on {@code 0.5.0-alpha.1} builds because the word was a literal while the
     * version moved underneath it; the wording is derived precisely so it cannot drift again.
     */
 public static String preReleaseChannel(String version) {
        if (version == null) {
            return null;
        }
        String lower = version.toLowerCase(Locale.ROOT);
        if (lower.contains("alpha")) {
            return "alpha";
        }
        if (lower.contains("beta")) {
            return "beta";
        }
        return null;
    }

    /** The join notice for a given channel. The channel word is never written literally here. */
   public static Component noticeMessage(String channel) {
        return Component.literal(
                        "Slabbed is in " + channel + " — expect some rough edges while it's being developed. ")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                .append(Component.literal("[Don't show again]")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE)
                        .withStyle(style -> style.withClickEvent(
                                new ClickEvent.RunCommand("/slabbed_dismiss_beta_notice"))));
    }

    /** The running mod version, or {@code null} if the container cannot be resolved. */
    private static String runningModVersion() {
        return com.slabbed.loader.Loader.modVersion("slabbed").orElse(null);
    }

    private static int runDismiss(CommandContext<CommandSourceStack> ctx) {
        Minecraft client = Minecraft.getInstance();
        String worldKey = currentWorldKey(client);
        boolean persisted = BetaNoticeDismissedWorlds.dismiss(worldKey);
        Component feedback = dismissFeedbackMessage(persisted);
        ctx.getSource().sendSuccess(() -> feedback, false);
        return 1;
    }

    /**
     * The chat feedback shown after the player clicks "Don't show again". Must never claim the
     * preference was saved unless it actually was: {@code persisted} is {@code false} when
     * {@link #currentWorldKey} couldn't identify this context (e.g. an unresolvable Realms world),
     * in which case {@link BetaNoticeDismissedWorlds#dismiss} is a documented no-op and the notice
     * WILL reappear next session — telling the player otherwise would be a false success message.
     */
   public static Component dismissFeedbackMessage(boolean persisted) {
        if (persisted) {
            return Component.literal("Won't show the beta notice again for this world.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        return Component.literal(
                        "Couldn't identify this world, so that preference couldn't be saved — "
                                + "the beta notice may show again next time.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    /**
     * A stable identity for "this world" — the save folder for singleplayer (unique; Minecraft
     * de-dupes save-folder names on its own), the server address for multiplayer. Returns
     * {@code null} for a context this can't identify (e.g. a Realms world without a resolvable
     * address) — a null key is never dismissible and never considered dismissed, so the notice
     * simply behaves as session-only there instead of persisting incorrectly.
     */
    private static String currentWorldKey(Minecraft client) {
        if (client == null) {
            return null;
        }
        ServerData serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            return "server:" + serverData.ip;
        }
        if (client.isLocalServer() && client.getSingleplayerServer() != null) {
            try {
                return "singleplayer:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize();
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
