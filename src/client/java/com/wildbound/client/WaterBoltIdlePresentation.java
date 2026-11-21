package com.wildbound.client;

import com.wildbound.item.WildboundItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Lightweight first-person idle presentation for the Water Bolt.
 *
 * <p>When the Water Wand is held with Water Bolt selected, a few client-local
 * droplets orbit the crystal tip and the camera gains a very subtle breathing
 * drift. It is intentionally cosmetic: it does not send packets, alter aim,
 * consume resources, or affect server-side combat.</p>
 */
public final class WaterBoltIdlePresentation {
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static float blend;
    private static float phase;

    private WaterBoltIdlePresentation() {
    }

    /** Called from the end-client-tick event. */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        boolean shouldPresent = player != null
                && client.level != null
                && client.gui.screen() == null
                && client.options.getCameraType().isFirstPerson()
                && player.getMainHandItem().is(WildboundItems.WAND)
                && "aqua_water_bolt".equals(WildboundClient.selectedRadialSkill(player))
                && !SkillChargeState.isCharging();

        float target = shouldPresent ? 1f : 0f;
        float response = shouldPresent ? 0.18f : 0.14f;
        blend += (target - blend) * response;

        if (blend < 0.002f) {
            blend = 0f;
            return;
        }
        if (!shouldPresent || player == null || client.level == null) return;

        phase += 0.045f;
        long tick = client.level.getGameTime();
        if (tick % 3L != 0L) return;

        Vec3 forward = player.getViewVector(1.0F).normalize();
        Vec3 right = forward.cross(WORLD_UP);
        if (right.lengthSqr() < 1.0E-5) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        // Approximate the crystal tip in first-person view space. The effect
        // follows player aim while staying just ahead of the held wand.
        Vec3 crystal = player.getEyePosition()
                .add(forward.scale(0.78))
                .add(right.scale(0.36))
                .subtract(up.scale(0.30));

        double orbit = phase * 1.7;
        double radius = 0.075 + Math.sin(phase * 0.7) * 0.018;
        for (int i = 0; i < 2; i++) {
            double angle = orbit + i * Math.PI;
            Vec3 position = crystal
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
            Vec3 tangent = right.scale(-Math.sin(angle) * 0.012)
                    .add(up.scale(Math.cos(angle) * 0.012));
            client.level.addParticle(ParticleTypes.BUBBLE,
                    position.x, position.y, position.z,
                    tangent.x, tangent.y + 0.006, tangent.z);
        }

        // A quiet glint punctuates the orbit; the cadence is deliberately low
        // to avoid turning an idle hold into a particle storm.
        if (tick % 12L == 0L) {
            client.level.addParticle(ParticleTypes.ENCHANT,
                    crystal.x, crystal.y, crystal.z,
                    right.x * 0.008, up.y * 0.012, forward.z * 0.008);
        }
    }

    /** Tiny camera breathing offsets, in degrees, blended in/out with the idle. */
    public static float yawOffset() {
        return blend * (float) (Math.sin(phase * 0.55) * 0.12
                + Math.sin(phase * 0.21 + 1.4) * 0.035);
    }

    public static float pitchOffset() {
        return blend * (float) (Math.sin(phase * 0.42 + 0.8) * 0.14);
    }

    public static void reset() {
        blend = 0f;
        phase = 0f;
    }
}
