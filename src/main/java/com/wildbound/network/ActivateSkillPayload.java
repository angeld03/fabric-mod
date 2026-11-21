package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server request to activate an unlocked skill. */
public record ActivateSkillPayload(String skillId) implements CustomPacketPayload {
    public static final Type<ActivateSkillPayload> TYPE = new Type<>(Wildbound.id("activate_skill"));
    public static final StreamCodec<FriendlyByteBuf, ActivateSkillPayload> CODEC =
            CustomPacketPayload.codec(ActivateSkillPayload::write, ActivateSkillPayload::read);

    private static ActivateSkillPayload read(FriendlyByteBuf buffer) {
        return new ActivateSkillPayload(buffer.readUtf(64));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(skillId, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}