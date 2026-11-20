# RPG API

Other modules must use `com.wildbound.api.WildboundApi` only.

```java
WildboundApi.grantExperience(player, 250L, ExperienceSource.MOB_KILL);
int level = WildboundApi.getPlayerLevel(player);
boolean paid = WildboundApi.consumeMana(player, 15f);
WildboundApi.unlockRealm(player, WildboundConstants.REALM_FROSTFALL);
DerivedStats stats = WildboundApi.getDerivedStats(player);
```

Callbacks are in `RpgProgressionEvents`: `LEVEL_UP` and `EXPERIENCE_GAINED`.

Never access `PlayerRpgProvider` or internal services from another module.