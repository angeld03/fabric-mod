import re

with open('src/main/java/com/wildbound/skills/AquaSkillExecutors.java', 'r', encoding='utf-8') as f:
    content = f.read()

new_water_bolt = """    // ============================================================
    // TIER 1 - WATER BOLT (cinematic charge + 3 lances + dramatic camera)
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

                double[] lanceRanges = { 10.0 * chargeMult, 16.0 * chargeMult, 24.0 * chargeMult };
                int[] lanceDamage  = { 8, 12, 18 };
                int[] lanceBursts  = { 20, 32, 48 };
                double[] lanceWidth = { 1.2, 1.8, 2.6 };

                int chargeTicks = 14;
                int lanceFormTicks = 8;
                int totalSteps = chargeTicks + (lanceFormTicks * 3) + 6;

                sendCameraFocus(player, look, origin, 0, chargeTicks, 0.15f, 1.5f);

                SkillScheduler.run(player, 0, 1, totalSteps, step -> {
                    if (player.isRemoved()) return;

                    if (step < chargeTicks) {
                        double t = step / (double) chargeTicks;
                        double radius = 0.9 * (1.0 - t * 0.4);
                        double pulse = 1.0 + Math.sin(step * 0.9) * 0.2;

                        for (int i = 0; i < 8; i++) {
                            double a = step * 1.2 + i * (Math.PI * 2 / 8);
                            Vec3 p = origin.add(
                                    Math.cos(a) * radius * pulse,
                                    Math.sin(a) * radius * 0.6 * pulse,
                                    Math.cos(a + step * 0.5) * radius * pulse);
                            particle(level, ParticleTypes.BUBBLE, p, 2, 0.02, 0.02);
                            particle(level, ParticleTypes.SPLASH, p, 1, 0.01, 0.01);
                            if (i % 2 == 0) particle(level, ParticleTypes.ELECTRIC_SPARK, p, 1, 0.06, 0.03);
                        }

                        particle(level, ParticleTypes.SPLASH, origin, 3, 0.04, 0.04);
                        particle(level, ParticleTypes.ELECTRIC_SPARK, origin, 3, 0.1, 0.05);

                        if (step % 2 == 0) {
                            ring(level, origin, side, up, 0.6 + t * 0.4, 16,
                                    ParticleTypes.BUBBLE, 0.02, 0.01);
                        }

                        if (step % 4 == 0) {
                            SkillActivationService.playSound(level, origin, SoundEvents.BUBBLE_POP,
                                    0.4f, 1.2f + (float) step * 0.08f);
                        }
                        return;
                    }

                    int lancePhase = (step - chargeTicks) / lanceFormTicks;
                    if (lancePhase >= 3) lancePhase = 2;

                    int tickInLance = (step - chargeTicks) % lanceFormTicks;
                    int lanceIndex = lancePhase;

                    if (tickInLance >= lanceFormTicks) return;

                    double range = lanceRanges[lanceIndex];
                    int dmg = (int) (lanceDamage[lanceIndex] * chargeMult);
                    int burst = lanceBursts[lanceIndex];
                    double width = lanceWidth[lanceIndex] * chargeMult;

                    double form = tickInLance / (double) (lanceFormTicks - 1);
                    double reach = 1.0 + form * (range - 1.0);
                    Vec3 tip = origin.add(look.scale(reach));

                    int points = Math.max(2, (int) (form * 20) + 2);
                    for (int i = 0; i <= points; i++) {
                        double seg = i / (double) points;
                        Vec3 p = origin.add(look.scale(1.0 + seg * (reach - 1.0)));
                        particle(level, ParticleTypes.SPLASH, p, 1, 0.01, 0.01);
                        particle(level, ParticleTypes.BUBBLE, p, 1, 0.005, 0.005);
                        if (seg > 0.5 && i % 2 == 0) {
                            particle(level, ParticleTypes.ELECTRIC_SPARK, p, 1, 0.04, 0.02);
                        }
                    }

                    double a = tickInLance * 1.6 + lanceIndex * (Math.PI * 2 / 3);
                    for (int e = 0; e < 5; e++) {
                        double ang = a + e * (Math.PI * 2 / 5);
                        Vec3 edge = tip.add(side.scale(Math.cos(ang) * 0.5))
                                .add(up.scale(Math.sin(ang) * 0.5));
                        particle(level, ParticleTypes.SPLASH, edge, 1, 0.03, 0.03);
                        particle(level, ParticleTypes.BUBBLE, edge, 1, 0.02, 0.02);
                        if (e % 2 == 0) {
                            particle(level, ParticleTypes.ELECTRIC_SPARK, edge, 1, 0.08, 0.04);
                        }
                    }

                    if (tickInLance % 2 == 0) {
                        ring(level, tip, side, up, 0.8 + form * 0.6, 20,
                                ParticleTypes.ELECTRIC_SPARK, 0.04, 0.02);
                        ring(level, tip, side, up, 0.4 + form * 0.3, 12,
                                ParticleTypes.SPLASH, 0.02, 0.01);
                    }

                    particle(level, ParticleTypes.ELECTRIC_SPARK, tip, 4, 0.12, 0.06);
                    particle(level, ParticleTypes.SPLASH, tip, 2, 0.08, 0.08);

                    if (tickInLance == 0) {
                        sendCameraTrack(player, look, tip, lanceFormTicks, 0.08f, true);
                        SkillActivationService.playSound(level, origin, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                                0.7f, 0.9f + lanceIndex * 0.15f);
                    } else if (tickInLance == lanceFormTicks - 1) {
                        sendCameraFocus(player, look, tip, 0, 3, 0.35f, 2.5f);
                        
                        Vec3 lanceEnd = origin.add(look.scale(range));

                        sendCameraKick(player, look, 8, 0.5f);
                        triggerScreenShake(level, lanceEnd, 6 + lanceIndex * 2, 0.5f);
                        triggerScreenShake(level, lanceEnd, 3 + lanceIndex, 1.0f);
                        sendCameraRecoil(player, look, 6, 0.25f);

                        impactBurst(level, lanceEnd, side, up, burst, 1.4f + lanceIndex * 0.4f);
                        particle(level, ParticleTypes.SPLASH, lanceEnd, 10 + lanceIndex * 4, 0.2f, 0.2f);
                        particle(level, ParticleTypes.BUBBLE, lanceEnd, 8 + lanceIndex * 2, 0.12f, 0.12f);
                        particle(level, ParticleTypes.ELECTRIC_SPARK, lanceEnd, 8, 0.2f, 0.1f);

                        ring(level, lanceEnd.add(0, 0.1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                range * 0.6, 32, ParticleTypes.SPLASH, 0.1f, 0.15f);
                        ring(level, lanceEnd.add(0, 0.15, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                                range * 0.4, 24, ParticleTypes.BUBBLE, 0.05f, 0.08f).

                        for (int f = 0; f < 12; f++) {
                            double ff = f / 11.0;
                            Vec3 flash = origin.add(look.scale(ff * range));
                            particle(level, ParticleTypes.ELECTRIC_SPARK, flash, 1, 0.15f, 0.15f);
                            if (f % 3 == 0) particle(level, ParticleTypes.SPLASH, flash, 1, 0.05f, 0.05f);
                        }

                        AABB lanceBox = new AABB(origin, lanceEnd).inflate(width);
                        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, lanceBox,
                                e -> e != player && e.isAlive())) {
                            float damage = dmg + profile.level() * 0.5f;
                            SkillActivationService.damageEntity(target, player, damage);
                            SkillActivationService.applyKnockback(target, player.position(), 1.8f + lanceIndex * 0.3f);
                            spawnDamageNumber(level, target.position().add(0, 2.4, 0), (int) damage);
                        }

                        SkillActivationService.playSound(level, lanceEnd, SoundEvents.GENERIC_SPLASH,
                                1.2f + lanceIndex * 0.3f, 0.7f);
                        SkillActivationService.playSound(level, lanceEnd, SoundEvents.WATER_AMBIENT,
                                0.8f + lanceIndex * 0.2f, 1.3f);
                        SkillActivationService.playSound(level, lanceEnd, SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                                0.6f, 1.6f);
                    }
                    return;
                });
            }
        });
    }"""

pattern = r'// ============================================================\s+// TIER 1 - WATER BOLT \(gacha-style 3-lance cinematic sequence\)\s+// ============================================================\s+private static void registerWaterBolt\(\).*?(?=^\s{4})'
content = re.sub(pattern, new_water_bolt, content, flags=re.DOTALL | re.MULTILINE)

with open('src/main/java/com/wildbound/skills/AquaSkillExecutors.java', 'w', encoding='utf-8') as f:
    f.write(content)

print('Done')