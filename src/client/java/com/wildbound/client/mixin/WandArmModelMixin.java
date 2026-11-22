package com.wildbound.client.mixin;

import com.wildbound.client.WandAimRenderState;
import com.wildbound.client.WandAimState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person wand aim: the main arm follows the head (pitch/yaw) in a
 * bow-style pose, with the same subtle procedural sway as first person.
 * Only the wand-holding arm is overridden; the off-hand keeps its vanilla pose.
 */
@Mixin(HumanoidModel.class)
public abstract class WandArmModelMixin {
    @Shadow public ModelPart head;
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void wildbound$aimWandArm(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof WandAimRenderState duck) || !duck.wildbound$isWandAiming()) return;
        float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = WandAimState.time(tickDelta);
        float swayYaw = (float) Math.toRadians(WandAimState.swayYaw(time));
        float swayPitch = (float) Math.toRadians(WandAimState.swayPitch(time));
        boolean right = state.mainArm == HumanoidArm.RIGHT;
        ModelPart arm = right ? this.rightArm : this.leftArm;
        float sign = right ? 1f : -1f;
        // Bow-style: raise the arm forward and let it track the head exactly.
        arm.xRot = this.head.xRot - (float) (Math.PI / 2.0) + swayPitch;
        arm.yRot = this.head.yRot + sign * swayYaw;
        arm.zRot = 0f;
    }
}
