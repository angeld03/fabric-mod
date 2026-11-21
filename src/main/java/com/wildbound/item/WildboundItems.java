package com.wildbound.item;

import com.wildbound.Wildbound;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Central item registry for WILDBOUND.
 *
 * <p>Items are plain {@link Item} instances registered into the vanilla item
 * registry so they persist, drop and stack like any other item. Model and
 * texture resolution is data-driven in 26.3: the client looks up
 * {@code assets/<namespace>/items/<name>.json} and
 * {@code assets/<namespace>/models/item/<name>.json} automatically.</p>
 */
public final class WildboundItems {
    /** Water wand: a crystal-tipped staff used to channel Agua spells. */
    public static final Item WAND = new WandItem(
            new Item.Properties().setId(ResourceKey.create(BuiltInRegistries.ITEM.key(), Wildbound.id("wand")))
    );

    private WildboundItems() {
    }

    /** Registers all items into the registry. Called from {@link Wildbound#onInitialize}. */
    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, Wildbound.id("wand"), WAND);
    }
}