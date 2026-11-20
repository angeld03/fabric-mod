package com.wildbound.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.Wildbound;
import com.wildbound.attributes.DerivedStats;
import com.wildbound.attributes.DerivedStatsCalculator;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RpgAttachments;
import com.wildbound.progression.ExperienceCurve;
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

/** WILDBOUND HUD: compact level plaque + custom health/stamina/mana bars. Press K to toggle. */
public final class RpgHud {
    private static boolean enabled = true;
    private static boolean lastDown = false;

    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.wildbound.hud", InputConstants.KEY_K, KeyMapping.Category.MISC);

    private static final int GOLD = 0xFFFFD479;
    private static final int TEXT = 0xFFE6EEF8;
    private static final int MUTED = 0xFF9FB2C9;
    private static final int PANEL = 0xD418222F;
    private static final int BORDER = 0xFF0B1018;
    private static final int ACCENT = 0xFFFFC857;
    private static final int TRACK = 0xC8080D14;
    private static final int HP_COLOR = 0xFFE8484B;
    private static final int HP_DARK = 0xFF6E1E20;
    private static final int ST_COLOR = 0xFF57CE8E;
    private static final int MANA = 0xFF63B4FF;
    private static final int MANA_DARK = 0xFF122B4A;
    private static final int MANA_RIM = 0xFFBFE1FF;

    private RpgHud() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(TOGGLE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean down = TOGGLE.isDown();
            if (down && !lastDown) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.literal("HUD RPG: " + (enabled ? "ON" : "OFF")));
                }
            }
            lastDown = down;
        });

        // Wrap vanilla hearts: they show only while the RPG HUD is hidden,
        // so the player is never left without a health display.
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR,
                original -> (g, delta) -> {
                    if (!enabled) original.extractRenderState(g, delta);
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

        renderLevelPlaque(g, mc.font, profile);
        renderBottomBars(g, mc.font, player, profile);
    }

    /** Compact plaque: level + experience only. */
    private static void renderLevelPlaque(GuiGraphicsExtractor g, Font font, PlayerRpgProfile profile) {
        int x = 8;
        int y = 8;
        int w = 152;
        int h = 34;

        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, ACCENT);

        g.text(font, "WILDBOUND", x + 7, y + 7, GOLD, true);
        String level = "Lv " + profile.level();
        g.text(font, level, x + w - 7 - font.width(level), y + 7, GOLD, true);

        long needed = ExperienceCurve.required(profile.level());
        float progress = needed > 0 ? Math.min(1f, (float) profile.experience() / needed) : 1f;
        bar(g, x + 7, y + 20, w - 14, 5, progress, MANA);
        String xp = needed > 0 ? profile.experience() + " / " + needed : "MAX";
        g.text(font, xp, x + w - 7 - font.width(xp), y + 18, MUTED, false);
    }

    /** Numeric health bar on the left, stamina above the food bar, mana orb in the center. */
    private static void renderBottomBars(GuiGraphicsExtractor g, Font font,
                                         LocalPlayer player, PlayerRpgProfile profile) {
        int cx = g.guiWidth() / 2;
        int bottom = g.guiHeight();
        int y = bottom - 49;

        DerivedStats stats = DerivedStatsCalculator.calculate(profile);

        // Health: vanilla hearts removed; bar with numbers.
        int hx = cx - 91;
        bar(g, hx, y, 82, 9, player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 0f,
                HP_COLOR, HP_DARK);
        String hp = Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth());
        g.text(font, hp, hx + 41 - font.width(hp) / 2, y - 1, TEXT, true);

        // Stamina: above the vanilla food bar.
        int sx = cx + 11;
        bar(g, sx, y - 13, 82, 6,
                stats.maxStamina() > 0 ? profile.stamina() / stats.maxStamina() : 0f, ST_COLOR, TRACK);
        String st = "ST " + Math.round(profile.stamina());
        g.text(font, st, sx + 41 - font.width(st) / 2, y - 25, MUTED, false);

        // Mana: a small orb that fills from the bottom, between both bars.
        float manaProgress = stats.maxMana() > 0 ? Math.min(1f, profile.mana() / stats.maxMana()) : 0f;
        orb(g, cx, y + 2, 9, manaProgress);
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int color) {
        bar(g, x, y, w, h, progress, color, TRACK);
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                            float progress, int color, int track) {
        g.fill(x, y, x + w, y + h, track);
        int filled = (int) (w * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) g.fill(x, y, x + filled, y + h, color);
    }

    /** Pixel-art orb: radius 9, filled from the bottom according to mana. */
    private static void orb(GuiGraphicsExtractor g, int cx, int cy, int radius, float progress) {
        int filledRows = (int) (radius * 2 * Math.max(0f, Math.min(1f, progress)));
        for (int row = 0; row < radius * 2; row++) {
            double dy = row - radius + 0.5;
            double half = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
            int x0 = cx - (int) half;
            int x1 = cx + (int) half + 1;
            int py = cy - radius + row;
            boolean filled = row >= radius * 2 - filledRows;
            g.fill(x0, py, x1, py + 1, filled ? MANA : MANA_DARK);
            if (row == 0 || row == radius * 2 - 1) g.fill(x0, py, x1, py + 1, MANA_RIM);
        }
    }
}
