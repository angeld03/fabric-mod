package com.wildbound.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.attributes.DerivedStats;
import com.wildbound.attributes.DerivedStatsCalculator;
import com.wildbound.attributes.RpgAttribute;
import com.wildbound.core.WildboundConfig;
import com.wildbound.client.WildboundClient;
import com.wildbound.network.AttributeUpgradePayload;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RpgAttachments;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Character sheet with server-authoritative one-point attribute allocation. */
public final class CharacterScreen extends Screen {
    private static final RpgAttribute[] ATTRIBUTES = RpgAttribute.values();
    private final int[] buttonX = new int[ATTRIBUTES.length];
    private final int[] buttonY = new int[ATTRIBUTES.length];
    private final int[] buttonW = new int[ATTRIBUTES.length];
    private final int[] buttonH = new int[ATTRIBUTES.length];

    public CharacterScreen() {
        super(Component.translatable("wildbound.character.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        g.fill(0, 0, w, h, 0xE908101B);
        g.fill(12, 12, w - 12, h - 12, 0xF01B2A3B);
        g.fill(12, 12, w - 12, 14, 0xFFFFD479);
        g.text(font, tr("wildbound.character.title").toUpperCase(java.util.Locale.ROOT), 24, 23, 0xFFFFE6A8, true);

        PlayerRpgProfile profile = minecraft.player == null ? null
                : minecraft.player.getAttached(RpgAttachments.PROFILE);
        if (profile == null) {
            g.text(font, tr("wildbound.character.waiting"), 24, 52, 0xFFFFFFFF, false);
            return;
        }

        g.text(font, tr("wildbound.character.level", profile.level()), 24, 43, 0xFFFFFFFF, true);
        g.text(font, tr("wildbound.character.xp", profile.experience(), profile.totalExperience()),
                24, 57, 0xFF9FB7CF, false);
        g.text(font, tr("wildbound.character.attribute_points", profile.attributePoints()),
                24, 78, profile.attributePoints() > 0 ? 0xFFFFD479 : 0xFF9FB7CF, true);

        boolean compact = w < 720;
        int left = 24;
        int top = compact ? 100 : 108;
        int rowHeight = compact ? 24 : 29;
        int attributeWidth = compact ? Math.min(245, w - 48) : 245;
        g.text(font, tr("wildbound.character.attributes"), left, top - 15, 0xFF8EDBFF, true);
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            RpgAttribute attribute = ATTRIBUTES[i];
            int y = top + i * rowHeight;
            int value = profile.attributes().get(attribute);
            g.fill(left, y, left + attributeWidth, y + 21, 0xAA0A1421);
            g.fill(left, y, left + 2, y + 21, 0xFF4A9BCB);
            g.text(font, tr("wildbound.attribute." + attribute.id()), left + 9, y + 6, 0xFFEAF4FF, true);

            int bx = left + attributeWidth - 37;
            int valueX = bx - 30;
            g.text(font, String.valueOf(value), valueX, y + 6, 0xFFFFD479, true);
            buttonX[i] = bx;
            buttonY[i] = y + 2;
            buttonW[i] = 28;
            buttonH[i] = 17;
            boolean enabled = profile.attributePoints() > 0 && value < WildboundConfig.maxAttribute;
            int color = enabled ? (inside(mouseX, mouseY, bx, y + 2, 28, 17)
                    ? 0xFF477DA0 : 0xFF274C68) : 0xFF303A45;
            g.fill(bx, y + 2, bx + 28, y + 19, color);
            g.text(font, "+", bx + 10, y + 6, enabled ? 0xFFFFFFFF : 0xFF7D8995, true);
        }

        DerivedStats stats = DerivedStatsCalculator.calculate(profile);
        g.text(font, tr("wildbound.character.derived_stats"),
                compact ? left : left + 285, compact ? top + ATTRIBUTES.length * rowHeight + 18 : top - 15,
                0xFF8EDBFF, true);
        String[] lines = {
                tr("wildbound.character.stat.max_health", fmt(stats.maxHealth())),
                tr("wildbound.character.stat.max_mana", fmt(stats.maxMana())),
                tr("wildbound.character.stat.max_stamina", fmt(stats.maxStamina())),
                tr("wildbound.character.stat.melee_power", fmt(stats.meleePower())),
                tr("wildbound.character.stat.spell_power", fmt(stats.spellPower())),
                tr("wildbound.character.stat.physical_defense", fmt(stats.physicalDefense())),
                tr("wildbound.character.stat.magic_defense", fmt(stats.magicDefense())),
                tr("wildbound.character.stat.critical_chance", fmt(stats.criticalChance() * 100f)),
                tr("wildbound.character.stat.critical_damage", fmt(stats.criticalDamage())),
                tr("wildbound.character.stat.movement_speed", fmt(stats.movementSpeedBonus() * 100f)),
                tr("wildbound.character.stat.dodge", fmt(stats.dodgeBonus() * 100f)),
                tr("wildbound.character.stat.mana_regen", fmt(stats.manaRegeneration())),
                tr("wildbound.character.stat.stamina_regen", fmt(stats.staminaRegeneration()))
        };
        if (compact) {
            int statsY = top + ATTRIBUTES.length * rowHeight + 33;
            int columnGap = 10;
            int columnWidth = (w - 48 - columnGap) / 2;
            for (int i = 0; i < lines.length; i++) {
                int column = i / 7;
                int row = i % 7;
                String line = fit(lines[i], columnWidth);
                g.text(font, line, left + column * (columnWidth + columnGap),
                        statsY + row * 14, 0xFFD1DEEC, false);
            }
        } else {
            int sx = left + 285;
            for (int i = 0; i < lines.length; i++) {
                g.text(font, lines[i], sx, top + i * 15, 0xFFD1DEEC, false);
            }
        }

        g.text(font, tr("wildbound.character.footer", WildboundConfig.maxAttribute),
                24, h - 28, 0xFF8D9EB2, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        PlayerRpgProfile profile = minecraft.player == null ? null
                : minecraft.player.getAttached(RpgAttachments.PROFILE);
        if (profile == null || profile.attributePoints() <= 0
                || !ClientPlayNetworking.canSend(AttributeUpgradePayload.TYPE)) return true;

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            if (inside(event.x(), event.y(), buttonX[i], buttonY[i], buttonW[i], buttonH[i])
                    && profile.attributes().get(ATTRIBUTES[i]) < WildboundConfig.maxAttribute) {
                ClientPlayNetworking.send(new AttributeUpgradePayload(ATTRIBUTES[i].name()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || WildboundClient.OPEN_CHARACTER.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String candidate = out.toString() + value.charAt(i);
            if (font.width(candidate + ellipsis) > maxWidth) break;
            out.append(value.charAt(i));
        }
        return out + ellipsis;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
