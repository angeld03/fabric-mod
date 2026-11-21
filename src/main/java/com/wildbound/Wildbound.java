package com.wildbound;

import com.wildbound.commands.RpgCommands;
import com.wildbound.core.WildboundConfig;
import com.wildbound.item.WildboundItemGroups;
import com.wildbound.item.WildboundItems;
import com.wildbound.network.SkillNetworking;
import com.wildbound.player.PlayerRpgInitializer;
import com.wildbound.player.RpgAttachments;
import com.wildbound.realms.RealmRegistry;
import com.wildbound.resources.ResourceRegeneration;
import com.wildbound.skills.AquaSkillExecutors;
import com.wildbound.skills.SkillCatalog;
import com.wildbound.skills.SkillScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        WildboundItems.register();
        WildboundItemGroups.register();
        SkillCatalog.initialize();
        AquaSkillExecutors.registerAll();
        SkillNetworking.register();
        PlayerRpgInitializer.register();
        ResourceRegeneration.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> SkillScheduler.tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SkillScheduler.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SkillScheduler.cancel(handler.player.getUUID()));
        RpgCommands.register();
        RealmRegistry.all();
        LOGGER.info("WILDBOUND RPG core initialized");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
