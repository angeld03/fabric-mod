package com.wildbound.test;

import com.wildbound.progression.ExperienceCurve;
import com.wildbound.progression.LevelProgressionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceCurveTest {
    @Test
    void experienceRequirementIncreasesAcrossTheWholeCharacterJourney() {
        long previous = 0L;
        for (int level = 1; level < 90; level++) {
            long required = ExperienceCurve.required(level);
            assertTrue(required >= 20L, "minimum XP at level " + level);
            assertTrue(required > previous, "curve must not plateau before level cap at " + level);
            previous = required;
        }
        assertTrue(ExperienceCurve.required(89) <= 50_000_000L);
    }

    @Test
    void totalExperienceIsMonotonicAndStartsAtZero() {
        assertEquals(0L, ExperienceCurve.totalToReach(1));
        assertTrue(ExperienceCurve.totalToReach(50) > ExperienceCurve.totalToReach(25));
    }

    @Test
    void levelRewardsAggregateEveryLevelWithoutSkippingMilestones() {
        var result = LevelProgressionService.rewards(1, 7, 10_000L);
        assertEquals(6, result.levelsGained());
        assertEquals(12, result.attributePointsGained());
        assertEquals(2, result.skillPointsGained());
        assertEquals(7, result.newLevel());
        assertEquals(10_000L, result.experienceConsumed());
    }

    @Test
    void skillPointsAreGrantedOnlyAtConfiguredMilestones() {
        assertEquals(0, LevelProgressionService.skillPointsForLevel(2));
        assertEquals(1, LevelProgressionService.skillPointsForLevel(3));
        assertEquals(0, LevelProgressionService.skillPointsForLevel(4));
        assertEquals(1, LevelProgressionService.skillPointsForLevel(6));
    }
}
