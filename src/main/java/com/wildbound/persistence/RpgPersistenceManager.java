package com.wildbound.persistence;

import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Facade for all profile persistence operations. A persistent Fabric player
 * attachment remains the live/synced copy and a world SavedData snapshot is
 * kept as an independent recovery copy. All writes pass through validation.
 */
public final class RpgPersistenceManager {
    private RpgPersistenceManager() {
    }

    public static PlayerRpgProfile load(ServerPlayer player) {
        PlayerRpgProfile stored = PlayerRpgProvider.getOrCreate(player);
        PlayerRpgProfile migrated = RpgDataMigration.migrate(stored);
        if (!migrated.equals(stored)) {
            // Persist schema upgrades immediately instead of returning a
            // migrated value that is discarded when the player leaves.
            PlayerRpgProvider.set(player, migrated);
        }
        return migrated;
    }

    public static void save(ServerPlayer player, PlayerRpgProfile profile) {
        PlayerRpgProvider.set(player, RpgDataMigration.validate(profile));
    }
}
