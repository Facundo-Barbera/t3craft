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
| `ScheduleSource` | De dónde vienen los datos. `open(DirectController, Sink) → Handle`. Este módulo **no** abre conexiones ni envía peticiones por su cuenta. |
| `ScheduleBook` | Últimas listas por entorno (`Sink`, thread-safe). `entries(envLabel, projectTitle)` ya ordenadas. |
| `ScheduleEntry` | Tarea + entorno + proyecto. `ORDER` (próxima ejecución, nulls al final, desempate estable). |
| `Countdown` | `span`, `until`, `ago`, `next(task)`, `last(task)`. |
| `SchedulePager` | `page`, `rotating(items, pageSize, elapsedMs, rotateMs)`. |
| `ScheduleText` | Texto del tablero (`Board(lines, background)`) y de la línea de chat. |
| `SchedulePlacement` / `SchedulePlacementSource` / `RingBoardPlacement` | Dónde va el tablero. |
| `ScheduleBoardHook` | Hook para el bloque "tablero de tareas" de feat/blocks-core. |
| `ScheduleRenderer` | El display (ids de entidad desde **−7.000.000**). |
| `ScheduleClient` | Entrypoint de cliente, tick, comando. |

Decodificación en `dev.t3craft.t3.ScheduledTask` (tolerante: campos de más se ignoran, campos que
faltan llevan defaults, sin id se descarta; estados desconocidos → `UNKNOWN`).
`RpcFrames.SUBSCRIBE_SCHEDULED_TASKS` es solo el nombre del método. Solo pide `orchestration:read`:
no aparece en `clientRpcPermissions.ts`.

## Fuente de datos: estado del adaptador

El coordinador pidió no añadir otra vía de peticiones a `T3Connection` (ids propios, `sendText`
directo, early-returns en el listener). La vía compartida (`StreamRegistry`, con
`streams.subscribe` con refcount, un único generador de ids y `send()` serializado por socket)
llega con feat/chat, que **aún no está en main**. Mientras tanto, la fuente por defecto es
`ScheduleSource.PENDING`: no envía nada a T3 y marca cada entorno como *waiting for the shared
stream path (feat/chat)*. El tablero y el comando lo muestran tal cual.

Cuando entre feat/chat, el adaptador queda en pocas líneas:

```java
ScheduleClient.get().setSource((controller, sink) -> {
	// por entorno: streams.subscribe(RpcFrames.SUBSCRIBE_SCHEDULED_TASKS, {}, value ->
	//     sink.tasks(envId, ScheduledTask.listFrom(value)))   // null = no era una lista: ignorar
	// fallo o fin del stream → sink.unavailable(envId, T3Connection.sanitize(reason))
	return () -> { /* cerrar las suscripciones */ };
});
```

`ScheduleClient` vuelve a abrir la fuente cuando se recrea el `DirectController` (reload) o al
llamar a `setSource`. En modo bridge no hay tablero.

## Puntos de integración

- **HQ** (`setPlacementSource`): cuando el HQ esté colocado en el mundo,
  `setPlacementSource(() -> SchedulePlacement.onBoard(anchorDelBoardEnCoordenadasDeMundo))` con el
  anchor `AnchorNames.BOARD` (centro de la cara del tablero, 7×3 bloques). El texto se apoya
  1,35 bloques por debajo del centro y 0,05 por delante de la cara. Mientras tanto,
  `RingBoardPlacement` lo pone flotando detrás del tablero provisional del `RingLayout` del
  BehaviorDirector, mirando a los NPCs (solo si hay NPCs visibles).
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
