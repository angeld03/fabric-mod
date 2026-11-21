package com.wildbound.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Scoped cast direction supplied by the firing client.
 *
 * <p>The player camera is the source of aiming input. This short-lived context
 * lets the server-side executor capture that exact direction during activation
 * instead of relying on a potentially one-tick-old entity rotation packet.</p>
 */
public final class SkillAimContext {
    private static final ThreadLocal<Entry> ACTIVE = new ThreadLocal<>();

    private SkillAimContext() {
    }

    public static Vec3 direction(ServerPlayer player) {
        Entry entry = ACTIVE.get();
        if (entry != null && entry.playerId.equals(player.getUUID())) return entry.direction;
        return normalize(player.getLookAngle(), new Vec3(0, 0, 1));
    }

    public static <T> T withAim(ServerPlayer player, Vec3 requestedDirection, Supplier<T> action) {
        Entry previous = ACTIVE.get();
        ACTIVE.set(new Entry(player.getUUID(), normalize(requestedDirection, player.getLookAngle())));
        try {
            return action.get();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    private static Vec3 normalize(Vec3 value, Vec3 fallback) {
        if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            return safeFallback(fallback);
        }
        double lengthSqr = value.lengthSqr();
        if (!Double.isFinite(lengthSqr) || lengthSqr < 1.0E-8) return safeFallback(fallback);
        return value.scale(1.0 / Math.sqrt(lengthSqr));
    }

    private static Vec3 safeFallback(Vec3 fallback) {
        if (fallback == null || !Double.isFinite(fallback.x)
                || !Double.isFinite(fallback.y) || !Double.isFinite(fallback.z)
                || fallback.lengthSqr() < 1.0E-8) {
            return new Vec3(0, 0, 1);
        }
        return fallback.normalize();
    }

    private record Entry(UUID playerId, Vec3 direction) {
    }
}
