# feat/t3-sidebar-rows — sidebar del T3 screen (Y) como T3 Code

Referencia: `t3craft-ref/ui/t3code-real.png` (sidebar izquierdo de T3 Code).

## Qué cambió

### Filas de 2–3 líneas (`t3ui/sidebar/SidebarRow`)

```
 [P] ★ proyecto ..................... ◌ Working 4m     línea 1, tenue
 Título del hilo, truncado…                    ✳ •    línea 2, texto principal
 ⎇ feat/rama  +141 −12  #87                    ✳ •    línea 3, solo si hay datos
```

- **Línea 1** (color tenue): icono del proyecto (inicial sobre un color estable; si hay hilos de
  varias máquinas, el icono es el de la máquina), ★ si está fijado, el proyecto (o `owner/repo` del
  PR, o la máquina si no hay proyecto) y a la derecha el estado:
  - spinner animado + `Working 4m`, contado desde que empezó la corrida (`activityRunStartedAt`, si
    falta `latestRunStartedAt`, si falta la última actividad); con el sidebar angosto queda spinner + `4m`;
  - `Needs input` en el color de atención, con un pulso suave, **solo** cuando `Attention.NEEDS_YOU`
    (o sea `ThreadState.NEEDS_ATTENTION`: respeta el arreglo de `waiting` de fix/thread-state-waiting,
    no se toca `ThreadState`);
  - `Approval` (pedido no bloqueante), `Failed`; si no, la antigüedad (`2d`).
- **Línea 2**: el título, truncado con "…" (tenue si está settled y no seleccionado).
- **Línea 3**, solo si hay rama, worktree, diff o PR: `⎇` (en acento si el hilo corre en su propio
  worktree) + rama, `+N` / `−N` del snapshot del PR, `#N` del PR con color por estado (open, draft,
  merged, closed). A la derecha (o en la línea 2 cuando no hay línea 3): glifo del proveedor (✳
  claude, ◎ codex, …) y un punto de "no leído" (una corrida terminó después de `lastVisitedAt`).
- Hover: fondo redondeado; seleccionado: tarjeta redondeada más clara (`panelHi` con borde) sobre el
  pozo del sidebar. Mantiene las acciones de feat/t3-ui-clean: en hover, ✓ settle y ★/☆ pin
  reemplazan el estado de la línea 1; clic derecho abre el menú del hilo.
- Tooltip con estado, máquina, proyecto, rama/worktree, PR, diff, modelo, no leído y snooze.

### Secciones pegadas abajo (`SidebarModel.dock()` + `SidebarView`)

- La lista principal (Pinned + carpetas de proyecto) hace scroll arriba, sola.
- Abajo, fijas sobre la barra de iconos del pie: **Working** (contador, abierta por defecto,
  plegable), **Snoozed** (contador, plegada) y **Settled** (contador, plegada, `›`). Solo aparecen
  si tienen hilos; al buscar se abren todas, como antes.
- Cada hilo está en un solo lugar: fijado → Pinned (aunque corra, con su spinner); si no, corriendo →
  Working; si no, con `snoozedUntil` futuro → Snoozed; si no, settled → Settled; si no, su proyecto.
  Cuando vence un snooze el hilo vuelve solo (`expireSnoozes`, un compare por frame).
- El dock ocupa como máximo el 55 % de la lista (la lista de arriba conserva al menos 34 px). Las
  secciones abiertas se reparten ese alto; la que no entra tiene su propio scroll con la rueda.
- ↑/↓ recorren la lista de arriba y después las secciones abiertas del dock; la selección se
  revela en la parte que corresponda.

### "Working" en el resto de la interfaz (`WorkingStatus`)

- Header del hilo: spinner + `4m 12s` a la izquierda del chip de estado mientras corre.
- Final del transcript: línea sutil `◌ Working… 4m 12s` que se desplaza con el feed
  (`ConversationPanel.setTrailer`, hook nuevo y aditivo).

## Datos nuevos

- `dev.t3craft.t3.ThreadExtras` (decodificado del mismo objeto del shell, colgado de
  `ThreadMeta.extras()`): `snoozedUntil`, `lastVisitedAt`, PR (`linkedPullRequest`, si no el último
  `pullRequests[]` que no sea `stack-dismissed`, si no `branchPullRequest`) con `state`/`isDraft` y
  `additions`/`deletions` del `snapshot`. `ThreadShell` no cambia de forma.
- `dev.t3craft.client.chat.ThreadDetails`, componente nuevo al final de `ThreadRow`
  (`ThreadRow.details()`, nunca null); el constructor viejo de 12 argumentos sigue. Se llena en
  `ThreadListCache.row` (modo directo) y, en modo bridge, con los chips del NPC (rama, worktree,
  modelo). Las filas del caché en disco no traen detalles hasta que llega el shell.

## Rendimiento

- Offsets de la lista sumados una vez por rebuild del modelo (el modelo entrega una lista nueva
  solo entonces), búsqueda binaria de la primera fila visible, se dibujan y prueban solo las filas
  visibles (scissor, scroll por píxel).
- Textos truncados y anchos en `ClipCache` (por texto y ancho, se vacía al pasar 4096 entradas o al
  cambiar la fuente).
- El spinner son 8 `fill` sin estado. Los tiempos se formatean solo para las filas visibles.

## Archivos

- nuevos: `t3ui/sidebar/{SidebarView,SidebarRow,Spinner,ClipCache,WorkingStatus}.java`,
  `chat/ThreadDetails.java`, `t3/ThreadExtras.java`
- tocados: `SidebarModel` (dock), `ThreadRow`, `ThreadListCache`, `ThreadMeta`,
  `ConversationPanel` (trailer), `T3Screen` (dibuja con `SidebarView`; `drawList` delega, rutas de
  clic/scroll/tooltip, spinner en el header, trailer).

RESTART: cambian las formas de los records `ThreadRow` y `ThreadMeta` (no entra por hot swap).
