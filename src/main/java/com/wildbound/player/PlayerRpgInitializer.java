package com.wildbound.player;

import com.wildbound.Wildbound;
import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.persistence.RpgWorldProfilesData;
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
            RpgWorldProfilesData durableData = RpgWorldProfilesData.get(server);
            PlayerRpgProfile durable = durableData == null ? null
                    : durableData.getProfile(player.getUUID().toString());
            boolean hasDurableCopy = durable != null
                    && player.getUUID().toString().equals(durable.uuid());

            PlayerRpgProfile profile = RpgPersistenceManager.load(player);
            // Mirror any valid attachment restored by Minecraft into the durable
            // world store as well, so both persistence paths begin in agreement.
            RpgPersistenceManager.save(player, profile);
            String loadSource = uuidMatches ? "player-attachment"
                    : hasDurableCopy ? "world-saveddata-recovery" : "new-profile";

            Wildbound.LOGGER.info(
                    "RPG profile join: player={} uuid={} source={} attachmentPresent={} durableCopyBefore={} schemaBefore={} schemaAfter={} " +
                            "level={} xp={} skillPoints={} attributePoints={} unlockedSkills={} unlockedRealms={}",
                    player.getName().getString(),
                    player.getUUID(),
                    loadSource,
                    attachmentPresent,
                    hasDurableCopy,
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

            // Flush the final profile to both the synced attachment and the
            // independently persisted world SavedData copy.
            RpgPersistenceManager.save(player, profile);
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
