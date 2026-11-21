package com.wildbound.resources;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/** Regenerates RPG resources on the server once per second, never per render frame. */
public final class ResourceRegeneration {
    private static int tickCounter = 0;

    private ResourceRegeneration() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < 20) return;
            tickCounter = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ResourceService.regenerate(player, 1f);
            }
        });
    }
}
