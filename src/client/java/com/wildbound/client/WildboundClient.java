package com.wildbound.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildbound.client.hud.RpgHud;
import com.wildbound.client.screen.SkillRadialMenu;
import com.wildbound.client.screen.SkillScreen;
import com.wildbound.network.ActivateSkillPayload;
import com.wildbound.network.RadialSkillsResultPayload;
import com.wildbound.network.SkillActivateResultPayload;
import com.wildbound.network.SkillCameraAimPayload;
import com.wildbound.network.SkillCameraShakePayload;
import com.wildbound.network.SkillUnlockResultPayload;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RpgAttachments;
import com.wildbound.skills.SkillActivationService;
import com.wildbound.skills.SkillCatalog;
import com.wildbound.skills.SkillDefinition;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class WildboundClient implements ClientModInitializer {
    public static final KeyMapping OPEN_SKILLS = new KeyMapping(
            "key.wildbound.skills", InputConstants.KEY_H, KeyMapping.Category.MISC);
    public static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key.wildbound.radial", InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY);
    // NOTE: the 3-arg KeyMapping constructor always builds a KEYBOARD mapping, so a
    // mouse button requires the InputConstants.Type overload below. In 26.3 the
    // mouse buttons are 1=left, 2=middle, 3=right.
    //
    // The default right-click here is intercepted by RadialMouseMixin so that it
    // casts the radial skill instead of also using the held item. Keeping the
    // mapping registered means the action is still visible and rebindable in
    // Minecraft's controls screen; rebinding it to a keyboard key keeps working
    // through the tick handler below.
    public static final KeyMapping USE_SELECTED_SKILL = new KeyMapping(
            "key.wildbound.use_radial", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT,
            KeyMapping.Category.GAMEPLAY);

    @Override
    public void onInitializeClient() {
        RpgHud.register();

        KeyMappingHelper.registerKeyMapping(OPEN_SKILLS);
        KeyMappingHelper.registerKeyMapping(OPEN_RADIAL);
        KeyMappingHelper.registerKeyMapping(USE_SELECTED_SKILL);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SKILLS.consumeClick()) {
                if (client.gui.screen() instanceof SkillScreen screen) {
                    screen.onClose();
                } else if (client.gui.screen() == null && client.player != null) {
                    client.setScreenAndShow(new SkillScreen());
                }
            }
            while (OPEN_RADIAL.consumeClick()) {
                if (client.gui.screen() instanceof SkillRadialMenu radial) {
                    radial.onClose();
                } else if (client.gui.screen() == null && client.player != null) {
                    client.setScreenAndShow(new SkillRadialMenu());
                }
            }

            // Right mouse uses the selected radial slot while no screen is open.
            // The default right-mouse binding is handled by RadialMouseMixin so
            // it can suppress the vanilla "use item"/block-placement action.
            // Mouse clicks are only drained here to avoid a second cast; this
            // branch still casts normally when the action is rebound to a key.
            InputConstants.Key defaultRightClick =
                    InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT);
            while (USE_SELECTED_SKILL.consumeClick()) {
                // The default right-mouse action is already handled (and its
                // vanilla use/placement suppressed) by RadialMouseMixin.
                if (USE_SELECTED_SKILL.matches(defaultRightClick)) continue;
                if (client.gui.screen() != null || client.player == null) continue;
                useSelectedRadialSkill(client.player);
            }
            // Decay cinematic camera aim and update the Water Bolt's quiet idle FX.
            SkillCameraAim.tick();
            WaterBoltIdlePresentation.tick(client);
            WaterBoltChargePresentation.tick(client);
        });

        ClientPlayNetworking.registerGlobalReceiver(SkillUnlockResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> SkillScreen.handleResult(payload)));

        ClientPlayNetworking.registerGlobalReceiver(SkillActivateResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.success()) {
                        SkillActivationService.onClientActivate(payload.skillId(), payload.cooldownRemaining());
                        String skillName = payload.skillId();
                        SkillDefinition definition = SkillCatalog.getSkillDefinition(payload.skillId());
                        if (definition != null) skillName = definition.name();
                        context.client().player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal("§a" + skillName
                                        + " §7(cd: " + String.format("%.1fs", payload.cooldownRemaining()) + ")"));
                    } else if ("cooldown".equals(payload.reason()) && payload.cooldownRemaining() > 0f) {
                        // Normal cooldown feedback lives in the HUD; do not
                        // flood the chat while the player keeps clicking.
                        SkillActivationService.onClientActivate(payload.skillId(), payload.cooldownRemaining());
                    } else {
                        context.client().player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal("§cNo se puede activar: " + activationFailureText(payload.reason())));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(SkillCameraShakePayload.TYPE, (payload, context) ->
                context.client().execute(() -> SkillCameraShake.add(payload)));

        ClientPlayNetworking.registerGlobalReceiver(SkillCameraAimPayload.TYPE, (payload, context) ->
                context.client().execute(() -> SkillCameraAim.play(payload)));

        ClientPlayNetworking.registerGlobalReceiver(RadialSkillsResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    PlayerRpgProfile profile = context.client().player == null ? null
                            : context.client().player.getAttached(RpgAttachments.PROFILE);
                    if (payload.success()) {
                        RadialSkillState.acceptServerSlots(profile, payload.slots());
                    } else {
                        RadialSkillState.rejectPendingSlots();
                    }
                    if (context.client().gui.screen() instanceof SkillRadialMenu radial) {
                        radial.onRadialResult(payload);
                    } else if (context.client().gui.screen() instanceof SkillScreen skills) {
                        skills.onRadialResult(payload);
                    } else if (context.client().player != null) {
                        String message = payload.success()
                                ? "§aMenú radial actualizado"
                                : "§cNo se pudo actualizar el menú radial: " + radialFailureText(payload.reason());
                        context.client().player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal(message));
                    }
                }));

        // Never let a radial layout (or the optimistic mirror of it) outlive the
        // connection it came from.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            RadialSkillState.reset();
            SkillChargeState.reset();
            SkillCameraAim.reset();
            WaterBoltIdlePresentation.reset();
            WaterBoltChargePresentation.reset();
            WaterBoltChargePresentation.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RadialSkillState.reset();
            SkillChargeState.reset();
            SkillCameraAim.reset();
            WaterBoltIdlePresentation.reset();
        });
    }

    /**
     * Casts the skill assigned to the selected radial slot. Returns {@code true}
     * when a request was actually sent, so callers can suppress the vanilla
     * right-click action.
     */
    public static boolean useSelectedRadialSkill(LocalPlayer player) {
        if (player == null || Minecraft.getInstance().gui.screen() != null) return false;
        PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
        if (profile == null) return false;
        String skillId = RadialSkillState.selectedSkill(profile);
        if (skillId == null || skillId.isEmpty()) return false;
        if (!ClientPlayNetworking.canSend(ActivateSkillPayload.TYPE)) return false;
        // Do not send a request the server is guaranteed to reject.
        if (SkillActivationService.getClientCooldown(skillId) > 0f) return false;
        ClientPlayNetworking.send(new ActivateSkillPayload(skillId));
        return true;
    }

    /**
     * Returns the skill ID assigned to the currently selected radial slot, or
     * null/empty if none. Used by the wand charge system to know what to fire.
     */
    public static String selectedRadialSkill(LocalPlayer player) {
        if (player == null) return null;
        PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
        if (profile == null) return null;
        return RadialSkillState.selectedSkill(profile);
    }

    private static String activationFailureText(String reason) {
        return switch (reason) {
            case "unknown_skill" -> "habilidad desconocida";
            case "skill_not_unlocked" -> "habilidad no desbloqueada";
            case "skill_not_implemented" -> "habilidad sin efecto registrado";
            case "cooldown" -> "en enfriamiento";
            case "not_enough_mana" -> "maná insuficiente";
            case "not_enough_stamina" -> "resistencia insuficiente";
            case "execution_failed" -> "error al ejecutar";
            default -> "intento rechazado";
        };
    }

    private static String radialFailureText(String reason) {
        return switch (reason) {
            case "unknown_skill" -> "habilidad desconocida";
            case "skill_not_unlocked" -> "habilidad no desbloqueada";
            case "duplicate_skill" -> "habilidad repetida";
            default -> "operación rechazada";
        };
    }
}
