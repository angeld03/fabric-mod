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
        String type,
        String iconTexture // Custom icon texture path (e.g., "wildbound:skill/aqua_water_bolt")
) {
    public SkillDefinition {
        prerequisites = List.copyOf(prerequisites);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Skill id cannot be blank");
    }

    // Backward compatibility constructor
    public SkillDefinition(String id, String name, String description, int requiredLevel,
                           String originRealm, int pointCost, List<String> prerequisites,
                           SkillCategory category, SkillClass skillClass, String type) {
        this(id, name, description, requiredLevel, originRealm, pointCost, prerequisites,
             category, skillClass, type, "");
    }
}
