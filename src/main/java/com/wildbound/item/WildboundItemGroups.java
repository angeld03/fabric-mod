package com.wildbound.item;

import com.wildbound.Wildbound;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** WILDBOUND creative tab, holding every item the mod adds. */
public final class WildboundItemGroups {
    public static final ResourceKey<CreativeModeTab> MAIN =
            ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), Wildbound.id("main"));

    private WildboundItemGroups() {
    }

    public static void register() {
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .icon(() -> new ItemStack(WildboundItems.WAND))
                .title(Component.literal("WILDBOUND"))
                .displayItems((params, output) -> output.accept(WildboundItems.WAND))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN, tab);
    }
}