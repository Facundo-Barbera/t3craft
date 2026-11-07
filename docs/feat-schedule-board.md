# feat/schedule-board — tablero de tareas programadas (ítem 15)

Vista de **solo lectura** de las tareas programadas de T3 (`scheduledTasks.subscribe`) en el
tablero del HQ y en el chat. Sin "ejecutar ahora" ni activar/desactivar: eso es escritura
(ítem 26) y no está aprobado.

## Qué muestra

Por tarea, ordenadas por la próxima ejecución (las que no tienen próxima ejecución van al final:
pausadas, bloqueadas por política, webhooks, one-offs ya hechos):

- nombre (título, o la primera línea del prompt si no hay título);
- cuándo: `in 12m`, `due`, `running`, `paused`, `blocked`, `on webhook`;
- programación resumida (`every 30m`, `09:30 Mon-Fri`, `once`, `webhook`), proyecto y entorno;
- última ejecución y su resultado (`last ✔ 3h 00m ago`, `last ✘ 5m ago`, `never run`).

Colores: cian = corriendo, rojo = falló la última, verde = la última fue bien, blanco = programada
sin ejecutar aún, gris = pausada o bloqueada.

- **Tablero en el mundo**: un `TextDisplay` FIXED de cliente, 6 tareas por página (2 líneas cada
  una) con cabecera `SCHEDULED TASKS 1/2`. Rota de página cada 8 s y vuelve a la primera cuando
  cambia el número de páginas. Sin hitbox ni clics.
- **Chat**: `/t3craft schedule` lista todas las tareas, una línea por tarea, con el error de la
  última ejecución si falló.

## Piezas (`dev.t3craft.client.schedule`)

| Clase | Qué es |
|---|---|
| `ScheduleSource` / `ConnectionScheduleSource` | De dónde vienen los datos: `open(DirectController, Sink) → Handle` (`close`, `stale`). Por defecto, el adaptador sobre `T3Connection.subscribeScheduledTasks`. |
| `ScheduleBook` | Últimas listas por entorno (`Sink`, thread-safe). `entries(envLabel, projectTitle)` ya ordenadas. |
| `ScheduleEntry` | Tarea + entorno + proyecto. `ORDER` (próxima ejecución, nulls al final, desempate estable). |
| `Countdown` | `span`, `until`, `ago`, `next(task)`, `last(task)`. |
| `SchedulePager` | `page`, `rotating(items, pageSize, elapsedMs, rotateMs)`. |
| `ScheduleText` | Texto del tablero (`Board(lines, background)`) y de la línea de chat. |
| `SchedulePlacement` / `SchedulePlacementSource` / `LayoutBoardPlacement` | Dónde va el tablero. |
| `ScheduleBoardHook` | Hook para el bloque "tablero de tareas" de feat/blocks-core. |
| `ScheduleRenderer` | El display (ids de entidad desde **−7.000.000**). |
| `ScheduleClient` | Entrypoint de cliente, tick, comando. |

Decodificación en `dev.t3craft.t3.ScheduledTask` (tolerante: campos de más se ignoran, campos que
faltan llevan defaults, sin id se descarta; estados desconocidos → `UNKNOWN`).
`RpcFrames.SUBSCRIBE_SCHEDULED_TASKS` y `RpcFrames.subscribeScheduledTasks(id)` (payload vacío). Solo pide `orchestration:read`:
no aparece en `clientRpcPermissions.ts`.

## Fuente de datos

`ConnectionScheduleSource` (la fuente por defecto) llama a `T3Connection.subscribeScheduledTasks(listener)`
en la conexión existente de cada entorno. Va por la vía compartida de feat/chat (`StreamRegistry`):
un único generador de ids, el `send()` serializado del socket, refcount, réplica para los que llegan
tarde y resuscripción tras cada reconexión. No hay ids propios, ni `sendText` directo, ni early-returns
en el listener de la conexión.

Para eso, `StreamRegistry` gana un overload **aditivo**, `subscribe(key, SubscribeFrame, Listener)`,
para streams que no son de un hilo. La clave es `T3Connection.SCHEDULED_TASKS_STREAM`
(`"rpc:scheduledTasks.subscribe"`, que no puede ser un id de hilo) y el frame se guarda por entrada.
`subscribe(threadId, listener)` no cambia.

- Cada item del stream es una lista completa: se entrega la más nueva decodificable del lote; lo que
  no es una lista se ignora.
- `onEnded(null)` (se cayó el socket) → el entorno queda como *reconnecting* hasta la siguiente
  lista. Un fallo del servidor (T3 viejo sin el RPC, error de política…) → *unavailable (motivo
  saneado)*.
- `ScheduleClient` reabre la fuente si se recrea el `DirectController`, si cambia el conjunto de
  conexiones (`Handle.stale()`, comprobado cada 10 ticks: emparejar o desemparejar un entorno) o al
  llamar a `setSource`. En modo bridge no hay tablero.

Nota: la réplica de `StreamRegistry` guarda hasta `REPLAY_CAP` items por stream. Aquí cada item es
una lista entera, así que en una sesión larga con muchos cambios se acumulan listas viejas hasta ese
tope. Es acotado, pero si molesta, `StreamRegistry` podría limitar la réplica de los streams de
"lista completa" al último item.

## Puntos de integración

- **HQ** (`setPlacementSource`): cuando el HQ esté colocado en el mundo,
  `setPlacementSource(() -> SchedulePlacement.onBoard(anchorDelBoardEnCoordenadasDeMundo))` con el
  anchor `AnchorNames.BOARD` (centro de la cara del tablero, 7×3 bloques). El texto se apoya
  1,35 bloques por debajo del centro y 0,05 por delante de la cara. Mientras tanto,
  `LayoutBoardPlacement` lo pone flotando 2,5 bloques detrás del sitio del tablero en el
  layout de reserva del registro (`NpcRegistry.fallbackLayout()`, el `GridLayout` que usan el
  BehaviorDirector y las workstations provisionales), mirando a los NPCs (solo si hay NPCs visibles).
- **Bloques** (`setBoardHook`): si feat/blocks-core trae su bloque "tablero de tareas", registra un
  `ScheduleBoardHook` y dibuja el mismo `ScheduleText.Board`. Si devuelve `true`, se ocultan los
  displays. Este módulo no depende de blocks.
- **Otras UIs**: `ScheduleClient.get().entries()`.

## Rangos de ids de entidad de cliente

| Desde | Módulo |
|---|---|
| −1.000.000… | NpcRegistry |
| −2.000.000… | builders |
| −3.000.000… | BehaviorDirector |
| −4.000.000… | helpers |
| −5.000.000… | workstation |
| −6.000.000… | blocks / archive |
| **−7.000.000…** | **schedule board** (`ScheduleRenderer.ID_BASE`) |

El barrido de huérfanos de NpcRegistry trata como suyo todo id ≤ −1.000.000. Por eso el renderer
comprueba `isRemoved()` en cada tick y vuelve a crear el display.
