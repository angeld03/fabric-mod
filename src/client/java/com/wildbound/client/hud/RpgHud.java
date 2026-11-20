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

/**
 * WILDBOUND HUD.
 *
 * <p>Top-left: level plaque with an experience bar.
 * Bottom: numeric health bar where vanilla hearts used to be, a stamina bar
 * directly above the food bar, and a mana orb that fills from the bottom in
 * the gap between both bars. Press K to hide/show everything.</p>
 */
public final class RpgHud {
    private static boolean enabled = true;
    private static boolean lastDown = false;

    private static final int BORDER = 0xFF04070C;
    private static final int PANEL_TOP = 0xE61D2A3C;
    private static final int PANEL_BOTTOM = 0xF00A1018;
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

    private RpgHud() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(TOGGLE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean down = TOGGLE.isDown();
            if (down && !lastDown) {
                enabled = !enabled;
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

        renderPlaque(g, mc.font, profile);
        renderVitals(g, mc.font, player, profile);
    }

    // ------------------------------------------------------------------
    // Top-left plaque
    // ------------------------------------------------------------------

    private static void renderPlaque(GuiGraphicsExtractor g, Font font, PlayerRpgProfile profile) {
        int x = 8;
        int y = 8;
        int w = 164;
        int h = 42;

        // Frame + vertical panel gradient.
        g.fill(x, y, x + w, y + h, BORDER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_TOP, PANEL_BOTTOM);

        // Gold top edge and a soft inner light.
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 3, GOLD_LIGHT, GOLD_DEEP);
        g.fill(x + 1, y + 3, x + w - 1, y + 4, 0x30FFFFFF);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, 0x40FFD479);

        // Small diamond ornament.
        diamond(g, x + 10, y + 12, GOLD_LIGHT, GOLD_DEEP);

        // Title with a horizontal gold gradient and a hard shadow.
        gradientText(g, font, "WILDBOUND", x + 21, y + 8, GOLD_LIGHT, GOLD_DEEP);

        // Level pill.
        String level = "LV " + profile.level();
        int pillW = font.width(level) + 10;
        int pillX = x + w - 8 - pillW;
        g.fill(pillX, y + 6, pillX + pillW, y + 21, BORDER);
        g.fillGradient(pillX + 1, y + 7, pillX + pillW - 1, y + 20, 0xFF241708, 0xFF120C04);
        g.text(font, level, pillX + 5, y + 9, GOLD, true);

        // Experience row.
        long needed = ExperienceCurve.required(profile.level());
        String xpValue = needed > 0 ? profile.experience() + " / " + needed : "MAX";
        g.text(font, "XP", x + 8, y + 27, MUTED, false);
        int valueW = font.width(xpValue);
        g.text(font, xpValue, x + w - 8 - valueW, y + 27, TEXT, false);

        int barX = x + 26;
        int barY = y + 35;
        int barW = w - 34;
        int barH = 5;
        float progress = needed > 0 ? Math.min(1f, (float) profile.experience() / needed) : 1f;
        gradientBar(g, barX, barY, barW, barH, progress, XP_TOP, XP_MID, XP_BOTTOM);
    }

    // ------------------------------------------------------------------
    // Bottom vitals
    // ------------------------------------------------------------------

    private static void renderVitals(GuiGraphicsExtractor g, Font font,
                                     LocalPlayer player, PlayerRpgProfile profile) {
        int cx = g.guiWidth() / 2;
        int baseY = g.guiHeight() - 39; // exactly where vanilla hearts/food live
        DerivedStats stats = DerivedStatsCalculator.calculate(profile);

        int barW = 81;
        int barH = 11; // 1px border + 9px body: text with shadow fits perfectly

        // Health, in the vanilla hearts slot.
        int hx = cx - 91;
        float healthPct = player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 0f;
        gradientBar(g, hx, baseY, barW, barH, healthPct, HP_TOP, HP_MID, HP_BOTTOM);
        String hp = Math.round(player.getHealth()) + " / " + Math.round(player.getMaxHealth());
        centeredBarText(g, font, hp, hx, baseY, barW, barH);

        // Stamina, directly above the food bar (2px gap).
        int sx = cx + 10;
        int sy = baseY - 13;
        float staminaPct = stats.maxStamina() > 0
                ? (float) (profile.stamina() / stats.maxStamina()) : 0f;
        gradientBar(g, sx, sy, barW, barH, staminaPct, ST_TOP, ST_MID, ST_BOTTOM);
        String st = "ST " + Math.round(profile.stamina()) + " / " + Math.round(stats.maxStamina());
        centeredBarText(g, font, st, sx, sy, barW, barH);

        // Mana orb in the 20px gap between both bars.
        float manaPct = stats.maxMana() > 0 ? (float) (profile.mana() / stats.maxMana()) : 0f;
        int orbY = baseY + 5;
        orb(g, cx, orbY, 9, manaPct);
        String mp = String.valueOf(Math.round(profile.mana()));
        g.text(font, mp, cx - font.width(mp) / 2, orbY - 4, TEXT, true);
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    /**
     * Bordered bar with a three-stop vertical gradient and a tiny gloss.
     * The border is drawn inside the requested rectangle, so the bar never
     * grows beyond its slot.
     */
    private static void gradientBar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                    float progress, int top, int mid, int bottom) {
        g.fill(x, y, x + w, y + h, BORDER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, TRACK_TOP, TRACK_BOTTOM);

        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = w - 2;
        int innerH = h - 2;
        int filled = (int) (innerW * clamp01(progress));
        if (filled <= 0) return;

        int midY = innerY + innerH / 2;
        g.fillGradient(innerX, innerY, innerX + filled, midY, top, mid);
        g.fillGradient(innerX, midY, innerX + filled, innerY + innerH, mid, bottom);

        if (filled > 2) {
            g.fill(innerX + 1, innerY + 1, innerX + filled - 1, innerY + 2, 0x55FFFFFF);
            g.fill(innerX + 1, innerY + innerH - 1, innerX + filled - 1, innerY + innerH, 0x40000000);
        }
        if (filled < innerW) {
            g.fill(innerX + filled, innerY, innerX + filled + 1, innerY + innerH, 0x66FFFFFF);
        }
    }

    private static void centeredBarText(GuiGraphicsExtractor g, Font font, String text,
                                        int x, int y, int w, int h) {
        int tx = x + (w - font.width(text) - 1) / 2;
        int ty = y + (h - 9) / 2 + 1;
        g.text(font, text, tx, ty, TEXT, true);
    }

    private static void gradientText(GuiGraphicsExtractor g, Font font, String text,
                                     int x, int y, int from, int to) {
        int total = font.width(text);
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float t = total > 0 ? (float) (cursor - x) / total : 0f;
            g.text(font, ch, cursor + 1, y + 1, 0xFF2A1B06, false);
            g.text(font, ch, cursor, y, lerpColor(from, to, t), false);
            cursor += font.width(ch);
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

    /** Pixel-art mana orb: a dark circle filled from the bottom with a gradient. */
    private static void orb(GuiGraphicsExtractor g, int cx, int cy, int radius, float progress) {
        int totalRows = radius * 2 + 1;
        int filledRows = Math.round(totalRows * clamp01(progress));
        int fillTop = cy + radius + 1 - filledRows;

        for (int row = 0; row < totalRows; row++) {
            int dy = row - radius;
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            int py = cy - radius + row;
            int x0 = cx - half;
            int x1 = cx + half;

            // Dark liquid container.
            int bg = lerpColor(MANA_VOID_TOP, MANA_VOID_BOTTOM, (float) row / (totalRows - 1));
            g.fill(x0, py, x1 + 1, py + 1, bg);
            g.fill(x0, py, x0 + 1, py + 1, MANA_RIM_DARK);
            g.fill(x1, py, x1 + 1, py + 1, MANA_RIM_DARK);

            // Filled part, brighter at the surface and deeper at the bottom.
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

        // Small pixel-art specular highlight on the upper left.
        g.fill(cx - 4, cy - 4, cx - 2, cy - 3, 0x66FFFFFF);
        g.fill(cx - 3, cy - 3, cx - 2, cy - 2, 0xAAFFFFFF);
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
