# T3Craft — plan técnico

> **Histórico.** Este plan describe la arquitectura original, centrada en el puente. Hoy el puente (`bridge/`,
> `protocol/`) es **legacy y está congelado** (D2, #7: `bridge/README.md`). El camino vigente es el modo directo
> (`docs/plans/plan-v2.md`, v2.1).

> **Actualización de arquitectura (aprobada por Facundo, 2026-11-07). Prevalece sobre las secciones 3 y 5 de abajo.**
>
> 1. **El puente corre en <homelab>** (Ubuntu 24.04, siempre prendido) como servicio systemd
>    (`bridge/deploy/t3craft-bridge.service`, ejemplo sin secretos). Escucha **solo** en la IP de
>    Tailscale del homelab (`listen.host` en `/etc/t3craft/bridge.json`). La config rechaza
>    `0.0.0.0`, `::` y cualquier IP que no sea loopback, `100.64.0.0/10` o `fd7a:115c:a1e0::/48`, y
>    el socket lo vuelve a comprobar al hacer bind.
> 2. **El puente es cliente de varios T3 a la vez**: hoy son vps-env
>    (`https://<vps>.<tailnet>.ts.net`) y el portátil <laptop> (puerto 3773).
>    Cada entorno tiene su propia sesión revocable, con el token en un archivo fuera del repo
>    referenciado por `environments[].tokenFile`. Los threads de todos los entornos se juntan, y cada
>    NPC lleva `env`/`envLabel`. Ids únicos: `<env>:<threadId>`.
> 3. **El mod (PC) solo habla con el puente del homelab por Tailscale.** La URL se configura en
>    `config/t3craft.json` (`bridgeUrl`; `ws://127.0.0.1:7878/` queda solo como default). El mod no
>    guarda credenciales de T3 y es una **vista tonta**: el puente resuelve el estado, el color, el
>    glifo, la etiqueta, la prioridad y el texto de los avisos, y los manda listos para dibujar.
>    Así se puede iterar reiniciando solo el puente. Hay una clave opcional de acceso al puente
>    (`listen.clientKeyFile` ↔ `bridgeKey`), que no da acceso a T3.
> 4. El despliegue a la PC y al homelab lo hace otro agente; este repo solo compila (jar del mod y
>    puente) y corre tests.
>
> Protocolo actualizado en `protocol/protocol.md` (fuente de tipos: `bridge/src/protocol.ts`).
>
> Diagrama vigente:
> ```
> [PC Windows: Minecraft + mod]  --ws://<homelab tailscale>:7878 (t3craft v1)-->  [<homelab>: bridge (systemd)]
>                                                                                   ├─ HTTPS/WSS → vps-env T3 (token propio)
>                                                                                   └─ HTTP/WS  → <laptop> T3 :3773 (token propio)
> ```
> Decisión sobre `status=="waiting"` (revisada el 8 nov 2026): es un estado vivo de la corrida
> (espera una herramienta, un `thread_wait`, subagentes o tareas de fondo; está en
> `LIVE_SHELL_STATUSES`), así que t3craft lo muestra como *working*, igual que T3 Code. Solo un
> `pendingRuntimeRequest` bloqueante, un plan accionable o mensajes de invitado pasan a
> *needs_attention* (§1.4). Antes cada hilo pasaba por "needs your attention" antes de terminar.
> Regla en `bridge/src/state/mapper.ts` y `mod/.../t3/ThreadState.java`.

---

*(Plan original, investigación de solo lectura del 7 nov 2026, copiado tal cual. Decisión tomada: MC 26.3 + Java 25.)*

# T3Craft — plan técnico (investigación de solo lectura, 7 nov 2026)

Fuentes clonadas (shallow, solo lectura) en `/workspace/t3craft/t3code` y `/workspace/t3craft/agentcraft`.
t3code y agentcraft: licencia MIT. pixel-agents: MIT (solo se leyó el README).

## 1. Hallazgos del RPC de T3 Code

### 1.1 Autenticación (docs/internals/environment-auth.md, apps/server/src/auth/*, packages/contracts/src/environmentHttp.ts)
- El entorno emite sus propias sesiones con **scopes**. Lista (packages/contracts/src/auth.ts): `orchestration:read`, `orchestration:operate`, `terminal:read|operate`, `filesystem:read|write`, `access:read|write`, `relay:*`, `settings:write`, `providers:manage`, etc.
- Cada método RPC declara su scope (apps/server/src/auth/RpcAuthorization.ts): `subscribeShell`/`subscribeThread`/`getThreadProjection` → `orchestration:read`; `dispatchCommand`, `launchThread` → `orchestration:operate`.
- **Token con permisos mínimos** (headless; apps/server/src/cli/auth.ts, docs/user/remote-access.md:379):
  - Fase MVP (solo ver): `npx t3 auth session issue --scope orchestration:read --token-only` (ejecutado por Facundo en el VPS).
  - Fases chat/permisos/crear: añadir `--scope orchestration:operate`. Ojo: el doc avisa que `orchestration:operate` puede lanzar un agente full-access y, por ende, es casi equivalente a admin. Guardar como secreto fuerte.
  - Alternativa: `npx t3 pair --scope ...` (enlace de emparejamiento de un solo uso que el cliente canjea por un token).
  - Las sesiones con subject `mcp-client` (OAuth de `/mcp`) son **rechazadas** en `/ws`, así que no sirven para el puente.
- **Flujo WebSocket**: los clientes bearer no ponen el token en la URL:
  1. `POST https://<vps>.<tailnet>.ts.net/api/auth/websocket-ticket/rpc` con `Authorization: Bearer <token>` → `{ ticket, expiresAt }` (AuthWebSocketTicketResult, contracts/auth.ts:241). Ticket de vida corta y de un solo uso para la ruta `rpc`.
  2. `GET wss://…/ws?orchestrationProtocol=2&wsTicket=<ticket>` (apps/server/src/ws.ts:3506; `ORCHESTRATION_PROTOCOL_QUERY_PARAM="orchestrationProtocol"`, `ORCHESTRATION_PROTOCOL_VERSION=2` en contracts/environment.ts:13-15; `wsTicket` en EnvironmentAuth.ts:583). Si el protocolo no coincide, el servidor responde 426 `orchestration_protocol_incompatible`.
  3. Hay que reemitir el ticket en cada reconexión.
- Tailscale Serve funciona porque issuer/URLs se derivan de `Host` + `X-Forwarded-Proto`. El servicio headless solo acepta peers loopback/privados/tailnet (docs/internals/remote.md), así que no queda expuesto públicamente. No hay que usar T3 Connect ni el relay.
- `GET /api/auth/session` sirve para comprobar el token y ver `scopes`/`permissions`.

### 1.2 Formato de mensajes
- Servidor: `effect/rpc` `RpcServer` sobre WebSocket con **`RpcSerialization.layerJson`** (ws.ts:3575). El cliente oficial usa lo mismo (packages/client-runtime/src/rpc/session.ts:268). Effect 4.1.2 (pnpm-workspace.yaml).
- Framing JSON de Effect RPC (verificar contra effect 4.1.2 antes de implementarlo a mano):
  - Cliente→servidor: `{"_tag":"Request","id":"<bigint-string>","tag":"orchestration.subscribeShell","payload":{...},"headers":[]}`; además `{"_tag":"Ack","requestId":...}` para cada chunk de stream, `{"_tag":"Interrupt","requestId":...}` y `{"_tag":"Ping"}`.
  - Servidor→cliente: `{"_tag":"Chunk","requestId":...,"values":[...]}` en streams, `{"_tag":"Exit","requestId":...,"exit":{"_tag":"Success"|"Failure",...}}` y `{"_tag":"Pong"}`.
- **Recomendación**: el puente debe usar `effect` 4.1.2 + `RpcClient` con los esquemas de `@t3tools/contracts`, en vez de reimplementar el framing. Ese paquete es `private` y no está en npm, así que hay que vendorizarlo como git submodule pinneado a un commit o copiar los esquemas necesarios (MIT, con atribución). Así se valida la decodificación con los mismos Schemas que usa T3.
- Los DateTime viajan como strings ISO. Los snapshots de thread pueden traer `idTable` si el cliente pide `acceptCompactIds`; para simplificar, **no** pedir las opciones `accept*` compactas.

### 1.3 Métodos (packages/contracts/src/orchestrationV2.ts:3590-3612, rpc.ts:1734/1867/1874)
- `orchestration.subscribeShell` (stream). Input (:3692): `{ afterSequence?, requestCompletionMarker?, omitIdleSubagents?, omitOlderSettled? }`. Ítems (:2242):
  - `{kind:"snapshot", snapshot:{snapshotSequence, threads[], archivedThreads[], projects[]}}`
  - `{kind:"synchronized"}`
  - `{kind:"thread.updated", sequence, location:"active"|"archive", thread: ThreadShell}`
  - `{kind:"thread.removed", ...}` y `project.updated|removed`

  **Con esto basta para el MVP**: un NPC por `ThreadShell`.
- `ThreadShell` (:2078-2170). Campos útiles: `id, projectId, title, status, activityRunStatus, pendingRuntimeRequest{id,kind,createdAt,blocking?}, latestVisibleMessage{role,text}, lastError, hasActionableProposedPlan, activeRunId, latestRunId, settledAt, archivedAt, deletedAt, lineage, pendingBackgroundTasks, runtimeMode, modelSelection`.
- `orchestration.subscribeThread` (stream). Input (:3797): `{ threadId, afterSequence? , ... }`. Ítems (:3908): `snapshot{snapshotSequence, projection}`, `synchronized` y `event{sequence, event: DomainEvent}`. Tipos de evento: `message.updated`, `turn-item.updated`, `provider-turn.updated`, `runtime-request.updated`, `node.updated`, `plan.updated`, `subagent.updated`, `checkpoint.*`, etc. Los eventos desconocidos se decodifican como unknown y hay que ignorarlos. Se usa para chat (mensajes) y para los detalles de una solicitud de permiso.
- `orchestration.dispatchCommand` (unary, `orchestration:operate`). Input `OrchestrationV2Command`, output `{sequence}` (:3681). El ACK significa que la intención quedó registrada, no que el proveedor terminó (overview.md). `commandId` hace idempotente el reintento, así que conviene generar un UUID por comando.
  - `thread.create` (:2921): `{type, commandId, threadId (lo genera el cliente), projectId, title, modelSelection, runtimeMode: "approval-required"|"auto-accept-edits"|"auto"|"full-access", interactionMode:"default"|"plan", branch|null, worktreePath|null, createdBy:"user", creationSource:"web"|"mobile"|"mcp"|...}`. Hay que copiar `modelSelection` de un thread existente o de `server.getConfig`. Existe también `orchestration.launchThread`, que conviene evaluar: puede crear el thread y enviar el primer mensaje a la vez.
  - `message.dispatch` (:3161): `{type, commandId, threadId, messageId, text, attachments: [], createdBy:"user", creationSource, deliveryIntent?: "auto"|"steer"|"restart"}`.
  - `run.interrupt` (:3243): `{type, commandId, threadId, runId (= shell.activeRunId), reason?}`.
  - `runtime-request.respond` (:3292): `{type, commandId, threadId, requestId, decision?: "accept"|"acceptForSession"|"acceptAlways"|"decline"|"cancel", feedback?, answers? (para kind user_input)}` (decisiones en contracts/providerPolicy.ts:58).
- `RuntimeRequest` (:1216): `kind` ∈ `command|file-read|file-change|mcp-elicitation|permission|dynamic_tool_call|user_input|auth_refresh` y `status` ∈ `pending|resolved|expired|cancelled`. Si `responseCapability.type == "not_resumable"`, no se puede responder en vivo.

### 1.4 Mapeo de estados → color del aldeano
`status` = `"idle"` | RunStatus (`preparing, queued, starting, running, waiting, completed, interrupted, failed, cancelled, rolled_back`, :590). Prioridad de evaluación:
1. **Necesita atención (amarillo/naranja, "!")**: `pendingRuntimeRequest != null && blocking !== false` (cualquier `kind`: aprobación, `user_input`, `dynamic_tool_call`, `auth_refresh`; `blocking: false` = pregunta asíncrona que la corrida sigue de largo). También `hasActionableProposedPlan` y `pendingGuestMessageCount>0`. `status=="waiting"` **no**: es trabajando (ver punto 3).
2. **Error (rojo)**: `status ∈ {failed}` o `lastError != null` sin run activo.
3. **Trabajando (azul/verde parpadeante)**: `activityRunStatus != null`, `status ∈ {queued, preparing, starting, running, waiting}` o `pendingBackgroundTasks.length>0`. Es la misma regla que `isLiveShell` (LIVE_SHELL_STATUSES, :2165).
4. **Terminado (verde)**: `status ∈ {completed}` o (`idle` con `latestRunCompletedAt`). Si además `settledAt != null`, se muestra gris o "dormido". `interrupted/cancelled` → gris.
5. Ocultar los threads con `archivedAt` o `deletedAt`, y los subagentes (`lineage` no listado) salvo que se active una opción.

**Solicitud de permiso pendiente**: el shell ya trae `pendingRuntimeRequest` con id y kind. El detalle (comando, archivo, opciones) está en la projection del thread (`subscribeThread` o `getThreadProjection`) y en los eventos `runtime-request.updated`. Queda pendiente confirmar en qué campo de la projection o del turn-item va el texto del comando y las `ProviderApprovalOption` (label/warning).

## 2. AgentCraft (MIT) y pixel-agents (MIT)
AgentCraft (`blendi-remade/agentcraft`) es casi el mismo producto (Fabric 26.3, NPCs agente, permisos, podio), pero con su propio orquestador "foreman" sobre el Claude Agent SDK. Qué partes sirven:
- **Protocolo mod⇄puente** (docs/protocol.md): WS en `127.0.0.1:7878`, sobre JSON `{v,type,id?}`, `hello`→`snapshot`→upserts, `ack{re,ok}` y reconexión con backoff. También rechaza conexiones con `Origin` o con un Host que no sea loopback, lo que protege de páginas web. **Reutilizable como diseño**.
- Enums `AgentState` (idle/thinking/…/waiting_user/blocked/done/error) y `DecisionKind`. Etiquetas de permiso del tipo "Allow once / Always allow / Deny", que mapean a `accept/acceptForSession/decline`.
- Mod: `client/foreman/ForemanLink` (WS con `java.net.http`, envíos encadenados), `client/agents/*` (entidad solo-cliente `ClientAgentEntity`, `Nameplate`, `SpeechBubble`, `AgentParticles`, `AgentCardScreen`, `GridPathfinder`), `client/permissions/*` y `client/hud/{Toasts,ConnectionBanner,HudSounds}`. Se pueden copiar o adaptar archivos concretos conservando el aviso MIT ("Copyright (c) 2026 AgentCraft contributors") en `THIRD_PARTY_NOTICES.md` y en la cabecera de cada archivo copiado.
- `mod/DEV.md` documenta bien los cambios de API de 26.x (SDL3 en lugar de GLFW, nombres Mojang). Se recomienda leerlo antes de escribir código del mod.
- Lo que no sirve: el foreman entero (sustituido por el puente a T3), el HQ, la Task Wall y el merge.

pixel-agents: ideas de visualización, como burbujas de diálogo cuando el agente espera input o permiso, animación según la actividad (escribir/leer) y una máquina de estados por personaje. Su modelo `AgentEvent` normalizado equivale a nuestro `NpcState`.

Cheggin/minecraft-use (GPL-3.0) y minecraft-agent-ide (sin licencia): **no se revisaron ni se usaron**.

## 3. Arquitectura recomendada
*(Reemplazada por la actualización del principio: el puente ya no corre en el PC sino en <homelab>, y se conecta a varios entornos.)*
```
[Minecraft + mod Fabric (cliente)] --ws://127.0.0.1:7878 (JSON t3craft v1)--> [bridge TS (Node 22+), en <pc>]
     --HTTPS/WSS por Tailscale--> https://<vps>.<tailnet>.ts.net (T3 server: /api/auth/websocket-ticket/rpc, /ws)
```
- **El puente corre en <pc> (Windows), junto al juego**:
  - El mod solo habla con loopback, que no se expone.
  - El token de T3 vive en el PC, en la variable de entorno `T3CRAFT_T3_TOKEN` o en un archivo fuera del repo bajo `%APPDATA%\t3craft\` (opcionalmente cifrado con DPAPI).
  - En el VPS no se instala ni se abre nada.
  - La alternativa de poner el puente en el VPS escuchando solo en la IP tailnet suma superficie (otro puerto en el VPS) sin ganar nada. Solo tendría sentido para varios PCs.
- Protocolo **t3craft v1**, inspirado en AgentCraft:
  - Envelope `{v:1,type,id?}`.
  - Servidor→mod: `snapshot{npcs[]}`, `npc.upsert{id,title,project,state,activity,pending?}`, `npc.remove`, `chat.history`, `chat.append`, `permission.open{threadId,requestId,kind,summary,options[]}`, `permission.closed`, `notify{level,text,threadId}` y `bridge.status{t3:"ok|auth_failed|offline"}`.
  - Mod→servidor: `hello`, `chat.open{threadId}`, `chat.close`, `chat.send{threadId,text}`, `run.interrupt{threadId}`, `permission.respond{threadId,requestId,decision,feedback?}` y `thread.create{projectId,title,text}`, todos con `ack`.
  - Seguridad: rechazar `Origin` y cualquier Host que no sea loopback. Además, un token local opcional (generado por el puente en `%APPDATA%`, que el mod lee) para que otros procesos locales no puedan aprobar permisos.
- El puente mantiene una sola suscripción `subscribeShell` y un `subscribeThread` solo por cada chat abierto o permiso pendiente. Al reconectar, usa `afterSequence` con un ticket nuevo y backoff.
- En el mod, los NPC son **entidades solo-cliente** (no hace falta servidor ni mod en el servidor, y funciona en un singleplayer local). Se colocan en un "corral" cerca del jugador o en coordenadas configurables. El color se muestra con nameplate, partículas o un bloque de lana en la cabeza. Clic derecho (UseEntityCallback client-side o raycast propio) abre `ChatScreen`. Los avisos van por toast + sonido + "!" sobre el NPC.

## 4. Versiones (verificadas en meta.fabricmc.net y maven.fabricmc.net el 2026-11-07)
| | Recomendado (A) | Alternativa (B) |
|---|---|---|
| Minecraft | **26.3** (último estable) | 1.21.11 |
| Java | **25** (26.x lo exige) | 21 |
| Fabric Loader | 0.20.1 | 0.20.1 |
| Fabric API | 0.165.2+26.3 | 0.141.6+1.21.11 |
| Loom | 1.19.4, plugin no-remap `net.fabricmc.fabric-loom` | 1.19.x, plugin `fabric-loom` (remap) |
| Mappings | ninguno (26.x no está ofuscado, nombres Mojang) | Yarn 1.21.11+build.6 |
| Gradle | 9.x (wrapper del template oficial) | idem |
| Bridge | Node 22/24 LTS, TS, pnpm, `effect@4.1.2`, `ws` | idem |

**Conflicto**: el pedido dice "Java 21 + Yarn", pero Yarn ya no existe para 26.x y 26.x requiere Java 25. Se recomienda la opción A, que es la línea viva y la misma que AgentCraft (eso facilita reutilizar código). La B es 1.21.11, una versión antigua. Facundo debe decidir. *(Decidido: A.)*

## 5. Monorepo
```
t3craft/
  mod/      Gradle (Loom), src/main + src/client (splitEnvironmentSourceSets), Java-WebSocket o java.net.http
  bridge/   TS: src/t3/{auth.ts,rpc.ts,shell.ts,thread.ts}, src/state/mapper.ts, src/server/modSocket.ts, src/protocol.ts
  protocol/ protocol.md + JSON schema generado desde bridge/src/protocol.ts (fuente única)
  vendor/t3code (submodule pinneado, solo para @t3tools/contracts) o bridge/src/t3/contracts/ copiado con atribución
  THIRD_PARTY_NOTICES.md, LICENSE (MIT), .gitignore (.env, run/, build/, *.token)
```
*(Implementado: contratos copiados en `bridge/vendor/t3-contracts/` pinneados a t3code `25bf84ab`; además `bridge/deploy/` y `bridge/config.example.json`.)*

## 6. Fases e hitos
0. **Spike del puente**: CLI `bridge probe` que obtiene el ticket, abre `/ws`, se suscribe al shell e imprime los threads con su estado mapeado. Se le agregan tests del mapper con fixtures JSON.
1. **MVP**: el mod lista los threads como aldeanos con color y título, y se actualizan en vivo. Banner de conexión. Solo `orchestration:read`.
2. **Chat**: con clic derecho se ve el historial (`subscribeThread`) y se puede enviar con `message.dispatch` e interrumpir con `run.interrupt`. Requiere `orchestration:operate`.
3. **Permisos y avisos**: toast/sonido/"!" y una pantalla de decisión que envía `runtime-request.respond`, más las respuestas para `user_input`.
4. **Crear threads**: `thread.create` o `launchThread`, eligiendo el proyecto de `snapshot.projects`.
5. Pulido: pathfinding o estaciones, skins por proyecto y configuración.

## 7. Riesgos y dudas abiertas
- El scope `orchestration:operate` es casi admin, porque puede iniciar agentes full-access. Conviene emitir un token solo-lectura para el MVP y uno operate después, y poder revocarlo (`t3 auth` / Sharing & access).
- El framing JSON de Effect RPC no está documentado como API pública y los contratos son `private`. T3 se versiona rápido, así que hay que pinnear el commit de contracts y manejar el 426.
- Falta ubicar exactamente el texto o las opciones de una solicitud de permiso dentro de la projection.
- `modelSelection` obligatorio en `thread.create`: falta ver de dónde tomar el valor por defecto (server.getConfig / settings).
- El esquema `ThreadShell` tiene muchos campos opcionales según la versión del servidor; los decoders tienen que ser tolerantes.
- Hay que decidir la versión de Java/MC (26.3+Java 25 frente a 1.21.11+Java 21).
- El cliente de Minecraft en Windows necesita Tailscale activo para que el puente llegue al VPS.
- *(Nuevo)* Con el puente en el tailnet, cualquier nodo del tailnet puede conectarse al puerto 7878. Antes de la fase 3 (aprobar permisos) conviene activar `listen.clientKeyFile` y/o una ACL de Tailscale que solo deje pasar la PC.
- *(Nuevo)* `<laptop>:3773`: confirmar si se sirve por HTTPS (Tailscale Serve) o por HTTP plano dentro del tailnet. `config.example.json` asume HTTP plano.

## 8. Prompt para el thread de T3 Code (andamiaje)
El prompt de andamiaje que se usó para crear este repo es el pedido original (estructura `mod/`,
`bridge/`, `protocol/`, `docs/`; reglas: sin trailers de IA, sin dev servers expuestos, sin
secretos, sin código GPL ni sin licencia). Lo modifica la actualización de arquitectura del
principio de este documento.
