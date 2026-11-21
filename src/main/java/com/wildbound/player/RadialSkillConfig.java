package com.wildbound.player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared representation of the five radial slots.
 *
 * <p>An empty slot is represented by an empty string. Keeping the list at a
 * fixed length makes slot numbers stable and avoids nullable values in codecs
 * and attachment payloads.</p>
 */
public final class RadialSkillConfig {
    public static final int SLOT_COUNT = 5;
    public static final String EMPTY = "";

    private RadialSkillConfig() {
    }

    public static List<String> emptySlots() {
        return List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY);
    }

    /**
     * Copies exactly five values while preserving empty positions and duplicate
     * values. This is used for packets so the server can reject duplicates.
     */
    public static List<String> copySlots(List<String> values) {
        List<String> copy = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String value = values != null && i < values.size() ? values.get(i) : null;
            copy.add(value == null ? EMPTY : value.trim());
        }
        return List.copyOf(copy);
    }

    /**
     * Normalizes persisted radial data. Empty positions stay in place and only
     * later duplicates of the same skill are removed.
     */
    public static List<String> normalize(List<String> values) {
        List<String> copy = new ArrayList<>(copySlots(values));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < copy.size(); i++) {
            String value = copy.get(i);
            if (value.isEmpty()) continue;
            if (!seen.add(value)) copy.set(i, EMPTY);
        }
        return List.copyOf(copy);
    }

    /** Removes radial entries whose skill is no longer unlocked. */
    public static List<String> retainUnlocked(List<String> values, List<String> unlockedSkills) {
        List<String> copy = new ArrayList<>(copySlots(values));
        Set<String> unlocked = new HashSet<>(unlockedSkills == null ? List.of() : unlockedSkills);
        for (int i = 0; i < copy.size(); i++) {
            String value = copy.get(i);
            if (!value.isEmpty() && !unlocked.contains(value)) copy.set(i, EMPTY);
        }
        return List.copyOf(copy);
    }

    /** Assigns a skill to one slot, removing it from any other radial slot. */
    public static List<String> assign(List<String> values, int targetSlot, String skillId) {
        List<String> copy = new ArrayList<>(copySlots(values));
        if (targetSlot < 0 || targetSlot >= copy.size()) return List.copyOf(copy);

        String id = skillId == null ? EMPTY : skillId.trim();
        if (id.isEmpty()) {
            copy.set(targetSlot, EMPTY);
            return List.copyOf(copy);
        }

        for (int i = 0; i < copy.size(); i++) {
            if (i != targetSlot && id.equals(copy.get(i))) copy.set(i, EMPTY);
        }
        copy.set(targetSlot, id);
        return List.copyOf(copy);
    }

    public static int indexOf(List<String> values, String skillId) {
        if (values == null || skillId == null || skillId.isEmpty()) return -1;
        for (int i = 0; i < values.size(); i++) {
            if (skillId.equals(values.get(i))) return i;
        }
        return -1;
    }

    public static int firstEmpty(List<String> values) {
        if (values == null) return 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i >= values.size() || values.get(i) == null || values.get(i).isEmpty()) return i;
        }
        return -1;
    }
}
