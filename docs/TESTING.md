# Testing

JUnit 5 was added to `build.gradle` to test pure balance and data logic without launching Minecraft.

Run:

```bash
./gradlew build
./gradlew test
```

Covered: experience curve bounds, multi-level rewards, attribute immutability and clamping, derived stats scaling, codec round trip, missing fields, schema migration.

Not covered yet: Minecraft-runtime tests (death, reconnect, multiplayer sync). They require a Minecraft test harness and are a future phase.