package com.wildbound;

import com.wildbound.commands.RpgCommands;
import com.wildbound.core.WildboundConfig;
import com.wildbound.player.PlayerRpgInitializer;
import com.wildbound.player.RpgAttachments;
import com.wildbound.realms.RealmRegistry;
import com.wildbound.resources.ResourceRegeneration;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wildbound implements ModInitializer {
    public static final String MOD_ID = "wildbound";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WildboundConfig.validate();
        RpgAttachments.register();
        PlayerRpgInitializer.register();
        ResourceRegeneration.register();
        RpgCommands.register();
        RealmRegistry.all();
        LOGGER.info("WILDBOUND RPG core initialized");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
