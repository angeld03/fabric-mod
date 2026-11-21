package com.wildbound.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Persistent mastery track for one unlocked skill.
 *
 * Experience is server-authoritative. Rank is capped and validated here so
 * malformed player data cannot grant unbounded skill scaling.
 */
public record SkillProgress(
        int rank,
        long experience,
        long totalExperience,
        float mastery,
        List<String> upgrades
) {
    public static final int MAX_RANK = 10;
    public static final SkillProgress INITIAL = new SkillProgress(1, 0L, 0L, 0f, List.of());

    public static final Codec<SkillProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rank", 1).forGetter(SkillProgress::rank),
            Codec.LONG.optionalFieldOf("experience", 0L).forGetter(SkillProgress::experience),
            Codec.LONG.optionalFieldOf("total_experience", 0L).forGetter(SkillProgress::totalExperience),
            Codec.FLOAT.optionalFieldOf("mastery", 0f).forGetter(SkillProgress::mastery),
            Codec.list(Codec.STRING).optionalFieldOf("upgrades", List.of()).forGetter(SkillProgress::upgrades)
    ).apply(instance, SkillProgress::new));

    public SkillProgress {
        rank = Math.max(1, Math.min(MAX_RANK, rank));
        long maxCurrentExperience = rank >= MAX_RANK ? 0L : 100L * rank - 1L;
        experience = Math.max(0L, Math.min(experience, maxCurrentExperience));
        totalExperience = Math.max(experience, totalExperience);
        mastery = Float.isFinite(mastery) ? Math.max(0f, Math.min(100f, mastery)) : 0f;
        java.util.LinkedHashSet<String> safeUpgrades = new java.util.LinkedHashSet<>();
        if (upgrades != null) {
            for (String upgrade : upgrades) {
                if (upgrade == null || upgrade.isBlank() || upgrade.length() > 64) continue;
                safeUpgrades.add(upgrade);
                if (safeUpgrades.size() >= 64) break;
            }
        }
        upgrades = List.copyOf(safeUpgrades);
    }

    public long experienceRequired() {
        return rank >= MAX_RANK ? 0L : 100L * rank;
    }

    public SkillProgress addExperience(long amount) {
        if (amount <= 0 || rank >= MAX_RANK) return this;
        long remaining = amount;
        long xp = experience;
        long total = amount > Long.MAX_VALUE - totalExperience
                ? Long.MAX_VALUE : totalExperience + amount;
        int nextRank = rank;

        while (remaining > 0 && nextRank < MAX_RANK) {
            long needed = Math.max(0L, 100L * nextRank - xp);
            if (remaining < needed) {
                xp += remaining;
                remaining = 0;
            } else {
                remaining -= needed;
                nextRank++;
                xp = 0L;
            }
        }
        if (nextRank >= MAX_RANK) xp = 0L;
        return new SkillProgress(nextRank, xp, total, Math.min(100f, mastery + amount * 0.05f), upgrades);
    }

    public SkillProgress withUpgrade(String upgradeId) {
        if (upgradeId == null || upgradeId.isBlank() || upgrades.contains(upgradeId)) return this;
        java.util.ArrayList<String> next = new java.util.ArrayList<>(upgrades);
        next.add(upgradeId);
        return new SkillProgress(rank, experience, totalExperience, mastery, next);
    }
}
