package com.wildbound.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildbound.client.WandAimContext;
import com.wildbound.client.WandAimState;
import com.wildbound.skills.AimUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person wand aim: rotates the held wand about its grip (pommel) so the
 * crystal tip converges on the crosshair.
 *
 * <p>The solve reads the live pose matrix after vanilla has applied the hand
 * anchor and the item display transform for THIS frame, so any interpolated
 * pitch/bob/equip motion is compensated automatically - the tip stays glued to
 * the screen centre even during fast vertical mouse movement. A subtle
 * procedural sway (+/- ~1.5 deg) is layered on top of the aim pose.</p>
 */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class WandAimItemPoseMixin {
    @Inject(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
            shift = At.Shift.BY, by = 1))
    private void wildbound$convergeWandTip(PoseStack poseStack, SubmitNodeCollector collector,
                                           int light, int overlay, int outline, CallbackInfo ci) {
        if (!WandAimContext.active()) return;
        float blend = WandAimState.blend();
        if (blend <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Model-space locators in centred item space (y up): grip at the pommel, tip at the crystal apex.
        float centre = AimUtil.WAND_MODEL_CENTRE - 0.5f;
        float gripY = AimUtil.WAND_GRIP_MODEL_Y / 16.0f - 0.5f;
        float tipY = AimUtil.WAND_TIP_MODEL_Y / 16.0f - 0.5f;

        Matrix4f matrix = poseStack.last().pose();
        Vector3f grip = matrix.transform(new Vector3f(centre, gripY, centre));
        Vector3f tip = matrix.transform(new Vector3f(centre, tipY, centre));
        Vector3f axis = new Vector3f(tip).sub(grip);
        float length = axis.length();
        if (length < 1.0E-4f) return;

        // The crosshair is the view-space -Z axis. Solve the depth where the rigid
        // wand (pure rotation about the grip) can place its tip exactly on that axis.
        float reach = length * length - grip.x * grip.x - grip.y * grip.y;
        if (reach <= 1.0E-6f) return;
        float depth = -grip.z + (float) Math.sqrt(reach);
        Vector3f to = new Vector3f(-grip.x, -grip.y, -depth - grip.z);
        if (to.lengthSquared() < 1.0E-8f) return;

        Quaternionf correction = new Quaternionf().rotationTo(axis.normalize(), to.normalize());

        // Procedural sway on top of the aim pose, in view space.
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = WandAimState.time(tickDelta);
        float sign = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        float yaw = (float) Math.toRadians(WandAimState.swayYaw(time)) * sign;
        float pitch = (float) Math.toRadians(WandAimState.swayPitch(time));
        Quaternionf total = new Quaternionf().rotationYXZ(yaw, pitch, 0f).mul(correction);
        total = new Quaternionf().slerp(total, blend);

        // Conjugate the view-space rotation into local space and pivot at the grip.
        Quaternionf frame = matrix.getNormalizedRotation(new Quaternionf());
        Quaternionf local = new Quaternionf(frame).invert().mul(total).mul(frame);
        poseStack.translate(centre, gripY, centre);
        poseStack.rotate(local);
        poseStack.translate(-centre, -gripY, -centre);
    }
}
