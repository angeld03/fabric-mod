package com.wildbound.player;

import com.wildbound.core.WildboundConstants;
import com.wildbound.persistence.RpgDataMigration;
import com.wildbound.persistence.RpgWorldProfilesData;
import net.minecraft.server.level.ServerPlayer;

/** Server-side access point for player profiles. Never call from the client. */
public final class PlayerRpgProvider {
    private PlayerRpgProvider() {
    }

    public static PlayerRpgProfile get(ServerPlayer player) {
        return getOrCreate(player);
    }

    public static PlayerRpgProfile getOrCreate(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
        if (profile != null && uuid.equals(profile.uuid())) return profile;

        // If the player attachment is absent, malformed or restored with the
        // zero UUID initializer, recover from the world's independent RPG store.
        RpgWorldProfilesData durableData = RpgWorldProfilesData.get(player.getServer());
        PlayerRpgProfile durable = durableData == null ? null : durableData.getProfile(uuid);
        if (durable != null && uuid.equals(durable.uuid())) {
            profile = RpgDataMigration.migrate(durable);
        } else {
            profile = PlayerRpgProfile.create(player.getUUID(), WildboundConstants.REALM_EMERALD_VALE);
        }

        // Restore the live attachment first; set() also refreshes the durable copy.
        set(player, profile);
        return profile;
    }

    /**
     * Writes both the live synced attachment and an independent world SavedData
     * snapshot. The second copy survives a missing/failed player attachment load.
     */
    public static void set(ServerPlayer player, PlayerRpgProfile profile) {
        if (player == null || profile == null) return;
        if (!player.getUUID().toString().equals(profile.uuid())) {
            throw new IllegalArgumentException("RPG profile UUID does not match its player");
        }
        player.setAttached(RpgAttachments.PROFILE, profile);
        RpgWorldProfilesData durableData = RpgWorldProfilesData.get(player.getServer());
        if (durableData != null) durableData.putProfile(profile.uuid(), profile);
    }

    public static void modify(ServerPlayer player, java.util.function.UnaryOperator<PlayerRpgProfile> change) {
        set(player, change.apply(getOrCreate(player)));
    }
}
