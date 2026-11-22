package com.wildbound.test;

import com.wildbound.skills.AimUtil;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure-math checks for the shared aim source of truth (no Minecraft runtime). */
class AimUtilTest {
    @Test
    void basisIsOrthonormalForEveryLook() {
        Vec3[] looks = {
                new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, 0, 0),
                new Vec3(0.3, 0.9, 0.2).normalize(), new Vec3(0, -1, 0), new Vec3(0, 1, 0)
        };
        for (Vec3 look : looks) {
            Vec3 right = AimUtil.right(look);
            Vec3 up = AimUtil.up(look);
            assertEquals(1.0, right.length(), 1.0E-6);
            assertEquals(1.0, up.length(), 1.0E-6);
            assertEquals(0.0, right.dot(look), 1.0E-6);
            assertEquals(0.0, up.dot(look), 1.0E-6);
            assertEquals(0.0, right.dot(up), 1.0E-6);
        }
    }

    @Test
    void wandTipSitsOnTheAimRayAtTipDepth() {
        Vec3 eye = new Vec3(10.0, 65.0, -4.0);
        Vec3 look = new Vec3(0.4, -0.2, 0.8).normalize();
        Vec3 offset = AimUtil.wandTip(eye, look).subtract(eye);
        assertEquals(AimUtil.TIP_DEPTH, offset.length(), 1.0E-9);
        // Parallel to look => exactly on the crosshair ray.
        assertEquals(0.0, offset.cross(look).length(), 1.0E-9);
    }

    @Test
    void degenerateLookFallsBackSafely() {
        Vec3 zero = new Vec3(0, 0, 0);
        assertEquals(1.0, AimUtil.right(zero).length(), 1.0E-9);
        assertEquals(1.0, AimUtil.up(zero).length(), 1.0E-9);
    }
}
