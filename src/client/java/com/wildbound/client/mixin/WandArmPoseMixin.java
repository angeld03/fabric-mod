package com.wildbound.client.mixin;

import com.wildbound.client.WandAimRenderState;
import com.wildbound.client.WandAimState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flags the player render state when the wand is held so the model poses the aiming arm. */
@Mixin(AvatarRenderer.class)
public abstract class WandArmPoseMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void wildbound$flagWandAim(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        boolean aiming = WandAimState.poseActive(entity) && !state.isSpectator;
        ((WandAimRenderState) state).wildbound$setWandAiming(aiming);
    }
}
