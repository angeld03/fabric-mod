package com.wildbound.realms;

import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Server-authoritative realm unlock and access checks. */
public final class RealmProgressionService {
    private RealmProgressionService() {
    }

    public static boolean isRealmUnlocked(ServerPlayer player, String realmId) {
        return PlayerRpgProvider.getOrCreate(player).hasRealm(realmId);
    }

    public static List<String> getUnlockedRealms(ServerPlayer player) {
        return PlayerRpgProvider.getOrCreate(player).unlockedRealms();
    }

    public static RealmDefinition getRealmDefinition(String realmId) {
        return RealmRegistry.get(realmId).orElse(null);
    }

    public static boolean canAccess(ServerPlayer player, String realmId) {
        RealmDefinition definition = getRealmDefinition(realmId);
        if (definition == null) return false;
        PlayerRpgProfile profile = PlayerRpgProvider.getOrCreate(player);
        if (!profile.hasRealm(realmId)) return false;
        return profile.level() >= definition.minLevel();
    }

    public static boolean unlock(ServerPlayer player, String realmId) {
        RealmDefinition definition = getRealmDefinition(realmId);
        if (definition == null) return false;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        if (profile.hasRealm(realmId)) return false;
        if (profile.level() < definition.minLevel()) return false;
        if (!definition.previousRealm().isEmpty() && !profile.hasRealm(definition.previousRealm())) return false;
        RpgPersistenceManager.save(player, profile.withRealm(realmId));
        return true;
    }
}
