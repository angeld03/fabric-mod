package com.wildbound.skills;

import com.wildbound.persistence.RpgPersistenceManager;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.RadialSkillConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-authoritative validation and persistence for radial slots. */
public final class SkillRadialService {
    private SkillRadialService() {
    }

    public static UpdateResult update(ServerPlayer player, List<String> requestedSlots) {
        List<String> requested = RadialSkillConfig.copySlots(requestedSlots);
        PlayerRpgProfile profile = RpgPersistenceManager.load(player);

        Set<String> seen = new HashSet<>();
        for (String skillId : requested) {
            if (skillId.isEmpty()) continue;
            if (!SkillRegistry.contains(skillId)) {
                return UpdateResult.fail("unknown_skill", profile.radialSkills());
            }
            if (!profile.hasSkill(skillId)) {
                return UpdateResult.fail("skill_not_unlocked", profile.radialSkills());
            }
            if (!seen.add(skillId)) {
                return UpdateResult.fail("duplicate_skill", profile.radialSkills());
            }
        }

        PlayerRpgProfile updated = profile.withRadialSkills(requested);
        RpgPersistenceManager.save(player, updated);
        return UpdateResult.success(updated.radialSkills());
    }

    public record UpdateResult(boolean success, String reason, List<String> slots) {
        public static UpdateResult success(List<String> slots) {
            return new UpdateResult(true, RadialSkillConfig.EMPTY, RadialSkillConfig.copySlots(slots));
        }

        public static UpdateResult fail(String reason, List<String> slots) {
            return new UpdateResult(false, reason, RadialSkillConfig.copySlots(slots));
        }
    }
}
