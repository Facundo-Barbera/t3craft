# feat/archive-terminal: la terminal de archivo

Decisión de Facundo: el archivo **no** es una estantería ni un slot por hilo. Es una **terminal**, un
bloque propio (`t3craft:archive_terminal`) donde se consultan los hilos cerrados. Reemplaza las
presentaciones por slot de feat/archive-view.

## Qué hay

| Pieza | Dónde | Qué hace |
|---|---|---|
| `t3craft:archive_terminal` (bloque, block entity e ítem) | `dev.t3craft.blocks.ArchiveTerminalBlock` / `ArchiveTerminalBlockEntity`, registrado en `T3Blocks.init` | Consola: escritorio con teclado y pantalla vertical atrás (`facing` = hacia dónde mira la pantalla). Modelo y texturas propios (`archive_terminal_{body,side,top,screen}`). Clic → pantalla de archivo. Receta: 6 hierro + panel de vidrio + librería + redstone. Se suelta a sí misma. |
| BER | `dev.t3craft.client.blocks.ArchiveTerminalRenderer` | En la pantalla: `ARCHIVE · N hilos` (ámbar) y los últimos 3 hilos cerrados (título; `● entorno · ✔/▣ fecha` en el color del entorno). Las líneas salen de `TerminalDisplay`, **cacheadas por `ArchiveStore.version()`**: con el archivo quieto, una comparación por frame. |
| Pantalla de archivo | `dev.t3craft.client.archive.ArchiveScreen` (host `T3PanelScreen`) + `ArchivePanel` (página del kit, #26) | Lista con scroll (`T3List`) de los hilos settled y archivados de **todos** los entornos, primero los cerrados más recientemente. Cabecera: búsqueda (`T3Field`; todas las palabras, en título, proyecto, entorno o id) y dos `T3Select` de **entorno** y **proyecto** con "all" primero (el de proyecto ofrece solo los del entorno filtrado). Cada fila: `EnvBadge` del entorno, título, proyecto, icono del tipo (`circle-check` settled, `archive` archivado) y fecha de cierre; clic izquierdo o Enter la abre. Pie: estado de lectura de cada entorno, **Older** (siguiente página de T3) y **Refresh**. Colores solo de `ChatTheme`. La búsqueda y los filtros siguen en `ArchiveList` (guardado por `ArchiveClient`, vuelve igual). Ya no pagina: la lista hace scroll (RePág/AvPág con el foco en la lista). |
| Abrir en solo lectura | `ArchiveOpener` → `ChatScreens.openReadOnly(env, thread, envLabel, title)` | El navegador del chat sobre ese hilo **sin acciones**: historial por la conexión compartida y barra de respuesta que solo dice "solo lectura" (sin enviar ni "Enable control"), aunque el entorno tenga operate. |
| Datos | `ArchiveStore` / `ArchiveSource` de feat/archive-view, sin cambios | `getArchivedShellSnapshot`, `getOlderSettledThreads`, caché, refresco cada 10 min, backoff y paginación. |

## Cómo se llega

`ArchiveAccess.routes(blocksModule)`:

- **Con el módulo blocks**: la terminal (en el hub del HQ o puesta a mano), el comando y el portátil.
- **Sin el módulo blocks** (modo solo GUI): no hay terminal. La misma pantalla se abre con
  **`/t3craft archive`** y con el botón **Archivo** del portátil (`PortableScreen`, arriba junto a las
  pestañas). El comando se registra siempre.

`/t3craft archive refresh` y `/t3craft archive status` siguen igual (status ya no habla de slots;
dice desde dónde se abre).

## El archivo vive solo en la terminal

- Se borraron `ArchiveRenderer`, `ArchiveShelf`, `ArchiveSlots` y `CatalogScreen`: no hay letreros,
  props, `Interaction` ni cartel por hilo en el mundo (el rango de ids −6.000.000 queda libre), y no
  hay `UseBlock`/`UseEntity`/`AttackEntity` del archivo.
- `HqBindings` ya no convierte las celdas `archive_<threadId>` del estante en monitores vinculados al
  hilo: quedan como el bloque liso del plan, sin mostrar ningún hilo. `HqBlockFixtures.ID` pasa a
  `blocks-3` (los mundos demo construidos se actualizan con un diff).
- El **estante en sí** (geometría, anchors `shelf@i`, `archive_<id>` y los carteles) sigue en
  `HqGenerator`: los NPC de hilos que hacen settle caminan hasta él (`HqWorldAnchors`). Sacarlo es
  trabajo del rediseño del HQ (feat/hq-style-cherry).

## Cómo coloca el plan la terminal (para el rediseño)

- **Anchor** `AnchorNames.ARCHIVE_TERMINAL = "archiveTerminal"`: `Anchor.face(celda, facing)`, la
  celda del bloque y la normal de la pantalla (hacia donde está el jugador al usarla).
- Hoy: `HqGeometry.ARCHIVE_TERMINAL = (7, 1, 5)`, contra la pared este del hub, al sur de la maceta de
  los bancos y al norte del rincón de lectura, pantalla al **oeste**. Grupo `hub`.
- **Vanilla**: el generador pone un **atril** (`Palette.archiveTerminal(facing)`) en esa celda.
- **Con el módulo blocks**: `HqBindings` da un fixture `Role.ARCHIVE_TERMINAL` (sin binding) en la
  celda del anchor y `HqBlockFixtures` la cambia por `States.archiveTerminal(facing)`
  (`t3craft:archive_terminal`). `States.archiveTerminal` tiene default (el atril), así que los
  stand-ins de otros tests no cambian.
- **Para reservarla en otro diseño** basta con publicar el anchor `archiveTerminal` en una celda libre
  del piso (con aire delante para el jugador) y poner ahí cualquier bloque: los fixtures siguen al
  anchor. Test: `HqArchiveTerminalTest`.

## Tests

`ArchiveListTest` (búsqueda, filtros por entorno/proyecto, paginación), `TerminalDisplayTest`
(cabecera, últimos 3, caché por versión), `ArchiveOpenerTest` (pedido de apertura en solo lectura),
`ArchiveAccessTest` (sin bloques: el comando abre el archivo; el comando ejecuta el opener),
`HqArchiveTerminalTest` (anchor y atril en todos los planes) y `HqBindingsTest` /
`ModBlockStatesTest` actualizados (fixture de la terminal, el estante ya no es fixture, el HQ con el
módulo usa `t3craft:archive_terminal`).

## Cambios estructurales → **RESTART**

- Bloque, block entity e ítem `t3craft:archive_terminal` (en la pestaña T3Craft); renderer
  `ArchiveTerminalRenderer`; blockstate, modelo, 4 texturas, loot table, receta, lang
  (`block.t3craft.archive_terminal`, `t3craft.archive.screen.*`, `terminal.*`, `hint.*`, `portable`).
- `ArchiveClient`: fuera los callbacks de uso/ataque y el render de entidades; el comando
  `/t3craft archive` se mantiene (siempre registrado).
- Botón "Archivo" en el portátil. `ChatScreens.openReadOnly/createReadOnly`,
  `ThreadBrowser.open(key, envLabel, title)` (aditivos).
- hq: `AnchorNames.ARCHIVE_TERMINAL`, atril en el hub. Sin cambios de config.

## Pendientes

- **No se probó dentro del juego** (por regla, no se lanza Minecraft): revisar a ojo que el texto de
  la pantalla quede sobre la cara de la carcasa (z = 6/16) y no espejado, el tamaño, y la forma
  rotada de la colisión.
