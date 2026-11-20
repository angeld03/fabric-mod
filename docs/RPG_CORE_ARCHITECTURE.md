# WILDBOUND RPG Core — Arquitectura

Minecraft 26.3 / Fabric Loader 0.20.1 / Java 25 / mod id `wildbound`.

## Principios

1. **El servidor es la autoridad.** El cliente solo recibe copias sincronizadas.
2. **Perfiles inmutables.** Cada servicio produce un `PlayerRpgProfile` nuevo y lo guarda; nunca se edita en sitio.
3. **Una fuente de verdad para las fórmulas.** Solo `DerivedStatsCalculator` calcula estadísticas derivadas.
4. **Sin dependencias circulares.** `api` depende de los servicios; los servicios nunca dependen de `api`.
5. **Sin TODOs críticos.** Si algo no está implementado, no existe; se añade en una fase posterior.

## Módulos

| Paquete | Responsabilidad |
|---|---|
| `core` | Constantes, configuración, inicialización |
| `player` | Perfil, contenedor de atributos, attachment, proveedor |
| `progression` | Curva de XP, niveles, recompensas |
| `attributes` | Atributos y estadísticas derivadas |
| `resources` | Maná y stamina |
| `skills` | Definiciones, registro, desbloqueos |
| `realms` | Definiciones y acesso a reinos |
| `networking` / `persistence` | Códecs, migración, sincronización |
| `commands` | Comandos administrativos |
| `api` | Contrato público para otros módulos |
| `client.hud` | HUD mínimo de verificación |

## Persistencia y sincronización

Se usa **Fabric Data Attachment API**:

```java
AttachmentRegistry.<PlayerRpgProfile>builder()
    .persistent(PlayerRpgProfile.CODEC)
    .copyOnDeath()
    .initializer(PlayerRpgProfile::empty)
    .syncWith(RpgDataCodec.STREAM_CODEC, AttachmentSyncPredicate.all())
    .buildAndRegister(Wildbound.id("rpg_profile"));
```

Esto cubre guardado, muerte, cambio de dimensión, reconexión y sincronización sin paquetes propios.
