package com.wildbound.player;

import com.wildbound.Wildbound;
import com.wildbound.persistence.RpgPersistenceManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loads and validates the persisted RPG profile as soon as a player joins.
 *
 * The pre-load attachment check is intentionally performed before calling
 * the provider: getOrCreate would otherwise hide the distinction between a
 * restored profile and a newly initialized profile.
 */
public final class PlayerRpgInitializer {
    private PlayerRpgInitializer() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            boolean attachmentPresent = player.hasAttached(RpgAttachments.PROFILE);
            PlayerRpgProfile stored = player.getAttached(RpgAttachments.PROFILE);
            boolean uuidMatches = stored != null && player.getUUID().toString().equals(stored.uuid());
            int storedSchema = stored == null ? -1 : stored.schemaVersion();

            PlayerRpgProfile profile = RpgPersistenceManager.load(player);
            String loadSource = !attachmentPresent
                    ? "missing-or-new"
                    : uuidMatches ? "restored" : "uuid-mismatch-reinitialized";

            Wildbound.LOGGER.info(
                    "RPG profile join: player={} uuid={} source={} schemaBefore={} schemaAfter={} " +
                            "level={} xp={} skillPoints={} attributePoints={} unlockedSkills={} unlockedRealms={}",
                    player.getName().getString(),
                    player.getUUID(),
                    loadSource,
                    storedSchema,
                    profile.schemaVersion(),
                    profile.level(),
                    profile.experience(),
                    profile.skillPoints(),
                    profile.attributePoints(),
                    profile.unlockedSkills().size(),
                    profile.unlockedRealms().size()
            );
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
            if (profile == null) {
                Wildbound.LOGGER.warn(
                        "RPG profile disconnect: no attachment present for player={} uuid={}",
                        player.getName().getString(),
                        player.getUUID()
                );
                return;
            }

            // This is a final in-memory snapshot, not proof that the world's
            // player-data file has already been flushed to disk.
            Wildbound.LOGGER.info(
                    "RPG profile disconnect snapshot: player={} uuid={} schema={} level={} xp={} " +
                            "skillPoints={} attributePoints={} unlockedSkills={} unlockedRealms={}",
                    player.getName().getString(),
                    player.getUUID(),
                    profile.schemaVersion(),
                    profile.level(),
                    profile.experience(),
                    profile.skillPoints(),
                    profile.attributePoints(),
                    profile.unlockedSkills().size(),
                    profile.unlockedRealms().size()
            );
        });
    }
}
