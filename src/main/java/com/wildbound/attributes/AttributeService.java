package com.wildbound.attributes;

import com.wildbound.core.WildboundConfig;
import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.player.AttributeContainer;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative attribute operations. */
public final class AttributeService {
    private AttributeService() {
    }

    public static int getAttribute(ServerPlayer player, RpgAttribute attribute) {
        return PlayerRpgProvider.getOrCreate(player).attributes().get(attribute);
    }

    public static int getAvailableAttributePoints(ServerPlayer player) {
        return PlayerRpgProvider.getOrCreate(player).attributePoints();
    }

    public static boolean increaseAttribute(ServerPlayer player, RpgAttribute attribute, int amount) {
        if (amount <= 0) return false;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        if (profile.attributePoints() < amount) return false;
        int current = profile.attributes().get(attribute);
        if (current + amount > WildboundConfig.maxAttribute) return false;
        AttributeContainer attributes = profile.attributes().with(attribute, current + amount);
        RpgPersistenceManager.save(player, copy(profile, attributes, profile.attributePoints() - amount));
        return true;
    }

    public static boolean decreaseAttribute(ServerPlayer player, RpgAttribute attribute, int amount) {
        if (amount <= 0) return false;
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        int current = profile.attributes().get(attribute);
        if (current - amount < WildboundConfig.baseAttribute) return false;
        AttributeContainer attributes = profile.attributes().with(attribute, current - amount);
        // Refunded points can be spent again; they never exceed what was invested.
        RpgPersistenceManager.save(player, copy(profile, attributes, profile.attributePoints() + amount));
        return true;
    }

    public static boolean setAttribute(ServerPlayer player, RpgAttribute attribute, int value) {
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);
        int current = profile.attributes().get(attribute);
        int delta = value - current;
        if (delta == 0) return true;
        if (delta > 0) return increaseAttribute(player, attribute, delta);
        return decreaseAttribute(player, attribute, -delta);
    }

    private static PlayerRpgProfile copy(PlayerRpgProfile p, AttributeContainer attributes, int points) {
        return new PlayerRpgProfile(p.schemaVersion(), p.uuid(), p.level(), p.experience(),
                p.totalExperience(), points, p.skillPoints(), attributes, p.mana(), p.stamina(),
                p.unlockedSkills(), p.unlockedRealms(), p.stats());
    }
}
