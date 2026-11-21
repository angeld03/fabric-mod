package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to spend one attribute point on a named RPG attribute. */
public record AttributeUpgradePayload(String attribute) implements CustomPacketPayload {
    public static final Type<AttributeUpgradePayload> TYPE =
            new Type<>(Wildbound.id("upgrade_attribute"));
    public static final StreamCodec<FriendlyByteBuf, AttributeUpgradePayload> CODEC =
            CustomPacketPayload.codec(AttributeUpgradePayload::write, AttributeUpgradePayload::read);

    public AttributeUpgradePayload {
        attribute = attribute == null ? "" : attribute.trim();
    }

    private static AttributeUpgradePayload read(FriendlyByteBuf buf) {
        return new AttributeUpgradePayload(buf.readUtf(32));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(attribute, 32);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
