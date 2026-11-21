package com.wildbound.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.Wildbound;
import com.wildbound.attributes.DerivedStats;
import com.wildbound.attributes.DerivedStatsCalculator;
import com.wildbound.core.WildboundConfig;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RpgAttachments;
import com.wildbound.progression.ExperienceCurve;
import com.wildbound.realms.RealmRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * WILDBOUND HUD.
 *
 * <p>Top-left: a progression plaque with a level medallion, the latest realm
 * and an animated experience bar. Bottom: numeric health and stamina bars in
 * the vanilla slots, with a mana orb bridging them. The bars react to damage,
 * healing and low resources, and press K hides everything.</p>
 */
public final class RpgHud {
    private static boolean enabled = true;
    private static boolean lastDown = false;

    // Palette shared with the skill screen.
    private static final int BORDER = 0xFF04070C;
    private static final int PANEL_TOP = 0xE61D2A3C;
    private static final int PANEL_BOTTOM = 0xF00A1018;
    private static final int SHADOW = 0x66000000;
    private static final int GOLD = 0xFFFFD479;
    private static final int GOLD_LIGHT = 0xFFFFF0C0;
    private static final int GOLD_DEEP = 0xFFC98A2B;
    private static final int TEXT = 0xFFF4F9FF;
    private static final int MUTED = 0xFFA7B8CE;
    private static final int TRACK_TOP = 0xFF060A10;
    private static final int TRACK_BOTTOM = 0xFF16202D;

    private static final int HP_TOP = 0xFFFF9184;
    private static final int HP_MID = 0xFFE23B3F;
    private static final int HP_BOTTOM = 0xFF8E161D;
    private static final int HP_TRAIL_TOP = 0xFFFFD9A8;
    private static final int HP_TRAIL_MID = 0xFFE07963;
    private static final int HP_TRAIL_BOTTOM = 0xFF8F3830;
    private static final int ST_TOP = 0xFF9BF7C2;
    private static final int ST_MID = 0xFF3FCB7F;
    private static final int ST_BOTTOM = 0xFF1B7044;
    private static final int XP_TOP = 0xFFADEBFF;
    private static final int XP_MID = 0xFF49B8F5;
    private static final int XP_BOTTOM = 0xFF1E6BC4;
    private static final int MANA_TOP = 0xFFD6F5FF;
    private static final int MANA_MID = 0xFF5FB8FF;
    private static final int MANA_BOTTOM = 0xFF214FB4;
    private static final int MANA_VOID_TOP = 0xFF132743;
    private static final int MANA_VOID_BOTTOM = 0xFF07101E;
    private static final int MANA_RIM_DARK = 0xFF081120;

    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.wildbound.hud", InputConstants.KEY_K, KeyMapping.Category.MISC);

    private static final HudAnimation ANIMATION = new HudAnimation();

    private RpgHud() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(TOGGLE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean down = TOGGLE.isDown();
            if (down && !lastDown && client.gui.screen() == null) {
                enabled = !enabled;
                if (enabled) ANIMATION.reset();
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.translatable(
                            enabled ? "wildbound.hud.on" : "wildbound.hud.off"));
                }
            }
            lastDown = down;
        });

        // The vanilla hearts are replaced by a numeric bar. They come back
        // automatically when the RPG HUD is hidden so the player never loses
        // the health display.
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR,
                original -> (g, delta) -> {
                    if (!enabled) original.extractRenderState(g, delta);
                });

        // The vanilla air bubbles share the row where our stamina bar lives,
        // so move them one row up while the RPG HUD is visible.
        HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR,
                original -> (g, delta) -> {
                    if (!enabled) {
                        original.extractRenderState(g, delta);
                        return;
                    }
                    g.pose().pushMatrix();
                    g.pose().translate(0.0f, -14.0f);
                    original.extractRenderState(g, delta);
                    g.pose().popMatrix();
                });

        HudElementRegistry.addLast(Wildbound.id("rpg_hud"), RpgHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
        if (profile == null) return;

        DerivedStats stats = DerivedStatsCalculator.calculate(profile);
        ANIMATION.update(player, profile, stats);

        renderPlaque(g, mc.font, profile);
        renderVitals(g, mc.font, player, profile, stats);
        renderLevelUpBanner(g, mc.font, profile);
    }

    // ------------------------------------------------------------------
    // Top-left progression plaque
    // ------------------------------------------------------------------

    private static void renderPlaque(GuiGraphicsExtractor g, Font font, PlayerRpgProfile profile) {
        int x = 9;
        int y = 9; // top-left corner
        int w = 190;
        int h = 59;
        float time = ANIMATION.time;

        // The frame brightens for a moment after a level up.
        float pulse = ANIMATION.levelUp;
        int accent = lerpColor(GOLD_DEEP, GOLD_LIGHT, 0.25f + 0.75f * pulse);
        panel(g, x, y, w, h, accent, true);

        // Very subtle stars, deterministic and alive but never distracting.
        for (int i = 0; i < 7; i++) {
            int sx = x + 44 + (i * 31) % (w - 58);
            int sy = y + 7 + (i * 19) % 13;
            int alpha = 14 + (int) (25 * (0.5f + 0.5f * Math.sin(time * 1.1f + i * 1.7f)));
            g.fill(sx, sy, sx + 1, sy + 1, alpha(0xFFFFFFFF, alpha));
        }

        // Level medallion.
        drawLevelMedallion(g, font, x + 23, y + 25, profile.level(), time);

        // Title and realm name.
        gradientText(g, font, "WILDBOUND", x + 44, y + 7, GOLD_LIGHT, GOLD_DEEP);
        g.fill(x + 44, y + 17, x + w - 9, y + 18, alpha(GOLD_DEEP, 0x50));
        g.fill(x + 44, y + 17, x + Math.min(w - 9, x + 73), y + 18, alpha(GOLD, 0xA8));

        String realm = latestRealm(font, profile, w - 53);
        g.text(font, realm, x + 44, y + 21, MUTED, false);

        // Experience row and animated bar.
        long needed = requiredExperience(profile);
        boolean maxed = needed <= 0L;
        String xpLabel = maxed ? "XP  MAX" : "XP  " + Math.round(ANIMATION.xp * 100f) + "%";
        String xpValue = maxed ? "—" : compactNumber(profile.experience())
                + " / " + compactNumber(needed);
        g.text(font, xpLabel, x + 44, y + 34, alpha(GOLD, 0xE0), false);
        g.text(font, xpValue, x + w - 9 - font.width(xpValue), y + 34, TEXT, false);

        int barX = x + 44;
        int barY = y + 46;
        int barW = w - 53;
        drawBar(g, barX, barY, barW, 7, ANIMATION.xp, XP_TOP, XP_MID, XP_BOTTOM, time);
        if (ANIMATION.xpGain > 0f) {
            int edge = alpha(GOLD, Math.round(0xA0 * ANIMATION.xpGain));
            g.fill(barX - 1, barY - 1, barX + barW + 1, barY, edge);
            g.fill(barX - 1, barY + 7, barX + barW + 1, barY + 8, edge);
        }

        // Skill points indicator: small pill on the right edge of the plaque
        if (profile.skillPoints() > 0) drawSkillPointsPill(g, font, x + w - 6, y + 4, profile, time);
    }

    /** Level medallion: a dark gold ring with the level inside and a level-up halo. */
    private static void drawLevelMedallion(GuiGraphicsExtractor g, Font font,
                                           int cx, int cy, int level, float time) {
        float pulse = ANIMATION.levelUp;
        int r = 16;
        if (pulse > 0f) {
            fillCircle(g, cx, cy, r + 2 + Math.round(4f * pulse),
                    alpha(GOLD_LIGHT, Math.round(0x70 * pulse)));
            strokeCircle(g, cx, cy, r + 2, alpha(GOLD_LIGHT, Math.round(0xB0 * pulse)));
        }

        // Four fixed golden ticks around the medallion.
        strokeCircle(g, cx, cy, r, alpha(GOLD_DEEP, 0x88));
        g.fill(cx - 1, cy - r - 3, cx + 1, cy - r - 1, GOLD);
        g.fill(cx - 1, cy + r + 1, cx + 1, cy + r + 3, GOLD_DEEP);
        g.fill(cx - r - 3, cy - 1, cx - r - 1, cy + 1, GOLD_DEEP);
        g.fill(cx + r + 1, cy - 1, cx + r + 3, cy + 1, GOLD);

        fillCircle(g, cx, cy, r + 1, BORDER);
        fillCircleGradient(g, cx, cy, r, 0xFF35290F, 0xFF0C101A);
        strokeCircle(g, cx, cy, r - 1, alpha(GOLD, 0xD0));
        fillCircle(g, cx, cy, r - 3, alpha(0xFF0A111D, 0xE8));
        g.fill(cx - 7, cy - 9, cx - 3, cy - 7, alpha(GOLD_LIGHT, 0x44));
        g.fill(cx - 5, cy - 7, cx - 3, cy - 5, alpha(GOLD_LIGHT, 0x70));

        String caption = "LV";
        g.text(font, caption, cx - font.width(caption) / 2, cy - 10, alpha(GOLD, 0xB8), false);
        String number = String.valueOf(level);
        g.text(font, number, cx - font.width(number) / 2, cy + 1, GOLD_LIGHT, true);
    }

    /** Compact skill points pill on the right edge of the plaque. */
    private static void drawSkillPointsPill(GuiGraphicsExtractor g, Font font,
                                            int rightX, int y, PlayerRpgProfile profile, float time) {
        String pts = String.valueOf(profile.skillPoints());
        int pillW = font.width(pts) + 14;
        int pillX = rightX - pillW;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.0f);
        int bg = alpha(GOLD, Math.round(0x60 + 0x40 * pulse));
        int border = alpha(GOLD_LIGHT, Math.round(0xC0 + 0x3F * pulse));

        // Pill background
        g.fill(pillX, y, pillX + pillW, y + 16, alpha(0xFF0C101A, 0xE0));
        g.fill(pillX, y, pillX + pillW, y + 1, border);
        g.fill(pillX, y + 15, pillX + pillW, y + 16, border);
        g.fill(pillX, y, pillX + 1, y + 16, border);
        g.fill(pillX + pillW - 1, y, pillX + pillW, y + 16, alpha(GOLD_DEEP, 0xB0));

        // Small "+" icon
        g.fill(pillX + 4, y + 6, pillX + 5, y + 10, GOLD_LIGHT);
        g.fill(pillX + 3, y + 7, pillX + 6, y + 8, GOLD_LIGHT);

        // Points number
        g.text(font, pts, pillX + 9, y + 4, GOLD_LIGHT, true);
    }

    /** Cinematic but compact level-up announcement, driven by the synced profile change. */
    private static void renderLevelUpBanner(GuiGraphicsExtractor g, Font font, PlayerRpgProfile profile) {
        float strength = ANIMATION.levelUp;
        if (strength <= 0.01f) return;

        float fadeIn = clamp01((1f - strength) * 7f);
        float fadeOut = clamp01(strength * 4.5f);
        float opacity = Math.min(fadeIn, fadeOut);
        float pulse = 0.5f + 0.5f * (float) Math.sin(ANIMATION.time * 5.5f);
        int screenW = g.guiWidth();
        int bannerW = Math.min(270, screenW - 20);
        int bannerH = 43;
        int x = (screenW - bannerW) / 2;
        int y = 72 - Math.round((1f - fadeIn) * 12f);

        int goldGlow = alpha(GOLD_LIGHT, Math.round(0x45 * opacity * (0.65f + pulse * 0.35f)));
        g.fill(x - 3, y - 3, x + bannerW + 3, y + bannerH + 3, goldGlow);
        g.fill(x - 1, y - 1, x + bannerW + 1, y + bannerH + 1, alpha(GOLD_DEEP, Math.round(255f * opacity)));
        g.fill(x, y, x + bannerW, y + bannerH, alpha(0xFF101A2A, Math.round(240f * opacity)));
        g.fillGradient(x + 1, y + 1, x + bannerW - 1, y + 3,
                alpha(GOLD_LIGHT, Math.round(255f * opacity)), alpha(0xFF55DFFF, Math.round(255f * opacity)));
        g.fill(x + 1, y + bannerH - 2, x + bannerW - 1, y + bannerH - 1,
                alpha(0xFF55DFFF, Math.round(140f * opacity)));

        String title = Component.translatable("wildbound.hud.level_up").getString();
        int titleX = x + (bannerW - font.width(title)) / 2;
        gradientText(g, font, title, titleX, y + 7,
                lerpColor(GOLD_LIGHT, 0xFFFFFFFF, pulse * 0.45f), 0xFF56DFFF);
        String level = Component.translatable("wildbound.hud.level_transition", ANIMATION.levelUpFrom, ANIMATION.levelUpTo).getString();
        int levelX = x + (bannerW - font.width(level)) / 2;
        g.text(font, level, levelX, y + 20, alpha(TEXT, Math.round(255f * opacity)), true);

        String rewards = Component.translatable("wildbound.hud.level_rewards",
                ANIMATION.levelUpAttributePoints, ANIMATION.levelUpSkillPoints).getString();
        if (font.width(rewards) <= bannerW - 12) {
            int rewardX = x + (bannerW - font.width(rewards)) / 2;
            g.text(font, rewards, rewardX, y + 32, alpha(MUTED, Math.round(255f * opacity)), false);
        }

        // Tiny animated starbursts on both ends of the plaque.
        for (int i = 0; i < 4; i++) {
            float phase = ANIMATION.time * 4f + i * 1.57f;
            int sy = y + 9 + Math.round((0.5f + 0.5f * (float) Math.sin(phase)) * 21f);
            int size = 1 + (int) (pulse * 2f);
            int sx = i % 2 == 0 ? x + 7 + i * 3 : x + bannerW - 8 - (i - 1) * 3;
            g.fill(sx - size, sy, sx + size + 1, sy + 1, alpha(GOLD_LIGHT, Math.round(255f * opacity)));
            g.fill(sx, sy - size, sx + 1, sy + size + 1, alpha(0xFF8CEEFF, Math.round(255f * opacity)));
        }
    }

    // ------------------------------------------------------------------
    // Bottom vitals
    // ------------------------------------------------------------------

    private static void renderVitals(GuiGraphicsExtractor g, Font font,
                                     LocalPlayer player, PlayerRpgProfile profile,
                                     DerivedStats stats) {
        int cx = g.guiWidth() / 2;
        int baseY = g.guiHeight() - 39; // exactly where vanilla hearts/food live
        int barW = 81;
        int barH = 11;
        float time = ANIMATION.time;

        // Health, in the vanilla hearts slot.
        int hx = cx - 91;
        int hpBorder = lowPulse(ANIMATION.health, time, 0.30f);
        if (ANIMATION.damage > 0f) {
            hpBorder = lerpColor(hpBorder, HP_TOP, ANIMATION.damage * 0.7f);
        }
        drawBarBase(g, hx, baseY, barW, barH, hpBorder);
        drawBarFill(g, hx, baseY, barW, barH, ANIMATION.healthTrail,
                HP_TRAIL_TOP, HP_TRAIL_MID, HP_TRAIL_BOTTOM);
        drawBarFill(g, hx, baseY, barW, barH, ANIMATION.health, lowHealthTop(), HP_MID, HP_BOTTOM);
        drawBarOverlay(g, hx, baseY, barW, barH, ANIMATION.health, time, 0f);
        drawHeart(g, hx + 6, baseY + 5, ANIMATION.health < 0.30f ? 0xFFFFC3C3 : 0xFFFFE7E7);
        centeredBarText(g, font, healthText(player), hx, baseY, barW, barH, 3);

        if (ANIMATION.damage > 0f) {
            int edge = alpha(HP_TOP, Math.round(0xB0 * ANIMATION.damage));
            g.fill(hx - 1, baseY - 1, hx + barW + 1, baseY, edge);
            g.fill(hx - 1, baseY + barH, hx + barW + 1, baseY + barH + 1, edge);
        } else if (ANIMATION.healing > 0f) {
            int edge = alpha(0xFF8CFFB8, Math.round(0x90 * ANIMATION.healing));
            g.fill(hx - 1, baseY - 1, hx + barW + 1, baseY, edge);
            g.fill(hx - 1, baseY + barH, hx + barW + 1, baseY + barH + 1, edge);
        }

        // Stamina, directly above the food bar (2px gap).
        int sx = cx + 10;
        int sy = baseY - 13;
        drawBar(g, sx, sy, barW, barH, ANIMATION.stamina, ST_TOP, ST_MID, ST_BOTTOM, time);
        drawBolt(g, sx + 6, sy + 5);
        centeredBarText(g, font, staminaText(profile, stats), sx, sy, barW, barH, 3);
        if (ANIMATION.stamina < 0.18f) {
            int edge = alpha(ST_TOP, lowPulse(ANIMATION.stamina, time, 0.18f));
            g.fill(sx - 1, sy - 1, sx + barW + 1, sy, edge);
            g.fill(sx - 1, sy + barH, sx + barW + 1, sy + barH + 1, edge);
        }

        // Mana orb in the 20px gap between both bars.
        int orbY = baseY + 5;
        drawManaOrb(g, cx, orbY, ANIMATION.mana, time);
        String mp = compactNumber(Math.round(profile.mana()));
        outlinedCenteredText(g, font, mp, cx, orbY - 4);
    }

    private static String healthText(LocalPlayer player) {
        return compactNumber(Math.round(player.getHealth()))
                + " / " + compactNumber(Math.round(player.getMaxHealth()));
    }

    private static String staminaText(PlayerRpgProfile profile, DerivedStats stats) {
        return compactNumber(Math.round(profile.stamina()))
                + " / " + compactNumber(Math.round(stats.maxStamina()));
    }

    // ------------------------------------------------------------------
    // Animation state
    // ------------------------------------------------------------------

    /**
     * Render-only smoothing. Every value is normalized [0..1]; the game's
     * authoritative values are always drawn as text immediately.
     */
    private static final class HudAnimation {
        private UUID playerId;
        private long lastFrame;
        private float time;

        private float health;
        private float healthTrail;
        private float stamina;
        private float mana;
        private float xp;

        private float damage;
        private float healing;
        private float levelUp;
        private float xpGain;
        private int levelUpFrom;
        private int levelUpTo;
        private int levelUpAttributePoints;
        private int levelUpSkillPoints;

        private int lastLevel = -1;
        private int lastAttributePoints = -1;
        private int lastSkillPoints = -1;
        private long lastExperience = -1L;
        private float lastHealth;

        void reset() {
            playerId = null;
            lastFrame = 0L;
        }

        void update(LocalPlayer player, PlayerRpgProfile profile, DerivedStats stats) {
            long now = System.nanoTime();
            float dt = lastFrame == 0L ? 0f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
            lastFrame = now;

            float healthTarget = player.getMaxHealth() > 0f
                    ? clamp01(player.getHealth() / player.getMaxHealth()) : 0f;
            float staminaTarget = stats.maxStamina() > 0f
                    ? clamp01(profile.stamina() / stats.maxStamina()) : 0f;
            float manaTarget = stats.maxMana() > 0f
                    ? clamp01(profile.mana() / stats.maxMana()) : 0f;
            long needed = requiredExperience(profile);
            float xpTarget = needed > 0L ? clamp01((float) profile.experience() / needed) : 1f;

            boolean newPlayer = !player.getUUID().equals(playerId);
            if (newPlayer) {
                playerId = player.getUUID();
                time = 0f;
                health = healthTarget;
                healthTrail = healthTarget;
                stamina = staminaTarget;
                mana = manaTarget;
                xp = xpTarget;
                damage = 0f;
                healing = 0f;
                levelUp = 0f;
                xpGain = 0f;
                levelUpFrom = profile.level();
                levelUpTo = profile.level();
                levelUpAttributePoints = 0;
                levelUpSkillPoints = 0;
            } else {
                time += dt;
                if (healthTarget < lastHealth - 0.001f) damage = 1f;
                if (healthTarget > lastHealth + 0.001f) healing = 1f;
                if (profile.level() > lastLevel) {
                    levelUp = 1f;
                    levelUpFrom = lastLevel;
                    levelUpTo = profile.level();
                    levelUpAttributePoints = lastAttributePoints < 0 ? 0
                            : Math.max(0, profile.attributePoints() - lastAttributePoints);
                    levelUpSkillPoints = lastSkillPoints < 0 ? 0
                            : Math.max(0, profile.skillPoints() - lastSkillPoints);
                }
                if (profile.experience() > lastExperience || profile.level() > lastLevel) xpGain = 1f;

                health = approach(health, healthTarget, 14f, dt);
                healthTrail = healthTarget > healthTrail
                        ? healthTarget : approach(healthTrail, healthTarget, 2.3f, dt);
                stamina = approach(stamina, staminaTarget, 10f, dt);
                mana = approach(mana, manaTarget, 8f, dt);
                xp = approach(xp, xpTarget, 8f, dt);
            }

            lastLevel = profile.level();
            lastExperience = profile.experience();
            lastAttributePoints = profile.attributePoints();
            lastSkillPoints = profile.skillPoints();
            lastHealth = healthTarget;

            damage = Math.max(0f, damage - dt * 2.2f);
            healing = Math.max(0f, healing - dt * 2.5f);
            levelUp = Math.max(0f, levelUp - dt * 0.45f);
            xpGain = Math.max(0f, xpGain - dt * 0.8f);
        }

        private static float approach(float current, float target, float speed, float dt) {
            if (dt <= 0f) return target;
            return target + (current - target) * (float) Math.exp(-speed * dt);
        }
    }

    private static long requiredExperience(PlayerRpgProfile profile) {
        return profile.level() >= WildboundConfig.maxLevel ? 0L : ExperienceCurve.required(profile.level());
    }

    private static String latestRealm(Font font, PlayerRpgProfile profile, int maxWidth) {
        List<String> realms = profile.unlockedRealms();
        if (realms.isEmpty()) return "WILDBOUND RPG";
        String id = realms.get(realms.size() - 1);
        String name = RealmRegistry.get(id).map(realm -> realm.name()).orElse(id);
        return fit(font, name.toUpperCase(Locale.ROOT), maxWidth);
    }

    // ------------------------------------------------------------------
    // Bars
    // ------------------------------------------------------------------

    /** Complete bar: track, fill, gloss, tick marks and moving shine. */
    private static void drawBar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                float progress, int top, int mid, int bottom, float time) {
        drawBarBase(g, x, y, w, h, BORDER);
        drawBarFill(g, x, y, w, h, progress, top, mid, bottom);
        drawBarOverlay(g, x, y, w, h, progress, time, 0f);
    }

    private static void drawBarBase(GuiGraphicsExtractor g, int x, int y, int w, int h, int border) {
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, SHADOW);
        g.fill(x, y, x + w, y + h, border);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, TRACK_TOP, TRACK_BOTTOM);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, alpha(0xFFFFFFFF, 0x20));
    }

    private static void drawBarFill(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                    float progress, int top, int mid, int bottom) {
        int innerX = x + 1;
        int innerY = y + 1;
        int innerH = h - 2;
        int filled = Math.round((w - 2) * clamp01(progress));
        if (filled <= 0) return;

        int midY = innerY + innerH / 2;
        g.fillGradient(innerX, innerY, innerX + filled, midY, top, mid);
        g.fillGradient(innerX, midY, innerX + filled, innerY + innerH, mid, bottom);

        if (filled > 2) {
            g.fill(innerX + 1, innerY + 1, innerX + filled - 1, innerY + 2, 0x55FFFFFF);
            g.fill(innerX + 1, innerY + innerH - 1, innerX + filled - 1, innerY + innerH, 0x40000000);
        }
        if (filled < w - 2) {
            g.fill(innerX + filled, innerY, innerX + filled + 1, innerY + innerH, 0x66FFFFFF);
        }
    }

    private static void drawBarOverlay(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                       float progress, float time, float phase) {
        int innerX = x + 1;
        int innerY = y + 1;
        int innerH = h - 2;
        int innerW = w - 2;
        int filled = Math.round(innerW * clamp01(progress));
        if (filled <= 0) return;

        // Quarter marks make the bars readable without taking space from text.
        for (int i = 1; i < 4; i++) {
            int markX = innerX + innerW * i / 4;
            if (markX < innerX + filled) {
                g.fill(markX, innerY + 1, markX + 1, innerY + innerH - 1, 0x28000000);
            }
        }

        // One diagonal highlight sweeping along the filled part.
        int travel = innerW + 18;
        int center = innerX - 9 + (int) ((time * 24f + phase * travel) % travel);
        for (int row = 0; row < innerH; row++) {
            int sx = center + row / 2;
            if (sx >= innerX && sx + 2 <= innerX + filled) {
                g.fill(sx, innerY + row, sx + 2, innerY + row + 1, alpha(0xFFFFFFFF, 0x18));
            }
        }
    }

    // ------------------------------------------------------------------
    // Mana orb
    // ------------------------------------------------------------------

    /** Pixel-art mana orb: dark vessel, rising liquid, bubbles and bright surface. */
    private static void drawManaOrb(GuiGraphicsExtractor g, int cx, int cy,
                                    float progress, float time) {
        float p = clamp01(progress);
        boolean full = p >= 0.99f;
        boolean low = p <= 0.20f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * (low ? 6.0f : 2.2f));
        int radius = 9;

        if (full || low) {
            fillCircle(g, cx, cy, radius + 2,
                    alpha(full ? MANA_MID : MANA_TOP, Math.round((full ? 0x38 : 0x50) + 0x30 * pulse)));
        }

        fillCircle(g, cx, cy, radius + 1, BORDER);
        fillCircleGradient(g, cx, cy, radius + 1, MANA_VOID_TOP, MANA_VOID_BOTTOM);
        strokeCircle(g, cx, cy, radius + 1, lerpColor(MANA_RIM_DARK, MANA_MID, full ? 0.4f + 0.25f * pulse : 0.15f));

        int totalRows = radius * 2 + 1;
        int filledRows = Math.round(totalRows * p);
        int fillTop = cy + radius + 1 - filledRows;

        for (int row = 0; row < totalRows; row++) {
            int dy = row - radius;
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            int py = cy - radius + row;
            int x0 = cx - half;
            int x1 = cx + half;

            int bg = lerpColor(MANA_VOID_TOP, MANA_VOID_BOTTOM, (float) row / (totalRows - 1));
            g.fill(x0, py, x1 + 1, py + 1, bg);
            g.fill(x0, py, x0 + 1, py + 1, MANA_RIM_DARK);
            g.fill(x1, py, x1 + 1, py + 1, MANA_RIM_DARK);

            if (filledRows > 0 && py >= fillTop) {
                int index = py - fillTop;
                float t = filledRows <= 1 ? 0f : (float) index / (filledRows - 1);
                int color = t < 0.5f
                        ? lerpColor(MANA_TOP, MANA_MID, t * 2f)
                        : lerpColor(MANA_MID, MANA_BOTTOM, (t - 0.5f) * 2f);
                g.fill(x0 + 1, py, x1, py + 1, color);
                if (x1 - x0 >= 5) {
                    g.fill(x0 + 1, py, x0 + 2, py + 1, 0x33FFFFFF);
                }
            }
        }

        // Bright liquid surface.
        if (filledRows > 0 && filledRows < totalRows) {
            int row = totalRows - filledRows;
            int dy = row - radius;
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            int py = cy - radius + row;
            g.fill(cx - half + 1, py, cx + half, py + 1, 0xFFD6F2FF);
        }

        // Small bubbles only where there is liquid.
        if (filledRows > 8) {
            for (int i = 0; i < 3; i++) {
                float phase = (time * (0.28f + i * 0.07f) + i * 0.33f) % 1f;
                int by = cy + radius - 2 - Math.round(phase * (filledRows - 5));
                int bx = cx + (i - 1) * 3 + (i == 1 ? 1 : 0);
                if (by > fillTop + 1 && Math.abs(bx - cx) < radius - 2) {
                    g.fill(bx, by, bx + 1, by + 1, alpha(0xFFFFFFFF, 0x60));
                }
            }
        }

        // Glass highlights and cardinal anchors.
        g.fill(cx - 4, cy - 4, cx - 2, cy - 3, 0x66FFFFFF);
        g.fill(cx - 3, cy - 3, cx - 2, cy - 2, 0xAAFFFFFF);
        g.fill(cx - 1, cy - radius - 3, cx + 1, cy - radius - 1, alpha(GOLD, 0xC8));
        g.fill(cx - 1, cy + radius + 1, cx + 1, cy + radius + 3, alpha(GOLD_DEEP, 0xB8));
        g.fill(cx - radius - 3, cy - 1, cx - radius - 1, cy + 1, alpha(GOLD_DEEP, 0x90));
        g.fill(cx + radius + 1, cy - 1, cx + radius + 3, cy + 1, alpha(GOLD, 0xC8));
    }

    // ------------------------------------------------------------------
    // Icons and text
    // ------------------------------------------------------------------

    private static void drawHeart(GuiGraphicsExtractor g, int cx, int cy, int color) {
        g.fill(cx - 3, cy - 2, cx - 1, cy, color);
        g.fill(cx + 1, cy - 2, cx + 3, cy, color);
        g.fill(cx - 3, cy - 1, cx + 3, cy + 1, color);
        g.fill(cx - 2, cy + 1, cx + 2, cy + 2, color);
        g.fill(cx - 1, cy + 2, cx + 1, cy + 3, color);
        g.fill(cx - 2, cy - 2, cx - 1, cy - 1, 0x80FFFFFF);
    }

    private static void drawBolt(GuiGraphicsExtractor g, int cx, int cy) {
        g.fill(cx, cy - 4, cx + 2, cy - 1, 0xFFEFFFE8);
        g.fill(cx - 1, cy - 1, cx + 3, cy + 1, 0xFFD4FFC4);
        g.fill(cx - 2, cy + 1, cx + 1, cy + 2, 0xFF9BF7C2);
        g.fill(cx - 2, cy + 2, cx, cy + 4, 0xFFC8FFD8);
    }

    private static void centeredBarText(GuiGraphicsExtractor g, Font font, String text,
                                        int x, int y, int w, int h, int iconOffset) {
        int tx = x + (w - font.width(text) - 1) / 2 + iconOffset;
        int ty = y + (h - 9) / 2;
        g.text(font, text, tx, ty, TEXT, true);
    }

    private static void outlinedCenteredText(GuiGraphicsExtractor g, Font font,
                                             String text, int cx, int y) {
        int tx = cx - font.width(text) / 2;
        g.text(font, text, tx - 1, y, 0xFF04070C, false);
        g.text(font, text, tx + 1, y, 0xFF04070C, false);
        g.text(font, text, tx, y - 1, 0xFF04070C, false);
        g.text(font, text, tx, y + 1, 0xFF04070C, false);
        g.text(font, text, tx, y, 0xFFEAF9FF, true);
    }

    private static void gradientText(GuiGraphicsExtractor g, Font font, String text,
                                     int x, int y, int from, int to) {
        int total = Math.max(1, font.width(text));
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float t = (float) (cursor - x) / total;
            g.text(font, ch, cursor + 1, y + 1, 0xFF2A1B06, false);
            g.text(font, ch, cursor, y, lerpColor(from, to, t), false);
            cursor += font.width(ch);
        }
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        for (int end = text.length(); end > 0; end--) {
            String candidate = text.substring(0, end) + ellipsis;
            if (font.width(candidate) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

    private static String compactNumber(long value) {
        long magnitude = Math.abs(value);
        if (magnitude < 1000L) return Long.toString(value);
        if (magnitude < 1_000_000L) return trimDecimal(magnitude / 1000d) + "K";
        if (magnitude < 1_000_000_000L) return trimDecimal(magnitude / 1_000_000d) + "M";
        return trimDecimal(magnitude / 1_000_000_000d) + "B";
    }

    private static String trimDecimal(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text;
    }

    // ------------------------------------------------------------------
    // Panels and primitives
    // ------------------------------------------------------------------

    /** Dark chamfered panel with an accent edge, inner light and corner ornaments. */
    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h,
                              int accent, boolean ornate) {
        chamferedRect(g, x + 2, y + 3, w, h, 3, SHADOW);
        chamferedRect(g, x, y, w, h, 3, BORDER);
        chamferedGradient(g, x + 1, y + 1, w - 2, h - 2, 2, PANEL_TOP, PANEL_BOTTOM);

        g.fill(x + 7, y + 1, x + w - 7, y + 2, alpha(accent, 0xD8));
        g.fill(x + 12, y + 2, x + w - 12, y + 3, alpha(accent, 0x38));
        g.fill(x + 1, y + 8, x + 2, y + h - 8, alpha(accent, 0x42));
        g.fill(x + w - 2, y + 8, x + w - 1, y + h - 8, alpha(accent, 0x22));

        int corner = alpha(accent, 0xD0);
        g.fill(x + 3, y + 3, x + 5, y + 5, corner);
        g.fill(x + w - 5, y + 3, x + w - 3, y + 5, corner);
        g.fill(x + 3, y + h - 5, x + 5, y + h - 3, alpha(accent, 0x70));
        g.fill(x + w - 5, y + h - 5, x + w - 3, y + h - 3, alpha(accent, 0x70));
        if (ornate) {
            diamond(g, x + 8, y + 4, alpha(GOLD_LIGHT, 0xB8), alpha(GOLD_DEEP, 0xB8));
            diamond(g, x + w - 9, y + 4, alpha(GOLD_LIGHT, 0xB8), alpha(GOLD_DEEP, 0xB8));
        }
    }

    private static void chamferedRect(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                      int cut, int color) {
        if (w < cut * 2 || h < cut * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int row = 0; row < h; row++) {
            int inset = row < cut ? cut - row
                    : row >= h - cut ? row - (h - cut - 1)
                    : 0;
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private static void chamferedGradient(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                          int cut, int top, int bottom) {
        for (int row = 0; row < h; row++) {
            int inset = row < cut ? cut - row
                    : row >= h - cut ? row - (h - cut - 1)
                    : 0;
            int color = lerpColor(top, bottom, h <= 1 ? 0f : (float) row / (h - 1));
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int light, int dark) {
        int top = cy - 3;
        for (int row = 0; row < 7; row++) {
            int half = row <= 3 ? row : 6 - row;
            g.fill(cx - half, top + row, cx + half + 1, top + row + 1,
                    row < 3 ? light : dark);
        }
    }

    private static void fillCircleGradient(GuiGraphicsExtractor g, int cx, int cy,
                                           int radius, int top, int bottom) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            int color = lerpColor(top, bottom, (float) (dy + radius) / (radius * 2));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void fillCircle(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void strokeCircle(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            g.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 1, color);
            g.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    // ------------------------------------------------------------------
    // Colors and small math
    // ------------------------------------------------------------------

    private static int lowHealthTop() {
        float danger = 1f - clamp01((ANIMATION.health - 0.12f) / 0.18f);
        if (danger <= 0f) return HP_TOP;
        float flash = 0.5f + 0.5f * (float) Math.sin(ANIMATION.time * 7f);
        return lerpColor(HP_TOP, 0xFFFFE0E0, danger * (0.35f + 0.45f * flash));
    }

    private static int lowPulse(float value, float time, float threshold) {
        if (value >= threshold) return BORDER;
        float t = 1f - clamp01(value / threshold);
        return lerpColor(BORDER, 0xFFA22626, t * (0.35f + 0.4f * (0.5f + 0.5f * (float) Math.sin(time * 5.5f))));
    }

    private static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;

        int alpha = (int) (aa + (ba - aa) * t);
        int red = (int) (ar + (br - ar) * t);
        int green = (int) (ag + (bg - ag) * t);
        int blue = (int) (ab + (bb - ab) * t);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
