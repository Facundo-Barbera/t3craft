# feat/chat-compose: escribir y enviar mensajes desde el chat del juego

El chat (`ThreadBrowserScreen` / `ConversationPanel`) tiene ahora un **composer** abajo de la
conversación, como el de T3 Code. Escribes, pulsas Enter y el mensaje va al hilo con el pairing de
escritura. La respuesta del asistente se ve en vivo con la suscripción del hilo que ya existía.

## Para el jugador

- **Clic** en el campo para escribir. **Enter** envía y **Shift+Enter** inserta una nueva línea.
  **Esc** quita el foco; un segundo Esc cierra la pantalla.
- Mientras el campo tiene el foco, ninguna tecla dispara atajos del navegador (↑↓, Tab, PgUp…).
  Las teclas F siguen siendo del juego.
- Edición: Ctrl+A/C/V/X, flechas (con Shift seleccionan y con Ctrl van por palabra), Home/End por
  línea visual (con Ctrl, al inicio o al final del texto), Backspace/Delete (con Ctrl, por
  palabra), arrastrar para seleccionar y rueda para el scroll interno. El campo crece hasta 6 líneas.
- El mensaje aparece **al instante** como "You" (optimista) y se sustituye por el real cuando el
  servidor lo devuelve. Si falla, el texto **vuelve al campo** y la barra muestra el error.
- Mientras corre un turno, la barra dice "Trabajando…". Si el campo está vacío, el botón pasa a
  ser **Stop** (`run.interrupt`, con la confirmación en el juego que ya tenía op-stop). Si escribes,
  el botón vuelve a ser Enviar y T3 dirige el turno en curso (steer), igual que T3 Code.
- Sin operate, el campo queda deshabilitado con **"Habilitar control"**, que abre
  `OperatePairScreen`.
- Borrador por hilo: al cambiar de hilo se guarda lo que estabas escribiendo y vuelve al regresar.
  Solo dura la sesión, en memoria.
- Autoscroll: la conversación sigue la última línea si ya estabas abajo (`FeedScroll`) y salta
  abajo al enviar.

## Métodos y comandos de T3 que se usan

Referencia: `~/projects/t3code-ref` @ `15fe2832`.

| Qué | RPC / comando | Contrato |
|---|---|---|
| Enviar | `orchestration.dispatchCommand` con `message.dispatch` | `packages/contracts/src/orchestrationV2.ts` (unión `OrchestrationV2Command`, rama `message.dispatch`); el web lo construye en `packages/client-runtime/src/operations/commands.ts` (`startThreadTurn`, modo `"auto"`) |
| Stop | `orchestration.dispatchCommand` con `run.interrupt` | `orchestrationV2.ts`, rama `run.interrupt` (reusa `ThreadControl.interrupt` de op-stop) |
| Streaming | `orchestration.subscribeThread` (ya existente, `ThreadFeed`) | eventos `turn-item.updated`, `message.updated`, `run.*` |

Payload exacto de `message.dispatch` (lo comprueba `ComposerStateTest`):

```json
{"type":"message.dispatch","commandId":"cmd-<uuid>","createdBy":"user","creationSource":"web",
 "threadId":"<thread>","messageId":"msg-<uuid>","text":"<texto sin espacios en los extremos>",
 "attachments":[],"deliveryIntent":"auto","dispatchMode":{"type":"start_immediately"}}
```

- `createdBy` y `creationSource` son **obligatorios** en el contrato
  (`OrchestrationV2CreationFields`). `"web"` es lo que manda la UI de T3 y ninguno de sus valores
  nombra un cliente de Minecraft.
- Con `deliveryIntent: "auto"`, el servidor decide: si hay un turno en curso lo dirige (steer) y si
  no, inicia uno nuevo (`apps/server/src/orchestration-v2/Orchestrator.ts`,
  `resolveMessageDispatchIntent`).
- El `messageId` lo elige el cliente. El `user_message` que vuelve por el stream trae el mismo
  `messageId`, y así se reconcilia la fila optimista.
- `run.interrupt`: `{type, commandId, threadId, runId}`, con `runId` = el último run activo del
  transcript (`ChatTranscript.activeRunId()`).

Componentes de T3 Code copiados (estructura y comportamiento, no código): `ChatComposer.tsx`
(input y placeholder "Ask anything"), `ComposerControlsBar.tsx` (barra bajo el input),
`ComposerPrimaryActions.tsx` (Stop si corre y no hay texto, y si no, Enviar/Steer),
`composerDraftStore.ts` (borradores por hilo) y `useComposerSend.ts` (mensajes optimistas, y el
texto vuelve si falla).

## Código

| Pieza | Qué |
|---|---|
| `client.chat.compose.ComposerText` | Modelo de texto puro: caret, selección, wrap visual y teclas (códigos `InputConstants` de MC 26.x), con el portapapeles como interfaz. |
| `client.chat.compose.ComposerDrafts` | Borradores por `env:thread` (LRU de 64, en memoria). |
| `client.chat.compose.ChatSender` | Construye `message.dispatch` y `run.interrupt` y los manda por `OperateGate` (`MESSAGE_SEND` no es destructivo; `RUN_INTERRUPT` sí). |
| `client.chat.compose.ComposerState` | Estado puro: hilo, filas optimistas y su reconciliación, envío en vuelo, errores por hilo, botón (Enviar/Stop/Habilitar control). |
| `client.chat.compose.ComposerView` | Widget MC autocontenido: `setThread`, `observe`, `setBounds`, `render`, `height`, `mouseClicked`, `mouseDragged`, `mouseReleased`, `mouseScrolled`, `keyPressed`, `charTyped`, `setFocused`, `setControls`. |
| `client.chat.compose.ChatComposer` | Cableado de producción: `create(screen, theme, onSent)` (OperateUi, OperatePairScreen), `attach(conv, composer)` y `sync(conv, composer)`. |
| `ConversationPanel` | Dos ganchos: `setFooter(Footer)` (el composer ocupa el lugar de la barra de respuesta) y `setExtra(Extra)` (filas optimistas tras el transcript). |
| `ChatTranscript` | `activeRunId()`. |
| `ThreadBrowserScreen` | Es dueña del composer, que sobrevive a las pantallas de confirmación y de pairing: le pasa las teclas, los caracteres, el ratón y el scroll, y le quita el foco al hacer clic fuera. |

**Para feat/t3-ui-unified**: crea el composer con `ChatComposer.create(...)`, llama a
`ChatComposer.attach(conv, c)` una vez y a `ChatComposer.sync(conv, c)` en cada frame. Pasa
`keyPressed` y `charTyped` al composer **antes** que al resto. Para otro layout, usa
`c.setBounds(rect)` + `c.render(g, font, mx, my)` con `c.height(width)`.

**Para feat/t3-actions**: `ComposerView.setControls(List<Control>)` añade controles de 12 px en
el hueco izquierdo de la barra (modelo, provider, effort). Por ahora está vacío.

## Pendiente (pulido posterior)

- Controles reales en la barra (model/provider/effort; los hace feat/t3-actions).
- Deshacer/rehacer, doble clic para seleccionar palabra y Home/End con selección por línea lógica.
- Los borradores no se persisten en disco.
- Una fila optimista aceptada (`OK`) cuyo eco no llega nunca (stream caído) se queda hasta cerrar
  la pantalla. No hay timeout.
- Los mensajes de error usan `ThreadControl.feedback` (en inglés).
- Adjuntos, `@` archivos, `/` comandos y elegir cola o steer (T3 Code los tiene).
- `ConversationPanel` no expone el título del hilo al composer: la confirmación de Stop puede salir
  sin título hasta que llega el snapshot.

## Pruebas

`ComposerTextTest` (edición, selección, portapapeles fake, Enter frente a Shift+Enter, wrap,
↑↓/Home/End) y `ComposerStateTest` (payload exacto de `message.dispatch` y `run.interrupt`, fila
optimista y su reconciliación, error y recuperación, borradores por hilo, solo lectura y Stop).
Todo con un `OperateTransport` fake, nunca contra un T3 real.
