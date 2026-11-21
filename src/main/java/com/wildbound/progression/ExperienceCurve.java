package com.wildbound.progression;

import com.wildbound.core.WildboundConfig;

/** Progressive but bounded experience curve for a 90-level RPG. */
public final class ExperienceCurve {
    private ExperienceCurve() {
    }

    /** Experience required to go from {@code level} to {@code level + 1}. */
    public static long required(int level) {
        if (level < 1) return required(1);
        double value = WildboundConfig.experienceBase
                * Math.pow(level, 1.45)
                * Math.pow(WildboundConfig.experienceGrowth, level * 0.45);
        long result = (long) Math.round(value / 5.0) * 5L;
        return Math.max(20L, Math.min(50_000_000L, result));
    }

    public static long totalToReach(int level) {
        long total = 0L;
        for (int i = 1; i < level; i++) total += required(i);
        return total;
    }
}
