package com.wildbound.attributes;

import com.wildbound.core.WildboundConfig;
import com.wildbound.player.AttributeContainer;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

/** All attribute formulas live here and nowhere else. */
public final class DerivedStatsCalculator {
    private DerivedStatsCalculator() {
    }

    public static DerivedStats calculate(ServerPlayer player) {
        return calculate(PlayerRpgProvider.getOrCreate(player).attributes());
    }

    public static DerivedStats calculate(PlayerRpgProfile profile) {
        return calculate(profile.attributes());
    }

    public static DerivedStats calculate(AttributeContainer a) {
        float str = a.strength();
        float vit = a.vitality();
        float agi = a.agility();
        float intel = a.intelligence();
        float spi = a.spirit();

        return new DerivedStats(
                20f + (vit - WildboundConfig.baseAttribute) * WildboundConfig.healthPerVitality * 2f,
                20f + (intel - WildboundConfig.baseAttribute) * WildboundConfig.maxManaPerIntelligence,
                20f + (agi - WildboundConfig.baseAttribute) * WildboundConfig.maxStaminaPerAgility,
                5f + (str - WildboundConfig.baseAttribute) * WildboundConfig.meleePerStrength,
                5f + (intel - WildboundConfig.baseAttribute) * WildboundConfig.spellPerIntelligence,
                2f + (vit - WildboundConfig.baseAttribute) * 0.4f,
                2f + (spi - WildboundConfig.baseAttribute) * 0.4f,
                Math.min(0.75f, 0.05f + (agi - WildboundConfig.baseAttribute) * 0.008f),
                1.5f + (str - WildboundConfig.baseAttribute) * 0.01f,
                Math.min(0.5f, (agi - WildboundConfig.baseAttribute) * 0.005f),
                Math.min(0.4f, (agi - WildboundConfig.baseAttribute) * 0.004f),
                WildboundConfig.manaRegenPerSecond + (spi - WildboundConfig.baseAttribute) * 0.08f,
                WildboundConfig.staminaRegenPerSecond + (agi - WildboundConfig.baseAttribute) * 0.1f
        );
    }
}
