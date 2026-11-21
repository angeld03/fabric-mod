package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serverbound charge release: skill ID, charge fraction and the view direction
 * sampled at the actual firing moment.
 */
public record SkillChargePayload(String skillId, float chargeLevel,
                                 double aimX, double aimY, double aimZ)
        implements CustomPacketPayload {
    public static final Type<SkillChargePayload> TYPE =
            new Type<>(Wildbound.id("skill_charge"));
    public static final StreamCodec<FriendlyByteBuf, SkillChargePayload> CODEC =
            CustomPacketPayload.codec(SkillChargePayload::write, SkillChargePayload::read);

    /** Compatibility constructor for callers that do not provide an aim vector. */
    public SkillChargePayload(String skillId, float chargeLevel) {
        this(skillId, chargeLevel, 0.0, 0.0, 1.0);
    }

    public SkillChargePayload {
        skillId = skillId == null ? "" : skillId.substring(0, Math.min(64, skillId.length()));
        chargeLevel = Float.isFinite(chargeLevel) ? Math.max(0f, Math.min(1f, chargeLevel)) : 0f;
        double length = Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ);
        if (!Double.isFinite(aimX) || !Double.isFinite(aimY) || !Double.isFinite(aimZ)
                || !Double.isFinite(length) || length < 1.0E-6) {
            aimX = 0.0;
            aimY = 0.0;
            aimZ = 1.0;
        } else {
            aimX /= length;
            aimY /= length;
            aimZ /= length;
        }
    }

    private static SkillChargePayload read(FriendlyByteBuf buffer) {
        return new SkillChargePayload(buffer.readUtf(64), buffer.readFloat(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(skillId, 64);
        buffer.writeFloat(chargeLevel);
        buffer.writeDouble(aimX);
        buffer.writeDouble(aimY);
        buffer.writeDouble(aimZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
