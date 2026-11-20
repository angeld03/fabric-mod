package com.wildbound.persistence;

import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Facade for all profile persistence operations. Actual saving is handled by
 * Fabric Data Attachments; this class enforces migration and validation on
 * every read and write.
 */
public final class RpgPersistenceManager {
    private RpgPersistenceManager() {
    }

    public static PlayerRpgProfile load(ServerPlayer player) {
        return RpgDataMigration.migrate(PlayerRpgProvider.getOrCreate(player));
    }

    public static void save(ServerPlayer player, PlayerRpgProfile profile) {
        PlayerRpgProvider.set(player, RpgDataMigration.validate(profile));
    }
}
