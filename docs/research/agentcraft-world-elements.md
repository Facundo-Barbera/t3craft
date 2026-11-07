# Elementos de mundo de AgentCraft: catálogo, comparación y propuesta

Rama `research/agentcraft-world-elements`, 2026-11-07. Solo investigación: no se tocó código del mod.

**Fuentes**
- **AgentCraft**: `github.com/blendi-remade/agentcraft` @ `8fe3ad23489b06f9142c6382bc23d31090c6b406`, MIT.
  Clonado en `/tmp/agentcraft-world` y leído sin ejecutar nada. Abreviaturas: `M/` = `mod/src/main/java/dev/agentcraft`,
  `C/` = `mod/src/client/java/dev/agentcraft/client` y `R/` = `mod/src/main/resources/assets/agentcraft`.
- **t3craft**: `main` @ `f830eb4`. Leí `docs/feat-*.md`, `docs/research/{inventario,compat}.md`,
  `THIRD_PARTY_NOTICES.md` y los paquetes `blocks`, `client.blocks`, `hq`, `client.hq`, `client.npc{,.behavior,.helpers}`,
  `client.workstation`, `client.chat`, `client.portable{,.held}`, `client.hud`, `client.demo` y `site`.
  Abreviaturas: `t/` = `mod/src/main/java/dev/t3craft` y `tc/` = `mod/src/client/java/dev/t3craft/client`.

Leyenda de scope: **R** = `orchestration:read` (lo que ya da el pairing). **W** = `orchestration:operate`
(infra en main: `OperateGate`, `docs/feat-operate.md`). **SC-W** = `source-control:write` (fuera de alcance).

---

## 0. Cómo funciona por dentro (el patrón común de AgentCraft)

Todos los elementos "vivos" de AgentCraft siguen el mismo esquema, y conviene entenderlo antes de las tablas:

| Pieza | Archivo | Qué hace |
|---|---|---|
| Fuente de datos | `C/foreman/ForemanLink.java`, `ForemanState.java`, `Protocol.java` | WebSocket al Foreman (Node): `hello → snapshot → upserts` (agents, tasks, decisions, goal, feed, `agent.log`). El estado vive **solo en el cliente**. |
| Cambios | `C/foreman/ForemanListener.java` | Callbacks `onSnapshot/onAgent/onTask/onLog/onFeed/onGoal`. Cada feature sube un contador de secuencia (`MonitorFeature.LOG_SEQ`, `TaskWallFeature.taskSeq`) y el BER **solo rehace el layout si cambió la secuencia**. |
| Binding | `M/block/entity/StationBlockEntity.java` | String `binding` en el block entity (`"kit"`, `"agent:kit"`, `"ci:#1"`, `"goal:atrium"`, `"decisions"`, `"merge"`, `"beacon"`, `"shared"`). Se guarda con el mundo y se sincroniza con `getUpdatePacket`/`getUpdateTag`. Lo pone el builder del HQ (`Plan.bind`). |
| Dibujo | `C/world/StationRenderer.java` (base de los 7 BER) | Extrae binding, facing, origen y tamaño del panel conectado; `toFace()` da un "espacio de píxeles de pantalla" sobre la cara del bloque. Lee el modelo en `extract`, nunca en `submit` (API de render states de 26.x). |
| Estado de bloque | `C/hq/HqWorldDriver.java` + `ServerTasks` | El cliente calcula el estado deseado (`Wanted`: lámparas, podio `open`, merge `active`, monitor `lit`, copper bulbs) y el **servidor integrado** lo aplica solo donde difiere. Se recalcula en cada cambio y se reaplica cada 40 ticks. Con el enlace caído, conserva el último estado. |
| Clic | `C/world/StationInteractions.java` | `onUse(block, handler)`: el clic se consume en el cliente y abre una pantalla; nunca va al servidor. |
| Texto en el mundo | `C/ui/WorldUi.java`, `C/monitor/DisplayDraw.java` | Capas SOLID/BASE/OVERLAY, nine-slice de sprites del kit en el mundo, una sola `submitCustomGeometry` por pantalla, pasos de z explícitos y full-bright. |
| Costo | `C/monitor/DisplayStats.java` | µs por frame de monitores y tablero, consultable por DevBridge. |

t3craft ya copió la base (`BoundBlockEntity` ← `StationBlockEntity`, `MonitorRenderer.toFace` ← `StationRenderer`,
`LampApplier` ≈ `HqWorldDriver`). Lo que falta es casi todo lo que se construye **encima**.

---

## 1. Catálogo completo de AgentCraft

### 1.1 Bloques (16, `M/block/ModBlocks.java`) y block entities (7, `M/block/entity/ModBlockEntities.java`)

| Bloque | BE / BER | Qué hace y qué muestra | Cómo se actualiza |
|---|---|---|---|
| `monitor` | `MonitorBlockEntity` / `C/monitor/MonitorRenderer.java` (+ `MonitorFeature`, `MonitorScreen`, `LogRows`, `ScreenStyle`, `DisplayDraw`) | **Pantalla multibloque** (`PanelBlock`: `up/down/left/right` conectan N×M monitores con el mismo facing y quitan el bisel). Dibuja una vez desde el bloque origen (`PanelBlock.origin/extent`). Muestra el **log en vivo** del agente: cabecera (punto de estado, nombre, actividad, id de tarea), filas de tool calls con icono, rutas y diffs rojo/verde, cursor parpadeante mientras trabaja, "waiting for you", y estados OFF_SHIFT, NO_AGENT, CONNECTING y "enlace perdido" (último log atenuado con badge). Las filas nuevas entran deslizándose (260 ms) y la de arriba se desvanece. Densidad de 128 px/bloque (1 de alto) o 96. Dos looks: PAPER y DARK. `lit` → luz 7. Binding vacío = el agente del ancla `monitor_<id>` que cae en el panel; si no hay, el **feed del equipo** (`feed`). | `agent.log` del Foreman → `LOG_SEQ`/`AGENT_SEQ` → `MonitorScreen.sync()` solo si cambió. `lit` lo pone `HqWorldDriver` (agente en turno). Barrido cada 200 ticks de las pantallas sin usar durante 30 s. |
| `task_board` | `TaskBoardBlockEntity` / `C/taskwall/TaskBoardRenderer.java` (+ `TaskBoard` 1069 líneas, `TaskWallFeature`, `TaskScreen`) | **Tablero kanban multibloque** (el mismo `PanelBlock`; en el HQ, 7×4). Columnas Todo/Doing/Review/Done con conteos y anchos según el contenido. Tarjetas del kit por estado (`card_*.png`); las bloqueadas son rojas y van arriba con el motivo. Muestran título, cara y nombre del asignado, punto de estado en vivo y una pista al pie (bloqueo, CI fallando, deps, prioridad). Las tarjetas **vuelan entre columnas** y brillan al llegar; las que sobran se agrupan en "+N more". Hover con contorno de latón (`LevelRenderEvents.BEFORE_BLOCK_OUTLINE`). Clic en una tarjeta → `TaskScreen` (Retry/Prioritize/Reassign/Cancel). | `onTask`/`onSnapshot` → `taskSeq`; `onAgent` → `agentSeq`. `TaskBoard.step(now)` anima cada frame. Toma la luz del cuarto, con un mínimo de 13. |
| `decision_podium` | `DecisionPodiumBlockEntity` / `C/decisions/DecisionPodiumRenderer.java` | Atril. `open=true` enciende papel, lente y campana (luz 9). Mientras hay decisiones, **sube un bocadillo flotante (billboard)**: conteo con punto clay pulsante y tecla, la cara y el nombre del agente de la primera decisión y su pregunta en 2 líneas. Clic → `DecisionScreen`. | `open` lo pone `HqWorldDriver`; el bocadillo lee `Foreman.state().openDecisions()` en cada extract. |
| `merge_station` | `MergeStationBlockEntity` / `C/diff/MergeStationRenderer.java` | Mesa de revisión con incrustación de latón. `active` cuando espera un merge (luz 6). Encima, una **tarjeta de papel apoyada**: cara y nombre del worker, tarea, título, +/- y número de archivos, punto de CI y punto clay pulsante. **Una fila de estaciones es una cola**: la k-ésima muestra el k-ésimo merge pendiente, y la primera lleva el conteo o "all clear". Clic → `DiffScreen`. | Lee las decisiones de tipo merge en cada extract; `active` lo pone el driver. |
| `status_lamp` | `StatusLampBlockEntity` / `C/hq/StatusLampRenderer.java` | Lámpara con `status` = OFF/IDLE/THINKING/WORKING/WAITING/ERROR/DONE (`M/block/LampStatus.java`) y luz 12. Texturas `status_lamp_<estado>{,_emissive}.png`. El BER añade lo que un block state no puede: **WAITING respira** (brillo clay aditivo a ~0,8 Hz), **marco de luz en el nicho** alrededor de las lámparas `decisions`/`merge` (del suelo hasta 3 bloques), **faro de la cúpula** (`beacon`: halo del color de estado en las 4 caras, visible desde lejos) y **holograma del atrio** (abajo). | Bindings `agent:<id>`, `ci:<repo>`/`ci:#n`, `goal`, `goal:atrium`, `decisions`, `merge` y `beacon`. El estado lo pone `HqWorldDriver.tick` y el brillo se anima con el tiempo. |
| `console_terminal` | `ConsoleTerminalBlockEntity` / `C/console/ConsoleTerminalRenderer.java` | Terminal con la pantalla inclinada 22,5°. Es una **mini-consola en vivo**: banda de título con el conteo de decisiones, tarjeta de noticias (el último ítem del feed con franja de color, cara, nombre y 2 líneas; pagina por los últimos ítems) y prompt con tu borrador o "Enter to type" con caret. Clic → `ConsoleScreen`. Luz 6. | Feed del Foreman en extract, más tiempo para el paginado y el caret. |
| `memory_archive` | `MemoryArchiveBlockEntity` / `C/library/MemoryArchiveRenderer.java` | Estantería. **Una fila de archivos es una sección**: el primero lleva una placa con el scope y el número de notas, y cada bloque siguiente rotula una nota (el plan primero) con una pestaña del color del autor y una pluma si no la leíste. Clic → `LibraryScreen` en ese scope o nota. | `MemoryIndex` del Foreman en extract. |
| `memory_catalog` | — (`FacingBlock`) | Fichero decorativo de la biblioteca. | Estático. |
| `glow_panel` | — | Panel de luz 15 (emisivo). | Estático. |
| `glow_strip` | — (`GlowStripBlock`) | Tira de luz de 2 px en suelo, techo o pared, con `facing` + `axis` y luz 12; sin colisión. | Estático. |
| `plaster_panel`, `plaster_frame`, `walnut_panel`, `walnut_trim`, `terracotta_tile`, `oak_parquet` | — | Materiales decorativos del estilo "Warm Studio". | Estáticos. |

Ítems: solo los 16 block items y una pestaña creativa propia (`M/block/ModItems.java`). No hay ítems sueltos, ni
recetas, ni GUIs de contenedor (todas las pantallas son de cliente).

### 1.2 Hologramas y proyecciones flotantes

| Elemento | Archivo | Qué muestra | Cómo se actualiza |
|---|---|---|---|
| **Holograma del Goal Atrium** | `StatusLampRenderer.extractHologram/beam/card` (binding `goal:atrium`) | Tarjeta flotante sobre el plinto del atrio: texto del goal en 3 líneas, palabra de estado coloreada (PLANNING/ACTIVE/DONE/FAILED), **anillo de progreso** (`Kit.progressRing`, 17 sprites `progress_ring_00..16`) con "2/9" dentro, "N of M tasks done · k in progress", "n agents working · r in review · w need you". **Haz de luz** suave desde la lente hasta la tarjeta. Sin goal: "What should the team work on? / Press ` and type a goal". Offline: "Foreman offline · last known state". Reserva espacio en `PlateLayout` para que los nameplates no queden detrás. | Cada extract, desde `Foreman.state()` (goal, tasks, agents, decisions). `shouldRenderOffScreen` y vista a 96 bloques. |
| Bocadillo del podio | `DecisionPodiumRenderer` | Ver 1.1. | Ídem. |
| Tarjeta de la merge station | `MergeStationRenderer` | Ver 1.1 (es una tarjeta "apoyada", no un billboard). | Ídem. |
| Faro de la cúpula | `StatusLampRenderer.beacon` + `HqWorldDriver.beaconLamp` | Estado agregado del estudio, lo más urgente primero (waiting > error > working > …). | Driver más el BER. |
| Marco de luz del nicho | `StatusLampRenderer.frame` | Línea clay con halo que respira alrededor del nicho del podio o de la merge station. | BER. |

### 1.3 NPCs: render, animaciones, nameplates, partículas

| Elemento | Archivo | Qué hace | Actualización |
|---|---|---|---|
| Entidad y render | `C/agents/ClientAgentEntity.java`, `AgentRenderer.java`, `AgentModel.java`, `mixin/EntityRenderDispatcherMixin.java` | Modelo de jugador (slim o wide) con 6 skins propias (`R/textures/entity/agent/*.png`). El mixin elige el renderer por skin. | Tick de cliente. |
| **Posturas** | `C/agents/AgentLife.java` (781 líneas) | Sentado tecleando, leyendo un libro abierto (en la mano), apoyado en el banco de tests, mano en la barbilla al pensar, rascarse la cabeza en error, estirarse; la cabeza mira al monitor, al libro, a quien le habla o a ti. Canales de pose interpolados, deterministas por agente y sin asignar memoria por frame. | 20 Hz + interpolación por frame. |
| Asientos | `C/agents/Seats.java` | Escaleras inferiores, losas o bloques de 0,3–0,7 de alto en el ancla = silla; el agente se sienta mirando al yaw del ancla. El bloque de enfrente es la mesa y define el alcance de los brazos. | Caché por ancla, revalidada cada pocos segundos. |
| **Partículas de estado** | `C/agents/AgentParticles.java` | Pensando: estrellas de latón (sprite `glow`). Trabajando: motas teal que suben de las manos (`glitter`). Error: bocanada roja y luego una cada pocos segundos. Done: confeti sage/cream/brass/clay. Pool fijo por agente, como mucho 1 partícula cada 0,4 s, **sprites vanilla teñidos** con la paleta y un solo batch de quads. | Simuladas por tick, dibujadas por frame. |
| Nameplate | `C/agents/Nameplate.java` | Píldora opaca (kit `nameplate`) con punto de estado, nombre del color del agente y línea de actividad; halo clay pulsante si espera. Variante compacta. Chip de modelo. | Caché por texto. |
| **Declutter** | `C/agents/PlateLayout.java` (732 líneas) | Proyecta los plates a pantalla, colapsa los de poco valor, eleva los que se solapan por prioridad (crosshair > waiting > error > working…) con **línea guía** a la cabeza y les da un empujón de profundidad por rango. | Una vez por frame (`LevelExtractionEvents.END_EXTRACTION`). |
| Pila sobre el plate | `C/agents/PlateStack.java` | Bocadillo encima y, más arriba, la "!" clay pulsante. Reserva su altura. | Extract. |
| Bocadillo | `C/agents/SpeechBubble.java` | Nine-slice `bubble` + cola, 3 líneas, el destinatario ("@Juniper") coloreado; aparece y se desvanece en 3,5–11 s según el largo. | Evento `agent.say`. |
| Movimiento | `C/agents/GridPathfinder.java`, `AgentMotion.java`, `StationAssigner.java` | A*, paso tranquilo y una estación por agente. | Tick. |
| Ficha | `C/agents/AgentCardScreen.java` | Estado, tarea, logs y acciones (Message, Pause, Stop). | Pantalla. |

### 1.4 HQ, decoración y efectos del mundo

| Elemento | Archivo | Qué es |
|---|---|---|
| Edificio | `M/hq/StudioHqBuilder.java` (1330 líneas) | Nave con entramado de nogal, techos de cobre encerado, buhardillas y un **atrio octogonal con domo y linterna**. Escritorios (monitor + `status_lamp` `agent:<id>` encima), biblioteca (`memory_archive` + `memory_catalog`), taller con lámparas `ci:#n` y `console_terminal`, lounge con lámparas de pie (poste + farol), sala de reuniones y fila de `merge_station` con lámpara `merge`. En el atrio: plinto del holograma, task wall 7×4 con capucha y luz oculta, podio en un nicho enmarcado, bancos, maceteros, cornisa con luz oculta y **candelabro de anillo** sobre el holograma. Anclas de cámara `cam_*`. |
| **Signal bulbs** | `HqWorldDriver` + `StudioHqBuilder` | Copper bulbs **vanilla** a ≤3 bloques del podio o de una merge station, encendidas sin redstone mientras esa estación te necesita. |
| Partículas ambiente | `C/hq/HqClientFeature.java` | Motas `DustParticleOptions` clay que suben de las lámparas en espera y del podio abierto. |
| Paisaje | `M/hq/HqLandscape.java` (800 líneas) | Hondonada con colinas boscosas, terrazas, pórtico, caminos con faroles hasta una puerta techada (lychgate), estanque, jardín, árboles y pradera, todo con hash de coordenadas (determinista). |
| Diff del HQ | `M/hq/Plan.java`, `PlanStore.java` | Construir como diff de una caja; respeta los cambios del jugador. |
| Verificación | `C/hq/HqCheck.java` | A* a cada ancla, rutas sobre muebles y niveles de luz interior. |

### 1.5 HUD, sonidos y GUIs

| Elemento | Archivo | Qué hace |
|---|---|---|
| Barra del goal | `C/hud/GoalBar.java` | Estilo boss bar: goal, %, barra y conteos; badge "2 waiting · J". |
| Pill de conexión | `C/hud/ConnectionBanner.java` | Conectado / reconectando / sin Foreman / fallo de auth. |
| Toasts | `C/hud/Toasts.java` | Toasts de papel con **retrato enmarcado** (`R/textures/gui/portrait/*_framed.png`), franja clay y tecla en los "need you"; máx. 3; se van al responder. |
| Sonidos | `C/hud/HudSounds.java` | **Solo vanilla** (`forUI`): campana `NOTE_BLOCK_BELL` al abrir una decisión, `NOTE_BLOCK_CHIME` al terminar una tarea, `AMETHYST_BLOCK_CHIME` + chime para el goal. Solo en upserts en vivo, máx. 1 por tipo cada 1,5 s, `/sound off`. No hay `sounds.json` ni .ogg propios. |
| GUIs | `C/console/ConsoleScreen`, `C/decisions/DecisionScreen`, `C/diff/DiffScreen`, `C/library/LibraryScreen`, `C/taskwall/TaskScreen`, `C/agents/AgentCardScreen` | Ya analizadas en `inventario.md`; aquí solo importan como destino del clic de cada bloque. |

### 1.6 Assets

| Tipo | Dónde | Notas |
|---|---|---|
| Texturas de bloque (53) | `R/textures/block/` | Monitor (marco, pantalla on/off, glint), lámparas ×7 estados + emisivas, podio (+ lit/emissive), merge station, consola, archivo, task board, glow, materiales. |
| Modelos (60) | `R/models/block/` | Incluye `monitor_edge_{up,down,left,right}` para los biseles conectables (multipart). |
| Sprites del kit (114) | `R/textures/gui/sprites/kit/` | Paneles, tarjetas `card_*`, `progress_ring_00..16`, `progress_fill_*`, `nameplate`, `bubble`, dots, iconos, botones, tabs. |
| Retratos y skins | `R/textures/gui/portrait/`, `R/textures/entity/agent/` | Los 6 personajes. |
| Estilo | `R/gui/ui-style.json`, `kit.json`, `R/palette.json`, `cast.json` | Tokens "Warm Studio". |
| Pipeline | `assets-src/gen/*.py`, `assets-src/blender/*.py` | Generadores deterministas (Pillow) y renders de verificación en Blender. |

---

## 2. Comparación con t3craft (main @ `f830eb4`)

| Elemento AgentCraft | t3craft | Equivalente nuestro / nota |
|---|---|---|
| Binding en block entity + sync | ✅ | `t/blocks/BoundBlockEntity.java`, `Binding` (`thread:<npcKey>`, `board:<i>`), `HqBindings` |
| Interruptor del módulo | ✅ (t3craft va más allá) | `BlocksSwitch` + `T3Blocks.init`; AgentCraft no tiene modo sin bloques |
| `monitor` de 1 bloque | 🟡 | `t/blocks/MonitorBlock` + `tc/blocks/MonitorRenderer`: título, estado, edad y último mensaje (22 col × 3 líneas, `MonitorScreens` reusa `WorkstationModel`). Sin log en vivo, sin tool calls, sin animación, sin estado `lit` |
| **Monitor multibloque (N×M, `PanelBlock`)** | ❌ | Ningún bloque nuestro conecta con sus vecinos |
| Log en vivo en el monitor (tool calls, diffs) | ❌ | Los datos existen (`subscribeThread` vía `t/t3/StreamRegistry` refcount, `chat/ToolLine`, `TurnItems`), pero el monitor no los usa |
| Feed del equipo en un monitor | ❌ | — |
| Workstation (pantalla por escritorio) | ✅ | `tc/workstation/WorkstationRenderer` (TextDisplay) + `HqStations`; con bloques la sustituye el monitor (`setScreenSuppressor`) |
| `status_lamp` por hilo | ✅ | `t/blocks/StatusLampBlock` + `LampApplier`/`LampSignals` (6 estados: working/attention/error/done/idle/off) |
| Bombillas del tablero | ✅ (propio) | `board_lamp` `board:<i>`: la i-ésima bombilla = i-ésimo hilo que te necesita |
| Lámpara que **respira** en espera / marco de nicho | ❌ | Las lámparas son solo block state, sin BER |
| **Faro agregado** (cúpula) | ❌ | El hub tiene una linterna acristalada (`HqGenerator`), pero no muestra nada |
| **Holograma del atrio** (progreso, conteos) | ❌ | Lo más parecido es `tc/hud/StatusBar` (HUD) |
| **Task wall kanban** | 🟡 | Tablero "Needs attention" vanilla (pizarra + cartel + bombillas) en `HqGenerator.board`; las NPC van ahí en ATTENTION (`BoardSlots`). Sin tarjetas ni texto. Esqueleto `task_board` descrito en `feat-blocks.md` §"Esqueleto" |
| `decision_podium` + bocadillo | ❌ | Esqueleto en `feat-blocks.md`. Hoy, "needs you" se ve en la NPC (Emote `EXCLAIM`), el chat y el HUD |
| `merge_station` (cola de reviews) | ❌ | — (no hay merge local en T3; el equivalente sería **PRs**) |
| `console_terminal` | 🟡 | El terminal portátil (`tc/portable`, `held/TerminalScreenRenderer`) cumple esa función en la mano. No hay bloque |
| `memory_archive` (estante con placas) | 🟡 | Ranuras de archivo `archive_<threadId>` → monitor (`HqBindings`); la estantería está pendiente en fix/hq-archive (`feat-hq-construction.md`) |
| Signal bulbs (copper bulb vanilla) | 🟡 | Usamos copper bulbs en escritorios y tablero, pero las movemos con nuestros bloques, no con bulbs vanilla |
| Partículas ambiente en lámparas | ❌ | — |
| Bloques decorativos (glow, plaster, walnut…) | ❌ (a propósito) | `hq/Palette` usa vanilla: correcto para compat |
| Partículas de estado de NPC | 🟡 | `NpcRegistry` (ENCHANT/NOTE/ANGRY_VILLAGER/HAPPY_VILLAGER), `BehaviorDirector` (festejo con FIREWORK), `HelperDirector` (POOF). Partículas vanilla sin teñir y sin pool |
| Posturas (sentado, leyendo, pensando) | 🟡 | `NpcBrain`/`BehaviorDirector`: caminar, swing de teclear (`Hand`), deambular, festejo. **No se sientan** ni hay poses de brazos (RemotePlayer sin modelo propio) |
| Nameplate + declutter + línea guía | 🟡 | `tc/npc/Nameplate`, `NpcLabels` (tag lejano + panel cercano con histéresis), chips. Sin declutter entre NPCs |
| Bocadillo y "!" | ✅ | `BubbleText`, `Emote` (glifos ⌨ ! ✖ ★ ♪ z) |
| Subagentes | ✅ (solo t3craft) | `tc/npc/helpers/*`: aldeanos ayudantes alrededor del padre |
| Barra de progreso en el HUD | ✅ | `tc/hud/StatusBar`/`StatusBarModel` ← `GoalBar` (ya atribuido) |
| Pill o banner de conexión | ✅ | `PeekHud` (banner de entornos) |
| Toasts con retrato | 🟡 | `tc/hud/Toasts` usa toasts de sistema vanilla |
| Sonidos | ✅ | `tc/hud/Notifier` (bell/bass/levelup). Falta el límite de 1 por tipo y el "no sonar en el snapshot" (ya hay siembra silenciosa) |
| Clic en bloque → pantalla | ✅ | Monitor → `ThreadOpener` (chat) |
| Hover con contorno en las tarjetas | ❌ | — |
| Pantalla en el ítem en la mano | ✅ (solo t3craft) | `held/TerminalScreenRenderer` |
| Paisaje alrededor del HQ | 🟡 | feat/site trabaja sobre terreno real; demo superflat (`tc/demo`) |
| Métricas de costo de render | ❌ | — |

---

## 3. Propuesta para lo que falta o está parcial

Columnas: **Datos T3** (qué los alimenta y con qué scope), **Esf.** (S, M o L), **RESTART** (registros, BER o
entrypoints nuevos) y **Módulo** (¿cabe en el módulo de bloques desactivable?). Regla general: todo lo que es
**bloque o BER** va dentro de `T3Blocks.init`/`BlocksClient` y desaparece con `blocks.enabled=false`. Todo lo que es
**proyección flotante** puede hacerse **sin bloque** (render en `LevelRenderEvents` sobre un ancla del HQ, o
`TextDisplay` de cliente como la workstation), y entonces funciona también en el modo "solo GUI".

| # | Propuesta | Datos T3 (scope) | Esf. | RESTART | Módulo |
|---|---|---|---|---|---|
| P1 | **Monitor multibloque**: `connect`/`origin`/`extent` como `PanelBlock` (props `up/down/left/right` + modelos de bisel por lado); el BER dibuja desde el origen con densidad según el tamaño | Igual que hoy (`NpcRegistry`) | M | Sí (props de block state nuevas, modelos multipart) | ✅ dentro |
| P2 | **Log en vivo en el monitor**: tail de turnos y tool calls (icono, ruta, +/-) reusando `ToolLine`/`TurnItems` del chat; cursor si trabaja; "needs you" si hay pending | `subscribeThread` por `StreamRegistry` (refcount, **R**); subscribirse solo a los monitores a < N bloques y visibles, para no multiplicar streams; `subscribeCommandOutput` opcional (**R**) para la salida de comandos | M | No (mismo BER), salvo que se combine con P1 | ✅ dentro (los datos son de cliente) |
| P3 | **Pared de hilos** (task wall en versión T3): kanban de hilos por estado (Working / Needs you / Error / Done) con tarjetas del kit, tarjetas que vuelan, "+N more", clic → chat, hover. Reemplaza la pizarra vanilla del hub (o se le pone encima). Variante: columnas por proyecto o entorno | `subscribeShell` (**R**): estado, título, `pendingRuntimeRequest`, `latestVisibleMessage`, modelo; cola: `queuePosition` en `runs` (**R**) | L | Sí (bloque `t3craft:thread_board` + BER) | ✅ dentro. Sin módulo: el tablero vanilla actual sigue igual |
| P4 | **Holograma del hub**: tarjeta flotante sobre el hub con conteos agregados (n trabajando · n needs you · n error · n done hoy), un anillo por entorno o proyecto, el hilo más urgente y el haz de luz | `subscribeShell` (**R**) de todos los entornos; opcional `scheduledTasks.subscribe` (**R**) para "próxima tarea" | M | **No si se hace sin bloque** (render de cliente en el ancla `hub` o `board` del HQ de `HqPresence`). Sí si va en un BER de lámpara | ✅ **fuera del módulo**: es de cliente puro y vale en modo GUI-only. Opcional por config (`hud.hologram`) |
| P5 | **Faro de la linterna** del hub: estado agregado (needs you > error > working > idle) visible desde lejos; respira en espera | `subscribeShell` (**R**) | S | Con BER de `status_lamp` (binding `beacon`): sí, por registrar el renderer. Sin bloque (render de cliente en el ancla `hub`): no | ✅ ambas formas |
| P6 | **Lámparas que respiran** + motas clay en las lámparas `attention` y en el tablero | Igual que hoy | S | Sí (BER nuevo para `status_lamp`) | ✅ dentro |
| P7 | **Podio "Needs you"**: bocadillo flotante con el conteo y la primera pending request (hilo, tipo, resumen); clic → pantalla "Needs you". Responder: botones Allow/Deny con `OperateUi.guard` | Ver: `pendingRuntimeRequest`/`runtimeRequests` (**R**). Responder: `runtime-request.respond` (**W**, `REQUEST_DENY` destructivo) | M | Sí (bloque + BE + BER) | ✅ dentro. Fuera del módulo, el bocadillo puede colgar de un ancla (`podium`) sin bloque |
| P8 | **Estación de PRs** (equivalente a la merge station): fila de mesas, la k-ésima muestra el k-ésimo PR abierto de los hilos (número, estado, checks, +/-); clic → pantalla de diff | `linkedPullRequest`/`pullRequests` en el shell, `pullRequests*` (**R**); diff: `getFullThreadDiff`/`/api/pull-requests/diff` (**R**). Merge: **SC-W**, fuera de alcance | L | Sí | ✅ dentro |
| P9 | **Tablero de tareas programadas** (el esqueleto `task_board` de `feat-blocks.md`): próximas ejecuciones y la última con su resultado | `scheduledTasks.subscribe` (**R**, por `T3Connection`); "ejecutar ahora": **W** | M | Sí | ✅ dentro |
| P10 | **Estante del archivo** con placas (como `memory_archive`): una fila por proyecto, una pestaña por hilo archivado; clic → chat (solo lectura) | `getArchivedShellSnapshot`, `getOlderSettledThreads` (**R**) | M | Sí | ✅ dentro; depende de fix/hq-archive |
| P11 | **Partículas de estado teñidas y con pool** (pensando = estrellas, trabajando = motas, error = bocanada, done = confeti) en vez de las vanilla actuales | Estado actual | S | No | ✅ fuera del módulo (cliente puro) |
| P12 | **Sentarse a teclear**: usar `Seats` (escalera o losa en el ancla del escritorio) y bajar al NPC; pose de brazos limitada sin modelo propio (RemotePlayer) | — | M (sentarse) / L (posturas con modelo propio + mixin) | No / Sí con mixin | ✅ fuera del módulo |
| P13 | **Declutter de nameplates** (`PlateLayout`) entre NPCs cercanos | — | M | No | ✅ fuera del módulo |
| P14 | **Terminal de consola** (bloque): pantalla del último aviso de `NotificationLog` + "Enter to type"; clic → `ThreadBrowser`; "nuevo hilo" con **W** (`launchThread`) | `subscribeShell` (**R**); crear: **W** | M | Sí | ✅ dentro (el portátil cubre el modo GUI) |
| P15 | Toasts con retrato y franja (kit) | Estado actual | S | No | ✅ fuera |
| P16 | Métricas de render (`DisplayStats`) en `/t3dev` | — | S | No | ✅ |

**Riesgo transversal**: P2 y P3 son los que abren streams. La regla de `api.md` ("nada de sockets propios") ya se
cumple con `StreamRegistry`; falta una **política de visibilidad** (subscribirse solo a lo que se ve y soltarlo a
los N segundos de dejar de verlo, como hace `MonitorFeature` con su barrido de 30 s).

---

## 4. Licencia

| Qué | Licencia y aviso | ¿Se puede copiar o adaptar? |
|---|---|---|
| Código Java y Node | `LICENSE`: MIT, "Copyright (c) 2026 AgentCraft contributors" | Sí, conservando el aviso de copyright y el texto MIT en las copias o porciones sustanciales |
| Texturas, skins, sprites y modelos | README §License: "All art (skins, block textures and UI sprites) is generated by the scripts in `assets-src/` and is covered by the same license" | Sí, bajo MIT. Los generadores (`assets-src/gen/*.py`) también son MIT: se pueden adaptar para generar arte propio con el mismo estilo |
| Sonidos | No hay sonidos propios: solo `SoundEvents` vanilla referenciados | Nada que copiar; referenciar sonidos vanilla está bien |
| Sprites de partículas, `stripped_oak_log`, fuente | Vanilla, **referenciados** en runtime (no se distribuyen) | Igual: referenciar, nunca copiar PNGs de Mojang. `assets-src` extrae referencias del jar solo para hojas de revisión y no las publica; no copiar esa carpeta (`artifacts/art/_ref`) |
| Personajes (Marlow, Juniper…), retratos, "Powered by Claude" | MIT a nivel de copyright, pero son la identidad del proyecto; marcas de Anthropic | **No reutilizar** personajes, nombres ni branding: nuestros NPCs son hilos con skins vanilla |

**Atribución exigida**: el aviso MIT. t3craft ya lo cumple bien en `THIRD_PARTY_NOTICES.md`: cabecera
`// Adapted from AgentCraft … MIT License.` en cada archivo adaptado, lista archivo-por-archivo con origen y commit,
y texto MIT completo. Para lo nuevo, conviene:
- Seguir **listando cada archivo** (p. ej. `PanelBlock` → nuestro bloque conectable, `TaskBoard` → layout del
  tablero, `AgentParticles`, `PlateLayout`) con el commit `8fe3ad2`.
- Para **texturas o modelos copiados** (monitor_edge_*, card_*, progress_ring_*), una entrada como la de los sprites
  del chat ("copied unchanged" o "adapted") y su ruta destino.
- Si se adapta un **generador Python**, ponerlo en `tools/` con la misma cabecera MIT.

---

## 5. Top recomendado (valor contra esfuerzo)

| # | Ítem | Por qué | Esf. | Depende de |
|---|---|---|---|---|
| 1 | **P2 Log en vivo en el monitor** (con política de visibilidad) | Es lo que más "vive" en AgentCraft y ya tenemos los datos (`StreamRegistry`, `ToolLine`) y el BER | M | `blocks` en main ✅, chat en main ✅ |
| 2 | **P4 Holograma del hub** (cliente puro) | Alto impacto visual, funciona sin bloques (vale en GUI-only) y usa solo el shell | M | `HqPresence` (ancla del hub) |
| 3 | **P1 Monitor multibloque** | Habilita "pantallas grandes" (feed del entorno en una pared de 3×2) y combina con P2 | M | P2 recomendable antes; RESTART |
| 4 | **P6 + P5 Lámparas que respiran y faro de la linterna** | Barato y se lee desde lejos | S | `status_lamp` ✅ |
| 5 | **P3 Pared de hilos (kanban)** | Sustituye el tablero vanilla por información real y es el destino natural de las NPC en ATTENTION | L | P1 (reusa conexión y origen); `HqBindings` |
| 6 | **P7 Podio "Needs you"** | Cierra el ciclo de atención; con operate responde en el mundo | M | Pantalla "Needs you" (inventario ítem 4); operate ✅ para responder |
| 7 | **P11 Partículas teñidas con pool** | Pulido barato del lenguaje visual, sin RESTART | S | — |
| 8 | **P12 Sentarse a teclear** | Gran mejora de "vida" en los escritorios | M | Sillas en el `HqGenerator` (ya hay silla por escritorio) |
| 9 | **P10 Estante del archivo** | Da sentido al archivo | M | fix/hq-archive |
| 10 | **P8 Estación de PRs** | Valioso si se usan PRs a menudo; solo lectura | L | Badge de PR (inventario ítem 10), pantalla de diff (ítem 6) |

Todo lo que es bloque o BER (P1, P2, P3, P5 y P6 vía BER, P7–P10, P14) es **RESTART** y va dentro del módulo
`blocks`. P4, P11, P12, P13 y P15 son de cliente, **no requieren RESTART por registros** (P4 sí si añade un
entrypoint o evento nuevo) y siguen funcionando con los bloques apagados.
