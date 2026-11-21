package com.wildbound.client;

import com.wildbound.network.SkillCameraShakePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-side decaying camera shake used by cinematic skill impacts. */
public final class SkillCameraShake {
    private static final List<Shake> ACTIVE = new ArrayList<>();

    private SkillCameraShake() {
    }

    public static void add(SkillCameraShakePayload payload) {
        long now = System.currentTimeMillis();
        synchronized (ACTIVE) {
            ACTIVE.removeIf(shake -> shake.endsAt <= now);
            ACTIVE.add(new Shake(
                    new Vec3(payload.x(), payload.y(), payload.z()),
                    payload.intensity(),
                    now,
                    now + payload.durationTicks() * 50L));
            if (ACTIVE.size() > 8) ACTIVE.subList(0, ACTIVE.size() - 8).clear();
        }
    }

    public static float pitchOffset() {
        return offset(true);
    }

    public static float yawOffset() {
        return offset(false);
    }

    private static float offset(boolean pitch) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || ACTIVE.isEmpty()) return 0f;

        long now = System.currentTimeMillis();
        Vec3 eye = client.player.getEyePosition();
        float result = 0f;
        synchronized (ACTIVE) {
            Iterator<Shake> iterator = ACTIVE.iterator();
            while (iterator.hasNext()) {
                Shake shake = iterator.next();
                if (shake.endsAt <= now) {
                    iterator.remove();
                    continue;
                }
                double duration = Math.max(1, shake.endsAt - shake.started);
                float life = (float) ((shake.endsAt - now) / duration);
                float distance = (float) eye.distanceTo(shake.center);
                float falloff = Math.max(0f, 1f - distance / 32f);
                float amplitude = shake.intensity * 0.22f * falloff * life * life;
                double phase = now * (pitch ? 0.057 : 0.043) + (pitch ? 1.7 : 0.3);
                result += (float) (Math.sin(phase) * amplitude);
            }
        }
        return Math.max(-4.0f, Math.min(4.0f, result));
    }

    private record Shake(Vec3 center, int intensity, long started, long endsAt) {
    }
}
