# feat/blocks-core: bloques propios (monitor, lámpara, bombilla del tablero)

Paquetes: `mod/src/main/java/dev/t3craft/blocks/` (registro, bloques, block entities, adaptador del HQ,
aplicador de lámparas) y `mod/src/client/java/dev/t3craft/client/blocks/` (renderer, driver, click,
comandos). Tests: `mod/src/test/java/dev/t3craft/blocks/` y `.../client/blocks/` (30 tests JUnit).

Decisión de Facundo: bloques propios con block entities, como AgentCraft. Respeta
`docs/research/compat.md`: todo vive en un módulo que se puede **apagar entero**; apagado, el mod
queda exactamente como sin él.

## Qué hay

| Bloque | Block entity | Qué hace |
|---|---|---|
| `t3craft:monitor` | `t3craft:monitor` (`MonitorBlockEntity`) | Bloque entero con `facing`. La cara frontal es una pantalla: título, estado con color y antigüedad, último mensaje. Click → abre el chat del hilo. |
| `t3craft:status_lamp` | `t3craft:status_lamp` (`LampBlockEntity`) | Lámpara del escritorio: color y luz según el estado del hilo. |
| `t3craft:board_lamp` | el mismo tipo `t3craft:status_lamp` | Variante "bombilla del tablero": la i-ésima bombilla muestra el i-ésimo hilo que necesita al jugador (errores primero, luego "needs you"). |

Ítems de bloque con el mismo id, en la pestaña propia **T3Craft** (`dev.t3craft.T3CraftTab`, única
del mod; el módulo aporta sus ítems con `T3CraftTab.contribute` solo si está prendido). Loot tables: cada bloque se suelta a sí mismo. Recetas
(razonables y baratas): monitor = 7 hierro + panel de vidrio + redstone; lámpara = 4 cobre (en cruz) +
glowstone + redstone; lámpara ⇄ bombilla, 1:1 sin forma. Modelos y texturas propios, sencillos
(`textures/block/monitor_*`, `status_lamp_<estado>`, `board_lamp_<estado>`).

### Estados de la lámpara (`LampStatus`, propiedad `status`)

| Estado del hilo (`StationStatus`) | `status` | Color | Luz |
|---|---|---|---|
| `working` | `working` | `#55FFFF` | 11 |
| `needs_attention` | `attention` | `#FFAA00` | 14 |
| `error` | `error` | `#FF5555` | 12 |
| `done` | `done` | `#55FF55` | 9 |
| `idle` (y desconocido) | `idle` | `#AAAAAA` | 4 |
| archivado (settled), sin vincular, hilo desconocido, bombilla sin hilo | `off` | `#2A2A2A` | 0 |

Los colores son los de `ThreadState`/`StationStatus` (lo comprueba un test): lámpara, pantalla,
nametag y HUD coinciden.

## El interruptor (modo "solo GUI")

Un único punto de registro: **`T3Blocks.init(BlocksSwitch, BlockRegistrar)`**, llamado una vez desde
su propio entrypoint `main` (`dev.t3craft.blocks.BlocksInit`). Es el único código del módulo que
registra bloques, ítems o tipos de block entity, y todo pasa por la interfaz `BlockRegistrar`.

`BlocksSwitch` decide, de más fuerte a más débil:

1. `-Dt3craft.blocks=false|true` (modpacks; el futuro modo "solo GUI" del cliente puede fijarlo);
2. `config/t3craft.json` → `"blocks": { "enabled": false }` (clave aditiva; si falta o el tipo es
   incorrecto, vale `true`);
3. por defecto: encendido.

Los registros se congelan al arrancar, así que cambiarlo exige **RESTART**. No hay `WorldCapabilities`
en main todavía; cuando exista, puede calcular este mismo switch (o fijar la propiedad) sin tocar el
módulo.

**Apagado** (lo comprueban tests): no se registra nada (`BlockRegistrar` no recibe ni una llamada),
`T3Blocks.active()` es `false`, `HqFixtures.active()` vuelve a `VANILLA` (el HQ sale byte a byte igual
que sin el módulo), `BlocksClient` no registra renderer, tick ni hooks, y las workstations siguen
dibujando su propia pantalla flotante y su computadora provisional.

## Vinculación hilo ↔ bloque

`Binding` (string en el block entity, clave `binding`):

- `thread:<npcKey>`: un hilo. `npcKey` es el id del NPC (`<envId>:<threadId>` en modo directo), la
  misma clave que usan `NpcRegistry`, las workstations y el plan del HQ (`HqInput.fromNpcs` usa
  `NpcView.id()` como thread id).
- `board:<i>`: la bombilla `i` del tablero.
- vacío: sin vincular (bloque puesto a mano, escritorio libre).

Resolución (`Binding.resolve(own, positional)`): **gana el dato del block entity**; si no hay, el
de la posición en el plan del HQ; si tampoco, sin vincular. Determinista.

`HqBindings.of(plan)` es la tabla celda → (rol, binding, orientación), función pura del plan:

- cada escritorio: `computerPos` → monitor mirando a la silla, `lampPos` → lámpara; ambos
  `thread:<threadId>` o sin vincular si el escritorio está libre;
- anclas `board_lamp_<i>` → bombilla `board:<i>`;
- cada módulo: el dintel de su puerta (la viga del muro interior, una celda hacia el pasillo desde el
  ancla `module@<i>` y tres arriba) → bombilla `module:<k1>,<k2>…`, el hilo más urgente de los
  escritorios de ese módulo (claves ordenadas; apagada si el módulo está vacío);
- anclas `archive_<threadId>` → monitor mirando al pasillo, vinculado al hilo archivado.

Como las celdas de un escritorio dependen solo de (módulo, slot) y `HqLayout` es estable, la tabla es
determinista e idempotente: el mismo roster en cualquier orden da la misma tabla (test con rosters
barajados), y aplicarla dos veces no cambia nada. Se usa dos veces con el mismo resultado:

1. `HqBlockFixtures` la escribe **en el plan** como datos de block entity (`{"binding": "thread:…"}`),
   que el preview (`PlanApplier`) y los builders cargan con `loadCustomOnly`. Un escritorio que
   cambia de hilo cambia ese dato, así que el diff del HQ lo vuelve a colocar.
2. `HqBindings.world(plan, site)` da el **fallback por posición** (celda del mundo → binding) para
   bloques sin datos propios. Se enchufa con `BlocksClient.get().setPositionBindings(map)` (punto de
   integración para el dueño del HQ en el mundo; hoy nadie lo llama porque el dato ya viaja en el
   block entity).

### Binding automático (fix/hq-blocks-autobind)

Nadie tiene que usar `/t3craft blocks link` en el HQ:

- **Al construir**: el demo genera su plan con `HqPlans.plan(input, previous)` (aplica `HqFixtures`).
  Antes usaba `HqGenerator.plan`, que **nunca** aplica fixtures: por eso los bloques no aparecían en el
  HQ del demo aunque el módulo estuviera prendido. El hash del demo incluye `HqFixtures.fingerprint()`
  (`"vanilla"` o `HqBlockFixtures.ID`) y el marcador guarda `"fixtures"`, así que un mundo construido en
  vanilla recibe solo las celdas de fixtures como diff (un marcador sin `"fixtures"` cuenta como vanilla;
  con fixtures desconocidas, reconstrucción completa). Sube `HqBlockFixtures.ID` si cambia la sustitución.
- **En vivo**: `DemoHqBindings` recalcula, cuando cambia el roster, el layout contra el construido (y
  luego contra el último en vivo: `HqLayout.allocate` no mueve a nadie) y da `HqBindings.world(plan,
  site)`. `BlocksClient.setHqBindings(source)` lo lee cada 20 ticks y escribe en el servidor integrado
  solo `HqBindings.changes(antes, ahora)` con `BindingWriter` (que además solo toca block entities cuyo
  binding difiere; reenvío completo cada 200 ticks para chunks recién cargados). Un hilo que hace settle
  libera su escritorio (monitor "NO THREAD", lámpara apagada), su monitor del archivo se vincula si la
  celda existe, y la bombilla de su módulo cambia; nada más. Un hilo nuevo toma un escritorio libre ya
  construido; si hace falta un módulo nuevo, sus celdas no existen todavía y se vinculan al reconstruir.
- El HQ de site (`feat/hq-construction`) genera con `HqPlans.generate` (objetivo y plan aplicado), así
  que recibe fixtures y escribe su binding como dato de block entity. Además `HqPresence` (el único punto
  que enchufa el HQ en pie) le pasa a `BlocksClient.setHqBindings` la tabla del HQ en pie: la del plan de
  site (`HqBindings.world(plan, site)`) o, en el demo, la del roster en vivo (`HqPresence.demoBindings`).

A mano: mirando un monitor o lámpara, `/t3craft blocks link <env:thread>` o `/t3craft blocks unlink`
(solo singleplayer o anfitrión LAN; escribe el block entity en el servidor integrado).

## Monitor

- `MonitorScreens` (puro) reusa `WorkstationModel` (mismas líneas, colores y ajuste que la pantalla
  de la workstation, con layout de 22 columnas × 3 líneas de actividad para un bloque) y
  `WorkstationCache` (hash de contenido + etiqueta de edad por hilo; versión nueva solo si cambió).
- `BlocksClient` lo alimenta **una vez por tick** con `NpcRegistry.all()`, el estado que el cliente ya
  tiene (lo mantienen `ThreadShell`/`T3Connection` o el bridge). Es la pantalla de estado; el log en
  vivo (abajo) solo se suscribe a los monitores cercanos y visibles.
- `MonitorRenderer` (block entity renderer, API de render states de 26.x): en `extractRenderState`
  resuelve el binding, elige el nivel de detalle y **solo rehace los glyph runs cuando cambia la
  versión** (contenido, nivel o tamaño); en `submit` dibuja capas opacas full-bright sobre la cara
  frontal. Detalle en "Log en vivo" abajo.
- Sin vincular: "T3Craft monitor · NO THREAD". Hilo desconocido: su id + "· OFFLINE".
- Click (`useWithoutItem` en el cliente) → `ThreadOpener` de la workstation
  (`WorkstationClient.get().opener()`, que respeta el override de feat/chat; por defecto
  `ChatScreenOpener`). Si el monitor está en el `computerPos` de una estación, el `UseBlockCallback`
  de la workstation lo toma antes y abre el mismo hilo: mismo resultado.
- Integración con workstation: `WorkstationClient.setScreenSuppressor(state -> …instanceof MonitorBlock)`.
  Sobre un monitor, el `TextDisplay` flotante de la estación queda vacío (una pantalla, no dos).
  Sobre cualquier bloque vanilla, todo sigue igual.

### Log en vivo (feat/monitor-live-log, P2 de `research/agentcraft-world-elements.md`)

Paquete `client.blocks`, todo dentro del módulo (apagado, nada de esto existe). Tests:
`MonitorVisibilityTest`, `MonitorFeedsTest`, `MonitorLogTest`, `MonitorLodTest`.

**Qué se ve** (`MonitorRenderer`, dos niveles de detalle, `MonitorLod`):

| Nivel | Cuándo | Contenido |
|---|---|---|
| **NEAR** | a ≤ `nearDistance` (8) bloques por bloque del lado menor de la pantalla; sale a +2 (histéresis) | Cabecera (punto de estado, título, palabra de estado) + **tail del log** (la fila más nueva abajo): tool calls con icono del kit (`t3craft:chat/icon_*`), nombre y argumento (`ToolLine` del chat), rutas recortadas por la izquierda (`…/blocks/Foo.java`), `+N -M` en verde y rojo, comandos fallidos en rojo, prosa del agente en ≤ 2 líneas sin Markdown, peticiones con fondo ámbar. Pie: **cursor** parpadeante si trabaja, **"! needs you: …"** pulsante si hay una petición pendiente. Sin stream (lejos, modo bridge, sin pairing): las líneas de estado de siempre (`MonitorScreens`). |
| **FAR** | más lejos | Cara entera del color del estado (oscurecido), bandas del color arriba y abajo, **palabra de estado grande** (escala hasta 2,5×, ajustada al ancho) y el título debajo. Se lee de un lado a otro de la sala. |

El modelo (`MonitorLog`) es puro: filas de `ChatTranscript.View` (las mismas `ChatEntry` y el mismo
`ToolLine` que el chat), estado del shell (`NpcRegistry`) salvo que el transcript tenga una petición
pendiente, que gana. Sin filas heredadas (fork).

**Datos y política de visibilidad** (`MonitorLiveLog` + `MonitorVisibility` + `MonitorFeeds`):

- `BlocksClient` sigue los `MonitorBlockEntity` cargados (`ClientBlockEntityEvents` load/unload) y cada
  5 ticks mide cada uno: distancia del ojo al centro de la cara, si se ve de frente, si cae en el cono
  de visión (FOV de las opciones + aspecto de la ventana + 15° de margen; a < 2,5 bloques cuenta
  siempre) y, en modo `line_of_sight`, un `level.clip` hasta la pantalla.
- **Adquiere** a ≤ `subscribeRadius` (16) y visible. **Mantiene** mientras siga a ≤ radio +
  `releaseHysteresis` (4) y se haya visto hace ≤ `releaseAfterSeconds` (10). Si no, **suelta**.
  Un monitor descargado se olvida.
- Devuelve el conjunto de **claves de hilo** (no de monitores): `MonitorFeeds` mantiene **un
  `ThreadFeed` por hilo**, abierto con `ChatScreens.defaultSource()` (la conexión compartida del
  entorno, `subscribeThread` refcount de `StreamRegistry`, solo lectura). Tres monitores del mismo hilo
  y el chat abierto encima = una sola suscripción en el servidor (test). Si no se puede abrir (modo
  bridge, entorno sin pairing) reintenta a los 15 s, sin tormenta por tick.
- Al salir del mundo, en `DevReload` y al cerrar el cliente se cierran todos los feeds.
- `/t3craft blocks` añade una línea: monitores cargados, streams del log y sus hilos.

**Config** (aditiva, `config/t3craft.json`; se relee con `DevReload`):

```json
"blocks": { "monitor": {
  "liveLog": true, "subscribeRadius": 16, "releaseHysteresis": 4, "releaseAfterSeconds": 10,
  "visibility": "frustum", "viewDistance": 64, "nearDistance": 8
} }
```

`visibility`: `frustum` (cono de visión, por defecto), `line_of_sight` (cono + raycast) o `distance`.
Valores fuera de rango se acotan; tipos incorrectos dejan el valor por defecto.

**El bug "desaparece al acercarse / ilegible de lejos"** (no se pudo reproducir sin lanzar el
juego; se atacaron todas las causas plausibles del renderer viejo):

- El fondo se dibujaba con `submitTextBackground` (pipeline de texto **translúcido**): va después de lo
  opaco, ordenado por distancia, contra una cara de bloque casi coplanaria. Ahora el fondo, las bandas,
  el cursor y los iconos son **geometría opaca en la pasada sólida** con escritura de profundidad
  (`ScreenDraw`, copia del pipeline de texto del mundo sin blending, como `DisplayDraw` de AgentCraft).
- Separación fija de 0,003 bloques: un depth buffer de 24 bits resuelve ~d²/(0,05·2²⁴) bloques a
  distancia d, así que pasados ~40 bloques el fondo parpadeaba con la cara. Ahora el paso entre capas
  crece con d² (`MonitorLod.zStep`, mínimo 0,0012 como AgentCraft, ~0,02 a 64 bloques).
- `shouldRenderOffScreen() = true`: el monitor no depende del culling de su sección (también lo
  necesita P1 para dibujar desde el origen) y `getViewDistance`/`shouldRender` usan `viewDistance`
  (64, configurable; antes 32 fijo).
- Legibilidad: densidad 128 px/bloque de cerca (antes 160: letras de 0,05 bloque), nivel FAR con
  texto grande, fondo casi negro con texto claro, todo full-bright.

**Para P1 (multibloque)**: todo el layout sale de `ScreenExtent` (ancho × alto en bloques):
`MonitorLod.layout(level, extent)`, `nearLimit`, densidad 128/96 según el lado menor, y el renderer
ya traslada hacia arriba `(alto − 1)` bloques desde el bloque de origen. P1 solo tiene que devolver
el extent del panel en `MonitorRenderer.extent(be)` y no dibujar en los bloques que no son origen.

## Lámparas

Las lámparas son modelos de block state (sin renderer): el color y la luz salen de `status`. Como
la luz de bloque necesita el estado real en el servidor, el cliente calcula lo que quiere
(`LampSignals.wanted`: `thread:<key>` → estado, `board:<i>` → estado) y, **solo con servidor integrado**
(singleplayer o anfitrión LAN), se lo pasa con `server.execute(...)` a `LampApplier`, que recorre los
`LampBlockEntity` cargados (`LiveLamps`, sin escanear chunks) y escribe `status` solo donde difiere.
Se envía cuando cambia lo deseado y, además, cada 40 ticks (lámparas recién colocadas o cargadas).
En un servidor remoto las lámparas conservan su estado guardado (el servidor manda).

Misma idea que el `HqWorldDriver` de AgentCraft; implementación nueva y más chica.

## HQ: adaptador opcional

Interfaz nueva y pequeña en hq: **`dev.t3craft.hq.HqFixtures`** (`BuildPlan apply(BuildPlan)`, con
`VANILLA` = identidad, `active()` y `use(...)`), más `BuildPlan.replacing(...)` (sustituye estados o
datos de block entity sin mover celdas). `HqGenerator` no cambió: sigue generando el HQ vanilla. Se
aplica en:

- `HqPlans.generate(layout)` (y por tanto `forRoster`, `forDemo`);
- `/t3craft debug hq-preview` (`HqPreviewCommand`).

`T3Blocks.init` instala `HqBlockFixtures` solo si el módulo está activo. Anclas, workstations y
celdas no cambian, así que NPCs, workstations y diffs del HQ ven el mismo plan. Con los bloques el
fingerprint del plan cambia (si alguien versiona el plan del demo, sube la versión).

## Cómo añadir un bloque nuevo

1. **Bloque** en `dev.t3craft.blocks` (si muestra estado: `extends BaseEntityBlock`, recibe un
   `Supplier<BlockEntityType<…>>` como `MonitorBlock`). Nada de registros estáticos en la clase.
2. **Block entity**: `extends BoundBlockEntity` si se vincula a algo (hereda `binding`, guardado y
   sincronización con el cliente). Si tiene un formato de binding nuevo, añade el `Kind` en `Binding`
   (con test de ida y vuelta).
3. **Registro**: en `T3Blocks.init`, con los helpers `block(...)`, `item(...)` y
   `r.blockEntity(...)`; añade el ítem a la lista de `creativeTab`. Agrega el campo a `Content`.
   **Nunca** registres fuera de `init`: así el interruptor lo apaga.
4. **Cliente** en `dev.t3craft.client.blocks`: renderer (`BlockEntityRendererRegistry.register` dentro
   de `BlocksClient.onInitializeClient`, después del `return` del módulo apagado) y la lógica pura
   aparte, testeada. Lee estado que el cliente ya tiene; no abras suscripciones.
5. **HQ** (si va en el HQ): añade el rol en `HqBindings` (celda desde anclas o workstations) y su
   estado en `HqBlockFixtures.States`. Sin tocar `HqGenerator`.
6. **Datos**: `blockstates/<id>.json`, `models/block/…`, `textures/block/…`, `items/<id>.json`,
   `data/t3craft/loot_table/blocks/<id>.json` (que se suelte a sí mismo), lang `block.t3craft.<id>` en
   `en_us` y `es_es`, receta si tiene sentido.
7. **Tests**: lógica pura + que con el módulo apagado siga sin registrarse nada.
8. **CHANGELOG-dev**: es **RESTART** (registros).

## Esqueleto para después (no está en esta rama)

- **Podio de decisiones** (`t3craft:decision_podium`): depende de la pantalla "Needs you" (inventario
  ítem 4). Bloque con `facing` + `BoundBlockEntity` con binding nuevo `decisions` (o
  `decisions:<env>`); `HqBindings` lo pondría frente al tablero del hub (ancla nueva en hq-design,
  p. ej. `podium`). Click → abre "Needs you". Luz: `attention` si hay pending requests. Botones
  Allow/Deny deshabilitados mientras el mod sea solo lectura.
- **Tablero de tareas** (`t3craft:task_board`): depende de las tareas programadas (ítem 15,
  `scheduledTasks.subscribe`, que debe ir por `T3Connection`, no por un socket propio). Panel con
  renderer como el monitor, binding `tasks` o `tasks:<env>`; layout puro (lista de próximas
  ejecuciones) testeado aparte.

Ambos siguen los pasos de arriba; `LampStatus` y `MonitorRenderer.toFace` se pueden reusar.

## Compatibilidad (compat.md)

- Todo el registro está aislado y es apagable. compat.md §4 pide, a medio plazo, no registrar nada
  para el modo remoto: con `-Dt3craft.blocks=false` o `"blocks": {"enabled": false}` el módulo no
  existe. Con el módulo encendido, en un servidor remoto **sin** el mod los bloques simplemente no
  aparecen (el servidor no los manda); el ítem en la pestaña creativa tiene el mismo riesgo que el
  T3 Terminal (§1.1).
- Sin payloads de red, sin mixins. `ServerLevel` solo en el comando `link` y en `LampApplier`
  (servidor integrado).

## Cambios estructurales → **RESTART**

- Bloques: `t3craft:monitor`, `t3craft:status_lamp`, `t3craft:board_lamp`.
- Block entities: `t3craft:monitor`, `t3craft:status_lamp` (lámpara y bombilla).
- Ítems de bloque: `t3craft:monitor`, `t3craft:status_lamp`, `t3craft:board_lamp` (pestaña Functional
  blocks).
- Renderer: `MonitorRenderer` para `t3craft:monitor`.
- Entrypoints nuevos: `main` `dev.t3craft.blocks.BlocksInit`, `client`
  `dev.t3craft.client.blocks.BlocksClient`.
- Tick de cliente (`GuardedEvents`, módulo `blocks`), `CLIENT_STOPPING`, `DevReload`
  (`t3craft.blocks`), comando `/t3craft blocks [link <thread>|unlink]`.
- API nueva: `HqFixtures`, `BuildPlan.replacing`, `WorkstationClient.setScreenSuppressor`,
  `WorkstationClient.opener()`.
- Datos: blockstates, modelos, texturas, loot tables, 4 recetas, lang.
- Config aditiva: `"blocks": { "enabled": true }`.
- feat/monitor-live-log (**RESTART**): listeners nuevos `ClientBlockEntityEvents` load/unload en
  `BlocksClient.onInitializeClient`; `MonitorRenderer` reescrito (`shouldRenderOffScreen`, distancia de
  vista configurable) con un `RenderPipeline`/`RenderType` propio (`t3craft:pipeline/monitor_solid`) y
  textura dinámica `t3craft:dynamic/monitor_white`; clases nuevas `MonitorLiveLog`, `MonitorFeeds`,
  `MonitorVisibility`, `MonitorLog`, `MonitorLod`, `MonitorSettings`, `ScreenExtent`, `ScreenDraw`;
  lang `t3craft.blocks.monitor_status` (en_us, es_es, es_mx); config aditiva `blocks.monitor.*`.
  Sin registros de bloques ni datos nuevos.

## Pendientes

- **No se probó dentro del juego** (por regla, no se lanza Minecraft). Falta verificar a ojo: que el
  texto del monitor quede del lado correcto y no espejado (la transformación es la de AgentCraft, ya
  probada allí), el tamaño del texto, la luz de las lámparas y que el click del monitor no mande
  paquetes indebidos.
- Log en vivo, sin probar en el juego: verificar a ojo que el monitor ya no desaparece al acercarse
  (con y sin Sodium), el cambio NEAR/FAR, los iconos del kit en el mundo y el cursor.
- La edad en el monitor tiene el mismo límite que la workstation (sin timestamp en `NpcInfo`).
- Nadie llama todavía a `setPositionBindings`; el dueño del HQ en el mundo (site/builders) puede
  hacerlo con `HqBindings.world(plan, site)`.
