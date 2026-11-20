# fabric-mod

Mod de Minecraft **Fabric** para la versiÃ³n **26.3** (Java 25), desarrollado por dos personas sin pisarse el trabajo.

## Estado

| Dato | Valor |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.2+26.3 |
| Loom | 1.19-SNAPSHOT |
| Java | 25 |
| Mod id | `ejemplo-mod` |

## Puesta en marcha

Necesitas **JDK 25** (Microsoft o Temurin). El wrapper de Gradle ya estÃ¡ incluido.

```powershell
# Windows (PowerShell)
.\gradlew build      # compila y genera el jar en build/libs/
.\gradlew runClient  # lanza Minecraft con el mod
.\gradlew runServer  # lanza servidor de prueba
```

En Linux/macOS usa `./gradlew` en vez de `.\gradlew`.

Importa el proyecto en IntelliJ IDEA abriendo `settings.gradle` como proyecto Gradle.

## ColaboraciÃ³n

Todo el flujo y las reglas estÃ¡n en [CONTRIBUTING.md](CONTRIBUTING.md). Resumen:

1. **Nadie commitea directamente en `main`.** `main` estÃ¡ protegida.
2. Cada cambio va en su propia rama: `nombre/cosa-que-hago`.
3. Se abre un **Pull Request** y se fusiona con *Squash and merge*.
4. Cada persona trabajan en archivos distintos siempre que sea posible; los `CODEOWNERS` fuerzan revisiÃ³n.

## Estructura

```
src/main/java/com/ejemplo/          lÃ³gica comÃºn (servidor + cliente)
src/main/java/com/ejemplo/mixin/    mixins comunes
src/client/java/com/ejemplo/client/ lÃ³gica y mixins SOLO de cliente
src/main/resources/                 fabric.mod.json, mixins, texturas, datos
```