import io

path = 'src/main/java/com/wildbound/skills/AquaSkillExecutors.java'
with io.open(path, 'r', encoding='utf-8') as f:
    content = f.read()

start = content.index('    // ============================================================\n    // TIER 1 - WATER BOLT')
end = content.index('    // ============================================================\n    // TIER 2 - HEALING STREAM')

new_block = r'''    // ============================================================
    // TIER 1 - WATER BOLT (cinematic charge + 3 animated lances)
    // ============================================================
    private static void registerWaterBolt() {
        SkillActivationService.registerExecutor("aqua_water_bolt", new SkillActivationService.SkillExecutor() {
            @Override public float getCooldown(SkillDefinition def, PlayerRpgProfile profile) { return 2.5f; }
            @Override public float getManaCost(SkillDefinition def, PlayerRpgProfile profile) { return 8f; }

            @Override
            public void execute(ServerPlayer player, SkillDefinition def, PlayerRpgProfile profile) {
                ServerLevel level = player.level();
                Vec3 look = look(player);
                Vec3 side = side(look);
                Vec3 up = up(look);
                Vec3 origin = player.getEyePosition().add(look.scale(1.0));

                float charge = SkillActivationService.getChargeLevel();
                float chargeMult = 1.0f + charge;

                // Each lance: [range, damage, burst size, shaft thickness]
                double[] lanceRanges  = { 10.0 * chargeMult, 16.0 * chargeMult, 24.0 * chargeMult };
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
                    int dmg = (int) (lanceDamage[li] * chargeMult);
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
                            SkillActivationService.applyKnockback(target, look, 1.8f + li * 0.3f);
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

'''

content = content[:start] + new_block + content[end:]
with io.open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)
print('Water Bolt rewritten')