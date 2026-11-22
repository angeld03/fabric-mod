package com.wildbound.client.mixin;

import com.wildbound.client.SkillCameraAim;
import com.wildbound.client.SkillCameraShake;
import com.wildbound.client.WandAimState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies additive impact shake and cinematic clips after vanilla camera setup.
 *
 * <p>While the wand aim pose is active the camera is left completely untouched:
 * no cinematic clips, no shake, no idle breathing. The crosshair, the wand tip
 * and {@code AimUtil.lookDir} therefore always agree while aiming.</p>
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void wildbound$applySkillCameraEffects(DeltaTracker deltaTracker, CallbackInfo callback) {
        if (WandAimState.cameraLocked()) return;
        Camera camera = (Camera) (Object) this;
        setRotation(
                camera.yRot() + SkillCameraShake.yawOffset() + SkillCameraAim.yawOffset(),
                camera.xRot() + SkillCameraShake.pitchOffset() + SkillCameraAim.pitchOffset());
    }
}
