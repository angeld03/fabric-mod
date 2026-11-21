# WILDBOUND — Plan maestro: persistencia RPG, progresión, varita y animaciones

> Documento vivo de implementación. Cada fase debe cerrarse con cambios pequeños, preview del resultado, pruebas y un commit identificable. No integrar en `angeld03/motor-rpg` hasta que el bloque esté revisado y validado.


## Registro de ejecución autónoma

### P0.1 — Instrumentación inicial publicada

Cambios añadidos en la rama de trabajo:
- `PlayerRpgInitializer` inspecciona `hasAttached(PROFILE)` antes de llamar al gestor que inicializa el perfil. Registra si llega un attachment, si el UUID corresponde, la versión del esquema y un resumen de progresión.
- En desconexión registra una instantánea del perfil en memoria. El log advierte expresamente que esto no demuestra que el archivo de jugador ya se haya escrito en disco.
- `/wildbound debug attachment` muestra el estado actual del attachment sin llamar a `getOrCreate`, evitando ocultar el caso en que el perfil no estaba inicializado.
- `PersistenceTest` ahora usa perfiles no triviales y comprueba todos los campos principales en el round-trip del Codec, la migración y la validación de datos inválidos/ranuras asociadas a habilidades bloqueadas.

**Cómo ejecutar la comprobación en un mundo de pruebas (solo operador):**
1. Entrar con el mod actualizado y ejecutar `/wildbound debug attachment`.
2. Cambiar datos de prueba, por ejemplo `/wildbound xp add @s 500`, y volver a consultar el attachment.
3. Salir completamente del mundo/servidor y volver a entrar.
4. Ejecutar otra vez `/wildbound debug attachment` y comparar con las líneas `RPG profile join` y `RPG profile disconnect snapshot` del log.

Interpretación:
- `source=missing-or-new` en un jugador que ya tenía progreso indica que el attachment no llegó presente al JOIN; es una pista para investigar registro, lectura del NBT y carga.
- `source=restored` con valores correctos demuestra que el perfil llegó a la sesión; si luego desaparecen cambios, hay que seguir el camino de las escrituras.
- Un UUID incorrecto produce `uuid-mismatch-reinitialized`; no se debe borrar ni sobrescribir manualmente ningún playerdata para probarlo.

**Estado:** instrumentación y tests actualizados en código; no se ha confirmado aún una prueba de reinicio en un mundo real. GitHub Actions ha informado jobs fallidos sin pasos ejecutados ni logs descargables, por lo que la compilación del commit aún no tiene una validación CI interpretable.

### P3.0 — Dirección visual conceptual de la Water Wand

Se ha generado una preview conceptual para orientar el rediseño: bastón de madera oscura tallada, runas luminosas cian, herrajes plata/oro, corona de metal angular y gran cristal acuático facetado con gotas y anillos de agua. La imagen es una referencia artística, **no** un modelo Blockbench ni una textura importable. La geometría y atlas UV se implementarán como un asset cúbico independiente cuando la validación del código vuelva a estar operativa.

## Estado de la auditoría

Rama funcional auditada: `angeld03/motor-rpg` en `8c3b769c87b7852cfe52c990b9f5584f99038632`.

Rama de trabajo de esta PR: `chatgpt/water-bolt-cinematic-idle`, que parte de la rama funcional y contiene una primera mejora de cámara/idle para Water Bolt. PR #3 continúa abierta y sin fusionar.

### Lo que ya existe

- `PlayerRpgProfile` contiene versión de esquema, UUID, nivel, XP actual y total, puntos de atributos y habilidades, cinco atributos, maná, stamina, IDs de habilidades y reinos desbloqueados, cinco ranuras radiales y estadísticas.
- `RpgAttachments.PROFILE` se registra como attachment persistente con `PlayerRpgProfile.CODEC`, copia al morir y sincronización privada al jugador.
- `PlayerRpgInitializer` fuerza la carga/validación del perfil al entrar.
- Experiencia, atributos, desbloqueo de habilidades, reinos, recursos y configuración radial usan normalmente `RpgPersistenceManager.load/save`.
- La progresión de personaje está definida para niveles 1–90: dos puntos de atributo por nivel y un punto de habilidad cada tres niveles, según la configuración.
- El catálogo declara 18 elementos con 10 habilidades por elemento (180 definiciones). La inicialización actual registra los ejecutores de Agua mediante `AquaSkillExecutors.registerAll()`; las demás definiciones no deben confundirse con habilidades que ya tengan una ejecución jugable.
- Los cinco espacios radiales se normalizan y validan en el servidor.

### Diagnóstico de persistencia: pendiente de reproducir

La estructura actual usa el mecanismo oficial de Fabric para attachments persistentes. La clase de attachments también se referencia desde `Wildbound.onInitialize()`, por lo que no hay una omisión evidente de registro en el código revisado.

Todavía no se ha encontrado una causa demostrada para la pérdida que se observa al salir. No se debe sustituir el sistema ni añadir un segundo archivo de guardado antes de comprobar el flujo real de Minecraft.

La prueba `PersistenceTest` actual tiene una limitación importante: su round-trip principal serializa un perfil prácticamente inicial, y no verifica que el attachment de un jugador real se escriba y vuelva a cargarse desde el NBT de su mundo. Por tanto, pasar esa prueba no demuestra todavía que el cierre y reapertura del mundo conserve todo.

### Estado de la varita actual

- Modelo: `assets/wildbound/models/item/wand.json`.
- Geometría: 20 piezas cúbicas, con mango recto, anillos, guarda y cristal superior.
- Materiales: referencias directas a texturas vanilla (`dark_oak_log`, `gold_block`, `beacon`, `sea_lantern`, etc.).
- Existe `textures/item/wand.png`, pero el modelo actual no la utiliza.
- `WandItem` extiende el `Item` normal y define identidad/tooltip. No contiene estados de animación de equipar, apuntar, cargar, lanzar o recuperar.
- La secuencia de Water Bolt se basa principalmente en efectos de mundo, partículas, sonido y cámara; eso no equivale a animar la propia varita en la mano.

### Límites funcionales detectados

- El perfil guarda IDs de habilidades desbloqueadas, pero no existe todavía un progreso individual por habilidad (XP, nivel/rango o maestría).
- Las definiciones de habilidad contienen requisitos de nivel, coste y prerrequisitos; eso es desbloqueo, no subida de nivel de cada habilidad.
- Los cooldowns de ejecución se mantienen en mapas estáticos en memoria. Se reinician al reiniciar el servidor; decidir si deben persistir será una regla explícita, no un efecto accidental.
- Las estadísticas del perfil tienen estructura de datos, pero habrá que verificar e implementar los eventos que las incrementan.

## Prioridades

### P0 — Diagnosticar y reparar la persistencia real

Objetivo: que ningún avance confirmado del personaje desaparezca al salir, morir o reiniciar el mundo.

1. Añadir diagnóstico de servidor: attachment presente, UUID coincidente, versión del esquema, nivel, XP, puntos, cantidad de habilidades/reinos y ranuras radiales.
2. Registrar en el log de entrada si el perfil se ha cargado, migrado o recreado por ausencia/UUID incorrecto. No registrar datos privados innecesarios.
3. Ampliar `PersistenceTest` con perfiles no triviales: nivel alto, XP, puntos, atributos modificados, habilidades, reinos, ranuras y estadísticas.
4. Probar migración desde las versiones de esquema anteriores y fallback seguro ante campos ausentes o datos inválidos.
5. Ejecutar una prueba real en el mundo: modificar valores conocidos, guardar, salir por completo, volver a entrar y comparar. Repetir con muerte y respawn.
6. Comprobar que el mod correcto está cargado en el lado servidor y que el JAR probado corresponde a este commit. Si el attachment no aparece en el NBT del jugador, investigar registro/codec/carga; si aparece pero vuelve a valores iniciales, investigar migración y validación.
7. Corregir la causa reproducida. No añadir guardado duplicado ni forzar escritura manual de playerdata como parche preventivo.

Criterio de cierre: una prueba documentada de logout/restart/rejoin mantiene exactamente los valores de perfil y las pruebas automatizadas pasan.

### P1 — Datos de progresión individual de habilidades

Ampliar el perfil mediante una migración de esquema (sin borrar perfiles existentes).

Cada habilidad desbloqueada podrá almacenar:
- nivel o rango individual;
- experiencia de habilidad y experiencia necesaria para el siguiente rango;
- maestría/proficiencia;
- mejoras desbloqueadas en hitos concretos;
- fecha/versión de datos solo cuando sea necesaria para migraciones.

Reglas propuestas:
- El nivel del personaje y los puntos siguen controlando el desbloqueo inicial.
- La experiencia individual se concede por usos válidos y efectivos, no por mantener pulsado ni por enviar paquetes repetidos.
- Cada habilidad tendrá una curva configurable y un límite de rango.
- La mejora debe afectar a parámetros definidos de la habilidad (coste, control, duración, daño o utilidad), con límites de balance explícitos.
- Servidor autoritativo: el cliente solo solicita acciones y muestra el progreso sincronizado.
- Los cambios de perfil pasan siempre por un único servicio de actualización y el attachment inmutable.

### P2 — Completar el núcleo RPG del personaje

- Unificar y auditar las fuentes de XP (combate, exploración, objetivos, misiones y jefes).
- Revisar curva de XP, nivel máximo, recompensas y límites para que no haya desbordamientos ni saltos de nivel incorrectos.
- Revisar atributos y estadísticas derivadas; asegurar que invertir puntos siempre actualice vida, daño, regeneración y recursos de forma coherente.
- Añadir respec/reasignación controlada si forma parte del diseño.
- Completar progreso de reinos, misiones, descubrimientos y estadísticas con eventos concretos.
- Decidir explícitamente qué datos son permanentes y cuáles son temporales (carga de hechizo, cooldowns, efectos activos, etc.).
- Mantener los cinco espacios radiales, pero validar cada cambio en el servidor y conservarlo tras reiniciar.

### P3 — Rediseño visual de la Water Wand

Dirección artística propuesta: reliquia de un mago de mareas, reconocible como arma mágica y no como un palo con una gema.

- Silueta más elegante y asimétrica: mango de madera oscura tallado, agarres y runas, metal trabajado y una estructura que sujeta el cristal.
- Cristal de agua azul/cian con núcleo luminoso y facetas legibles desde primera y tercera persona.
- Detalles de metal, filigranas y pequeñas gotas suspendidas integradas en el diseño, sin geometría innecesaria.
- Textura propia con atlas UV coherente; eliminar las referencias a bloques vanilla como material principal.
- Variantes visuales por estado de carga si el formato/modelo final lo permite sin duplicar assets innecesariamente.
- Revisar escala, orientación y posición en primera persona, tercera persona, inventario, GUI y suelo.

### P4 — Sistema de animación de la varita y hechizos

Implementar una máquina de estados cliente con datos de activación controlados por el servidor:

1. **Idle:** flotación/balanceo mínimo, núcleo con pulso lento y pequeñas gotas orbitando.
2. **Equipar:** entrada breve de la varita a la pose de combate, con transición suave.
3. **Apuntar:** seguimiento amortiguado de la cámara, sin temblores ni retraso excesivo.
4. **Carga:** elevación y giro de la varita, cristal que aumenta su intensidad, anillos de agua que se cierran y partículas que convergen.
5. **Carga completa:** pulso claramente legible y señal sonora/visual sin saturar la pantalla.
6. **Lanzamiento:** retroceso breve de la varita, explosión acuática, estela del proyectil y golpe de cámara sincronizado.
7. **Recuperación:** retorno con easing, caída de gotas y vuelta al idle.

También se debe contemplar la animación en tercera persona, la cancelación por cambio de objeto/menú/desconexión, latencia de red y límites de partículas. Las animaciones cosméticas no deben decidir daño, costes ni cooldowns.

### P5 — Interfaz RPG y feedback

- Ficha de personaje con nivel, barra de XP, puntos disponibles y estadísticas.
- Árbol de habilidades por elemento, con requisitos, desbloqueo, rango individual, XP y mejoras.
- Preview/tooltip de cada habilidad: coste, cooldown, efecto y siguiente mejora.
- Menú radial con cinco slots, estados de carga y feedback de cooldown.
- Feedback de subida de nivel y desbloqueo: notificación, sonido, animación y resumen de recompensas.
- Sincronización fiable: el cliente nunca presenta como guardado un cambio que el servidor haya rechazado.

### P6 — Verificación, balance y entrega

- Tests unitarios de codecs, migración, curvas, recompensas, desbloqueos y mejoras.
- CI con `./gradlew build` en Java 25.
- Prueba de mundo real: entrar, progresar, salir, reiniciar, volver, morir y comprobar el perfil.
- Prueba multijugador: dos jugadores no comparten perfiles y la progresión no depende del cliente.
- Prueba visual: varita en mano, inventario, GUI, tercera persona y secuencia completa Water Bolt.
- Revisar consumo de CPU/partículas y evitar que el sistema de animación guarde datos cosméticos cada tick.

## Formato de trabajo y preview por acción

Cada acción se entregará como un bloque independiente:

1. **Objetivo y alcance** — qué problema resuelve y qué no toca.
2. **Preview previa** — esquema, diseño o comportamiento esperado antes de alterar archivos sensibles.
3. **Implementación** — cambios limitados a los archivos necesarios en la rama de trabajo.
4. **Preview final** — resumen del diff, archivos modificados y resultado visual/código disponible.
5. **Validación** — resultado real de tests/Gradle/CI; lo que no se haya podido ejecutar se marcará como pendiente.
6. **Commit** — hash corto y enlace al diff/PR.

No se fusionará automáticamente a `angeld03/motor-rpg`. Una fase no se dará por terminada solo porque se haya subido el código: deberá tener pruebas y una validación visual/jugable cuando corresponda.


### P1 — Primera implementación de maestría publicada

Se añadió una primera capa persistente de progresión por habilidad:
- Nuevo `SkillProgress`: rango 1–10, XP actual, XP total, maestría 0–100 y lista de mejoras desbloqueadas.
- El perfil RPG sube a esquema 3 y añade el mapa `skill_progress`.
- La migración inicializa con rango 1 las habilidades que ya estaban desbloqueadas, sin retirar habilidades ni cambiar los puntos del jugador.
- Cada activación que completa correctamente su ejecutor en el servidor concede 10 XP de maestría; una activación cargada puede conceder hasta 15. Fallos por cooldown, recursos o excepción no conceden XP.
- El listado administrativo de habilidades muestra rango y XP.
- Water Bolt recibe una mejora inicial controlada: hasta 22.5% de daño adicional al rango 10 y reducción de coste de maná de 8 a un mínimo de 5.

**Pruebas añadidas:** round-trip del codec de maestría, migración de perfiles antiguos, subida de rango y límite máximo.

**Balance provisional:** la curva usa 100 XP por rango actual (100 para R1→R2, 200 para R2→R3, etc.). Es una primera curva funcional para validar; el balance definitivo se ajustará en P2. La maestría es un valor de progresión informativo por ahora; los efectos directos están limitados a Water Bolt para no inventar comportamiento en 179 definiciones que aún no tienen ejecutor jugable.

**Pendiente de validación:** CI sigue marcando fallos con jobs sin pasos ni logs disponibles. No se declara compilado ni probado dentro de Minecraft. El esquema 3 debe validarse con perfiles reales de versiones anteriores antes de distribuir el JAR.


### P2 — Auditoría y primera corrección de progresión

Hallazgo importante durante la integración de P1: varios servicios reconstruían el perfil con el constructor antiguo al conceder XP, gastar puntos de atributo, regenerar recursos o usar comandos administrativos. Al añadir el mapa de maestría, esas reconstrucciones habrían eliminado silenciosamente el progreso de habilidades. Se corrigieron las copias en `ExperienceService`, `AttributeService`, `ResourceService` y `RpgCommands` para conservar `skillProgress`.

Además:
- Se corrigió el cálculo del XP concedido para evitar overflow de `long` al alcanzar el máximo.
- El XP total acumulado ahora satura en `Long.MAX_VALUE` en lugar de desbordarse.
- La curva de personaje tenía un tope de 5.000.000 XP por nivel, que provocaba que se aplanara antes del nivel 70 y dejara de escalar durante los últimos niveles. El límite de seguridad se elevó a 50.000.000 y se añadió una prueba para comprobar que la curva sigue creciendo hasta el nivel 89.
- Se ampliaron pruebas de recompensas acumuladas por subir varios niveles y de hitos de puntos de habilidad.

**Pendiente:** revisar todos los eventos que conceden XP/actualizan estadísticas y ejecutar tests/build con Java 25. No se afirma todavía que P2 esté cerrado ni que el balance esté aprobado.


### P3 — Primera revisión de la geometría de la Water Wand

Se amplió el modelo de 20 a 33 elementos cúbicos:
- Tres collares de hierro para separar visualmente el mango.
- Incrustaciones y runas cian en las caras del mango.
- Refuerzos metálicos en la corona del cristal.
- Gotas de agua flotantes y un núcleo frontal.

Se reutilizan texturas vanilla existentes (hierro, diamante, prismarine, oro, beacon y sea lantern). **No es todavía la versión final con atlas UV artesanal**; la textura propia del proyecto no se ha podido validar visualmente y no se ha sustituido por una imagen inventada. Pendiente comprobar orientación/escala en mano, GUI, suelo y tercera persona.

### P4 — Primera animación de carga de la varita

- `SkillChargeState` expone el progreso 0..1 de carga.
- Nueva `WaterBoltChargePresentation` genera un vórtice de burbujas que se contrae hacia el cristal, salpicaduras y chispas que aparecen en fases altas de carga.
- El efecto solo existe en el cliente, primera persona, con Water Wand y Water Bolt seleccionado; no modifica daño, costes ni duración del servidor.
- Se integra con el ciclo de conexión/desconexión y se limpia al salir.

Esta fase es feedback de carga con partículas/cámara; todavía no anima los huesos o cubos del modelo de la varita. La animación real del objeto en mano requiere un renderer/model pipeline compatible y una prueba en Minecraft.

### P5 — Primera ampliación de la interfaz RPG

- La cabecera del árbol de habilidades muestra puntos de habilidad y puntos de atributo disponibles.
- Al enfocar una habilidad desbloqueada, el pie del menú muestra su rango individual y XP para el siguiente rango (o MAX).
- El HUD ya existente continúa mostrando nivel, XP, salud, maná y stamina.

P5 ya cuenta con una primera ficha de personaje, inversión de atributos validada por servidor y preview de estadísticas derivadas. Siguen pendientes una vista detallada de mejoras por habilidad, feedback visual de subida de nivel/desbloqueos y validación visual/jugable en Minecraft.


### P1/P2 — Corrección adicional de sincronización y conservación

- El codec binario de sincronización del attachment (`RpgDataCodec`) no incluía el mapa nuevo de maestría. Se amplió para serializar/deserializar rangos, XP, maestría y mejoras, con límites defensivos en cantidades de entradas.
- Se revisaron y actualizaron los constructores de perfil en XP, atributos, regeneración/consumo de recursos y comandos administrativos para conservar el mapa de progreso individual. Sin esta corrección, esas operaciones habrían reiniciado el progreso de habilidad al reconstruir el perfil.

### P5 — Ficha del personaje y asignación de atributos

Se ha añadido una primera pantalla de personaje, accesible con **J**:
- Muestra nivel, XP actual y total, puntos de atributo.
- Lista los cinco atributos y su valor.
- Muestra estadísticas derivadas (vida, maná, stamina, poder, defensas, crítico, movilidad y regeneración).
- El botón `+` solicita gastar un punto de atributo; el servidor valida el límite y los puntos disponibles mediante `AttributeService`.
- Incluye etiqueta del control en inglés y español.
- La pantalla de habilidades permite abrir la ficha con J y muestra rango/XP de la habilidad seleccionada.

**Pendiente:** no se ha añadido todavía una pantalla de respec; la asignación individual es de un punto por clic. La respuesta de aceptación llega por el mensaje del servidor y el attachment sincronizado actualiza la pantalla. Validar visualmente el layout en resoluciones pequeñas y grandes.


### P5 — Pulido de interfaz (continuación)

- Se han traducido al inglés y español los textos de la ficha de personaje: atributos, estadísticas derivadas, XP, puntos disponibles y límites.
- El estado de rango/XP de la habilidad enfocada y los puntos de atributo del encabezado del árbol de habilidades ya usan claves de idioma, no texto fijo.
- La ficha adapta su composición a ventanas estrechas: atributos arriba y estadísticas derivadas en dos columnas debajo; en ventanas amplias mantiene la distribución en dos paneles.
- Se corrigió el ciclo de vida de los efectos de carga Water Bolt: se elimina una llamada duplicada al reset en JOIN y se resetea también al desconectar.

**Validación pendiente:** estos cambios son una revisión de código y de recursos JSON; no se han podido compilar ni ver dentro del cliente Minecraft. La composición compacta requiere comprobación a distintas resoluciones.


### P5 — Vista de maestría por habilidad

- Nueva pantalla de solo lectura `SkillMasteryScreen`, accesible desde el árbol de habilidades al pulsar **M** con una habilidad desbloqueada enfocada.
- Muestra rango actual/máximo, XP del rango, barra de progreso, XP total, porcentaje de dominio y número/lista de mejoras registradas.
- La pantalla usa el perfil sincronizado del jugador; no concede XP ni mejoras desde el cliente.
- Textos localizados en español e inglés.

**Límite funcional:** el sistema de datos ya almacena IDs de mejoras, pero todavía no existe un catálogo completo de mejoras desbloqueables para cada habilidad. La pantalla muestra las mejoras registradas y explica cuando aún no hay ninguna. No se han inventado upgrades que no estén implementados.


### P5 — Rediseño visual de la pantalla de maestría

- Se ha sustituido la tarjeta plana por un panel de alto contraste con marco doble, acentos cian/oro y fondo con aurora azul-violeta.
- Se añadieron animaciones de entrada, barrido luminoso superior, progreso de XP animado, brillo móvil, pulsación de métricas, partículas ambientales y destellos de borde.
- El rango tiene emblema de cristal animado; XP total, dominio y mejoras se presentan en tarjetas separadas con etiquetas compactas y color propio.
- El subtítulo y las etiquetas de métricas ya están localizados en español e inglés.
- Se mantienen los datos reales del attachment del jugador; el diseño no inventa mejoras ni altera el progreso del servidor.

**Validación:** los dos JSON de idioma se han parseado correctamente. GitHub Actions ha terminado en fallo, pero el job no expone pasos ni logs descargables (el endpoint de logs devuelve BlobNotFound); por tanto no se declara compilación confirmada ni prueba visual dentro de Minecraft.


### P5 — Feedback de subida de nivel en el HUD

- Se añadió un anuncio central temporal al detectar un aumento de nivel en el perfil sincronizado.
- La placa muestra la transición de nivel anterior al nuevo, con una cabecera luminosa, aparición/desvanecimiento, brillo pulsante y destellos laterales.
- El anuncio incluye los puntos de atributo y habilidad ganados en el mismo cambio de perfil.
- Se ha localizado en español e inglés. No concede recompensas desde el cliente; solo representa los valores ya sincronizados.
- El HUD conserva su halo/pulso del medallón y el brillo de XP durante la subida.

**Validación pendiente:** revisar la aparición real en cliente, especialmente cuando se ganan varios niveles en una sola concesión de XP y en resoluciones bajas.


### P5 — Anuncio cinematográfico de subida de nivel

- El HUD detecta el cambio de nivel en el attachment sincronizado y muestra un banner temporal en la zona superior-central.
- La animación combina entrada/desvanecimiento, transición del número de nivel anterior al nuevo, gradiente dorado-cian, halo pulsante y destellos laterales.
- Muestra los puntos de atributo y habilidad ganados en la concesión de XP.
- El texto está localizado en español e inglés y usa únicamente las recompensas ya guardadas por el servidor.
- Se conserva el pulso del medallón de nivel y el efecto de ganancia de XP de la placa superior izquierda.

**Pendiente de prueba:** verificar en Minecraft una subida normal, varias subidas de nivel en una única concesión y resoluciones bajas. No se ha confirmado todavía una compilación Gradle de esta revisión.


### P1 — Water Bolt: mejoras reales de maestría

Se ha conectado el progreso individual a tres hitos permanentes del Water Bolt:
- **Rango 3 — Núcleo hidrostático:** +10% de daño adicional, acumulable con el escalado normal por rango.
- **Rango 6 — Corriente glacial:** los impactos directos aplican Lentitud durante 60 ticks y generan partículas de escarcha.
- **Rango 9 — Perforador de mareas:** +18% de alcance a las tres lanzas del hechizo.

Los hitos se registran en `SkillProgress.upgrades` desde el servicio del servidor, una sola vez, y se conservan en el perfil/codec existente. La pantalla de maestría presenta ahora nombres localizados en español e inglés en lugar de IDs técnicos. No se ha añadido una interfaz de elección todavía: estos tres hitos se desbloquean automáticamente al alcanzar sus rangos.

**Validación pendiente:** no se ha ejecutado Gradle ni una sesión real de Minecraft. Revisar el efecto de Lentitud en jefes/inmunidades y el balance del daño/alcance en combate.


### P4 — Corrección de colisión del Water Bolt

Durante la ampliación del hechizo se detectó que una entidad podía permanecer dentro del volumen de colisión durante varios ticks del mismo proyectil y recibir daño repetido en cada tick. Ahora cada una de las tres lanzas mantiene un conjunto de UUID alcanzados y aplica el impacto directo una sola vez por objetivo y por lanza. Las tres lanzas todavía pueden alcanzar al mismo objetivo por separado, como parte del diseño del ataque.

**Validación pendiente:** comprobar en juego el daño por objetivo, el solapamiento entre lanzas y el impacto de área de la detonación.
