package com.wildbound.core;

/**
 * Central balance configuration. Values are validated so a malformed setting
 * can never produce an invalid game state. In a later phase this can be backed
 * by a config file without changing any consumer.
 */
public final class WildboundConfig {
    public static int maxLevel = WildboundConstants.MAX_LEVEL;
    public static int baseAttribute = WildboundConstants.BASE_ATTRIBUTE;
    public static int maxAttribute = 100;
    public static int attributePointsPerLevel = 2;
    public static int skillPointsEveryLevels = 3;
    public static double experienceBase = 80.0;
    public static double experienceGrowth = 1.18;
    public static float manaRegenPerSecond = 5.0f;
    public static float staminaRegenPerSecond = 4.0f;
    public static float maxManaPerIntelligence = 3.0f;
    public static float maxStaminaPerAgility = 1.5f;
    public static float healthPerVitality = 0.5f;
    public static float meleePerStrength = 1.2f;
    public static float spellPerIntelligence = 1.4f;

    private WildboundConfig() {
    }

    /** Clamps all values to safe ranges; called during initialization. */
    public static void validate() {
        maxLevel = Math.max(1, Math.min(1000, maxLevel));
        baseAttribute = Math.max(1, Math.min(100, baseAttribute));
        maxAttribute = Math.max(baseAttribute, Math.min(1000, maxAttribute));
        attributePointsPerLevel = Math.max(0, Math.min(100, attributePointsPerLevel));
        skillPointsEveryLevels = Math.max(1, Math.min(100, skillPointsEveryLevels));
        experienceBase = Math.max(1.0, Math.min(1_000_000.0, experienceBase));
        experienceGrowth = Math.max(1.0, Math.min(3.0, experienceGrowth));
        manaRegenPerSecond = Math.max(0f, Math.min(100f, manaRegenPerSecond));
        staminaRegenPerSecond = Math.max(0f, Math.min(100f, staminaRegenPerSecond));
    }
}
