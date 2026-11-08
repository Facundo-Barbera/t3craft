# Auditoría de interfaces (fix/ui-audit, 2026-11-08)

Revisión de código de todas las pantallas y GUIs del mod cliente (MC 26.3, Fabric). No se ha lanzado
el juego: cada hallazgo sale de leer el código y de comparar con las firmas reales de MC 26.3
(`javap` sobre `minecraft-clientonly-deobf-26.3.jar`). La lógica que se podía extraer (layout,
teclas, resolución de claves) está en clases puras con JUnit.

**Lección de la API de 26.x.** Los códigos de tecla y de botón **ya no son los de GLFW**:
`InputConstants.KEY_RETURN = 40` (no 257), `KEY_NUMPADENTER = 88`, `KEY_PAGEUP = 75`,
`KEY_PAGEDOWN = 78`, `MOUSE_BUTTON_LEFT = 1`, `MOUSE_BUTTON_RIGHT = 3` (no 0/1). Hay que compararlos
siempre con `InputConstants` o con `dev.t3craft.client.chat.MouseButtons` (de fix/ui-chat-sidebar),
nunca con literales. `DialogLayoutTest` cazó esto en mi propio primer intento (Enter = 257).

## Problemas arreglados

| Pantalla | Problema | Causa | Arreglo | Test |
|---|---|---|---|---|
| Workstations, monitores (`monitor`), tarjetas del thread wall (`thread_board`) | Al hacer clic se abría `ThreadChatScreen`, un marcador ("Chat is not implemented yet") sin sidebar, en vez del chat real | El `ThreadOpener` por defecto (`ChatScreenOpener`, también el fallback de `BlocksClient`) seguía apuntando al marcador de la fase 1, y nadie llamaba nunca a `setThreadOpener` | `ChatScreenOpener` abre `ChatScreens.create(key)`: el navegador de chat con el hilo del bloque ya seleccionado. Abre también hilos no listados si se conoce el entorno | `ChatScreenOpenerTest` (resolución de la clave: NPC `env:thread`, modo bridge, hilo no listado, nada que abrir) |
| Portable → fallback de chat | El fallback seguía abriendo `ThreadChatScreen` | Igual que la fila anterior | Abre `ChatScreens.openFor`. `ThreadChatScreen` queda sin usos y se marca `@Deprecated` | — (cableado) |
| SettingsScreen, EnvironmentsScreen, EnvironmentEditScreen (`BaseConfigScreen`) | El texto de estado ("Saved…", errores, la negativa de reset-hq) quedaba encima de una fila de botones y no se leía | Se dibujaba en `height - 64`, dentro de la fila `by - 24` (de `height - 76` a `height - 56`) | `DialogLayout.statusY`: justo debajo de la última fila de botones | `DialogLayoutTest.statusLineNeverSitsOnAButtonRow` (alturas de 120 a 1080) y una guarda de regresión |
| EnvironmentsScreen | Con menos de ~360 px de ancho GUI (GUI scale alto), los botones de cada fila (Hide/Edit/Unpair/Control) se salían por la izquierda y el texto quedaba con 0 px | Botones anclados a `x + w - 258` sin límite | `DialogLayout.EnvRow`: si la columna de texto quedaría por debajo de 90 px, los botones bajan a su propia línea dentro de la fila (filas de 46 px) y la paginación se recalcula | `DialogLayoutTest.envRow*`, `rowsPerPageAtLeastOne` |
| EnvironmentsScreen (lista vacía) | Los comandos del host salían cortados por los bordes en ventanas estrechas | `centeredText` de una sola línea | Se ajustan al ancho de la lista | — (render) |
| PairScreen | Tras emparejar, el conmutador Read/Control seguía deshabilitado | El callback solo reactivaba el botón Pair | Se guarda la referencia y se reactivan los dos | — (widget MC) |
| PairScreen, OperatePairScreen | Enter en los campos no hacía nada | `EditBox` ignora Enter y la pantalla no lo gestionaba | `keyPressed`: Enter o Enter numérico con un campo enfocado y el botón activo envía | `DialogLayoutTest.submitKeys` (incluye que 257, el Enter de GLFW, **no** cuenta) |
| OperatePairScreen | En ventanas bajas, las líneas del comando del host que no cabían se dibujaban una encima de otra | Cada línea se acotaba con `Math.min(y, fieldY - 22)`, así que todas caían en la misma `y` | El campo se coloca debajo de la cabecera (aviso + comando), acotado para que los botones sigan en pantalla. Solo se dibujan las líneas que caben (`linesThatFit`). El mensaje de resultado se ajusta al ancho | `DialogLayoutTest.linesThatFitClampsToTheLimit` |
| ConfirmActionScreen | Un título de hilo largo se salía del diálogo, y un aviso alto quedaba debajo de los botones | Líneas sin ajustar y botones fijos en `height/2 + 40` | El texto se ajusta, y los botones van debajo del bloque de texto (`buttonsBelow`), sin salir de la pantalla | `DialogLayoutTest.buttonsGoBelowTallText` |
| NewThreadScreen | "Sin entorno / sin proyecto" aparecía dentro del cuadro del prompt o debajo del botón de entorno, y el resultado del lanzamiento quedaba tapado por "Open chat" | Posiciones fijas (`height/2 - 30`, `y 24`, `height - 24`) | Se muestran en la línea de mensajes, ajustados al ancho. En la página de revisión, el mensaje va encima de la fila Launch/Back | — (render) |
| ArchiveScreen | Abierto desde el portable, Done o Esc te mandaban al juego | Sin `parent` | Nuevo constructor con `parent` y `ArchiveClient.openIfReady(parent)`. El portable pasa `this` | — (cableado) |
| ArchiveScreen → chat de solo lectura | Esc en el chat cerraba todo en vez de volver al archivo | `ThreadBrowserScreen` no tenía `parent` | `ThreadBrowserScreen.withParent(...)` (aditivo: no toca el sidebar), que `ArchiveOpener.DEFAULT` usa con la pantalla actual | — |
| ArchiveScreen | El clic derecho o central sobre una fila también abría el hilo. No había paginación con teclado | Sin filtro de botón | Solo abre `MouseButtons.isLeft`. RePág/AvPág pasan de página, también con el buscador enfocado (el `EditBox` no usa esas teclas) | `ArchiveKeysTest` (códigos de 26.x, paso de página, izquierdo = 1 y no 0) |
| PortableScreen | "Environments…" abría la pantalla de emparejar, no la lista de entornos | `PortableLinks.setEnvironments` no se llamaba nunca y el fallback era `PairScreen` | El fallback abre `EnvironmentsScreen(parent)`, que vuelve al terminal con Done | — (cableado) |
| PortableScreen → chat | Esc en el chat abierto desde el terminal cerraba al juego | Sin `parent` | `PortableLinks.setChat` crea el navegador con `withParent(terminal)`. En modo bridge usa el id de hilo sin prefijo | — |
| PortableScreen | El subtítulo o mensaje flash de la cabecera quedaba 52 px por debajo del botón "Archive" | Se recortaba a `w - 210`, pero el botón empieza en `w - 254` | `PortableNav.headerTextWidth` | `PortableNavTest.headerTextStopsLeftOfTheArchiveButton` |
| QueueScreen | Las cabeceras de hilo largas y los mensajes de resultado se salían de la lista o de la ventana | Sin recorte | Se recortan con "…" como las líneas de run | — (render) |

## Visto pero no arreglado

| Pantalla | Observación | Por qué no |
|---|---|---|
| Chat (`ThreadBrowserScreen` / `ConversationPanel`) | No hay campo de texto, así que Enter no envía y no se puede escribir | El envío está pendiente por diseño (item 19: la barra solo muestra "listo" o "read-only"). No es un bug de input. Además, el chat es de fix/ui-chat-sidebar |
| Chat, selección del sidebar y botones de ratón | Comparaciones con 0/1 de GLFW | Lo arregló fix/ui-chat-sidebar (`MouseButtons`, ya mergeado en esta rama). Busqué más comparaciones de botón fijas en el resto de pantallas y solo apareció la mía del archivo, ya corregida |
| `ThreadChatScreen` | Sigue existiendo, aunque nada la abre | Se deja `@Deprecated` para no romper a posibles llamadores externos y no reestructurar el archivo. Se puede borrar más adelante |
| `board_lamp` / `status_lamp` | No abren ninguna GUI | Por diseño: son solo indicadores (no tienen `useWithoutItem`). No hay interacción que arreglar |
| `archive_terminal` | Abre el archivo sin `parent` (Esc va al juego) | Es lo correcto desde un bloque |
| HUD (barra de estado, peek, toasts) | Sin interacción con el ratón | Solo se dibuja. No hay entrada que auditar |
| schedule/, site/, hq/, demo/ | No tienen pantallas propias. El botón reset-hq vive en SettingsScreen (usa `ConfirmScreen` y vuelve bien; su negativa ahora se lee gracias al arreglo de la línea de estado) | Nada roto |
| DemoClient, `CONFIRM_LEAVE` | "No" lleva a `setScreen(null)` y no al padre | Solo se llega desde un comando (el botón de demo solo existe en la pantalla de título y en la de selección de mundo), donde `null` es lo correcto |
| PortableScreen | Retroceso sin el buscador enfocado hace "atrás" o cierra | Comportamiento documentado ("Esc/Backspace back") |
| SearchBox | Con el campo enfocado, Tab no cambia el foco | Decisión del componente (con foco consume todas las teclas para que no salten los atajos del host) |
| SettingsScreen | En ventanas estrechas, las etiquetas "Recent window (hours)" y "Max per environment" pueden pasar del ancho de la columna | Cosmético. Los botones de vanilla desplazan su texto, y estas etiquetas solo se solapan con el margen |
| NewThreadScreen | El callback de lanzamiento llama a `rebuildWidgets` aunque la pantalla ya se haya cerrado | Inofensivo: reconstruye widgets de una pantalla que no se muestra |
| ThreadBrowserScreen | No reenvía `charTyped` | No hay ningún campo de texto que lo necesite (ver la primera fila) |
