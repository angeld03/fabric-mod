package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client response for skill activation. */
public record SkillActivateResultPayload(String skillId, boolean success, String reason, float cooldownRemaining)
        implements CustomPacketPayload {
    public static final Type<SkillActivateResultPayload> TYPE = new Type<>(Wildbound.id("skill_activate_result"));
    public static final StreamCodec<FriendlyByteBuf, SkillActivateResultPayload> CODEC =
            CustomPacketPayload.codec(SkillActivateResultPayload::write, SkillActivateResultPayload::read);

    private static SkillActivateResultPayload read(FriendlyByteBuf buffer) {
        return new SkillActivateResultPayload(
                buffer.readUtf(64),
                buffer.readBoolean(),
                buffer.readUtf(32),
                buffer.readFloat()
        );
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(skillId, 64);
        buffer.writeBoolean(success);
        buffer.writeUtf(reason, 32);
        buffer.writeFloat(cooldownRemaining);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SkillActivateResultPayload ok(String skillId, float cooldownRemaining) {
        return new SkillActivateResultPayload(skillId, true, "", cooldownRemaining);
    }

    public static SkillActivateResultPayload fail(String skillId, String reason) {
        return new SkillActivateResultPayload(skillId, false, reason, 0f);
    }
}