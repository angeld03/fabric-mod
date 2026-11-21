package com.wildbound.test;

import com.wildbound.attributes.DerivedStats;
import com.wildbound.skills.AquaSkillExecutors;
import com.wildbound.skills.SkillActivationService;
import com.wildbound.skills.SkillCatalog;
import com.wildbound.skills.SkillDefinition;
import com.wildbound.skills.SkillElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the water balance rules requested for the cinematic rework. */
class AquaSkillBalanceTest {

    @Test
    void everyWaterSkillCostsAtMostTwentyMana() {
        SkillCatalog.initialize();
        AquaSkillExecutors.registerAll();

        SkillElement water = SkillCatalog.byId("aqua").orElseThrow();
        for (SkillDefinition skill : water.skills()) {
            float cost = SkillActivationService.getManaCost(skill);
            assertTrue(cost > 0f, skill.id() + " should cost mana");
            assertTrue(cost <= 20f, skill.id() + " costs too much mana: " + cost);
        }
    }

    @Test
    void baseManaPoolCanAffordTheMostExpensiveSkill() {
        assertTrue(DerivedStats.BASE.maxMana() >= 20f);
        assertTrue(DerivedStats.BASE.manaRegeneration() > 0f);
    }
}
