# feat/ui-native-kit: kit de UI propio y pantallas migradas (#14, B3 y B4); teclado, listas y campos (#22); iconos (#28); últimas pantallas vanilla (#26)

Paquete del kit: `dev.t3craft.client.t3ui.kit` (`mod/src/client/java/dev/t3craft/client/t3ui/kit/`).
Plan: `docs/plans/ui-browser-and-integrations.md`, frente B. La pregunta Q6 quedó respondida por Facundo al ver las
capturas de `uiRender`: primero se pulen las pantallas de widgets vanilla (Settings, Environments, Pair, OperatePair,
Confirm, NewThread, Snooze), no el chat.

> **Estado de verificación:** escrito sin compilar (regla de la VPS). El coordinador compila en la PC; los nombres de la
> API de Minecraft usados son los que ya aparecen en el resto del código del mod.

## Qué es el kit

Controles propios dibujados con `ChatTheme` (los tokens de `gui/ui-style.json`, recargables con F3+T) y `ChatDraw`
(los sprites nine-slice y los rellenos planos del terminal). No son widgets vanilla: cada control tiene su rectángulo,
su `render` y su hit-testing; el host (una pantalla, un panel o un diálogo) los coloca y les reenvía el ratón y las
teclas por un `T3Form`. **La única excepción es la entrada de texto**, que envuelve un `EditBox` sin borde (la misma
receta que el buscador de `T3Screen`).

| Clase | Qué es |
|---|---|
| `T3Control` | Base: `bounds`, `enabled`, `visible`, tooltip, `render`, `mouseClicked`/`mouseDragged`/`mouseReleased`, popups (`overlayOpen`/`renderOverlay`) y el foco de teclado (`focusable`, `focused`, `focusIn`/`focusOut`, anillo latón). |
| `T3Button` | Botón. Estilos `DEFAULT` (la píldora de `T3Screen`), `PRIMARY` (relleno acento), `DANGER` (relleno error), `GHOST`. Enter/Espacio lo pulsan con el foco. Puede llevar un `T3Icon` (icono + etiqueta, o solo icono con `T3Button.iconOnly(icon, tooltip, style, action)`). |
| `T3IconButton` | Botón fantasma solo icono (sidebar, barras). Helpers con tooltip: `add` (`plus`), `remove` (`minus`), `refresh` (`refresh-cw`), `edit` (`pencil`), `delete` (`trash`), `copy`, `open` (`external-link`), `close` (`x`). No es parada de Tab. |
| `T3Toggle` | Fila con etiqueta y un interruptor (pista + perilla), para ajustes que se aplican al instante. Enter/Espacio lo cambian con el foco. |
| `T3Check` | Casilla de verificación con etiqueta (para una opción de un formulario). La marca es el icono `check` tintado. Fila entera clicable; Enter/Espacio. |
| `T3Field` | Campo de una línea: marco `ChatDraw.field` + `EditBox` sin borde. Conserva el texto entre reconstrucciones. En una página con scroll se recorta con scissor en el borde del viewport (`layoutClipped`). Para anfitriones que no son una pantalla (el popover del selector de modelo) dibuja y alimenta el `EditBox` con `renderBox`, `boxKeyPressed`, `boxCharTyped` y `setBoxFocused`. |
| `T3TextArea` | Campo multilínea: marco `ChatDraw.field` + `MultiLineEditBox` sin borde (envuelve el texto, barra propia, Enter escribe un salto). Mismo ciclo de vida que `T3Field`. |
| `T3Select<T>` | Desplegable: valor actual con `chevron-down` (`chevron-up` abierto) y un `check` en la opción elegida, lista debajo (o encima si no cabe), rueda, Arriba/Abajo/Enter/Esc. Con el foco, Enter/Espacio/Abajo lo abren. |
| `T3List<T>` | Lista con scroll en un pozo `inset`: filas del mismo alto dibujadas por un `Row` (que puede llevar sus propios botones). Teclado (cursor de fila) y barra arrastrable. |
| `T3Tabs` | Pestañas de texto con regla y subrayado de acento. Con el foco, Izquierda/Derecha cambian de pestaña. |
| `T3Dialog` | Modal: título + pila de bloques (texto envuelto, aviso con icono `notice`, `kv`, campos y cuadros multilínea con etiqueta, controles, fila de botones, widget vanilla). `T3Dialog.confirm(...)` para sí/no. `closable()` pone una `x` en el título (hace lo mismo que Esc). |
| `T3Form` | Reparto de entrada: un control con popup abierto recibe antes el ratón y las teclas; si no, el de arriba. Reenvía arrastre y suelta. |
| `T3Focus` | Tab / Mayús+Tab sobre una lista de controles (con vuelta al principio), y las teclas de activación (Enter, Espacio). |
| `T3Draw` | Texto: envoltura sobre strings (se dibuja con el `shadow` del tema, no el sombreado vanilla), `Tone` (NORMAL, MUTED, WARN, ERROR, OK, CODE, TITLE), tooltip, avisos con icono (`notice`, `noticeIcon`), el anillo `focusRing` y los trazos antiguos `caret` y `tick` (el kit ya no los usa; siguen para quien los llame fuera). |
| `T3KitDemoPanel` | Página de pruebas con todo el kit (galería `/t3dev ui kit`). |

### Hosts

* **`T3DialogScreen`**: una pantalla que es un solo `T3Dialog` sobre el fondo del kit. La subclase implementa
  `buildDialog()` (se llama en cada `init()`, así que lee su propio estado) y decide qué hace Esc en `onClose()`.
* **`T3Panel`**: una *página* (no es pantalla). Declara su contenido en `arrange(...)`: `place`, `placeField`,
  `placeTextArea`, `text`, `notice` (texto con el icono del tono), `pin`/`pinField` (cabecera y pie fijos). Scroll con la rueda o arrastrando la barra cuando el
  cuerpo es más alto que el área. Pide cosas a su host por `T3Panel.Host`: `relayout`, `show` (apilar otra página), `back`,
  `openScreen`, `modal`.
* **`T3PanelHost`**: lo que comparten quienes muestran páginas: pila de páginas (Esc retrocede), marco de papel con
  cabecera (título y botón `x` / `chevron-left` Back), diálogo modal sobre la página, Tab sobre los controles y el reparto de entrada.
  Quien lo posee reenvía además `charTyped`, `mouseDragged` y `mouseReleased` (ver "Teclado y ratón").
* **`T3PanelScreen`**: una página como pantalla propia, centrada sobre el fondo del kit.
* **Escala propia (#42):** `T3PanelScreen`, `T3DialogScreen` y `T3Screen` aplican `t3ui/UiScale` (clave `layout.uiScale`,
  ver `docs/features/t3-ui.md`): ponen su `width`/`height` en unidades lógicas, dibujan bajo un pose escalado y convierten los
  eventos de ratón una sola vez. Las páginas, los controles, los menús y los modales trabajan en unidades lógicas y no se
  enteran. Una pantalla nueva del kit hereda de una de estas tres para tenerlo; un host que sea un `Screen` propio no se escala.
* **`T3RightPanel`** (#39): el panel derecho de `T3Screen`. Es un `T3PanelHost` con id: cabecera con el título y la `x`,
  la página debajo, modal y reparto de entrada compartidos. Muestra una página de **`RightPanels`**, el registro
  `RightPanels.register(id, título, supplier)` donde cada feature enchufa la suya (el piece browser, `"pieces"`, lo registra
  `T3Screen`; luego diff, plan, cola). `T3Screen` reenvía dibujo y entrada como con sus páginas; las teclas van al panel
  mientras el último clic cayó dentro.
* **`T3Splitter`** (#39): asa vertical en el hueco de 4 px entre dos paneles (más 1 px de margen a cada lado). Invisible en
  reposo; al pasar el puntero, lavado `hover`, regla `edge` y cursor de redimensionar; mientras se arrastra, regla `accent`.
  No sabe de anchos: avisa al `Listener` con la x del puntero (`drag`), una vez al soltar (`released`, ahí se guarda) y
  con doble clic (`reset`, ancho por defecto). Sin tokens nuevos.

## Iconos del kit (#28)

Los iconos son los `T3Icon` (SVG de Lucide, ver `docs/tools/icons.md`), tintados con tokens de `ChatTheme`.

* **Tinte:** `muted` en reposo, `accent` bajo el puntero, `disabled` deshabilitado (`T3Icon.tint`). En `T3Button` `PRIMARY` y
  `DANGER` el icono toma el color de la etiqueta (va sobre el relleno). Un aviso toma el color de su `Tone`.
* **Cromo:** cerrar página `x` y atrás `chevron-left` (`T3PanelHost`, mismas cajas de clic que el texto `X` / `< Back` de
  antes), `chevron-down`/`chevron-up` en `T3Select` y `check` en la opción elegida, `check` en `T3Check`, `x` opcional en
  `T3Dialog.closable()`. `T3Tabs` y `T3List` no tenían chevrons; la fila de destinos de Outbound cambió su `>` por
  `chevron-right`.
* **Avisos:** `T3Panel.notice(x, y, w, texto, tone)`, `T3Dialog.notice(texto, tone)` y `T3Draw.notice(...)`. Icono por tono:
  WARN y ERROR `triangle-exclamation-point`, OK `circle-check`, el resto `info`. El texto se envuelve al lado del icono.
* **Páginas de config:** Environments (`plus` Pair new..., `refresh-cw` Refresh, por fila `eye`/`eye-off`, `pencil` Edit,
  `trash` Unpair; el botón Unpair creció de 50 a 58 px para que quepa la etiqueta), Environment edit (`chevron-left` Back,
  `refresh-cw` Reset, `minus` en la muestra "sin color", aviso `info`), Outbound (`plus` Add destination, avisos,
  `chevron-right` por fila), Outbound destination (`chevron-left` Back, `send` Send test, `trash` Delete, avisos según el
  estado de la URL y de "pending only") y Settings → Connections (`cloud` Environments..., `external-link` Piece browser... y
  Key binds... porque abren otra pantalla, `send` Outbound events..., `refresh-cw` Reload from disk, triángulo en Reset HQ).
  Los `…` de esas etiquetas pasaron a `...`. El comportamiento no cambió.
* **No hay acciones de copiar ni enlaces externos** en `client/config/*`: `copy` y `external-link` quedan como helpers
  (`T3IconButton.copy`, `T3IconButton.open`) para quien los necesite (el comando del host en Pair vive en `screen/`, fuera de
  este hilo).

## Teclado y ratón (#22)

* **Foco.** Tab / Mayús+Tab recorren los controles `focusable()` en orden de lectura (cabecera, cuerpo, pie y, al final,
  el botón de cabecera); al final del recorrido vuelve al principio. Hay un solo control con foco: moverlo se lo quita al
  anterior y también al cuadro de texto vanilla que lo tuviera. El **anillo latón** (`ChatTheme.brass`, `T3Draw.focusRing`)
  se dibuja solo cuando el foco vino del teclado; un clic lo quita (los interruptores y botones no se quedan marcados).
  Una página recuerda qué control tenía el foco por su posición en el orden y se lo devuelve al control nuevo cuando se
  re-maqueta; Tab trae a la vista un control que quedó fuera del scroll.
* **Activar.** Enter o Espacio pulsan `T3Button`, cambian `T3Toggle`/`T3Check`, abren `T3Select` (también Abajo) y activan
  la fila del cursor de `T3List`. En un campo de texto Espacio y Enter son caracteres; Enter en un `T3Field` con `onSubmit`
  lo ejecuta, y en un `T3Dialog` Enter sin otro dueño ejecuta `defaultAction` (no en un `T3TextArea`, donde escribe un salto).
* **`T3List`.** Con el foco: Arriba, Abajo, Inicio, Fin, Re Pág, Av Pág mueven un cursor de fila (resaltado, siempre a la
  vista con `ensureVisible`); Enter/Espacio llaman a `Row.activated` y `onActivate`. Un clic en una fila pone el cursor ahí y le
  da el foco. La barra de 6 px se arrastra por el pulgar y un clic en el carril salta una vista (`T3Scrollbar`, la misma
  geometría que `ChatDraw.scrollbar`). El cursor es del listado, no la selección de la página: la del navegador de piezas
  sigue siendo suya (`ensureVisible` se reusa tal cual).
* **Barra de `T3Panel`.** También se arrastra y salta por página.
* **Campos en una página con scroll.** `placeField` ya no oculta el campo entero cuando se sale un poco: el `EditBox` es un
  `ClippedEditBox` que se dibuja y recibe clics solo dentro del viewport (scissor) y se oculta únicamente si queda del
  todo fuera (sin parpadeo). `T3TextArea` no se puede recortar (vanilla lo construye con un *builder* de constructor
  privado): en una página con scroll se oculta hasta que cabe entera (`placeTextArea`); dale hueco. `pinField` sigue siendo
  el modelo para un campo fijo.
* **Lo que debe reenviar quien aloja una página.** `T3PanelScreen` ya lo hace. `T3Screen` reenvía `mouseDragged`,
  `mouseReleased` y `charTyped` (cuando hay un modal) al host de páginas; cualquier otra pantalla que use `T3PanelHost` debe
  hacer lo mismo. Sin `mouseReleased` el pulgar arrastrado no se suelta.

## Pantallas migradas (B4)

| Pantalla | Ahora | Notas |
|---|---|---|
| **Settings** | `SettingsPanel` (página) + `SettingsScreen` (host `T3PanelScreen`) | Cuatro pestañas: General (filtro, HUD), Notifications (interruptores, sonidos, señales en segundo plano con un `T3Select` para la barra de acción), Connections (Environments…, Key binds…, Outbound events…, Reload from disk, Reset HQ world) y World text (estilo, límites de placas, fuentes de texto flotante y Reset to defaults; escribe el bloque `labels`, ver `docs/features/world-text.md`). Pie con estado, Save y Done. Mismo borrador y mismas reglas de guardado que antes. |
| **Environments** | `EnvironmentsPanel` + `EnvironmentsScreen` | `T3List` con las cuatro acciones por fila; Pair new… y Refresh en la cabecera. Unpair y Revoke piden confirmación con un modal del kit sobre la página. |
| **Pair** | `PairScreen` (`T3DialogScreen`) | Tres `T3Field`, interruptor de acceso (lectura + control / solo lectura), estado en vivo. Enter en cualquier campo empareja. |
| **OperatePair** | `OperatePairScreen` (`T3DialogScreen`) | Aviso de riesgo, comando del host, campo, estado. |
| **Confirm** | `ConfirmActionScreen` (`T3DialogScreen`) | Acción / hilo / entorno / aviso. Enter equivale a Cancelar, como antes (el foco estaba en Cancelar). |
| **Snooze** | `SnoozeScreen` (`T3DialogScreen`) | Tres presets y Cancelar. |
| **Environment edit** (#23) | `EnvironmentEditPanel` (página) | "Edit" de `EnvironmentsPanel` hace `host().show(...)`: la edición se queda dentro de la página y "Back" vuelve a la lista (antes abría una pantalla vanilla). Nombre, muestras de color (un control privado de la página, `Swatches`), interruptores y campos de ventana y máximo. |
| **Outbound** (#23) | `OutboundPanel` + `OutboundDestinationPanel` (páginas) | Ajustes → Connections → "Outbound events…" la apila. Interruptor general con modal de confirmación (queda apagado hasta confirmar), lista de destinos en un `T3List`, y la página del destino (nombre, plantilla en un `T3Select`, URL secreta que nunca se muestra de vuelta, acción al tocar, "pending only", eventos, Send test, Delete con confirmación). |
| **Run queue** (#26) | `QueuePanel` (página) + `QueueScreen` (host `T3PanelScreen`) | Un `T3List`: fila de cabecera por hilo (entorno, título, estado) y una fila por run con tres `T3IconButton` (`chevron-up`, `chevron-down`, `x`). Sin control (operate) los botones quedan deshabilitados con "Requires control (operate)". Cancelar pide confirmación con un modal del kit sobre la página y luego manda la acción confirmada por `OperateGate`; reordenar sale directo. Resultado en el pie. Datos por `QueuePanel.Source` (la real vigila el hilo enfocado y los que trabajan mientras la página se ve). |
| **Archive** (#26) | `ArchivePanel` + `ArchiveScreen` | Búsqueda (`T3Field`) y filtros de entorno y proyecto con `T3Select` (antes chips que rotaban), `T3List` con scroll (ya no pagina), Older / Refresh en el pie con iconos. El bloque archive terminal, `/t3craft archive` y el portable siguen abriéndola por `ArchiveClient.openArchive`. |
| **HQ menu** (#26) | `HqMenuPanel` + `HqMenuScreen` (host `T3PanelScreen`, un `Screen` normal: se abre desde la pantalla de título) | Open HQ en la cabecera, `T3List` de layouts (clic o Enter eligen), línea de estado, pie con Export/Reload layouts, Rebuild with y Reset HQ; Rebuild y Reset piden confirmación con un modal del kit, y una negativa se muestra en otro modal. |
| **Merge station** (#26) | `MergeStationPanel` + `MergeStationScreen` | `T3List` de PRs pendientes con dos botones por fila: Open (`external-link`, deshabilitado sin enlace) y Chat (`message-square`). Solo lectura. |
| **Register project** (#23) | `RegisterProjectDialog` (`T3DialogScreen`) | `T3Select` de entorno, campos de ruta y nombre, interruptor "create the folder", Register / Cancel; el estado en vivo dice el nombre que tomará, el primer error tras pulsar Register o la respuesta de T3. Mismos puntos de entrada (`new RegisterProjectDialog(...)` y `open(...)`). |

Los puntos de entrada no cambian: `new SettingsScreen(parent, actions)`, `new EnvironmentsScreen(...)`, `new PairScreen(parent[, readOnly])`,
`new OperatePairScreen(...)`, `new ConfirmActionScreen(...)` y `new SnoozeScreen(...)` siguen siendo
válidos (Mod Menu, la tecla J, `/t3craft config`, `/t3craft pair`, `OperateUi.run`, el menú contextual del sidebar).

**Crear un hilo no es un diálogo del kit.** Como en T3 Code, "New thread" es el composer de la pantalla principal más el selector de
proyecto: `T3Screen.withDraft(env, project)` abre un borrador (fila bajo su proyecto, chips de proyecto, modelo y espacio de trabajo) y el
primer mensaje lo lanza (`DraftLauncher`). `NewThreadScreen` se eliminó en feat/ui-native-kit: nadie la construía desde feat/t3-composer.

### Páginas dentro de `T3Screen`

El ⚙ y el ☁ del pie del sidebar y los items "Settings" / "Environments" del menú ⋯ abren la página **en el lugar del hilo**
(como la página de ajustes de T3 Code); el sidebar se queda. Esc o ✕ vuelve al hilo; abrir un hilo o un borrador desde el
sidebar cierra la página. En el layout compacto la página ocupa la pantalla. La página se re-maqueta sola con F3+T.

El item "Queue" del menú ⋯ (#39) abre la página de la cola (`QueuePanel`) igual, en el lugar del hilo. El **panel derecho**
(#39, `T3RightPanel`) es otro sitio para páginas: a la derecha del hilo, redimensionable con `T3Splitter`, con los anchos
guardados en el bloque `layout` de `config/t3craft.json` (ver `docs/features/t3-ui.md`).

## Cómo usarlo

* **Añadir un ajuste:** en `SettingsPanel`, en el método de la pestaña, `y = toggle(x, y, w, "Etiqueta", valor, v -> draft = ...)`.
  Los controles se crean de nuevo en cada `arrange`, así que siempre reflejan el estado.
* **Una página nueva:** subclase de `T3Panel`; ábrela con `host().show(new MiPanel())` desde otra página, o desde
  `new T3PanelScreen(parent, page)` o `T3Screen.withPage(page)` (la galería); el ⚙/☁ del sidebar usan `T3Screen.openPage`.
* **Un diálogo nuevo:** subclase de `T3DialogScreen` con `buildDialog()`; los campos (`T3Field`) viven en la pantalla, no en el
  diálogo, para que sobrevivan a `rebuildWidgets()`.
* **Un color nuevo:** un token en `ui-style.json` (nunca un literal en la pantalla); ver `docs/dev.md` "UI tokens".

## Reglas del kit

* La posición de un bloque depende solo de los de arriba. Un texto dinámico (`T3Dialog.text(Supplier, Supplier, reservedLines)`)
  reserva sus líneas y va **debajo** de todos los campos; el panel crece hacia abajo.
* Un diálogo abierto por el host sobre una página (`host().modal(dialog)`) **puede llevar campos, cuadros multilínea y selects**:
  el host lo maqueta **una vez** al abrirlo (y otra vez en un resize o con F3+T), no en cada frame. Sus cajas de texto no son
  widgets de la pantalla: el diálogo las dibuja sobre su propio marco (`T3Dialog.layoutOwned`) y las enruta (clic, teclas,
  `charTyped`). Las posiciones de los bloques siguen dependiendo solo de los de arriba, y un texto dinámico reserva líneas.
* Un diálogo de una pantalla propia (`T3DialogScreen`) mantiene los campos en la pantalla, no en el diálogo, para que
  sobrevivan a `rebuildWidgets()`.
* Los popups (`T3Select`) se dibujan al final con `g.nextStratum()`, como los menús de `T3Screen`.
* Texto del kit en ASCII/Latin-1. Nada de glifos que solo existen en Unifont: los símbolos son `T3Icon` (chevrons, marca,
  cerrar, atrás) y el anillo se dibuja con rellenos; los recortes acaban en `...`. `uiRender` dibuja esos glifos como cuadro
  de glifo ausente.
* Colores solo de `ChatTheme`/`T3Draw.Tone`; un literal de color o un `ChatFormatting` en una pantalla del kit es un fallo.

## Auditoría de colores y textos literales (#22)

Dentro de `t3ui/kit/*` no queda ningún color literal (las únicas constantes son máscaras de alfa del degradado del fondo) y los
glifos fuera de Latin-1 que había (`▾`, `—`, `…`, `✕`, `‹`) se sustituyeron por trazos dibujados o ASCII. **Remanentes** fuera del
kit, que no se tocaron porque son de otros hilos o de pantallas sin migrar (excluido lo del selector de modelo,
`ModelPicker*`/`ProviderCatalog`):

* Colores con `ChatFormatting` o literales `0xFF…` en widgets vanilla sin migrar: `operate/ThreadOpsBar` (rojo del botón
  peligroso). `BaseConfigScreen`, `OutboundScreen`/`OutboundDestinationScreen` y `RegisterProjectDialog` ya no tienen
  (borradas o migradas en #23 y #26). Las cuatro pantallas del #26 (cola, archivo, menú del HQ, merge station) dibujan solo con
  tokens; las paletas de dominio que quedan en esas carpetas son del mundo, no de la GUI: `MergeFeed.color` y
  `MergeStationRenderer` (el panel del bloque), `TerminalDisplay` y `ArchiveLabel.envColor` (la pantalla del bloque archive
  terminal, el color de tinte del entorno).
* Paletas de dominio con literales, que no son tokens de tema: `t3ui/actions/ThreadStatus` (color por estado de hilo),
  `t3ui/sidebar/SidebarRow` (estados y acentos), `t3ui/actions/ThreadStatusChip` (`BG`), `t3ui/DraftControls` (gris del chip y
  fondo del campo). Convendría pasarlos a tokens de `ui-style.json` cuando alguien toque esas pantallas.
* `config/EnvironmentsPanel` pinta `0xFF000000 | p.color()`: es el color elegido por el usuario (dato), no un token.
* Texto con `…` (U+2026, fuera de Latin-1) en pantallas de otros hilos (los de `SettingsPanel` y `EnvironmentsPanel` ya se
  pasaron a `...` en #28): `PairScreen` y `OperatePairScreen` (pista del enlace y
  "Pairing…"), la galería (un transcript de ejemplo). Se ven bien en el juego (la fuente cae a Unifont); solo `uiRender` los dibuja
  como cuadro. Pasarlos a `...` es un cambio de una línea cada uno.

## Lo que queda a medias

* Ya no queda ninguna pantalla de T3Craft con widgets vanilla salvo los diálogos vanilla que abren otros (p. ej. `ConfirmLinkScreen`
  para un enlace, el `ConfirmScreen` de `DemoClient` al salir de un mundo). `BaseConfigScreen` y el placeholder `ThreadChatScreen`
  se borraron en el #26 (nadie los usaba).
* La cola todavía no se abre desde el menú del sidebar de `T3Screen` (solo `/t3craft queue` y la galería): toca `T3Screen`, queda
  para después.
* Los botones dentro de las filas de un `T3List` (la lista de entornos) no están en el orden de Tab: se llegan con el ratón.
  La lista sí es una parada de Tab, con su cursor de fila.
* `T3TextArea` no se recorta en una página con scroll (ver arriba), y la selección con el ratón dentro de un diálogo con cajas
  propias solo coloca el cursor (no arrastra la selección).
* `uiRender` no tiene fixtures de estas pantallas: la galería (`/t3dev ui`) es el camino. Pendiente: un tipo de pantalla
  `settings`/`environments`/`pair` en `Mock` (requiere sembrar `ConfigManager` en modo headless).
* Del #15, la escala propia llegó con el #42 (`UiScale`, solo escalas enteras; por defecto `"minecraft"`, sin cambios). La fuente
  TTF sigue pendiente: el kit dibuja con la fuente bitmap. Con una escala propia activa, el hover vanilla de un `EditBox` dentro de
  un scissor activo compara el ratón lógico con el scissor ya transformado (solo cosmético: el cursor de texto); y un texto con
  estilo hover de Minecraft (`HoverEvent`) dibujaría su tooltip dos veces (ninguna pantalla T3 lo usa hoy). Los iconos
  llegaron con #24 (pipeline) y #28 (este cromo).
* `T3IconButton` no es parada de Tab (en el kit de pruebas se llega con el ratón; los `T3Button` con icono sí lo son).
* Escrito sin compilar (regla de la VPS): el coordinador compila en la PC. Los nombres de la API de Minecraft 26.3 usados se
  comprobaron contra el jar (`EditBox`, `MultiLineEditBox.builder()`, `AbstractWidget`, `MouseButtonEvent`, `CharacterEvent`).

## Probarlo en la PC

Con el juego abierto (cliente de desarrollo), `/t3dev ui` lista los escenarios y `/t3dev ui <id>` abre uno con datos falsos:

```
/t3dev ui inbox-settings        página de ajustes dentro de T3Screen (lo que abre el ⚙)
/t3dev ui inbox-environments    lista de entornos falsos dentro de T3Screen
/t3dev ui settings              ajustes como pantalla propia (General)
/t3dev ui settings-notifications
/t3dev ui settings-connections
/t3dev ui settings-world-text
/t3dev ui environments          lista de entornos falsos (ok, connecting, offline, con control)
/t3dev ui pair                  emparejado (control por defecto)
/t3dev ui pair-readonly
/t3dev ui operate-pair
/t3dev ui confirm
/t3dev ui snooze
/t3dev ui launch                nuevo hilo
/t3dev ui environment-edit      edicion de un entorno falso (Save escribe la config real bajo el id falso "studio")
/t3dev ui outbound              eventos salientes con destinos falsos (carpeta temporal, no el outbound.json real)
/t3dev ui outbound-destination  un destino (URL guardada en la carpeta temporal; nunca se muestra)
/t3dev ui register-project      dialogo de registrar proyecto (necesita un entorno emparejado para elegir entorno)
/t3dev ui kit                   el kit entero en una página (teclado, listas, campos, diálogo con campos)
/t3dev ui inbox-kit             la misma página dentro de T3Screen (el diálogo y el arrastre pasan por T3Screen)
/t3dev ui queue                 cola de ejecuciones con colas falsas (un entorno con control, otro de solo lectura)
/t3dev ui archive               archivo con hilos cerrados falsos (búsqueda, selects de entorno y proyecto)
/t3dev ui hq-menu               menú del HQ con los layouts reales y un HQ construido falso (no abre ni resetea nada)
/t3dev ui merge-station         merge station con PRs falsos (abierto, draft, uno sin enlace)
```

F3+T recarga `ui-style.json` y estas pantallas se re-maquetan. Qué mirar: que Esc retroceda página a página, que el scroll con
la rueda funcione en una ventana baja (GUI scale 4), que Enter en un campo de Pair empareje, que el desplegable de Notifications
se abra encima del resto, y que Unpair / Revoke abran el modal y lo cierren con Esc.

**Lista de pruebas de `/t3dev ui kit` (#22)**, mejor en una ventana baja:

1. Pestaña *Controls*: Tab recorre pestañas, buscador, interruptor, casillas, los cuatro botones, el desplegable, la lista, el
   botón del pie y Done, y por último la X; Mayús+Tab va hacia atrás. El anillo latón se ve en cada uno y desaparece al hacer
   clic. Con el foco, Enter y Espacio pulsan los botones y cambian el interruptor y la casilla (el pie dice qué pasó).
2. La lista: Tab hasta ella, Abajo/Arriba/Inicio/Fin/Re Pág/Av Pág mueven el cursor y lo mantienen a la vista; Enter lo
   activa. Arrastra el pulgar de su barra, y haz clic en el carril para saltar una vista. Lo mismo con la barra de la página.
3. Pestaña *Text*: baja con la rueda despacio; el primer campo se **recorta** en el borde y no salta. El cuadro multilínea
   aparece cuando cabe entero. Tab hasta el campo de abajo: la página se desplaza para mostrarlo y escribes en él.
4. *Dialog with fields*: se abre con el foco en el nombre; escribe, Tab pasa a las notas (Enter escribe un salto de línea), a
   la casilla y a los botones; Esc cierra. Haz clic en ambos cuadros para mover el cursor.
5. Lo mismo en `inbox-kit` (T3Screen): el diálogo, el arrastre del pulgar y el tipeo deben comportarse igual.
6. **Iconos (#28):** en *Controls*, los cuatro botones con icono + etiqueta (en Primary y Danger el icono va del color del
   texto), "Open (disabled)" gris, el botón `info` solo icono con tooltip, la fila de `T3IconButton` (pasa el puntero: muted
   pasa a accent con fondo; el último está deshabilitado) y los cuatro avisos (triángulo ámbar, info gris, check verde,
   triángulo rojo). La casilla marcada muestra el `check`, la deshabilitada lo deja gris. El desplegable muestra
   `chevron-down`, `chevron-up` abierto y un `check` en la opción elegida. El diálogo del pie tiene una `x` en el título que
   lo cierra. La X de la cabecera de la página y, en una página apilada (Settings → Connections → Environments...), el
   `chevron-left` Back siguen en el mismo sitio.
7. Páginas de config: `/t3dev ui settings-connections`, `environments`, `environment-edit`, `outbound`,
   `outbound-destination`: iconos en los botones y avisos con icono, mismas acciones de antes.
