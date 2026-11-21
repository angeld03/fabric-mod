package com.wildbound.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RpgAttachments;
import com.wildbound.player.SkillProgress;
import com.wildbound.skills.SkillDefinition;
import com.wildbound.skills.SkillRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Animated, high-contrast mastery dashboard for the currently selected skill.
 * All animation is presentation-only; the server-owned profile remains the
 * sole source of mastery, XP and upgrade data.
 */
public final class SkillMasteryScreen extends Screen {
    private static final int GOLD = 0xFFFFD66E;
    private static final int CYAN = 0xFF65E7FF;
    private static final int VIOLET = 0xFFB28BFF;
    private static final int MINT = 0xFF79F2C0;
    private static final int WHITE = 0xFFF5FAFF;
    private static final int MUTED = 0xFFB7C7DA;
    private static final int PANEL = 0xF00B1422;
    private static final int TRACK = 0xFF07101B;

    private final String skillId;
    private final long openedAt = System.currentTimeMillis();

    public SkillMasteryScreen(String skillId) {
        super(Component.translatable("wildbound.mastery.title"));
        this.skillId = skillId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        float intro = easeOut(Math.min(1f, time / 0.72f));

        // Deep, layered backdrop with a slow aurora wash.
        g.fill(0, 0, w, h, 0xF5070D18);
        for (int i = 0; i < 5; i++) {
            float wave = 0.5f + 0.5f * (float) Math.sin(time * 0.42f + i * 1.31f);
            int alpha = 8 + Math.round(wave * 9f);
            int color = i % 2 == 0 ? 0xFF087E9C : 0xFF573A9A;
            g.fill(0, h * i / 5, w, h * (i + 1) / 5,
                    (alpha << 24) | (color & 0x00FFFFFF));
        }
        drawAmbientRunes(g, w, h, time);

        int panelW = Math.min(590, w - 24);
        int panelH = Math.min(344, h - 20);
        if (panelW < 220 || panelH < 170) {
            panelW = Math.max(180, w - 12);
            panelH = Math.max(150, h - 12);
        }
        int x = (w - panelW) / 2;
        int targetY = (h - panelH) / 2;
        int y = targetY + Math.round((1f - intro) * 18f);

        // Multi-layer frame, colored edge light and animated header rail.
        g.fill(x - 3, y - 3, x + panelW + 3, y + panelH + 3, 0xAA02050B);
        g.fill(x - 2, y - 2, x + panelW + 2, y + panelH + 2, 0xFF3D9BB4);
        g.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, 0xFFB38A49);
        g.fill(x, y, x + panelW, y + panelH, PANEL);
        g.fillGradient(x + 1, y + 1, x + panelW - 1, y + 4, 0xFFFFE7A3, 0xFF47DDF4);
        float sweep = (time * 110f) % (panelW + 80) - 40;
        g.fill(x + Math.round(sweep), y + 1, x + Math.round(sweep) + 34, y + 4, 0x99FFFFFF);

        String heading = tr("wildbound.mastery.title");
        drawCentered(g, heading, x, y + 12, panelW, GOLD, true);
        drawCentered(g, "✦  SKILL RESONANCE  ✦", x, y + 25, panelW, 0xFF65DDF4, false);

        SkillDefinition skill = SkillRegistry.get(skillId).orElse(null);
        if (skill == null) {
            drawCentered(g, tr("wildbound.mastery.missing"), x, y + 60, panelW, 0xFFFF879A, false);
            drawFooter(g, x, y, panelW, panelH);
            return;
        }

        PlayerRpgProfile profile = minecraft == null || minecraft.player == null ? null
                : minecraft.player.getAttached(RpgAttachments.PROFILE);
        SkillProgress progress = profile == null ? SkillProgress.INITIAL : profile.getSkillProgress(skillId);

        int contentX = x + 18;
        int contentW = panelW - 36;
        int heroY = y + 43;
        int heroH = 52;
        drawHeroCard(g, contentX, heroY, contentW, heroH, skill, progress, time);

        int xpY = heroY + heroH + 12;
        drawSectionLabel(g, tr("wildbound.mastery.rank", progress.rank(), SkillProgress.MAX_RANK),
                contentX, xpY, CYAN);
        String xpText = progress.rank() >= SkillProgress.MAX_RANK
                ? tr("wildbound.skills.mastery_max")
                : tr("wildbound.skills.mastery_xp", progress.experience(), progress.experienceRequired());
        g.text(font, xpText, contentX + contentW - font.width(xpText), xpY, GOLD, true);

        int barY = xpY + 15;
        drawProgressBar(g, contentX, barY, contentW, 12,
                progress.rank() >= SkillProgress.MAX_RANK ? 1f
                        : (float) progress.experience() / Math.max(1L, progress.experienceRequired()),
                time, intro, CYAN, VIOLET);

        int cardsY = barY + 24;
        int cardGap = 8;
        int cardW = (contentW - cardGap * 2) / 3;
        drawMetricCard(g, contentX, cardsY, cardW, 48,
                tr("wildbound.mastery.total_xp", compact(progress.totalExperience())),
                "✧", CYAN, time);
        drawMetricCard(g, contentX + cardW + cardGap, cardsY, cardW, 48,
                tr("wildbound.mastery.mastery", Math.round(progress.mastery())),
                "◆", VIOLET, time + 1.2f);
        drawMetricCard(g, contentX + (cardW + cardGap) * 2, cardsY, cardW, 48,
                tr("wildbound.mastery.upgrades", progress.upgrades().size()),
                "✦", MINT, time + 2.4f);

        int upgradesY = cardsY + 61;
        drawSectionLabel(g, tr("wildbound.mastery.upgrades", progress.upgrades().size()),
                contentX, upgradesY, GOLD);
        g.fill(contentX, upgradesY + 13, contentX + contentW, upgradesY + 14, 0x665E91A8);

        if (progress.upgrades().isEmpty()) {
            int emptyY = upgradesY + 24;
            g.fill(contentX, emptyY - 4, contentX + contentW, emptyY + 19, 0x553B6680);
            g.fill(contentX, emptyY - 4, contentX + 2, emptyY + 19, 0xFF55CDEB);
            g.text(font, tr("wildbound.mastery.no_upgrades"), contentX + 10, emptyY + 3,
                    MUTED, false);
        } else {
            int lineY = upgradesY + 21;
            int maxLines = Math.max(1, (y + panelH - 30 - lineY) / 15);
            for (int i = 0; i < Math.min(progress.upgrades().size(), maxLines); i++) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2f + i * 0.8f);
                int rowY = lineY + i * 15;
                g.fill(contentX, rowY - 2, contentX + contentW, rowY + 12,
                        0x553C9B83);
                g.fill(contentX, rowY - 2, contentX + 3, rowY + 12,
                        alpha(MINT, 0.55f + pulse * 0.45f));
                String label = "✦  " + progress.upgrades().get(i).replace('_', ' ');
                g.text(font, fit(label, contentW - 16), contentX + 9, rowY,
                        lerpColor(0xFFB8F8DF, 0xFFFFFFFF, pulse), true);
            }
        }

        drawFooter(g, x, y, panelW, panelH);
        drawBorderSparks(g, x, y, panelW, panelH, time);
    }

    private void drawHeroCard(GuiGraphicsExtractor g, int x, int y, int w, int h,
                              SkillDefinition skill, SkillProgress progress, float time) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.6f);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, alpha(CYAN, 0.18f + pulse * 0.12f));
        g.fillGradient(x, y, x + w, y + h, 0xE51B4560, 0xE5141A38);
        g.fill(x, y, x + 4, y + h, 0xFF55E5F5);
        g.fill(x + 5, y + h - 2, x + w - 4, y + h - 1, alpha(VIOLET, 0.35f + pulse * 0.35f));

        int badgeX = x + 13;
        int badgeY = y + 10;
        // Animated crystal/rank emblem built from crisp pixel geometry.
        int cx = badgeX + 14;
        int cy = badgeY + 14;
        int size = 10 + Math.round(pulse * 2f);
        g.fill(cx - size - 2, cy - size - 2, cx + size + 2, cy + size + 2,
                alpha(CYAN, 0.12f + pulse * 0.12f));
        g.fill(cx - size, cy - size, cx + size, cy + size, 0xFF153C58);
        g.fill(cx - 2, cy - size + 2, cx + 2, cy + size - 2, 0xFF8CF4FF);
        g.fill(cx - size + 2, cy - 2, cx + size - 2, cy + 2, 0xFF8CF4FF);
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFFFFF);

        int textX = x + 53;
        String name = fit(skill.name(), w - 150);
        g.text(font, name, textX, y + 10, WHITE, true);
        g.text(font, fit(skill.description(), w - 150), textX, y + 25, MUTED, false);

        String rank = "R" + progress.rank() + " / " + SkillProgress.MAX_RANK;
        int pillW = font.width(rank) + 18;
        int pillX = x + w - pillW - 10;
        int pillY = y + 15;
        g.fill(pillX - 1, pillY - 1, pillX + pillW + 1, pillY + 17, alpha(GOLD, 0.25f));
        g.fill(pillX, pillY, pillX + pillW, pillY + 16, 0xFF352A18);
        g.fillGradient(pillX + 1, pillY + 1, pillX + pillW - 1, pillY + 3,
                0xFFFFE8A6, 0xFFB77A27);
        drawCentered(g, rank, pillX, pillY + 5, pillW, GOLD, true);
    }

    private void drawProgressBar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                 float rawProgress, float time, float intro, int left, int right) {
        float target = Math.max(0f, Math.min(1f, rawProgress));
        float animated = target * easeOut(Math.min(1f, intro * 1.4f));
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xAA02070D);
        g.fill(x, y, x + w, y + h, TRACK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF172638);
        int fillW = Math.max(0, Math.round((w - 2) * animated));
        if (fillW > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + fillW, y + h - 1, left, right);
            g.fill(x + 1, y + 1, x + 1 + fillW, y + 3, 0xAAFFFFFF);
            int shineX = x + 1 + Math.round((time * 76f) % Math.max(1, fillW));
            if (shineX < x + 1 + fillW) {
                g.fill(shineX, y + 1, Math.min(x + 1 + fillW, shineX + 3), y + h - 1, 0x66FFFFFF);
            }
        }
        // Fine tick marks make each rank segment feel deliberate.
        for (int i = 1; i < 10; i++) {
            int tickX = x + Math.round(w * i / 10f);
            g.fill(tickX, y + 2, tickX + 1, y + h - 2, 0x6689B6CC);
        }
    }

    private void drawMetricCard(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                String value, String glyph, int accent, float time) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.1f);
        g.fill(x, y, x + w, y + h, 0xC9162638);
        g.fill(x, y, x + w, y + 2, alpha(accent, 0.55f + pulse * 0.4f));
        g.fill(x, y + 2, x + 2, y + h, alpha(accent, 0.3f));
        g.text(font, glyph, x + 7, y + 7, accent, true);
        String display = fit(value, w - 12);
        g.text(font, display, x + 7, y + 25, WHITE, true);
        g.fill(x + w - 9, y + 7, x + w - 5, y + 11, alpha(accent, 0.45f + pulse * 0.5f));
    }

    private void drawSectionLabel(GuiGraphicsExtractor g, String label, int x, int y, int color) {
        g.text(font, label, x, y, color, true);
        int dotX = x + font.width(label) + 7;
        if (dotX < x + 180) {
            g.fill(dotX, y + 4, dotX + 3, y + 7, color);
        }
    }

    private void drawAmbientRunes(GuiGraphicsExtractor g, int w, int h, float time) {
        for (int i = 0; i < 12; i++) {
            float phase = time * (0.22f + (i % 3) * 0.07f) + i * 1.71f;
            int px = Math.floorMod((int) (i * 97 + Math.sin(phase) * 38), Math.max(1, w));
            int py = Math.floorMod((int) (i * 53 + Math.cos(phase * 0.8f) * 24), Math.max(1, h));
            int color = i % 2 == 0 ? CYAN : VIOLET;
            int a = 18 + Math.round((0.5f + 0.5f * (float) Math.sin(phase * 1.7f)) * 30f);
            g.fill(px, py, px + 2, py + 2, alpha(color, a / 255f));
            if (i % 3 == 0) g.fill(px - 2, py + 1, px + 4, py + 2, alpha(color, 0.12f));
        }
    }

    private void drawBorderSparks(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
        for (int i = 0; i < 8; i++) {
            float phase = time * 1.8f + i * 0.79f;
            int px = x + 12 + Math.floorMod((int) (i * 71 + time * 24), Math.max(1, w - 24));
            int py = (i % 2 == 0) ? y + 5 : y + h - 6;
            int spark = 2 + Math.round((0.5f + 0.5f * (float) Math.sin(phase)) * 2f);
            g.fill(px - spark, py, px + spark + 1, py + 1, alpha(i % 2 == 0 ? CYAN : GOLD, 0.35f + 0.55f * (float) Math.sin(phase) * (float) Math.sin(phase)));
            g.fill(px, py - spark, px + 1, py + spark + 1, alpha(WHITE, 0.28f));
        }
    }

    private void drawFooter(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        String hint = tr("wildbound.mastery.back");
        int footerY = y + h - 20;
        g.fill(x + 12, footerY - 5, x + w - 12, footerY - 4, 0x665D7C98);
        drawCentered(g, hint, x, footerY, w, 0xFF8FEAFF, false);
    }

    private void drawCentered(GuiGraphicsExtractor g, String value, int x, int y, int w, int color, boolean shadow) {
        g.text(font, value, x + (w - font.width(value)) / 2, y, color, shadow);
    }

    private String fit(String value, int maxWidth) {
        if (value == null || maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return end == 0 ? suffix : value.substring(0, end) + suffix;
    }

    private static float easeOut(float value) {
        float inv = 1f - value;
        return 1f - inv * inv * inv;
    }

    private static int alpha(int color, float amount) {
        int a = Math.max(0, Math.min(255, Math.round(amount * 255f)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = (int) (((from >>> 24) & 255) + ((((to >>> 24) & 255) - ((from >>> 24) & 255)) * t));
        int r = (int) (((from >>> 16) & 255) + ((((to >>> 16) & 255) - ((from >>> 16) & 255)) * t));
        int gr = (int) (((from >>> 8) & 255) + ((((to >>> 8) & 255) - ((from >>> 8) & 255)) * t));
        int b = (int) ((from & 255) + (((to & 255) - (from & 255)) * t));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static String compact(long value) {
        if (value >= 1_000_000L) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000d);
        if (value >= 10_000L) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000d);
        return Long.toString(value);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_M) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
