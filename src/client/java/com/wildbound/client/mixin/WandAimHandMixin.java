package com.wildbound.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildbound.client.WandAimContext;
import com.wildbound.client.WandAimState;
import com.wildbound.item.WildboundItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scopes the wand aim correction to the exact first-person submission of our
 * wand. The flag is raised at HEAD and cleared at every RETURN, so it can never
 * leak into another item or render context.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class WandAimHandMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void wildbound$beginWandAim(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                        float partialTick, float pitch, InteractionHand hand, float swingProgress,
                                        ItemStack stack, float equipProgress, PoseStack poseStack,
                                        SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (stack.is(WildboundItems.WAND) && WandAimState.poseActive(Minecraft.getInstance().player)) {
            WandAimContext.begin();
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void wildbound$endWandAim(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                      float partialTick, float pitch, InteractionHand hand, float swingProgress,
                                      ItemStack stack, float equipProgress, PoseStack poseStack,
                                      SubmitNodeCollector collector, int light, CallbackInfo ci) {
        WandAimContext.end();
    }
}
