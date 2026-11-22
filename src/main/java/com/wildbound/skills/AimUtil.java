package com.wildbound.skills;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Single source of truth for wand aiming, shared by client and server.
 *
 * <p>{@link #lookDir(Player, float)} is the crosshair direction: the same
 * interpolated view vector the camera renders with. The wand pose, the VFX
 * spawn locator and the projectile direction are all derived from it, so the
 * tip, the particles and the damage ray can never disagree.</p>
 *
 * <p>{@link #wandTip(Vec3, Vec3)} is the crystal-tip locator. Because the
 * first-person renderer rotates the wand about its grip until the tip lands on
 * the crosshair ray, the tip lives exactly {@link #TIP_DEPTH} blocks ahead of
 * the eye on that ray. No hardcoded side/up offsets are needed anywhere.</p>
 */
public final class AimUtil {
    /** Depth (blocks from the eye) where the converged wand tip sits on the crosshair ray. */
    public static final double TIP_DEPTH = 0.96;
    /** Crystal apex height in model pixels (0..16 grid, y up). The grip/pommel sits at y = 0. */
    public static final float WAND_TIP_MODEL_Y = 22.1f;
    public static final float WAND_GRIP_MODEL_Y = 0.0f;
    /** Shaft centre on the model x/z grid (8 of 16). */
    public static final float WAND_MODEL_CENTRE = 8.0f / 16.0f;

    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    private AimUtil() {
    }

    /** Interpolated, normalized look direction - identical to the camera/crosshair direction. */
    public static Vec3 lookDir(Player player, float tickDelta) {
        Vec3 look = player.getViewVector(tickDelta);
        double length = look.length();
        if (!Double.isFinite(length) || length < 1.0E-8) return new Vec3(0.0, 0.0, 1.0);
        return look.scale(1.0 / length);
    }

    /** Freshest look direction, for sampling at the exact firing moment. */
    public static Vec3 lookDir(Player player) {
        return lookDir(player, 1.0F);
    }

    /** Screen-right basis vector for a look direction (matches the server side() helper). */
    public static Vec3 right(Vec3 look) {
        Vec3 side = look.cross(WORLD_UP);
        return side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
    }

    /** Screen-up basis vector for a look direction. */
    public static Vec3 up(Vec3 look) {
        Vec3 up = right(look).cross(look);
        return up.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : up.normalize();
    }

    /** World-space crystal-tip locator: on the crosshair ray, TIP_DEPTH ahead of the eye. */
    public static Vec3 wandTip(Vec3 eye, Vec3 look) {
        return eye.add(look.scale(TIP_DEPTH));
    }

    /** Interpolated tip locator, matching the pose rendered this frame. */
    public static Vec3 wandTip(Player player, float tickDelta) {
        return wandTip(player.getEyePosition(tickDelta), lookDir(player, tickDelta));
    }

    public static Vec3 wandTip(Player player) {
        return wandTip(player, 1.0F);
    }
}
