package com.wildbound.client.mixin;

import com.wildbound.client.WandAimRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the wand-aim flag to the player render state (duck interface). */
@Mixin(AvatarRenderState.class)
public abstract class WandAimRenderStateMixin implements WandAimRenderState {
    @Unique
    private boolean wildbound$wandAiming;

    @Unique
    @Override
    public boolean wildbound$isWandAiming() {
        return wildbound$wandAiming;
    }

    @Unique
    @Override
    public void wildbound$setWandAiming(boolean aiming) {
        this.wildbound$wandAiming = aiming;
    }
}
