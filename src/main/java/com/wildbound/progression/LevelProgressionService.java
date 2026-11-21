package com.wildbound.progression;

import com.wildbound.core.WildboundConfig;

/** Rules that turn levels into rewards. Pure functions, no side effects. */
public final class LevelProgressionService {
    private LevelProgressionService() {
    }

    public static int attributePointsForLevel(int newLevel) {
        return WildboundConfig.attributePointsPerLevel;
    }

    public static int skillPointsForLevel(int newLevel) {
        return newLevel % WildboundConfig.skillPointsEveryLevels == 0 ? 1 : 0;
    }

    /** Aggregates rewards for every level in (from, to]. */
    public static LevelUpResult rewards(int from, int to, long consumed) {
        int attributePoints = 0;
        int skillPoints = 0;
        for (int level = from + 1; level <= to; level++) {
            attributePoints += attributePointsForLevel(level);
            skillPoints += skillPointsForLevel(level);
        }
        return new LevelUpResult(from, to, consumed, attributePoints, skillPoints, to - from);
    }
}
