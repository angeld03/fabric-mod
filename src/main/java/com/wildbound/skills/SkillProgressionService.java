package com.wildbound.skills;

import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import com.wildbound.player.SkillProgress;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative unlock and mastery progression for skills. */
public final class SkillProgressionService {
    private SkillProgressionService() {}

    public static boolean isUnlocked(ServerPlayer player, String skillId) {
        return PlayerRpgProvider.getOrCreate(player).hasSkill(skillId);
    }

    public static SkillUnlockResult canUnlock(ServerPlayer player, String skillId) {
        SkillDefinition definition = SkillRegistry.get(skillId).orElse(null);
        if (definition == null) return SkillUnlockResult.fail("unknown_skill");
        PlayerRpgProfile profile = PlayerRpgProvider.getOrCreate(player);
        if (profile.hasSkill(skillId)) return SkillUnlockResult.fail("already_unlocked");
        if (profile.level() < definition.requiredLevel()) return SkillUnlockResult.fail("level_too_low");
        if (profile.skillPoints() < definition.pointCost()) return SkillUnlockResult.fail("not_enough_points");
        if (!definition.originRealm().isEmpty() && !profile.hasRealm(definition.originRealm())) {
            return SkillUnlockResult.fail("realm_locked");
        }
        for (String prerequisite : definition.prerequisites()) {
            if (!profile.hasSkill(prerequisite)) return SkillUnlockResult.fail("missing_prerequisite");
        }
        return SkillUnlockResult.OK;
    }

    public static SkillUnlockResult unlock(ServerPlayer player, String skillId) {
        SkillUnlockResult check = canUnlock(player, skillId);
        if (!check.success()) return check;
        SkillDefinition definition = SkillRegistry.get(skillId).orElseThrow();
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        PlayerRpgProfile updated = new PlayerRpgProfile(profile.schemaVersion(), profile.uuid(),
                profile.level(), profile.experience(), profile.totalExperience(), profile.attributePoints(),
                profile.skillPoints() - definition.pointCost(), profile.attributes(), profile.mana(),
                profile.stamina(), profile.unlockedSkills(), profile.unlockedRealms(),
                profile.radialSkills(), profile.stats(), profile.skillProgress()).withSkill(skillId);
        RpgPersistenceManager.save(player, updated);
        return SkillUnlockResult.OK;
    }

    /** Grants XP only after a server-side executor completed successfully. */
    public static SkillProgressResult grantUseExperience(ServerPlayer player, String skillId, long amount) {
        if (amount <= 0) return SkillProgressResult.NO_CHANGE;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        if (!profile.hasSkill(skillId)) return SkillProgressResult.NO_CHANGE;
        SkillProgress before = profile.getSkillProgress(skillId);
        SkillProgress after = before.addExperience(amount);

        // Water Bolt mastery milestones unlock permanent, server-owned
        // specializations. The upgrades are granted once and survive relogs.
        if ("aqua_water_bolt".equals(skillId)) {
            if (after.rank() >= 3) after = after.withUpgrade("hydrostatic_core");
            if (after.rank() >= 6) after = after.withUpgrade("frostbite_current");
            if (after.rank() >= 9) after = after.withUpgrade("tidal_piercer");
        }
        if (after.equals(before)) return SkillProgressResult.NO_CHANGE;
        RpgPersistenceManager.save(player, profile.withSkillProgress(skillId, after));
        return new SkillProgressResult(true, before.rank(), after.rank(), amount,
                after.experience(), after.experienceRequired());
    }

    public record SkillProgressResult(boolean changed, int previousRank, int rank,
                                      long gainedExperience, long experience, long requiredExperience) {
        public static final SkillProgressResult NO_CHANGE =
                new SkillProgressResult(false, 1, 1, 0L, 0L, 0L);
    }
}
