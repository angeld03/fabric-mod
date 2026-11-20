package com.wildbound.skills;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Central registry for skill definitions. */
public final class SkillRegistry {
    private static final Map<String, SkillDefinition> SKILLS = new LinkedHashMap<>();

    private SkillRegistry() {
    }

    public static void register(SkillDefinition definition) {
        if (SKILLS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate skill id: " + definition.id());
        }
    }

    public static Optional<SkillDefinition> get(String id) {
        return Optional.ofNullable(SKILLS.get(id));
    }

    public static Collection<SkillDefinition> all() {
        return SKILLS.values();
    }

    public static boolean contains(String id) {
        return SKILLS.containsKey(id);
    }
}
