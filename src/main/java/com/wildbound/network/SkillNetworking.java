package com.wildbound.network;

import com.wildbound.network.SkillCameraAimPayload;
import com.wildbound.network.SkillChargePayload;
import com.wildbound.attributes.AttributeService;
import com.wildbound.attributes.RpgAttribute;
import com.wildbound.skills.SkillActivationService;
import com.wildbound.skills.SkillAimContext;
import com.wildbound.skills.SkillProgressionService;
import com.wildbound.skills.SkillRadialService;
import com.wildbound.skills.SkillUnlockResult;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Registers the skill menu packets. Unlocks and activations stay
 * server-authoritative: the server validates, then answers with the
 * result so the UI can show feedback.
 */
public final class SkillNetworking {
    private SkillNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(UnlockSkillPayload.TYPE, UnlockSkillPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkillUnlockResultPayload.TYPE, SkillUnlockResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActivateSkillPayload.TYPE, ActivateSkillPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkillActivateResultPayload.TYPE, SkillActivateResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateRadialSkillsPayload.TYPE, UpdateRadialSkillsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RadialSkillsResultPayload.TYPE, RadialSkillsResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkillCameraShakePayload.TYPE, SkillCameraShakePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkillCameraAimPayload.TYPE, SkillCameraAimPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AquaSkillFxPayload.TYPE, AquaSkillFxPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SkillChargePayload.TYPE, SkillChargePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AttributeUpgradePayload.TYPE, AttributeUpgradePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UnlockSkillPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.hasDisconnected()) return;
                SkillUnlockResult result = SkillProgressionService.unlock(player, payload.skillId());
                if (ServerPlayNetworking.canSend(player, SkillUnlockResultPayload.TYPE)) {
                    ServerPlayNetworking.send(player, new SkillUnlockResultPayload(
                            payload.skillId(), result.success(), result.reason()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ActivateSkillPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.hasDisconnected()) return;
                SkillAimContext.withAim(player,
                        new Vec3(payload.aimX(), payload.aimY(), payload.aimZ()), () -> {
                            var result = SkillActivationService.activate(player, payload.skillId());
                            if (ServerPlayNetworking.canSend(player, SkillActivateResultPayload.TYPE)) {
                                ServerPlayNetworking.send(player, new SkillActivateResultPayload(
                                        payload.skillId(), result.success(), result.reason(), result.cooldownRemaining()));
                            }
                            return null;
                        });
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateRadialSkillsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.hasDisconnected()) return;
                SkillRadialService.UpdateResult result =
                        SkillRadialService.update(player, payload.slots());
                if (ServerPlayNetworking.canSend(player, RadialSkillsResultPayload.TYPE)) {
                    // The request metadata is only feedback, never authority.
                    // Reflect it only when it matches the server-accepted slot;
                    // otherwise return a neutral successful update.
                    String actionSkillId = "";
                    int actionSlot = -1;
                    if (result.success() && payload.actionSlot() >= 0
                            && payload.actionSlot() < result.slots().size()) {
                        String acceptedSkill = result.slots().get(payload.actionSlot());
                        if (acceptedSkill.equals(payload.actionSkillId())) {
                            actionSkillId = acceptedSkill;
                            actionSlot = payload.actionSlot();
                        }
                    }
                    ServerPlayNetworking.send(player, new RadialSkillsResultPayload(
                            result.slots(), result.success(), result.reason(),
                            actionSkillId, actionSlot));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SkillChargePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.hasDisconnected()) return;
                SkillAimContext.withAim(player,
                        new Vec3(payload.aimX(), payload.aimY(), payload.aimZ()), () -> {
                            SkillActivationService.activateWithCharge(player, payload.skillId(), payload.chargeLevel());
                            return null;
                        });
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(AttributeUpgradePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.hasDisconnected()) return;
                try {
                    RpgAttribute attribute = RpgAttribute.valueOf(payload.attribute().toUpperCase(java.util.Locale.ROOT));
                    boolean success = AttributeService.increaseAttribute(player, attribute, 1);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(success
                            ? "§aAtributo mejorado: " + attribute.name()
                            : "§cNo se pudo mejorar el atributo (puntos insuficientes o límite alcanzado)."));
                } catch (IllegalArgumentException exception) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cAtributo desconocido."));
                }
            });
        });

    }
}