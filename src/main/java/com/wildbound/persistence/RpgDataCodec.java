package com.wildbound.persistence;

import com.wildbound.player.AttributeContainer;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerStatistics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/** Binary codec used by Fabric Data Attachments to sync profiles to clients. */
public final class RpgDataCodec {
    private static final int MAX_STRING = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerRpgProfile> STREAM_CODEC =
            StreamCodec.of(RpgDataCodec::encode, RpgDataCodec::decode);

    private RpgDataCodec() {
    }

    private static PlayerRpgProfile decode(RegistryFriendlyByteBuf buf) {
        int schema = buf.readVarInt();
        String uuid = buf.readUtf(MAX_STRING);
        int level = buf.readVarInt();
        long experience = buf.readLong();
        long total = buf.readLong();
        int attributePoints = buf.readVarInt();
        int skillPoints = buf.readVarInt();
        AttributeContainer attributes = new AttributeContainer(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        float mana = buf.readFloat();
        float stamina = buf.readFloat();
        List<String> skills = readStrings(buf);
        List<String> realms = readStrings(buf);
        PlayerStatistics stats = new PlayerStatistics(
                buf.readLong(), buf.readLong(), buf.readLong(),
                buf.readLong(), buf.readLong(), buf.readLong());
        return new PlayerRpgProfile(schema, uuid, level, experience, total, attributePoints,
                skillPoints, attributes, mana, stamina, skills, realms, stats);
    }

    private static void encode(RegistryFriendlyByteBuf buf, PlayerRpgProfile profile) {
        buf.writeVarInt(profile.schemaVersion());
        buf.writeUtf(profile.uuid(), MAX_STRING);
        buf.writeVarInt(profile.level());
        buf.writeLong(profile.experience());
        buf.writeLong(profile.totalExperience());
        buf.writeVarInt(profile.attributePoints());
        buf.writeVarInt(profile.skillPoints());
        AttributeContainer a = profile.attributes();
        buf.writeVarInt(a.strength());
        buf.writeVarInt(a.vitality());
        buf.writeVarInt(a.agility());
        buf.writeVarInt(a.intelligence());
        buf.writeVarInt(a.spirit());
        buf.writeFloat(profile.mana());
        buf.writeFloat(profile.stamina());
        writeStrings(buf, profile.unlockedSkills());
        writeStrings(buf, profile.unlockedRealms());
        PlayerStatistics s = profile.stats();
        buf.writeLong(s.mobsDefeated());
        buf.writeLong(s.bossesDefeated());
        buf.writeLong(s.questsCompleted());
        buf.writeLong(s.dimensionsDiscovered());
        buf.writeLong(s.creaturesDiscovered());
        buf.writeLong(s.dungeonsCompleted());
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(buf.readVarInt(), 512));
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(buf.readUtf(MAX_STRING));
        return list;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s, MAX_STRING);
    }
}
