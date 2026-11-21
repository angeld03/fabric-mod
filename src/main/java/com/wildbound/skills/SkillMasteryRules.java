package com.wildbound.skills;

import com.wildbound.player.SkillProgress;

/**
 * Balance rules shared by skill executors and activation flow.
 * Kept pure so mastery scaling can be tested without a running Minecraft world.
 */
public final class SkillMasteryRules {
    private SkillMasteryRules() {}

    public static float cooldown(float baseCooldown, int rank) {
        if (!Float.isFinite(baseCooldown) || baseCooldown <= 0f) return 0f;
        int safeRank = Math.max(1, Math.min(SkillProgress.MAX_RANK, rank));
        float reduction = Math.min(0.0675f, (safeRank - 1) * 0.0075f);
        return Math.max(0.25f, baseCooldown * (1f - reduction));
    }

    public static float manaCost(float baseCost, int rank, boolean hasCustomCostCurve) {
        if (!Float.isFinite(baseCost) || baseCost <= 0f || hasCustomCostCurve) return 0f;
        int safeRank = Math.max(1, Math.min(SkillProgress.MAX_RANK, rank));
        float reduction = Math.min(0.09f, (safeRank - 1) * 0.01f);
        return Math.max(0.1f, baseCost * (1f - reduction));
    }

    public static long experienceAward(int requiredPlayerLevel, float chargeLevel) {
        long tierBonus = Math.min(18L, Math.max(0, requiredPlayerLevel) / 3L);
        float safeCharge = Float.isFinite(chargeLevel)
                ? Math.max(0f, Math.min(1f, chargeLevel)) : 0f;
        return 10L + tierBonus + Math.round(safeCharge * 5f);
    }
}
