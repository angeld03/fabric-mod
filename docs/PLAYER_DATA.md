# Player Data

Each player has one `PlayerRpgProfile` attached through Fabric Data Attachments.

Fields: schema version, UUID, level, current XP, total XP, attribute points, skill points, five attributes, mana, stamina, unlocked skill ids, unlocked realm ids and `PlayerStatistics`.

Persistence is automatic: the attachment is `persistent()` and `copyOnDeath()`. `RpgDataMigration` validates and upgrades the schema on every load. Old files with missing fields get safe defaults through `optionalFieldOf`.

Access from the server:

```java
PlayerRpgProfile profile = PlayerRpgProvider.getOrCreate(player);
PlayerRpgProvider.set(player, updatedProfile);
```

The client receives the same profile through `syncWith(...)` and reads it with `player.getAttached(RpgAttachments.PROFILE)`.