package com.wildbound.client;

import com.wildbound.network.SkillCameraAimPayload;

/**
 * Client-side cinematic camera clips.
 *
 * <p>Every command is a relative offset from the vanilla camera rotation. Clips
 * have an explicit settle phase: a focus or impact can be felt, then the view
 * returns to neutral instead of leaving the player permanently tilted.</p>
 */
public final class SkillCameraAim {
    private static final int MODE_SMOOTH = 0;
    private static final int MODE_PUNCH = 1;
    private static final int MODE_SHAKE_KICK = 2;
    private static final int MODE_HOLD = 3;
    private static final int MIN_RETURN_TICKS = 4;

    private static float fromYaw;
    private static float fromPitch;
    private static float targetYaw;
    private static float targetPitch;
    private static float currentYaw;
    private static float currentPitch;

    private static int elapsed;
    private static int duration;
    private static int returnDuration;
    private static int mode = MODE_SMOOTH;
    private static boolean active;

    private SkillCameraAim() {
    }

    /** Starts a new relative camera clip, chaining from the current offset. */
    public static void play(SkillCameraAimPayload payload) {
        fromYaw = currentYaw;
        fromPitch = currentPitch;

        // Scale once when the clip is received. Multiplying the live output on
        // every tick caused low-intensity clips to decay exponentially.
        float intensity = payload.intensity();
        targetYaw = clamp(payload.yawOffset() * intensity, -12f, 12f);
        targetPitch = clamp(payload.pitchOffset() * intensity, -12f, 12f);

        duration = Math.max(1, payload.durationTicks());
        returnDuration = Math.max(MIN_RETURN_TICKS, Math.min(12, duration));
        mode = payload.mode();
        elapsed = 0;
        active = true;
    }

    /** Advances the cinematic clip once per client tick. */
    public static void tick() {
        if (!active) return;

        elapsed++;
        float t = Math.min(1f, elapsed / (float) duration);

        if (mode == MODE_SHAKE_KICK) {
            // A damped impulse. Any previous camera offset is gently absorbed
            // while the new kick rings out and returns to zero.
            float impulse = (float) (Math.sin(t * Math.PI * 3.0) * Math.exp(-4.0 * t));
            float settle = 1f - smoothstep(t);
            currentYaw = fromYaw * settle + targetYaw * impulse;
            currentPitch = fromPitch * settle + targetPitch * impulse;
        } else if (elapsed <= duration) {
            float eased = ease(t, mode);
            currentYaw = lerp(fromYaw, targetYaw, eased);
            currentPitch = lerp(fromPitch, targetPitch, eased);
        } else {
            float returnT = Math.min(1f, (elapsed - duration) / (float) returnDuration);
            float settle = smoothstep(returnT);
            currentYaw = targetYaw * (1f - settle);
            currentPitch = targetPitch * (1f - settle);
        }

        if (elapsed >= duration + returnDuration) {
            currentYaw = 0f;
            currentPitch = 0f;
            active = false;
        }
    }

    private static float ease(float t, int curve) {
        return switch (curve) {
            case MODE_PUNCH -> {
                // Fast camera response, then a soft arrival at the focus point.
                if (t < 0.32f) {
                    float p = t / 0.32f;
                    yield 1f - (float) Math.pow(1f - p, 3.2);
                }
                float p = (t - 0.32f) / 0.68f;
                yield 1f - (float) Math.pow(1f - p, 2.4);
            }
            case MODE_HOLD -> {
                if (t < 0.22f) yield smoothstep(t / 0.22f);
                if (t < 0.78f) yield 1f;
                yield 1f - smoothstep((t - 0.78f) / 0.22f);
            }
            case MODE_SHAKE_KICK -> (float) (Math.sin(t * Math.PI * 3.0) * Math.exp(-4.0 * t));
            case MODE_SMOOTH -> smoothstep(t);
            default -> smoothstep(t);
        };
    }

    private static float smoothstep(float t) {
        float x = clamp(t, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
        fromYaw = fromPitch = targetYaw = targetPitch = 0f;
        currentYaw = currentPitch = 0f;
        elapsed = duration = returnDuration = 0;
        mode = MODE_SMOOTH;
        active = false;
    }
}
