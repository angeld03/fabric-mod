package com.wildbound.persistence;

import com.wildbound.core.WildboundConfig;
import com.wildbound.player.AttributeContainer;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RadialSkillConfig;
import com.wildbound.player.SkillProgress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Schema upgrades preserve existing character progression. */
public final class RpgDataMigration {
    private RpgDataMigration() {}

    public static PlayerRpgProfile migrate(PlayerRpgProfile profile) {
        if (profile.schemaVersion() == PlayerRpgProfile.CURRENT_SCHEMA) return validate(profile);
        int level = Math.max(1, Math.min(WildboundConfig.maxLevel, profile.level()));
        AttributeContainer attributes = profile.attributes().validated(WildboundConfig.maxAttribute);
        String uuid = profile.uuid().isEmpty() ? new UUID(0L, 0L).toString() : profile.uuid();
        List<String> realms = profile.unlockedRealms().isEmpty()
                ? List.of(com.wildbound.core.WildboundConstants.REALM_EMERALD_VALE) : profile.unlockedRealms();

        Map<String, SkillProgress> progress = new HashMap<>();
        for (String id : profile.unlockedSkills()) {
            progress.put(id, profile.skillProgress().getOrDefault(id, SkillProgress.INITIAL));
        }

        return validate(new PlayerRpgProfile(PlayerRpgProfile.CURRENT_SCHEMA, uuid, level,
                Math.max(0L, profile.experience()), Math.max(0L, profile.totalExperience()),
                Math.max(0, profile.attributePoints()), Math.max(0, profile.skillPoints()), attributes,
                Math.max(0f, profile.mana()), Math.max(0f, profile.stamina()),
                profile.unlockedSkills(), realms,
                RadialSkillConfig.retainUnlocked(profile.radialSkills(), profile.unlockedSkills()),
                profile.stats().validated(), progress));
    }

    public static PlayerRpgProfile validate(PlayerRpgProfile p) {
        int level = Math.max(1, Math.min(WildboundConfig.maxLevel, p.level()));
        Map<String, SkillProgress> progress = new HashMap<>();
        for (String id : p.unlockedSkills()) {
            progress.put(id, p.skillProgress().getOrDefault(id, SkillProgress.INITIAL));
        }
        return new PlayerRpgProfile(PlayerRpgProfile.CURRENT_SCHEMA, p.uuid(), level,
                Math.max(0L, p.experience()), Math.max(0L, p.totalExperience()),
                Math.max(0, p.attributePoints()), Math.max(0, p.skillPoints()),
                p.attributes().validated(WildboundConfig.maxAttribute),
                Math.max(0f, p.mana()), Math.max(0f, p.stamina()),
                p.unlockedSkills(), p.unlockedRealms(),
                RadialSkillConfig.retainUnlocked(p.radialSkills(), p.unlockedSkills()),
                p.stats().validated(), progress);
    }
}
