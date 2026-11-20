package com.wildbound.client.hud;

import com.wildbound.Wildbound;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RpgAttachments;
import com.wildbound.progression.ExperienceCurve;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Minimal HUD that proves the engine is synced and working. */
public final class RpgHud {
    private RpgHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(Wildbound.id("rpg_hud"), (extractor, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
            if (profile == null) return;

            int x = 6;
            int y = 6;
            extractor.fill(x - 2, y - 2, x + 150, y + 62, 0x90000000);
            extractor.text(mc.font, Component.literal("WILDBOUND  Lv " + profile.level()), x, y, 0xFFD66B);

            long required = ExperienceCurve.required(profile.level());
            String xp = required > 0 ? profile.experience() + " / " + required : "MAX";
            extractor.text(mc.font, Component.literal("XP " + xp), x, y + 10, 0xFFFFFF);

            extractor.text(mc.font, Component.literal("HP " + fmt(player.getHealth()) + " / " + fmt(player.getMaxHealth())),
                    x, y + 22, 0xFF6666);
            int maxMana = (int) com.wildbound.attributes.DerivedStatsCalculator.calculate(profile).maxMana();
            int maxStamina = (int) com.wildbound.attributes.DerivedStatsCalculator.calculate(profile).maxStamina();
            extractor.text(mc.font, Component.literal("MP " + (int) profile.mana() + " / " + maxMana), x, y + 34, 0x6699FF);
            extractor.text(mc.font, Component.literal("ST " + (int) profile.stamina() + " / " + maxStamina), x, y + 46, 0x66DD88);
        });
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }
}
