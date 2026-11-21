package com.wildbound.network;

import com.wildbound.Wildbound;
import com.wildbound.player.RadialSkillConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server response for a radial-slot update. */
public record RadialSkillsResultPayload(
        List<String> slots,
        boolean success,
        String reason,
        String actionSkillId,
        int actionSlot
) implements CustomPacketPayload {
    public static final Type<RadialSkillsResultPayload> TYPE =
            new Type<>(Wildbound.id("radial_skills_result"));
    public static final StreamCodec<FriendlyByteBuf, RadialSkillsResultPayload> CODEC =
            CustomPacketPayload.codec(RadialSkillsResultPayload::write, RadialSkillsResultPayload::read);

    public RadialSkillsResultPayload {
        slots = RadialSkillConfig.copySlots(slots);
        reason = reason == null ? RadialSkillConfig.EMPTY : reason;
        actionSkillId = actionSkillId == null ? RadialSkillConfig.EMPTY : actionSkillId.trim();
    }

    private static RadialSkillsResultPayload read(FriendlyByteBuf buffer) {
        List<String> slots = new ArrayList<>(RadialSkillConfig.SLOT_COUNT);
        for (int i = 0; i < RadialSkillConfig.SLOT_COUNT; i++) {
            slots.add(buffer.readUtf(64));
        }
        return new RadialSkillsResultPayload(
                slots,
                buffer.readBoolean(),
                buffer.readUtf(32),
                buffer.readUtf(64),
                buffer.readVarInt()
        );
    }

    private void write(FriendlyByteBuf buffer) {
        for (int i = 0; i < RadialSkillConfig.SLOT_COUNT; i++) {
            buffer.writeUtf(slots.get(i), 64);
        }
        buffer.writeBoolean(success);
        buffer.writeUtf(reason, 32);
        buffer.writeUtf(actionSkillId, 64);
        buffer.writeVarInt(actionSlot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
