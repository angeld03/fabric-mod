# Integration guide

- **New creature reward:** call `WildboundApi.grantExperience(player, amount, ExperienceSource.MOB_KILL)`.
- **New skill:** create a `SkillDefinition` and `SkillRegistry.register(definition)`. Unlocks go through `WildboundApi.unlockSkill`.
- **New realm:** create a `RealmDefinition` and `RealmRegistry.register(definition)`.
- **New attribute:** extend `RpgAttribute`, `AttributeContainer`, its codec and `DerivedStatsCalculator`.
- **New item or equipment:** read `WildboundApi.getDerivedStats(player)`; never recompute formulas.
- **New quest reward:** use `ExperienceSource.QUEST` and the public API.
- **Worlds and portals:** use `RealmProgressionService.canAccess` before teleporting; the actual dimensions are not implemented yet.