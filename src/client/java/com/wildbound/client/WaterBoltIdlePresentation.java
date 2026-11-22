package com.wildbound.client;

import com.wildbound.item.WildboundItems;
import com.wildbound.skills.AimUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Quiet first-person idle droplets around the wand crystal.
 *
 * <p>Anchored to {@link AimUtil#wandTip} - the same locator the renderer
 * converges onto the crosshair - instead of hardcoded view offsets. Purely
 * cosmetic: no packets, no aim changes, no resource use. The old camera
 * breathing was removed; the aim pose now carries a subtle procedural sway
 * ({@link WandAimState}) and the camera stays locked to the crosshair.</p>
 */
public final class WaterBoltIdlePresentation {
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
        if (!shouldPresent) return;

        phase += 0.045f;
        long tick = client.level.getGameTime();
        if (tick % 3L != 0L) return;

        Vec3 look = AimUtil.lookDir(player, 1.0F);
        Vec3 right = AimUtil.right(look);
        Vec3 up = AimUtil.up(look);
        Vec3 crystal = AimUtil.wandTip(player, 1.0F);

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

        // A quiet glint punctuates the orbit; deliberately low cadence.
        if (tick % 12L == 0L) {
            client.level.addParticle(ParticleTypes.ENCHANT,
                    crystal.x, crystal.y, crystal.z,
                    right.x * 0.008, up.y * 0.012, look.z * 0.008);
        }
    }

    public static void reset() {
        phase = 0f;
    }
}
