package com.wildbound.skills;

/** Outcome of a skill unlock attempt. */
public record SkillUnlockResult(boolean success, String reason) {
    public static final SkillUnlockResult OK = new SkillUnlockResult(true, "ok");

    public static SkillUnlockResult fail(String reason) {
        return new SkillUnlockResult(false, reason);
    }
}
