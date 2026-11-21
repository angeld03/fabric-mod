package com.wildbound.client.mixin;

import com.wildbound.client.SkillCameraAim;
import com.wildbound.client.SkillCameraShake;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies skill camera shake and cinematic aim offsets after vanilla camera rotation is calculated. */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void wildbound$applySkillCameraEffects(DeltaTracker deltaTracker, CallbackInfo callback) {
        Camera camera = (Camera) (Object) this;
        setRotation(
                camera.yRot() + SkillCameraShake.yawOffset() + SkillCameraAim.yawOffset(),
                camera.xRot() + SkillCameraShake.pitchOffset() + SkillCameraAim.pitchOffset());
    }
}