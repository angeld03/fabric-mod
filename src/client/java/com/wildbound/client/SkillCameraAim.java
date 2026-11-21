package com.wildbound.client;

import com.wildbound.network.SkillCameraAimPayload;

/**
 * Client-side cinematic camera system.
 *
 * <p>Replaces the old broken absolute-angle approach. Now uses RELATIVE offsets
 * from the player's view with proper easing curves (keyframe animation), so the
 * camera always starts from neutral and returns to neutral smoothly — no more
 * violent jumps to world angles.</p>
 */
public final class SkillCameraAim {
    private static final int MODE_SMOOTH = 0;
    private static final int MODE_PUNCH = 1;
    private static final int MODE_SHAKE_KICK = 2;
    private static final int MODE_HOLD = 3;

    // Active clip state
    private static float fromYaw = 0f, fromPitch = 0f;
    private static float toYaw = 0f, toPitch = 0f;
    private static int elapsed = 0, duration = 0;
    private static int mode = MODE_SMOOTH;
    private static float intensity = 1f;
    private static boolean active = false;

    // Current output offsets (read by the Camera mixin)
    private static float currentYaw = 0f;
    private static float currentPitch = 0f;

    private SkillCameraAim() {
    }

    /** Start a new camera clip from a payload. Relative offsets, eased. */
    public static void play(SkillCameraAimPayload payload) {
        // Start the clip from wherever the camera currently is (smooth chaining).
        fromYaw = currentYaw;
        fromPitch = currentPitch;
        toYaw = payload.yawOffset();
        toPitch = payload.pitchOffset();
        duration = Math.max(1, payload.durationTicks());
        mode = payload.mode();
        intensity = payload.intensity();
        elapsed = 0;
        active = true;
    }

    /** Advance the animation one client tick. */
    public static void tick() {
        if (!active) return;

        elapsed++;
        float t = Math.min(1f, elapsed / (float) duration);
        float eased = ease(t, mode);

        currentYaw = fromYaw + (toYaw - fromYaw) * eased;
        currentPitch = fromPitch + (toPitch - fromPitch) * eased;

        // Apply intensity scaling.
        currentYaw *= intensity;
        currentPitch *= intensity;

        if (elapsed >= duration) {
            active = false;
        }
    }

    /** Easing functions. */
    private static float ease(float t, int mode) {
        switch (mode) {
            case MODE_PUNCH: {
                // Fast attack (t^0.35), slow ease-out. Great for impact hits.
                if (t < 0.35f) {
                    float p = t / 0.35f;
                    return (float) Math.pow(p, 0.35);
                } else {
                    float p = (t - 0.35f) / 0.65f;
                    return 1f - (float) Math.pow(1f - p, 2.2);
                }
            }
            case MODE_SHAKE_KICK: {
                // Damped sine wave: snaps out then oscillates back to zero.
                float decay = (float) Math.exp(-4.0 * t);
                return (float) (Math.sin(t * Math.PI * 3.0) * decay);
            }
            case MODE_HOLD: {
                // Linear ramp up, hold, then release.
                if (t < 0.25f) return t / 0.25f;
                if (t < 0.75f) return 1f;
                return 1f - (t - 0.75f) / 0.25f;
            }
            case MODE_SMOOTH:
            default: {
                // Smoothstep: buttery ease in-out (3t² - 2t³).
                return t * t * (3f - 2f * t);
            }
        }
    }

    public static float yawOffset() {
        return currentYaw;
    }

    public static float pitchOffset() {
        return currentPitch;
    }

    public static boolean active() {
        return active;
    }

    public static void reset() {
        fromYaw = toYaw = currentYaw = 0f;
        fromPitch = toPitch = currentPitch = 0f;
        elapsed = duration = 0;
        active = false;
        intensity = 1f;
    }
}