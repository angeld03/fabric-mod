package com.wildbound.progression;

/** Detailed outcome of an experience grant. */
public record LevelUpResult(
        int oldLevel,
        int newLevel,
        long experienceConsumed,
        int attributePointsGained,
        int skillPointsGained,
        int levelsGained
) {
    public static final LevelUpResult NONE = new LevelUpResult(0, 0, 0L, 0, 0, 0);

    public boolean leveledUp() {
        return levelsGained > 0;
    }
}
