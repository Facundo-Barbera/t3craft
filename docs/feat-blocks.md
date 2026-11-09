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
| `t3craft:archive_terminal` | `t3craft:archive_terminal` (`ArchiveTerminalBlockEntity`) | Consola de archivo (feat/archive-terminal): la pantalla muestra "ARCHIVE · N hilos" y los últimos 3; click → pantalla de archivo. En el HQ, en el anchor `archiveTerminal`. Ver `docs/feat-archive-terminal.md`. |
| `t3craft:board_lamp` | el mismo tipo `t3craft:status_lamp` | Variante "bombilla del tablero": la i-ésima bombilla muestra el i-ésimo hilo que necesita al jugador (errores primero, luego "needs you"). |
| `t3craft:decision_podium` | `t3craft:decision_podium` (`LampBlockEntity`, sin renderer) | Podio de decisiones (kit/decision-podium): atril con campana y pantalla, vinculado `board:<i>` como una bombilla del tablero. Muestra el i-ésimo hilo que necesita al jugador (errores primero, luego "needs you"); modelo y luz por `status`; click → abre ese hilo. Ver sección "Podio de decisiones". |
| `t3craft:thread_board` | `t3craft:thread_board` (`ThreadBoardBlockEntity`) | Ancla de la pared kanban de hilos (P3): el BER dibuja una pared de `width × height` bloques desde la celda inferior izquierda. Click en una tarjeta → chat del hilo. Ver `docs/feat-thread-wall.md`. |

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

### Aspecto de la lámpara (kit/status-lamp)

Modelo de farol en cápsula, simétrico arriba/abajo (sirve sobre la pantalla y colgada de una cadena, como la
baliza del hub): tapas de acero con anillos dorados y una lente de 10 px. Solo la lente y sus aletas brillan
(`light_emission` por elemento, también en el juego). El estado se lee por color, silueta y animación:

| `status` | Silueta | Glifo | Animación |
|---|---|---|---|
| `off` | lente oscura y delgada, sin brillo | reflejo apagado | ninguna |
| `idle` | vela gris delgada y tenue | pausa | ninguna |
| `working` | lente de 8 px con banda brillante | anillo que gira | 4 fotogramas (`frametime` 3) |
| `done` | lente verde lisa y estable | check | ninguna |
| `attention` | lente de 10 px con 4 cuernos (estrella de sirena) | `!` | parpadeo lento (`frametime` 12) |
| `error` | lente ancha y baja con 8 garras (corona de púas) | X | parpadeo rápido (`frametime` 5, < 3 Hz) |

Texturas y modelos se generan con `python3 tools/art/status_lamp_art.py` (solo biblioteca estándar); las tiras
animadas llevan `.png.mcmeta`. Galería de revisión: `tools/render/submit.sh --final -- --lamps` (`LampViews`),
y `--lamp-demo` pone estados en las lámparas de cualquier render de plan. `lampProps` ahora lleva `noOcclusion`
(el modelo ya no es un cubo; también afecta a `board_lamp`, que sigue siendo un cubo sin consecuencias visibles).

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

- cada escritorio: las 6 celdas de su pantalla de 3×2 (`Workstation.screenCells()`, origen arriba a la
  izquierda en `screenOrigin`) → monitores mirando al agente, todos `thread:<threadId>`, así que se
  fusionan en una sola pantalla que dibuja desde su celda de abajo a la izquierda (cabecera con título y
  estado, que respira si "needs you", y debajo el log en vivo). Los escritorios ya no tienen lámpara
  (fix/desk-screens-3x2); sin vincular si el escritorio está libre;
- anclas `board_lamp_<i>` → bombilla `board:<i>`;
- cada módulo: el dintel de su puerta (la viga del muro interior, una celda hacia el pasillo desde el
  ancla `module@<i>` y tres arriba) → bombilla `module:<k1>,<k2>…`, el hilo más urgente de los
  escritorios de ese módulo (claves ordenadas; apagada si el módulo está vacío);
- ancla `archiveTerminal` → la terminal de archivo (sin binding). Las celdas `archive_<threadId>` del
  estante ya **no** son fixtures (feat/archive-terminal: el archivo vive solo en la terminal).

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

### Aspecto del monitor (kit/monitor)

Monitor de escritorio de oficina en grafito/acero, con el cristal hundido. Todo se genera con
`python3 tools/art/monitor_art.py` (texturas, modelos y blockstate; solo biblioteca estándar).

- **Marco**: tiras de 1 px de relieve (el labio sobresale del cristal) solo en los lados **sin vecino** (`up/down/left/right`
  en `false`), así un muro es una sola pantalla con un marco continuo. Bisel de 2 px arriba y a los lados, **barbilla de 4 px**
  abajo y, en sus 2 px más bajos, el **pie**: un reborde de acero de 5 px de fondo y cara superior clara (el soporte sobre el escritorio, un zócalo
  en un muro). Las tiras izquierda/derecha son de altura completa y se quedan con las esquinas; las de arriba/abajo paran en
  una esquina solo si ese lado tiene tira (variantes `monitor_edge_{up,down}`, `_l`, `_r`, `_l_r`), sin solapes.
- **Carcasa**: `monitor_side`/`monitor_top` lisas con una línea de panel; `monitor_back` con rejilla y placa "T3". El ítem usa
  `models/block/monitor.json` (todos los lados enmarcados). Texturas: `monitor_front` (el marco visto de frente), `monitor_screen`
  (el cristal; el renderer lo tapa), `monitor_trim` (muestras para labios y pie).
- **Renderer** (`MonitorLod.Layout` lleva `bezel` y `chin`; el cristal acaba donde empieza la barbilla):
  - densidad NEAR `nearPpb = clamp(150 / ancho, 32, 96)`: unos 25 caracteres de ancho, el texto de 8 px mide 1/6 de bloque en
    una pantalla de escritorio de 3 de ancho (legible a 3–6 bloques);
  - cabecera en dos filas: el título a ancho completo y debajo una **banda de estado** a ancho completo del color del
    estado (texto oscuro) con palabra + antigüedad (`WORKING · 3m`, de la línea de estado de `WorkstationModel`); sin stream,
    el resto (rama, último mensaje) va debajo, de arriba abajo; las pantallas de proyecto/entorno conservan su cabecera de una fila;
  - **sin hilo** (sin vincular, hilo desconocido o archivado): pantalla de espera de cristal casi negro con dos reflejos
    inclinados, la marca **T3** y la palabra grande `NO THREAD` / `OFFLINE` / `ARCHIVED` (más el nombre del hilo en las dos
    últimas); igual a cualquier distancia; el LED de la barbilla respira apenas en `NO THREAD`;
  - **barbilla**: LED del color del estado a la derecha, sobre la cara del labio (la banda de 2 px sobre el pie).
- **Revisión**: `tools/render/submit.sh --final -- --monitors` (`MonitorViews`: muro 6×3, tres escritorios de 3×2 con lámpara,
  un monitor suelto; vistas `iso_se`, `front`, `wall`, `desks`, `desk1`, `far_20`, `far_50`). El renderer headless dibuja solo
  modelos de bloque: **el texto de la pantalla (el BER) no se ve**, solo el cristal.

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

**Interfaz con P1 (multibloque)**: `ScreenExtent(width, height, origin)` + `ScreenExtent.Provider`
(`MonitorBlockEntity → ScreenExtent`, null = 1×1). P1 lo instala con
`BlocksClient.get().setScreenExtents(provider)`. Contrato: el **origen es el bloque inferior izquierdo
visto de frente**; el renderer dibuja desde su cara hacia la derecha y hacia arriba
`width × height` bloques con el mismo facing. Los bloques `ScreenExtent.member(w, h)` no dibujan, y
solo el origen se mide para la visibilidad (desde el centro de la pantalla, `screenCenter`) y se
suscribe, con el binding del origen. Todo el layout sale del extent: `MonitorLod.layout(level, extent)`,
`nearLimit` (escala con el lado menor) y densidad 128/96 px por bloque. P1 se encarga de los block
states conectados, los modelos de bisel y el cálculo del extent.

### Pantallas grandes (feat/monitor-multiblock, P1 de `research/agentcraft-world-elements.md`)

Monitores contiguos (arriba, abajo, izquierda o derecha vistos de frente) con **el mismo facing** forman
un grupo (`MonitorWall`, puro). El grupo es **una sola pantalla** si:
- llena exactamente su rectángulo (formas en L o con huecos → cada monitor sigue siendo 1×1);
- mide como mucho 12 × 8 bloques;
- sus bindings coinciden: todos los monitores vinculados tienen el mismo binding, y los sin vincular se
  suman a cualquiera. Así dos escritorios o dos ranuras del archivo que se tocan (cada una con su hilo)
  no se fusionan, y una pared puesta a mano (sin vincular) sí, y luego se vincula entera.

Block states nuevos de `t3craft:monitor`: `up`, `down`, `left`, `right` (booleanos, vistos de frente:
`left = facing.getClockWise()`), como en `PanelBlock` de AgentCraft. `true` = ese vecino es de la misma
pantalla. El blockstate es multipart: `monitor_panel` (cubo con la pantalla sin bisel) más
`monitor_edge_<lado>` (tira de bisel de 1 px de modelo, la misma que reserva `MonitorLod`) en cada lado
no conectado. El ítem sigue usando `models/block/monitor.json` (con bisel completo).

Actualización: el servidor recalcula **un tick después** de poner, quitar, girar o revincular un monitor
(`onPlace`, `affectNeighborsAfterRemoval`, `MonitorBlockEntity.bindingChanged` → `scheduleTick` →
`MonitorBlock.refresh`), para el grupo entero, y escribe solo los flags que cambian
(`UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, sin cascada). El retraso deja que el builder del HQ o una
estructura carguen antes los datos del block entity (el binding). Las celdas sin cargar cuentan como
vacías.

Cliente: `MonitorBlock.extent(level, pos, state)` lee la pantalla desde los flags (`ScreenRect`: esquina
superior izquierda, ancho × alto). `BlocksClient` instala el `ScreenExtent.Provider` de P2
(`connectedExtent`): `null` para un monitor suelto; si no, el tamaño y si el bloque es el **inferior
izquierdo** (`ScreenRect.bottomLeft()`, el origen de dibujo de P2). El renderer de P2 no cambia salvo
dos ganchos: el binding de la pantalla (`BlocksClient.screenBinding`: la primera celda vinculada, el
origen primero, con el fallback posicional) y el contenido de los feeds. El clic en cualquier celda abre
lo que muestra la pantalla entera. `/t3craft blocks link` vincula **todas** las celdas de la pantalla
que miras y acepta `project:<env:proyecto>`, `env:<envId>` o `thread:<key>` (sin prefijo = hilo).

Contenido de una pantalla grande:
- vinculada a un hilo: el log en vivo de P2 a su tamaño (`MonitorLod` reparte filas y columnas);
- vinculada a un **proyecto** (`project:<groupId>`, el `groupId` de la NPC, `env:proyecto`) o a un
  **entorno** (`env:<envId>`), bindings nuevos: `ProjectFeed`, una cabecera "▤ nombre · n active
  threads" y los hilos activos (no archivados), el más urgente primero (needs you, error, working,
  done, idle), con el título en el color de su estado y su último evento debajo (o en la misma línea si
  no caben, y "+N more" si sobran). Memoizado por `MonitorScreens.revision()`.

**Desde el plan del HQ** (para feat/hq-style-cherry): el generador reserva una pantalla con un ancla
```java
b.anchor(Anchor.face(AnchorNames.bigScreen("module@" + i, 3, 2, "project:" + projectKey), topLeft, facing));
```
(`bigscreen_<clave>@<w>x<h>=<binding>`; la clave es única y no lleva `=`; el bloque del ancla es la
celda **superior izquierda** vista de frente y el facing es la normal de la pantalla) y pone un bloque
sólido en cada celda (el módulo solo sustituye celdas existentes; sin módulo se quedan como estén).
`HqBindings.screens(plan)` las lista; `HqBindings.of` da un `Fixture` `Role.SCREEN` por celda, todas con
el binding de la pantalla, y `HqBlockFixtures` pone monitores con los flags que `MonitorWall` calcula
sobre las celdas de monitor del plan (la misma regla que el mundo, así que la pantalla ya sale
fusionada). `BuildPlan.withAnchors` añade anclas a un plan ya generado. `HqBlockFixtures.ID` pasa a
`blocks-4` (junto con las placas de hq-blocks-kit; los mundos demo se actualizan).

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

## Terminal de consola (kit/console-terminal)

`t3craft:console_terminal`: escritorio de mando con teclado inclinado (una cuña a -22,5° como la tapa del
atril) y una pantalla pequeña emisiva con un prompt `> _` en turquesa T3 (cursor ámbar, línea de estado),
más dos LED encima. Pizarra de acero con luz propia (6) y pantalla/LED con `light_emission` 15.
La idea (una consola que abre la línea de comandos) es de AgentCraft (MIT); código, modelo y texturas son
nuestros (`tools/art/console_terminal_art.py` genera las seis texturas y el modelo, solo biblioteca estándar).

- **Click** (consumido en ambos lados, como el monitor; agáchate para colocar bloques contra él):
  abre el compositor de T3 sobre un **borrador de hilo nuevo** (`ChatScreens.create(null).withDraft(null, null)`,
  lo mismo que el "+" de `NewThreadScreen`); si está vinculado a un hilo (`thread:<env>:<id>`), abre el chat de ese
  hilo (`openThread`, igual que el monitor). Cualquier otro vínculo se trata como sin vincular.
- **Vínculo**: `ConsoleTerminalBlockEntity extends BoundBlockEntity`, así que `/t3craft blocks link <hilo>` y
  `unlink` funcionan mirando el bloque, y `binding` se guarda con el mundo. Sin renderer propio.
- **Código**: patrón de `ArchiveTerminalBlock` (`facing`, formas por orientación, hook estático `clientUse` que fija
  `BlocksClient` con `ConsoleTerminalBlock.setClientUse(this::useConsole)`), con el binding del monitor.
- **HQ**: rol `HqKit.Role.CONSOLE` (orientado: frente = donde se para el jugador), `HqKit#console(Direction)`;
  fallback vanilla: atril. `HqBlockFixtures.States#consoleTerminal(Direction)` para quien coloque por fixtures
  (por defecto atril). `KitBlocks` lo mapea (`ROLE_BLOCKS`) sin añadirlo a `IDS`: lo registra `T3Blocks.init`
  con su propio block entity y lo suma al mapa del kit. La oficina de mando (`CitadelOffice`) pone tres en la sala
  de control, donde estaban los atriles.
- **Datos**: blockstate (4 `facing`), modelo, 6 texturas, ítem, loot table, receta barata
  (panel de vidrio, 2 botones de piedra, redstone, 3 lingotes de hierro), tag `mineable/pickaxe`, lang en `en_us`,
  `es_es` y `es_mx`.
- **RESTART** (registro nuevo, clases kernel `dev.t3craft.blocks.*` y `HqKit`).

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

## Podio de decisiones (kit/decision-podium)

Idea de AgentCraft (`decision_podium`: un podio donde espera el agente que necesita una decisión, con campana y `!`), implementada
desde cero: modelo, texturas (`tools/art/decision_podium_art.py`) y código son nuestros; no se copió nada (ver `THIRD_PARTY_NOTICES.md`).

- **Bloque** `t3craft:decision_podium` (`DecisionPodiumBlock`): `facing` + `status` (la **misma** propiedad que `StatusLampBlock.STATUS`, así
  `LampApplier` lo maneja sin código nuevo). Luz = `LampStatus.light`. Block entity: `LampBlockEntity` con tipo propio
  `t3craft:decision_podium` (entra en `LiveLamps`; sin `LampRenderer`, que dibuja un halo sobre un cubo entero).
- **Vinculación:** `board:<i>`, como `board_lamp`. En el HQ, el ancla `decision_podium_<i>` (`AnchorNames.podium(i)`, ancla de superficie: no
  se para nadie en ella) da `HqBindings.Role.PODIUM` con `Binding.board(i)`; `HqBlockFixtures` cambia la celda del plan por el podio
  (`States.podium(facing)`; sin el módulo queda lo que puso el plan: un atril vanilla).
- **Qué muestra** (`LampSignals`): el i-ésimo de `LampSignals.urgent` (errores primero, luego "needs you"; empates por clave): el mismo hilo que
  la bombilla `board:<i>`. Pantalla de 14×6 px por estado: `off` vidrio oscuro; `idle` pausa gris; `working` barra cian que corre (animada);
  `done` palomita verde; `attention` tres `!` naranjas que parpadean lento; `error` X roja que parpadea rápido. En `attention` y `error`
  además dos marcas luminosas flanquean la campana ("está sonando") para que se lea de lejos aunque no se vea la pantalla.
- **Click** (cliente, `BlocksClient.usePodium`): abre el hilo (`openThread`, el mismo `ThreadOpener` de los monitores) para responder su pregunta o
  permiso pendiente; si nadie espera, una línea de chat (`t3craft.podium.nobody`). Consumido en ambos lados; agachado se pueden poner bloques.
- **Receta:** 1 lingote de oro + 1 atril + 1 redstone (en vertical). Se rompe con hacha y se suelta a sí mismo.
- **Oficina** (`CitadelOffice.control`): cinco podios (`decision_podium_0..4`, `board:0..4`) bajo la pantalla de hilos, de cara al norte; las
  bombillas de la pared del este siguen mostrando los rangos 6 a 10.
- **Qué no hace:** los NPC que esperan siguen juntándose en el tablero del hub (`board_slot_*`, que `HqAnchorSource` lee de 0 en adelante y que
  son del hub); la oficina no suma `board_slot_*` propios. Allow/Deny directos (sin abrir el chat) quedan para cuando el mod deje de ser solo lectura.
- **Sin módulo:** el atril vanilla del plan (sin campana). El ancla sigue en el plan, inocua.

## Esqueleto para después (no está en esta rama)

- ~~Podio de decisiones~~: hecho en kit/decision-podium (sección de arriba, versión `board:<i>`). La variante con un binding `decisions` y botones Allow/Deny queda pendiente de la pantalla "Needs you".
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

## Kit del HQ (feat/hq-blocks-kit): bloques decorativos propios, por rol

Once bloques decorativos propios para el estilo **C, campus de cerezo** (`docs/research/hq-styles.md`
§C) y el oficio "agentes trabajando". Modelos JSON y texturas propias (`textures/block/kit_*.png`,
pixel art original dibujado por `mod/tools/blocks_kit_textures.py`, determinista, sin nada de Mojang).
Registro en `KitBlocks.register`, llamado solo desde `T3Blocks.init`: con el módulo apagado no existe
ninguno. Ítems en la pestaña **T3Craft**, loot tables (se sueltan a sí mismos), tags `mineable/axe` y
`mineable/pickaxe`, lang en `en_us`, `es_es` y `es_mx`. Sin recetas por ahora.

| Bloque | Qué es | Estado / luz |
|---|---|---|
| `t3craft:standing_desk` | Escritorio de pie: tablero, cuatro patas, faldón atrás. El agente trabaja de pie delante (sin silla); el monitor va en la celda de arriba. | `facing` = frente (donde se para el agente) |
| `t3craft:shoji_panel` | Panel shoji: papel en celosía de cerezo. Click → enciende/apaga. | `lit` (luz 12) |
| `t3craft:cherry_floor_tile` | Loseta de tablas de cerezo con borde oscuro y clavos de latón. | — |
| `t3craft:stone_floor_tile` | Piedra pulida con la marca de t3craft (una "T" turquesa) en una esquina. | — |
| `t3craft:cherry_beam` | Viga/columna de cerezo. | `axis` |
| `t3craft:beam_cap` | Remate de columna: cuello y corona laqueada con banda de latón. | — |
| `t3craft:paper_lantern` | Farol de papel. Es un `LampBlockEntity` (mismo tipo que la lámpara de estado): con `{"binding": "thread:<k>"}` o `module:<k1>,…` respira con el hilo (BER de lamps-breathe, con el brillo sobre el cuerpo de papel); sin vincular es un farol cálido. | `status` (luz 12 apagado/sin vincular, ≥ 9 vinculado) |
| `t3craft:room_plaque` | Placa de sala: marco de cerezo con placa de tinta; el texto lo dibuja `PlaqueRenderer`. Sin colisión, como un letrero. | `facing` = frente (cuelga del bloque de detrás) |
| `t3craft:cherry_railing` | Barandilla en el borde `facing` de la celda que se une con sus vecinas (`KitRailingBlock`): pasamanos continuo, postes solo en extremos y giros, esquinas cerradas (`turn`) y escalones de una celda tendidos con un tramo lateral (`step`); en una esquina interior la celda deja solo el poste (`front=false`). Lo resuelven `getStateForPlacement`/`updateShape` en el mundo y `PlanConnect` en los planos, con la misma regla pura (`KitRailingBlock.resolve`, solo lee el `facing` de las barandillas vecinas). | `facing` = borde; `front`; `left`/`right` = `open`, `post`, `turn`, `step` |
| `t3craft:cherry_planter` | Maceta de cerezo (tiesto, tronco, copa en flor). Todo opaco: sin capas cutout. | — |
| `t3craft:stone_lantern` | Linterna de piedra (tōrō) con musgo. | luz 13 |

Ningún modelo usa transparencia (no hace falta registrar capas de render); los bloques que no son un
cubo llevan `noOcclusion` y su `VoxelShape`.

### Luz integrada (kit/glow-panel): `glow_strip` y `glow_panel`

Idea de AgentCraft (MIT; solo la idea, ver `THIRD_PARTY_NOTICES.md`): iluminación emisiva integrada en la obra, para que
los interiores tengan luz sin faroles colgados por todas partes y sin fuentes de luz visibles en suelos. Código, modelos y
texturas propios (`tools/art/glow_art.py`, solo biblioteca estándar; `--preview DIR` escribe PNG ampliados para revisar).
Luz 12, sin block entity, sin recetas caras (tiras: 3 losas de deepslate tile + 3 polvo de glowstone → 6; panel: 6 deepslate tiles + 3 polvo → 2).
Mina con pico. Con la mano vacía, click derecho alterna el tono.

| Bloque | Qué es | Estado |
|---|---|---|
| `t3craft:glow_strip` | Tira de luz de 16 de largo, 3 de alto y 2 de fondo, pegada a un borde de la celda (zócalo, borde de escalón, bajo una pantalla o una viga). Sin colisión. Se conecta en línea por geometría: los extremos son caras lisas y las caras donde se tocan dos tiras quedan dentro de la barra, así que una fila se lee como una sola barra y solo los extremos de la fila enseñan tapa. No hay estado de conexión (nada que resolver en `PlanConnect`). | `facing` (horizontal: hacia dónde mira; el borde que abraza queda detrás), `half` (`bottom`/`top`), `tone` (`warm` papel/ámbar, `cool` cian T3) |
| `t3craft:glow_panel` | Bloque entero que es una luz de oficina moderna: marco de deepslate, ribete de cerezo y difusor con dos líneas de lamas. Va a ras en un techo o una pared. | `facing` (6 direcciones: la cara que brilla; `down` en un techo), `tone` |

Roles de `HqKit` (kit-4): `GLOW_STRIP`, `GLOW_STRIP_COOL` (parte baja de la celda, cálida/fría), `GLOW_STRIP_HIGH`,
`GLOW_STRIP_HIGH_COOL` (parte alta), `GLOW_PANEL`, `GLOW_PANEL_COOL`; atajos `glowStrip(facing, high, cool)` y
`glowPanel(facing, cool)`.

| Rol | Con el módulo | Fallback vanilla |
|---|---|---|
| tiras (los cuatro) | `glow_strip[facing,half,tone]` | `minecraft:light[level=12]` invisible: ilumina igual, no tiene colisión (mismo tipo de paso que la tira) |
| `GLOW_PANEL` | `glow_panel[facing,tone=warm]` | `ochre_froglight` |
| `GLOW_PANEL_COOL` | `glow_panel[facing,tone=cool]` | `sea_lantern` |

Usos en la ciudadela: `CitadelOffice.glow` (sala de control: tiras frías bajo las pantallas y tres paneles fríos en el techo,
que sustituyen a las luces invisibles de ese lado; tira cálida a lo largo de cada tramo de escalera, una por peldaño) y
`HallInterior.runway` (halls: zócalo cálido a ambos lados de la alfombra del pasillo, solo en celdas libres que no son de un
escritorio).

Modelos: `glow_panel_<tone>`, `glow_strip_<half>_<tone>`; el brillo (`light_emission` 15) va por elemento, como en la lámpara
de estado. **Pendiente de ojo en el juego**: la orientación de la tira (`facing` + `y`), el `uv` de la mitad alta y el aspecto de
las lamas del difusor; no se renderizó (clases kernel).

### `HqKit`: el mapeo por roles (para `HqStyle`)

`dev.t3craft.hq.HqKit` (paquete hq, como `HqFixtures`): `HqKit.active()` es `VANILLA` hasta que
`T3Blocks.init` instala `KitBlocks` (solo con el módulo encendido). El estilo pide un **rol** y recibe
el bloque propio o su sustituto vanilla:

| Rol (`HqKit.Role`) | Atajo | Con el módulo | Fallback vanilla (módulo apagado) |
|---|---|---|---|
| `DESK` | `desk(front)` | `standing_desk[facing]` | `cherry_slab[type=top]` |
| `WALL_PANEL` | `wallPanel(false)` | `shoji_panel[lit=false]` | `calcite` |
| `WALL_PANEL_LIT` | `wallPanel(true)` | `shoji_panel[lit=true]` | `pearlescent_froglight` |
| `FLOOR` | `floor()` | `cherry_floor_tile` | `cherry_planks` |
| `FLOOR_ACCENT` | `floorAccent()` | `stone_floor_tile` | `polished_andesite` |
| `BEAM` | `beam(axis)` | `cherry_beam[axis]` | `stripped_cherry_log[axis]` |
| `BEAM_CAP` | `beamCap()` | `beam_cap` | `polished_blackstone` |
| `LANTERN` | `lantern()` | `paper_lantern` | `waxed_copper_lantern[hanging=false]` |
| `PLAQUE` | `plaque(front)` + `plaqueData(dye, líneas…)` | `room_plaque[facing]` + texto | `cherry_wall_sign[facing]` + texto encerado |
| `RAILING` | `railing(edge)` | `cherry_railing[facing]` (extremos por `PlanConnect`) | `waxed_copper_bars` (como el `Palette` actual) |
| `GARDEN_PLANTER` | `gardenPlanter()` | `cherry_planter` | `potted_cherry_sapling` |
| `GARDEN_LANTERN` | `gardenLantern()` | `stone_lantern` | `lantern[hanging=false]` |

- `state(role, facing)`: `facing` es el frente (escritorio, placa), el borde (barandilla) o el eje
  (viga; `UP`/`DOWN` = vertical). Los demás roles lo ignoran. Puro: mismos argumentos, mismo estado.
- `plaqueData(DyeColor, String...)`: datos de block entity de la placa (o del letrero, en vanilla),
  hasta 4 líneas; la primera en el color del entorno.
- `custom()` (false en vanilla) e `id()`/`HqKit.fingerprint()` (`"vanilla"` o `KitBlocks.ID` =
  `kit-2` desde kit/cherry-railing) para los hashes de construcción: el estilo que use el kit debe incluirlo en su hash y
  subir `KitBlocks.ID` si cambia algún estado.
- Forma: cada rol tiene la misma "clase" que su fallback donde importa al caminar (suelos y paneles
  son cubos enteros; escritorio, barandilla, farol y jardín no son de paso en ninguno de los dos).

### La placa: texto cacheado por versión

`PlaqueText` (puro) guarda las líneas normalizadas (recortadas a 17 caracteres, máx. 4, sin vacías al
final) y el color de acento (los tintes demasiado oscuros se aclaran para leerse sobre la tinta). Cada
cambio real toma una **versión nueva de un contador global**, así que dos placas nunca comparten
versión (ni una placa reemplazada en la misma posición). `PlaqueRenderer` guarda los glyph runs y
anchos en un `VersionedCache` por posición: con el mismo texto, cero reconstrucciones por frame.
Datos: `{"line0": …, "line3": …, "color": 0xRRGGBB}`.

**Sustituye a los letreros de los módulos**: con el módulo encendido, `HqBlockFixtures` (ahora
`blocks-3`) convierte cada letrero de pared con texto del plan en una placa con las mismas líneas y el
color del tinte (letrero de cada módulo: proyecto, `#n`, entorno; y la cabecera "Needs attention" del
tablero). Los letreros que son **anclas** (cabecera del archivo y "older threads", que es un objetivo
de click) siguen siendo letreros vanilla. Con `HqFixtures` de tests sin `plaque` (método por defecto
null) los letreros no cambian.

### Tests

`ModBlockStatesTest` (registro real: todos los bloques del kit, ítems y todos sus estados en todas las
rotaciones pasan por el parser del sitio; roles del kit = bloques t3craft; letreros → placas salvo
anclas), `HqKitTest` (cada rol tiene fallback vanilla que parsea, orientación, placa vanilla
encerada), `PlaqueTextTest` (versión solo con cambios, global, recorte, contraste, claves),
`VersionedCacheTest` (una reconstrucción por versión) y `KitResourcesTest` (lang en_us/es_es/es_mx,
blockstates → modelos → texturas propias `kit_*`, ítems y loot tables).

### Pendiente

- **No se probó dentro del juego**: revisar a ojo los modelos (sobre todo la orientación del
  escritorio y la barandilla), el tamaño del texto de la placa y el brillo del farol vinculado.
- El estilo C (`feat/hq-style-cherry`) es quien usa `HqKit`; esta rama no toca `HqGenerator` ni
  `Palette` (salvo los letreros → placas vía fixtures).

## Cambios estructurales → **RESTART**

- Bloques: `t3craft:monitor`, `t3craft:status_lamp`, `t3craft:board_lamp`, `t3craft:decision_podium` y el kit del HQ
  (`standing_desk`, `shoji_panel`, `cherry_floor_tile`, `stone_floor_tile`, `cherry_beam`, `beam_cap`,
  `paper_lantern`, `room_plaque`, `cherry_railing`, `cherry_planter`, `stone_lantern`).
- Block entities: `t3craft:monitor`, `t3craft:status_lamp` (lámpara y bombilla).
- Ítems de bloque: `t3craft:monitor`, `t3craft:status_lamp`, `t3craft:board_lamp` (pestaña Functional
  blocks).
- Renderer: `MonitorRenderer` para `t3craft:monitor`; `PlaqueRenderer` para `t3craft:room_plaque`
  (block entity nuevo `t3craft:room_plaque`; el farol usa el tipo `t3craft:status_lamp`).
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
- feat/monitor-multiblock (**RESTART**): block states nuevos en `t3craft:monitor` (`up`, `down`, `left`,
  `right`), blockstate multipart, modelos `monitor_panel` y `monitor_edge_{up,down,left,right}`, textura
  `monitor_screen`; ticks programados del bloque; bindings nuevos `project:`/`env:`; `HqBlockFixtures.ID`
  `blocks-4`; API nueva `AnchorNames.bigScreen/parseBigScreen`, `BuildPlan.withAnchors`,
  `HqBindings.screens`/`Role.SCREEN`, `MonitorWall`, `ScreenRect`.

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
