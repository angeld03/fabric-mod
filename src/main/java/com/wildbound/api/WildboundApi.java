package com.wildbound.api;

import com.wildbound.attributes.AttributeService;
import com.wildbound.attributes.DerivedStats;
import com.wildbound.attributes.DerivedStatsCalculator;
import com.wildbound.attributes.RpgAttribute;
import com.wildbound.progression.ExperienceService;
import com.wildbound.progression.LevelUpResult;
import com.wildbound.realms.RealmProgressionService;
import com.wildbound.resources.ResourceService;
import com.wildbound.resources.ResourceType;
import com.wildbound.skills.SkillProgressionService;
import com.wildbound.skills.SkillUnlockResult;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public integration contract. Other modules should only use this class,
 * never the internal services directly.
 */
public final class WildboundApi {
    private WildboundApi() {
    }

    public static LevelUpResult grantExperience(ServerPlayer player, long amount, ExperienceSource source) {
        return ExperienceService.grantExperience(player, amount, source);
    }

    public static int getPlayerLevel(ServerPlayer player) {
        return com.wildbound.player.PlayerRpgProvider.getOrCreate(player).level();
    }

    public static int getAttribute(ServerPlayer player, RpgAttribute attribute) {
        return AttributeService.getAttribute(player, attribute);
    }

    public static int getAvailableSkillPoints(ServerPlayer player) {
        return com.wildbound.player.PlayerRpgProvider.getOrCreate(player).skillPoints();
    }

    public static int getAvailableAttributePoints(ServerPlayer player) {
        return AttributeService.getAvailableAttributePoints(player);
    }

    public static boolean consumeMana(ServerPlayer player, float amount) {
        return ResourceService.consume(player, ResourceType.MANA, amount);
    }

    public static boolean restoreMana(ServerPlayer player, float amount) {
        return ResourceService.restore(player, ResourceType.MANA, amount);
    }

    public static boolean consumeStamina(ServerPlayer player, float amount) {
        return ResourceService.consume(player, ResourceType.STAMINA, amount);
    }

    public static SkillUnlockResult unlockSkill(ServerPlayer player, String skillId) {
        return SkillProgressionService.unlock(player, skillId);
    }

    public static boolean unlockRealm(ServerPlayer player, String realmId) {
        return RealmProgressionService.unlock(player, realmId);
    }

    public static DerivedStats getDerivedStats(ServerPlayer player) {
        return DerivedStatsCalculator.calculate(player);
    }

    public static int getExperienceSourceCount() {
        return ExperienceSource.values().length;
    }
}
