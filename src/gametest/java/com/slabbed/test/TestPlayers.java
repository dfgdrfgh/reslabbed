package com.slabbed.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Mock-player helpers that keep the suite's call shape stable across Minecraft versions. */
final class TestPlayers {
    private TestPlayers() {}

    /**
     * A detached survival server player bound to the test level. The player is never placed into the
     * player list, so it does not tick, gain advancements, or receive packets; rows that snapshot the
     * player envelope depend on that stillness.
     */
    static ServerPlayer survivalServerPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "test-mock-player"),
                ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
