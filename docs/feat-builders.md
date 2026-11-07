# feat/builders — constructores animados (`dev.t3craft.build`)

Constructores con skin de jugador que caminan hasta una zona, levantan una estructura por capas a partir de
una lista de colocaciones de bloques, y se van al terminar. Funciona en singleplayer (servidor integrado).

## Qué hay

### API común (`mod/src/main/java/dev/t3craft/build/`)
| Clase | Rol |
|---|---|
| `Placement` | `(BlockPos relativo, BlockState, @Nullable CompoundTag blockEntity)`. Aire = "limpiar este hueco". |
| `BuildPlan` | Lista ordenada e inmutable de `Placement`. `builder()` con `set`/`fill` (escribir dos veces la misma posición deja la última y la mueve al final: "rellenar pared, luego abrir puerta"). `layered()` = copia ordenada por capas. `min()`/`max()` relativos. Convención: frente al norte (`-z`), crece hacia `+z`, `x = 0` en el eje. |
| `Layers` | Orden por capas sobre ints puros: `y` ascendente, filas por `z`, serpenteando en `x`; estable. |
| `BuildCursor` | Núcleo puro: presupuesto de N bloques por tick (admite fraccionario: `0.5` = uno cada 2 ticks), pausa/reanudar/cancelar, progreso, contadores placed/skipped/failed. El crédito no se acumula en pausa (sin ráfagas). Bloques ya iguales = `SKIPPED`, gratis pero acotados por tick. |
| `BuildJob` | Plan + dimensión + origen + `Rotation` + cursor. Rota posiciones y estados (puertas/antorchas giran con el plan). Controles y progreso thread-safe. |
| `BlockSink` / `ServerLevelSink` | Dónde se colocan los bloques. El real: `setBlock` en `ServerLevel` con `UPDATE_CLIENTS \| UPDATE_KNOWN_SHAPE` (puertas/antorchas no se caen mientras se construye), carga NBT de block entity (`loadWithComponents` vía `TagValueInput`), sonido de colocación del `SoundType` y partículas `BLOCK` (al limpiar con aire: partículas+sonido de rotura). Los efectos salen del servidor, así que se ven también en LAN. |
| `BuildManager` | Corre jobs en el hilo del servidor (`tick(server)`), `submit` desde cualquier hilo, `BuildListener` (started/placed/finished), `clear()`. Instancia, no singleton. |
| `BuildHandle` | Una construcción con su cuadrilla: fases `ARRIVING → BUILDING → LEAVING → DONE`, `pause/resume/cancel/progress` válidos incluso antes de que exista el job. |
| `BuildService` / `Builders` | Punto de entrada para otras partes del mod: `Builders.service().build(label, dim, origin, rotation, plan, blocksPerTick, crewSize)`. |
| `DemoPlans` | `shed()`: caseta 7x7 (suelo de piedra, esquinas de tronco, paredes de tablones, ventanas, puerta, cofre con NBT, mesa de crafteo, antorcha, techo de losas con alero; interior despejado con aire). `rotationFacingViewer(Direction)`. |

### Cliente (`mod/src/client/java/dev/t3craft/build/client/`)
- `BuilderAvatar`: `RemotePlayer` solo-cliente renderizado por el renderer vanilla de jugador (mismo enfoque que
  `client.npc.ThreadAvatar`: sin entity type registrado, sin tráfico de red), skin por defecto determinista, nombre
  "Builder N". Se mueve dentro de `tick()` (después de que el nivel guarde la posición vieja) para que haya
  interpolación suave y animación de caminar vanilla; brazo con `swing(MAIN_HAND)`, bloque en la mano.
- `BuildCrew` (implementa `BuildHandle`): aparecen a ~14 bloques del lado del jugador con un "poof", caminan al
  perímetro; al llegar (o a los 10 s) se hace `submit` del job. Cada bloque colocado se asigna en round-robin a un
  constructor, que va a un punto justo fuera de la huella (elevado a `y-2` del bloque cuando la obra sube, como en
  andamio), mira el bloque, lo sostiene y mueve el brazo. Al terminar/cancelar se alejan ~18 bloques y desaparecen.
- `BuildersClient`: entrypoint `client` propio (en `fabric.mod.json`), `BuildManager` tickeado en
  `ServerTickEvents.END_SERVER_TICK` del servidor integrado, eventos servidor→cliente por cola concurrente,
  limpieza en cambio de mundo, `SERVER_STOPPING` y `CLIENT_STOPPING`.
- `BuildCommands`: se fusiona (Brigadier) bajo el `/t3craft` existente sin tocar `T3CraftCommands`.

### Comandos
- `/t3craft debug build-demo [blocksPerTick] [crew]` — caseta 3 bloques delante del jugador, puerta hacia él.
  Por defecto 0.5 bloques/tick y 3 constructores (~300 entradas, de ellas 75 de aire que se saltan si ya está despejado; ~25-30 s + caminata).
- `/t3craft debug build-pause | build-resume | build-cancel` — todas las construcciones activas.
- `/t3craft debug build-status` — fase, estado del job y progreso.

## Hot-swap / estado
- Sin estado estático salvo `Builders.service` (tipado a la interfaz `BuildService`, se pone en init y se limpia en
  `CLIENT_STOPPING`). Todo lo demás vive en la instancia del entrypoint.
- `BuildManager.clear()` en arranque y parada del servidor; cuadrillas descartadas al cambiar de nivel.
- Ids de entidad cliente propios desde `-2_000_000` hacia abajo (los NPC de hilos usan `-1_000_000`).

## Puntos de integración
1. **`fabric.mod.json`**: añadida una entrada `client` (`dev.t3craft.build.client.BuildersClient`). Única línea tocada
   fuera del paquete junto con el `.gitignore`.
2. **`.gitignore`**: el patrón `build/` ignoraba también el paquete fuente `dev/t3craft/build`; añadido
   `!mod/src/**/build/` (sigue ignorando `mod/build/`). Si otra rama tiene paquetes llamados `build`, le afecta igual.
3. **`Builders.service()`** para disparar construcciones desde otras features (p. ej. "un hilo terminó → casa").
   Llamar en el hilo cliente; lanza `IllegalStateException` en multijugador.
4. **`BlockSink`** para otro modo de colocación (preview/dry-run, o un mod de servidor por red en multijugador).
5. **`BuildListener`** en `BuildManager` para otras reacciones a eventos de obra (callbacks en hilo servidor).
6. **`ThreadAvatar`** no se reutiliza directamente: es `final`, su `update(NpcInfo)` es package-private y el nombre/skin
   salen de `NpcInfo`. Si se abre (no final, o un constructor con nombre/skin), `BuilderAvatar` puede pasar a
   extenderlo o los NPC de hilos pueden usar `BuilderAvatar` para caminar.
7. `/t3craft debug` lo puede registrar otra rama también: Brigadier fusiona los nodos, no hay conflicto.

## Verificación
- `./gradlew build` en `mod/`: verde. Tests JUnit nuevos: `BuildCursorTest` (9) y `LayersTest` (4).
- No se lanzó Minecraft (regla del VPS): la parte visual (caminar, brazo, partículas, sonido, NBT del cofre) está
  compilada contra la API de 26.3 pero **sin probar en juego**.

## Pendientes
- Probar en juego: `/t3craft debug build-demo`, luego `build-pause`/`build-resume`/`build-cancel` y abrir el cofre.
- Multijugador: hoy requiere servidor integrado; para servidores dedicados haría falta un lado servidor
  (`BlockSink` + mensajes de red) o comandos vanilla.
- Los constructores no tienen pathfinding (van en línea recta, flotan sobre obstáculos con `noPhysics`) y la colocación
  no espera a que el constructor llegue (con presupuestos altos la animación va por detrás).
- Sin deshacer/rollback ni persistencia de jobs en el guardado (un job se cancela al salir del mundo).
- Bloques con dependencias complejas (redstone, camas, plantas) se colocan sin updates de vecinos; puede hacer falta
  un pase final con `UPDATE_ALL`.
- No se usó código de AgentCraft (no hace falta atribución).
