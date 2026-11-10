# feat/hq-menu: menú del HQ (`dev.t3craft.client.demo.HqMenuScreen` + `HqMenuPanel`)

> Desde el #26 el menú es una página del kit de UI (`HqMenuPanel`) en un host `T3PanelScreen` (`HqMenuScreen`),
> no widgets vanilla. Abajo, lo que cambió en cada punto.

El botón **"T3Craft HQ"** de la pantalla de título y de la de selección de mundo ya no entra directamente
al mundo HQ: abre un menú propio, que vuelve a la pantalla de origen con **Esc** o la **X** de la cabecera.

## Qué hay en el menú

> Un `hqLayout` de la config, o un mundo HQ guardado, con un id eliminado o desconocido cae a la
> ciudadela y se escribe una línea WARN en el log (una por id). No se borra nada más que la elección
> de layout.
- **Abrir HQ**: lo mismo que hacía antes el botón (`DemoClient.open`): crea el mundo HQ la primera vez
  o lo abre, y entra. Si vanilla cancela la carga, se vuelve a este menú.
- **Tipo de layout del HQ**: la lista de `HqLayouts.all()` (de feat/hq-layouts), cada una con su nombre
  y una línea de descripción (`t3craft.hq.layout.<id>.name` / `.desc`).
  - Orden de presentación (`HqLayouts.menuOrder()`, feat/ui-hq-menu-lineage): primero el diseño
    principal (`HqLayouts.PRIMARY_ID` = `hybrid-ringcampus`, la ciudadela, etiqueta PRINCIPAL) y luego
    los demás en el orden del registro (hoy solo `classic`). No hay sección "Legacy": los 7 layouts
    antiguos (campus, ring, vertical, ensanche, village, hybrid-towncenter, hybrid-terraces) se
    eliminaron (cleanup/2-legacy-layouts, #4). La ciudadela es también el layout por defecto.
  - La lista es un `T3List`. La elegida va con el relleno `selected` y una barra de acento a la izquierda, y su
    nombre en acento. La construida (`HqLayoutState.builtLayoutId`), con la etiqueta **CONSTRUIDO** (token
    `result`). Las de `large = true` (hoy ninguna), con **MUY GRANDE** y la descripción en `youText`.
    Todos los colores salen de `ChatTheme`.
  - Se elige con clic izquierdo (`MouseButtons.isLeft`: 1 en 26.x), o con el teclado: Tab hasta la lista,
    las flechas, RePág/AvPág e Inicio/Fin mueven el cursor y Enter elige. Se guarda en el momento en la
    clave `hqLayout` de la config.
  - Si no cabe, la lista tiene scroll con la rueda y la barra arrastrable del kit (`T3List`).
  - "Abrir HQ" es el botón primario de la cabecera; con el foco (Tab), Enter o Espacio lo pulsan.
- **Reconstruir con &lt;layout&gt;**: solo aparece si hay un HQ construido y su layout no es el elegido.
  Si no hay HQ construido, basta con elegir: el próximo "Abrir HQ" construye con ese layout.
- **Resetear HQ**: solo activo si el mundo HQ existe.
- **Exportar / Recargar layouts** (feat/hq-layout-packs): en el pie, encima de Reconstruir y Resetear.

Línea de estado bajo la lista: "Construido con: X", "aún no hay nada construido" o "aún no hay mundo HQ".

## Reset y reconstrucción desde el menú (decisión)
"Resetear HQ" y "Reconstruir con X" usan el mismo flujo: un modal de confirmación del kit sobre la página que dice que antes
se guarda un **zip en `backups/`** (o, con `demo.resetBackup = false`, que **no** hay copia), que solo se
borra y se recrea el mundo HQ (`saves/<carpeta>`), con qué layout se construirá, y que no se toca ningún
otro mundo. Al aceptar se llama a `HqRebuild.resetAndRebuild(layoutId)` (punto único de feat/hq-layouts).

Desde el menú inicial **no hay mundo abierto**, así que se hace todo **desde fuera** y no "abrir y luego
resetear":
1. No hay servidor integrado en marcha, así que nada tiene la carpeta abierta (`session.lock`) y no hay
   que salir de ningún mundo. Abrir el mundo solo para borrarlo después sería más lento y cargaría
   chunks para nada.
2. La carpeta se valida igual que con `/t3craft debug reset-hq` (`HqReset.validate`): tiene que ser un
   directorio real (no un enlace simbólico), hijo directo de `saves/`, con el nombre exacto configurado,
   y su marcador, si lo tiene, tiene que ser de T3Craft. Nunca se borra por patrón.
3. Zip en `backups/`. Si falla, no se borra nada.
4. Se borra la carpeta, revalidándola, y se crea de nuevo con `VanillaDemoWorld.create`. Al entrar, la
   construcción normal levanta el HQ con el `hqLayout` elegido, que ya está guardado.

`DemoClient.resetRefusal()` sigue protegiendo los casos raros: si por lo que sea hay otro mundo abierto,
se muestra un modal con el motivo (antes un `AlertScreen`) y no se toca nada. El botón de reset de `SettingsScreen` no cambia.

## Código
| Clase | Qué hace | Test |
|---|---|---|
| `HqMenuModel` | Elegida, construida, persistencia solo si cambia, "reconstruir" solo si hay HQ construido y difiere, `warnLarge`, reset solo si existe el mundo | `HqMenuTest` |
| `HqMenuBridge` | Une el menú con feat/hq-layouts (registro, `hqLayout`, `builtLayoutId`, `HqRebuild`) | — (cableado) |
| `HqMenuPanel` | La página del kit (#26): cabecera con Abrir HQ, `T3List` de layouts, línea de estado, pie con Exportar/Recargar, Reconstruir y Resetear (modales de confirmación). Sus acciones externas pasan por `HqMenuPanel.Actions` (la galería usa unas falsas) | — (render) |
| `HqMenuScreen` | Host `T3PanelScreen` (un `Screen` normal, no `T3Screen`: se abre desde la pantalla de título), ancho máximo 380 px, y las acciones reales sobre `DemoClient` | — (cableado) |

`HqMenuList` y `HqMenuKeys` (geometría de la lista y teclas de la pantalla vanilla) se borraron en el #26: `T3List` hace
ambas cosas. `/t3dev ui hq-menu` abre el menú con los layouts reales y un HQ construido falso.

Textos en `en_us`, `es_es` y `es_mx` (`t3craft.hqmenu.*`).
