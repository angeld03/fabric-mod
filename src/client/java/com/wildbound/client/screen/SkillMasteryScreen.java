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

/** Read-only detail view for the selected skill's persistent mastery track. */
public final class SkillMasteryScreen extends Screen {
    private final String skillId;

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
        g.fill(0, 0, w, h, 0xE908101B);

        int panelW = Math.min(520, w - 24);
        int panelH = Math.min(300, h - 24);
        int x = (w - panelW) / 2;
        int y = (h - panelH) / 2;
        g.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, 0xFFB88A42);
        g.fill(x, y, x + panelW, y + panelH, 0xF01B2A3B);
        g.fill(x, y, x + panelW, y + 2, 0xFFFFD479);

        String title = tr("wildbound.mastery.title");
        g.text(font, title, x + 16, y + 14, 0xFFFFE6A8, true);

        SkillDefinition skill = SkillRegistry.get(skillId).orElse(null);
        if (skill == null) {
            g.text(font, tr("wildbound.mastery.missing"), x + 16, y + 42, 0xFFFF9AA6, false);
            return;
        }
        g.text(font, skill.name(), x + 16, y + 36, 0xFF8EDBFF, true);
        g.text(font, skill.description(), x + 16, y + 53, 0xFFB9C9DA, false);

        PlayerRpgProfile profile = minecraft == null || minecraft.player == null ? null
                : minecraft.player.getAttached(RpgAttachments.PROFILE);
        SkillProgress progress = profile == null ? SkillProgress.INITIAL : profile.getSkillProgress(skillId);

        int textY = y + 82;
        g.text(font, tr("wildbound.mastery.rank", progress.rank(), SkillProgress.MAX_RANK),
                x + 16, textY, 0xFFFFFFFF, true);
        String xp = progress.rank() >= SkillProgress.MAX_RANK
                ? tr("wildbound.skills.mastery_max")
                : tr("wildbound.skills.mastery_xp", progress.experience(), progress.experienceRequired());
        g.text(font, xp, x + panelW - 16 - font.width(xp), textY, 0xFFFFD479, true);

        int barX = x + 16;
        int barY = textY + 17;
        int barW = panelW - 32;
        g.fill(barX, barY, barX + barW, barY + 8, 0xFF07111B);
        float rankProgress = progress.rank() >= SkillProgress.MAX_RANK ? 1f
                : (float) progress.experience() / Math.max(1L, progress.experienceRequired());
        int filled = Math.round((barW - 2) * Math.max(0f, Math.min(1f, rankProgress)));
        if (filled > 0) g.fillGradient(barX + 1, barY + 1, barX + 1 + filled, barY + 7,
                0xFFB7F7FF, 0xFF12617D);

        int infoY = barY + 20;
        g.text(font, tr("wildbound.mastery.total_xp", progress.totalExperience()),
                x + 16, infoY, 0xFFD1DEEC, false);
        g.text(font, tr("wildbound.mastery.mastery", Math.round(progress.mastery())),
                x + 16, infoY + 16, 0xFFD1DEEC, false);

        int upgradesY = infoY + 43;
        g.text(font, tr("wildbound.mastery.upgrades", progress.upgrades().size()),
                x + 16, upgradesY, 0xFFFFD479, true);
        if (progress.upgrades().isEmpty()) {
            g.text(font, tr("wildbound.mastery.no_upgrades"), x + 16, upgradesY + 17,
                    0xFF9FB0C3, false);
        } else {
            int lineY = upgradesY + 17;
            int maxLines = Math.max(1, (y + panelH - 32 - lineY) / 14);
            for (int i = 0; i < Math.min(progress.upgrades().size(), maxLines); i++) {
                String upgrade = progress.upgrades().get(i).replace('_', ' ');
                g.text(font, "• " + upgrade, x + 20, lineY + i * 14, 0xFFB9E7D0, false);
            }
        }

        String hint = tr("wildbound.mastery.back");
        g.text(font, hint, x + panelW - 16 - font.width(hint),
                y + panelH - 20, 0xFF8D9EB2, false);
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
