package com.wildbound.progression;

import com.wildbound.api.ExperienceSource;
import com.wildbound.api.RpgProgressionEvents;
import com.wildbound.core.WildboundConfig;
import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative experience and leveling service. */
public final class ExperienceService {
    private ExperienceService() {
    }

    public static long getExperience(ServerPlayer player) {
        return PlayerRpgProvider.getOrCreate(player).experience();
    }

    public static long getTotalExperience(ServerPlayer player) {
        return PlayerRpgProvider.getOrCreate(player).totalExperience();
    }

    public static long getExperienceRequired(ServerPlayer player) {
        PlayerRpgProfile profile = PlayerRpgProvider.getOrCreate(player);
        if (profile.level() >= WildboundConfig.maxLevel) return 0L;
        return ExperienceCurve.required(profile.level());
    }

    public static float getExperienceProgress(ServerPlayer player) {
        long required = getExperienceRequired(player);
        if (required <= 0L) return 1f;
        return Math.min(1f, (float) getExperience(player) / (float) required);
    }

    /** Grants experience and applies every level reward in one atomic update. */
    public static LevelUpResult grantExperience(ServerPlayer player, long amount, ExperienceSource source) {
        if (amount <= 0L) return LevelUpResult.NONE;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        long gained = Math.min(amount, Long.MAX_VALUE - profile.experience() - 1);
        if (gained <= 0L) return LevelUpResult.NONE;

        long experience = profile.experience() + gained;
        long total = profile.totalExperience() + gained;
        int level = profile.level();
        long consumed = 0L;

        while (level < WildboundConfig.maxLevel) {
            long needed = ExperienceCurve.required(level);
            if (experience < needed) break;
            experience -= needed;
            consumed += needed;
            level++;
        }
        if (level >= WildboundConfig.maxLevel) {
            // At cap: experience is kept but produces no further levels.
            experience = Math.min(experience, ExperienceCurve.required(WildboundConfig.maxLevel));
        }

        int attributePoints = profile.attributePoints();
        int skillPoints = profile.skillPoints();
        LevelUpResult result = LevelUpResult.NONE;
        if (level > profile.level()) {
            result = LevelProgressionService.rewards(profile.level(), level, consumed);
            attributePoints += result.attributePointsGained();
            skillPoints += result.skillPointsGained();
        }

        PlayerRpgProfile updated = new PlayerRpgProfile(
                profile.schemaVersion(), profile.uuid(), level, experience, total,
                attributePoints, skillPoints, profile.attributes(), profile.mana(), profile.stamina(),
                profile.unlockedSkills(), profile.unlockedRealms(), profile.stats());
        RpgPersistenceManager.save(player, updated);

        RpgProgressionEvents.EXPERIENCE_GAINED.invoker().onExperienceGained(player, gained, source);
        if (result.leveledUp()) {
            RpgProgressionEvents.LEVEL_UP.invoker().onLevelUp(player, result);
        }
        return result;
    }

    public static void removeExperience(ServerPlayer player, long amount) {
        if (amount <= 0L) return;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        long value = Math.max(0L, profile.experience() - amount);
        RpgPersistenceManager.save(player, copyWithExperience(profile, value, profile.totalExperience()));
    }

    public static void setExperience(ServerPlayer player, long amount) {
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        long value = Math.max(0L, amount);
        RpgPersistenceManager.save(player, copyWithExperience(profile, value, value));
    }

    private static PlayerRpgProfile copyWithExperience(PlayerRpgProfile p, long experience, long total) {
        return new PlayerRpgProfile(p.schemaVersion(), p.uuid(), p.level(), experience, total,
                p.attributePoints(), p.skillPoints(), p.attributes(), p.mana(), p.stamina(),
                p.unlockedSkills(), p.unlockedRealms(), p.stats());
    }
}
