package com.wildbound.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable, server-authoritative RPG profile for one player. */
public record PlayerRpgProfile(
        int schemaVersion, String uuid, int level, long experience, long totalExperience,
        int attributePoints, int skillPoints, AttributeContainer attributes, float mana, float stamina,
        List<String> unlockedSkills, List<String> unlockedRealms, List<String> radialSkills,
        PlayerStatistics stats, Map<String, SkillProgress> skillProgress
) {
    public static final int CURRENT_SCHEMA = 3;
    private static final Codec<Map<String, SkillProgress>> SKILL_PROGRESS_CODEC =
            Codec.unboundedMap(Codec.STRING, SkillProgress.CODEC);

    public static final Codec<PlayerRpgProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 0).forGetter(PlayerRpgProfile::schemaVersion),
            Codec.STRING.optionalFieldOf("uuid", "").forGetter(PlayerRpgProfile::uuid),
            Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerRpgProfile::level),
            Codec.LONG.optionalFieldOf("experience", 0L).forGetter(PlayerRpgProfile::experience),
            Codec.LONG.optionalFieldOf("total_experience", 0L).forGetter(PlayerRpgProfile::totalExperience),
            Codec.INT.optionalFieldOf("attribute_points", 0).forGetter(PlayerRpgProfile::attributePoints),
            Codec.INT.optionalFieldOf("skill_points", 0).forGetter(PlayerRpgProfile::skillPoints),
            AttributeContainer.CODEC.optionalFieldOf("attributes", AttributeContainer.BASE).forGetter(PlayerRpgProfile::attributes),
            Codec.FLOAT.optionalFieldOf("mana", 0f).forGetter(PlayerRpgProfile::mana),
            Codec.FLOAT.optionalFieldOf("stamina", 0f).forGetter(PlayerRpgProfile::stamina),
            Codec.list(Codec.STRING).optionalFieldOf("unlocked_skills", List.of()).forGetter(PlayerRpgProfile::unlockedSkills),
            Codec.list(Codec.STRING).optionalFieldOf("unlocked_realms", List.of()).forGetter(PlayerRpgProfile::unlockedRealms),
            Codec.list(Codec.STRING).optionalFieldOf("radial_skills", List.of()).forGetter(PlayerRpgProfile::radialSkills),
            PlayerStatistics.CODEC.optionalFieldOf("stats", PlayerStatistics.EMPTY).forGetter(PlayerRpgProfile::stats),
            SKILL_PROGRESS_CODEC.optionalFieldOf("skill_progress", Map.of()).forGetter(PlayerRpgProfile::skillProgress)
    ).apply(instance, PlayerRpgProfile::new));

    /** Keeps existing 14-argument construction sites source-compatible. */
    public PlayerRpgProfile(int schemaVersion, String uuid, int level, long experience, long totalExperience,
                            int attributePoints, int skillPoints, AttributeContainer attributes,
                            float mana, float stamina, List<String> unlockedSkills, List<String> unlockedRealms,
                            List<String> radialSkills, PlayerStatistics stats) {
        this(schemaVersion, uuid, level, experience, totalExperience, attributePoints, skillPoints,
                attributes, mana, stamina, unlockedSkills, unlockedRealms, radialSkills, stats, Map.of());
    }

    public PlayerRpgProfile {
        unlockedSkills = List.copyOf(unlockedSkills);
        unlockedRealms = List.copyOf(unlockedRealms);
        radialSkills = RadialSkillConfig.normalize(radialSkills);
        skillProgress = Map.copyOf(skillProgress);
    }

    public static PlayerRpgProfile create(UUID playerUuid, String startingRealm) {
        return new PlayerRpgProfile(CURRENT_SCHEMA, playerUuid.toString(), 1, 0L, 0L, 0, 1,
                AttributeContainer.BASE, 100f, 20f, List.of(), List.of(startingRealm),
                RadialSkillConfig.emptySlots(), PlayerStatistics.EMPTY, Map.of());
    }

    public static PlayerRpgProfile empty() {
        return create(new UUID(0L, 0L), "");
    }

    public boolean hasSkill(String id) { return unlockedSkills.contains(id); }
    public boolean hasRealm(String id) { return unlockedRealms.contains(id); }

    public SkillProgress getSkillProgress(String id) {
        return skillProgress.getOrDefault(id, SkillProgress.INITIAL);
    }

    public PlayerRpgProfile withSkill(String id) {
        if (hasSkill(id)) return this;
        List<String> skills = new ArrayList<>(unlockedSkills);
        skills.add(id);
        Map<String, SkillProgress> progress = new java.util.HashMap<>(skillProgress);
        progress.putIfAbsent(id, SkillProgress.INITIAL);
        return copyWith(skills, unlockedRealms, radialSkills, progress);
    }

    public PlayerRpgProfile withSkillProgress(String id, SkillProgress value) {
        if (!hasSkill(id) || value == null) return this;
        Map<String, SkillProgress> progress = new java.util.HashMap<>(skillProgress);
        progress.put(id, value);
        return copyWith(unlockedSkills, unlockedRealms, radialSkills, progress);
    }

    public PlayerRpgProfile withRealm(String id) {
        if (hasRealm(id)) return this;
        List<String> realms = new ArrayList<>(unlockedRealms);
        realms.add(id);
        return copyWith(unlockedSkills, realms, radialSkills, skillProgress);
    }

    private PlayerRpgProfile copyWith(List<String> skills, List<String> realms, List<String> radial,
                                      Map<String, SkillProgress> progress) {
        return new PlayerRpgProfile(schemaVersion, uuid, level, experience, totalExperience,
                attributePoints, skillPoints, attributes, mana, stamina, skills, realms, radial, stats, progress);
    }

    public PlayerRpgProfile withRadialSkills(List<String> radial) {
        return copyWith(unlockedSkills, unlockedRealms, RadialSkillConfig.normalize(radial), skillProgress);
    }

    /** Updates current resources without discarding skill mastery or other profile fields. */
    public PlayerRpgProfile withResources(float newMana, float newStamina) {
        return new PlayerRpgProfile(schemaVersion, uuid, level, experience, totalExperience,
                attributePoints, skillPoints, attributes, newMana, newStamina,
                unlockedSkills, unlockedRealms, radialSkills, stats, skillProgress);
    }
}
