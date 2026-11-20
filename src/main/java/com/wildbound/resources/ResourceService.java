package com.wildbound.resources;

import com.wildbound.attributes.DerivedStats;
import com.wildbound.attributes.DerivedStatsCalculator;
import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative mana and stamina operations. */
public final class ResourceService {
    private ResourceService() {
    }

    public static float get(ServerPlayer player, ResourceType type) {
        PlayerRpgProfile p = PlayerRpgProvider.getOrCreate(player);
        return type == ResourceType.MANA ? p.mana() : p.stamina();
    }

    public static float getMax(ServerPlayer player, ResourceType type) {
        DerivedStats stats = DerivedStatsCalculator.calculate(player);
        return type == ResourceType.MANA ? stats.maxMana() : stats.maxStamina();
    }

    public static boolean canAfford(ServerPlayer player, ResourceType type, float amount) {
        return amount >= 0f && get(player, type) >= amount;
    }

    public static boolean consume(ServerPlayer player, ResourceType type, float amount) {
        if (amount < 0f) return false;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        float current = type == ResourceType.MANA ? profile.mana() : profile.stamina();
        if (current < amount) return false;
        float value = Math.max(0f, current - amount);
        RpgPersistenceManager.save(player, withResource(profile, type, value));
        return true;
    }

    public static boolean restore(ServerPlayer player, ResourceType type, float amount) {
        if (amount < 0f) return false;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        float current = type == ResourceType.MANA ? profile.mana() : profile.stamina();
        float max = getMax(player, type);
        float value = Math.min(max, current + amount);
        RpgPersistenceManager.save(player, withResource(profile, type, value));
        return true;
    }

    public static void set(ServerPlayer player, ResourceType type, float amount) {
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        float max = getMax(player, type);
        RpgPersistenceManager.save(player, withResource(profile, type, Math.max(0f, Math.min(max, amount))));
    }

    /** Called by the server tick at a controlled interval. */
    public static void regenerate(ServerPlayer player, float seconds) {
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        DerivedStats stats = DerivedStatsCalculator.calculate(profile);
        float mana = Math.min(stats.maxMana(), profile.mana() + stats.manaRegeneration() * seconds);
        float stamina = Math.min(stats.maxStamina(), profile.stamina() + stats.staminaRegeneration() * seconds);
        if (mana == profile.mana() && stamina == profile.stamina()) return;
        RpgPersistenceManager.save(player, new PlayerRpgProfile(
                profile.schemaVersion(), profile.uuid(), profile.level(), profile.experience(),
                profile.totalExperience(), profile.attributePoints(), profile.skillPoints(),
                profile.attributes(), mana, stamina, profile.unlockedSkills(),
                profile.unlockedRealms(), profile.stats()));
    }

    private static PlayerRpgProfile withResource(PlayerRpgProfile p, ResourceType type, float value) {
        return type == ResourceType.MANA
                ? new PlayerRpgProfile(p.schemaVersion(), p.uuid(), p.level(), p.experience(),
                        p.totalExperience(), p.attributePoints(), p.skillPoints(), p.attributes(),
                        value, p.stamina(), p.unlockedSkills(), p.unlockedRealms(), p.stats())
                : new PlayerRpgProfile(p.schemaVersion(), p.uuid(), p.level(), p.experience(),
                        p.totalExperience(), p.attributePoints(), p.skillPoints(), p.attributes(),
                        p.mana(), value, p.unlockedSkills(), p.unlockedRealms(), p.stats());
    }
}
