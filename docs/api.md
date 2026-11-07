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
- **Solo lectura por defecto**: `T3Connection` y todo lo de lectura solo usan `orchestration:read`.
  Las escrituras (chat, aprobaciones, crear o interrumpir threads) pasan **únicamente** por
  `OperateGate` (§5), con el segundo emparejamiento operate opcional por entorno. Sin token
  operate, todo sigue en solo lectura. Ver `docs/feat-operate.md`.
- **Secretos**: los tokens y tickets nunca van a logs, al chat ni a `toString()`. Usa
  `T3Connection.sanitize(...)` para cualquier texto de error que pueda llevarlos.
- **Hilos**: lo marcado *client thread* solo se llama desde el hilo de Minecraft
  (`Minecraft.execute` / tick). Las clases de `dev.t3craft.t3` no dependen de Minecraft.

---

## 1. Modelo de threads (`dev.t3craft.t3`, source set `main`, sin clases de MC)

| Clase | Qué es |
|---|---|
| `ThreadShell` (record) | Los campos de `OrchestrationV2ThreadShell` que leemos, decodificados de forma tolerante. `fromJson(JsonObject)` devuelve `null` si no hay id. Los tiempos van en epoch ms o `null`. `PendingRequest(id, kind, blocking)`: `blocking` es `null` si el servidor es viejo. |
| `ThreadState` (enum) | `NEEDS_ATTENTION, ERROR, WORKING, DONE, IDLE`, cada uno con `wire`, `color` (0xRRGGBB), `glyph`, `priority`, `word`. `ThreadState.of(ThreadShell)` aplica las reglas de mapeo (las mismas que el puente). |
| `Visibility` | `select(threads, nowMs, Options)` elige los threads de **un** entorno que llevan NPC. `Options(recentWindowMs, maxPerEnvironment, includeSubagents)` y `Options.defaults()` (24 h, 40, false). También `isSettled`, `lastActivity` y `tier`. Es pura: no modifica la fuente. |
| `Presentation` | `present(env, envLabel, ThreadShell, projectTitle) → NpcView`. También `npcId(env, threadId)` (`"<env>:<threadId>"`) y `clip`. |
| `NpcView` (record) | Un NPC ya resuelto: `id, env, envLabel, threadId, title, project, state, color, glyph, label, sublabel, activity, priority, settled, groupId, groupLabel, skin, particle`. `groupId` = `"<env>:<projectId>"` (barrio). `skin` = semilla `"<env>/<state>"`. `particle` ∈ `working|attention|error|done|null`. |
| `Transitions` | `of(before, after)` y `between(beforeById, after, syncedEnvs)` dan los `Transition(npcId, from, to, title, text, color, Sound)` que merecen aviso. El estado inicial nunca avisa. `peek(npcs)` da `Peek(text, color, npcId)` para el HUD. |
| `ShellState` | Reducer de `subscribeShell`: los snapshots autoritativos reemplazan el estado y los de enriquecimiento solo actualizan proyectos. `view()` → `View(threads, projectTitles, synced)`: copia inmutable, segura entre hilos. No es thread-safe: lo usa un solo hilo de conexión. |

## 2. Cliente T3 (`dev.t3craft.t3`)

| Clase | Qué es |
|---|---|
| `DirectController` | Modo directo. `start()` (relee entornos y credenciales y conecta), `stop()`, `reconnectAll()`, `statuses() → List<EnvStatus(id, label, status, detail)>`, `npcs(nowMs) → List<NpcView>` (filtrado, con tope por entorno, ordenado), `setOptions(Visibility.Options)`, `environments()`. Su callback `onChange` corre en un hilo de conexión: salta al client thread antes de tocar Minecraft (así lo hace `T3CraftClient`). |
| `T3Connection` | Una conexión de solo lectura a un entorno (ticket → `/ws` → `subscribeShell` con `requestCompletionMarker`, Ack por chunk, Ping, backoff). `Status`: `CONNECTING, OK, AUTH_FAILED, NOT_PAIRED, OFFLINE, INCOMPATIBLE` (`wire` en minúsculas). `Listener.onStatus/onShell`. `sanitize(String)` es estático. Para leer otros streams (por ejemplo `subscribeThread` para el chat), agrega un método aquí; no abras otra conexión por tu cuenta. |
| `RpcFrames` | Framing Effect RPC JSON: `request`, `subscribeShell(id)`, `ack`, `interrupt`, `ping`, `parse(text) → Chunk | Exit | Defect | Pong | Other`. Constantes `SUBSCRIBE_SHELL`, `SUBSCRIBE_THREAD`. |
| `Pairing` | `parse(linkOrCode, hostUrl) → Target(baseUrl, credential)`, `tokenRequestBody`, `parseTokenResponse` (exige exactamente `orchestration:read` y Bearer) y `describeTokenError`. `READ_SCOPE`. `PairingException` lleva mensajes para el jugador, sin secretos. |
| `PairingClient` | `exchange(Target, clientLabel, clientOs) → CompletableFuture<Pairing.Session>`. |
| `Environments` | El almacén en `config/t3craft/`: `list() → List<Environment(id, label, url)>`, `save(env, session)`, `remove(id)` (también borra el token operate), `credential(id) → Session | null` (solo devuelve sesiones read-only), `slug(label)`. Los tokens solo se leen aquí. |
| `Json` | Accesores tolerantes de Gson (`str`, `obj`, `arr`, `bool`, `optBool`, `num`, `time`). |

## 3. Entidades y registro (`dev.t3craft.client.npc`, source set `client`)

| Clase | Qué es |
|---|---|
| `NpcInfo` (record) | Lo que dibuja el mod: `id, title, label, envLabel, sublabel, color, activity, priority, settled, state, groupId, groupLabel, skin, particle`. Fábricas `fromView(NpcView)` (modo directo) y `fromJson(JsonObject)` (modo bridge, tolerante). Helper `parseColor("#RRGGBB")`. |
| `NpcRegistry` | *Client thread.* Fuente única de los NPCs en el mundo. Se alimenta con `replaceAll(List<NpcInfo>)`, `upsert`, `remove(npcId)`. Se consulta con `info(npcId)`, `all()`, `size()`, `position(npcId)` y `threadFor(Entity) → npcId | null` (sirve para el avatar y para su panel). `tick(Minecraft)` **nunca lanza**: valida las entradas, purga lo que no corresponde, barre huérfanos y se reconstruye ante errores (también tras un hot-swap). `clear(Minecraft)`. Los ids de entidad son ≤ `-1_000_000`: ese rango es nuestro y no lo uses para otras entidades. |
| `ThreadAvatar` | `RemotePlayer` solo de cliente: skin por defecto según `NpcInfo.skin`, nametag = `Nameplate.of`. No es un tipo de entidad registrado. El comportamiento (caminar, estaciones) corresponde a `feat/npc-behavior`: extiéndelo con hooks y coordina antes de cambiar la construcción en `NpcRegistry`. |
| `Nameplate` | Textos: `of(info)` (nametag), `info(info)` (panel), `sign(groupLabel, envLabel, count)` (letrero del barrio). |

## 4. Fachada de cliente (`dev.t3craft.client`)

| Clase | Qué es |
|---|---|
| `T3CraftClient` | `get()` (instancia). `npcs()`, `direct()` (o `null` en modo bridge), `link()` (o `null` en modo directo), `config()`, `environments() → id → {label, status, detail}`, `reconnect()`, `reload()`, `pair(label, linkOrCode, hostUrl, Consumer<PairResult>)`, `unpair(envId)`, `openNextTick(Screen)`. |
| `ModConfig` (record) | `mode()` (`DIRECT` / `BRIDGE`), `bridgeUrl()`, `bridgeKey()`, `visibility()`. Rutas: `file()` (`config/t3craft.json`) y `dataDir()` (`config/t3craft/`). La config que agregue `feat/config` debe ser **aditiva**: claves nuevas con defaults y compatibilidad con archivos sin ellas. |
| `T3CraftCommands` | Registra `/t3craft`. Para agregar subcomandos, coordina con `feat/devx`/`feat/chat`. `prefix()` es público para mensajes de chat. |
| `hud.Notifier`, `hud.PeekHud`, `hud.Toasts` | Avisos (chat + toast + sonido), la línea de HUD con el banner y los toasts. `PeekHud.setPeek(text, color)` y `setBanner(text)` (`null` oculta). |
| `screen.ThreadChatScreen` | Placeholder del chat. Lo reemplaza `feat/chat` y se abre con `new ThreadChatScreen(NpcInfo)`. |
| `screen.PairScreen` | UI de emparejado de solo lectura. |

## 5. Escritura: `orchestration:operate` (`feat/operate-core`)

Detalle y receta para añadir acciones: `docs/feat-operate.md`.

| Clase | Qué es |
|---|---|
| `Pairing` (añadidos) | `OPERATE_SCOPE`, `READ_SCOPES`, `OPERATE_SCOPES`; `tokenRequestBody(…, scopes)`, `parseTokenResponse(body, now, expected)`, `describeTokenError(status, body, scopes)` y `hostCommand(scopes, label, baseUrl)`. Solo aceptan `READ_SCOPES` u `OPERATE_SCOPES`: nunca sin scopes explícitos. `PairingClient.exchange(…, scopes)`. |
| `OperateCredentials` | `credentials/operate/<env>.json`: `save`, `get(env, now)` (null si falta, vence o tiene otros scopes), `stored`, `has`, `revoke` (solo local). |
| `OperateKind` | Enum de acciones con `destructive`, `wire` y `langKey()`. `isDestructive(wire)`: lo desconocido cuenta como destructivo. |
| `OperateAction` (record) | `kind, envId, threadId, threadTitle, rpcTag, payload`. Factoría `dispatch(...)`. El payload es una copia defensiva y no aparece en `toString()`. |
| `OperateGate` | `availability(env)` (`ENABLED/READ_ONLY/EXPIRED`), `canOperate`, `operate(env)` → `Env{available, dispatch(kind, threadId, title, command), submit}`, `submit(action, origin, confirmed) → CompletableFuture<OperateResult>` (nunca falla) y `cancelled(action, origin)`. `Origin`: `UI`, `COMMAND`. |
| `OperateResult` (record) | `outcome, message, value`. `Outcome`: `OK, READ_ONLY, NEEDS_CONFIRMATION, CANCELLED, AUTH_FAILED, OFFLINE, REJECTED, FAILED`. |
| `OperateTransport` / `OperateConnection` | Transporte por entorno. `DirectController.operateTransport(env)` / `resetOperate(env)`: no abras sockets operate por tu cuenta. |
| `AuditLog` | `config/t3craft/audit.log` (JSONL, rota a 1 MiB). `append(Entry)` nunca lanza. |
| `client.operate.OperateUi` | *Client thread.* `run(action, origin, returnTo, done)`, `guard(widget, env)`, `canOperate(env)`, `gate()`. |
| `client.operate.OperateClient` | `get().enable(env, link, origin, done)`, `revoke(env, origin)`, `environment(env)`, `label(env)`. |
| `client.screen.ConfirmActionScreen` | `new ConfirmActionScreen(parent, action, envLabel, yes -> …)`. Normalmente la abre `OperateUi.run`. |
| `client.screen.OperatePairScreen` | "Habilitar control (operate)…" para un entorno ya emparejado. |
