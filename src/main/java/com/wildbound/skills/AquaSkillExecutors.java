package com.wildbound.skills;

import com.wildbound.network.SkillCameraAimPayload;
import com.wildbound.network.SkillCameraShakePayload;
import com.wildbound.player.PlayerRpgProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Cinematic executors for the ten Agua (Water) skills.
 *
 * <p>Every effect is spread across real server ticks through
 * {@link SkillScheduler}. Nothing here calls {@code Thread.sleep} or spawns a
 * whole animation inside a single tick, so the server stays responsive and the
 * client can actually see the animation. All directions come from
 * {@link ServerPlayer#getLookAngle()} at cast time, which means every skill
 * travels exactly where the player is aiming.</p>
 */
public final class AquaSkillExecutors {
    private static final Random RNG = new Random();

    private AquaSkillExecutors() {
    }

    /** Registers all 10 Agua skill executors. */
    public static void registerAll() {
        registerWaterBolt();
        registerHealingStream();
        registerAquaWhip();
        registerBubbleShield();
        registerTidalWave();
        registerAquaDash();
        registerWhirlpool();
        registerPressureLance();
        registerTsunami();
        registerLeviathansWrath();
    }

    // ============================================================
    // TIER 1 - WATER BOLT (cinematic charge + 3 animated lances)
    // ============================================================
    private static void registerWaterBolt() {
        SkillActivationService.registerExecutor("aqua_water_bolt", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 2.5f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return Math.max(5f, 8f - (profile.getSkillProgress(def.id()).rank() - 1) * 0.25f); }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 side = side(look);
                Vec3 up = up(look);
                Vec3 origin = player.getEyePosition().add(look.scale(1.0));

                float charge = SkillActivationService.getChargeLevel();
                float chargeMult = 1.0f + charge;
                var mastery = profile.getSkillProgress(def.id());
                int skillRank = mastery.rank();
                boolean hydrostaticCore = mastery.upgrades().contains("hydrostatic_core");
                boolean frostbiteCurrent = mastery.upgrades().contains("frostbite_current");
                boolean tidalPiercer = mastery.upgrades().contains("tidal_piercer");
                float masteryDamageMultiplier = 1.0f + (skillRank - 1) * 0.025f
                        + (hydrostaticCore ? 0.10f : 0f);
                double rangeMasteryMultiplier = tidalPiercer ? 1.18 : 1.0;

                // Each lance: [range, damage, burst size, shaft thickness]
                double[] lanceRanges  = {
                        10.0 * chargeMult * rangeMasteryMultiplier,
                        16.0 * chargeMult * rangeMasteryMultiplier,
                        24.0 * chargeMult * rangeMasteryMultiplier
                };
                int[]    lanceDamage  = { 8, 12, 18 };
                int[]    lanceBursts  = { 22, 36, 54 };
                double[] lanceWidth   = { 0.55, 0.8, 1.1 };

                final int CHARGE_TICKS   = 12;
                final int FORM_TICKS     = 6;   // lance materializes at the wand
                final int FLIGHT_TICKS   = 10;  // lance TRAVELS to the target
                final int GAP_TICKS      = 3;   // pause between lances
                final int LANCE_CYCLE    = FORM_TICKS + FLIGHT_TICKS + GAP_TICKS;
                final int TRAIL_LEN      = 10;

                // === PHASE 0: CHARGE — water spirals inward into a dense glowing core ===
                sendCameraFocus(player, look, origin, 0, CHARGE_TICKS, 0.15f, 1.0f);
                SkillScheduler.run(player, 0, 1, CHARGE_TICKS, step -> {
                    if (player.isRemoved()) return;
                    double t = step / (double) CHARGE_TICKS;
                    double radius = 1.0 * (1.0 - t * 0.65);
                    for (int i = 0; i < 10; i++) {
                        double ang = step * 1.3 + i * (Math.PI * 2 / 10);
                        Vec3 p = origin.add(Math.cos(ang) * radius, Math.sin(ang) * radius * 0.6,
                                Math.sin(ang + step * 0.4) * radius);
                        particle(level, ParticleTypes.BUBBLE, p, 1, 0.02, 0.02);
                        if (i % 2 == 0) particle(level, ParticleTypes.SPLASH, p, 1, 0.01, 0.01);
                    }
                    particle(level, ParticleTypes.SPLASH, origin, 3, 0.03, 0.03);
                    particle(level, ParticleTypes.ELECTRIC_SPARK, origin, 2, 0.06, 0.03);
                    if (step % 3 == 0) ring(level, origin, side, up, 0.4 + t * 0.5, 14, ParticleTypes.BUBBLE, 0.02, 0.01);
                    if (step % 4 == 0) SkillActivationService.playSound(level, origin, SoundEvents.BUBBLE_POP, 0.35f, 1.2f + step * 0.07f);
                });

                // === PHASES 1-3: one fully animated lance per cycle ===
                for (int lanceIndex = 0; lanceIndex < 3; lanceIndex++) {
                    final int li = lanceIndex;
                    int startTick = CHARGE_TICKS + lanceIndex * LANCE_CYCLE;
                    double range = lanceRanges[li];
                    int dmg = Math.round(lanceDamage[li] * chargeMult * masteryDamageMultiplier);
                    int burst = lanceBursts[li];
                    double shaft = lanceWidth[li];

                    // 1) FORM: the lance builds at the wand tip, segment by segment.
                    SkillScheduler.run(player, startTick, 1, FORM_TICKS, t -> {
                        if (player.isRemoved()) return;
                        double form = (t + 1) / (double) FORM_TICKS; // 0..1
                        double len = 0.5 + form * 2.3;               // grows outward from wand
                        Vec3 tip = origin.add(look.scale(len));

                        for (int i = 0; i < len * 6; i++) {
                            double seg = i / (double) Math.max(1, (int) (len * 6));
                            Vec3 p = origin.add(look.scale(seg * len));
                            particle(level, ParticleTypes.SPLASH, p, 1, 0.01, 0.01);
                        }
                        // Swirling helix that tightens as it forms.
                        for (int e = 0; e < 4; e++) {
                            double a = t * 1.8 + e * (Math.PI / 2);
                            Vec3 edge = tip.add(side.scale(Math.cos(a) * shaft))
                                    .add(up.scale(Math.sin(a) * shaft));
                            particle(level, ParticleTypes.BUBBLE, edge, 1, 0.02, 0.02);
                        }
                        if (t % 2 == 0) ring(level, tip, side, up, shaft + 0.25, 12, ParticleTypes.ELECTRIC_SPARK, 0.03, 0.02);
                        particle(level, ParticleTypes.ELECTRIC_SPARK, tip, 3, 0.08, 0.04);

                        sendCameraTrack(player, look, tip, FORM_TICKS, 0.12f, true);
                        if (t == 0) SkillActivationService.playSound(level, origin, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.7f, 1.0f + li * 0.15f);
                    });

                    // 2) FLIGHT: a real projectile travels the whole path with a dense wake.
                    SkillScheduler.run(player, startTick + FORM_TICKS, 1, FLIGHT_TICKS, f -> {
                        if (player.isRemoved()) return;
                        double progress = (f + 1) / (double) FLIGHT_TICKS; // 0..1 eased below
                        double eased = progress * progress * (3 - 2 * progress); // smoothstep
                        Vec3 head = origin.add(look.scale(eased * range));

                        // Dense tapered wake: newest at head, older fades behind.
                        for (int s = 0; s < TRAIL_LEN; s++) {
                            double back = s * 0.55;
                            Vec3 w = head.add(look.scale(-back));
                            double fade = 1.0 - s / (double) TRAIL_LEN;
                            int count = s < 3 ? 2 : 1;
                            particle(level, ParticleTypes.SPLASH, w, count, 0.02 * fade, 0.01);
                            particle(level, ParticleTypes.BUBBLE, w, 1, 0.015 * fade, 0.0);
                            if (s % 2 == 0) particle(level, ParticleTypes.ELECTRIC_SPARK, w, 1, 0.05 * fade, 0.02);
                            // Spiraling water sheath around the shaft.
                            double a = f * 1.4 + s * 0.6;
                            Vec3 edge = w.add(side.scale(Math.cos(a) * shaft * fade))
                                    .add(up.scale(Math.sin(a) * shaft * fade));
                            particle(level, ParticleTypes.SPLASH, edge, 1, 0.02, 0.02);
                        }

                        // Glowing tip core + focus ring travels with the lance.
                        particle(level, ParticleTypes.ELECTRIC_SPARK, head, 5, 0.1, 0.05);
                        particle(level, ParticleTypes.SPLASH, head, 2, 0.06, 0.06);
                        ring(level, head, side, up, shaft + 0.5, 16, ParticleTypes.BUBBLE, 0.03, 0.02);

                        // Camera tracks the flying head.
                        sendCameraTrack(player, look, head, 2, 0.3f, true);

                        // Hit detection travels WITH the projectile (only once per target).
                        AABB hitBox = new AABB(head, head).inflate(shaft + 0.9);
                        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                                e -> e != player && e.isAlive())) {
                            float d = dmg + profile.level() * 0.5f;
                            SkillActivationService.damageEntity(target, player, d);
                            if (frostbiteCurrent) {
                                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), player);
                                particle(level, ParticleTypes.SNOWFLAKE,
                                        target.position().add(0, target.getBbHeight() * 0.65, 0), 8, 0.25, 0.3);
                            }
                            SkillActivationService.applyKnockback(target, player.position(), 1.8f + li * 0.3f);
                            spawnDamageNumber(level, target.position().add(0, 2.4, 0), (int) d);
                        }

                        // Arrived: detonate.
                        if (f == FLIGHT_TICKS - 1) {
                            Vec3 end = origin.add(look.scale(range));
                            detonateLance(level, player, end, side, up, burst, shaft * 3.0, dmg, li);
                            SkillActivationService.playSound(level, end, SoundEvents.GENERIC_SPLASH, 1.2f + li * 0.25f, 0.7f);
                            SkillActivationService.playSound(level, end, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8f, 1.4f);
                            SkillActivationService.playSound(level, end, SoundEvents.WATER_AMBIENT, 0.7f + li * 0.2f, 1.3f);
                            sendCameraKick(player, look, 8, 0.5f);
                            sendCameraImpact(player, 4);
                            triggerScreenShake(level, end, 6 + li * 2, 0.5f);
                            triggerScreenShake(level, end, 3 + li, 1.0f);
                            sendCameraRecoil(player, look, 7, 0.28f);
                        }
                    });
                }
            }
        });
    }

    /** Dramatic impact when a lance reaches its target. */
    private static void detonateLance(ServerLevel level, ServerPlayer player, Vec3 end, Vec3 side, Vec3 up,
                                      int burst, double radius, int dmg, int lanceIndex) {
        impactBurst(level, end, side, up, burst, radius);
        particle(level, ParticleTypes.SPLASH, end, 16 + lanceIndex * 6, 0.22, 0.22);
        particle(level, ParticleTypes.BUBBLE, end, 10 + lanceIndex * 3, 0.14, 0.14);
        particle(level, ParticleTypes.ELECTRIC_SPARK, end, 10, 0.22, 0.12);
        // Expanding shockwave rings on the ground.
        ring(level, end.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius * 2.2, 36, ParticleTypes.SPLASH, 0.1, 0.18);
        ring(level, end.add(0, 0.15, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius * 1.6, 26, ParticleTypes.BUBBLE, 0.05, 0.1);
        // Vertical geyser column.
        for (int i = 0; i < 18; i++) {
            Vec3 g = end.add(RNG.nextGaussian() * radius * 0.4, RNG.nextDouble() * 3.0, RNG.nextGaussian() * radius * 0.4);
            particle(level, ParticleTypes.SPLASH, g, 1, 0.08, 0.15);
        }
    }

    // ============================================================
    // TIER 2 - HEALING STREAM
    // ============================================================
    private static void registerHealingStream() {
        SkillActivationService.registerExecutor("aqua_healing_stream", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 12f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 8f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                SkillActivationService.playSound(level, player.position(), SoundEvents.BEACON_AMBIENT, 0.5f, 1.4f);
                SkillActivationService.playSound(level, player.position(), SoundEvents.PLAYER_SPLASH, 0.4f, 1.8f);

                int steps = 18;
                SkillScheduler.run(player, 0, 3, steps, step -> {
                    if (!player.isAlive() || player.isRemoved()) return;
                    Vec3 base = player.position();
                    double t = step * 0.42;

                    for (int column = 0; column < 2; column++) {
                        for (int i = 0; i < 8; i++) {
                            double a = t + i * Math.PI * 2 / 8 + column * Math.PI;
                            double r = 0.9 + Math.sin(t + i * 0.5) * 0.25;
                            Vec3 p = base.add(Math.cos(a) * r, 0.35 + i * 0.18, Math.sin(a) * r);
                            particle(level, ParticleTypes.BUBBLE, p, 1, 0, 0);
                            if (i % 3 == 0) particle(level, ParticleTypes.HAPPY_VILLAGER, p, 1, 0, 0);
                            if (i % 4 == 0) particle(level, ParticleTypes.ENCHANT, p, 1, 0, 0);
                        }
                    }

                    if (step % 3 == 0) {
                        ring(level, base.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                2.3 + Math.sin(t) * 0.3, 28, ParticleTypes.ENCHANT, 0, 0);
                    }

                    if (step % 2 == 0) {
                        float heal = 1.1f + profile.level() * 0.07f;
                        SkillActivationService.healEntity(player, heal);
                        particle(level, ParticleTypes.HEART, player.position().add(0, 2.3, 0), 1, 0.2, 0.02);
                    }

                    if (step == steps - 1) {
                        ring(level, base.add(0, 0.2, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                2.6, 40, ParticleTypes.HEART, 0, 0);
                        SkillActivationService.playSound(level, base, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 2.0f);
                        SkillActivationService.playSound(level, base, SoundEvents.PLAYER_SPLASH, 0.5f, 1.7f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 3 - AQUA WHIP
    // ============================================================
    private static void registerAquaWhip() {
        SkillActivationService.registerExecutor("aqua_aqua_whip", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 4f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 6f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 side = side(look);
                Vec3 up = up(look);
                Vec3 origin = player.getEyePosition().add(look.scale(0.8));
                double range = 8.0;

                SkillActivationService.playSound(level, origin, SoundEvents.TRIDENT_THROW.value(), 0.7f, 1.6f);
                SkillActivationService.playSound(level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.1f);

                Set<UUID> hit = new HashSet<>();
                for (double d = 1.0; d <= range; d += 0.5) {
                    Vec3 point = origin.add(look.scale(d));
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(point, point).inflate(1.1), e -> e != player && e.isAlive())) {
                        if (!hit.add(target.getUUID())) continue;
                        float damage = 10f + profile.level() * 0.5f;
                        SkillActivationService.damageEntity(target, player, damage);
                        SkillActivationService.applyKnockback(target, player.position(), 2.2f);
                        spawnDamageNumber(level, target.position().add(0, 2.2, 0), (int) damage);
                        impactBurst(level, target.position().add(0, 1.0, 0), side, up, 22, 1.0);
                        triggerScreenShake(level, target.position(), 2, 0.2f);
                    }
                }

                SkillScheduler.run(player, 0, 1, 11, step -> {
                    if (player.isRemoved()) return;
                    double progress = step / 10.0;
                    double reach = 1.2 + progress * range;
                    double swing = Math.sin(progress * Math.PI) * 1.4;
                    Vec3 tip = origin.add(look.scale(reach))
                            .add(side.scale(swing))
                            .add(up.scale(Math.cos(progress * Math.PI) * 0.45));

                    for (int i = 0; i < 4; i++) {
                        Vec3 segment = tip.subtract(look.scale(i * 0.35));
                        particle(level, ParticleTypes.SPLASH, segment, 1, 0.05, 0.03);
                        particle(level, ParticleTypes.BUBBLE, segment, 1, 0.03, 0.01);
                    }
                    if (step % 3 == 0) particle(level, ParticleTypes.ELECTRIC_SPARK, tip, 2, 0.15, 0.15);
                    if (step == 10) {
                        impactBurst(level, tip, side, up, 20, 1.0);
                        SkillActivationService.playSound(level, tip, SoundEvents.GENERIC_SPLASH, 0.9f, 0.7f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 4 - BUBBLE SHIELD
    // ============================================================
    private static void registerBubbleShield() {
        SkillActivationService.registerExecutor("aqua_bubble_shield", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 20f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 10f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                int duration = 160;

                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0, false, false, true));

                SkillActivationService.playSound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 0.9f, 1.4f);
                SkillActivationService.playSound(level, player.position(), SoundEvents.CONDUIT_AMBIENT, 0.5f, 1.0f);

                int steps = duration / 4;
                SkillScheduler.run(player, 0, 4, steps, step -> {
                    if (!player.isAlive() || player.isRemoved()) return;
                    Vec3 center = player.position().add(0, 1.0, 0);
                    double t = step * 0.22;
                    double radius = 1.65 + Math.sin(t) * 0.12;

                    sphere(level, center, radius, 30, ParticleTypes.BUBBLE, 0.01);
                    if (step % 2 == 0) sphere(level, center, radius + 0.35, 18, ParticleTypes.SPLASH, 0.01);
                    if (step % 5 == 0) {
                        ring(level, player.position().add(0, 0.15, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                2.2, 24, ParticleTypes.ENCHANT, 0, 0);
                    }

                    if (step == steps - 1) {
                        for (int i = 0; i < 60; i++) {
                            double a = i * Math.PI * 2 / 60;
                            double r = 2.4 * (1 - i / 60.0);
                            Vec3 p = center.add(Math.cos(a) * r, Math.sin(a) * r * 0.6, Math.sin(a) * r);
                            particle(level, ParticleTypes.BUBBLE, p, 1, 0.08, 0.05);
                            particle(level, ParticleTypes.REVERSE_PORTAL, p, 1, 0.05, 0.02);
                        }
                        SkillActivationService.playSound(level, center, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.3f);
                        SkillActivationService.playSound(level, center, SoundEvents.PLAYER_SPLASH, 0.5f, 1.6f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 5 - TIDAL WAVE
    // ============================================================
    private static void registerTidalWave() {
        SkillActivationService.registerExecutor("aqua_tidal_wave", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 15f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 12f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 side = side(look);
                Vec3 origin = player.position().add(look.scale(1.5)).add(0, 0.4, 0);

                SkillActivationService.playSound(level, origin, SoundEvents.CONDUIT_ACTIVATE, 1.0f, 0.7f);
                SkillActivationService.playSound(level, origin, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8f, 0.5f);

                int steps = 18;
                double travel = 16.0;
                Set<UUID> hit = new HashSet<>();

                SkillScheduler.run(player, 0, 1, steps, step -> {
                    if (player.isRemoved()) return;
                    double progress = step / (double) (steps - 1);
                    Vec3 center = origin.add(look.scale(progress * travel));
                    double width = 2.2 + Math.sin(progress * Math.PI) * 2.4;
                    double height = 1.2 + Math.sin(progress * Math.PI) * 1.7;

                    for (int i = 0; i < 26; i++) {
                        double a = i / 25.0 * Math.PI;
                        Vec3 p = center.add(side.scale(Math.cos(a) * width)).add(0, Math.sin(a) * height, 0);
                        particle(level, ParticleTypes.SPLASH, p, 1, 0.18, 0.15);
                        if (i % 3 == 0) particle(level, ParticleTypes.BUBBLE, p, 1, 0.1, 0.05);
                        if (i % 5 == 0) particle(level, ParticleTypes.WHITE_ASH, p, 1, 0.2, 0.12);
                    }

                    for (int i = 0; i < 12; i++) {
                        double offset = (RNG.nextDouble() * 2 - 1) * width;
                        Vec3 p = center.add(side.scale(offset)).add(0, 0.15, 0);
                        particle(level, ParticleTypes.SPLASH, p, 1, 0.12, 0.1);
                    }

                    AABB box = new AABB(center.x - width, center.y - 1.5, center.z - width,
                            center.x + width, center.y + height + 1.5, center.z + width);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != player && e.isAlive())) {
                        if (!hit.add(target.getUUID())) continue;
                        float damage = 14f + profile.level() * 0.6f;
                        SkillActivationService.damageEntity(target, player, damage);
                        Vec3 knockback = target.position().subtract(player.position()).normalize().scale(2.6);
                        target.push(knockback.x, 0.8, knockback.z);
                        spawnDamageNumber(level, target.position().add(0, 2.4, 0), (int) damage);
                        triggerScreenShake(level, target.position(), 2, 0.2f);
                    }

                    if (step % 3 == 0) {
                        SkillActivationService.playSound(level, center, SoundEvents.GENERIC_SPLASH,
                                0.8f, 0.5f + (float) progress * 0.4f);
                    }

                    if (step == steps - 1) {
                        Vec3 end = origin.add(look.scale(travel));
                        impactBurst(level, end.add(0, 1.0, 0), side, up(look), 60, 3.0);
                        SkillActivationService.playSound(level, end, SoundEvents.GENERIC_SPLASH, 1.4f, 0.5f);
                        SkillActivationService.playSound(level, end, SoundEvents.CONDUIT_DEACTIVATE, 0.8f, 0.6f);
                        triggerScreenShake(level, end, 6, 0.4f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 6 - AQUA DASH
    // ============================================================
    private static void registerAquaDash() {
        SkillActivationService.registerExecutor("aqua_aqua_dash", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 8f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 8f; }
            @Override public float getStaminaCost(SkillDefinition def, PlayerRpgProfile profile) { return 6f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 start = player.position();
                double distance = Math.min(12.0, 8.5 + profile.level() * 0.15);
                Vec3 end = safeDashEnd(level, player, start, look, distance);

                SkillActivationService.playSound(level, start, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0f, 1.4f);
                SkillActivationService.playSound(level, start, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.3f);

                Set<UUID> hit = new HashSet<>();
                double travelled = start.distanceTo(end);
                for (double d = 0; d <= travelled; d += 0.6) {
                    Vec3 point = start.add(look.scale(d)).add(0, 0.9, 0);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(point, point).inflate(1.3), e -> e != player && e.isAlive())) {
                        if (!hit.add(target.getUUID())) continue;
                        float damage = 8f + profile.level() * 0.4f;
                        SkillActivationService.damageEntity(target, player, damage);
                        SkillActivationService.applyKnockback(target, start, 2.0f);
                        spawnDamageNumber(level, target.position().add(0, 2.2, 0), (int) damage);
                    }
                }

                player.teleportTo(end.x, end.y, end.z);
                player.resetFallDistance();
                player.setDeltaMovement(Vec3.ZERO);

                SkillScheduler.run(player, 0, 1, 12, step -> {
                    if (player.isRemoved()) return;
                    double progress = step / 11.0;
                    Vec3 pos = start.add(end.subtract(start).scale(progress)).add(0, 0.9, 0);
                    double radius = 0.75 * (1.0 - progress * 0.7);
                    ring(level, pos, side(look), up(look), radius, 12, ParticleTypes.BUBBLE, 0.03, 0.02);
                    if (step % 3 == 0) {
                        ring(level, pos, side(look), up(look), radius + 0.25, 8, ParticleTypes.SPLASH, 0.03, 0.02);
                    }
                    if (step == 11) {
                        impactBurst(level, end.add(0, 1.0, 0), side(look), up(look), 36, 1.8);
                        SkillActivationService.playSound(level, end, SoundEvents.PLAYER_SPLASH, 1.0f, 1.6f);
                        SkillActivationService.playSound(level, end, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.2f);
                        triggerScreenShake(level, end, 4, 0.3f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 7 - WHIRLPOOL
    // ============================================================
    private static void registerWhirlpool() {
        SkillActivationService.registerExecutor("aqua_whirlpool", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 18f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 14f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 center = groundBelow(level, player.getEyePosition().add(look.scale(6.0)));

                SkillActivationService.playSound(level, center, SoundEvents.CONDUIT_AMBIENT, 1.0f, 0.8f);
                SkillActivationService.playSound(level, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.6f, 0.5f);

                int steps = 36;
                SkillScheduler.run(player, 0, 4, steps, step -> {
                    if (player.isRemoved()) return;
                    double t = step * 0.24;

                    for (int arm = 0; arm < 3; arm++) {
                        for (int i = 0; i < 18; i++) {
                            double p = i / 17.0;
                            double r = 0.4 + p * 5.0;
                            double a = t + p * Math.PI * 3 + arm * Math.PI * 2 / 3;
                            Vec3 pos = center.add(Math.cos(a) * r,
                                    0.4 + Math.sin(t + p * 3) * 0.5,
                                    Math.sin(a) * r);
                            particle(level, ParticleTypes.BUBBLE, pos, 1, 0, 0);
                            if (i % 4 == 0) particle(level, ParticleTypes.SPLASH, pos, 1, 0, 0);
                        }
                    }

                    ring(level, center.add(0, 0.9, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                            0.85, 14, ParticleTypes.ELECTRIC_SPARK, 0, 0.1);

                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(center.x - 6, center.y - 2.5, center.z - 6,
                                    center.x + 6, center.y + 4, center.z + 6),
                            e -> e != player && e.isAlive())) {
                        Vec3 toCenter = center.subtract(target.position());
                        double dist = toCenter.length();
                        if (dist < 0.2 || dist > 6.0) continue;
                        Vec3 pull = toCenter.normalize().scale(0.32);
                        target.push(pull.x, 0.07, pull.z);
                        if (step % 6 == 0) {
                            float damage = 4f + profile.level() * 0.25f;
                            SkillActivationService.damageEntity(target, player, damage);
                            spawnDamageNumber(level, target.position().add(0, 2.2, 0), (int) damage);
                        }
                    }

                    if (step % 8 == 0) {
                        SkillActivationService.playSound(level, center, SoundEvents.CONDUIT_AMBIENT, 0.7f, 0.9f);
                    }

                    if (step == steps - 1) {
                        for (int i = 0; i < 90; i++) {
                            double a = i * Math.PI * 2 / 90;
                            double r = 5.5 * (1 - i / 90.0);
                            Vec3 p = center.add(Math.cos(a) * r, 0.5 + RNG.nextDouble() * 3.0, Math.sin(a) * r);
                            particle(level, ParticleTypes.SPLASH, p, 1, 0.25, 0.35);
                            particle(level, ParticleTypes.BUBBLE, p, 1, 0.15, 0.2);
                            if (i % 5 == 0) particle(level, ParticleTypes.REVERSE_PORTAL, p, 1, 0.05, 0.05);
                        }
                        SkillActivationService.playSound(level, center, SoundEvents.GENERIC_SPLASH, 1.5f, 0.5f);
                        SkillActivationService.playSound(level, center, SoundEvents.CONDUIT_DEACTIVATE, 1.0f, 0.6f);
                        triggerScreenShake(level, center, 6, 0.5f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 8 - PRESSURE LANCE
    // ============================================================
    private static void registerPressureLance() {
        SkillActivationService.registerExecutor("aqua_pressure_lance", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 10f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 12f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 side = side(look);
                Vec3 up = up(look);
                Vec3 origin = player.getEyePosition().add(look.scale(1.0));
                double range = 30.0;

                SkillActivationService.playSound(level, origin, SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.6f);
                SkillActivationService.playSound(level, origin, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8f, 1.5f);

                Set<UUID> hit = new HashSet<>();
                Vec3 end = origin.add(look.scale(range));
                AABB search = new AABB(origin, end).inflate(2.0);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search,
                        e -> e != player && e.isAlive())) {
                    Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    Vec3 toTarget = center.subtract(origin);
                    double projection = toTarget.dot(look);
                    if (projection < 0 || projection > range) continue;
                    double perpendicular = toTarget.subtract(look.scale(projection)).length();
                    if (perpendicular > 1.6) continue;
                    if (!hit.add(target.getUUID())) continue;

                    float damage = 24f + profile.level() * 0.9f;
                    SkillActivationService.damageEntity(target, player, damage);
                    SkillActivationService.applyKnockback(target, player.position(), 3.5f);
                    spawnDamageNumber(level, target.position().add(0, 2.4, 0), (int) damage);
                    impactBurst(level, center, side, up, 34, 1.3);
                    triggerScreenShake(level, target.position(), 3, 0.25f);
                }

                SkillScheduler.run(player, 0, 1, 10, step -> {
                    if (player.isRemoved()) return;
                    double visible = (step + 1) / 10.0 * range;
                    for (double d = 0; d <= visible; d += 0.6) {
                        Vec3 p = origin.add(look.scale(d));
                        particle(level, ParticleTypes.BUBBLE, p, 1, 0, 0);
                        if (((int) (d * 10)) % 3 == 0) {
                            double a = d * 0.9;
                            Vec3 sheath = p.add(side.scale(Math.cos(a) * 0.35)).add(up.scale(Math.sin(a) * 0.35));
                            particle(level, ParticleTypes.SPLASH, sheath, 1, 0, 0.02);
                        }
                    }
                    if (step == 9) {
                        impactBurst(level, origin.add(look.scale(range)), side, up, 26, 1.5);
                        SkillActivationService.playSound(level, origin, SoundEvents.GENERIC_SPLASH, 1.2f, 0.6f);
                        triggerScreenShake(level, origin.add(look.scale(range * 0.5)), 4, 0.3f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 9 - TSUNAMI
    // ============================================================
    private static void registerTsunami() {
        SkillActivationService.registerExecutor("aqua_tsunami", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 30f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 18f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 center = groundBelow(level, player.position().add(look.scale(3.0)));

                SkillActivationService.playSound(level, center, SoundEvents.CONDUIT_ACTIVATE, 1.6f, 0.5f);
                SkillActivationService.playSound(level, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.35f);
                SkillActivationService.playSound(level, center, SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 0.3f);
                triggerScreenShake(level, center, 5, 0.5f);

                int steps = 30;
                Set<UUID> hit = new HashSet<>();

                SkillScheduler.run(player, 0, 1, steps, step -> {
                    if (player.isRemoved()) return;
                    double radius = 1.0 + step * 0.45;
                    double waveHeight = 1.4 + Math.sin(step / (double) steps * Math.PI) * 2.6;

                    for (int ringIndex = 0; ringIndex < 2; ringIndex++) {
                        double r = radius - ringIndex * 0.9;
                        if (r <= 0.1) continue;
                        int points = 48;
                        for (int i = 0; i < points; i++) {
                            double a = i * Math.PI * 2 / points + step * 0.07;
                            double y = waveHeight * (0.35 + 0.65 * Math.sin(a * 3 + step * 0.22));
                            Vec3 p = center.add(Math.cos(a) * r, y, Math.sin(a) * r);
                            particle(level, ParticleTypes.SPLASH, p, 1, 0.22, 0.22);
                            if (i % 3 == 0) particle(level, ParticleTypes.BUBBLE, p, 1, 0.1, 0.1);
                            if (i % 6 == 0) particle(level, ParticleTypes.WHITE_ASH, p, 1, 0.22, 0.18);
                        }
                    }

                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(center.x - radius - 2, center.y - 3, center.z - radius - 2,
                                    center.x + radius + 2, center.y + 5, center.z + radius + 2),
                            e -> e != player && e.isAlive())) {
                        double distance = target.position().distanceTo(center);
                        if (Math.abs(distance - radius) > 1.7) continue;
                        if (!hit.add(target.getUUID())) continue;
                        float damage = 20f + profile.level() * 0.8f;
                        SkillActivationService.damageEntity(target, player, damage);
                        Vec3 knockback = target.position().subtract(center).normalize().scale(3.2);
                        target.push(knockback.x, 1.0, knockback.z);
                        spawnDamageNumber(level, target.position().add(0, 2.6, 0), (int) damage);
                        triggerScreenShake(level, target.position(), 2, 0.2f);
                    }

                    if (step % 5 == 0) {
                        SkillActivationService.playSound(level, center, SoundEvents.GENERIC_SPLASH,
                                1.0f, 0.4f + step * 0.01f);
                    }

                    if (step == steps - 1) {
                        for (int i = 0; i < 120; i++) {
                            double a = i * Math.PI * 2 / 120;
                            double r = RNG.nextDouble() * 14.0;
                            Vec3 p = center.add(Math.cos(a) * r, 0.5 + RNG.nextDouble() * 5.0, Math.sin(a) * r);
                            particle(level, ParticleTypes.SPLASH, p, 1, 0.3, 0.45);
                            particle(level, ParticleTypes.BUBBLE, p, 1, 0.2, 0.3);
                            if (i % 4 == 0) particle(level, ParticleTypes.WHITE_ASH, p, 1, 0.3, 0.25);
                        }
                        SkillActivationService.playSound(level, center, SoundEvents.GENERIC_SPLASH, 2.0f, 0.4f);
                        SkillActivationService.playSound(level, center, SoundEvents.CONDUIT_DEACTIVATE, 1.2f, 0.5f);
                        triggerScreenShake(level, center, 9, 0.7f);
                    }
                });
            }
        });
    }

    // ============================================================
    // TIER 10 - LEVIATHAN'S WRATH
    // ============================================================
    private static void registerLeviathansWrath() {
        SkillActivationService.registerExecutor("aqua_leviathan_s_wrath", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 45f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 20f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 side = side(look);
                Vec3 up = up(look);
                Vec3 origin = player.getEyePosition().add(look.scale(2.0));

                SkillActivationService.playSound(level, player.position(), SoundEvents.CONDUIT_ACTIVATE, 1.8f, 0.4f);
                SkillActivationService.playSound(level, player.position(), SoundEvents.ENDER_DRAGON_GROWL, 1.2f, 0.25f);

                int ritual = 16;
                int travel = 28;
                double length = 32.0;
                Set<UUID> hit = new HashSet<>();

                SkillScheduler.run(player, 0, 1, ritual, step -> {
                    if (player.isRemoved()) return;
                    double y = step * 0.28;
                    ring(level, player.position().add(0, y, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                            2.4 + step * 0.09, 30, ParticleTypes.BUBBLE, 0.05, 0.02);
                    if (step % 3 == 0) {
                        ring(level, player.position().add(0, y, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                2.0 + step * 0.08, 20, ParticleTypes.SPLASH, 0.04, 0.02);
                    }
                    if (step == ritual - 1) {
                        SkillActivationService.playSound(level, player.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.3f);
                    }
                });

                SkillScheduler.run(player, ritual, 1, travel, step -> {
                    if (player.isRemoved()) return;
                    double progress = step / (double) (travel - 1);
                    double sway = Math.sin(step * 0.45) * 1.6;
                    Vec3 head = origin.add(look.scale(progress * length))
                            .add(side.scale(sway))
                            .add(0, Math.sin(step * 0.3) * 0.8, 0);

                    for (int i = 0; i < 12; i++) {
                        double a = i * Math.PI * 2 / 12 + step * 0.2;
                        Vec3 p = head.add(side.scale(Math.cos(a) * 1.0)).add(up.scale(Math.sin(a) * 1.0));
                        particle(level, ParticleTypes.BUBBLE, p, 1, 0.04, 0.02);
                        if (i % 3 == 0) particle(level, ParticleTypes.SPLASH, p, 1, 0.03, 0.02);
                    }
                    particle(level, ParticleTypes.ELECTRIC_SPARK, head, 6, 0.5, 0.25);
                    particle(level, ParticleTypes.SOUL_FIRE_FLAME, head, 3, 0.2, 0);

                    for (int segment = 1; segment <= 7; segment++) {
                        double back = step - segment * 1.4;
                        if (back < 0) continue;
                        double backProgress = back / (travel - 1.0);
                        Vec3 body = origin.add(look.scale(backProgress * length))
                                .add(side.scale(Math.sin(back * 0.45) * 1.6))
                                .add(0, Math.sin(back * 0.3) * 0.8, 0);
                        double radius = 1.35 * (1.0 - segment / 9.0);
                        ring(level, body, side, up, radius, 10, ParticleTypes.BUBBLE, 0.04, 0.02);
                        if (segment % 2 == 0) {
                            ring(level, body, side, up, radius * 0.65, 8, ParticleTypes.SPLASH, 0, 0.02);
                        }
                    }

                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(head, head).inflate(2.8), e -> e != player && e.isAlive())) {
                        if (!hit.add(target.getUUID())) continue;
                        float damage = 38f + profile.level() * 1.4f;
                        SkillActivationService.damageEntity(target, player, damage);
                        Vec3 knockback = target.position().subtract(head).normalize().scale(5.0);
                        target.push(knockback.x, 1.8, knockback.z);
                        spawnDamageNumber(level, target.position().add(0, 3.0, 0), (int) damage);
                        triggerScreenShake(level, target.position(), 4, 0.3f);
                    }

                    if (step % 4 == 0) {
                        SkillActivationService.playSound(level, head, SoundEvents.ENDER_DRAGON_AMBIENT, 0.8f, 0.3f);
                    }

                    if (step == travel - 1) {
                        Vec3 end = head;
                        for (int ringIndex = 0; ringIndex < 6; ringIndex++) {
                            double r = 2.0 + ringIndex * 2.2;
                            ring(level, end, new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                    r, 48, ParticleTypes.SPLASH, 0.4, 0.45);
                            ring(level, end.add(0, ringIndex * 0.7, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                    r * 0.8, 32, ParticleTypes.BUBBLE, 0.25, 0.3);
                        }
                        for (int i = 0; i < 60; i++) {
                            Vec3 p = end.add(RNG.nextGaussian() * 5, RNG.nextDouble() * 6, RNG.nextGaussian() * 5);
                            particle(level, ParticleTypes.WHITE_ASH, p, 1, 0.5, 0.35);
                            if (i % 6 == 0) particle(level, ParticleTypes.EXPLOSION, p, 1, 0, 0);
                            if (i % 4 == 0) particle(level, ParticleTypes.LARGE_SMOKE, p, 1, 0.4, 0.2);
                        }
                        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                                new AABB(end.x - 9, end.y - 5, end.z - 9,
                                        end.x + 9, end.y + 7, end.z + 9),
                                e -> e != player && e.isAlive())) {
                            float damage = 30f + profile.level() * 1.2f;
                            SkillActivationService.damageEntity(target, player, damage);
                            Vec3 knockback = target.position().subtract(end).normalize().scale(6.0);
                            target.push(knockback.x, 2.0, knockback.z);
                            spawnDamageNumber(level, target.position().add(0, 3.2, 0), (int) damage);
                        }
                        triggerScreenShake(level, end, 12, 0.9f);
                        SkillActivationService.playSound(level, end, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.25f);
                        SkillActivationService.playSound(level, end, SoundEvents.ENDER_DRAGON_DEATH, 2.0f, 0.3f);
                        SkillActivationService.playSound(level, end, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
                    }
                });
            }
        });
    }

    // ============================================================
    // Shared cinematic helpers
    // ============================================================

    private static Vec3 look(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        return look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
    }

    private static Vec3 side(Vec3 look) {
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        return side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
    }

    private static Vec3 up(Vec3 look) {
        Vec3 up = side(look).cross(look);
        return up.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : up.normalize();
    }

    private static void particle(ServerLevel level, SimpleParticleType type, Vec3 pos,
                                 int count, double spread, double speed) {
        level.sendParticles(type, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    private static void ring(ServerLevel level, Vec3 center, Vec3 u, Vec3 v, double radius,
                             int points, SimpleParticleType type, double spread, double speed) {
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            Vec3 pos = center.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
            particle(level, type, pos, 1, spread, speed);
        }
    }

    private static void sphere(ServerLevel level, Vec3 center, double radius, int points,
                               SimpleParticleType type, double speed) {
        for (int i = 0; i < points; i++) {
            double phi = Math.acos(1 - 2 * (i + 0.5) / points);
            double theta = Math.PI * (3 - Math.sqrt(5)) * i;
            Vec3 pos = center.add(
                    Math.sin(phi) * Math.cos(theta) * radius,
                    Math.cos(phi) * radius,
                    Math.sin(phi) * Math.sin(theta) * radius);
            particle(level, type, pos, 1, 0, speed);
        }
    }

    private static void impactBurst(ServerLevel level, Vec3 center, Vec3 u, Vec3 v,
                                    int points, double radius) {
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            double r = radius * (0.35 + RNG.nextDouble() * 0.65);
            Vec3 pos = center.add(u.scale(Math.cos(angle) * r)).add(v.scale(Math.sin(angle) * r));
            particle(level, ParticleTypes.SPLASH, pos, 1, 0.12, 0.35);
            particle(level, ParticleTypes.BUBBLE, pos, 1, 0.08, 0.2);
            if (i % 5 == 0) particle(level, ParticleTypes.SOUL_FIRE_FLAME, pos, 1, 0, 0);
        }
        particle(level, ParticleTypes.WHITE_ASH, center, Math.max(6, points / 3), 0.45, 0.15);
    }

    private static LivingEntity firstTarget(ServerLevel level, ServerPlayer player, Vec3 pos, double radius) {
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos, pos).inflate(radius), e -> e != player && e.isAlive());
        return targets.isEmpty() ? null : targets.get(0);
    }

    private static boolean blocked(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        var state = level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static Vec3 groundBelow(ServerLevel level, Vec3 point) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(point).mutable();
        for (int i = 0; i < 12; i++) {
            if (!level.getBlockState(pos).isAir()) {
                return Vec3.atBottomCenterOf(pos.above());
            }
            pos.move(0, -1, 0);
        }
        return point;
    }

    private static Vec3 safeDashEnd(ServerLevel level, ServerPlayer player, Vec3 start, Vec3 look, double distance) {
        for (double d = distance; d >= 1.0; d -= 0.5) {
            Vec3 candidate = start.add(look.scale(d));
            if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start)))) {
                return candidate;
            }
        }
        return start.add(look.scale(1.0));
    }

    /** Sends a decaying camera shake to every player close enough to feel it. */
    private static void triggerScreenShake(ServerLevel level, Vec3 center, int intensity, float durationSeconds) {
        int ticks = Math.max(1, Math.round(durationSeconds * 20f));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) > 48 * 48) continue;
            if (!ServerPlayNetworking.canSend(player, SkillCameraShakePayload.TYPE)) continue;
            ServerPlayNetworking.send(player, new SkillCameraShakePayload(
                    center.x, center.y, center.z, intensity, ticks));
        }
    }

    // Camera modes (must match SkillCameraAim on the client)
    private static final int CAM_SMOOTH = 0;
    private static final int CAM_PUNCH = 1;
    private static final int CAM_SHAKE_KICK = 2;
    private static final int CAM_HOLD = 3;

    /** Smooth dolly toward a world point - SEND RELATIVE OFFSETS. */
    private static void sendCameraFocus(ServerPlayer player, Vec3 look, Vec3 target, int delayTicks,
                                        int durationTicks, float speed, float fovMultiplier) {
        Vec3 toTarget = target.subtract(player.getEyePosition());
        float targetYaw = (float) (Math.atan2(toTarget.x, toTarget.z) * 180.0 / Math.PI);
        float targetPitch = (float) (-Math.atan2(toTarget.y, Math.hypot(toTarget.x, toTarget.z)) * 180.0 / Math.PI);
        float yawOff = wrapAngle(targetYaw - player.getYRot());
        float pitchOff = targetPitch - player.getXRot();
        // Small tasteful tilt toward the target instead of full aim.
        yawOff *= 0.35f;
        pitchOff = pitchOff * 0.35f - 4f;
        if (!ServerPlayNetworking.canSend(player, SkillCameraAimPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new SkillCameraAimPayload(yawOff, pitchOff, durationTicks, CAM_SMOOTH, 1f));
    }

    /** Tracks the forming lance tip with a gentle relative dolly. */
    private static void sendCameraTrack(ServerPlayer player, Vec3 look, Vec3 target, int durationTicks,
                                        float speed, boolean smooth) {
        Vec3 toTarget = target.subtract(player.getEyePosition());
        float targetYaw = (float) (Math.atan2(toTarget.x, toTarget.z) * 180.0 / Math.PI);
        float targetPitch = (float) (-Math.atan2(toTarget.y, Math.hypot(toTarget.x, toTarget.z)) * 180.0 / Math.PI);
        float yawOff = wrapAngle(targetYaw - player.getYRot()) * 0.22f;
        float pitchOff = (targetPitch - player.getXRot()) * 0.22f - 2f;
        if (!ServerPlayNetworking.canSend(player, SkillCameraAimPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new SkillCameraAimPayload(yawOff, pitchOff, durationTicks, CAM_SMOOTH, 0.9f));
    }

    /** Violent recoil KICK on launch - damped sine wave that snaps back to neutral. */
    private static void sendCameraKick(ServerPlayer player, Vec3 look, int ticks, float speed) {
        float yawOff = (float) (Math.sin(System.currentTimeMillis() * 0.01f) * 2.5f);
        float pitchOff = -9f; // pitch up on launch (negative XRot looks up in MC)
        if (!ServerPlayNetworking.canSend(player, SkillCameraAimPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new SkillCameraAimPayload(yawOff, pitchOff, ticks, CAM_SHAKE_KICK, 1f));
    }

    /** Recoil snap-back after the kick - punch easing, settles to neutral. */
    private static void sendCameraRecoil(ServerPlayer player, Vec3 look, int ticks, float speed) {
        float yawOff = (float) (Math.sin(System.currentTimeMillis() * 0.013f) * 1.5f);
        float pitchOff = 4f;
        if (!ServerPlayNetworking.canSend(player, SkillCameraAimPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new SkillCameraAimPayload(yawOff, pitchOff, ticks, CAM_PUNCH, 0.8f));
    }

    /** Short punchy zoom-tilt for the impact frame. */
    private static void sendCameraImpact(ServerPlayer player, int ticks) {
        if (!ServerPlayNetworking.canSend(player, SkillCameraAimPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new SkillCameraAimPayload(0f, -6f, ticks, CAM_PUNCH, 1f));
    }

    /** Wraps an angle in degrees to [-180, 180]. */
    private static float wrapAngle(float angle) {
        while (angle > 180f) angle -= 360f;
        while (angle < -180f) angle += 360f;
        return angle;
    }

    private static void spawnDamageNumber(ServerLevel level, Vec3 pos, int damage) {
        int bursts = Math.max(1, Math.min(6, damage / 8));
        for (int i = 0; i < bursts; i++) {
            particle(level, ParticleTypes.CRIT,
                    pos.add(RNG.nextGaussian() * 0.25, i * 0.25, RNG.nextGaussian() * 0.25), 1, 0, 0);
        }
    }
}