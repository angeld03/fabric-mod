package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server skill request including the exact view direction at cast time. */
public record ActivateSkillPayload(String skillId, double aimX, double aimY, double aimZ)
        implements CustomPacketPayload {
    public static final Type<ActivateSkillPayload> TYPE = new Type<>(Wildbound.id("activate_skill"));
    public static final StreamCodec<FriendlyByteBuf, ActivateSkillPayload> CODEC =
            CustomPacketPayload.codec(ActivateSkillPayload::write, ActivateSkillPayload::read);

    /** Compatibility constructor for code paths which still use the server's current look vector. */
    public ActivateSkillPayload(String skillId) {
        this(skillId, 0.0, 0.0, 1.0);
    }

    public ActivateSkillPayload {
        skillId = skillId == null ? "" : skillId.substring(0, Math.min(64, skillId.length()));
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

    private static ActivateSkillPayload read(FriendlyByteBuf buffer) {
        return new ActivateSkillPayload(buffer.readUtf(64),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(skillId, 64);
        buffer.writeDouble(aimX);
        buffer.writeDouble(aimY);
        buffer.writeDouble(aimZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
