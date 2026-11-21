package com.wildbound.test;

import com.wildbound.skills.SkillMasteryRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillMasteryRulesTest {
    @Test
    void cooldownReductionScalesAndIsCappedAtRankTen() {
        assertEquals(10f, SkillMasteryRules.cooldown(10f, 1), 0.0001f);
        assertEquals(9.325f, SkillMasteryRules.cooldown(10f, 10), 0.0001f);
        assertEquals(SkillMasteryRules.cooldown(10f, 10),
                SkillMasteryRules.cooldown(10f, 99), 0.0001f);
    }

    @Test
    void manaDiscountIsCappedAndCustomCurvesArePreserved() {
        assertEquals(10f, SkillMasteryRules.manaCost(10f, 1, false), 0.0001f);
        assertEquals(9.1f, SkillMasteryRules.manaCost(10f, 10, false), 0.0001f);
        assertEquals(5f, SkillMasteryRules.manaCost(5f, 10, true), 0.0001f);
    }

    @Test
    void masteryExperienceRewardsHigherTiersAndCharge() {
        assertEquals(10L, SkillMasteryRules.experienceAward(1, 0f));
        assertEquals(20L, SkillMasteryRules.experienceAward(30, 0f));
        assertEquals(25L, SkillMasteryRules.experienceAward(30, 1f));
        assertEquals(25L, SkillMasteryRules.experienceAward(100, 2f));
    }

    @Test
    void invalidInputsAreSafelyClamped() {
        assertEquals(0.25f, SkillMasteryRules.cooldown(0.1f, 10), 0.0001f);
        assertEquals(10f, SkillMasteryRules.manaCost(10f, -5, false), 0.0001f);
        assertEquals(10L, SkillMasteryRules.experienceAward(-10, Float.NaN));
        assertEquals(0f, SkillMasteryRules.cooldown(Float.NaN, 10), 0.0001f);
        assertEquals(0f, SkillMasteryRules.manaCost(Float.POSITIVE_INFINITY, 10, false), 0.0001f);
    }
}
