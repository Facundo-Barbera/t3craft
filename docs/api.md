# T3Craft mod — API pública interna

Para los hilos que trabajan en ramas `feat/*` (site, portable, builders, hq-design, chat,
npc-behavior, config, devx). Este documento describe las clases que otros módulos consumen.
Responsable: hilo `main`, dueño de `dev.t3craft.t3` (cliente directo de T3) y de
`dev.t3craft.client.npc` (entidades y registro).

**Política de cambios:** lo que figura aquí es estable. Se pueden **agregar** métodos o campos
(al final de los records, con defaults en las fábricas), pero no se renombra ni se quita nada
sin avisar en este archivo. Si necesitas un cambio, pídelo en vez de editar estas clases desde
tu rama.

Reglas para todos:
- **Lectura y escritura separadas en el código**: `T3Connection` y todo lo de lectura solo hacen
  llamadas de `orchestration:read`. Las escrituras (chat, aprobaciones, crear o interrumpir
  threads) pasan **únicamente** por `OperateGate` (§5). Desde 2026-11-07 el emparejamiento por
  defecto pide `orchestration:read` + `orchestration:operate` en una sola sesión;
  `/t3craft pair --read-only` (o el toggle de la pantalla) pide solo lectura. Sin token operate,
  todo sigue en solo lectura. Ver `docs/feat-operate.md`.
- **Secretos**: los tokens y tickets nunca van a logs, al chat ni a `toString()`. Usa
  `T3Connection.sanitize(...)` para cualquier texto de error que pueda llevarlos.
- **Hilos**: lo marcado *client thread* solo se llama desde el hilo de Minecraft
  (`Minecraft.execute` / tick). Las clases de `dev.t3craft.t3` no dependen de Minecraft.

---

## 1. Modelo de threads (`dev.t3craft.t3`, source set `main`, sin clases de MC)

| Clase | Qué es |
|---|---|
| `ThreadShell` (record) | Los campos de `OrchestrationV2ThreadShell` que leemos, decodificados de forma tolerante. `fromJson(JsonObject)` devuelve `null` si no hay id. Los tiempos van en epoch ms o `null`. `PendingRequest(id, kind, blocking)`: `blocking` es `null` si el servidor es viejo. `parentThreadId` (de `lineage`, último componente) es el hilo padre de un subagente o fork. |
| `ThreadState` (enum) | `NEEDS_ATTENTION, ERROR, WORKING, DONE, IDLE`, cada uno con `wire`, `color` (0xRRGGBB), `glyph`, `priority`, `word`. `ThreadState.of(ThreadShell)` aplica las reglas de mapeo (las mismas que el puente). |
| `Visibility` | `select(threads, nowMs, Options)` elige los threads de **un** entorno que llevan NPC. `Options(recentWindowMs, maxPerEnvironment, includeSubagents)` y `Options.defaults()` (24 h, 40, false). También `isSettled`, `lastActivity` y `tier`. Es pura: no modifica la fuente. |
| `Presentation` | `present(env, envLabel, ThreadShell, projectTitle) → NpcView`. También `npcId(env, threadId)` (`"<env>:<threadId>"`) y `clip`. |
| `NpcView` (record) | Un NPC ya resuelto: `id, env, envLabel, threadId, title, project, state, color, glyph, label, sublabel, activity, priority, settled, groupId, groupLabel, skin, particle`. `groupId` = `"<env>:<projectId>"` (barrio). `skin` = semilla `"<env>/<state>"`. `particle` ∈ `working|attention|error|done|null`. |
| `Transitions` | `of(before, after)` y `between(beforeById, after, syncedEnvs)` dan los `Transition(npcId, from, to, title, text, color, Sound)` que merecen aviso. El estado inicial nunca avisa. `peek(npcs)` da `Peek(text, color, npcId)` para el HUD. |
| `ThreadSearch` | Búsqueda de solo lectura (`orchestration.searchThreads`). `decode(value) → Decoded(matches, threads)` tolerante (campos faltantes con defaults, `quality`/`hits`/`threads` opcionales); `enrich(env, label, query, decoded, view) → List<Hit>` (título, proyecto y estado desde el shell, más coincidencias locales de título); `merge(perEnv, limit)` con orden estable `ORDER` (relevancia, actividad, entorno, título, id). `Outcome(query, hits, searched, paired, errors)`; `SearchException` con mensaje ya saneado. |
| `ShellState` | Reducer de `subscribeShell`: los snapshots autoritativos reemplazan el estado y los de enriquecimiento solo actualizan proyectos. `view()` → `View(threads, projectTitles, synced)`: copia inmutable, segura entre hilos. No es thread-safe: lo usa un solo hilo de conexión. |

## 2. Cliente T3 (`dev.t3craft.t3`)

| Clase | Qué es |
|---|---|
| `DirectController` | Modo directo. `start()` (relee entornos y credenciales y conecta), `stop()`, `reconnectAll()`, `statuses() → List<EnvStatus(id, label, status, detail)>`, `npcs(nowMs) → List<NpcView>` (filtrado, con tope por entorno, ordenado), `search(query, limit, timeoutMs) → CompletableFuture<ThreadSearch.Outcome>` (todos los entornos conectados, fusionado; cancelar el future interrumpe cada petición), `setOptions(Visibility.Options)`, `environments()`, `shellViews() → Map<envId, ShellState.View>` (sin filtrar; lo usan los ayudantes), `view(envId) → ShellState.View | null` (un entorno) y `connection(envId) → T3Connection | null` (la conexión única del entorno, para leer streams). Su callback `onChange` corre en un hilo de conexión: salta al client thread antes de tocar Minecraft (así lo hace `T3CraftClient`). |
| `T3Connection` | Una conexión de solo lectura a un entorno (ticket → `/ws` → `subscribeShell` con `requestCompletionMarker`, Ack por chunk, Ping, backoff). `Status`: `CONNECTING, OK, AUTH_FAILED, NOT_PAIRED, OFFLINE, INCOMPATIBLE` (`wire` en minúsculas). `Listener.onStatus/onShell`. `sanitize(String)` es estático. Todo lo que no es el shell va por el mismo socket, con **un solo** generador de ids y **una** cola de envío (`StreamRegistry`, envíos en serie): `subscribeThread(threadId, StreamRegistry.Listener) → Subscription` (compartida con refcount: replay para quien llega tarde, resuscripción al reconectar, `Interrupt` al cerrar la última; cierra el handle al terminar), `getTurnItem(threadId, itemId)`, `getThreadProjection(threadId)`, `threadHistory(threadId, cursor)` (HTTP con la sesión del entorno) y `searchThreads(query, limit, timeoutMs) → CompletableFuture<ThreadSearch.Decoded>` (timeout, `Interrupt` al cancelar, falla si no está `OK`, mensajes sin secretos). Para leer otros streams agrega un método aquí (vía `StreamRegistry.call`/`subscribe`); no abras otra conexión por tu cuenta. |
| `StreamRegistry` | Multiplexor puro (testeado) de `T3Connection`: `subscribe(threadId, listener)`, `call(tag, payload)` / `call(frameBuilder)` → `CompletableFuture<List<JsonElement>>` (cancelar = `Interrupt`), `abandon(future)`, `onOpen/onClosed`, enrutado de `Chunk`/`Exit` por request id ("1" es el shell). |
| `RpcFrames` | Framing Effect RPC JSON: `request`, `subscribeShell(id)`, `subscribeThread(id, threadId)`, `ack`, `interrupt`, `ping`, `parse(text) → Chunk | Exit | Defect | Pong | Other`. Constantes `SUBSCRIBE_SHELL`, `SUBSCRIBE_THREAD`, `GET_TURN_ITEM`, `GET_THREAD_PROJECTION`, `SEARCH_THREADS`. `searchThreads(id, query, limit, envLabel)` y `searchQuery(q)` (recorta a 200). `Exit.value()` lleva el valor de éxito de las RPC no-stream (`null` en streams). |
| `Pairing` | `parse(linkOrCode, hostUrl) → Target(baseUrl, credential)`, `tokenRequestBody`, `parseTokenResponse` y `describeTokenError`. Las sobrecargas sin `scopes` son las de solo lectura (exigen exactamente `orchestration:read`); la UI siempre pasa los scopes (§5). `READ_SCOPE`. `PairingException` lleva mensajes para el jugador, sin secretos. |
| `PairingClient` | `exchange(Target, clientLabel, clientOs) → CompletableFuture<Pairing.Session>` (solo lectura) y `exchange(…, scopes)`. |
| `Environments` | El almacén en `config/t3craft/`: `list() → List<Environment(id, label, url)>`, `pair(env, session) → boolean` (guarda el resultado de un emparejamiento: con read + operate escribe también el token operate y devuelve `true`; uno de solo lectura borra el operate anterior), `save(env, session)` (solo la credencial de lectura; nunca toca el token operate), `remove(id)` (también borra el token operate), `credential(id) → Session | null` (sesión de la zona de lectura: `orchestration:read` o `read + operate`; cualquier otro conjunto se ignora), `slug(label)`. Los tokens solo se leen aquí. |
| `Json` | Accesores tolerantes de Gson (`str`, `obj`, `arr`, `bool`, `optBool`, `num`, `time`). |

## 3. Entidades y registro (`dev.t3craft.client.npc`, source set `client`)

| Clase | Qué es |
|---|---|
| `NpcInfo` (record) | Lo que dibuja el mod: `id, title, label, envLabel, sublabel, color, activity, priority, settled, state, groupId, groupLabel, skin, particle`. Fábricas `fromView(NpcView)` (modo directo) y `fromJson(JsonObject)` (modo bridge, tolerante). Helper `parseColor("#RRGGBB")`. |
| `NpcRegistry` | *Client thread.* Fuente única de los NPCs en el mundo. Se alimenta con `replaceAll(List<NpcInfo>)`, `upsert`, `remove(npcId)`. Se consulta con `info(npcId)`, `all()`, `size()`, `position(npcId)` y `threadFor(Entity) → npcId | null` (sirve para el avatar y para su panel), `avatar(npcId)` y `setPanelExtra(Function<npcId, Component>)` (línea extra del panel; la usa feat/npc-helpers). `tick(Minecraft)` **nunca lanza**: valida las entradas, purga lo que no corresponde, barre huérfanos y se reconstruye ante errores (también tras un hot-swap). `clear(Minecraft)`. Los ids de entidad son ≤ `-1_000_000`: ese rango es nuestro y no lo uses para otras entidades. |
| `HelperDirector` (`client.npc.helpers`) | *Client thread.* Subagentes como aldeanos bebé que siguen al NPC padre (ver `docs/feat-npc-helpers.md`). `update(HelperRoster.Collected, visibleParents, maxPerParent)`, `tick(Minecraft, npcId → avatar)` (nunca lanza), `panelExtra(npcId)`, `parentFor(Entity)`, `clear(level)`. Ids de entidad desde `-4_000_000`. |
| `ThreadAvatar` | `RemotePlayer` solo de cliente: skin por defecto según `NpcInfo.skin`, nametag = `Nameplate.of`. No es un tipo de entidad registrado. El comportamiento (caminar, estaciones) corresponde a `feat/npc-behavior`: extiéndelo con hooks y coordina antes de cambiar la construcción en `NpcRegistry`. |
| `Nameplate` | Textos: `of(info)` (nametag), `info(info)` (panel), `sign(groupLabel, envLabel, count)` (letrero del barrio). |

## 4. Fachada de cliente (`dev.t3craft.client`)

| Clase | Qué es |
|---|---|
| `T3CraftClient` | `get()` (instancia). `npcs()`, `direct()` (o `null` en modo bridge), `link()` (o `null` en modo directo), `config()`, `environments() → id → {label, status, detail}`, `reconnect()`, `reload()`, `pair(label, linkOrCode, hostUrl, Consumer<PairResult>)`, `unpair(envId)`, `openNextTick(Screen)`. |
| `ModConfig` (record) | `mode()` (`DIRECT` / `BRIDGE`), `bridgeUrl()`, `bridgeKey()`, `visibility()`. Rutas: `file()` (`config/t3craft.json`) y `dataDir()` (`config/t3craft/`). La config que agregue `feat/config` debe ser **aditiva**: claves nuevas con defaults y compatibilidad con archivos sin ellas. |
| `search.SearchClient` | `/t3craft search <texto>` y `/t3craft search-open <env> <thread>` (el que ejecuta el clic), registrados como un literal `t3craft` aparte que Brigadier fusiona. `search(client, q, limit)` (directo: `DirectController.search`; bridge: filtro local por título) y `open(client, env, thread)` (vía `PortableLinks`, al tick siguiente). |
| `search.SearchModel` / `search.SearchBox` | Modelo puro del campo de búsqueda (debounce, cancelación, descarte de resultados viejos) y el widget que lo dibuja. Para moverlo a otra pantalla: `layout(...)` como widget y reenviar `tick`, `keyPressed`, `mouseClicked`, `render` y `removed`. |
| `T3CraftCommands` | Registra `/t3craft`. Para agregar subcomandos, coordina con `feat/devx`/`feat/chat`. `prefix()` es público para mensajes de chat. |
| `hud.Notifier`, `hud.PeekHud`, `hud.Toasts` | Avisos (chat + toast + sonido), la línea de HUD con el banner y los toasts. `PeekHud.setPeek(text, color)` y `setBanner(text)` (`null` oculta). |
| `screen.ThreadChatScreen` | Placeholder del chat. Lo reemplaza `feat/chat` y se abre con `new ThreadChatScreen(NpcInfo)`. |
| `screen.PairScreen` | UI de emparejado de solo lectura. |

## 5. Escritura: `orchestration:operate` (`feat/operate-core`)

Detalle y receta para añadir acciones: `docs/feat-operate.md`.

| Clase | Qué es |
|---|---|
| `Pairing` (añadidos) | `OPERATE_SCOPE`, `READ_SCOPES`, `OPERATE_SCOPES`, `DEFAULT_SCOPES` (= `OPERATE_SCOPES`, lo que pide el emparejamiento por defecto), `pairingScopes(readOnly)`, `pairingHostCommand(readOnly, baseUrl)`, `grantsOperate(scopes)`, `requireOperate(session)`; `tokenRequestBody(…, scopes)`, `parseTokenResponse(body, now, requested)` (acepta `read` o `read + operate` dentro de lo pedido; rechaza cualquier otro scope, operate en una petición de solo lectura y una concesión sin `read`; si se pidió read + operate y la credencial era de solo lectura, devuelve una sesión de solo lectura), `describeTokenError(status, body, scopes)` y `hostCommand(scopes, label, baseUrl)` (siempre `--scope orchestration:read [--scope orchestration:operate]`). Solo aceptan `READ_SCOPES` u `OPERATE_SCOPES`: nunca sin scopes explícitos. `PairingClient.exchange(…, scopes)`. |
| `OperateCredentials` | `credentials/operate/<env>.json`, el interruptor del control: `save`, `get(env, now)` (null si falta, vence o tiene otros scopes), `stored`, `has`, `sharesReadSession(env)` (el token operate es la misma sesión combinada que la de lectura), `revoke` (solo local; el entorno queda en solo lectura). `HOST_REVOKE_HINT`, `SHARED_REVOKE_HINT`. |
| `OperateKind` | Enum de acciones con `destructive`, `wire` y `langKey()`. `isDestructive(wire)`: lo desconocido cuenta como destructivo. |
| `OperateAction` (record) | `kind, envId, threadId, threadTitle, rpcTag, payload`. Factoría `dispatch(...)`. El payload es una copia defensiva y no aparece en `toString()`. |
| `OperateGate` | `availability(env)` (`ENABLED/READ_ONLY/EXPIRED`), `canOperate`, `operate(env)` → `Env{available, dispatch(kind, threadId, title, command), submit}`, `submit(action, origin, confirmed) → CompletableFuture<OperateResult>` (nunca falla) y `cancelled(action, origin)`. `Origin`: `UI`, `COMMAND`. |
| `OperateResult` (record) | `outcome, message, value`. `Outcome`: `OK, READ_ONLY, NEEDS_CONFIRMATION, CANCELLED, AUTH_FAILED, OFFLINE, REJECTED, FAILED`. |
| `OperateTransport` / `OperateConnection` | Transporte por entorno. `DirectController.operateTransport(env)` / `resetOperate(env)`: no abras sockets operate por tu cuenta. |
| `AuditLog` | `config/t3craft/audit.log` (JSONL, rota a 1 MiB). `append(Entry)` nunca lanza. |
| `client.operate.OperateUi` | *Client thread.* `run(action, origin, returnTo, done)`, `guard(widget, env)`, `canOperate(env)` (la consulta para textos y botones: `true` = no digas "solo lectura"), `availability(env)`, `enableControlScreen(parent, env)` (la pantalla que da control: "Habilitar control" si el entorno está emparejado, si no la de emparejar) y `gate()`. |
| `client.operate.OperateClient` | `get().enable(env, link, origin, done)`, `revoke(env, origin)`, `environment(env)`, `label(env)`. |
| `client.screen.ConfirmActionScreen` | `new ConfirmActionScreen(parent, action, envLabel, yes -> …)`. Normalmente la abre `OperateUi.run`. |
| `client.screen.OperatePairScreen` | "Habilitar control" para un entorno emparejado en solo lectura (mejora; el de lectura se mantiene). |
| `client.screen.PairScreen` | Emparejar: lectura + control por defecto; `new PairScreen(parent, true)` o `/t3craft pair --read-only` para solo lectura. |
