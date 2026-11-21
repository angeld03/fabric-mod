# Progression

Level range: 1-90.

`ExperienceCurve.required(level)` grows progressively and is bounded. `ExperienceService.grantExperience(player, amount, source)` is the only entry point: it validates the amount, handles multiple level ups in a single grant, applies every reward, fires `LEVEL_UP` / `EXPERIENCE_GAINED` events and returns a `LevelUpResult`.

Rewards: 2 attribute points per level, 1 skill point every 3rd level.

At level 90 no further levels or points are granted; XP is kept but capped.

Balance lives in `WildboundConfig`.