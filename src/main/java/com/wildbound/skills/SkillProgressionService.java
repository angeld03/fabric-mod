package com.wildbound.skills;

import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

/** Validates and applies skill unlocks on the server. */
public final class SkillProgressionService {
    private SkillProgressionService() {
    }

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
        int points = profile.skillPoints() - definition.pointCost();
        PlayerRpgProfile updated = new PlayerRpgProfile(
                profile.schemaVersion(), profile.uuid(), profile.level(), profile.experience(),
                profile.totalExperience(), profile.attributePoints(), points, profile.attributes(),
                profile.mana(), profile.stamina(), profile.unlockedSkills(), profile.unlockedRealms(),
                profile.stats()).withSkill(skillId);
        RpgPersistenceManager.save(player, updated);
        return SkillUnlockResult.OK;
    }
}
