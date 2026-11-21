package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serverbound: the client notifies the server that the player has released
 * the charge button, with how long they held it (0.0 = instant tap, 1.0 =
 * full charge).
 */
public record SkillChargePayload(String skillId, float chargeLevel)
        implements CustomPacketPayload {
    public static final Type<SkillChargePayload> TYPE =
            new Type<>(Wildbound.id("skill_charge"));
    public static final StreamCodec<FriendlyByteBuf, SkillChargePayload> CODEC =
            CustomPacketPayload.codec(SkillChargePayload::write, SkillChargePayload::read);

    public SkillChargePayload {
        chargeLevel = Math.max(0f, Math.min(1f, chargeLevel));
    }

    private static SkillChargePayload read(FriendlyByteBuf buffer) {
        return new SkillChargePayload(buffer.readUtf(64), buffer.readFloat());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(skillId);
        buffer.writeFloat(chargeLevel);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}