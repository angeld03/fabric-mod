package com.wildbound.client;

import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RadialSkillConfig;

import java.util.List;
import java.util.UUID;

/**
 * Client-side selection and short-lived mirror of the server-owned radial
 * configuration. The mirror makes assignment feedback immediate while the
 * authoritative profile attachment catches up.
 *
 * <p>The mirror is deliberately scoped to one player. Storing it globally meant
 * that disconnecting and joining another account (or another server) within the
 * cache window kept showing the previous layout, and could even cast a skill the
 * new account never assigned.</p>
 */
public final class RadialSkillState {
    private static final long CACHE_MILLIS = 20_000L;

    private static UUID ownerId;
    private static int selectedSlot;
    private static List<String> cachedSlots;
    private static long cacheUntil;

    private RadialSkillState() {
    }

    /** Called when the client leaves a world so no state leaks into the next one. */
    public static synchronized void reset() {
        ownerId = null;
        selectedSlot = 0;
        cachedSlots = null;
        cacheUntil = 0L;
    }

    private static synchronized void bind(UUID playerId) {
        if (playerId == null) {
            reset();
            return;
        }
        if (!playerId.equals(ownerId)) {
            ownerId = playerId;
            selectedSlot = 0;
            cachedSlots = null;
            cacheUntil = 0L;
        }
    }

    /**
     * Resolves the slots to display. An in-flight optimistic mirror wins, but
     * only for the player that requested it; otherwise the authoritative
     * attachment is used directly.
     */
    public static synchronized List<String> slots(PlayerRpgProfile profile) {
        bind(profile == null || profile.uuid() == null || profile.uuid().isEmpty()
                ? null : UUID.fromString(profile.uuid()));
        long now = System.currentTimeMillis();
        if (cachedSlots != null && now < cacheUntil) return cachedSlots;
        cachedSlots = null;
        if (profile == null) return RadialSkillConfig.emptySlots();
        return profile.radialSkills();
    }

    public static synchronized void setOptimisticSlots(PlayerRpgProfile profile, List<String> slots) {
        bind(profile == null || profile.uuid() == null || profile.uuid().isEmpty()
                ? null : UUID.fromString(profile.uuid()));
        cachedSlots = RadialSkillConfig.copySlots(slots);
        cacheUntil = System.currentTimeMillis() + CACHE_MILLIS;
    }

    public static synchronized void acceptServerSlots(PlayerRpgProfile profile, List<String> slots) {
        bind(profile == null || profile.uuid() == null || profile.uuid().isEmpty()
                ? null : UUID.fromString(profile.uuid()));
        cachedSlots = RadialSkillConfig.copySlots(slots);
        cacheUntil = System.currentTimeMillis() + CACHE_MILLIS;
    }

    public static synchronized void rejectPendingSlots() {
        cachedSlots = null;
        cacheUntil = 0L;
    }

    public static synchronized int selectedSlot() {
        return selectedSlot;
    }

    public static synchronized void selectSlot(int slot) {
        if (slot >= 0 && slot < RadialSkillConfig.SLOT_COUNT) selectedSlot = slot;
    }

    public static synchronized String selectedSkill(PlayerRpgProfile profile) {
        List<String> slots = slots(profile);
        if (slots.isEmpty()) return null;
        if (selectedSlot >= 0 && selectedSlot < slots.size()) {
            String selected = slots.get(selectedSlot);
            if (selected != null && !selected.isEmpty()) return selected;
        }
        for (int i = 0; i < slots.size(); i++) {
            String skillId = slots.get(i);
            if (skillId != null && !skillId.isEmpty()) {
                selectedSlot = i;
                return skillId;
            }
        }
        return null;
    }
}