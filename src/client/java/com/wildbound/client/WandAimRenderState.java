package com.wildbound.client;

/** Duck interface mixed into AvatarRenderState to flag the wand aim arm pose. */
public interface WandAimRenderState {
    boolean wildbound$isWandAiming();

    void wildbound$setWandAiming(boolean aiming);
}
