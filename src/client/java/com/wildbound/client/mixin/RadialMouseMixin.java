package com.wildbound.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.client.SkillChargeState;
import com.wildbound.client.WildboundClient;
import com.wildbound.item.WildboundItems;
import com.wildbound.network.SkillChargePayload;
import com.wildbound.skills.AimUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the default right mouse button charge and cast the selected radial skill.
 *
 * <p>Hold right-click to charge the wand; release to fire. If no skill is
 * assigned or no wand is held, the click is left untouched so placement,
 * eating and interacting keep working normally.</p>
 *
 * <p>The click is only consumed when the crosshair is <em>not</em> pointing at
 * a block. Looking at a block keeps vanilla behaviour.</p>
 */
@Mixin(targets = "net.minecraft.client.MouseHandler")
public class RadialMouseMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void wildbound$onRadialButton(long window, MouseButtonInfo info, int action, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || minecraft.player == null) return;
        LocalPlayer player = minecraft.player;
        if (player.isSpectator()) return;
        if (info.button() != InputConstants.MOUSE_BUTTON_RIGHT) return;

        // Never steal a right click aimed at a block: that is how blocks are
        // placed and how most block interactions work.
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return;
        }

        // Keep eating, shooting, drinking and other "hold right click" items
        // working. Tools, weapons, blocks and an empty hand still cast/charge.
        if (!player.getMainHandItem().isEmpty()
                && player.getMainHandItem().getUseDuration(player) > 0) {
            return;
        }

        // Only the wand with an assigned radial skill enters charge mode.
        boolean hasWand = player.getMainHandItem().getItem() == WildboundItems.WAND;
        String skillId = WildboundClient.selectedRadialSkill(player);
        boolean canCast = hasWand && skillId != null && !skillId.isEmpty();

        if (action == InputConstants.PRESS) {
            if (canCast && !SkillChargeState.isCharging()) {
                SkillChargeState.start(skillId);
                callback.cancel(); // suppress vanilla use/item while charging
            }
        } else if (action == InputConstants.RELEASE) {
            if (SkillChargeState.isCharging()) {
                float level = SkillChargeState.stop();
                if (canCast && level > 0f) {
                    var aim = AimUtil.lookDir(player);
                    ClientPlayNetworking.send(new SkillChargePayload(skillId, level, aim.x, aim.y, aim.z));
                }
                callback.cancel(); // suppress vanilla use/item on release
            }
        }
    }
}