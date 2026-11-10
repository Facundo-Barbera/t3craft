# feat/ui-native-kit: kit de UI propio y pantallas migradas (#14, B3 y B4; teclado, foco y controles nuevos en #22)

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
| `T3Control` | Base: `bounds`, `enabled`, `visible`, tooltip, `render`, ratón (`mouseClicked`/`mouseDragged`/`mouseReleased`), teclado (`keyPressed`/`charTyped`), foco (`focusable`/`hasFocus`), popups (`overlayOpen`/`renderOverlay`). |
| `T3Button` | Botón. Estilos `DEFAULT` (la píldora de `T3Screen`), `PRIMARY` (relleno acento), `DANGER` (relleno error), `GHOST`. |
| `T3Toggle` | Fila con etiqueta y un interruptor (pista + perilla). Reemplaza el botón vanilla "Etiqueta: ON/OFF" verde y rojo. Para un ajuste que se aplica al momento. |
| `T3Check` | Casilla de verificación con la etiqueta a la derecha (cuadro + marca). Para elegir varias cosas de una lista. |
| `T3Field` | Campo de una línea: marco `ChatDraw.field` + `EditBox` sin borde. Conserva el texto entre reconstrucciones. |
| `T3TextArea` | Campo multilínea de alto fijo (`rows` líneas) que se dibuja con el kit (sin widget vanilla, así que se recorta con su panel). Modelo de texto, teclas y portapapeles de `ComposerText` (el compositor del chat). Enter = salto de línea, Ctrl+Enter = `onSubmit`, ratón: caret, arrastre, doble clic = palabra, rueda. |
| `T3Select<T>` | Desplegable: valor actual con ▾, lista debajo (o encima si no cabe), rueda, ↑/↓/Enter/Esc. |
| `T3List<T>` | Lista con scroll en un pozo `inset`: filas del mismo alto dibujadas por un `Row` (que puede llevar sus propios botones). Teclado (cursor de fila), thumb arrastrable. |
| `T3Tabs` | Pestañas de texto con regla y subrayado de acento. |
| `T3Dialog` | Modal: título + pila de bloques (texto envuelto, `kv`, campos con etiqueta, controles, fila de botones, widget vanilla). `T3Dialog.confirm(...)` para sí/no. |
| `T3Form` | Reparto de entrada: un control con popup abierto recibe antes el ratón y las teclas; si no, el de arriba. Los caracteres van al control con el teclado; los arrastres, a quien los empezó. |
| `T3Focus` | (interna) Quién tiene el teclado en un panel o diálogo y el anillo de Tab. Ver "Teclado y foco". |
| `T3Draw` | Texto: envoltura sobre strings (se dibuja con el `shadow` del tema, no el sombreado vanilla), `Tone` (NORMAL, MUTED, WARN, ERROR, OK, CODE, TITLE) y tooltip. |

### Hosts

* **`T3DialogScreen`**: una pantalla que es un solo `T3Dialog` sobre el fondo del kit. La subclase implementa
  `buildDialog()` (se llama en cada `init()`, así que lee su propio estado) y decide qué hace Esc en `onClose()`.
* **`T3Panel`**: una *página* (no es pantalla). Declara su contenido en `arrange(...)`: `place`, `placeField`, `text`,
  `pin` (cabecera y pie fijos). Scroll con la rueda cuando el cuerpo es más alto que el área. Pide cosas a su host por
  `T3Panel.Host`: `relayout`, `show` (apilar otra página), `back`, `openScreen`, `modal`.
* **`T3PanelHost`**: lo que comparten quienes muestran páginas: pila de páginas (Esc retrocede), marco de papel con
  cabecera (título y botón ✕ / ‹ Back), modal de confirmación sobre la página y el reparto de entrada.
* **`T3PanelScreen`**: una página como pantalla propia, centrada sobre el fondo del kit.

## Teclado y foco (#22)

* **Tab / Shift+Tab** pasan al control siguiente / anterior de la página (cabecera, cuerpo, pie; da la vuelta). Sirve en un
  `T3Panel` y en un `T3Dialog` (pantalla propia o modal). Controles tabulables: `T3Button`, `T3Toggle`, `T3Check`, `T3Select`,
  `T3Tabs`, `T3List`, `T3Field`, `T3TextArea` (los que están `enabled`). En un panel, si el control queda fuera de la vista, el
  cuerpo se desplaza lo justo para mostrarlo.
* **Enter o Espacio** pulsan el botón, interruptor o casilla enfocado; en un `T3Select` abren la lista (también Abajo); en un
  `T3Tabs` enfocado, Izquierda/Derecha/Inicio/Fin cambian de pestaña. Con un botón enfocado, Enter pulsa ese botón y no la acción
  por defecto del diálogo.
* **Anillo de foco:** contorno latón (`brass`) de 1 px en el borde del control y otro más tenue por dentro
  (`T3Draw.focusRing`). Un clic sobre un botón no deja anillo; un clic sobre una lista, un desplegable o un `T3TextArea` sí le da el
  teclado. Los campos (`T3Field`/`T3TextArea`) ponen además el contorno latón al enfocarse.
* **Las páginas rehacen sus controles en cada `arrange`**, así que el foco se recuerda por identidad (listas, campos y áreas viven
  lo que la página) y, si no, por posición y clase en el anillo: tras re-maquetar, el mismo hueco lo recupera.
* **`T3List`:** Arriba/Abajo mueven el cursor de fila (la primera pulsación lo pone en la primera fila visible), Inicio/Fin saltan a
  los extremos, RePág/AvPág mueven una página, Enter/Espacio activan la fila del cursor (`Row.activate`, por defecto un clic
  izquierdo en la fila). `ensureVisible(i)` coloca también el cursor. El thumb del scrollbar se arrastra y un clic en la pista lo
  lleva allí.
* **`T3TextArea`:** Tab sale del cuadro; el teclado y los caracteres le llegan por `T3Form.charTyped`, que reenvían `T3PanelScreen`,
  `T3DialogScreen` y (con tres líneas) `T3Screen` (`mouseDragged`, `mouseReleased`, `charTyped`).

### Campos dentro de un panel que se desplaza

`T3Panel.placeField` ya no oculta la caja al tocar el borde: mientras el campo no está entero en el área, el kit dibuja su marco y su
texto recortados con la página (se desliza fuera de la vista sin saltar); cuando está entero vuelve a ser la caja vanilla (mismo
aspecto), y del todo fuera se oculta. Un campo enfocado que sale a medias pierde el foco (Tab lo vuelve a traer a la vista). Para
cabeceras y pies, `pinField` sigue siendo el modelo (siempre visibles). Un `T3TextArea` no tiene este problema: es del kit.

## Pantallas migradas (B4)

| Pantalla | Ahora | Notas |
|---|---|---|
| **Settings** | `SettingsPanel` (página) + `SettingsScreen` (host `T3PanelScreen`) | Tres pestañas: General (filtro, HUD), Notifications (interruptores, sonidos, señales en segundo plano con un `T3Select` para la barra de acción) y Connections (Environments…, Key binds…, Outbound events…, Reload from disk, Reset HQ world). Pie con estado, Save y Done. Mismo borrador y mismas reglas de guardado que antes. |
| **Environments** | `EnvironmentsPanel` + `EnvironmentsScreen` | `T3List` con las cuatro acciones por fila; Pair new… y Refresh en la cabecera. Unpair y Revoke piden confirmación con un modal del kit sobre la página. |
| **Pair** | `PairScreen` (`T3DialogScreen`) | Tres `T3Field`, interruptor de acceso (lectura + control / solo lectura), estado en vivo. Enter en cualquier campo empareja. |
| **OperatePair** | `OperatePairScreen` (`T3DialogScreen`) | Aviso de riesgo, comando del host, campo, estado. |
| **Confirm** | `ConfirmActionScreen` (`T3DialogScreen`) | Acción / hilo / entorno / aviso. Enter equivale a Cancelar, como antes (el foco estaba en Cancelar). |
| **Snooze** | `SnoozeScreen` (`T3DialogScreen`) | Tres presets y Cancelar. |

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
* Un modal abierto por el host sobre una página (`T3Panel.Host.modal`) **puede llevar campos** (`T3Field`, `T3TextArea`): el host lo
  maqueta una vez, al abrirlo (`modal(...)` reconstruye la pantalla) y en cada reconstrucción, no por frame. Los campos viven en la
  página, no en el diálogo, para conservar el texto. Mientras hay un modal, las cajas vanilla de la página se ocultan (su texto lo
  dibuja el kit bajo el velo) y no reciben ratón, foco ni teclas; el host reenvía al modal los caracteres y las teclas de edición.
* Los popups (`T3Select`) se dibujan al final con `g.nextStratum()`, como los menús de `T3Screen`.
* Evita glifos que solo existen en Unifont (⚙ y similares) en texto nuevo: `uiRender` los dibuja como cuadro de glifo ausente.

## Auditoría de literales (#22)

Se buscaron `g.text(`, `Component.literal`, `ChatFormatting` y colores `0x...` en el cliente. Las páginas y diálogos del kit
(`SettingsPanel`, `EnvironmentsPanel`, `PieceBrowserPanel`, Pair, OperatePair, Confirm, Snooze, la barra lateral y el propio kit)
ya usan solo tokens de `ChatTheme` o `T3Draw.Tone`. Corregido en esta rama: `DraftControls` (dos colores del compositor pasan a
`ComposerSkin.CHIP_ICON` y `ComposerSkin.FIELD_BG`). Lo que queda, y por qué:

| Dónde | Qué | Por qué queda |
|---|---|---|
| `ComposerSkin`, `ComposerView`, `RequestCard` | paleta propia oscura del compositor (T3 Code) | decisión de diseño: el compositor no es crema; un tema propio es otro trabajo |
| `ThreadStatus`, `ThreadStatusChip` | tonos Tailwind de T3 Code para la píldora de estado | se copian de T3 Code a propósito (misma etiqueta, prioridad y color) |
| `QueueScreen`, `BaseConfigScreen` y sus hijas (`EnvironmentEditScreen`, `OutboundScreen`, `OutboundDestinationScreen`), `RegisterProjectDialog`, `ThreadChatScreen`, `HqMenuScreen`, `MergeStationScreen` | widgets vanilla con texto blanco / `ChatFormatting` sobre el velo del kit | no hay token de "texto sobre el velo" (los tokens son para el papel); lo correcto es migrarlas a páginas del kit (mecánico con el kit actual) |
| `PeekHud`, `StatusBar`, `Nameplate`, `AttentionClient`, `MonitorRenderer` | HUD y rótulos dentro del mundo, fondos translúcidos oscuros | se dibujan sobre el mundo, no sobre el papel; el HUD tiene sus propios colores por entorno |
| `T3Screen` (opaco + color del entorno), `EnvironmentsPanel` (muestra de color), `EnvironmentEditScreen.PALETTE` | colores de entorno elegidos por el usuario | son datos, no estilo |
| `T3ThreadActions`, `*Commands`, `DemoClient`, `*Client` | mensajes de chat y toasts con `ChatFormatting` | chat vanilla: el color lo pone el chat, no el kit |
| `ModelPickerControl`, `ModelPicker*` | tooltips y literales del selector | lo reescribe otro hilo |
| `T3Select`, `T3PanelHost`, `ChatDraw.clip` | la flecha del desplegable, la cruz de cerrar y la elipsis son glifos Unifont (no Latin-1) | ya existian; ver la regla "evita glifos que solo existen en Unifont" de arriba; en texto nuevo no se usan |

## Lo que queda a medias

* `EnvironmentEditScreen`, `OutboundScreen`, `OutboundDestinationScreen`, `RegisterProjectDialog`, `QueueScreen`, `ArchiveScreen`,
  `HqMenuScreen` y `MergeStationScreen` siguen con widgets vanilla. Las tres de `config` (`BaseConfigScreen`) ya usan el fondo del
  kit, así que no desentonan al abrirse desde Settings; migrarlas es mecánico con el kit actual.
* El scroll de `T3Panel` es por rueda y por Tab (sin arrastrar su barra; la de `T3List` sí se arrastra).
* Los controles de `chrome` del host (el botón Cerrar / Atrás de la cabecera) no están en el anillo de Tab: se cierra con Esc.
* El selector de modelo (`ModelPicker*`) lo reescribe otro hilo y no se auditó aquí.
* `uiRender` no tiene fixtures de estas pantallas: la galería (`/t3dev ui`) es el camino. Pendiente: un tipo de pantalla
  `settings`/`environments`/`pair` en `Mock` (requiere sembrar `ConfigManager` en modo headless).
* El #15 (escala propia, fuente TTF, iconos) no se tocó; el kit dibuja con la fuente bitmap y el GUI scale de vanilla.

## Probarlo en la PC

Con el juego abierto (cliente de desarrollo), `/t3dev ui` lista los escenarios y `/t3dev ui <id>` abre uno con datos falsos:

```
/t3dev ui inbox-settings        página de ajustes dentro de T3Screen (lo que abre el ⚙)
/t3dev ui inbox-environments    lista de entornos falsos dentro de T3Screen
/t3dev ui settings              ajustes como pantalla propia (General)
/t3dev ui settings-notifications
/t3dev ui settings-connections
/t3dev ui environments          lista de entornos falsos (ok, connecting, offline, con control)
/t3dev ui pair                  emparejado (control por defecto)
/t3dev ui pair-readonly
/t3dev ui operate-pair
/t3dev ui confirm
/t3dev ui snooze
/t3dev ui launch                nuevo hilo
/t3dev ui kit-controls          todos los controles en una página: Tab, anillo de foco, Enter/Espacio, lista larga, área de texto, modal con campos
/t3dev ui inbox-kit-controls    la misma página dentro de T3Screen (el modal sobre una página de T3Screen)
/t3dev ui kit-dialog            diálogo con campo, área de texto, casilla, interruptor y desplegable
```

F3+T recarga `ui-style.json` y estas pantallas se re-maquetan. Qué mirar: que Esc retroceda página a página, que el scroll con
la rueda funcione en una ventana baja (GUI scale 4), que Enter en un campo de Pair empareje, que el desplegable de Notifications
se abra encima del resto, y que Unpair / Revoke abran el modal y lo cierren con Esc. De #22: Tab y Shift+Tab recorren los controles con un anillo latón;
Enter/Espacio pulsan; en `kit-controls` la lista larga responde a Arriba/Abajo/Inicio/Fin/RePág/AvPág y su thumb se arrastra; el campo
del cuerpo se desliza fuera de la vista sin saltar; "Modal with fields" abre un modal con un campo y un área de texto.
