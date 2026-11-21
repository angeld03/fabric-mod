package com.wildbound.test;

import com.wildbound.progression.ExperienceCurve;
import com.wildbound.progression.LevelProgressionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceCurveTest {
    @Test
    void curveIsIncreasingAndBounded() {
        long previous = 0L;
        for (int level = 1; level <= 90; level++) {
            long needed = ExperienceCurve.required(level);
            assertTrue(needed > 0L);
            assertTrue(needed >= previous || level > 2, "curve must not decrease at level " + level);
            assertTrue(needed <= 5_000_000L);
            previous = needed;
        }
    }

    @Test
    void rewardsAcrossMultipleLevelsAreAccumulated() {
        var result = LevelProgressionService.rewards(1, 4, 100L);
        assertEquals(3, result.levelsGained());
        assertEquals(6, result.attributePointsGained());
        assertEquals(1, result.skillPointsGained());
        assertEquals(4, result.newLevel());
    }

    @Test
    void nonMultiplesOfThreeGiveNoSkillPoints() {
        assertEquals(0, LevelProgressionService.skillPointsForLevel(2));
        assertEquals(1, LevelProgressionService.skillPointsForLevel(3));
    }
}
