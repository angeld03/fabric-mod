package com.wildbound.test;

import com.wildbound.player.RadialSkillConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialSkillConfigTest {
    @Test
    void normalizesToFiveStableSlots() {
        List<String> normalized = RadialSkillConfig.normalize(
                Arrays.asList("aqua_water_bolt", "", "aqua_healing_stream", null, "fire_ember_bolt"));

        assertEquals(5, normalized.size());
        assertEquals("aqua_water_bolt", normalized.get(0));
        assertEquals("", normalized.get(1));
        assertEquals("aqua_healing_stream", normalized.get(2));
        assertEquals("", normalized.get(3));
        assertEquals("fire_ember_bolt", normalized.get(4));
    }

    @Test
    void removesOnlyLaterDuplicates() {
        List<String> normalized = RadialSkillConfig.normalize(
                List.of("skill_a", "skill_a", "skill_b"));

        assertEquals("skill_a", normalized.get(0));
        assertEquals("", normalized.get(1));
        assertEquals("skill_b", normalized.get(2));
    }

    @Test
    void assignmentMovesExistingSkillInsteadOfDuplicatingIt() {
        List<String> assigned = RadialSkillConfig.assign(
                List.of("skill_a", "skill_b", "", "", ""), 3, "skill_a");

        assertEquals("", assigned.get(0));
        assertEquals("skill_b", assigned.get(1));
        assertEquals("skill_a", assigned.get(3));
        assertEquals(1, count(assigned, "skill_a"));
    }

    @Test
    void findsFirstEmptySlotWithoutMovingExistingSlots() {
        assertEquals(2, RadialSkillConfig.firstEmpty(
                List.of("skill_a", "skill_b", "", "", "")));
        assertTrue(RadialSkillConfig.emptySlots().stream().allMatch(String::isEmpty));
    }

    private static int count(List<String> values, String wanted) {
        int result = 0;
        for (String value : values) {
            if (wanted.equals(value)) result++;
        }
        return result;
    }
}
