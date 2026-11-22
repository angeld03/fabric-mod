package com.wildbound.client;

import com.wildbound.item.WildboundItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Aim-pose gate and procedural sway for the Water Wand.
 *
 * <p>Replaces the old decoupled idle camera: while aiming, the camera stays
 * vanilla and the wand/arm do the moving. The sway is a subtle (+/- ~1.5 deg)
 * procedural drift layered on top of the aim pose, damped while charging.</p>
 */
public final class WandAimState {
    private static float blend;

    private WandAimState() {
    }

    /** Wand in the main hand: first-person tip convergence + third-person bow arm. */
    public static boolean poseActive(LivingEntity entity) {
        return entity != null && entity.getMainHandItem().is(WildboundItems.WAND);
    }

    /** Aiming = pose + a radial skill ready. Locks the camera onto the crosshair. */
    public static boolean aiming() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || mc.gui.screen() != null || player == null || player.isSpectator()) return false;
        if (!poseActive(player)) return false;
        String skill = WildboundClient.selectedRadialSkill(player);
        return skill != null && !skill.isEmpty();
    }

    /** While true, no additive camera rotation is applied (crosshair == lookDir). */
    public static boolean cameraLocked() {
        return aiming();
    }

    /** Called from the end-client-tick event. */
    public static void tick(Minecraft client) {
        boolean target = poseActive(client.player);
        blend += ((target ? 1f : 0f) - blend) * (target ? 0.22f : 0.16f);
        if (blend < 0.002f) blend = 0f;
    }

    public static float blend() {
        return blend;
    }

    /** Continuous tick-based time so the sway is smooth between frames. */
    public static float time(float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        long ticks = mc.level == null ? 0L : mc.level.getGameTime();
        return ticks + tickDelta;
    }

    /** Procedural sway in degrees (<= ~1.6), damped while a charge is held. */
    public static float swayYaw(float time) {
        float damp = SkillChargeState.isCharging() ? 0.35f : 1f;
        return blend * damp * (float) (Math.sin(time * 0.11) * 1.15 + Math.sin(time * 0.043 + 1.7) * 0.45);
    }

    public static float swayPitch(float time) {
        float damp = SkillChargeState.isCharging() ? 0.35f : 1f;
        return blend * damp * (float) (Math.sin(time * 0.09 + 0.6) * 0.95 + Math.sin(time * 0.037 + 2.3) * 0.35);
    }

    public static void reset() {
        blend = 0f;
    }
}
