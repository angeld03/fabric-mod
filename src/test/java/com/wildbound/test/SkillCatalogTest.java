package com.wildbound.test;

import com.wildbound.skills.SkillCatalog;
import com.wildbound.skills.SkillCategory;
import com.wildbound.skills.SkillDefinition;
import com.wildbound.skills.SkillElement;
import com.wildbound.skills.SkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCatalogTest {

    @Test
    void registersEighteenElementsWithTenOrderedSkillsEach() {
        SkillCatalog.initialize();
        List<SkillElement> elements = SkillCatalog.all();
        assertEquals(18, elements.size());

        Set<String> ids = new HashSet<>();
        for (SkillElement element : elements) {
            assertEquals(10, element.skills().size(), element.displayName());
            for (int i = 0; i < 10; i++) {
                SkillDefinition skill = element.skills().get(i);
                assertEquals(SkillCatalog.TIER_LEVELS[i], skill.requiredLevel(), skill.name());
                assertEquals(SkillCatalog.TIER_COSTS[i], skill.pointCost(), skill.name());
                assertEquals(i == 0 ? 0 : 1, skill.prerequisites().size(), skill.name());
                assertEquals(i == 9, skill.category() == SkillCategory.ULTIMATE, skill.name());
                assertEquals("", skill.originRealm(), skill.name());
                assertTrue(ids.add(skill.id()), "duplicate id " + skill.id());
                assertTrue(SkillRegistry.contains(skill.id()), skill.id());
            }
        }
        assertEquals(180, ids.size());
    }

    @Test
    void keepsTheRequestedNamesAndBranchOrder() {
        SkillCatalog.initialize();
        SkillElement water = SkillCatalog.byId("aqua").orElseThrow();
        assertEquals("Agua", water.displayName());
        assertEquals("Aqua", water.alias());
        assertEquals("Water Bolt", water.skills().get(0).name());
        assertEquals("Leviathan's Wrath", water.skills().get(9).name());
        assertEquals(water.skills().get(8).id(), water.skills().get(9).prerequisites().get(0));

        SkillElement time = SkillCatalog.byId("chronos").orElseThrow();
        assertEquals("Tiempo", time.displayName());
        assertEquals("Eternal Moment", time.skills().get(9).name());

        SkillElement sound = SkillCatalog.byId("resonance").orElseThrow();
        assertEquals("Sonido", sound.displayName());
        assertEquals("World Resonance", sound.skills().get(9).name());
        assertEquals(sound, SkillCatalog.forSkill(sound.skills().get(4).id()).orElseThrow());
    }
}
