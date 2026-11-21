package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server answer to an unlock request, used by the skill menu for feedback. */
public record SkillUnlockResultPayload(String skillId, boolean success, String reason) implements CustomPacketPayload {
    public static final Type<SkillUnlockResultPayload> TYPE =
            new Type<>(Wildbound.id("skill_unlock_result"));
    public static final StreamCodec<FriendlyByteBuf, SkillUnlockResultPayload> CODEC =
            CustomPacketPayload.codec(SkillUnlockResultPayload::write, SkillUnlockResultPayload::read);

    private static SkillUnlockResultPayload read(FriendlyByteBuf buffer) {
        return new SkillUnlockResultPayload(buffer.readUtf(64), buffer.readBoolean(), buffer.readUtf(64));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(skillId, 64);
        buffer.writeBoolean(success);
        buffer.writeUtf(reason, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
