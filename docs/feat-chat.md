# feat/chat: chat/historial real de un hilo (solo lectura)

Paquete: `dev.t3craft.client.chat` (mod/src/client/java). Tests: `mod/src/test/java/dev/t3craft/client/chat`.

## Qué hace

Al hacer clic derecho sobre un NPC (o desde cualquier UI con `ChatScreens.open(envId, threadId)`),
se abre una pantalla con el historial real del hilo:

- Filas con rol (You / Agent / Thinking / Plan / To-do / Tool / Subagent / Needs you / System /
  Error), hora y color por rol. Si una fila viene del hilo padre (fork o side chat), lo indica.
- Markdown básico: títulos, párrafos, listas con viñeta, numeradas, anidadas y de tareas, citas,
  separadores, **negrita**, *cursiva*, `código` y bloques de código en fuente monoespaciada
  (`minecraft:uniform`) sobre fondo oscuro. Los enlaces (`[texto](url)` y URLs sueltas) se ven
  subrayados: al pasar el ratón muestran un tooltip y al hacer clic se copian al portapapeles
  (con un toast). No se abre el navegador.
- Tool calls plegables (comando, edición de archivo, búsqueda, web, herramienta dinámica,
  subagente y razonamiento). Empiezan plegados; un clic en la cabecera los despliega y muestra la
  salida o el diff en monoespaciado. La salida se recorta a 8000 caracteres.
- Cabecera: título del hilo, entorno, estado actual (`ThreadState`: needs you / working / error /
  done / idle, con su motivo) y **quién lo necesita**. Ejemplo: "You: approve command, asked by
  subagent "Researcher" · 3 min ago". Sale de las `runtimeRequests` pendientes y los
  `subagents` de la projection. También indica si la conexión está en vivo, reconectando o falló.
- Scroll con rueda, flechas, PgUp/PgDn e Inicio/Fin, más una barra de scroll. La vista queda
  pegada abajo mientras llegan mensajes; si subiste, conserva tu posición y aparece
  "▼ newest (End)".
- Carga incremental: llega primero el snapshot acotado (la ventana más reciente). Al llegar arriba
  del todo, o con clic en "▲ Load earlier messages", se pide la página anterior y se antepone sin
  mover lo que estabas mirando.
- En vivo: upserts de turn items (texto en streaming, salida de herramientas), `message.updated`,
  runs, runtime requests, subagentes y título.
- El campo de respuesta está visible pero deshabilitado, con el aviso "Replying requires operate
  (read-only pairing)" y un tooltip que explica que haría falta `orchestration:operate`.

## RPC de T3 que usa (solo `orchestration:read`)

Lo revisé en T3 Code (`packages/contracts/src/orchestrationV2.ts`, `environmentHttp.ts` y
`apps/server/src/orchestration-v2/http.ts`). Los dos endpoints hacen
`requireEnvironmentScope(AuthOrchestrationReadScope)`:

1. **WS `orchestration.subscribeThread`**
   `{threadId, requestCompletionMarker:true, acceptBoundedSnapshot:true}`. Devuelve
   `OrchestrationV2ThreadStreamItem`: un `snapshot` (projection acotada con `visibleTurnItems`,
   `historyCursor`, `hasMoreHistory`), después `synchronized` y luego `event`s (`turn-item.updated`,
   `message.updated`, `run.*`, `runtime-request.updated`, `subagent.updated`, `thread.*`). No
   pide `compactIds`, `dedupeBodies`, `compactTurnItems` ni `deferredWork`, así que el JSON llega
   plano. Usa el mismo framing que `T3Connection` (Ack por Chunk, Ping cada 5 s, ticket nuevo por
   conexión y backoff con jitter).
2. **HTTP `GET /api/orchestration/threads/:threadId/history?cursor=…`** con
   `Authorization: Bearer` y `x-t3-orchestration-protocol: 2`. Devuelve
   `OrchestrationV2ThreadHistoryPage` (`items` del más antiguo al más nuevo, `nextCursor`,
   `hasMoreHistory`). La fusión se deduplica por `sourceThreadId:sourceItemId`, igual que
   `client-runtime/state/threadHistoryMerge.ts`.

Nunca se envía `dispatchCommand`, `launchThread` ni nada que requiera `operate` (hay un test que
lo comprueba).

## Diseño / archivos

| Archivo | Qué es | ¿Minecraft? |
|---|---|---|
| `Markdown.java` | Parser tolerante: bloques + spans inline | no (testeado) |
| `TextWrap.java` | Word-wrap de spans con posiciones x (hit-test de enlaces) | no (testeado) |
| `ChatEntry.java` | Fila del chat + `Kind` (rol, color, plegable) | no |
| `TurnItems.java` | JSON de turn item → `ChatEntry` (todos los tipos del contrato) | no (testeado) |
| `ChatTranscript.java` | Modelo thread-safe: snapshot, eventos, páginas, estado y quién lo necesita | no (testeado) |
| `ThreadFeed.java` | Socket propio + HTTP de historial; vive mientras la pantalla está abierta | no (helpers testeados) |
| `ChatSource.java` | Interfaz mínima: envId → (url, token, label) + HttpClient | no |
| `ChatScreens.java` | Fachada: `open(envId, threadId)`, `openFor(NpcInfo)`, `create(...)`, `defaultSource()` | sí |
| `ChatScreen.java` | La pantalla | sí |

**Hot-swap:** no hay estado estático mutable (solo constantes inmutables). Cada `ChatScreen` es
dueña de su `ThreadFeed`: lo arranca en el primer `init()` y lo cierra en `removed()` (Esc, Done,
cambio de pantalla). Eso corta el socket y apaga su executor daemon. No queda ningún registro en
memoria que haya que limpiar.

## Puntos de integración (código fuera de mi paquete)

1. **`T3CraftClient` (clic derecho sobre el NPC):** cambié una línea.
   `new ThreadChatScreen(info)` pasó a ser `dev.t3craft.client.chat.ChatScreens.openFor(info)`, y
   quité el import. Los ids de NPC en modo directo son `env:threadId` (`Presentation.npcId`). En
   modo puente (legacy) se muestra un aviso: el chat necesita modo directo.
2. **`mod/build.gradle`:** el source set `test` ahora ve `sourceSets.client.output`, para poder
   testear las clases puras del cliente. Los tests no cargan clases de Minecraft.
3. **`client/screen/ThreadChatScreen.java`:** ya nadie lo usa. Lo dejé sin tocar porque no es mío;
   Taller puede borrarlo al integrar.
4. **Para otras ramas:**
   - `/t3craft history <npc>` (rama de comandos):
     `client.openNextTick(ChatScreens.create(ChatSource-por-defecto, envId, threadId, título))` o
     `ChatScreens.createFor(npcInfo)`. Hay que usar `openNextTick`, porque la pantalla de chat del
     juego se cierra justo después del comando.
   - Notificaciones clicables: lo mismo, con el `env:threadId` del NPC.
   - Otra fuente de credenciales: implementar `ChatSource`.
5. **Dependencias de `dev.t3craft.t3` (solo lectura, sin cambios):** `RpcFrames` (request, ack,
   ping, parse y `SUBSCRIBE_THREAD`), `Json`, `T3Connection.sanitize/ORCHESTRATION_PROTOCOL`,
   `DirectController.environments()/http()`, `Environments.list()/credential()`,
   `Pairing.Session` y `ThreadState`.

## Verificación

- `./gradlew build` en `mod/`: verde. JUnit: 92 tests, de los cuales 42 son nuevos (`MarkdownTest`,
  `TextWrapTest` y `ChatTranscriptTest`, este último con helpers de RPC y URI).
- No lo probé contra un T3 real ni en Minecraft: las reglas lo prohíben en el VPS. Los JSON de los
  tests siguen la forma de los contratos de T3 Code.

## Pendientes

- Probar en el juego: layout con escalas de GUI distintas, rendimiento con hilos muy largos
  (el layout se recalcula entero en cada cambio; si pesa, cachear por fila) y legibilidad de la
  fuente `uniform`.
- Comandos con `outputOmitted`: se puede traer la salida con `orchestration.getTurnItem` (read) al
  desplegar.
- `acceptDeferredWork` y las vistas `conversation`/`work` del historial, para colapsar el trabajo
  como en T3 Code.
- Al reconectar se pide un snapshot nuevo y se pierden las páginas antiguas ya cargadas, a
  propósito, por simplicidad. Se podría reanudar con `afterSequence`.
- El "hace X min" de la cabecera solo se recalcula cuando cambia el transcript.
- Responder, aprobar e interrumpir quedan bloqueados (P3, requieren `operate`).
