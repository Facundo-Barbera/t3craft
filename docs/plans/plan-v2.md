# T3Craft — plan v2

> **v2.1, cambio de arquitectura (Facundo, 2026-11-07). Prevalece sobre §0 y sobre las menciones
> al puente de §2-§3.** El mod se empareja **directo** con cada instancia de T3, como la app de
> escritorio con un servidor remoto: se pega un link o código de pairing por entorno, sin
> pasar por el puente. El puente (`bridge/`) queda como **opción legacy** (modo `bridge`); no se
> borra. Desde la limpieza de 2026-11-09 además está **congelado** (D2, #7): solo arreglos críticos, ninguna
> feature nueva (`bridge/README.md`).

## A. Emparejado con T3: investigación

Fuentes: `~/projects/t3code-ref` (commit `15fe2832`), contrastado con el servidor instalado en
el VPS (`0.1.8-nightly.20261107.4314`, leyendo el binario sin modificarlo). Coinciden en todo lo
de abajo.

**Credencial de pairing** (`PairingGrantStore`, `AuthPairingLinks`, `cli/auth.ts`, `cli/pair.ts`):
- Es un *bootstrap credential* de **un solo uso**, con TTL por defecto de **5 min** (`--ttl` lo
  cambia), etiqueta opcional y una lista de **scopes**.
- Se emite en el host:
  - `t3 auth pairing create --scope orchestration:read [--ttl 15m] [--label …] [--base-url https://host]`, o
  - `t3 pair --scope orchestration:read` (además imprime un QR y puede configurar Tailscale Serve), o
  - desde la UI web de "Sharing & access", o
  - `POST /api/auth/pairing-token` con una sesión que ya tenga permisos de acceso
    (`AuthCreatePairingCredentialInput {label?, scopes?}` → `AuthPairingCredentialResult {id, credential, label?, expiresAt}`).
- **Ojo:** sin `--scope`, la credencial lleva **todos** los scopes estándar
  (`AuthStandardClientScopes`, operate incluido). Para T3Craft siempre se emite con
  `--scope orchestration:read`.
- **Link:** `https://<host>/pair#token=<credential>` (`buildPairingUrl`; el token va en el
  fragmento, así que no sale en logs HTTP). El "código" es la misma credencial sin URL.
- Revocar una credencial que no se usó: `t3 auth pairing revoke --id <id>` o
  `POST /api/auth/pairing-links/revoke`.

**Canje** (`POST /oauth/token`, `EnvironmentAuth.exchangeBootstrapCredentialForAccessToken`, `auth/http.ts`):
- Form-urlencoded:
  - `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`
  - `subject_token=<credential>`
  - `subject_token_type=urn:t3:params:oauth:token-type:environment-bootstrap`
  - `requested_token_type=urn:ietf:params:oauth:token-type:access_token`
  - `scope=orchestration:read`
  - `client_label=T3Craft (Minecraft)`, `client_device_type=desktop`, `client_os=…`
- Scopes concedidos = **pedidos ∩ los de la credencial**. Si la intersección es vacía, falla con
  `invalid_scope`. **El cliente puede pedir solo `orchestration:read`, aunque la credencial
  traiga más.**
- Respuesta: `{access_token, issued_token_type, token_type:"Bearer"|"DPoP", expires_in, scope}`.
- Sin cabecera `dpop` se obtiene una sesión **Bearer de 30 días** (`DEFAULT_SESSION_TTL`). Con
  DPoP (prueba de posesión de una clave, `proofKeyThumbprint`) el token dura **1 h**, y cada
  ticket WS y el upgrade exigen pruebas DPoP.
- **No hay refresh token.** Cuando vence, se vuelve a emparejar. El mod avisa antes del
  vencimiento (`expires_in`).
- La sesión aparece en el host como un cliente con su etiqueta. Se revoca con
  `t3 auth session list` / `t3 auth session revoke <id>`, en Sharing & access, o con
  `POST /api/auth/clients/revoke`.

**Uso de la sesión** (igual que el puente; ya funciona en `bfaebf1`):
`POST /api/auth/websocket-ticket/rpc` con `Authorization: Bearer` → ticket de 5 min y un solo
uso → `wss://<host>/ws?orchestrationProtocol=2&wsTicket=<ticket>`. Effect RPC con JSON:
- El cliente manda `{"_tag":"Request","id":"1","tag":"orchestration.subscribeShell","payload":{"requestCompletionMarker":true},"headers":[]}`.
- El servidor responde con `Chunk{requestId,values[]}`. **Espera un `{"_tag":"Ack","requestId"}`
  por cada chunk** antes de mandar el siguiente.
- También hay `Exit`, `Defect`, `ClientProtocolError` y `Ping`/`Pong`.
- Si el parámetro de protocolo no coincide, el servidor responde 426. A las sesiones de clientes
  MCP (OAuth `/oauth/mcp/*`, "pairing-code" de MCP) **las rechaza en `/ws`**, así que ese flujo no
  sirve; se usa el bootstrap de arriba.

**Conclusión sobre los scopes (§4 del pedido):** el emparejado de un cliente **no exige
`orchestration:operate`** ni ningún otro scope además de `orchestration:read`. Se implementa
pidiendo y verificando solo `orchestration:read`. Si la respuesta trae un scope de más, el mod
rechaza el token y no lo guarda.

## B. Arquitectura v2.1

```
[PC: Minecraft + mod]  --HTTPS/WSS por Tailscale-->  vps-env T3        (sesión read propia)
                       --HTTP/WS  por Tailscale-->   <laptop> :3773 (sesión read propia)
[bridge/ en homelab]   opcional, legacy (modo "bridge")
```
- **El mod Java es cliente directo de T3**: WebSocket RPC con `java.net.http`, framing Effect RPC
  JSON a mano (con Ack por chunk y Ping), `requestCompletionMarker:true`, y mantiene los threads
  en los snapshots de enriquecimiento (port de `ShellState` de `bfaebf1`). Reconecta con backoff
  y pide un ticket nuevo en cada intento.
- **La lógica pasa al mod**: mapeo de estados, visibilidad (excluye archivados y settled; incluye
  activos, los que esperan al usuario y los recientes de 24 h; máximo 40 por entorno),
  presentación (colores, glifos, textos) y transiciones. Son puertos de la lógica testeada del
  puente, con tests JUnit.
- **Credenciales** en el perfil de la instancia de Minecraft en la PC:
  `config/t3craft/credentials/<env>.json` (permisos 600 donde el SO lo permite; nunca en el repo,
  y `config/t3craft/credentials/` está en `.gitignore`). Los entornos (id, etiqueta, URL) van en
  `config/t3craft/environments.json`. El token nunca se escribe en logs ni en el chat.
- **UI de emparejado** en el juego (`/t3craft pair` o tecla): nombre/etiqueta del entorno y un
  campo para pegar el link `https://host/pair#token=…`, o el código más la URL del host. Canjea,
  verifica el scope, guarda y conecta. `/t3craft unpair <env>` borra la credencial local (la
  sesión se revoca en el host).
- **Multi-entorno**: vps y laptop a la vez, con ids de NPC `<env>:<threadId>`.
- **Modo**: `config/t3craft.json` → `"mode": "direct" | "bridge"`. Las instalaciones que ya
  tienen `bridgeUrl` y no tienen `mode` siguen en `bridge`; las instalaciones nuevas arrancan en
  `direct`.
- **Iteración rápida**: `/t3craft reconnect` y `/t3craft reload` (relee la config, los entornos y
  las credenciales, y reconecta) sin reiniciar el juego.

## C. Prioridad de implementación (v2.1)
1. **(a)** Cliente directo con pairing **solo lectura** (`orchestration:read`): pairing, almacén
   de credenciales, conexión RPC, ShellState, filtro y presentación en el mod. Con tests JUnit.
2. **(b)** NPC tipo jugador (skin + nametag + ícono/partículas de estado).
3. **(c)** `/t3craft reconnect` y `/t3craft reload` funcionando en modo directo.
4. Después, lo demás de §2 (barrios, chat con historial vía `subscribeThread`, notificaciones,
   HUD peek, resto de `/t3craft`), ahora dentro del mod.

---

*(Lo que sigue es la versión v2.0, centrada en el puente. Sigue valiendo para el modo `bridge` y
para la separación lectura/operate de §1.)*

Estado de partida: `main` @ `c27b0b9`. Fase 1 funcionando: aldeanos coloreados, filtro de
visibilidad por entorno (24 h / 40), puente multi-entorno en <homelab> y mod como vista tonta.
Facundo pide algo mucho más desarrollado.

Referencias de ideas (no se copia código):
- **AgentCraft** (MIT): NPC con skin de jugador y pathfinding, escritorios y monitores con
  archivos y líneas cambiadas, Task Wall, campana + podio cuando hace falta decidir, pantalla de
  diff, tecla J, DevBridge para screenshots. Es MIT: se puede adaptar con atribución
  (`THIRD_PARTY_NOTICES.md` y cabecera del archivo).
- **MC-CC**: notificaciones por chat + toast + sonido al pasar a blocked/done/error (el estado
  inicial se siembra en silencio), `/cc` con lista clicable, `/cc history` con markdown, HUD peek
  de una línea y pantalla [K]. Licencia no verificada: solo ideas.
- **Minecraft Agent IDE / minecraft-use (Cheggin)**: aldeanos, texto flotante de progreso, chat
  scrollable y botones Allow/Reject. GPL-3.0 o sin licencia: **solo la idea, ni se lee ni se
  copia el código.**

## 0. Principio que no cambia

El mod es una **vista tonta**. El estado, los textos, los colores, la prioridad, los sonidos de
aviso y la agrupación los decide el puente. Para cambiar el comportamiento basta reiniciar el
puente, sin reiniciar Minecraft. El mod no guarda credenciales de T3.

## 1. Dos zonas de permisos

> **Actualización 2026-11-07:** Facundo dio el OK a la escritura. Primero como segundo
> emparejamiento opcional (`feat/operate-core`) y luego, en `fix/pair-default-operate`, como
> **emparejamiento por defecto** `read + operate` en una sola sesión, con `--read-only` como
> opción explícita. Se mantienen (b) revocación por entorno, (c) confirmaciones y (d) audit log,
> ahora en el mod. Ver `docs/features/operate.md`.

| | **SOLO LECTURA** (`orchestration:read`) | **REQUIERE `orchestration:operate`** |
|---|---|---|
| Métodos T3 | `subscribeShell`, `subscribeThread`, `getThreadProjection`, `getTurnItem`, `getTurnDiff`, `getFullThreadDiff`, `searchThreads` | `dispatchCommand` (`message.dispatch` / turn start, `run.interrupt`, `runtime-request.respond`, `thread.create`, settle), `launchThread` |
| Riesgo | Ve el contenido de los threads (código, mensajes). Es revocable. | **Casi admin**: puede arrancar agentes `full-access` en ese host, aprobar comandos arbitrarios y escribir archivos. Quien controle el puente o el mod controla esas máquinas. |
| Estado | Se implementa ahora (§3). | **No se implementa sin el OK explícito de Facundo.** No se emiten ni se piden tokens operate hasta entonces. |

Por qué separarlo: con `operate`, el puerto 7878 del homelab pasa a ser una superficie de
ejecución remota sobre la VPS y la mini. Antes de habilitarlo hace falta:
(a) `listen.clientKeyFile` obligatorio y/o una ACL de Tailscale que solo deje entrar a la PC;
(b) un token operate **por entorno**, separado del de lectura y fácil de revocar;
(c) confirmación en el juego para cada acción destructiva;
(d) un log de auditoría en el puente.

El puente debe funcionar igual de bien con solo lectura. Si llega un comando de escritura y el
token de ese entorno no tiene `operate`, responde `ack{ok:false, error:"read_only"}`.

## 2. Prioridades

### P0: iteración rápida (lo primero)
- `/t3craft reconnect`: corta y reabre el WS al puente.
- `/t3craft reload`: relee `config/t3craft.json` (bridgeUrl, bridgeKey) y reconecta. No hace
  falta reiniciar Minecraft. F3+T solo recarga recursos y no sirve para esto.
- `/t3craft status`: URL, estado del enlace y estado de cada entorno.
- En el puente: `bridge.reload` (pedido desde el mod) relee el bloque `visibility` de
  `bridge.json` y reevalúa. Cambiar los entornos o los tokens sigue requiriendo reiniciar el
  servicio.
- Flujo para desplegar el jar (lo hace Taller con su script): `./gradlew build` → copiar
  `mod/build/libs/t3craft-<v>.jar` a `mods/` de la instancia → reiniciar Minecraft. Mientras
  tanto, los cambios de lógica o texto solo necesitan redesplegar el puente; el mod se reconecta
  solo, o con `/t3craft reconnect`.

### P1: solo lectura, visible
1. **NPC tipo jugador.** Un `RemotePlayer` solo de cliente (sin tipos de entidad registrados, así
   se puede entrar a cualquier servidor) con skin por defecto elegida de forma determinista por
   entorno + estado, con un UUID sintético. El nametag es la `label` del puente en su color.
   Encima, un `TextDisplay` solo de cliente con el ícono de estado, el `sublabel` y la
   `activity`. Partículas sobre la cabeza según el estado (las elige el puente con
   `particle`: `working`→enchant, `needs_attention`→note, `error`→angry, `done`→happy).
2. **Barrios.** El puente agrupa por entorno + proyecto (`groupId`, `groupLabel`). El mod
   coloca cada barrio en una celda de una cuadrícula alrededor del ancla y le pone un letrero
   flotante (`TextDisplay` grande) con el nombre del proyecto y del entorno.
3. **Chat con historial real** (clic derecho o `/t3craft history`). El puente se suscribe con
   `subscribeThread` solo mientras haya un chat abierto en algún mod. `chat.history` va
   paginado (los 50 más recientes; "cargar más" con un cursor) y `chat.message` es un upsert en
   vivo, incluso mientras el mensaje está en streaming. Pantalla scrollable con markdown básico
   (títulos, **negrita**, `código`, listas, bloques de código) y el último mensaje abajo. El
   input queda deshabilitado con el aviso "responder requiere operate".
4. **Notificaciones.** El puente manda `npc.transition` (de/a, título, texto, color, `sound`)
   cuando un thread pasa a necesita-input / terminó / error. El estado inicial de cada entorno
   se siembra en silencio (no hay spam al arrancar ni al reconectar). El mod lo muestra en chat
   (clicable para abrir el chat), como toast y con sonido.
5. **HUD peek.** Una línea arriba en pantalla con el thread más relevante (el puente manda
   `hud.peek`: primero los que necesitan atención, luego los que trabajan), más un banner de
   conexión cuando el enlace o un entorno no está ok.
6. **`/t3craft`**: `status`, `reconnect`, `reload`, `list` (clicable: abrir chat o tp), `history
   <npc>`, `filter <texto>|clear` (filtro local de vista sobre entorno, proyecto o estado; las
   reglas de visibilidad siguen en el puente), `locate <npc>` (coordenadas + brillo) y `tp <npc>`
   (manda `/tp` normal; requiere cheats en el mundo).

### P2: solo lectura, profundidad
- Monitor o escritorio por NPC con los archivos y líneas cambiadas (`getTurnDiff` /
  `getFullThreadDiff`), y una pantalla de diff.
- Campana + podio en el barrio cuando un thread necesita una decisión. Se ve el detalle de la
  solicitud (`runtimeRequests` de la projection), sin poder responder.
- `searchThreads` desde `/t3craft search`.
- Pathfinding/estaciones (idea de AgentCraft: NPC que camina a su escritorio cuando trabaja).
- Tecla J o K para abrir una tarjeta del thread más relevante.
- DevBridge local (127.0.0.1) para screenshots automáticos: solo en dev, nunca en la VPS.

### P3: REQUIERE `orchestration:operate` (bloqueado hasta el OK de Facundo)
- Responder desde el chat (`message.dispatch`).
- Aprobar o denegar permisos con botones Allow once / Always / Deny (`runtime-request.respond`),
  y responder `user_input`.
- Interrumpir (`run.interrupt`, con runId = `activeRunId`).
- Crear threads (`thread.create` o `launchThread`) eligiendo el proyecto del barrio.
- Settle.
- Precondiciones: las de §1 (clave del puente o ACL, token operate por entorno, confirmación y
  auditoría).

## 3. Implementación de este ciclo (solo lectura)

En commits pequeños y en este orden, con `pnpm test` y `./gradlew build` en verde en cada paso:
- **0.** Mod: `/t3craft reconnect | reload | status`.
- **a.** Puente: historial paginado (`chat.open/close/history/message`), eventos
  `npc.transition`, agrupación (`groupId/groupLabel`), `hud.peek`, `bridge.reload`. Con tests.
  Se actualiza `protocol/protocol.md`.
- **b.** Mod: NPC tipo jugador + nametag + ícono/partículas + barrios con letrero.
- **c.** Mod: pantalla de chat con historial real.
- **d.** Mod: notificaciones (chat/toast/sonido) + HUD peek + banner.
- **e.** Mod: resto de `/t3craft` (`list`, `history`, `filter`, `locate`, `tp`).

## 4. Riesgos
- La projection completa puede ser grande en threads largos. Primero se pide la projection
  entera y se pagina en el puente; después se evalúa `acceptBoundedSnapshot` con `historyCursor`.
- `RemotePlayer` sin `PlayerInfo` usa la skin por defecto del UUID. Las skins personalizadas
  necesitarían texturas propias (P2).
- La versión de T3 se mueve rápido: hay que seguir decodificando con los contratos vendorizados
  y manejar los eventos desconocidos con tolerancia.
