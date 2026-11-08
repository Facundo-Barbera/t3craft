# feat/hq-menu: menú del HQ (`dev.t3craft.client.demo.HqMenuScreen`)

El botón **"T3Craft HQ"** de la pantalla de título y de la de selección de mundo ya no entra directamente
al mundo HQ: abre un menú propio, que vuelve a la pantalla de origen con **Esc** o **Volver**.

## Qué hay en el menú
- **Abrir HQ**: lo mismo que hacía antes el botón (`DemoClient.open`): crea el mundo HQ la primera vez
  o lo abre, y entra. Si vanilla cancela la carga, se vuelve a este menú.
- **Tipo de layout del HQ**: la lista de `HqLayouts.all()` (de feat/hq-layouts), cada una con su nombre
  y una línea de descripción (`t3craft.hq.layout.<id>.name` / `.desc`).
  - La elegida va con un marco blanco y "▶". La construida (`HqLayoutState.builtLayoutId`), con la
    etiqueta verde **CONSTRUIDO**. Las de `large = true` (campus), con **MUY GRANDE** y la descripción en
    naranja.
  - Se elige con clic izquierdo (`MouseButtons.isLeft`: 1 en 26.x) o con las flechas, RePág/AvPág e
    Inicio/Fin. Se guarda en el momento en la clave `hqLayout` de la config.
  - Si no cabe, la lista tiene scroll con la rueda (una fila por muesca), una barra a la derecha, y el
    hit-testing tiene en cuenta el scroll y solo acepta clics dentro del recuadro visible
    (`HqMenuList`).
  - Enter (`InputConstants.KEY_RETURN`, 40) abre el HQ si ningún botón tiene el foco. Si lo tiene, lo
    pulsa (vanilla).
- **Reconstruir con &lt;layout&gt;**: solo aparece si hay un HQ construido y su layout no es el elegido.
  Si no hay HQ construido, basta con elegir: el próximo "Abrir HQ" construye con ese layout.
- **Resetear HQ**: solo activo si el mundo HQ existe.

Línea de estado bajo la lista: "Construido con: X", "aún no hay nada construido" o "aún no hay mundo HQ".

## Reset y reconstrucción desde el menú (decisión)
"Resetear HQ" y "Reconstruir con X" usan el mismo flujo: un `ConfirmScreen` vanilla que dice que antes
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
se muestra un `AlertScreen` y no se toca nada. El botón de reset de `SettingsScreen` no cambia.

## Código
| Clase | Qué hace | Test |
|---|---|---|
| `HqMenuModel` | Elegida, construida, persistencia solo si cambia, "reconstruir" solo si hay HQ construido y difiere, `warnLarge`, reset solo si existe el mundo | `HqMenuTest` |
| `HqMenuList` | Geometría de la lista: scroll acotado, `rowAt` con scroll, `ensureVisible`, rueda, barra | `HqMenuTest` |
| `HqMenuKeys` | Flechas, RePág/AvPág, Inicio/Fin, Enter y Enter numérico con códigos de 26.x; solo el clic izquierdo elige | `HqMenuTest` |
| `HqMenuBridge` | Une el menú con feat/hq-layouts (registro, `hqLayout`, `builtLayoutId`, `HqRebuild`) | — (cableado) |
| `HqMenuScreen` | La pantalla (vanilla, sin mixins). Ancho máximo de 340 px, siempre se ve al menos una fila, y los botones se dibujan por encima en ventanas muy bajas | — (render) |

Textos en `en_us`, `es_es` y `es_mx` (`t3craft.hqmenu.*`).
