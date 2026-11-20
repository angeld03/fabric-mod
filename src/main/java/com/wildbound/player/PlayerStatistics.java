package com.wildbound.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** General lifetime statistics. Sources are added by future modules. */
public record PlayerStatistics(
        long mobsDefeated,
        long bossesDefeated,
        long questsCompleted,
        long dimensionsDiscovered,
        long creaturesDiscovered,
        long dungeonsCompleted
) {
    public static final PlayerStatistics EMPTY = new PlayerStatistics(0, 0, 0, 0, 0, 0);

    public static final Codec<PlayerStatistics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("mobs_defeated", 0L).forGetter(PlayerStatistics::mobsDefeated),
            Codec.LONG.optionalFieldOf("bosses_defeated", 0L).forGetter(PlayerStatistics::bossesDefeated),
            Codec.LONG.optionalFieldOf("quests_completed", 0L).forGetter(PlayerStatistics::questsCompleted),
            Codec.LONG.optionalFieldOf("dimensions_discovered", 0L).forGetter(PlayerStatistics::dimensionsDiscovered),
            Codec.LONG.optionalFieldOf("creatures_discovered", 0L).forGetter(PlayerStatistics::creaturesDiscovered),
            Codec.LONG.optionalFieldOf("dungeons_completed", 0L).forGetter(PlayerStatistics::dungeonsCompleted)
    ).apply(instance, PlayerStatistics::new));

    public PlayerStatistics validated() {
        return new PlayerStatistics(
                Math.max(0L, mobsDefeated), Math.max(0L, bossesDefeated),
                Math.max(0L, questsCompleted), Math.max(0L, dimensionsDiscovered),
                Math.max(0L, creaturesDiscovered), Math.max(0L, dungeonsCompleted));
    }
}
