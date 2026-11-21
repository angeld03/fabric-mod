package com.wildbound.network;

import com.wildbound.Wildbound;
import com.wildbound.player.RadialSkillConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Client request to replace the five radial slots with a validated layout. */
public record UpdateRadialSkillsPayload(List<String> slots, String actionSkillId, int actionSlot)
        implements CustomPacketPayload {
    public static final Type<UpdateRadialSkillsPayload> TYPE =
            new Type<>(Wildbound.id("update_radial_skills"));
    public static final StreamCodec<FriendlyByteBuf, UpdateRadialSkillsPayload> CODEC =
            CustomPacketPayload.codec(UpdateRadialSkillsPayload::write, UpdateRadialSkillsPayload::read);

    public UpdateRadialSkillsPayload {
        slots = RadialSkillConfig.copySlots(slots);
        actionSkillId = actionSkillId == null ? RadialSkillConfig.EMPTY : actionSkillId.trim();
    }

    private static UpdateRadialSkillsPayload read(FriendlyByteBuf buffer) {
        List<String> slots = new java.util.ArrayList<>(RadialSkillConfig.SLOT_COUNT);
        for (int i = 0; i < RadialSkillConfig.SLOT_COUNT; i++) {
            slots.add(buffer.readUtf(64));
        }
        return new UpdateRadialSkillsPayload(
                slots,
                buffer.readUtf(64),
                buffer.readVarInt()
        );
    }

    private void write(FriendlyByteBuf buffer) {
        for (int i = 0; i < RadialSkillConfig.SLOT_COUNT; i++) {
            buffer.writeUtf(slots.get(i), 64);
        }
        buffer.writeUtf(actionSkillId, 64);
        buffer.writeVarInt(actionSlot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
