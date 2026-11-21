# WILDBOUND — Plan maestro: persistencia RPG, progresión, varita y animaciones

> Documento vivo de implementación. Cada fase debe cerrarse con cambios pequeños, preview del resultado, pruebas y un commit identificable. No integrar en `angeld03/motor-rpg` hasta que el bloque esté revisado y validado.

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
