package com.wildbound.skills;

import java.util.List;

/**
 * One of the eighteen elemental paths of WILDBOUND. Elements are a catalog/UI
 * layer on top of the original six realms: every element is universally
 * available and only gated by level, points and the previous tier.
 */
public record SkillElement(
        String id,
        String displayName,
        String alias,
        int primaryColor,
        int secondaryColor,
        List<SkillDefinition> skills
) {
    public SkillElement {
        skills = List.copyOf(skills);
    }

    public int unlockedCount(java.util.function.Predicate<String> hasSkill) {
        int count = 0;
        for (SkillDefinition skill : skills) {
            if (hasSkill.test(skill.id())) count++;
        }
        return count;
    }
}
