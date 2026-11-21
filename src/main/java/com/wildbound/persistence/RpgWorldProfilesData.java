package com.wildbound.persistence;

import com.mojang.serialization.Codec;
import com.wildbound.Wildbound;
import com.wildbound.player.PlayerRpgProfile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * World-scoped durable backup of player RPG profiles.
 *
 * <p>Fabric player attachments remain the fast/live copy and continue to sync
 * to the owner. This SavedData store is an independent recovery copy in the
 * world's data folder, so a missing or malformed player attachment cannot
 * silently reset a character to level one.</p>
 */
public final class RpgWorldProfilesData extends SavedData {
    private static final Codec<RpgWorldProfilesData> CODEC =
            Codec.unboundedMap(Codec.STRING, PlayerRpgProfile.CODEC)
                    .xmap(RpgWorldProfilesData::new, data -> Map.copyOf(data.profiles));

    private static final SavedDataType<RpgWorldProfilesData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Wildbound.MOD_ID, "rpg_profiles"),
            RpgWorldProfilesData::new,
            CODEC,
            null
    );

    private final Map<String, PlayerRpgProfile> profiles = new HashMap<>();

    public RpgWorldProfilesData() {
    }

    private RpgWorldProfilesData(Map<String, PlayerRpgProfile> stored) {
        if (stored == null) return;
        stored.forEach((uuid, profile) -> {
            if (uuid != null && !uuid.isBlank() && profile != null
                    && uuid.equals(profile.uuid()) && profiles.size() < 100_000) {
                profiles.put(uuid, profile);
            }
        });
    }

    public static RpgWorldProfilesData get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        return overworld == null ? null : overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerRpgProfile getProfile(String uuid) {
        return profiles.get(uuid);
    }

    public boolean contains(String uuid) {
        return profiles.containsKey(uuid);
    }

    public void putProfile(String uuid, PlayerRpgProfile profile) {
        if (uuid == null || uuid.isBlank() || profile == null || !uuid.equals(profile.uuid())) return;
        PlayerRpgProfile previous = profiles.put(uuid, profile);
        if (!profile.equals(previous)) setDirty();
    }
}
