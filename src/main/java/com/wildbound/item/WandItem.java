package com.wildbound.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The Water Wand: a crystal-tipped staff that channels Agua magic.
 *
 * <p>The cinematic spell animations (water compression, lance formation,
 * launch, and letterbox focus) are played server-side through
 * {@link AquaSkillExecutors} when {@code aqua_water_bolt} is cast. This class
 * only carries the item identity and the flavour text so the wand appears in
 * the player's hand while casting.</p>
 */
public class WandItem extends Item {

    public WandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§bWater Wand"));
        tooltip.accept(Component.literal("§7A crystal-tipped staff that channels Agua magic."));
        tooltip.accept(Component.literal("§8Channels water into a lance before launching it."));
    }
}