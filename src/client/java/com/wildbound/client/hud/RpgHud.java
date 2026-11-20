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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** Polished, toggleable RPG HUD. Press O to show/hide. */
public final class RpgHud {
    private static boolean enabled = true;

    private static final int PANEL_W = 180;
    private static final int PANEL_H = 94;
    private static final int BAR_W = 108;
    private static final int BAR_H = 6;

    private static final int COLOR_BORDER = 0xFF0E1420;
    private static final int COLOR_PANEL = 0xD41B2636;
    private static final int COLOR_ACCENT = 0xFFFFC857;
    private static final int COLOR_TITLE = 0xFFFFD479;
    private static final int COLOR_TEXT = 0xFFD6E2F0;
    private static final int COLOR_MUTED = 0xFF93A7BF;
    private static final int COLOR_TRACK = 0xFF0B1018;
    private static final int COLOR_XP = 0xFF53C8F5;
    private static final int COLOR_HP = 0xFFFF6B6B;
    private static final int COLOR_MP = 0xFF6BA8FF;
    private static final int COLOR_ST = 0xFF63D69A;

    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.wildbound.hud", InputConstants.KEY_O, KeyMapping.Category.MISC);

    private RpgHud() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(TOGGLE);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) enabled = !enabled;
        });
        HudElementRegistry.addLast(Wildbound.id("rpg_hud"), RpgHud::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
        if (profile == null) return;

        Font font = mc.font;
        int x = 8;
        int y = 8;

        // Panel with a crisp 1px border and a subtle accent line.
        g.fill(x, y, x + PANEL_W, y + PANEL_H, COLOR_BORDER);
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, COLOR_PANEL);
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + 3, COLOR_ACCENT);

        // Title row.
        g.text(font, "WILDBOUND", x + 8, y + 9, COLOR_TITLE, true);
        String level = "Lv " + profile.level();
        g.text(font, level, x + PANEL_W - 8 - font.width(level), y + 9, COLOR_TITLE, true);

        g.fill(x + 8, y + 25, x + PANEL_W - 8, y + 26, 0x40FFFFFF);

        // Experience bar.
        long needed = ExperienceCurve.required(profile.level());
        float progress = needed > 0 ? Math.min(1f, (float) profile.experience() / needed) : 1f;
        String xpText = needed > 0 ? "XP " + profile.experience() + " / " + needed : "XP MAX";
        g.text(font, xpText, x + 8, y + 30, COLOR_MUTED, false);
        bar(g, x + 8, y + 42, PANEL_W - 16, BAR_H, progress, COLOR_XP);

        DerivedStats stats = DerivedStatsCalculator.calculate(profile);
        int row = y + 56;
        resource(g, font, "HP", player.getHealth(), player.getMaxHealth(), row, COLOR_HP);
        resource(g, font, "MP", profile.mana(), stats.maxMana(), row + 13, COLOR_MP);
        resource(g, font, "ST", profile.stamina(), stats.maxStamina(), row + 26, COLOR_ST);
    }

    private static void resource(GuiGraphicsExtractor g, Font font, String label,
                                 float current, float max, int y, int color) {
        int x = 8;
        g.text(font, label, x + 8, y, color, true);
        bar(g, x + 30, y, BAR_W, BAR_H, max > 0 ? current / max : 0f, color);
        String value = Math.round(current) + "/" + Math.round(max);
        g.text(font, value, x + PANEL_W - 8 - font.width(value), y, COLOR_TEXT, false);
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int color) {
        g.fill(x, y, x + w, y + h, COLOR_TRACK);
        int filled = (int) (w * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) g.fill(x, y, x + filled, y + h, color);
    }
}
