package com.wildbound.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.wildbound.core.WildboundConstants;
import com.wildbound.persistence.RpgDataMigration;
import com.wildbound.player.PlayerRpgProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @Test
    void codecRoundTripPreservesAllFields() {
        PlayerRpgProfile original = PlayerRpgProfile.create(UUID.randomUUID(), WildboundConstants.REALM_EMERALD_VALE);
        DataResult<JsonElement> encoded = PlayerRpgProfile.CODEC.encodeStart(JsonOps.INSTANCE, original);
        JsonElement json = encoded.result().orElseThrow();
        PlayerRpgProfile decoded = PlayerRpgProfile.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(original, decoded);
    }

    @Test
    void missingFieldsUseSafeDefaults() {
        String json = "{\"uuid\":\"00000000-0000-0000-0000-000000000000\"}";
        PlayerRpgProfile profile = PlayerRpgProfile.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(json)).result().orElseThrow();
        assertEquals(1, profile.level());
        assertEquals(5, profile.attributes().strength());
        assertTrue(profile.unlockedRealms().isEmpty());
    }

    @Test
    void migrationUpgradesSchemaWithoutLosingLevel() {
        PlayerRpgProfile old = new PlayerRpgProfile(0, UUID.randomUUID().toString(), 12,
                30L, 1000L, 4, 2, com.wildbound.player.AttributeContainer.BASE,
                0f, 0f, java.util.List.of(), java.util.List.of(), java.util.List.of(),
                com.wildbound.player.PlayerStatistics.EMPTY);
        PlayerRpgProfile migrated = RpgDataMigration.migrate(old);
        assertEquals(PlayerRpgProfile.CURRENT_SCHEMA, migrated.schemaVersion());
        assertEquals(12, migrated.level());
        assertTrue(migrated.hasRealm(WildboundConstants.REALM_EMERALD_VALE));
    }
}
