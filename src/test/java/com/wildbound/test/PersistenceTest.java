package com.wildbound.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.wildbound.core.WildboundConstants;
import com.wildbound.persistence.RpgDataMigration;
import com.wildbound.player.AttributeContainer;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerStatistics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @Test
    void codecRoundTripPreservesNonDefaultProgression() {
        PlayerRpgProfile original = new PlayerRpgProfile(
                PlayerRpgProfile.CURRENT_SCHEMA,
                UUID.randomUUID().toString(),
                37,
                218L,
                13_840L,
                12,
                4,
                new AttributeContainer(18, 22, 11, 29, 14),
                73.25f,
                42.5f,
                List.of("aqua_water_bolt", "aqua_tidal_wave", "inferno_ember_bolt"),
                List.of("emerald_vale", "sunken_reach"),
                List.of("aqua_water_bolt", "inferno_ember_bolt", "", "", ""),
                new PlayerStatistics(101L, 7L, 4L, 3L, 15L, 2L)
        );

        DataResult<JsonElement> encoded = PlayerRpgProfile.CODEC.encodeStart(JsonOps.INSTANCE, original);
        JsonElement json = encoded.result().orElseThrow();
        PlayerRpgProfile decoded = PlayerRpgProfile.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(original, decoded);
    }

    @Test
    void missingFieldsUseSafeDefaults() {
        String json = "{\"uuid\":\"00000000-0000-0000-0000-000000000000\"}";
        PlayerRpgProfile profile = PlayerRpgProfile.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString(json)
        ).result().orElseThrow();

        assertEquals(1, profile.level());
        assertEquals(5, profile.attributes().strength());
        assertEquals(0L, profile.experience());
        assertEquals(0, profile.skillPoints());
        assertTrue(profile.unlockedRealms().isEmpty());
        assertTrue(profile.unlockedSkills().isEmpty());
        assertEquals(5, profile.radialSkills().size());
    }

    @Test
    void migrationPreservesAllExistingProgression() {
        PlayerRpgProfile old = new PlayerRpgProfile(
                PlayerRpgProfile.CURRENT_SCHEMA - 1,
                UUID.randomUUID().toString(),
                12,
                30L,
                1_000L,
                4,
                2,
                new AttributeContainer(8, 9, 10, 11, 12),
                61f,
                17f,
                List.of("aqua_water_bolt", "aqua_tidal_wave"),
                List.of(WildboundConstants.REALM_EMERALD_VALE, "sunken_reach"),
                List.of("aqua_water_bolt", "aqua_tidal_wave", "", "", ""),
                new PlayerStatistics(21L, 2L, 3L, 4L, 5L, 6L)
        );

        PlayerRpgProfile migrated = RpgDataMigration.migrate(old);

        assertEquals(PlayerRpgProfile.CURRENT_SCHEMA, migrated.schemaVersion());
        assertEquals(old.uuid(), migrated.uuid());
        assertEquals(old.level(), migrated.level());
        assertEquals(old.experience(), migrated.experience());
        assertEquals(old.totalExperience(), migrated.totalExperience());
        assertEquals(old.attributePoints(), migrated.attributePoints());
        assertEquals(old.skillPoints(), migrated.skillPoints());
        assertEquals(old.attributes(), migrated.attributes());
        assertEquals(old.mana(), migrated.mana());
        assertEquals(old.stamina(), migrated.stamina());
        assertEquals(old.unlockedSkills(), migrated.unlockedSkills());
        assertEquals(old.unlockedRealms(), migrated.unlockedRealms());
        assertEquals(old.radialSkills(), migrated.radialSkills());
        assertEquals(old.stats(), migrated.stats());
    }

    @Test
    void validationClampsInvalidValuesAndRemovesLockedRadialSkills() {
        PlayerRpgProfile invalid = new PlayerRpgProfile(
                PlayerRpgProfile.CURRENT_SCHEMA,
                UUID.randomUUID().toString(),
                -10,
                -50L,
                -100L,
                -2,
                -3,
                new AttributeContainer(-1, 5, 5, 5, 5),
                -20f,
                -5f,
                List.of("aqua_water_bolt"),
                List.of(),
                List.of("aqua_water_bolt", "locked_skill", "", "", ""),
                PlayerStatistics.EMPTY
        );

        PlayerRpgProfile validated = RpgDataMigration.validate(invalid);

        assertEquals(1, validated.level());
        assertEquals(0L, validated.experience());
        assertEquals(0L, validated.totalExperience());
        assertEquals(0, validated.attributePoints());
        assertEquals(0, validated.skillPoints());
        assertEquals(0, validated.attributes().strength());
        assertEquals(0f, validated.mana());
        assertEquals(0f, validated.stamina());
        assertEquals("aqua_water_bolt", validated.radialSkills().get(0));
        assertEquals("", validated.radialSkills().get(1));
    }
}
