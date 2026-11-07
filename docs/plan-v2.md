# T3Craft — plan v2

Estado de partida: `main` @ `c27b0b9`. Fase 1 funcionando: aldeanos coloreados, filtro de
visibilidad por entorno (24 h / 40), puente multi-entorno en my-homelab y mod como vista tonta.
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
