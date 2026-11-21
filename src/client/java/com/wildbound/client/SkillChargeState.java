package com.wildbound.client;

import net.minecraft.world.item.ItemStack;

/**
 * Client-side hold-to-charge state for wand spells.
 *
 * <p>The player holds right-click to charge, then releases to fire. This class
 * tracks whether a charge is in progress and how long it has been held so the
 * client can send an accurate charge level when the button is released.</p>
 */
public final class SkillChargeState {
    private static boolean charging = false;
    private static long startTime = 0L;
    private static String skillId = "";

    private SkillChargeState() {
    }

    /** Call when the charge button is pressed. */
    public static void start(String skillId) {
        charging = true;
        startTime = System.currentTimeMillis();
        SkillChargeState.skillId = skillId;
    }

    /** Call when the charge button is released. Returns the charge level (0-1). */
    public static float stop() {
        if (!charging) return 0f;
        charging = false;
        long held = System.currentTimeMillis() - startTime;
        // Full charge at 1.0 seconds; linear ramp.
        float level = held / 1000f;
        if (level > 1f) level = 1f;
        if (level < 0f) level = 0f;
        skillId = "";
        return level;
    }

    public static boolean isCharging() {
        return charging;
    }

    public static String skillId() {
        return skillId;
    }

    /** Smooth 0..1 charge fraction for client-side visual feedback. */
    public static float chargeProgress() {
        if (!charging) return 0f;
        return Math.max(0f, Math.min(1f, (System.currentTimeMillis() - startTime)
                / (float) fullChargeMillis()));
    }

    /** Call on disconnect / screen open to abort any in-progress charge. */
    public static void reset() {
        charging = false;
        startTime = 0L;
        skillId = "";
    }

    /** Milliseconds required for a full charge. */
    public static int fullChargeMillis() {
        return 1000;
    }
}