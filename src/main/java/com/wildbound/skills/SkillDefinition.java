package com.wildbound.skills;

import java.util.List;

/** Extensible definition of a skill; no gameplay effects live here. */
public record SkillDefinition(
        String id,
        String name,
        String description,
        int requiredLevel,
        String originRealm,
        int pointCost,
        List<String> prerequisites,
        SkillCategory category,
        SkillClass skillClass,
        String type
) {
    public SkillDefinition {
        prerequisites = List.copyOf(prerequisites);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Skill id cannot be blank");
    }
}
