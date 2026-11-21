package com.wildbound.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The complete RPG profile of one player. Immutable on purpose: services always
 * produce a new profile and store it back through the attachment, which keeps
 * the server as the single source of truth.
 */
public record PlayerRpgProfile(
        int schemaVersion,
        String uuid,
        int level,
        long experience,
        long totalExperience,
        int attributePoints,
        int skillPoints,
        AttributeContainer attributes,
        float mana,
        float stamina,
        List<String> unlockedSkills,
        List<String> unlockedRealms,
        List<String> radialSkills,
        PlayerStatistics stats
) {
    public static final int CURRENT_SCHEMA = 2;

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
            PlayerStatistics.CODEC.optionalFieldOf("stats", PlayerStatistics.EMPTY).forGetter(PlayerRpgProfile::stats)
    ).apply(instance, PlayerRpgProfile::new));

    public PlayerRpgProfile {
        unlockedSkills = List.copyOf(unlockedSkills);
        unlockedRealms = List.copyOf(unlockedRealms);
        radialSkills = RadialSkillConfig.normalize(radialSkills);
    }

    public static PlayerRpgProfile create(UUID playerUuid, String startingRealm) {
        // One starting point so a brand new character can open the skill menu
        // and unlock its first tier-1 skill right away.
        return new PlayerRpgProfile(CURRENT_SCHEMA, playerUuid.toString(), 1,
                0L, 0L, 0, 1, AttributeContainer.BASE, 100f, 20f,
                List.of(), List.of(startingRealm), RadialSkillConfig.emptySlots(), PlayerStatistics.EMPTY);
    }

    /** Empty profile used only as an attachment initializer before the player joins. */
    public static PlayerRpgProfile empty() {
        return create(new UUID(0L, 0L), "");
    }

    public boolean hasSkill(String id) {
        return unlockedSkills.contains(id);
    }

    public boolean hasRealm(String id) {
        return unlockedRealms.contains(id);
    }

    public PlayerRpgProfile withSkill(String id) {
        if (hasSkill(id)) return this;
        List<String> list = new ArrayList<>(unlockedSkills);
        list.add(id);
        return copyWith(list, unlockedRealms, radialSkills);
    }

    public PlayerRpgProfile withRealm(String id) {
        if (hasRealm(id)) return this;
        List<String> list = new ArrayList<>(unlockedRealms);
        list.add(id);
        return copyWith(unlockedSkills, list, radialSkills);
    }

    private PlayerRpgProfile copyWith(List<String> skills, List<String> realms, List<String> radial) {
        return new PlayerRpgProfile(schemaVersion, uuid, level, experience, totalExperience,
                attributePoints, skillPoints, attributes, mana, stamina, skills, realms, radial, stats);
    }

    public PlayerRpgProfile withRadialSkills(List<String> radial) {
        return copyWith(unlockedSkills, unlockedRealms, RadialSkillConfig.normalize(radial));
    }
}
