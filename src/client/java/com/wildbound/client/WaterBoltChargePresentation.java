package com.wildbound.client;

import com.wildbound.item.WildboundItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Client-only, charge-scaled visual feedback around the Water Wand crystal. */
public final class WaterBoltChargePresentation {
    private static float phase;

    private WaterBoltChargePresentation() {}

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        boolean charging = player != null && client.level != null
                && client.gui.screen() == null
                && client.options.getCameraType().isFirstPerson()
                && player.getMainHandItem().is(WildboundItems.WAND)
                && SkillChargeState.isCharging()
                && "aqua_water_bolt".equals(SkillChargeState.skillId());

        if (!charging) {
            phase *= 0.82f;
            if (phase < 0.002f) phase = 0f;
            return;
        }

        float charge = SkillChargeState.chargeProgress();
        phase += 0.24f + charge * 0.12f;
        if (client.level.getGameTime() % 2L != 0L) return;

        Vec3 forward = player.getViewVector(1.0F).normalize();
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-5) right = new Vec3(1.0, 0.0, 0.0);
        else right = right.normalize();
        Vec3 up = right.cross(forward).normalize();

        Vec3 crystal = player.getEyePosition()
                .add(forward.scale(0.78))
                .add(right.scale(0.36))
                .subtract(up.scale(0.30));

        // The orbit tightens as energy compresses into the crystal.
        double radius = 0.28 - charge * 0.17;
        int strands = 3 + Math.round(charge * 3f);
        for (int i = 0; i < strands; i++) {
            double angle = phase + i * (Math.PI * 2.0 / strands);
            Vec3 point = crystal
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
            double speed = 0.012 + charge * 0.025;
            Vec3 velocity = forward.scale(speed)
                    .add(right.scale(-Math.sin(angle) * speed))
                    .add(up.scale(Math.cos(angle) * speed));
            client.level.addParticle(ParticleTypes.BUBBLE,
                    point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }

        client.level.addParticle(ParticleTypes.SPLASH,
                crystal.x, crystal.y, crystal.z, 0.0, 0.015 + charge * 0.025, 0.0);
        if (charge > 0.35f) {
            client.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    crystal.x, crystal.y, crystal.z,
                    right.x * 0.02, up.y * 0.02, forward.z * 0.02);
        }
        if (charge > 0.85f && client.level.getGameTime() % 4L == 0L) {
            client.level.addParticle(ParticleTypes.ENCHANT,
                    crystal.x, crystal.y, crystal.z, 0.0, 0.035, 0.0);
        }
    }

    public static void reset() {
        phase = 0f;
    }
}
