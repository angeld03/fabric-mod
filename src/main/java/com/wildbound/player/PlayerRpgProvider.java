package com.wildbound.player;

import com.wildbound.core.WildboundConstants;
import net.minecraft.server.level.ServerPlayer;

/** Server-side access point for player profiles. Never call from the client. */
public final class PlayerRpgProvider {
    private PlayerRpgProvider() {
    }

    public static PlayerRpgProfile get(ServerPlayer player) {
        return getOrCreate(player);
    }

    public static PlayerRpgProfile getOrCreate(ServerPlayer player) {
        PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
        if (profile == null || !player.getUUID().toString().equals(profile.uuid())) {
            profile = PlayerRpgProfile.create(player.getUUID(), WildboundConstants.REALM_EMERALD_VALE);
            player.setAttached(RpgAttachments.PROFILE, profile);
        }
        return profile;
    }

    public static void set(ServerPlayer player, PlayerRpgProfile profile) {
        player.setAttached(RpgAttachments.PROFILE, profile);
    }

    public static void modify(ServerPlayer player, java.util.function.UnaryOperator<PlayerRpgProfile> change) {
        set(player, change.apply(getOrCreate(player)));
    }
}
