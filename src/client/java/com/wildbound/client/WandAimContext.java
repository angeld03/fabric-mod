package com.wildbound.client;

/**
 * Marks the first-person item submission that belongs to the aimed wand.
 *
 * <p>Set around {@code FirstPersonHandsAndItemsRenderer.submitArmWithItem} so
 * the item-pose mixin only ever corrects our wand in first person - never other
 * items, GUI icons or third-person hand layers.</p>
 */
public final class WandAimContext {
    private static boolean active;

    private WandAimContext() {
    }

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    public static boolean active() {
        return active;
    }
}
