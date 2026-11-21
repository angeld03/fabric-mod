package com.wildbound.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.Wildbound;
import com.wildbound.client.RadialSkillState;
import com.wildbound.client.WildboundClient;
import com.wildbound.network.RadialSkillsResultPayload;
import com.wildbound.network.SkillUnlockResultPayload;
import com.wildbound.network.UnlockSkillPayload;
import com.wildbound.network.UpdateRadialSkillsPayload;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RadialSkillConfig;
import com.wildbound.player.RpgAttachments;
import com.wildbound.realms.RealmRegistry;
import com.wildbound.skills.SkillCatalog;
import com.wildbound.skills.SkillDefinition;
import com.wildbound.skills.SkillElement;
import com.wildbound.skills.SkillRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The WILDBOUND skill menu.
 *
 * <p>A cosmic two-panel screen: the eighteen elements on the left, and the ten
 * skills of the selected element drawn as a glowing branch on the right.
 * Hovering a node shows its details in the footer; clicking asks the server to
 * unlock it. Nothing is unlocked locally: the server validates and the synced
 * profile updates the screen.</p>
 */
public final class SkillScreen extends Screen {
    private static final Identifier BACKGROUND = Wildbound.id("textures/gui/skill_background.png");

    // Palette shared with RpgHud
    private static final int BORDER = 0xFF04070C;
    private static final int PANEL_TOP = 0xE61D2A3C;
    private static final int PANEL_BOTTOM = 0xF00A1018;
    private static final int SHADOW = 0x66000000;
    private static final int GOLD = 0xFFFFD479;
    private static final int GOLD_LIGHT = 0xFFFFF0C0;
    private static final int GOLD_DEEP = 0xFFC98A2B;
    private static final int TEXT = 0xFFF4F9FF;
    private static final int MUTED = 0xFFA7B8CE;
    private static final int DIM = 0xFF73829A;
    private static final int TRACK_TOP = 0xFF060A10;
    private static final int TRACK_BOTTOM = 0xFF16202D;
    private static final int NODE_DARK = 0xFF0A1322;
    private static final int HP_TOP = 0xFFFF9184;
    private static final int HP_MID = 0xFFE23B3F;
    private static final int HP_BOTTOM = 0xFF8E161D;

    private static final int LOCKED = 0;
    private static final int READY = 1;
    private static final int UNLOCKED = 2;

    /** Gentle S-curve used by the ten nodes of the branch, in [-1, 1]. */
    private static final float[] CURVE = {
            0f, -0.32f, -0.66f, -0.52f, -0.14f, 0.24f, 0.60f, 0.46f, 0.12f, 0f
    };

    private final int[] elementHits = new int[18 * 4];
    private final int[] skillHits = new int[10 * 4];
    private int skillHitCount;
    private int lastMouseX;
    private int lastMouseY;

    private int selectedElement;
    private int hoveredElement = -1;
    private int hoveredSkill = -1;
    private int keyboardFocusedSkill = -1;

    // Explicit radial-slot management: select a slot, select an unlocked
    // skill, then press the visible Assign button. Double-click assignment is
    // kept as a shortcut.
    private final int[] radialSlotHits = new int[RadialSkillConfig.SLOT_COUNT * 4];
    private int radialTargetSlot;
    private String radialSkillId = "";
    private int assignX;
    private int assignY;
    private int assignW;
    private int assignH;
    private boolean assignHovered;

    private String pendingUnlockSkillId;
    private long pendingUnlockUntil;

    private int closeX;
    private int closeY;
    private int closeW;
    private int closeH;
    private boolean closeHovered;

    private String toast;
    private int toastColor = GOLD;
    private long toastUntil;

    private final long openedAt = System.currentTimeMillis();

    // Animation state
    private float skillPanelTime;
    private float elementSwitchTime = -1f;
    private int previousElement = -1;
    private final List<UnlockParticle> unlockParticles = new ArrayList<>();
    private final List<AmbientMote> ambientMotes = new ArrayList<>();

    public SkillScreen() {
        super(Component.translatable("wildbound.skills.title"));
        radialTargetSlot = Math.floorMod(RadialSkillState.selectedSlot(), RadialSkillConfig.SLOT_COUNT);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                0, 0, 0f, 0f, w, h, 960, 540, 0xFFFFFFFF);

        // Subtle vignette
        for (int i = 0; i < 4; i++) {
            int alpha = 0x14 + i * 0x0A;
            g.fill(i, i, w - i, h - i, alpha(0x000000, alpha));
        }

        // Realm-themed ambient color wash based on selected element
        List<SkillElement> elements = SkillCatalog.all();
        if (!elements.isEmpty() && selectedElement < elements.size()) {
            SkillElement el = elements.get(selectedElement);
            int color = el.primaryColor();
            int r = (color >>> 16) & 0xFF;
            int gr = (color >>> 8) & 0xFF;
            int b = color & 0xFF;
            float pulse = 0.03f + 0.02f * (float) Math.sin((System.currentTimeMillis() / 1000f) * 0.6f);
            g.fill(0, 0, w, h, ((int) (pulse * 255) << 24) | (r << 16) | (gr << 8) | b);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        skillPanelTime += delta;

        boolean compact = h < 330;
        int margin = compact ? 4 : 9;
        int headerH = compact ? 34 : 48;
        int footerH = compact ? 48 : 66;
        int contentY = margin + headerH + (compact ? 4 : 7);
        int contentH = h - contentY - footerH - margin - (compact ? 4 : 6);
        if (contentH < 70) contentH = 70;

        List<SkillElement> elements = SkillCatalog.all();
        if (selectedElement < 0 || selectedElement >= elements.size()) selectedElement = 0;
        SkillElement element = elements.isEmpty() ? null : elements.get(selectedElement);

        // Track element switches for transition animation
        if (previousElement != selectedElement) {
            if (previousElement >= 0) {
                elementSwitchTime = 0f;
            }
            previousElement = selectedElement;
        }
        if (elementSwitchTime >= 0f) {
            elementSwitchTime += delta;
            if (elementSwitchTime > 0.5f) elementSwitchTime = -1f;
        }

        PlayerRpgProfile profile = profile();

        // Update ambient motes
        updateAmbientMotes(g, w, h, time);
        updateUnlockParticles(delta);

        renderHeader(g, w, margin, headerH, profile, time, mouseX, mouseY);
        renderFooterBase(g, w, h, margin, footerH);

        int leftW;
        boolean elementNames;
        if (w >= 400) {
            elementNames = true;
            leftW = Math.max(150, Math.min(188, Math.round(w * 0.33f)));
        } else {
            elementNames = false;
            leftW = Math.max(96, Math.min(124, Math.round(w * 0.26f)));
        }
        renderElementPanel(g, elements, margin, contentY, leftW, contentH,
                time, mouseX, mouseY, elementNames);

        int rightX = margin + leftW + (compact ? 5 : 8);
        int rightW = w - rightX - margin;
        if (element != null && rightW > 80) {
            renderSkillPanel(g, element, profile, rightX, contentY, rightW, contentH,
                    compact, time, mouseX, mouseY);
        }

        renderFooterContent(g, w, h, margin, footerH, compact, element, profile);
        renderToast(g, w, h, margin, footerH);

        // Draw ambient motes on top of everything except header/footer
        drawAmbientMotes(g);
        drawUnlockParticles(g);
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    private void renderHeader(GuiGraphicsExtractor g, int w, int margin, int headerH,
                              PlayerRpgProfile profile, float time, int mouseX, int mouseY) {
        int x = margin;
        int y = margin;
        int width = w - margin * 2;
        panel(g, x, y, width, headerH, GOLD, true);

        // Title with letter spacing, centered.
        String title = tr("wildbound.skills.title");
        int titleW = spacedWidth(font, title, 1);
        int titleX = x + (width - titleW) / 2;
        int titleY = y + (headerH <= 34 ? 5 : 8);
        gradientText(g, title, titleX, titleY, GOLD_LIGHT, GOLD_DEEP, 1);

        if (headerH > 34) {
            String subtitle = tr("wildbound.skills.subtitle");
            int subX = x + (width - font.width(subtitle)) / 2;
            g.text(font, subtitle, subX, titleY + 14, DIM, false);

            // Realm progress indicator
            String realmName = latestRealm(profile);
            int realmX = x + (width - font.width(realmName)) / 2;
            g.text(font, realmName, realmX, titleY + 26, alpha(GOLD, 0xB0), false);
        }

        // Level pill on the left.
        String level = tr("wildbound.skills.level", profile.level());
        int levelW = font.width(level) + 12;
        int pillY = y + (headerH - 15) / 2;
        drawPill(g, x + 7, pillY, levelW, 15, 0x33FFD479, GOLD);
        g.text(font, level, x + 13, pillY + 4, GOLD_LIGHT, false);

        // Available points on the right; pulses while there is something to spend.
        String points = tr("wildbound.skills.points", profile.skillPoints());
        int pointsW = font.width(points) + 12;
        int pointsX = x + width - 13 - (headerH > 34 ? 14 : 11) - pointsW;
        boolean hasPoints = profile.skillPoints() > 0;
        int pulseAlpha = hasPoints ? 0x60 + (int) (0x50 * (0.5f + 0.5f * Math.sin(time * 3.2f))) : 0x30;
        drawPill(g, pointsX, pillY, pointsW, 15, alpha(0xFF9BE7FF, pulseAlpha), 0xFF9BE7FF);
        g.text(font, points, pointsX + 6, pillY + 4, hasPoints ? 0xFFDFF7FF : MUTED, false);

        // Close button.
        int size = headerH > 34 ? 14 : 11;
        closeW = size;
        closeH = size;
        closeX = x + width - size - 5;
        closeY = y + 5;
        closeHovered = inside(mouseX, mouseY, closeX, closeY, closeW, closeH);
        g.fill(closeX, closeY, closeX + closeW, closeY + closeH,
                closeHovered ? 0x88321A1A : 0x55101620);
        g.fill(closeX + 1, closeY + 1, closeX + closeW - 1, closeY + 2, 0x44FFD479);
        int cx0 = closeX + 3;
        int cy0 = closeY + 3;
        int cx1 = closeX + closeW - 4;
        int cy1 = closeY + closeH - 4;
        int cross = closeHovered ? 0xFFFF9A9A : MUTED;
        line(g, cx0, cy0, cx1, cy1, cross, 1);
        line(g, cx1, cy0, cx0, cy1, cross, 1);
    }

    // ------------------------------------------------------------------
    // Element selector panel
    // ------------------------------------------------------------------

    private void renderElementPanel(GuiGraphicsExtractor g, List<SkillElement> elements,
                                    int x, int y, int w, int h, float time,
                                    int mouseX, int mouseY, boolean withNames) {
        SkillElement selected = elements.isEmpty() ? null : elements.get(selectedElement);
        int accent = selected == null ? GOLD_DEEP : selected.primaryColor();
        panel(g, x, y, w, h, accent, false);

        String heading = tr("wildbound.skills.elements");
        g.text(font, heading, x + (w - font.width(heading)) / 2, y + 5, alpha(GOLD, 0xCC), false);
        g.fill(x + 6, y + 16, x + w - 6, y + 17, alpha(GOLD_DEEP, 0x55));

        // Element switch transition line
        if (elementSwitchTime >= 0f && elementSwitchTime < 0.5f) {
            float prog = elementSwitchTime / 0.5f;
            int lineW = Math.round((w - 12) * prog);
            int lineX = x + 6 + (w - 12 - lineW) / 2;
            int color = lerpColor(GOLD_DEEP, selected == null ? GOLD : selected.primaryColor(), prog);
            g.fill(lineX, y + 16, lineX + lineW, y + 17, alpha(color, 0xFF));
        }

        int listY = y + 20;
        int listH = h - 25;
        hoveredElement = -1;

        int cols = withNames ? 2 : 3;
        int rows = 18 / cols;
        float cellW = (w - 8f) / cols;
        float cellH = listH / (float) rows;

        for (int i = 0; i < elements.size(); i++) {
            SkillElement element = elements.get(i);
            int col = i % cols;
            int row = i / cols;
            int cellX = x + 4 + Math.round(cellW * col);
            int cellY = listY + Math.round(cellH * row);
            int cellWidth = Math.round(cellW);
            int cellHeight = Math.round(cellH);
            elementHits[i * 4] = cellX;
            elementHits[i * 4 + 1] = cellY;
            elementHits[i * 4 + 2] = cellWidth;
            elementHits[i * 4 + 3] = cellHeight;

            boolean hovered = inside(mouseX, mouseY, cellX, cellY, cellWidth, cellHeight);
            if (hovered) hoveredElement = i;
            boolean isSelected = i == selectedElement;

            if (isSelected) {
                // Selected cell highlight with breathing glow
                float breathe = 0.5f + 0.5f * (float) Math.sin(time * 2.5f);
                int glowAlpha = Math.round(0x24 + 0x14 * breathe);
                g.fill(cellX + 1, cellY + 1, cellX + cellWidth - 1, cellY + cellHeight - 1,
                        alpha(element.primaryColor(), glowAlpha));
                g.fill(cellX + 1, cellY + 2, cellX + 2, cellY + cellHeight - 2, element.primaryColor());
                g.fill(cellX + 1, cellY + 1, cellX + cellWidth - 1, cellY + 2,
                        alpha(element.primaryColor(), 0x66));
                // Subtle corner accents
                g.fill(cellX + 1, cellY + 1, cellX + 3, cellY + 3, alpha(element.primaryColor(), 0x88));
                g.fill(cellX + cellWidth - 3, cellY + 1, cellX + cellWidth - 1, cellY + 3, alpha(element.primaryColor(), 0x88));
            } else if (hovered) {
                g.fill(cellX + 1, cellY + 1, cellX + cellWidth - 1, cellY + cellHeight - 1, 0x14FFFFFF);
                g.fill(cellX + 1, cellY + 1, cellX + cellWidth - 1, cellY + 2, alpha(0xFFFFFF, 0x30));
            }

            int radius;
            int orbX;
            if (withNames) {
                radius = clamp(Math.round(cellHeight * 0.28f), 4, 8);
                orbX = cellX + 6 + radius;
            } else {
                radius = clamp(Math.round(Math.min(cellW, cellH) * 0.28f), 4, 8);
                orbX = cellX + cellWidth / 2;
            }
            int orbY = cellY + cellHeight / 2;

            drawElementOrb(g, element, orbX, orbY, radius, isSelected, hovered, time);

            if (withNames) {
                int textX = orbX + radius + 4;
                int maxW = cellX + cellWidth - textX - 3;
                String label = fit(element.displayName(), maxW);
                int color = isSelected ? element.primaryColor() : hovered ? TEXT : MUTED;
                g.text(font, label, textX, orbY - 4, color, isSelected);
            }
        }
    }

    // ------------------------------------------------------------------
    // Skill branch panel
    // ------------------------------------------------------------------

    private void renderSkillPanel(GuiGraphicsExtractor g, SkillElement element, PlayerRpgProfile profile,
                                  int x, int y, int w, int h, boolean compact, float time,
                                  int mouseX, int mouseY) {
        panel(g, x, y, w, h, element.primaryColor(), false);
        drawAmbientMotesInPanel(g, x, y, w, h, element, time);

        int pad = compact ? 6 : 10;
        int headH = compact ? 25 : 35;
        int orbR = compact ? 8 : 11;
        int orbX = x + pad + orbR;
        int orbY = y + headH / 2;
        drawElementOrb(g, element, orbX, orbY, orbR, true, false, time);

        int textX = orbX + orbR + 7;
        int nameY = y + (compact ? 3 : 6);
        gradientText(g, element.displayName(), textX, nameY,
                element.primaryColor(), lighten(element.primaryColor(), 0.55f), 0);
        int nameW = font.width(element.displayName());
        String alias = element.alias().toUpperCase(Locale.ROOT);
        g.text(font, alias, textX + nameW + 5, nameY, alpha(element.primaryColor(), 0xAA), false);

        int unlocked = element.unlockedCount(profile::hasSkill);
        int nextLevel = 0;
        for (SkillDefinition skill : element.skills()) {
            if (!profile.hasSkill(skill.id())) {
                nextLevel = skill.requiredLevel();
                break;
            }
        }
        String progress = unlocked >= element.skills().size()
                ? tr("wildbound.skills.mastered")
                : tr("wildbound.skills.element_progress", unlocked, element.skills().size())
                + (nextLevel > 0 ? "  ·  " + tr("wildbound.skills.required_level", nextLevel) : "");
        g.text(font, progress, textX, nameY + 11, MUTED, false);

        // Mastery bar under the header
        int barY = y + headH + 2;
        int barW = w - pad * 2;
        float mastery = (float) unlocked / element.skills().size();
        drawBar(g, x + pad, barY, barW, 4, mastery,
                element.primaryColor(), lighten(element.primaryColor(), 0.4f), GOLD_DEEP, time);
        if (mastery >= 1f) {
            g.fill(x + pad - 1, barY - 1, x + pad + barW + 1, barY, alpha(GOLD, 0x80));
            g.fill(x + pad - 1, barY + 4, x + pad + barW + 1, barY + 5, alpha(GOLD, 0x80));
        }

        int treeTop = y + headH + (compact ? 10 : 14);
        int treeBottom = y + h - (compact ? 5 : 8);
        int treeH = Math.max(20, treeBottom - treeTop);
        int centerX = x + Math.round(w * 0.32f);
        int amplitude = Math.round(Math.min(compact ? 36 : 56, w * 0.16f));
        int radius = clamp(Math.round(treeH / 9f * 0.34f), 4, 10);
        float step = (treeH - radius * 2f) / 9f;

        // Cosmic halo behind the branch - animated
        int midY = treeTop + treeH / 2;
        int halo = Math.min(treeH / 2, w / 3);
        for (int i = 5; i >= 1; i--) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.2f + i * 0.7f);
            int r = Math.max(3, Math.round(halo * i / 5f * (0.9f + 0.1f * pulse)));
            fillCircle(g, centerX, midY, r, alpha(element.primaryColor(), Math.round(0x09 * pulse)));
        }
        strokeCircle(g, centerX, midY, Math.max(4, halo), alpha(element.primaryColor(), 0x28));
        strokeCircle(g, centerX, midY, Math.max(3, Math.round(halo * 2 / 3f)), alpha(element.primaryColor(), 0x18));

        int[] nodeX = new int[10];
        int[] nodeY = new int[10];
        for (int i = 0; i < 10; i++) {
            nodeX[i] = centerX + Math.round(CURVE[i] * amplitude);
            nodeY[i] = treeTop + radius + Math.round(step * i);
        }

        skillHitCount = element.skills().size();
        hoveredSkill = -1;
        int[] labelX = new int[10];
        int[] labelY = new int[10];
        int[] labelW = new int[10];
        int labelH = compact ? 10 : 12;
        int labelsRight = x + w - 6;

        for (int i = 0; i < element.skills().size(); i++) {
            int lx = nodeX[i] + radius + 8;
            int maxW = labelsRight - lx;
            if (maxW < 44) {
                lx = Math.min(lx, x + w - 46);
                maxW = labelsRight - lx;
            }
            labelX[i] = lx;
            labelY[i] = nodeY[i] - labelH / 2;
            labelW[i] = Math.max(20, Math.min(maxW, font.width(element.skills().get(i).name()) + 12));

            int hitX = Math.min(nodeX[i] - radius - 2, labelX[i] - 1);
            int hitW = (labelX[i] + labelW[i] + 1) - hitX;
            int hitY = nodeY[i] - Math.max(radius, labelH / 2) - 1;
            int hitH = Math.max(radius, labelH / 2) * 2 + 2;
            skillHits[i * 4] = hitX;
            skillHits[i * 4 + 1] = hitY;
            skillHits[i * 4 + 2] = hitW;
            skillHits[i * 4 + 3] = hitH;

            if (inside(mouseX, mouseY, hitX, hitY, hitW, hitH)) hoveredSkill = i;
        }

        g.enableScissor(x + 1, y + headH + 8, x + w - 1, y + h - 1);
        try {
            // Connectors with animated motes
            for (int i = 1; i < element.skills().size(); i++) {
                int state = stateOf(profile, element, i);
                int prev = stateOf(profile, element, i - 1);
                int color;
                if (prev == UNLOCKED && state == UNLOCKED) {
                    color = lighten(element.primaryColor(), 0.35f);
                } else if (prev == UNLOCKED && state == READY) {
                    color = alpha(element.primaryColor(), 0xCC);
                } else {
                    color = 0x33FFFFFF;
                }
                drawConnector(g, nodeX[i - 1], nodeY[i - 1], nodeX[i], nodeY[i], color, time, i);
            }

            // Draw nodes
            for (int i = 0; i < element.skills().size(); i++) {
                SkillDefinition definition = element.skills().get(i);
                int state = stateOf(profile, element, i);
                boolean hovered = hoveredSkill == i;
                boolean keyFocused = keyboardFocusedSkill == i && !hovered;
                drawSkillNode(g, element, definition, i, state, nodeX[i], nodeY[i], radius,
                        hovered || keyFocused, time, keyFocused);
                drawSkillLabel(g, element, definition, i, state, hovered,
                        labelX[i], labelY[i], labelW[i], labelH);
            }
        } finally {
            g.disableScissor();
        }
    }

    private int stateOf(PlayerRpgProfile profile, SkillElement element, int index) {
        SkillDefinition definition = element.skills().get(index);
        if (profile.hasSkill(definition.id())) return UNLOCKED;
        boolean prerequisite = index == 0 || profile.hasSkill(element.skills().get(index - 1).id());
        return prerequisite && profile.level() >= definition.requiredLevel() ? READY : LOCKED;
    }

    private void drawSkillNode(GuiGraphicsExtractor g, SkillElement element, SkillDefinition definition,
                               int index, int state, int x, int y, int radius,
                               boolean hovered, float time, boolean keyFocused) {
        int primary = element.primaryColor();
        boolean ultimate = index == 9;

        // State-specific pulse
        float pulse;
        int glowColor;
        if (state == UNLOCKED) {
            pulse = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(time * 2.0f + index * 0.3f));
            glowColor = alpha(primary, hovered ? 0x7A : Math.round(0x30 + 0x20 * pulse));
            if (definition.id().equals(radialSkillId)) glowColor = alpha(GOLD_LIGHT, 0x90);
        } else if (state == READY) {
            pulse = 0.5f + 0.5f * (float) Math.sin(time * 4.0f + index * 0.5f);
            glowColor = alpha(GOLD, hovered ? 0xA0 : Math.round(0x60 + 0x40 * pulse));
        } else {
            pulse = 0f;
            glowColor = hovered ? 0x33FFFFFF : 0x18FFFFFF;
        }

        // Outer glow
        if (state != LOCKED) {
            int outerR = radius + 4 + (int) (2f * pulse);
            fillCircle(g, x, y, outerR, alpha(glowColor, (glowColor >>> 24) / 3));
            fillCircle(g, x, y, outerR - 2, glowColor);
        } else if (hovered) {
            fillCircle(g, x, y, radius + 4, alpha(0x33FFFFFF, 0x10));
        }

        // Orbiting particles for READY nodes
        if (state == READY && !hovered) {
            for (int k = 0; k < 3; k++) {
                float angle = time * 3.5f + index * 1.2f + k * 2.094f; // 120 degrees apart
                int px = x + Math.round((radius + 5 + 2f * (float) Math.sin(time * 1.5f + k)) * (float) Math.cos(angle));
                int py = y + Math.round((radius + 5 + 2f * (float) Math.sin(time * 1.5f + k)) * (float) Math.sin(angle));
                int a = Math.round(0x60 + 0x40 * (0.5f + 0.5f * (float) Math.sin(time * 2.5f + k)));
                g.fill(px, py, px + 1, py + 1, alpha(GOLD, a));
                if (k == 0) {
                    g.fill(px - 1, py, px + 2, py + 1, alpha(GOLD, a / 3));
                    g.fill(px, py - 1, px + 1, py + 2, alpha(GOLD, a / 3));
                }
            }
        }

        // Ultimate node extra decoration
        if (ultimate) {
            strokeCircle(g, x, y, radius + 3, alpha(GOLD, state == UNLOCKED ? 0xAA : 0x55));
            spark(g, x, y - radius - 4, GOLD_LIGHT);
            spark(g, x, y + radius + 4, GOLD_LIGHT);
            spark(g, x - radius - 4, y, GOLD_LIGHT);
            spark(g, x + radius + 4, y, GOLD_LIGHT);
        }

        // Main ring
        fillCircle(g, x, y, radius + 1, BORDER);
        int ring = state == UNLOCKED ? lighten(primary, 0.35f)
                : state == READY ? GOLD
                : hovered ? 0xFF8A98AD : 0xFF5C6B82;
        if (keyFocused) ring = GOLD;
        strokeCircle(g, x, y, radius, ring);
        fillCircle(g, x, y, Math.max(1, radius - 1), state == UNLOCKED ? NODE_DARK : 0xFF0E1828);

        // Inner fill
        if (state == UNLOCKED) {
            fillCircle(g, x, y, Math.max(1, radius - 3), alpha(primary, 0x66));
            fillCircle(g, x, y, Math.max(1, radius - 6), alpha(primary, 0xAA));
            // Center gem
            fillCircle(g, x, y, Math.max(1, radius - 8), alpha(GOLD_LIGHT, 0x88));
        } else if (state == READY) {
            float readyPulse = 0.5f + 0.5f * (float) Math.sin(time * 3.0f);
            fillCircle(g, x, y, Math.max(1, radius - 3), alpha(GOLD, Math.round(0x22 + 0x18 * readyPulse)));
            // Center pulse
            fillCircle(g, x, y, Math.max(1, radius - 6), alpha(GOLD, Math.round(0x40 + 0x30 * readyPulse)));
        }

        // Number or checkmark
        if (radius >= 7) {
            String number;
            int color;
            if (state == UNLOCKED) {
                number = "✓";
                color = 0xFFFFFFFF;
            } else {
                number = String.valueOf(index + 1);
                color = state == READY ? GOLD_LIGHT : MUTED;
            }
            g.text(font, number, x - font.width(number) / 2, y - 4, color, true);
        } else if (state == UNLOCKED) {
            g.fill(x, y - 1, x + 1, y, 0xFFFFFFFF);
        }

        // Keyboard focus ring
        if (keyFocused && !hovered) {
            strokeCircle(g, x, y, radius + 2, alpha(GOLD, 0xC0 + (int) (0x3F * (float) Math.sin(time * 5f))));
            strokeCircle(g, x, y, radius + 4, alpha(GOLD, 0x60 + (int) (0x30 * (float) Math.sin(time * 5f + 1f))));
        }
    }

    private void drawSkillLabel(GuiGraphicsExtractor g, SkillElement element, SkillDefinition definition,
                                int index, int state, boolean hovered,
                                int x, int y, int w, int h) {
        int color;
        if (state == UNLOCKED) {
            color = TEXT;
        } else if (state == READY) {
            color = GOLD_LIGHT;
        } else {
            color = DIM;
        }
        if (hovered) color = 0xFFFFFFFF;
        boolean radialSelection = definition.id().equals(radialSkillId);

        int fill;
        if (radialSelection) {
            fill = 0xCC284761;
        } else if (hovered) {
            fill = 0xB00C1526;
        } else if (state == UNLOCKED) {
            fill = 0x48040A12;
        } else if (state == READY) {
            fill = 0x44101820;
        } else {
            fill = 0x30040A12;
        }
        g.fill(x, y, x + w, y + h, fill);

        int edge = state == UNLOCKED ? alpha(element.primaryColor(), 0x55)
                : state == READY ? alpha(GOLD, 0x66)
                : 0x22FFFFFF;
        if (hovered) edge = element.primaryColor();
        if (radialSelection) edge = GOLD_LIGHT;
        g.fill(x, y, x + w, y + 1, edge);
        if (hovered) {
            g.fill(x, y + h - 1, x + w, y + h, alpha(element.primaryColor(), 0x88));
        }

        int dot = state == UNLOCKED ? 0xFF8CFFB8 : state == READY ? GOLD : 0xFF5F6C82;
        g.fill(x + 3, y + h / 2 - 1, x + 5, y + h / 2 + 1, dot);

        String name = fit(definition.name(), w - 12);
        g.text(font, name, x + 6, y + (h - 9) / 2, color, hovered || state != LOCKED);

        if (state != UNLOCKED && w > font.width(name) + 24) {
            String cost = String.valueOf(definition.pointCost());
            int costColor = state == READY ? GOLD : DIM;
            g.text(font, cost, x + w - 4 - font.width(cost), y + (h - 9) / 2, costColor, false);
            g.fill(x + w - 9, y + (h - 9) / 2, x + w - 8, y + (h - 9) / 2 + 1, costColor);
        }
    }

    private void drawConnector(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1,
                               int color, float time, int index) {
        // Base line with glow
        glowLine(g, x0, y0, x1, y1, color);

        // Drifting motes along the connector
        int steps = 5;
        for (int i = 0; i < steps; i++) {
            float t = (i + (time * 0.3f) % 1f) / steps;
            int mx = Math.round(x0 + (x1 - x0) * t);
            int my = Math.round(y0 + (y1 - y0) * t);
            float wobble = (float) Math.sin(time * 2.5f + index * 0.8f + i * 1.2f) * 1.5f;
            int a = Math.round(0x30 + 0x20 * (0.5f + 0.5f * (float) Math.sin(time * 1.8f + i)));
            g.fill(mx + (int) wobble, my, mx + (int) wobble + 1, my + 1, alpha(color, a));
        }
    }

    // ------------------------------------------------------------------
    // Footer + feedback
    // ------------------------------------------------------------------

    private void renderFooterBase(GuiGraphicsExtractor g, int w, int h, int margin, int footerH) {
        int y = h - margin - footerH;
        panel(g, margin, y, w - margin * 2, footerH, GOLD_DEEP, true);
    }

    private void renderFooterContent(GuiGraphicsExtractor g, int w, int h, int margin, int footerH,
                                     boolean compact, SkillElement element, PlayerRpgProfile profile) {
        int x = margin + 7;
        int y = h - margin - footerH;

        renderRadialToolbar(g, w, margin, y, profile);

        int detailY = y + 30;
        g.fill(x, detailY - 4, w - margin - 7, detailY - 3, alpha(GOLD_DEEP, 0x42));

        SkillDefinition focused = focusedSkillDefinition(element);
        if (focused != null) {
            int focusedIndex = indexOfSkill(element, focused);
            int state = stateOf(profile, element, focusedIndex);
            int color = state == UNLOCKED ? 0xFF8CFFB8 : state == READY ? GOLD_LIGHT : 0xFFFF9AA6;

            String name = focused.name();
            g.text(font, fit(name, w - margin * 2 - 16), x, detailY, color, true);

            String meta = tr("wildbound.skills.rank", focusedIndex + 1)
                    + "  ·  " + tr("wildbound.skills.required_level", focused.requiredLevel())
                    + "  ·  " + tr("wildbound.skills.cost_value", focused.pointCost());
            g.text(font, fit(meta, w - margin * 2 - font.width(name) - 18),
                    x + font.width(name) + 8, detailY, MUTED, false);

            if (!compact) {
                drawWrappedText(g, font, focused.description(), x, detailY + 12,
                        w - margin * 2 - 14, DIM);
            }

            String stateText = state == UNLOCKED ? tr("wildbound.skills.state_unlocked")
                    : state == READY ? tr("wildbound.skills.state_ready")
                    : tr("wildbound.skills.state_locked");
            g.text(font, stateText, w - margin - 7 - font.width(stateText), detailY, color, true);
        } else {
            String hint = tr("wildbound.skills.hint");
            g.text(font, fit(hint, w - margin * 2 - 16), x, detailY, MUTED, false);
            String close = tr("wildbound.skills.close_hint");
            g.text(font, close, w - margin - 7 - font.width(close), detailY, DIM, false);
        }
    }

    /** Always-visible fallback for managing the five radial slots. */
    private void renderRadialToolbar(GuiGraphicsExtractor g, int w, int margin, int y,
                                     PlayerRpgProfile profile) {
        int x = margin + 7;
        int right = w - margin - 7;
        int toolbarY = y + 4;
        int toolbarH = 20;

        String label = tr("wildbound.skills.radial_slots");
        g.text(font, label, x, toolbarY + 6, alpha(GOLD, 0xD0), false);

        assignW = compactAssignWidth(w);
        assignH = toolbarH;
        assignX = right - assignW;
        assignY = toolbarY;

        int slotsX = x + font.width(label) + 7;
        int slotsRight = assignX - 7;
        int gap = 3;
        int slotWidth = Math.max(18, Math.min(30, (slotsRight - slotsX - gap * 4) / 5));
        List<String> radial = RadialSkillState.slots(profile);

        for (int i = 0; i < RadialSkillConfig.SLOT_COUNT; i++) {
            int slotX = slotsX + i * (slotWidth + gap);
            int slotY = toolbarY;
            radialSlotHits[i * 4] = slotX;
            radialSlotHits[i * 4 + 1] = slotY;
            radialSlotHits[i * 4 + 2] = slotWidth;
            radialSlotHits[i * 4 + 3] = toolbarH;

            boolean selected = i == radialTargetSlot;
            g.fill(slotX, slotY, slotX + slotWidth, slotY + toolbarH,
                    selected ? 0xCC284761 : 0x99101820);
            g.fill(slotX, slotY, slotX + slotWidth, slotY + 1,
                    selected ? GOLD_LIGHT : alpha(GOLD_DEEP, 0x80));
            g.fill(slotX, slotY, slotX + 1, slotY + toolbarH,
                    selected ? GOLD : alpha(GOLD_DEEP, 0x55));

            String number = String.valueOf(i + 1);
            g.text(font, number, slotX + (slotWidth - font.width(number)) / 2,
                    slotY + 6, selected ? GOLD_LIGHT : MUTED, selected);

            String skillId = i < radial.size() ? radial.get(i) : RadialSkillConfig.EMPTY;
            if (skillId != null && !skillId.isEmpty()) {
                SkillDefinition definition = SkillCatalog.getSkillDefinition(skillId);
                SkillElement skillElement = definition == null ? null
                        : SkillCatalog.forSkill(skillId).orElse(null);
                int color = skillElement == null ? GOLD : skillElement.primaryColor();
                g.fill(slotX + slotWidth / 2 - 2, slotY + toolbarH - 4,
                        slotX + slotWidth / 2 + 2, slotY + toolbarH - 2, color);
            }
        }

        boolean canAssign = !radialSkillId.isEmpty() && profile.hasSkill(radialSkillId);
        assignHovered = canAssign && inside(lastMouseX, lastMouseY, assignX, assignY, assignW, assignH);
        g.fill(assignX, assignY, assignX + assignW, assignY + assignH,
                canAssign ? (assignHovered ? 0xCC355F80 : 0x99203950)
                        : 0x66101820);
        g.fill(assignX, assignY, assignX + assignW, assignY + 1,
                canAssign ? GOLD : alpha(GOLD_DEEP, 0x45));
        String assignText = tr("wildbound.skills.assign_to_slot", radialTargetSlot + 1);
        g.text(font, fit(assignText, assignW - 10),
                assignX + (assignW - font.width(fit(assignText, assignW - 10))) / 2,
                assignY + 6, canAssign ? GOLD_LIGHT : DIM, canAssign);
    }

    private int compactAssignWidth(int screenWidth) {
        return screenWidth < 380 ? 88 : 118;
    }

    private SkillDefinition focusedSkillDefinition(SkillElement element) {
        if (element == null) return null;
        if (!radialSkillId.isEmpty()) {
            for (SkillDefinition definition : element.skills()) {
                if (definition.id().equals(radialSkillId)) return definition;
            }
        }
        if (hoveredSkill >= 0 && hoveredSkill < element.skills().size()) {
            return element.skills().get(hoveredSkill);
        }
        return null;
    }

    private int indexOfSkill(SkillElement element, SkillDefinition definition) {
        for (int i = 0; i < element.skills().size(); i++) {
            if (element.skills().get(i).id().equals(definition.id())) return i;
        }
        return 0;
    }

    private void drawWrappedText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int color) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = y;
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (font.width(test) > maxWidth) {
                g.text(font, line.toString(), x, lineY, color, false);
                lineY += 10;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            g.text(font, line.toString(), x, lineY, color, false);
        }
    }

    private void renderToast(GuiGraphicsExtractor g, int w, int h, int margin, int footerH) {
        long now = System.currentTimeMillis();
        if (toast == null || now >= toastUntil) return;
        int alpha = 0xFF;
        long left = toastUntil - now;
        if (left < 500) alpha = (int) (0xFF * left / 500L);
        if (alpha <= 0) return;

        int width = font.width(toast) + 18;
        int x = (w - width) / 2;
        int y = h - margin - footerH - 18;
        panel(g, x, y, width, 14, toastColor, false);
        g.text(font, toast, x + 9, y + 3, alpha(toastColor, alpha), true);
    }

    private void showToast(String message, int color) {
        toast = message;
        toastColor = color;
        toastUntil = System.currentTimeMillis() + 3200L;
    }

    public void onUnlockResult(SkillUnlockResultPayload payload) {
        if (pendingUnlockSkillId != null && pendingUnlockSkillId.equals(payload.skillId())) {
            pendingUnlockSkillId = null;
            pendingUnlockUntil = 0L;
        }
        if (payload.success()) {
            String name = SkillRegistry.get(payload.skillId())
                    .map(SkillDefinition::name)
                    .orElse(payload.skillId());
            radialSkillId = payload.skillId();
            showToast(tr("wildbound.skills.status_unlocked", name), 0xFF8CFFB8);
            // Spawn unlock particles at the hovered skill position
            if (hoveredSkill >= 0) {
                int[] hit = new int[4];
                // We don't have the exact position here, but we can add particles at center
                // They'll be drawn in the skill panel area
                for (int i = 0; i < 12; i++) {
                    unlockParticles.add(new UnlockParticle());
                }
            }
        } else {
            showToast(reasonText(payload.reason()), 0xFFFF8A98);
        }
    }

    /** Called from the client packet receiver; works even if the menu was closed. */
    public static void handleResult(SkillUnlockResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof SkillScreen screen) {
            screen.onUnlockResult(payload);
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player != null) {
            String message = payload.success()
                    ? tr("wildbound.skills.status_unlocked", SkillRegistry.get(payload.skillId())
                    .map(SkillDefinition::name).orElse(payload.skillId()))
                    : reasonText(payload.reason());
            player.sendOverlayMessage(Component.literal(message));
        }
    }

    private static String reasonText(String reason) {
        return switch (reason) {
            case "unknown_skill" -> tr("wildbound.skills.reason.unknown_skill");
            case "already_unlocked" -> tr("wildbound.skills.reason.already_unlocked");
            case "level_too_low" -> tr("wildbound.skills.reason.level_too_low");
            case "not_enough_points" -> tr("wildbound.skills.reason.not_enough_points");
            case "realm_locked" -> tr("wildbound.skills.reason.realm_locked");
            case "missing_prerequisite" -> tr("wildbound.skills.reason.missing_prerequisite");
            default -> tr("wildbound.skills.reason.unknown_skill");
        };
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        if (closeHovered) {
            onClose();
            return true;
        }

        for (int i = 0; i < RadialSkillConfig.SLOT_COUNT; i++) {
            if (inside(event.x(), event.y(), radialSlotHits[i * 4], radialSlotHits[i * 4 + 1],
                    radialSlotHits[i * 4 + 2], radialSlotHits[i * 4 + 3])) {
                radialTargetSlot = i;
                RadialSkillState.selectSlot(i);
                showToast(tr("wildbound.skills.radial_slot_selected", i + 1), GOLD);
                return true;
            }
        }

        if (inside(event.x(), event.y(), assignX, assignY, assignW, assignH)) {
            assignSelectedSkillToRadial();
            return true;
        }

        List<SkillElement> elements = SkillCatalog.all();
        for (int i = 0; i < elements.size(); i++) {
            if (inside(event.x(), event.y(), elementHits[i * 4], elementHits[i * 4 + 1],
                    elementHits[i * 4 + 2], elementHits[i * 4 + 3])) {
                selectedElement = i;
                hoveredSkill = -1;
                keyboardFocusedSkill = -1;
                radialSkillId = "";
                return true;
            }
        }
        SkillElement element = elements.isEmpty() ? null : elements.get(selectedElement);
        if (element != null) {
            for (int i = 0; i < skillHitCount && i < element.skills().size(); i++) {
                if (inside(event.x(), event.y(), skillHits[i * 4], skillHits[i * 4 + 1],
                        skillHits[i * 4 + 2], skillHits[i * 4 + 3])) {
                    handleSkillClick(element, i, doubleClick);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            cycleElement(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (WildboundClient.OPEN_SKILLS.matches(event) || event.key() == InputConstants.KEY_ESCAPE) {
            while (WildboundClient.OPEN_SKILLS.consumeClick()) {
            }
            onClose();
            return true;
        }
        if (WildboundClient.OPEN_RADIAL.matches(event)) {
            // Open the radial directly from the fallback screen. This also
            // makes a rebound radial shortcut work everywhere.
            while (WildboundClient.OPEN_RADIAL.consumeClick()) {
            }
            Minecraft.getInstance().setScreenAndShow(new SkillRadialMenu());
            return true;
        }
        if (event.key() >= InputConstants.KEY_1 && event.key() <= InputConstants.KEY_5) {
            radialTargetSlot = event.key() - InputConstants.KEY_1;
            RadialSkillState.selectSlot(radialTargetSlot);
            showToast(tr("wildbound.skills.radial_slot_selected", radialTargetSlot + 1), GOLD);
            return true;
        }
        if (event.key() == InputConstants.KEY_RIGHT) {
            cycleElement(1);
            return true;
        }
        if (event.key() == InputConstants.KEY_LEFT) {
            cycleElement(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_UP) {
            cycleSkillFocus(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_DOWN) {
            cycleSkillFocus(1);
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) {
            if (!radialSkillId.isEmpty() && profile().hasSkill(radialSkillId)) {
                SkillDefinition selected = SkillCatalog.getSkillDefinition(radialSkillId);
                if (selected != null) {
                    assignUnlockedSkillToRadial(selected);
                    return true;
                }
            }
            if (keyboardFocusedSkill >= 0) {
                SkillElement element = SkillCatalog.all().isEmpty() ? null
                        : SkillCatalog.all().get(selectedElement);
                if (element != null) {
                    handleSkillClick(element, keyboardFocusedSkill, false);
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void cycleElement(int direction) {
        List<SkillElement> elements = SkillCatalog.all();
        if (elements.isEmpty()) return;
        selectedElement = Math.floorMod(selectedElement + direction, elements.size());
        hoveredSkill = -1;
        keyboardFocusedSkill = -1;
    }

    private void cycleSkillFocus(int direction) {
        SkillElement element = SkillCatalog.all().isEmpty() ? null
                : SkillCatalog.all().get(selectedElement);
        if (element == null) return;
        int count = element.skills().size();
        if (count == 0) return;
        if (keyboardFocusedSkill < 0) {
            keyboardFocusedSkill = 0;
        } else {
            keyboardFocusedSkill = Math.floorMod(keyboardFocusedSkill + direction, count);
        }
        hoveredSkill = keyboardFocusedSkill;
    }

    private void handleSkillClick(SkillElement element, int index, boolean doubleClick) {
        SkillDefinition definition = element.skills().get(index);
        PlayerRpgProfile profile = profile();

        if (!profile.hasSkill(definition.id())) {
            attemptUnlock(element, index);
            return;
        }

        radialSkillId = definition.id();
        keyboardFocusedSkill = index;
        if (doubleClick) {
            assignUnlockedSkillToRadial(definition);
            return;
        }

        showToast(tr("wildbound.skills.skill_selected_for_slot", definition.name(),
                radialTargetSlot + 1), GOLD);
    }

    /** Assigns the skill selected in the tree to the currently selected radial slot. */
    private void assignSelectedSkillToRadial() {
        if (radialSkillId.isEmpty()) {
            showToast(tr("wildbound.skills.select_skill_first"), 0xFFFF8A98);
            return;
        }
        SkillDefinition definition = SkillCatalog.getSkillDefinition(radialSkillId);
        if (definition == null) {
            showToast(tr("wildbound.skills.reason.unknown_skill"), 0xFFFF8A98);
            return;
        }
        assignUnlockedSkillToRadial(definition);
    }

    /** Assigns an unlocked skill to the selected radial slot. */
    private void assignUnlockedSkillToRadial(SkillDefinition definition) {
        PlayerRpgProfile profile = profile();
        if (!profile.hasSkill(definition.id())) {
            showToast(tr("wildbound.skills.radial_not_unlocked"), 0xFFFF8A98);
            return;
        }

        List<String> radial = RadialSkillState.slots(profile);
        int targetSlot = Math.floorMod(radialTargetSlot, RadialSkillConfig.SLOT_COUNT);
        int existingSlot = RadialSkillConfig.indexOf(radial, definition.id());
        if (existingSlot == targetSlot) {
            showToast(tr("wildbound.skills.already_in_radial", targetSlot + 1), GOLD);
            return;
        }

        List<String> next = RadialSkillConfig.assign(radial, targetSlot, definition.id());
        if (!ClientPlayNetworking.canSend(UpdateRadialSkillsPayload.TYPE)) {
            showToast(tr("wildbound.skills.radial_server_unavailable"), 0xFFFF8A98);
            return;
        }

        RadialSkillState.setOptimisticSlots(profile, next);
        ClientPlayNetworking.send(new UpdateRadialSkillsPayload(next, definition.id(), targetSlot));
        showToast(tr("wildbound.skills.assigning_radial", definition.name(), targetSlot + 1), GOLD);
    }

    public void onRadialResult(RadialSkillsResultPayload result) {
        if (result.success()) {
            RadialSkillState.acceptServerSlots(profile(), result.slots());
            String name = SkillRegistry.get(result.actionSkillId())
                    .map(SkillDefinition::name)
                    .orElse(result.actionSkillId());
            if (!name.isEmpty() && result.actionSlot() >= 0) {
                showToast(tr("wildbound.skills.assigned_radial", name, result.actionSlot() + 1),
                        0xFF8CFFB8);
            } else {
                showToast(tr("wildbound.skills.radial_updated"), 0xFF8CFFB8);
            }
        } else {
            RadialSkillState.rejectPendingSlots();
            showToast(radialFailureText(result.reason()), 0xFFFF8A98);
        }
    }

    private String radialFailureText(String reason) {
        return switch (reason) {
            case "skill_not_unlocked" -> tr("wildbound.skills.radial_not_unlocked");
            case "unknown_skill" -> tr("wildbound.skills.reason.unknown_skill");
            case "duplicate_skill" -> tr("wildbound.skills.radial_duplicate");
            default -> tr("wildbound.skills.radial_update_failed");
        };
    }

    private void attemptUnlock(SkillElement element, int index) {
        SkillDefinition definition = element.skills().get(index);
        PlayerRpgProfile profile = profile();

        if (profile.hasSkill(definition.id())) {
            showToast(tr("wildbound.skills.reason.already_unlocked"), 0xFFFF8A98);
            return;
        }
        if (profile.level() < definition.requiredLevel()) {
            showToast(tr("wildbound.skills.requires_level", definition.requiredLevel()), 0xFFFF8A98);
            return;
        }
        if (index > 0 && !profile.hasSkill(element.skills().get(index - 1).id())) {
            showToast(tr("wildbound.skills.requires_previous", element.skills().get(index - 1).name()),
                    0xFFFF8A98);
            return;
        }
        if (profile.skillPoints() < definition.pointCost()) {
            showToast(tr("wildbound.skills.reason.not_enough_points"), 0xFFFF8A98);
            return;
        }

        long now = System.currentTimeMillis();
        if (pendingUnlockSkillId != null && now < pendingUnlockUntil) {
            showToast(tr("wildbound.skills.unlock_request_pending"), GOLD);
            return;
        }
        if (!ClientPlayNetworking.canSend(UnlockSkillPayload.TYPE)) {
            showToast(tr("wildbound.skills.radial_server_unavailable"), 0xFFFF8A98);
            return;
        }

        pendingUnlockSkillId = definition.id();
        pendingUnlockUntil = now + 5_000L;
        ClientPlayNetworking.send(new UnlockSkillPayload(definition.id()));
    }

    // ------------------------------------------------------------------
    // Draw helpers
    // ------------------------------------------------------------------

    /** Chamfered panel matching RpgHud style. */
    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, boolean ornate) {
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

    private void drawPill(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int accent) {
        g.fill(x, y, x + w, y + h, 0xCC060A12);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, alpha(accent, 0x44));
        g.fill(x, y, x + 1, y + h, alpha(accent, 0x55));
    }

    private void drawElementOrb(GuiGraphicsExtractor g, SkillElement element, int cx, int cy, int radius,
                                boolean selected, boolean hovered, float time) {
        int primary = element.primaryColor();
        if (selected) {
            int pulse = 0x70 + (int) (0x50 * (0.5f + 0.5f * Math.sin(time * 3.4f)));
            fillCircle(g, cx, cy, radius + 4, alpha(primary, 0x28));
            strokeCircle(g, cx, cy, radius + 3, alpha(primary, pulse));
        } else if (hovered) {
            fillCircle(g, cx, cy, radius + 3, alpha(primary, 0x24));
        }

        fillCircle(g, cx, cy, radius + 1, 0xFF02040A);
        strokeCircle(g, cx, cy, radius, selected ? GOLD : primary);
        fillCircle(g, cx, cy, Math.max(1, radius - 1), NODE_DARK);
        if (radius >= 6) {
            fillCircle(g, cx, cy + Math.max(1, radius / 3), Math.max(1, radius / 3),
                    alpha(element.secondaryColor(), 0x66));
        }
        drawRune(g, element, cx, cy, Math.max(2f, radius * 0.62f),
                selected ? GOLD_LIGHT : 0xFFEAF6FF, NODE_DARK);
    }

    private void gradientText(GuiGraphicsExtractor g, String text, int x, int y,
                              int from, int to, int spacing) {
        int cursor = x;
        int total = Math.max(1, spacedWidth(font, text, spacing) - spacing);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float t = (float) (cursor - x) / total;
            g.text(font, ch, cursor, y, lerpColor(from, to, t), true);
            cursor += font.width(ch) + spacing;
        }
    }

    private static int spacedWidth(Font font, String text, int spacing) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += font.width(String.valueOf(text.charAt(i))) + spacing;
        }
        return Math.max(0, width - spacing);
    }

    private void glowLine(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        line(g, x0, y0, x1, y1, alpha(color, 0x1E), 5);
        line(g, x0, y0, x1, y1, alpha(color, 0x44), 3);
        line(g, x0, y0, x1, y1, color, 1);
    }

    private void updateAmbientMotes(GuiGraphicsExtractor g, int w, int h, float time) {
        // Spawn new motes occasionally
        if (ambientMotes.size() < 30 && Math.random() < 0.02) {
            ambientMotes.add(new AmbientMote(w, h));
        }
        // Update existing
        for (int i = ambientMotes.size() - 1; i >= 0; i--) {
            AmbientMote mote = ambientMotes.get(i);
            mote.update();
            if (mote.life <= 0) ambientMotes.remove(i);
        }
    }

    private void drawAmbientMotes(GuiGraphicsExtractor g) {
        for (AmbientMote mote : ambientMotes) {
            mote.draw(g);
        }
    }

    private void drawAmbientMotesInPanel(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                         SkillElement element, float time) {
        int primary = element.primaryColor();
        int count = Math.min(8, w / 20);
        for (int i = 0; i < count; i++) {
            float phase = time * 0.4f + i * 0.7f;
            int px = x + 10 + Math.round((w - 20) * (0.5f + 0.5f * (float) Math.sin(phase * 0.7f + i)));
            int py = y + 10 + Math.round((h - 20) * (0.5f + 0.5f * (float) Math.cos(phase * 0.5f + i * 1.3f)));
            int a = Math.round(0x18 + 0x18 * (0.5f + 0.5f * (float) Math.sin(time * 1.2f + i)));
            g.fill(px, py, px + 1, py + 1, alpha(primary, a));
            if (i % 3 == 0) {
                g.fill(px - 1, py, px + 2, py + 1, alpha(primary, a / 3));
                g.fill(px, py - 1, px + 1, py + 2, alpha(primary, a / 3));
            }
        }
    }

    private void updateUnlockParticles(float delta) {
        for (int i = unlockParticles.size() - 1; i >= 0; i--) {
            UnlockParticle p = unlockParticles.get(i);
            p.update(delta);
            if (p.life <= 0) unlockParticles.remove(i);
        }
    }

    private void drawUnlockParticles(GuiGraphicsExtractor g) {
        for (UnlockParticle p : unlockParticles) {
            p.draw(g);
        }
    }

    /** Complete bar with track, fill, gloss and moving shine. */
    private void drawBar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                         float progress, int top, int mid, int bottom, float time) {
        drawBarBase(g, x, y, w, h);
        drawBarFill(g, x, y, w, h, progress, top, mid, bottom);
        drawBarOverlay(g, x, y, w, h, progress, time);
    }

    private void drawBarBase(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, SHADOW);
        g.fill(x, y, x + w, y + h, BORDER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, TRACK_TOP, TRACK_BOTTOM);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, alpha(0xFFFFFFFF, 0x20));
    }

    private void drawBarFill(GuiGraphicsExtractor g, int x, int y, int w, int h,
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

    private void drawBarOverlay(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                float progress, float time) {
        int innerX = x + 1;
        int innerY = y + 1;
        int innerH = h - 2;
        int innerW = w - 2;
        int filled = Math.round(innerW * clamp01(progress));
        if (filled <= 0) return;

        // Quarter marks
        for (int i = 1; i < 4; i++) {
            int markX = innerX + innerW * i / 4;
            if (markX < innerX + filled) {
                g.fill(markX, innerY + 1, markX + 1, innerY + innerH - 1, 0x28000000);
            }
        }

        // Diagonal shine
        int travel = innerW + 18;
        int center = innerX - 9 + (int) ((time * 24f) % travel);
        for (int row = 0; row < innerH; row++) {
            int sx = center + row / 2;
            if (sx >= innerX && sx + 2 <= innerX + filled) {
                g.fill(sx, innerY + row, sx + 2, innerY + row + 1, alpha(0xFFFFFFFF, 0x18));
            }
        }
    }

    private String latestRealm(PlayerRpgProfile profile) {
        List<String> realms = profile.unlockedRealms();
        if (realms.isEmpty()) return "WILDBOUND RPG";
        String id = realms.get(realms.size() - 1);
        return RealmRegistry.get(id).map(r -> r.name().toUpperCase(Locale.ROOT)).orElse(id);
    }

    // ------------------------------------------------------------------
    // Primitive drawing
    // ------------------------------------------------------------------

    private static void chamferedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int cut, int color) {
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

    private static void chamferedGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int cut, int top, int bottom) {
        for (int row = 0; row < h; row++) {
            int inset = row < cut ? cut - row
                    : row >= h - cut ? row - (h - cut - 1)
                    : 0;
            int color = lerpColor(top, bottom, h <= 1 ? 0f : (float) row / (h - 1));
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private void diamond(GuiGraphicsExtractor g, int cx, int cy, int light, int dark) {
        int top = cy - 3;
        for (int row = 0; row < 7; row++) {
            int half = row <= 3 ? row : 6 - row;
            g.fill(cx - half, top + row, cx + half + 1, top + row + 1, row < 3 ? light : dark);
        }
    }

    private void spark(GuiGraphicsExtractor g, int cx, int cy, int color) {
        g.fill(cx, cy - 1, cx + 1, cy + 2, color);
        g.fill(cx - 1, cy, cx + 2, cy + 1, color);
    }

    private void drawRune(GuiGraphicsExtractor g, SkillElement element, int cx, int cy, float s,
                          int color, int background) {
        switch (element.id()) {
            case "aqua" -> {
                line(g, cx, cy - s, cx - s * 0.55f, cy + s * 0.2f, color, 1);
                line(g, cx, cy - s, cx + s * 0.55f, cy + s * 0.2f, color, 1);
                fillCircle(g, cx, Math.round(cy + s * 0.4f), Math.max(1, Math.round(s * 0.55f)), color);
            }
            case "inferno" -> {
                line(g, cx, cy - s, cx + s * 0.55f, cy, color, 1);
                line(g, cx + s * 0.55f, cy, cx + s * 0.15f, cy + s, color, 1);
                line(g, cx + s * 0.15f, cy + s, cx - s * 0.55f, cy + s * 0.2f, color, 1);
                line(g, cx - s * 0.55f, cy + s * 0.2f, cx, cy - s, color, 1);
                line(g, cx, cy + s * 0.4f, cx, cy + s * 0.75f, color, 1);
            }
            case "terra" -> {
                line(g, cx - s, cy + s * 0.7f, cx, cy - s * 0.8f, color, 1);
                line(g, cx, cy - s * 0.8f, cx + s, cy + s * 0.7f, color, 1);
                line(g, cx - s * 0.55f, cy + s * 0.7f, cx + s * 0.55f, cy + s * 0.7f, color, 1);
            }
            case "storm" -> {
                line(g, cx + s * 0.35f, cy - s, cx - s * 0.5f, cy + s * 0.1f, color, 1);
                line(g, cx - s * 0.5f, cy + s * 0.1f, cx + s * 0.1f, cy + s * 0.1f, color, 1);
                line(g, cx + s * 0.1f, cy + s * 0.1f, cx - s * 0.25f, cy + s, color, 1);
            }
            case "flora" -> {
                line(g, cx - s * 0.6f, cy + s, cx + s * 0.6f, cy - s, color, 1);
                line(g, cx + s * 0.6f, cy - s, cx - s * 0.1f, cy - s * 0.9f, color, 1);
                line(g, cx - s * 0.1f, cy - s * 0.9f, cx - s * 0.75f, cy - s * 0.1f, color, 1);
                line(g, cx - s * 0.75f, cy - s * 0.1f, cx + s * 0.6f, cy - s, color, 1);
            }
            case "radiance" -> {
                strokeCircle(g, cx, cy, Math.max(2, Math.round(s * 0.45f)), color);
                for (int k = 0; k < 4; k++) {
                    double angle = Math.PI / 4 + k * Math.PI / 2;
                    line(g, cx + (float) Math.cos(angle) * s * 0.7f,
                            cy + (float) Math.sin(angle) * s * 0.7f,
                            cx + (float) Math.cos(angle) * s,
                            cy + (float) Math.sin(angle) * s, color, 1);
                }
            }
            case "umbra" -> {
                fillCircle(g, cx, cy, Math.max(2, Math.round(s * 0.8f)), color);
                fillCircle(g, cx + Math.round(s * 0.55f), cy, Math.max(1, Math.round(s * 0.7f)), background);
            }
            case "zephyr" -> {
                line(g, cx - s, cy - s * 0.55f, cx + s * 0.7f, cy - s * 0.55f, color, 1);
                line(g, cx - s * 0.6f, cy, cx + s, cy, color, 1);
                line(g, cx - s * 0.3f, cy + s * 0.55f, cx + s * 0.55f, cy + s * 0.55f, color, 1);
            }
            case "void" -> {
                strokeCircle(g, cx, cy, Math.max(2, Math.round(s * 0.85f)), color);
                fillCircle(g, cx, cy, Math.max(1, Math.round(s * 0.22f)), color);
            }
            case "sylvaris" -> {
                line(g, cx, cy + s, cx, cy - s * 0.2f, color, 1);
                line(g, cx - s * 0.8f, cy - s * 0.15f, cx, cy - s, color, 1);
                line(g, cx + s * 0.8f, cy - s * 0.15f, cx, cy - s, color, 1);
                line(g, cx - s * 0.55f, cy + s * 0.5f, cx + s * 0.55f, cy + s * 0.5f, color, 1);
            }
            case "corrosive" -> {
                fillCircle(g, cx - Math.round(s * 0.3f), cy + Math.round(s * 0.3f),
                        Math.max(1, Math.round(s * 0.55f)), color);
                fillCircle(g, cx + Math.round(s * 0.55f), cy - Math.round(s * 0.2f),
                        Math.max(1, Math.round(s * 0.28f)), color);
                fillCircle(g, cx + Math.round(s * 0.45f), cy + Math.round(s * 0.65f),
                        Math.max(1, Math.round(s * 0.2f)), color);
            }
            case "blight" -> {
                int top = Math.round(cy - s * 0.6f);
                int leftX = Math.round(cx - s * 0.6f);
                int leftY = Math.round(cy + s * 0.4f);
                int rightX = Math.round(cx + s * 0.6f);
                int rightY = leftY;
                fillCircle(g, cx, top, 1, color);
                fillCircle(g, leftX, leftY, 1, color);
                fillCircle(g, rightX, rightY, 1, color);
                line(g, cx, top, cx, cy, color, 1);
                line(g, leftX, leftY, cx, cy, color, 1);
                line(g, rightX, rightY, cx, cy, color, 1);
            }
            case "ferrum" -> {
                line(g, cx, cy - s, cx + s * 0.8f, cy, color, 1);
                line(g, cx + s * 0.8f, cy, cx, cy + s, color, 1);
                line(g, cx, cy + s, cx - s * 0.8f, cy, color, 1);
                line(g, cx - s * 0.8f, cy, cx, cy - s, color, 1);
                line(g, cx - s * 0.5f, cy, cx + s * 0.5f, cy, color, 1);
            }
            case "frost" -> {
                for (int k = 0; k < 3; k++) {
                    double angle = k * Math.PI / 3;
                    line(g, cx - (float) Math.cos(angle) * s, cy - (float) Math.sin(angle) * s,
                            cx + (float) Math.cos(angle) * s, cy + (float) Math.sin(angle) * s, color, 1);
                }
            }
            case "sanguis" -> {
                fillCircle(g, cx - Math.round(s * 0.35f), cy - Math.round(s * 0.25f),
                        Math.max(1, Math.round(s * 0.42f)), color);
                fillCircle(g, cx + Math.round(s * 0.35f), cy - Math.round(s * 0.25f),
                        Math.max(1, Math.round(s * 0.42f)), color);
                line(g, cx - s * 0.7f, cy - s * 0.1f, cx, cy + s * 0.85f, color, 1);
                line(g, cx + s * 0.7f, cy - s * 0.1f, cx, cy + s * 0.85f, color, 1);
            }
            case "gravity" -> {
                strokeCircle(g, cx, cy, Math.max(2, Math.round(s * 0.9f)), color);
                strokeCircle(g, cx, cy, Math.max(1, Math.round(s * 0.45f)), color);
                fillCircle(g, cx, cy, 1, color);
            }
            case "chronos" -> {
                line(g, cx - s * 0.75f, cy - s, cx + s * 0.75f, cy - s, color, 1);
                line(g, cx - s * 0.75f, cy + s, cx + s * 0.75f, cy + s, color, 1);
                line(g, cx - s * 0.75f, cy - s, cx + s * 0.2f, cy, color, 1);
                line(g, cx + s * 0.75f, cy - s, cx - s * 0.2f, cy, color, 1);
                line(g, cx - s * 0.75f, cy + s, cx + s * 0.2f, cy, color, 1);
                line(g, cx + s * 0.75f, cy + s, cx - s * 0.2f, cy, color, 1);
            }
            case "resonance" -> {
                fillCircle(g, Math.round(cx - s * 0.7f), cy, 1, color);
                for (int k = 1; k <= 3; k++) {
                    float lineX = cx - s * 0.7f + k * s * 0.45f;
                    float half = s * (0.2f + k * 0.24f);
                    line(g, lineX, cy - half, lineX, cy + half, color, 1);
                }
            }
            default -> fillCircle(g, cx, cy, 2, color);
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

    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1,
                             int color, int thickness) {
        int dx = Math.round(x1 - x0);
        int dy = Math.round(y1 - y0);
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        int half = thickness / 2;
        if (steps <= 0) {
            g.fill(Math.round(x0) - half, Math.round(y0) - half,
                    Math.round(x0) - half + thickness, Math.round(y0) - half + thickness, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x0 + dx * (float) i / steps);
            int y = Math.round(y0 + dy * (float) i / steps);
            g.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
        }
    }

    // ------------------------------------------------------------------
    // Colors and small math
    // ------------------------------------------------------------------

    private static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    private static int lighten(int color, float amount) {
        return lerpColor(color, 0xFFFFFFFF, amount);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
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

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = out.toString() + text.charAt(i);
            if (font.width(next) + ellipsisWidth > maxWidth) break;
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private PlayerRpgProfile profile() {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) return PlayerRpgProfile.empty();
        PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
        return profile == null ? PlayerRpgProfile.empty() : profile;
    }

    // ------------------------------------------------------------------
    // Animation particles
    // ------------------------------------------------------------------

    private static class AmbientMote {
        float x, y;
        float vx, vy;
        int color;
        float life = 1f;
        float maxLife;
        float size = 1f;

        AmbientMote(int w, int h) {
            this.x = (float) (Math.random() * w);
            this.y = (float) (Math.random() * h);
            this.vx = (float) (Math.random() * 0.3f - 0.15f);
            this.vy = (float) (Math.random() * 0.3f - 0.15f);
            this.maxLife = 1f + (float) (Math.random() * 3f);
            this.life = maxLife;
            this.size = 1f + (float) Math.random() * 1f;
        }

        void update() {
            x += vx;
            y += vy;
            vx *= 0.995f;
            vy *= 0.995f;
            life -= 0.008f;
        }

        void draw(GuiGraphicsExtractor g) {
            if (life <= 0) return;
            float alpha = life / maxLife;
            int a = Math.round(0x30 * alpha);
            g.fill(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size), alpha(color, a));
        }
    }

    private static class UnlockParticle {
        float x = 0, y = 0;
        float vx, vy;
        float life = 1f;
        int color = GOLD_LIGHT;
        float size = 2f;

        UnlockParticle() {
            // Position will be set when spawned from skill node
            this.vx = (float) (Math.random() * 4f - 2f);
            this.vy = (float) (Math.random() * 4f - 2f);
            this.color = Math.random() < 0.5 ? GOLD_LIGHT : GOLD;
            this.size = 1f + (float) Math.random() * 2f;
        }

        void update(float delta) {
            x += vx * delta * 60f;
            y += vy * delta * 60f;
            vy += 0.1f * delta * 60f; // gravity
            vx *= 0.98f;
            vy *= 0.98f;
            life -= delta * 1.5f;
            size *= 0.98f;
        }

        void draw(GuiGraphicsExtractor g) {
            if (life <= 0) return;
            int a = Math.round(0xC0 * life);
            g.fill(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size), alpha(color, a));
        }
    }

    // Need to import Math for the new classes
    private static final java.util.Random RANDOM = new java.util.Random();
}