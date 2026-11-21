package com.wildbound.attributes;

/** Single source of truth for all stats derived from attributes. */
public record DerivedStats(
        float maxHealth,
        float maxMana,
        float maxStamina,
        float meleePower,
        float spellPower,
        float physicalDefense,
        float magicDefense,
        float criticalChance,
        float criticalDamage,
        float movementSpeedBonus,
        float dodgeBonus,
        float manaRegeneration,
        float staminaRegeneration
) {
    public static final DerivedStats BASE = new DerivedStats(
            20f, 100f, 20f, 5f, 5f, 2f, 2f, 0.05f, 1.5f, 0f, 0f, 5f, 4f);
}
