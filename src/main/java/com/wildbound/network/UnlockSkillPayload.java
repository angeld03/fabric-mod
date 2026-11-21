package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server request to unlock one skill. */
public record UnlockSkillPayload(String skillId) implements CustomPacketPayload {
    public static final Type<UnlockSkillPayload> TYPE = new Type<>(Wildbound.id("unlock_skill"));
    public static final StreamCodec<FriendlyByteBuf, UnlockSkillPayload> CODEC =
            CustomPacketPayload.codec(UnlockSkillPayload::write, UnlockSkillPayload::read);

    private static UnlockSkillPayload read(FriendlyByteBuf buffer) {
        return new UnlockSkillPayload(buffer.readUtf(64));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(skillId, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
