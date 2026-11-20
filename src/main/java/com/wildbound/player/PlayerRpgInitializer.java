package com.wildbound.player;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Creates and validates the profile as soon as a player joins. */
public final class PlayerRpgInitializer {
    private PlayerRpgInitializer() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            PlayerRpgProvider.getOrCreate(player);
        });
    }
}
