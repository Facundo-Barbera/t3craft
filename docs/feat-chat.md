# feat/chat: navegador de hilos y chat (solo lectura)

Paquete: `dev.t3craft.client.chat` (mod/src/client/java). Tests: `mod/src/test/java/dev/t3craft/client/chat`
y `mod/src/test/java/dev/t3craft/client/config/ChatPrefsTest.java`.

## Qué hace

Clic derecho sobre un NPC (o `ChatScreens.open(envId, threadId)` desde cualquier UI) abre el
**navegador de hilos**, que imita la consola de AgentCraft:

- **Una sola lista de hilos de todos los ambientes**, ordenada por atención y después por actividad
  reciente: needs you → error → aprobación pendiente → working → done → idle; dentro de cada nivel,
  primero lo más reciente (los de hora desconocida van al final) y los activos antes que los
  settled. Cada fila lleva el **icono del ambiente** (inicial sobre un color estable por ambiente,
  como el sidebar de T3 Code, o el color configurado en `t3craft.json`), el punto de estado de
  AgentCraft (que pulsa si te necesita), el título y la antigüedad ("5m", "3h", "2d").
- **Los ambientes solo existen como filtro**: una pastilla por ambiente encima de la lista. Clic
  muestra u oculta ese ambiente, clic derecho deja solo ese y "All" los muestra todos. El filtro se
  guarda en `t3craft.json` (`chat.hiddenEnvironments`). En la configuración siguen como siempre.
- **La conversación del hilo seleccionado es el panel principal**, con el aspecto de la consola de
  AgentCraft: papel crema, una franja de color por turno, avatar y nombre ("You" en arcilla, "Agent"
  en el color de agente), separadores por minuto, markdown, herramientas plegadas como en los
  monitores de AgentCraft, aprobaciones y peticiones en el bocadillo de AgentCraft, scroll que sigue
  lo último y una barra de comando abajo (deshabilitada, porque es solo lectura).

Del trabajo anterior se mantiene: Markdown, links que se copian al hacer clic, carga incremental del
historial, quién lo necesita, eventos en vivo y la respuesta deshabilitada.

## Estudio de la UI de AgentCraft (referencia)

Clonado en `/tmp/agentcraft-repo/src` (commit `8fe3ad2`, MIT; no se ejecutó nada). Clases leídas:
`console/ConsoleScreen.java`, `console/TextFieldView.java`, `console/TextModel.java`,
`console/ConsoleLog.java`, `ui/{UiStyle,Panels,Kit,TextUtil,StatusMap}.java`, `hud/UiBits.java`,
`monitor/{LogRows,ScreenStyle}.java`, `decisions/DecisionScreen.java`, más
`assets/agentcraft/gui/{ui-style.json,kit.json}`, `palette.json`, `assets-src/ui-style.md` y
`docs/console.md`.

**Nota importante:** la consola de AgentCraft (su "chat") **no usa burbujas**. Es un registro con una
franja de color por hablante. Su sprite `kit/bubble` solo aparece en los bocadillos del mundo
(`agents/SpeechBubble.java`, máximo 3 líneas). Aquí replicamos el registro tal cual y usamos el
bocadillo para lo que equivale a un agente "hablándote": las aprobaciones y peticiones (REQUEST) y
el aviso de "quién lo necesita".

| Aspecto | AgentCraft | Aquí |
|---|---|---|
| Layout | Columna anclada abajo a la izquierda con margen de 8: panel de papel de ancho `min(420, max(260, 62 % del ancho))` y alto `min(fondo − 46, max(120, 60 % del alto))`, barra de comando de 18 px, 5 px por debajo. Detrás, un velo nogal (alpha 92) más un degradado de tinta (0 → 120) en los 2/3 inferiores; el mundo sigue visible y el juego no se pausa. | `ThreadBrowserScreen`: misma columna anclada abajo a la izquierda, margen de 8, ancho `min(640, ancho − 16)` (cabe la lista), alto `max(180, 60 % + 23)` y el mismo velo, sin pausar. Dentro: lista (30 % del ancho, entre 130 y 200 px), un hueco de 4 px y la consola con su barra. |
| Paneles | `panel_paper` nine-slice (padding de 8), cabecera de 20 px (título en tinta, resumen en gris, roster a la derecha), regla de 1 px `#C9BBA3`, pozo `panel_inset`, pie de 14 px con teclas. | Igual, con los mismos sprites copiados a `t3craft:chat/*`. A la derecha de la cabecera van el punto de estado y ‹ n/N ›. Hay una segunda línea con el estado y la conexión, que se omite en el modo compacto. |
| Filas | 10 px de alto. Franja de 2 px en x+3, cara de 8 px en x+8, texto en x+19 con sangría colgante; nombre del hablante en su color (You en `#B4553A`), cuerpo en tinta o gris; separador "23:55" centrado entre reglas `#C9BBA3` cuando cambia el minuto, en `#A39B8E`. | Igual. El avatar es un cuadrado de 8 px con la inicial (Y o A), porque T3 no tiene retratos. El nombre ocupa su propia fila y el cuerpo va debajo: con Markdown, AgentCraft lo junta en una sola línea. Un separador por minuto, o con el día si no es hoy. |
| Burbujas | Solo en el mundo (`bubble` con padding 6/4/6/5 y cola `bubble_tail`). | Sprite `bubble` detrás de las peticiones y aprobaciones, y en la franja "who needs it". |
| Paleta | Papel `#F4EFE6`, papel claro `#FFFBF4`, inset `#E9E1D3`, borde `#C9BBA3`, tinta `#1F1E1D`, gris `#655E55`, deshabilitado `#A39B8E`, fantasma `#BCAD95`, arcilla `#D97757` / `#B4553A`, latón `#C9A227`, nogal `#3B2A20`. Estados: idle `#9C9488`, thinking `#C9A227`, working `#2FA3A0`, waiting `#D97757`, error `#C2413B`, done `#8FA98B`. Monitor: tool `#624E16`, result `#485A48`, error `#9A2F2B`. Diff: add `#455746` sobre `#D6DDCC`, del `#873C2A` sobre `#F2D5C4`, enlace `#874431`, ruta `#6C5415`. | `ChatTheme.warmStudio()` con esos mismos valores, token a token. |
| Fuentes | Fuente vanilla sin sombra, línea de 10 px y escala 1:1 de la GUI. | Igual (`shadow=false`). Los bloques de código usan `minecraft:uniform`. |
| Entrada | `text_field` de 18 px, `>` en latón, placeholder `#BCAD95`, pista a la derecha en gris, caret de 1 px que parpadea cada 500 ms. Enter envía, Shift+Enter añade una línea, crece hasta 6 líneas, Tab autocompleta, ↑↓ recorre el historial y el borrador se conserva. | La barra se dibuja igual (`>` en latón y placeholder "Replying needs orchestration:operate"), pero está **deshabilitada**: la sesión es de solo lectura. |
| Scroll | Modelo por filas con *follow*: sigue lo último hasta que subes. Rueda: 3 filas. PgUp/PgDn: una página. Ctrl+End: lo último. Barra de 6 px (`scroll_track/thumb/grip`) y la pastilla "↓ newer below". | `FeedScroll`, el mismo modelo pero en píxeles (las filas de herramienta miden 13). Rueda: 3 filas. PgUp/PgDn, Home y End. La misma barra y la misma pastilla. Al llegar arriba carga la página anterior sin mover la vista. |
| Herramientas y estado | Monitores: icono de kit de 12 px (bash, edit, read, git, test, memory, message, decision, merge) más el nombre en `tool` y el argumento en `tool_arg`. Nunca parte la línea, acorta los nombres snake_case y tiñe las filas de diff. Puntos de estado de 7 px y un halo que pulsa (1200 ms) solo cuando espera. Caret de bloque que parpadea mientras trabaja. | Las herramientas seguidas se agrupan en una línea plegada ("4 steps · $, Edit, Search", con "…" si alguna sigue en marcha y ✖ si falló). Al desplegarla salen las líneas de monitor (icono, nombre y argumento) y cada una despliega su salida o diff con las filas teñidas. El punto de estado de la cabecera y de las filas pulsa con "needs you". Hay un caret de bloque al final mientras el hilo está en working. |
| Navegación | `` ` `` abre o cierra la consola. Clic en un retrato empieza un @mensaje. En las decisiones: Tab / Shift+Tab para la siguiente o la anterior, ‹ n/N › en la cabecera, 1-9 para elegir y Esc retrocede un nivel o cierra. | ↑↓ o Tab / Shift+Tab: hilo anterior o siguiente (se abre al instante). ‹ n/N › clicables en la cabecera. Clic en una fila. Esc cierra. En el modo compacto, Esc o Retroceso vuelven a la lista primero. |

## Componente reutilizable (API)

Todo se puede incrustar en cualquier `Screen` o marco: nada depende de una pantalla concreta, no
hay estado estático y el tema siempre se inyecta.

```java
// 1. Todo junto: lista + conversación
ThreadBrowser b = new ThreadBrowser(
        ChatScreens.defaultDirectory(),      // ThreadDirectory: filas + ambientes
        ChatScreens.defaultSource(),         // ChatSource: la T3Connection de cada ambiente
        ChatTheme.terminal(),                // tema (o warmStudio(), o toBuilder()...)
        ChatScreens.configFilterStore());    // FilterStore (o null para no persistir)
b.setMode(ThreadBrowser.Mode.AUTO);         // AUTO | SPLIT | COMPACT
b.open("vps:thr_123");                      // opcional
// tick():   b.tick();
// render:   b.setBounds(new Rect(x, y, w, h)); b.render(g, font, mouseX, mouseY);
// input:    b.mouseClicked(mx, my, button); b.mouseScrolled(mx, my, dy);
//           if (!b.keyPressed(keyEvent)) { /* el host: Esc cierra */ }
// removed(): b.close();                    // libera la suscripción compartida del hilo
// atajo:    ChatScreens.browser(theme) crea uno con las fuentes por defecto.

// 2. Solo la lista
ThreadList model = new ThreadList();
ThreadListView list = new ThreadListView(model, theme);
list.setOnFilterChanged(hidden -> ...);    // persistir si quieres
model.update(dir.rows(), dir.environments());      // cada tick
list.setBounds(rect); list.render(g, font, mx, my);
ThreadRow r = list.mouseClicked(mx, my, button);   // fila seleccionada (o null si era una pastilla)
list.tooltip(mx, my); list.mouseScrolled(mx, my, dy); list.setShowChips(false);

// 3. Solo la conversación
ConversationPanel conv = new ConversationPanel(source, theme);
conv.show(envId, envLabel, threadId, title);       // o showNothing("…")
conv.setCompact(true); conv.setShowPrompt(false);
conv.setNavigation(nav);                            // ‹ n/N › (opcional)
conv.setHints("Esc", "back");                       // teclas del pie
conv.setBounds(rect); conv.render(g, font, mx, my); conv.tick(); conv.close();
```

- **`Rect`**: rectángulo en píxeles de GUI (`x, y, w, h`, más `contains`, `inset` y `splitX`).
- **`ChatTheme`**: record inmutable con colores, métricas (`row`, `toolRow`, `listRow`, `margin`,
  `gap`, `pad`), `shadow` y `sprites`. Si `sprites` es `null` (como en `terminal()`), todo se
  dibuja con rellenos planos de los colores del tema, sin los sprites de AgentCraft. Así el portable
  puede aplicar su estilo sin que nada quede cableado. Para variantes: `toBuilder()…build()`.
- **Fuentes de datos**: `ThreadDirectory` (`rows()` y `environments()`, las dos en el hilo del
  cliente), `ChatSource` (acceso de lectura a cada ambiente: `ChatSource.of(T3Connection)`) y `ThreadBrowser.FilterStore`.
- **Pantalla completa**: `ThreadBrowserScreen` (la que usa `ChatScreens`).

### Modo compacto y escalado (para el portable)

- `Mode.AUTO` (por defecto) pasa a **COMPACT** cuando el rectángulo mide menos de **300 × 150**
  píxeles de GUI. Si no, usa **SPLIT**.
- **SPLIT**: lista a la izquierda (30 % del ancho, entre 130 y 200 px), un hueco de `theme.gap()` y
  la conversación ocupando el resto. La conversación tiene cabecera de dos líneas, pie con teclas y
  barra de comando.
- **COMPACT**: un panel cada vez. Primero la lista, con sus pastillas si caben (como mucho dos
  líneas). Enter o un clic abren la conversación a pantalla completa, con cabecera de una línea, sin
  pie y con ‹ n/N › para cambiar de hilo. Esc o Retroceso vuelven a la lista (`keyPressed` devuelve
  `true`). Con la lista a la vista, Esc devuelve `false` y el host decide qué hacer.
- Los tamaños mínimos de dibujo son 40 × 40 para la lista y 60 × 50 para la conversación. Por debajo
  no se dibuja nada. Los textos se recortan con "…" y las pastillas que no caben se omiten (el
  filtro sigue disponible con clic derecho o "All").
- El escalado lo pone el host: todo está en píxeles de GUI, a la escala de GUI de Minecraft. Para
  una pantalla pequeña, el portable puede usar `ChatTheme.terminal().toBuilder().row(9).listRow(11)
  .pad(3)` y `setShowPrompt(false)`.

## Config (`t3craft.json`, aditivo)

```json
"chat": { "hiddenEnvironments": ["vps"] }
```

`T3CraftSettings` gana un componente `chat` (`ChatPrefs`). El constructor de 9 argumentos se
mantiene, así que el cambio no rompe nada. Un archivo sin `chat` se lee como valores por defecto: no
hace falta migración y `schemaVersion` sigue en 1. Los ids inválidos se descartan con un aviso, las
claves desconocidas dentro de `chat` se conservan al guardar y el resto de `with*()` mantiene
`chat`. Las etiquetas y colores de cada ambiente salen de `environments.<id>.label/color`, que ya
existían, y se usan en los iconos.

## Conexión: una sola por entorno (`T3Connection`) y suscripciones con refcount

El chat **no abre sockets propios** (regla de `docs/api.md`). Todo pasa por la conexión única de
cada entorno:

```java
T3Connection c = direct.connection(envId);                 // DirectController, aditivo
StreamRegistry.Subscription sub = c.subscribeThread(threadId, listener);   // compartida
c.threadHistory(threadId, cursor);                         // HTTP …/history con la sesión del entorno
c.getTurnItem(threadId, itemId);                           // RPC puntual (salida completa de una tool)
c.getThreadProjection(threadId);                           // RPC puntual
sub.close();                                               // la última vista hace Interrupt
```

- **`StreamRegistry`** (`dev.t3craft.t3`, Java puro y testeado) multiplexa sobre el socket de la
  conexión. Todas las vistas de un mismo hilo comparten **una sola** suscripción
  `orchestration.subscribeThread` (`requestCompletionMarker`, `acceptBoundedSnapshot`). La primera
  envía el Request y la última `close()` envía el Interrupt. Las que llegan tarde reciben
  `onSubscribed` y un replay de lo ya recibido (snapshot + eventos). Pasado `REPLAY_CAP`
  (4000 items) se fuerza una suscripción nueva para todas. Al reconectar se resuscribe cada hilo
  vivo con un request id nuevo. Si se cae el socket, las llamadas puntuales fallan y las
  suscripciones reciben `onEnded(null)` (pausa, no error). Un listener que lanza excepciones no
  afecta a los demás.
- `T3Connection` enruta los Chunk y Exit por request id ("1" es el shell; el resto va al
  registro) y responde con un Ack a cada Chunk. Los envíos van **en serie** por socket (`send()`
  encadena los futures), porque el WebSocket del JDK no admite envíos solapados.
- **`ThreadFeed`** ya no tiene socket ni executor propios. Mantiene una suscripción vía
  `ChatSource.Env` (adaptador de `T3Connection`) y vuelca el stream en su `ChatTranscript`. Su
  estado (LIVE / CONNECTING / OFFLINE / FAILED) sale del estado de la conexión compartida.
  `close()` libera la suscripción. `fullItem(itemId)` usa `getTurnItem`.
- Se envía solo lectura: `subscribeThread`, `getTurnItem`, `getThreadProjection`, Ack, Ping e
  Interrupt, más `GET …/history`. Nunca `dispatchCommand` ni nada que requiera `operate`.
- Esto deja preparados los monitores en vivo: diez monitores mirando el mismo hilo hacen una sola
  suscripción.

## Consolidación con el portable (ya en main)

- **Un solo `EnvBadge`**: `dev.t3craft.client.chat.EnvBadge`. Tiene un color estable por id (o
  `environments.<id>.color` del config) y la inicial de la última palabra de la etiqueta
  ("my-vps" → V, "my-mini" → M), con texto legible según la luminancia. El held screen
  (`HeldScreen`, `HeldScreenModel`) lo usa. `HeldScreenModel.Badge` y `portable/pending/EnvBadge`
  se eliminaron.
- **Un solo modelo de conversación**: `ChatTranscript` + `ThreadFeed`. Se eliminó
  `portable/pending` (`Conversation`, `ConversationParser`, `ConversationSource`,
  `HttpConversationSource` con su lectura HTTP propia, `FetchPolicy`): nada lo usaba y no tenía
  tests.
- **Un solo orden**: `ThreadOrder` (atención → actividad → activos antes que settled → título →
  clave). Lo usan `ThreadList.ORDER` (`ThreadOrder.ROWS`), `PortableState.ORDER` y
  `HeldScreenModel.order()`. El portable no tiene marcas de tiempo (el modelo de NPC no las trae),
  así que allí la actividad empata. `ThreadOrderTest` comprueba que coinciden.
- Pendiente para D2: incrustar `ThreadBrowser` en `PortableScreen` (con `ChatTheme.terminal()` y
  `Mode.AUTO`) y `/t3craft history`. La API ya está lista (arriba).

## Archivos

| Archivo | Qué es | ¿Minecraft? |
|---|---|---|
| `ThreadBrowser.java` | Widget compuesto (lista + conversación, SPLIT/COMPACT, teclas) | sí |
| `ThreadListView.java` | Widget de la lista con las pastillas de ambiente | sí |
| `ConversationPanel.java` | Widget de la conversación al estilo de la consola de AgentCraft; dueño del `ThreadFeed` | sí |
| `ThreadBrowserScreen.java` | Pantalla completa que aloja el navegador | sí |
| `ChatScreens.java` | Fachada y fuentes por defecto (directorio, origen y filtro en config) | sí |
| `ChatDraw.java` | Kit de dibujo (sprites de AgentCraft o rellenos planos) | sí |
| `ChatTheme.java` | Tema inyectable (`warmStudio`, `terminal`, builder) | no |
| `ThreadList.java`, `ThreadRow.java`, `Attention.java`, `Ago.java`, `EnvBadge.java`, `Rect.java` | Modelo de la lista, orden, filtro e icono | no (testeado) |
| `ChatTurns.java`, `ToolLine.java`, `FeedScroll.java`, `FeedRules.java` | Turnos, líneas de herramienta, scroll y separadores | no (testeado) |
| `Markdown.java`, `TextWrap.java`, `ChatEntry.java`, `TurnItems.java`, `ChatTranscript.java`, `ThreadDirectory.java` | Datos del hilo | no (testeado) |
| `ThreadFeed.java`, `ChatSource.java` | Feed de una vista sobre la suscripción compartida; adaptador de `T3Connection` | no (testeado) |
| `ThreadOrder.java` | Orden único (chat, portable, held screen) | no (testeado) |
| `t3/StreamRegistry.java` (main) | Refcount, replay, resuscripción y llamadas puntuales sobre `T3Connection` | no (testeado) |
| `assets/t3craft/textures/gui/sprites/chat/*` | Sprites del kit de AgentCraft (MIT) | recurso |

`ChatScreen.java` (la pantalla anterior, que duplicaba el panel de conversación) se eliminó.

## Puntos de integración y cambios estructurales

- **Entrypoints, keybinds, comandos y registros: ninguno nuevo.** El clic derecho sobre el NPC de
  `T3CraftClient` ya llamaba a `ChatScreens.openFor(info)` y no se ha tocado.
- **`dev.t3craft.t3`** (aditivo): `DirectController.view(envId)` (actividad real de las filas) y
  `connection(envId)`. En `T3Connection`: `subscribeThread`, `getTurnItem`,
  `getThreadProjection`, `threadHistory` y `activeThreadSubscriptions`, con envíos en serie. Nuevo
  `StreamRegistry`. En `RpcFrames`: `subscribeThread(...)`, `GET_TURN_ITEM`,
  `GET_THREAD_PROJECTION` y `Exit.value` (con un constructor de 2 argumentos que se conserva).
- **Portable** (consolidación): se borró `portable/pending`. `HeldScreenModel`/`HeldScreen` usan
  `chat.EnvBadge`, y `PortableState.ORDER`/`HeldScreenModel.order` usan `ThreadOrder`.
- **Config**: `T3CraftSettings.chat` / `ChatPrefs` / `withChat`, y la sección `chat` en
  `ConfigCodec`. Todo aditivo.
- **Recursos**: sprites en `assets/t3craft/textures/gui/sprites/chat/` (atlas GUI vanilla).
- **Portable**: `PortableLinks` descubre `ChatScreens.open(String, String)` por reflexión, y esa
  firma se mantiene. En la ronda 2 puede incrustar `ThreadBrowser` con su propio tema.
- `PortableClient` todavía abre `client/screen/ThreadChatScreen` (el placeholder de main). Lo dejé
  sin tocar porque no es de esta rama.

## Verificación

- `./gradlew build` en `mod/`: verde. JUnit: 323 tests, 0 fallos. Los nuevos son `StreamRegistryTest` (refcount, replay, overflow,
  reconexión, fin desde el servidor, llamadas puntuales y listeners rotos), `ThreadFeedTest` (ciclo
  de vida sobre el registro), `ThreadOrderTest`, `ThreadListTest`, `AttentionAgoBadgeTest`,
  `ChatTurnsTest`, `ToolLineScrollRulesTest` y `ChatPrefsTest`, junto a `MarkdownTest`,
  `TextWrapTest` y `ChatTranscriptTest`. Los tests del held screen se adaptaron a `EnvBadge`.
- No lo probé en Minecraft ni contra un T3 real: las reglas no lo permiten aquí.

## Pendientes

- Probar en el juego: escalas de GUI, que los sprites `t3craft:chat/*` carguen en el atlas, la
  legibilidad del papel y el rendimiento con hilos largos (hoy el layout se recalcula entero en
  cada cambio).
- Una tecla o un comando para abrir el navegador sin un NPC (`ChatScreens.openBrowser()` ya
  existe): toca `T3CraftKeys` o los comandos, que no son de esta rama.
- Responder, aprobar e interrumpir (requieren `operate`). Cuando se pueda, la barra pasará a ser
  un `TextFieldView` real, como el de AgentCraft.
- `outputOmitted`: se podría pedir la salida con `orchestration.getTurnItem` al desplegarla.
- Al reconectar se vuelve a pedir el snapshot y se pierden las páginas viejas ya cargadas.
- La UI aún no usa `fullItem()` (`getTurnItem`) al desplegar una tool con `outputOmitted`. La vía
  está lista.

## fix/chat-polish (feedback a 1440p)

- **Layout centrado**: `BrowserLayout.compute(guiW, guiH)` (pura, testeada) centra el navegador en
  coordenadas escaladas, con márgenes iguales a izquierda y derecha y arriba y abajo (mínimo 8 px). Mide
  como máximo 760×440, nunca es más de 2 veces más ancho que alto y pasa al modo compacto por debajo
  de 300×150. `ThreadBrowserScreen` ya no lo ancla abajo a la izquierda y el velo cubre toda la
  pantalla. Tests: 1920×1080 y 2560×1440 con GUI scale 2, 3 y auto.
- **El "título gigante"** que se veía encima del mundo no lo dibujaba el chat: es el `TextDisplay`
  de nombre del NPC (`NpcRegistry`, billboard a 2,45 bloques sobre la cabeza). Como se acaba de
  hacer clic derecho sobre él, queda delante de la cámara. Ahora el panel centrado lo tapa. El
  título del hilo sigue dentro de la cabecera del panel, a tamaño normal.
- **Historial con bridge**: el protocolo v1 define `chat.open`/`chat.history`, pero el bridge
  responde "not implemented" a todo mensaje del mod (`bridge/src/main.ts`, TODO de la fase 2), así
  que no hay fallback de solo lectura. `ChatAccess.history(direct, conectado)` decide entre
  `DIRECT`, `PAIR_FOR_DIRECT` (bridge) y `NOT_PAIRED`. Los dos últimos muestran un estado vacío con
  el botón "Pair for direct mode…", que abre `EnvironmentsScreen` ("Pair new…").
- **Caja de respuesta**: `ChatAccess.reply(hilo, canOperate)`. `READY` deja la caja sin ningún
  texto (el envío llega con el ítem 19). `READ_ONLY` muestra "Read-only: enable control to reply" y
  un botón "Enable control" que abre `OperatePairScreen` para ese entorno. La consulta pasa por
  `ChatScreens.canOperate(env)`, que hoy delega en `OperateUi.canOperate`. Cuando el pairing por
  defecto sea read+operate (fix/pair-default-operate), solo hay que cambiar ese método.
- `ThreadBrowserScreen` construye su navegador en `init()` y lo cierra en `removed()`. Así puede ser
  el padre de las pantallas de emparejar y de control y volver al mismo hilo sin dejar feeds
  abiertos. `ThreadBrowserScreen.of(...)` se sustituyó por `new ThreadBrowserScreen(Supplier, key)`.
