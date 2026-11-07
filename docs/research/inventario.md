# Inventario de brechas: t3craft vs AgentCraft vs T3 Code

Rama `research/inventario`, fecha 2026-11-07. Solo investigación: no se tocó código del mod.

**Fuentes**
- **t3craft**: `main` @ `f6364f7` y ramas en curso, revisadas con `git log`/`git show` y sus docs. `feat/workstation` y `feat/demo-world` todavía no tienen commits propios (están en `f6364f7`, con el worktree limpio). `feat/portable` tiene WIP sin commitear en su worktree; solo cuento lo commiteado (`8b6d516`).
- **AgentCraft**: `github.com/blendi-remade/agentcraft` @ `8fe3ad2`, MIT. Se clonó en `/tmp/agentcraft-inv` y se leyó sin ejecutar nada. Abreviaturas de rutas: `M/` = `mod/src/main/java/dev/agentcraft`, `C/` = `mod/src/client/java/dev/agentcraft/client` y `F/` = `foreman/src`.
- **T3 Code**: contratos en `~/projects/t3code-ref` @ `15fe2832`, sobre todo `packages/contracts/src/orchestrationV2.ts` y `rpc.ts`. Los scopes salen de `apps/server/src/auth/RpcAuthorization.ts` (`RPC_REQUIRED_SCOPES`). También usé la CLI local `t3 v0.1.4-nightly` (solo `--help`) y `docs/api.md` y `docs/plan-v2.md` de t3craft. El VPS corre `0.1.8-nightly`, así que puede haber diferencias menores.

Leyenda: **R** = `orchestration:read` (lo que ya tiene el pairing). **W** = `orchestration:operate`. Los demás scopes van con su nombre.

---

## 1. Tabla comparativa

### UI y chat

| Tema | t3craft hoy | AgentCraft | T3 Code (scope) |
|---|---|---|---|
| Historial del hilo | main: `ThreadChatScreen` es un placeholder. **rama feat/chat (en curso)**: `ChatScreen` con markdown, tool calls plegables, carga incremental y datos en vivo vía `subscribeThread` + `GET …/history` | No hay historial por agente. Hay un feed global en la consola (`C/console/ConsoleScreen.java`) y logs en los monitores | `subscribeThread`, `getThreadProjection`, `getTurnItem` y HTTP `/threads/:id/{history,bounded,transcript}` (R) |
| Responder o enviar mensaje | Campo deshabilitado: "requires operate" (feat/chat) | Consola: texto libre = goal; `@name msg` y `@all` (`ConsoleCommands.java`); botón Message en `AgentCardScreen` | `dispatchCommand` `message.dispatch`, que se encola si el hilo está ocupado (**W**). CLI `t3 thread send` |
| Aprobaciones y preguntas | Solo se muestra quién lo necesita: cabecera del chat (feat/chat) y estado NEEDS_ATTENTION (main) | `DecisionScreen` (tecla J): teclas 1–9, Allow once / Always / Deny, chip de riesgo, cola de decisiones; también `/answer dN` (`C/permissions/PermissionsFeature.java`, `F/decisions.ts`) | Ver: `pendingRuntimeRequest` en el shell (R). Responder: `runtime-request.respond`, `thread.user-input.dismiss` (**W**) |
| Interrumpir o detener | No | `/pause`, `/resume`, `/stop @agent`, y botones en `AgentCardScreen` | `run.interrupt`, `thread.stop` (**W**); `forceStopThread`, `subagent.stop` (**W**) |
| Diff | No (feat/chat muestra el diff de una edición dentro del tool call plegado) | `DiffScreen`: lista de archivos A/M/D, unified diff, Merge / Request changes / Reject (`C/diff/DiffScreen.java`) | `getTurnDiff`, `getFullThreadDiff` (R); `/api/pull-requests/diff` (R) |
| Salida de comandos en vivo | No (feat/chat recorta la salida a 8000 caracteres) | Monitores con el log de cada agente (`C/monitor/MonitorRenderer.java`) | `subscribeCommandOutput` (R), un stream acotado del tail |
| Lista de hilos | main: `PortableScreen` con entornos, hilos y notificaciones. feat/chat: `ThreadListView` | `/status`, `/decide` en la consola | `subscribeShell` (R) |
| Búsqueda | No | No | `searchThreads`, `searchThread`, `searchThreadStream` (R) |
| Plan y to-dos | feat/chat: filas Plan y To-do en el chat | Task Wall kanban + `TaskScreen` (Retry / Prioritize / Reassign / Cancel) | `plans` en la projection, `hasActionableProposedPlan` en el shell (R) |
| Costo y tokens | No | `/status` muestra USD (Claude) y tokens por turno (Codex) en el monitor | `serverGetUsageSummary` (`diagnostics:read`); `usageLimitResetAt` en el shell (R) |
| Consola con historial persistente | No | `ConsoleLog`, 200 entradas | n/a |
| Modelo del hilo | No se muestra | Chip de modelo en el nameplate (`Nameplate.java`, `F/agents/models.ts`) | `modelSelection`, `providerInstanceId` en el shell (R); `thread.model-selection.set` (**W**) |

### NPCs

| Tema | t3craft hoy | AgentCraft | T3 Code (scope) |
|---|---|---|---|
| Un NPC por hilo con color de estado | main: `ThreadAvatar` (RemotePlayer con skin), panel y partículas | `ClientAgentEntity` con 6 skins generados | `subscribeShell` (R) |
| Comportamiento | main (feat/npc-behavior): `NpcBrain` con escritorio, tablero, deambular y festejo; A* adaptado | `AgentLife`: posturas (teclear, leer, rascarse la cabeza, sentarse), sigue al jugador | n/a |
| Bocadillo y emotes | main: `BubbleText` con el último resumen | `SpeechBubble`, "!" por cada decisión abierta | `latestVisibleMessage`, `pendingRuntimeRequest` (R) |
| Nameplate | main: label + panel entorno · proyecto | Pill con punto de estado, declutter y líneas guía (`PlateLayout.java`); chip de modelo | `modelSelection` (R) |
| Subagentes | Opción `includeSubagents` (default false): se muestran como hilos sueltos | Los niega (`F/policy.ts`) | `subagents` en la projection, `delegatedFrom` y `getThreadFamily` (R) |
| Clic derecho | main: placeholder. feat/chat: abre el chat | `AgentCardScreen`: estado, tarea, logs y acciones | n/a |
| El agente viene hacia el jugador | No: en ATTENTION va al tablero | Camina hasta el podio o hasta el jugador y lo sigue | n/a |

### HQ y mundo

| Tema | t3craft hoy | AgentCraft | T3 Code (scope) |
|---|---|---|---|
| Barrios por proyecto | main: `RingLayout` + letrero flotante | n/a | `projects` en el shell (R) |
| Generador de HQ | **rama feat/hq-design (en curso)**: `HqGenerator` determinista (hub, tablero, pórtico, espina, alas por proyecto, escritorios y anchors) y `/t3craft debug hq-preview` en superflat | `StudioHqBuilder` (atrio con domo, biblioteca, merge station, lounge) y `HqLandscape` | n/a |
| Sitio en terreno real | **rama feat/site (en curso)**: evaluación, preview con partículas, snapshot, undo, move y resume | No: usa superflat propio (`C/AutoWorld.java`, `M/world/HqWorld.java`) | n/a |
| Rebuild por diff que respeta cambios del jugador | feat/site (`Snapshot`, `Restorer`) y feat/hq-design (`BuildPlan.diff`) | `M/hq/Plan.java`, `PlanStore.java` | n/a |
| Bloques propios con estado | No (solo el ítem T3 Terminal) | 16 bloques: monitor, task_board, decision_podium, merge_station, status_lamp… (`M/block/ModBlocks.java`) | n/a |
| Displays en el mundo | No (hq-design deja anchors `monitor_<id>` y `lamp_<id>`) | Monitores con log, Task Wall, podio, merge station y archivo de memoria (`C/hq/HqWorldDriver.java`) | Datos: projection y shell (R) |
| Estación de trabajo por hilo | **rama feat/workstation**: sin commits todavía | Escritorio + monitor por agente (`StationAssigner`) | n/a |
| Mundo demo | **rama feat/demo-world**: sin commits todavía | `AutoWorld`: superflat creativo pacífico | n/a |
| Archivo de hilos viejos | No (los settled y archivados se ocultan) | n/a | `getArchivedShellSnapshot`, `getOlderSettledThreads` (R) |

### Construcción

| Tema | t3craft hoy | AgentCraft | T3 Code |
|---|---|---|---|
| Constructores animados | main (feat/builders): `BuildCrew`, `BuildManager`, `DemoPlans.shed` | No: los agentes "construyen" código, no bloques | n/a |
| Construir el HQ con la cuadrilla | Pendiente: integrar hq-design + site + builders | El builder coloca el HQ de golpe | n/a |
| Reaccionar a eventos ("hilo terminó → casa") | Hook `Builders.service()` disponible, pero sin uso | n/a | Transiciones del shell (R) |

### Comandos

| t3craft hoy | AgentCraft | T3 Code CLI |
|---|---|---|
| main: `/t3craft status \| reconnect \| reload \| pair \| unpair \| envs \| config [environments\|reload]`, `/t3craft debug build-*`, `/t3dev status\|reset\|check\|enable\|disable` | Servidor: `/agentcraft hq [builder] [force]`, `/agentcraft anchors` (`M/command/AgentCraftCommands.java`) | Lectura: `t3 thread list`, `t3 thread show`, `t3 environment list`, `t3 auth session list` |
| rama feat/site: `/t3craft site here\|pick\|wand\|confirm\|cancel\|undo\|move\|resume\|status` | Consola: `/answer`, `/decide`, `/diff`, `/pause`, `/resume`, `/stop`, `/spawn`, `/task`, `/repo`, `/status`, `/sound`, `/goal` | Escritura: `t3 thread start`, `t3 thread send`, `t3 project add\|remove\|rename` |
| rama feat/hq-design: `/t3craft debug hq-preview`; rama feat/survival: `/t3craft survival status\|reload` | DevBridge `dev.*` (WebSocket en :7879) | |
| **Falta** `/t3craft history <npc>`, `/t3craft list` clicable y `/t3craft search` | | |

### Config

| t3craft hoy | AgentCraft | T3 Code |
|---|---|---|
| main (feat/config): `t3craft.json` v1 con migraciones, avisos y filtros por entorno; pantallas `SettingsScreen` y `EnvironmentsScreen`; Mod Menu; tecla J | Sin archivo de config del mod: solo variables de entorno (`C/ClientEnv.java`); el Foreman usa `~/.agentcraft/config.json` | `subscribeServerConfig`, `serverGetConfig` (R) |

### Notificaciones

| Tema | t3craft hoy | AgentCraft | T3 Code (scope) |
|---|---|---|---|
| Chat + toast + sonido en las transiciones | main: `Notifier`, `Toasts`, `PeekHud`; siembra silenciosa | `C/hud/Toasts.java` con retrato, `HudSounds` (campana, chime) | Transiciones del shell (R) |
| Registro de avisos | main: `NotificationLog` (50) en `PortableScreen` | n/a | Turn items `notification` (R) |
| Barra de progreso en el HUD | No | `GoalBar` ("2 waiting · J") | n/a |
| Señales en el mundo | No (hq-design deja `board_lamp_*` y `lamp_*`) | Lámparas, domo y bombillas de cobre | n/a |
| Notificación de escritorio | No (fuera de alcance) | `F/notifier.ts` | Las da la propia app de T3 |
| Avisos clicables (abren el chat) | No | n/a | n/a |

### Supervivencia

| t3craft hoy | AgentCraft | T3 Code |
|---|---|---|
| main: ítem T3 Terminal con receta barata (vidrio + redstone + cobre). **rama feat/survival (en curso)**: `SurvivalRules` (abrir terminal, construir, materiales), `MaterialBill`, avance "First contact", `/t3craft survival` | Nada: sin recetas, avances ni `data/`; solo pestaña creativa (`M/block/ModItems.java`) | n/a |

### Integración con T3

| Capacidad T3 | Scope | t3craft hoy | AgentCraft (equivalente) |
|---|---|---|---|
| Shell de hilos y proyectos (`subscribeShell`) | R | main | Snapshot del Foreman |
| Historial y stream de un hilo (`subscribeThread`, `/history`, `/bounded`) | R | feat/chat; feat/portable WIP (`/bounded`) | `agent.log` |
| Turn item completo (`getTurnItem`) | R | No (pendiente en feat/chat) | n/a |
| Diffs (`getTurnDiff`, `getFullThreadDiff`) | R | No | `diff.request` |
| Salida de comandos en vivo (`subscribeCommandOutput`) | R | No | Monitores |
| Búsqueda (`searchThreads*`) | R | No | n/a |
| Familia, subagentes y worktree (`getThreadFamily`, `getWorktreeThreadIds`) | R | No | n/a (los niega) |
| Archivados y settled viejos | R | No | n/a |
| Pending requests (`pendingRuntimeRequest`, `runtimeRequests`) | R ver / **W** responder | Ver: main + feat/chat | `decision.answer` |
| Mensaje / cola (`message.dispatch`, `queued-run.*`, `queue.resume`) | **W** | No | `user.message`, `goal.submit` |
| Settle, snooze, pin, archive, mark-unread, visit | **W** (todos son `dispatchCommand`) | Solo lectura de `settledAt`/`snoozedUntil` para filtrar | n/a |
| Crear y lanzar hilos (`thread.create`, `launchThread`, `thread.fork`) | **W** | No | `/spawn`, `goal.submit` |
| Interrumpir / stop / force stop | **W** | No | `agent.action` |
| Cambiar modelo, modo de runtime o de interacción | **W** | No | n/a |
| PRs: lista, detalle, checks, review guide (`pullRequests*`) | R | No | Merge station (merge local) |
| PRs: acciones, comentarios, reviews | `source-control:write` | No | `/diff` + Merge |
| PR del hilo (`linkedPullRequest`, `pullRequests` en el shell) | R | No | n/a |
| Tareas programadas (`scheduledTasks.subscribe`) | R | No | n/a |
| Tareas programadas: crear, ejecutar, borrar | **W** | No | n/a |
| Worktree y rama del hilo (`branch`, `worktreePath`) | R | No | Worktree por worker |
| Estado VCS (`subscribeVcsStatus`, `vcsListRefs`) | R | No | n/a |
| Worktrees: crear o quitar (`vcsCreateWorktree`) | `source-control:write` | No | El Foreman lo hace |
| Previews (`previewList`, `subscribePreviewEvents`) | R | No | n/a |
| Previews: abrir o cerrar | `preview:operate` | No | n/a |
| Ambientes vinculados (`peerLinksList`) | `access:read` | No (multi-entorno propio por pairing) | n/a |
| Dispositivos (`deviceList`, `subscribeDeviceState`) | R | No | n/a |
| Adjuntos: URL de lectura (`assetsCreateUrl`) | R | No | n/a |
| Adjuntos: subir o borrar | **W** | No | n/a |
| Terminal (`terminalObserve`, `subscribeTerminalMetadata`) | `terminal:read` | No | n/a |
| Uso y costos (`serverGetUsageSummary`) | `diagnostics:read` | No | `costUsd` |
| Recursos del host (`serverGetHostResources`) | R | No | n/a |
| Archivos del proyecto (`projectsReadFile`, `projectsSearchEntries`) | `filesystem:read` | No | n/a |

### Devx

| t3craft hoy | AgentCraft |
|---|---|
| main (feat/devx): CI en GitHub Actions, JUnit (~90+ tests), client GameTests (compilan, no se corrieron), hot-swap con `DevReload`/`ModuleGuard`, `/t3dev`, `docs/dev.md`, `CHANGELOG-dev.md` (RESTART/RELOAD) | DevBridge (cámara, screenshots, estado) y `tools/*.mjs` (`shoot`, `qa`, `record`); escenas JSON; ~30 tests vitest en el Foreman; ningún test Java; pipeline de arte (Python + Blender); docs del protocolo generados |

---

## 2. Lista priorizada (lo que falta)

Orden: valor alto con esfuerzo bajo primero. Los ítems marcados **⚠️ ESCRITURA** necesitan `orchestration:operate` (u otro scope de escritura) y **no se hacen sin el OK de Facundo**.

| # | Ítem | Valor | Esfuerzo | Dependencias | Scope |
|---|---|---|---|---|---|
| 1 | Integrar feat/chat a main y conectarlo con el clic derecho, `PortableScreen` y `/t3craft history <npc>` | Alto | S | feat/chat, feat/portable | R |
| 2 | Avisos clicables: el toast o la línea de chat abre el chat del hilo (`ChatScreens.open`) | Alto | S | feat/chat | R |
| 3 | Chip de modelo y proveedor en el nameplate y en el panel (`modelSelection`) | Medio | S | main (`ThreadShell` + `Nameplate`) | R |
| 4 | Pantalla "Needs you" en modo solo lectura (estilo `DecisionScreen`): cola de `pendingRuntimeRequest` de todos los entornos, con detalle y salto al chat. Botones Allow/Deny visibles pero deshabilitados | Alto | M | feat/chat | R |
| 5 | Salida completa al desplegar un tool call (`getTurnItem`) y tail en vivo (`subscribeCommandOutput`) | Medio | S–M | feat/chat | R |
| 6 | Pantalla de diff del hilo (`getFullThreadDiff`, `getTurnDiff`), inspirada en `DiffScreen`, sin botones de merge | Alto | M | feat/chat (Markdown/TextWrap) | R |
| 7 | Mundo vivo: la lámpara de cada escritorio, las bombillas del tablero y los monitores siguen el estado del hilo, con el último mensaje en el monitor | Alto | M | feat/hq-design, feat/workstation, feat/site | R |
| 8 | Construir el HQ con la cuadrilla en el sitio elegido (hq-design → site → builders) y anexar alas cuando aparece un proyecto | Alto | L | feat/hq-design, feat/site, main builders, feat/survival (costo) | R |
| 9 | Barra en el HUD (estilo `GoalBar`): trabajando, needs you, error, con badge "n waiting · tecla" | Medio | S | main `PeekHud` | R |
| 10 | Badge del PR del hilo (número, estado, checks) en el panel y el chat; pantalla de lista de PRs | Medio | M | feat/chat | R |
| 11 | `/t3craft search <texto>` y búsqueda en `PortableScreen` (`searchThreads`) | Medio | M | feat/portable | R |
| 12 | Subagentes visibles como "ayudantes" junto al NPC padre (`subagents`, `getThreadFamily`) en lugar de hilos sueltos | Medio | M | main npc-behavior | R |
| 13 | Sótano o archivo con hilos settled y archivados (`getOlderSettledThreads`, `getArchivedShellSnapshot`), según la decisión "archive basement" | Medio | M | feat/hq-design | R |
| 14 | Mostrar rama y worktree del hilo (`branch`, `worktreePath`) en el panel y en el letrero del escritorio | Bajo | S | main | R |
| 15 | Tablero de tareas programadas (`scheduledTasks.subscribe`): próxima ejecución, en el tablero del HQ | Bajo | M | feat/hq-design | R |
| 16 | Postura "viene hacia el jugador" en ATTENTION (opcional, configurable) | Bajo | S | main npc-behavior | R |
| 17 | DevBridge de screenshots para QA visual sin humano (idea de AgentCraft) | Medio | M | feat/devx | n/a |
| 18 | Costo y uso (`serverGetUsageSummary`) | Bajo | M | — | `diagnostics:read` (otro scope de lectura que habría que pedir en el pairing) |
| 19 | **Responder** en el chat (`message.dispatch`, con cola si el hilo está ocupado) | Alto | M | feat/chat + emitir y almacenar un token operate | **⚠️ ESCRITURA** |
| 20 | **Aprobar o denegar** pending requests (`runtime-request.respond`) desde la pantalla del ítem 4 | Alto | M | ítem 4 | **⚠️ ESCRITURA** |
| 21 | **Interrumpir o detener** un hilo (`run.interrupt`, `thread.stop`) desde el NPC | Medio | S | ítem 19 (misma infra de escritura) | **⚠️ ESCRITURA** |
| 22 | **Settle, snooze, pin y mark-read** desde el NPC o el terminal (el NPC "se va a casa") | Medio | S | ítem 19 | **⚠️ ESCRITURA** |
| 23 | **Crear o lanzar un hilo** en un proyecto (`launchThread`, `thread.create`) desde un bloque o el terminal | Medio | M | ítem 19 | **⚠️ ESCRITURA** |
| 24 | **Cola**: ver, reordenar o cancelar runs encolados (`queued-run.*`). Ver la cola es R (`queuePosition` en `runs`); gestionarla es W | Bajo | M | ítem 19 | **⚠️ ESCRITURA** (gestión) |
| 25 | **Acciones de PR**: merge, review o comentario | Bajo | L | ítem 10 | **⚠️ ESCRITURA** (`source-control:write`) |
| 26 | **Tareas programadas**: ejecutar ahora o activar/desactivar | Bajo | S | ítem 15 | **⚠️ ESCRITURA** |

Infraestructura común a todos los ítems ⚠️: un segundo pairing **opcional** por entorno con `orchestration:operate`, guardado aparte y revocable, con confirmación en el juego y log local de auditoría. Es lo que pide `plan-v2.md` §1 (puntos a–d).

---

## 3. Riesgos y preguntas abiertas para Facundo

**Riesgos**
- **`operate` es casi admin**: arranca agentes `full-access` y aprueba comandos arbitrarios. Si alguien controla la PC con Minecraft, controla el VPS y acmesite. Además, `t3 auth pairing create` sin `--scope` concede **todos** los scopes (`orchestration:operate`, `filesystem:write`, `terminal:operate`…).
- **Superficie de la API**: los métodos y campos citados vienen de `t3code-ref@15fe2832`, mientras que el VPS corre 0.1.8 y la CLI local 0.1.4. Los nombres de campo pueden cambiar entre nightlies, así que la decodificación debe seguir siendo tolerante (como hoy en `ThreadShell`).
- **Ramas paralelas**: chat (`ChatScreen`, `ThreadListView`, `EnvBadge`) y portable (`EnvBadge`, `Conversation`, `/bounded`) duplican piezas. Conviene consolidarlas antes de sumar features encima. feat/portable tiene WIP sin commitear.
- **Conexiones**: feat/chat abre su propio socket por pantalla, contra la regla de `api.md` ("agrega un método a `T3Connection`, no abras otra conexión"). Con monitores en vivo (ítem 7) se multiplicarían las suscripciones a `subscribeThread`.
- **Licencias**: AgentCraft es MIT. Adaptar código (por ejemplo `DiffScreen` o `DecisionScreen`) exige la cabecera y una entrada en `THIRD_PARTY_NOTICES.md`, como ya se hace con `GridPathfinder`.
- **Multijugador**: builders, site y hq solo funcionan en singleplayer (servidor integrado). AgentCraft tiene bloques y comandos de servidor.

**Preguntas**
1. ¿Habilitamos un **segundo pairing operate opcional**? Si sí, ¿con qué granularidad: solo responder y aprobar, o también crear, detener y settle?
2. Para los ítems 18 (costo) y la vista de terminal: ¿se puede pedir también `diagnostics:read` y `terminal:read` en el pairing, o el mod se queda estrictamente en `orchestration:read`?
3. ¿Bloques propios (monitor, lámpara, podio) con block entities, como AgentCraft, o seguimos con bloques vanilla y render client-side sobre los anchors? Los bloques propios obligan a RESTART y no funcionan en servidores sin el mod.
4. ¿Los subagentes deben verse como ayudantes del NPC padre (ítem 12) o seguir ocultos por defecto?
5. Orden de integración: ¿primero chat + portable (UI) o primero hq-design + site + builders (mundo)?
