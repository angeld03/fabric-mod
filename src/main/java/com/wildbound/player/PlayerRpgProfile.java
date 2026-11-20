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
        PlayerStatistics stats
) {
    public static final int CURRENT_SCHEMA = 1;

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
            PlayerStatistics.CODEC.optionalFieldOf("stats", PlayerStatistics.EMPTY).forGetter(PlayerRpgProfile::stats)
    ).apply(instance, PlayerRpgProfile::new));

    public PlayerRpgProfile {
        unlockedSkills = List.copyOf(unlockedSkills);
        unlockedRealms = List.copyOf(unlockedRealms);
    }

    public static PlayerRpgProfile create(UUID playerUuid, String startingRealm) {
        return new PlayerRpgProfile(CURRENT_SCHEMA, playerUuid.toString(), 1,
                0L, 0L, 0, 0, AttributeContainer.BASE, 0f, 0f,
                List.of(), List.of(startingRealm), PlayerStatistics.EMPTY);
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
        return copyWith(list, unlockedRealms);
    }

    public PlayerRpgProfile withRealm(String id) {
        if (hasRealm(id)) return this;
        List<String> list = new ArrayList<>(unlockedRealms);
        list.add(id);
        return copyWith(unlockedSkills, list);
    }

    private PlayerRpgProfile copyWith(List<String> skills, List<String> realms) {
        return new PlayerRpgProfile(schemaVersion, uuid, level, experience, totalExperience,
                attributePoints, skillPoints, attributes, mana, stamina, skills, realms, stats);
    }
}
