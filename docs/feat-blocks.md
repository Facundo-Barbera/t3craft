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
  tiene (lo mantienen `ThreadShell`/`T3Connection` o el bridge). **No abre suscripciones propias.**
- `MonitorRenderer` (block entity renderer, API de render states de 26.x): en `extractRenderState`
  resuelve el binding, pide la pantalla y **solo rehace los glyph runs cuando cambia la versión**; en
  `submit` dibuja fondo (color del estado oscurecido) y líneas sobre la cara frontal, full-bright.
- Sin vincular: "T3Craft monitor · NO THREAD". Hilo desconocido: su id + "· OFFLINE".
- Click (`useWithoutItem` en el cliente) → `ThreadOpener` de la workstation
  (`WorkstationClient.get().opener()`, que respeta el override de feat/chat; por defecto
  `ChatScreenOpener`). Si el monitor está en el `computerPos` de una estación, el `UseBlockCallback`
  de la workstation lo toma antes y abre el mismo hilo: mismo resultado.
- Integración con workstation: `WorkstationClient.setScreenSuppressor(state -> …instanceof MonitorBlock)`.
  Sobre un monitor, el `TextDisplay` flotante de la estación queda vacío (una pantalla, no dos).
  Sobre cualquier bloque vanilla, todo sigue igual.

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
| `t3craft:cherry_railing` | Barandilla en el borde `facing` de la celda (postes, pasamanos, balaustres). No conecta (sin tags). | `facing` = borde |
| `t3craft:cherry_planter` | Maceta de cerezo (tiesto, tronco, copa en flor). Todo opaco: sin capas cutout. | — |
| `t3craft:stone_lantern` | Linterna de piedra (tōrō) con musgo. | luz 13 |

Ningún modelo usa transparencia (no hace falta registrar capas de render); los bloques que no son un
cubo llevan `noOcclusion` y su `VoxelShape`.

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
| `RAILING` | `railing(edge)` | `cherry_railing[facing]` | `waxed_copper_bars` (como el `Palette` actual) |
| `GARDEN_PLANTER` | `gardenPlanter()` | `cherry_planter` | `potted_cherry_sapling` |
| `GARDEN_LANTERN` | `gardenLantern()` | `stone_lantern` | `lantern[hanging=false]` |

- `state(role, facing)`: `facing` es el frente (escritorio, placa), el borde (barandilla) o el eje
  (viga; `UP`/`DOWN` = vertical). Los demás roles lo ignoran. Puro: mismos argumentos, mismo estado.
- `plaqueData(DyeColor, String...)`: datos de block entity de la placa (o del letrero, en vanilla),
  hasta 4 líneas; la primera en el color del entorno.
- `custom()` (false en vanilla) e `id()`/`HqKit.fingerprint()` (`"vanilla"` o `KitBlocks.ID` =
  `kit-1`) para los hashes de construcción: el estilo que use el kit debe incluirlo en su hash y
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

- Bloques: `t3craft:monitor`, `t3craft:status_lamp`, `t3craft:board_lamp` y el kit del HQ
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

## Pendientes

- **No se probó dentro del juego** (por regla, no se lanza Minecraft). Falta verificar a ojo: que el
  texto del monitor quede del lado correcto y no espejado (la transformación es la de AgentCraft, ya
  probada allí), el tamaño del texto, la luz de las lámparas y que el click del monitor no mande
  paquetes indebidos.
- La edad en el monitor tiene el mismo límite que la workstation (sin timestamp en `NpcInfo`).
- Nadie llama todavía a `setPositionBindings`; el dueño del HQ en el mundo (site/builders) puede
  hacerlo con `HqBindings.world(plan, site)`.
