package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Clientbound camera shake request for cinematic skill impacts. */
public record SkillCameraShakePayload(double x, double y, double z,
                                      int intensity, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<SkillCameraShakePayload> TYPE =
            new Type<>(Wildbound.id("skill_camera_shake"));
    public static final StreamCodec<FriendlyByteBuf, SkillCameraShakePayload> CODEC =
            CustomPacketPayload.codec(SkillCameraShakePayload::write, SkillCameraShakePayload::read);

    public SkillCameraShakePayload {
        intensity = Math.max(1, Math.min(20, intensity));
        durationTicks = Math.max(1, Math.min(200, durationTicks));
    }

    private static SkillCameraShakePayload read(FriendlyByteBuf buffer) {
        return new SkillCameraShakePayload(
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeVarInt(intensity);
        buffer.writeVarInt(durationTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
