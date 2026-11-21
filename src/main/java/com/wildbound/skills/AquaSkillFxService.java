package com.wildbound.skills;

import com.wildbound.network.AquaSkillFxPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends compact, deterministic Aqua FX events to nearby clients.
 * Combat remains server-authoritative; this service only describes visuals.
 */
public final class AquaSkillFxService {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final double BROADCAST_RADIUS_SQR = 72.0 * 72.0;

    private AquaSkillFxService() {
    }

    public static void start(ServerPlayer caster, String skillId) {
        if (caster == null || caster.isRemoved() || skillId == null || !skillId.startsWith("aqua_")) return;

        int duration = duration(skillId);
        Vec3 origin = caster.position().add(0.0, 0.15, 0.0);
        Vec3 look = caster.getLookAngle().normalize();
        long id = NEXT_ID.getAndIncrement();
        long seed = ThreadLocalRandom.current().nextLong();

        AquaSkillFxPayload payload = AquaSkillFxPayload.start(
                id, caster.getUUID(), skillId,
                origin.x, origin.y, origin.z,
                look.x, look.y, look.z,
                duration, seed
        );

        for (ServerPlayer viewer : caster.level().players()) {
            if (viewer.distanceToSqr(origin) > BROADCAST_RADIUS_SQR) continue;
            if (!ServerPlayNetworking.canSend(viewer, AquaSkillFxPayload.TYPE)) continue;
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static int duration(String skillId) {
        return switch (skillId) {
            case "aqua_water_bolt" -> 48;
            case "aqua_healing_stream" -> 54;
            case "aqua_aqua_whip" -> 24;
            case "aqua_bubble_shield" -> 100;
            case "aqua_tidal_wave" -> 52;
            case "aqua_aqua_dash" -> 26;
            case "aqua_whirlpool" -> 100;
            case "aqua_pressure_lance" -> 42;
            case "aqua_tsunami" -> 72;
            case "aqua_leviathan_s_wrath" -> 88;
            default -> 30;
        };
    }
}
