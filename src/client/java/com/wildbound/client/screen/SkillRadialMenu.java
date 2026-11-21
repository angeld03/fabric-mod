package com.wildbound.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.client.RadialSkillState;
import com.wildbound.client.WildboundClient;
import com.wildbound.network.ActivateSkillPayload;
import com.wildbound.network.RadialSkillsResultPayload;
import com.wildbound.network.UpdateRadialSkillsPayload;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RadialSkillConfig;
import com.wildbound.player.RpgAttachments;
import com.wildbound.skills.SkillActivationService;
import com.wildbound.skills.SkillCatalog;
import com.wildbound.skills.SkillDefinition;
import com.wildbound.skills.SkillElement;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opaque five-slot radial skill menu.
 *
 * <p>Left click selects a slot or assigns one of the visible unlocked skills.
 * Right click casts the selected skill. The five slots and the complete
 * assignment list are visible at the same time, so assigning a skill never
 * depends on a hidden drawer or on remembering a previous interaction.</p>
 */
public final class SkillRadialMenu extends Screen {
    private static final int GOLD = 0xFFFFD479;
    private static final int GOLD_LIGHT = 0xFFFFF0C0;
    private static final int GOLD_DEEP = 0xFFC98A2B;
    private static final int TEXT = 0xFFF4F9FF;
    private static final int MUTED = 0xFFA7B8CE;
    private static final int DIM = 0xFF73829A;
    private static final int PANEL_TOP = 0xFF1D2A3C;
    private static final int PANEL_BOTTOM = 0xFF0A1018;

    private static final int MAX_SLOTS = RadialSkillConfig.SLOT_COUNT;
    private static final int LIST_ROW_HEIGHT = 30;
    private static final int LIST_HEADER_HEIGHT = 30;
    private static final int LIST_FOOTER_HEIGHT = 29;

    private final long openedAt = System.currentTimeMillis();
    private final List<SlotData> slots = new ArrayList<>();
    private final List<AssignableSkill> assignableSkills = new ArrayList<>();

    private int hoveredSlot = -1;
    private int selectedSlot;
    private int listScroll;

    private int closeX;
    private int closeY;
    private int closeW = 14;
    private int closeH = 14;

    private String statusMessage;
    private int statusColor = GOLD_LIGHT;
    private long statusUntil;

    public SkillRadialMenu() {
        super(Component.translatable("wildbound.radial.title"));
        rebuildSlots();
        selectedSlot = Math.floorMod(RadialSkillState.selectedSlot(), MAX_SLOTS);
        RadialSkillState.selectSlot(selectedSlot);
        clampListScroll();
    }

    private void rebuildSlots() {
        slots.clear();
        assignableSkills.clear();

        PlayerRpgProfile profile = profile();
        List<String> radial = RadialSkillState.slots(profile);
        for (int i = 0; i < MAX_SLOTS; i++) {
            String skillId = i < radial.size() ? radial.get(i) : RadialSkillConfig.EMPTY;
            SkillDefinition definition = skillId == null || skillId.isEmpty()
                    ? null : SkillCatalog.getSkillDefinition(skillId);
            slots.add(new SlotData(i, skillId == null ? RadialSkillConfig.EMPTY : skillId, definition));
        }

        if (profile != null) {
            for (String skillId : profile.unlockedSkills()) {
                SkillDefinition definition = SkillCatalog.getSkillDefinition(skillId);
                if (definition != null) assignableSkills.add(new AssignableSkill(skillId, definition));
            }
        }

        if (selectedSlot < 0 || selectedSlot >= slots.size()) selectedSlot = 0;
        RadialSkillState.selectSlot(selectedSlot);
        clampListScroll();
    }

    private PlayerRpgProfile profile() {
        LocalPlayer player = mc().player;
        if (player == null) return null;
        return player.getAttached(RpgAttachments.PROFILE);
    }

    private List<String> currentSlotIds() {
        List<String> ids = new ArrayList<>(MAX_SLOTS);
        for (int i = 0; i < MAX_SLOTS; i++) {
            SlotData slot = i < slots.size() ? slots.get(i) : null;
            ids.add(slot == null ? RadialSkillConfig.EMPTY : slot.skillId);
        }
        return ids;
    }

    private void submitSlots(List<String> next, String actionSkillId, int actionSlot) {
        List<String> safe = RadialSkillConfig.copySlots(next);
        if (!ClientPlayNetworking.canSend(UpdateRadialSkillsPayload.TYPE)) {
            showStatus(tr("wildbound.skills.radial_server_unavailable"), 0xFFFF8A98);
            return;
        }

        PlayerRpgProfile profile = profile();
        RadialSkillState.setOptimisticSlots(profile, safe);
        rebuildSlots();
        ClientPlayNetworking.send(new UpdateRadialSkillsPayload(safe, actionSkillId, actionSlot));
        showStatus(actionSkillId.isEmpty()
                ? tr("wildbound.radial.clearing_slot", actionSlot + 1)
                : tr("wildbound.radial.assigning", skillName(actionSkillId), actionSlot + 1),
                GOLD_LIGHT);
    }

    private void assignSkill(AssignableSkill skill) {
        List<String> current = currentSlotIds();
        if (selectedSlot < 0 || selectedSlot >= MAX_SLOTS) return;
        if (skill.skillId.equals(current.get(selectedSlot))) {
            showStatus(tr("wildbound.radial.already_assigned", skill.definition.name(), selectedSlot + 1), GOLD_LIGHT);
            return;
        }

        List<String> next = RadialSkillConfig.assign(current, selectedSlot, skill.skillId);
        submitSlots(next, skill.skillId, selectedSlot);
    }

    private void clearSelectedSlot() {
        List<String> current = currentSlotIds();
        if (selectedSlot < 0 || selectedSlot >= MAX_SLOTS) return;
        if (current.get(selectedSlot).isEmpty()) {
            showStatus(tr("wildbound.radial.slot_already_empty", selectedSlot + 1), GOLD_LIGHT);
            return;
        }
        List<String> next = RadialSkillConfig.assign(current, selectedSlot, RadialSkillConfig.EMPTY);
        submitSlots(next, RadialSkillConfig.EMPTY, selectedSlot);
    }

    private void castSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return;
        SlotData slot = slots.get(slotIndex);
        selectSlot(slotIndex);
        if (!slot.hasSkill()) {
            showStatus(tr("wildbound.radial.empty_slot", slotIndex + 1), GOLD_LIGHT);
            return;
        }
        if (!ClientPlayNetworking.canSend(ActivateSkillPayload.TYPE)) {
            showStatus(tr("wildbound.skills.radial_server_unavailable"), 0xFFFF8A98);
            return;
        }
        // Skip the round trip when the skill is still on cooldown.
        if (SkillActivationService.getClientCooldown(slot.skillId) > 0f) {
            showStatus(tr("wildbound.skills.on_cooldown", slot.definition.name()), 0xFFFF8A98);
            return;
        }
        var aim = Minecraft.getInstance().player.getLookAngle().normalize();
        ClientPlayNetworking.send(new ActivateSkillPayload(slot.skillId, aim.x, aim.y, aim.z));
        onClose();
    }

    private void castSelectedSlot() {
        castSlot(selectedSlot);
    }

    private void selectSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= MAX_SLOTS) return;
        selectedSlot = slotIndex;
        RadialSkillState.selectSlot(slotIndex);
    }

    private void clampListScroll() {
        Layout layout = layout();
        int maxScroll = Math.max(0, assignableSkills.size() - layout.visibleRows);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int width = g.guiWidth();
        int height = g.guiHeight();

        // Fully opaque: the radial menu must never look like a transparent HUD.
        g.fillGradient(0, 0, width, height, 0xFF08111F, 0xFF02050A);
        for (int inset = 0; inset < 5; inset++) {
            g.fill(inset, inset, width - inset, height - inset, alpha(0x000000, 0x0C));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        float time = (System.currentTimeMillis() - openedAt) / 1000f;

        drawRootPanel(g, layout, mouseX, mouseY, time);
        drawRadial(g, layout, mouseX, mouseY, time);
        drawSkillList(g, layout, mouseX, mouseY, time);
        drawFooter(g, layout);

        hoveredSlot = slotAt(layout, mouseX, mouseY);
        if (hoveredSlot >= 0 && hoveredSlot < slots.size() && slots.get(hoveredSlot).hasSkill()) {
            drawTooltip(g, mouseX, mouseY, slots.get(hoveredSlot));
        }
    }

    private void drawRootPanel(GuiGraphicsExtractor g, Layout layout, int mouseX, int mouseY, float time) {
        panel(g, layout.x, layout.y, layout.w, layout.h, GOLD, true);

        String title = tr("wildbound.radial.title");
        g.text(font, title, layout.x + 13, layout.y + 7, GOLD_LIGHT, true);
        String subtitle = tr("wildbound.radial.subtitle");
        g.text(font, subtitle, layout.x + 13, layout.y + 20, alpha(MUTED, 0xD0), false);

        closeW = 14;
        closeH = 14;
        closeX = layout.x + layout.w - closeW - 8;
        closeY = layout.y + 7;
        boolean closeHovered = inside(mouseX, mouseY, closeX, closeY, closeW, closeH);
        g.fill(closeX, closeY, closeX + closeW, closeY + closeH,
                closeHovered ? 0xAA4A1D24 : 0x88101620);
        g.fill(closeX + 1, closeY + 1, closeX + closeW - 1, closeY + 2, alpha(GOLD, 0x70));
        int x0 = closeX + 3;
        int y0 = closeY + 3;
        int x1 = closeX + closeW - 4;
        int y1 = closeY + closeH - 4;
        int color = closeHovered ? 0xFFFFA0A0 : MUTED;
        line(g, x0, y0, x1, y1, color, 1);
        line(g, x1, y0, x0, y1, color, 1);
    }

    private void drawRadial(GuiGraphicsExtractor g, Layout layout, int mouseX, int mouseY, float time) {
        int radialX = layout.x + 10;
        panel(g, radialX, layout.contentY, layout.radialW, layout.contentH, GOLD_DEEP, false);

        String heading = tr("wildbound.radial.slots");
        g.text(font, heading, radialX + 9, layout.contentY + 7, alpha(GOLD, 0xD0), false);
        g.fill(radialX + 8, layout.contentY + 18,
                radialX + layout.radialW - 8, layout.contentY + 19, alpha(GOLD_DEEP, 0x55));

        hoveredSlot = slotAt(layout, mouseX, mouseY);
        for (int i = 0; i < slots.size(); i++) {
            int[] center = slotCenter(layout, i);
            int size = layout.slotSize;
            drawSlot(g, center[0] - size / 2, center[1] - size / 2, size, i,
                    hoveredSlot == i, time);
        }
        drawCenter(g, layout, time);
    }

    private void drawSlot(GuiGraphicsExtractor g, int x, int y, int size, int index,
                          boolean hovered, float time) {
        SlotData slot = slots.get(index);
        boolean hasSkill = slot.hasSkill();
        int background;
        int border;
        if (selectedSlot == index) {
            background = 0xFF27364E;
            border = GOLD_LIGHT;
        } else if (hovered) {
            background = 0xFF29354A;
            border = lerpColor(GOLD_DEEP, GOLD_LIGHT,
                    0.5f + 0.5f * (float) Math.sin(time * 3.5f + index));
        } else if (hasSkill) {
            background = lerpColor(0xFF171E2B, 0xFF23304A,
                    0.5f + 0.5f * (float) Math.sin(time * 1.7f + index * 0.6f));
            border = 0xFF35425A;
        } else {
            background = 0xFF0C1119;
            border = 0xFF202A3A;
        }

        g.fill(x + 2, y + 3, x + size, y + size, 0x90000000);
        g.fill(x, y, x + size, y + size, background);
        g.fill(x, y, x + size, y + 2, border);
        g.fill(x, y + size - 2, x + size, y + size, alpha(border, 0xA0));
        g.fill(x, y, x + 2, y + size, border);
        g.fill(x + size - 2, y, x + size, y + size, alpha(border, 0xA0));

        g.text(font, String.valueOf(index + 1), x + 4, y + 4,
                selectedSlot == index ? GOLD_LIGHT : alpha(MUTED, 0xC0), selectedSlot == index);

        if (hasSkill) {
            int iconSize = Math.max(18, size - 22);
            drawSkillIcon(g, slot, x + (size - iconSize) / 2, y + 6, iconSize, time);
            String name = fit(font, slot.definition.name(), size - 6);
            g.text(font, name, x + (size - font.width(name)) / 2, y + size - 13,
                    selectedSlot == index ? GOLD_LIGHT : hovered ? TEXT : MUTED,
                    selectedSlot == index);

            float cooldown = SkillActivationService.getClientCooldown(slot.skillId);
            float maxCooldown = SkillActivationService.getMaxCooldown(slot.skillId);
            if (cooldown > 0f && maxCooldown > 0f) {
                drawCooldownArc(g, x + size / 2, y + size / 2, size / 2 - 2,
                        1f - cooldown / maxCooldown);
            }
        } else {
            String plus = "+";
            int plusAlpha = Math.round(0x70 + 0x50 *
                    (0.5f + 0.5f * (float) Math.sin(time * 2.4f + index)));
            g.text(font, plus, x + (size - font.width(plus)) / 2,
                    y + (size - 9) / 2, alpha(DIM, plusAlpha), false);
        }
    }

    private void drawSkillIcon(GuiGraphicsExtractor g, SlotData slot, int x, int y, int size, float time) {
        SkillElement element = slot.element;
        int primary = element == null ? GOLD_DEEP : element.primaryColor();
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.8f + slot.index * 0.9f);
        fillCircle(g, x + size / 2, y + size / 2, size / 2 + 2,
                alpha(primary, Math.round(0x22 + 0x18 * pulse)));
        fillCircle(g, x + size / 2, y + size / 2, size / 2, 0xFF060A11);
        strokeCircle(g, x + size / 2, y + size / 2, size / 2, primary);
        drawRune(g, element, x + size / 2, y + size / 2, size * 0.42f, GOLD_LIGHT);
    }

    private void drawCooldownArc(GuiGraphicsExtractor g, int cx, int cy, int radius, float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        int segments = Math.max(1, Math.round(36 * clamped));
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(-90f + i * 360f / 36f);
            double a2 = Math.toRadians(-90f + (i + 1) * 360f / 36f);
            line(g, cx + Math.round(radius * (float) Math.cos(a1)),
                    cy + Math.round(radius * (float) Math.sin(a1)),
                    cx + Math.round(radius * (float) Math.cos(a2)),
                    cy + Math.round(radius * (float) Math.sin(a2)),
                    lerpColor(0xFF8CFFB8, GOLD, i / 36f), 3);
        }
    }

    private void drawCenter(GuiGraphicsExtractor g, Layout layout, float time) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3f);
        for (int i = 0; i < 3; i++) {
            int radius = 28 + i * 3;
            fillCircle(g, layout.radialCx, layout.radialCy, radius,
                    alpha(GOLD_DEEP, Math.round((0x16 - i * 4) * pulse)));
        }
        fillCircleGradient(g, layout.radialCx, layout.radialCy, 25, 0xFF1A2030, 0xFF0C1018);
        strokeCircle(g, layout.radialCx, layout.radialCy, 25,
                alpha(GOLD, Math.round(0xD0 + 0x2F * pulse)));

        String slotNumber = String.valueOf(selectedSlot + 1);
        g.text(font, slotNumber, layout.radialCx - font.width(slotNumber) / 2,
                layout.radialCy - 10, GOLD_LIGHT, true);
        String selected = tr("wildbound.radial.selected");
        g.text(font, fit(font, selected, 46), layout.radialCx - font.width(fit(font, selected, 46)) / 2,
                layout.radialCy + 3, alpha(MUTED, 0xD0), false);
    }

    private void drawSkillList(GuiGraphicsExtractor g, Layout layout, int mouseX, int mouseY, float time) {
        panel(g, layout.listX, layout.contentY, layout.listW, layout.contentH, GOLD_DEEP, false);

        String title = tr("wildbound.radial.assign_to", selectedSlot + 1);
        g.text(font, fit(font, title, layout.listW - 18), layout.listX + 9,
                layout.contentY + 7, GOLD_LIGHT, true);
        String hint = tr("wildbound.radial.list_hint");
        g.text(font, fit(font, hint, layout.listW - 18), layout.listX + 9,
                layout.contentY + 18, alpha(MUTED, 0xB0), false);
        g.fill(layout.listX + 8, layout.contentY + LIST_HEADER_HEIGHT - 2,
                layout.listX + layout.listW - 8, layout.contentY + LIST_HEADER_HEIGHT - 1,
                alpha(GOLD_DEEP, 0x80));

        List<String> current = currentSlotIds();
        if (assignableSkills.isEmpty()) {
            String empty = tr("wildbound.radial.no_skills");
            g.text(font, fit(font, empty, layout.listW - 18), layout.listX + 9,
                    layout.listY + 9, MUTED, false);
        }

        int count = Math.min(layout.visibleRows, assignableSkills.size() - listScroll);
        for (int row = 0; row < count; row++) {
            AssignableSkill skill = assignableSkills.get(listScroll + row);
            int y = layout.listY + row * LIST_ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, layout.listX + 5, y,
                    layout.listW - 10, LIST_ROW_HEIGHT - 2);
            int background = hovered ? 0xFF2B3850 : (row % 2 == 0 ? 0xFF131B27 : 0xFF0F151F);
            g.fill(layout.listX + 5, y, layout.listX + layout.listW - 5,
                    y + LIST_ROW_HEIGHT - 2, background);
            if (hovered) {
                g.fill(layout.listX + 5, y, layout.listX + layout.listW - 5, y + 1, GOLD);
            }

            SkillElement element = skill.element;
            int primary = element == null ? GOLD_DEEP : element.primaryColor();
            fillCircle(g, layout.listX + 19, y + 13, 9, 0xFF060A11);
            strokeCircle(g, layout.listX + 19, y + 13, 9, primary);
            drawRune(g, element, layout.listX + 19, y + 13, 7, GOLD_LIGHT);

            int assignedSlot = RadialSkillConfig.indexOf(current, skill.skillId);
            int nameColor = assignedSlot == selectedSlot ? GOLD_LIGHT : assignedSlot >= 0 ? MUTED : TEXT;
            String name = fit(font, skill.definition.name(), layout.listW - 42);
            g.text(font, name, layout.listX + 34, y + 4, nameColor,
                    assignedSlot == selectedSlot);

            String state = assignedSlot >= 0
                    ? tr("wildbound.radial.in_slot", assignedSlot + 1)
                    : tr("wildbound.radial.click_assign");
            g.text(font, fit(font, state, layout.listW - 42), layout.listX + 34, y + 16,
                    assignedSlot >= 0 ? alpha(primary, 0xD0) : alpha(GOLD, 0xB0), false);
        }

        int maxScroll = Math.max(0, assignableSkills.size() - layout.visibleRows);
        if (maxScroll > 0) {
            String scroll = (listScroll + 1) + "/" + (maxScroll + 1);
            g.text(font, scroll, layout.listX + layout.listW - 9 - font.width(scroll),
                    layout.contentY + 18, alpha(MUTED, 0xA0), false);
            int trackX = layout.listX + layout.listW - 4;
            g.fill(trackX, layout.listY, trackX + 1, layout.listY + layout.visibleRows * LIST_ROW_HEIGHT,
                    0x66101820);
            int thumbHeight = Math.max(8, layout.visibleRows * LIST_ROW_HEIGHT
                    * layout.visibleRows / Math.max(1, assignableSkills.size()));
            int travel = Math.max(1, layout.visibleRows * LIST_ROW_HEIGHT - thumbHeight);
            int thumbY = layout.listY + travel * listScroll / Math.max(1, maxScroll);
            g.fill(trackX, thumbY, trackX + 1, thumbY + thumbHeight, alpha(GOLD, 0xC0));
        }

        int buttonY = layout.listFooterY + 4;
        boolean clearHovered = inside(mouseX, mouseY, layout.listX + 7, buttonY,
                layout.listW - 14, 21);
        g.fill(layout.listX + 7, buttonY, layout.listX + layout.listW - 7, buttonY + 21,
                clearHovered ? 0xAA5A2730 : 0x66101820);
        g.fill(layout.listX + 7, buttonY, layout.listX + layout.listW - 7, buttonY + 1,
                alpha(GOLD_DEEP, 0xA0));
        String clear = tr("wildbound.radial.clear_slot", selectedSlot + 1);
        g.text(font, fit(font, clear, layout.listW - 20),
                layout.listX + 10, buttonY + 6, clearHovered ? 0xFFFFC1C1 : MUTED, false);
    }

    private void drawFooter(GuiGraphicsExtractor g, Layout layout) {
        g.fill(layout.x + 8, layout.footerY, layout.x + layout.w - 8, layout.footerY + 1,
                alpha(GOLD_DEEP, 0x55));
        if (statusMessage != null && System.currentTimeMillis() < statusUntil) {
            g.text(font, fit(font, statusMessage, layout.w - 20), layout.x + 10,
                    layout.footerY + 6, statusColor, false);
            return;
        }

        String hints = tr("wildbound.radial.hints");
        g.text(font, fit(font, hints, layout.w - 20), layout.x + 10,
                layout.footerY + 6, alpha(MUTED, 0xD0), false);
    }

    private void drawTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, SlotData slot) {
        int width = Math.min(230, g.guiWidth() - 12);
        int height = 78;
        int x = mouseX + 16;
        int y = mouseY - height - 10;
        if (x + width > g.guiWidth() - 6) x = g.guiWidth() - width - 6;
        if (y < 44) y = mouseY + 18;
        if (y + height > g.guiHeight() - 30) y = Math.max(44, g.guiHeight() - height - 30);

        int accent = slot.element == null ? GOLD : slot.element.primaryColor();
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xF0000000);
        panel(g, x, y, width, height, accent, false);
        g.text(font, fit(font, slot.definition.name(), width - 20), x + 10, y + 8, GOLD_LIGHT, true);
        String elementName = slot.element == null ? "" : slot.element.displayName().toUpperCase(Locale.ROOT);
        if (!elementName.isEmpty()) {
            g.text(font, elementName, x + width - 10 - font.width(elementName), y + 8, accent, true);
        }

        String[] words = slot.definition.description().split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = y + 25;
        int maxWidth = width - 20;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > maxWidth) {
                g.text(font, line.toString(), x + 10, lineY, MUTED, false);
                lineY += 10;
                line = new StringBuilder(word);
                if (lineY > y + height - 30) break;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (lineY <= y + height - 30 && !line.isEmpty()) {
            g.text(font, line.toString(), x + 10, lineY, MUTED, false);
        }

        float mana = SkillActivationService.getManaCost(slot.definition);
        float cooldown = SkillActivationService.getCooldown(slot.definition);
        g.text(font, tr("wildbound.radial.cost", Math.round(mana), String.format("%.1fs", cooldown)),
                x + 10, y + height - 16, alpha(GOLD, 0xE0), false);
    }

    private void showStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 3_500L;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        Layout layout = layout();

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            int clickedSlot = slotAt(layout, mouseX, mouseY);
            castSlot(clickedSlot >= 0 ? clickedSlot : selectedSlot);
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);

        if (inside(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            onClose();
            return true;
        }

        int buttonY = layout.listFooterY + 4;
        if (inside(mouseX, mouseY, layout.listX + 7, buttonY, layout.listW - 14, 21)) {
            clearSelectedSlot();
            return true;
        }

        if (mouseY >= layout.listY && mouseY < layout.listFooterY
                && mouseX >= layout.listX && mouseX < layout.listX + layout.listW) {
            int relative = mouseY - layout.listY;
            int row = relative / LIST_ROW_HEIGHT;
            int index = listScroll + row;
            if (row >= 0 && row < layout.visibleRows && index < assignableSkills.size()) {
                assignSkill(assignableSkills.get(index));
            }
            return true;
        }

        int clickedSlot = slotAt(layout, mouseX, mouseY);
        if (clickedSlot >= 0) {
            selectSlot(clickedSlot);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (WildboundClient.OPEN_RADIAL.matches(event)) {
            // Drain the queued mapping click so the end-of-tick handler cannot
            // immediately reopen the menu after this screen closes it.
            while (WildboundClient.OPEN_RADIAL.consumeClick()) {
            }
            onClose();
            return true;
        }
        if (event.key() >= InputConstants.KEY_1 && event.key() <= InputConstants.KEY_5) {
            selectSlot(event.key() - InputConstants.KEY_1);
            return true;
        }
        if (event.key() == InputConstants.KEY_TAB) {
            selectSlot(Math.floorMod(selectedSlot + 1, MAX_SLOTS));
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) {
            castSelectedSlot();
            return true;
        }
        if (event.key() == InputConstants.KEY_F3) {
            listScroll += 1;
            clampListScroll();
            return true;
        }
        if (event.key() == InputConstants.KEY_F4) {
            listScroll -= 1;
            clampListScroll();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        Layout layout = layout();
        boolean overList = mouseX >= layout.listX && mouseX < layout.listX + layout.listW
                && mouseY >= layout.contentY && mouseY < layout.contentY + layout.contentH;
        if (overList) {
            listScroll -= scrollY > 0 ? 1 : -1;
            clampListScroll();
            return true;
        }
        selectSlot(Math.floorMod(selectedSlot + (scrollY > 0 ? -1 : 1), MAX_SLOTS));
        return true;
    }

    @Override
    public void onClose() {
        mc().setScreenAndShow(null);
    }

    public void onRadialResult(RadialSkillsResultPayload result) {
        PlayerRpgProfile profile = profile();
        if (result.success()) {
            RadialSkillState.acceptServerSlots(profile, result.slots());
            rebuildSlots();
            String skill = skillName(result.actionSkillId());
            if (!skill.isEmpty() && result.actionSlot() >= 0) {
                showStatus(tr("wildbound.radial.assigned", skill, result.actionSlot() + 1), 0xFF8CFFB8);
            } else if (result.actionSlot() >= 0) {
                showStatus(tr("wildbound.radial.cleared", result.actionSlot() + 1), 0xFF8CFFB8);
            } else {
                showStatus(tr("wildbound.skills.radial_updated"), 0xFF8CFFB8);
            }
        } else {
            RadialSkillState.rejectPendingSlots();
            rebuildSlots();
            showStatus(radialFailureText(result.reason()), 0xFFFF8A98);
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

    private String skillName(String skillId) {
        if (skillId == null || skillId.isEmpty()) return "";
        SkillDefinition definition = SkillCatalog.getSkillDefinition(skillId);
        return definition == null ? skillId : definition.name();
    }

    private Layout layout() {
        int screenWidth = mc().getWindow().getGuiScaledWidth();
        int screenHeight = mc().getWindow().getGuiScaledHeight();
        int margin = clamp(Math.min(screenWidth, screenHeight) / 42, 5, 14);
        int width = Math.min(760, screenWidth - margin * 2);
        int height = Math.min(420, screenHeight - margin * 2);
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        int headerHeight = screenHeight < 280 ? 32 : 42;
        int footerHeight = screenHeight < 280 ? 22 : 28;
        int contentY = y + headerHeight;
        int contentHeight = Math.max(95, height - headerHeight - footerHeight);

        int listWidth = width < 440
                ? Math.max(120, Math.round(width * 0.47f))
                : Math.min(280, Math.max(190, Math.round(width * 0.38f)));
        int gap = 8;
        int radialWidth = Math.max(90, width - 20 - listWidth - gap);
        int listX = x + 10 + radialWidth + gap;
        int visibleRows = Math.max(1, (contentHeight - LIST_HEADER_HEIGHT - LIST_FOOTER_HEIGHT)
                / LIST_ROW_HEIGHT);
        int listY = contentY + LIST_HEADER_HEIGHT;
        int listFooterY = listY + visibleRows * LIST_ROW_HEIGHT;

        int radius = clamp(Math.round(Math.min(radialWidth, contentHeight) * 0.29f), 36, 84);
        int slotSize = clamp(Math.round(radius * 0.62f), 30, 56);
        int radialCx = x + 10 + radialWidth / 2;
        int radialCy = contentY + contentHeight / 2 + 4;
        int footerY = y + height - footerHeight;

        return new Layout(x, y, width, height, headerHeight, footerHeight,
                contentY, contentHeight, radialWidth, listX, listWidth, visibleRows,
                listY, listFooterY, radialCx, radialCy, radius, slotSize, footerY);
    }

    private int[] slotCenter(Layout layout, int index) {
        double angle = -Math.PI / 2 + index * 2.0 * Math.PI / MAX_SLOTS;
        return new int[]{
                layout.radialCx + Math.round(layout.radius * (float) Math.cos(angle)),
                layout.radialCy + Math.round(layout.radius * (float) Math.sin(angle))
        };
    }

    private int slotAt(Layout layout, double mouseX, double mouseY) {
        int size = layout.slotSize;
        for (int i = 0; i < MAX_SLOTS; i++) {
            int[] center = slotCenter(layout, i);
            if (inside(mouseX, mouseY, center[0] - size / 2, center[1] - size / 2, size, size)) {
                return i;
            }
        }
        return -1;
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int width, int height,
                       int accent, boolean ornate) {
        g.fill(x + 2, y + 3, x + width, y + height, 0x66000000);
        g.fill(x, y, x + width, y + height, 0xFF04070C);
        for (int row = 0; row < height; row++) {
            int color = lerpColor(PANEL_TOP, PANEL_BOTTOM,
                    height <= 1 ? 0f : (float) row / (height - 1));
            g.fill(x + 1, y + row, x + width - 1, y + row + 1, color);
        }
        g.fill(x + 7, y + 1, x + width - 7, y + 2, alpha(accent, 0xD8));
        g.fill(x + 1, y + 8, x + 2, y + height - 8, alpha(accent, 0x42));
        g.fill(x + width - 2, y + 8, x + width - 1, y + height - 8, alpha(accent, 0x22));
        g.fill(x + 3, y + 3, x + 5, y + 5, alpha(accent, 0xD0));
        g.fill(x + width - 5, y + 3, x + width - 3, y + 5, alpha(accent, 0xD0));
        if (ornate) {
            g.fill(x + 8, y + 4, x + 9, y + 5, alpha(GOLD_LIGHT, 0xA0));
            g.fill(x + width - 9, y + 4, x + width - 8, y + 5, alpha(GOLD_LIGHT, 0xA0));
        }
    }

    private void drawRune(GuiGraphicsExtractor g, SkillElement element, int cx, int cy,
                          float size, int color) {
        if (element != null) {
            switch (element.id()) {
                case "aqua" -> {
                    line(g, cx, cy - size, cx - size * 0.55f, cy + size * 0.2f, color, 2);
                    line(g, cx, cy - size, cx + size * 0.55f, cy + size * 0.2f, color, 2);
                    fillCircle(g, cx, Math.round(cy + size * 0.4f),
                            Math.max(1, Math.round(size * 0.55f)), color);
                }
                case "inferno" -> {
                    line(g, cx, cy - size, cx, cy + size, color, 2);
                    line(g, cx - size * 0.55f, cy + size * 0.2f,
                            cx + size * 0.55f, cy + size * 0.2f, color, 2);
                }
                default -> {
                    line(g, cx, cy - size, cx - size * 0.5f, cy + size * 0.45f, color, 2);
                    line(g, cx, cy - size, cx + size * 0.5f, cy + size * 0.45f, color, 2);
                    fillCircle(g, cx, Math.round(cy + size * 0.42f),
                            Math.max(1, Math.round(size * 0.38f)), color);
                }
            }
            return;
        }
        line(g, cx, cy - size, cx - size * 0.5f, cy + size * 0.45f, color, 2);
        line(g, cx, cy - size, cx + size * 0.5f, cy + size * 0.45f, color, 2);
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

    private static void fillCircleGradient(GuiGraphicsExtractor g, int cx, int cy, int radius,
                                           int top, int bottom) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            int color = lerpColor(top, bottom,
                    radius <= 0 ? 0f : (float) (dy + radius) / (radius * 2f));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
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

    private String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = out.toString() + text.charAt(i);
            if (font.width(candidate) + ellipsisWidth > maxWidth) break;
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
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
        return ((int) (aa + (ba - aa) * t) << 24)
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private record Layout(
            int x, int y, int w, int h,
            int headerHeight, int footerHeight,
            int contentY, int contentH,
            int radialW, int listX, int listW, int visibleRows,
            int listY, int listFooterY,
            int radialCx, int radialCy,
            int radius, int slotSize,
            int footerY
    ) {
    }

    private static final class SlotData {
        private final int index;
        private final String skillId;
        private final SkillDefinition definition;
        private final SkillElement element;

        private SlotData(int index, String skillId, SkillDefinition definition) {
            this.index = index;
            this.skillId = skillId;
            this.definition = definition;
            this.element = definition == null ? null
                    : SkillCatalog.forSkill(definition.id()).orElse(null);
        }

        private boolean hasSkill() {
            return definition != null && !skillId.isEmpty();
        }
    }

    private static final class AssignableSkill {
        private final String skillId;
        private final SkillDefinition definition;
        private final SkillElement element;

        private AssignableSkill(String skillId, SkillDefinition definition) {
            this.skillId = skillId;
            this.definition = definition;
            this.element = SkillCatalog.forSkill(definition.id()).orElse(null);
        }
    }
}
