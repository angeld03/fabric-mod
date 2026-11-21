package com.wildbound.skills;

import com.wildbound.Wildbound;
import com.wildbound.api.WildboundApi;
import com.wildbound.network.SkillActivateResultPayload;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import com.wildbound.player.RpgAttachments;
import com.wildbound.resources.ResourceService;
import com.wildbound.resources.ResourceType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative skill activation with cooldowns, costs and effects.
 * Each skill ID maps to an {@link SkillExecutor} that implements the actual behavior.
 */
public final class SkillActivationService {
    private static final Map<String, SkillExecutor> EXECUTORS = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();

    /** Charge level (0-1) of the skill currently being executed. Set by the
     *  server before calling an executor, so gacha-style skills can scale
     *  damage/size with how long the player held the button. */
    private static float currentChargeLevel = 0f;

    private SkillActivationService() {
    }

    /** Registers a skill executor. Called during mod initialization. */
    public static void registerExecutor(String skillId, SkillExecutor executor) {
        EXECUTORS.put(skillId, executor);
    }

    public static ActivationResult activate(ServerPlayer player, String skillId) {
        SkillDefinition definition = SkillRegistry.get(skillId).orElse(null);
        if (definition == null) {
            return ActivationResult.fail("unknown_skill", 0f);
        }

        PlayerRpgProfile profile = PlayerRpgProvider.getOrCreate(player);
        if (!profile.hasSkill(skillId)) {
            return ActivationResult.fail("skill_not_unlocked", 0f);
        }

        SkillExecutor executor = EXECUTORS.get(skillId);
        if (executor == null) {
            return ActivationResult.fail("skill_not_implemented", 0f);
        }

        // Check cooldown
        long now = player.level().getGameTime();
        Map<String, Long> playerCooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long lastUse = playerCooldowns.get(skillId);
        float cooldown = executor.getCooldown(definition, profile);
        if (lastUse != null) {
            long elapsedTicks = now - lastUse;
            float elapsedSeconds = elapsedTicks / 20f;
            if (elapsedSeconds < cooldown) {
                return ActivationResult.fail("cooldown", cooldown - elapsedSeconds);
            }
        }

        // Check resource costs
        float manaCost = executor.getManaCost(definition, profile);
        float staminaCost = executor.getStaminaCost(definition, profile);
        if (manaCost > 0 && !ResourceService.canAfford(player, ResourceType.MANA, manaCost)) {
            return ActivationResult.fail("not_enough_mana", 0f);
        }
        if (staminaCost > 0 && !ResourceService.canAfford(player, ResourceType.STAMINA, staminaCost)) {
            return ActivationResult.fail("not_enough_stamina", 0f);
        }

        // Consume resources
        if (manaCost > 0) ResourceService.consume(player, ResourceType.MANA, manaCost);
        if (staminaCost > 0) ResourceService.consume(player, ResourceType.STAMINA, staminaCost);

        // Execute the skill. If an executor fails, refund its resource costs and
        // report the failure instead of silently treating it as a success.
        try {
            executor.execute(player, definition, profile);
        } catch (RuntimeException exception) {
            if (manaCost > 0) ResourceService.restore(player, ResourceType.MANA, manaCost);
            if (staminaCost > 0) ResourceService.restore(player, ResourceType.STAMINA, staminaCost);
            Wildbound.LOGGER.error("Skill executor failed for {}", skillId, exception);
            return ActivationResult.fail("execution_failed", 0f);
        }

        // Only a completed server-side execution grants mastery experience.
        // Cooldown, resource and executor failures return before this point.
        long masteryXp = 10L + Math.round(getChargeLevel() * 5f);
        SkillProgressionService.grantUseExperience(player, skillId, masteryXp);

        // Update cooldown
        playerCooldowns.put(skillId, now);

        return ActivationResult.ok(cooldown);
    }

    /** Activate a skill with a charge level (0-1) from holding the button.
     *  The charge level is exposed to executors via {@link #getChargeLevel()}. */
    public static ActivationResult activateWithCharge(ServerPlayer player, String skillId, float chargeLevel) {
        setChargeLevel(chargeLevel);
        try {
            return activate(player, skillId);
        } finally {
            clearChargeLevel();
        }
    }

    /** Sets the charge level for the currently executing skill (0 = tap, 1 = full). */
    public static void setChargeLevel(float level) {
        currentChargeLevel = Math.max(0f, Math.min(1f, level));
    }

    /** Returns the charge level of the skill currently executing, or 0 if none. */
    public static float getChargeLevel() {
        return currentChargeLevel;
    }

    /** Clears the charge level after execution. */
    public static void clearChargeLevel() {
        currentChargeLevel = 0f;
    }

    public static float getRemainingCooldown(ServerPlayer player, String skillId) {
        Map<String, Long> playerCooldowns = COOLDOWNS.get(player.getUUID());
        if (playerCooldowns == null) return 0f;
        Long lastUse = playerCooldowns.get(skillId);
        if (lastUse == null) return 0f;
        SkillExecutor executor = EXECUTORS.get(skillId);
        if (executor == null) return 0f;
        PlayerRpgProfile profile = PlayerRpgProvider.getOrCreate(player);
        SkillDefinition definition = SkillRegistry.get(skillId).orElse(null);
        if (definition == null) return 0f;
        float cooldown = executor.getCooldown(definition, profile);
        long elapsedTicks = player.level().getGameTime() - lastUse;
        float remaining = cooldown - (elapsedTicks / 20f);
        return Math.max(0f, remaining);
    }

    /** Result of a skill activation attempt. */
    public record ActivationResult(boolean success, String reason, float cooldownRemaining) {
        public static ActivationResult ok(float cooldown) {
            return new ActivationResult(true, "", cooldown);
        }
        public static ActivationResult fail(String reason, float cooldownRemaining) {
            return new ActivationResult(false, reason == null ? "activation_failed" : reason, cooldownRemaining);
        }
    }

    /** Interface for implementing individual skill behaviors. */
    public interface SkillExecutor {
        /** Cooldown in seconds. */
        float getCooldown(SkillDefinition definition, PlayerRpgProfile profile);

        /** Mana cost. */
        default float getManaCost(SkillDefinition definition, PlayerRpgProfile profile) {
            return 0f;
        }

        /** Stamina cost. */
        default float getStaminaCost(SkillDefinition definition, PlayerRpgProfile profile) {
            return 0f;
        }

        /** Execute the skill effect. */
        void execute(ServerPlayer player, SkillDefinition definition, PlayerRpgProfile profile);
    }

    // ------------------------------------------------------------------
    // Helper methods for executors
    // ------------------------------------------------------------------

    protected static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    protected static void spawnParticles(ServerLevel level, SimpleParticleType type, Vec3 pos, int count, double dx, double dy, double dz, double speed) {
        level.sendParticles(type, pos.x, pos.y, pos.z, count, dx, dy, dz, speed);
    }

    protected static Vec3 getLookVector(ServerPlayer player) {
        // Entity#getLookAngle is the authoritative view direction. Recomputing
        // it by hand is easy to get wrong (a swapped X/Z axis makes every
        // projectile fly 90 degrees away from the crosshair).
        return player.getLookAngle();
    }

    protected static Optional<LivingEntity> getTargetEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = getLookVector(player);
        Vec3 end = eye.add(look.scale(range));
        AABB box = new AABB(eye, end).inflate(1.0, 1.0, 1.0);
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())
                .stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
    }

    protected static void damageEntity(LivingEntity target, ServerPlayer source, float amount) {
        target.hurt(source.level().damageSources().playerAttack(source), amount);
    }

    protected static void healEntity(LivingEntity target, float amount) {
        target.heal(amount);
    }

    protected static void applyKnockback(LivingEntity target, Vec3 from, float strength) {
        Vec3 dir = target.position().subtract(from).normalize();
        target.push(dir.x * strength, 0.3, dir.z * strength);
    }

    protected static AABB createBox(Vec3 a, Vec3 b, double inflate) {
        double minX = Math.min(a.x, b.x) - inflate;
        double minY = Math.min(a.y, b.y) - inflate;
        double minZ = Math.min(a.z, b.z) - inflate;
        double maxX = Math.max(a.x, b.x) + inflate;
        double maxY = Math.max(a.y, b.y) + inflate;
        double maxZ = Math.max(a.z, b.z) + inflate;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ============================================================
    // Client-side cooldown tracking (for UI)
    // ============================================================
    private static final Map<String, Long> CLIENT_COOLDOWNS = new HashMap<>();
    private static final Map<String, Float> CLIENT_MAX_COOLDOWNS = new HashMap<>();

    /** Call from client when skill activation packet is sent. */
    public static void onClientActivate(String skillId, float cooldown) {
        CLIENT_COOLDOWNS.put(skillId, System.currentTimeMillis());
        CLIENT_MAX_COOLDOWNS.put(skillId, cooldown);
    }

    /** Get remaining cooldown in seconds for client UI. */
    public static float getClientCooldown(String skillId) {
        Long start = CLIENT_COOLDOWNS.get(skillId);
        Float max = CLIENT_MAX_COOLDOWNS.get(skillId);
        if (start == null || max == null) return 0f;
        float elapsed = (System.currentTimeMillis() - start) / 1000f;
        return Math.max(0f, max - elapsed);
    }

    public static float getMaxCooldown(String skillId) {
        return CLIENT_MAX_COOLDOWNS.getOrDefault(skillId, 0f);
    }

    public static float getManaCost(SkillDefinition def) {
        SkillExecutor executor = EXECUTORS.get(def.id());
        if (executor == null) return 0f;
        // Use a dummy profile for client-side cost display
        return executor.getManaCost(def, com.wildbound.player.PlayerRpgProfile.empty());
    }

    public static float getCooldown(SkillDefinition def) {
        SkillExecutor executor = EXECUTORS.get(def.id());
        if (executor == null) return 0f;
        return executor.getCooldown(def, com.wildbound.player.PlayerRpgProfile.empty());
    }
}