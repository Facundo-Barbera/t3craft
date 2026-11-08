# fix/hq-builders-ai: builders que trabajan por zonas, al alcance y con soporte

Pedido de Facundo: "Mejorar el algoritmo de movimiento y construcción de los builders para que sea más realista.
Actualmente solo ponen bloques en orden de capa y no respetan la proximidad de los builders."

Investigación previa (Baritone, MineColonies/Structurize, el printer de Litematica, el schematicannon de Create,
GDMC, asignación multiagente y licencias): `docs/research/builders-ai.md`.

## Resumen del algoritmo elegido y por qué
**Zonas con k-means balanceado, el elegible más cercano con soporte y caminar al punto de trabajo, todo simulado
de antemano.**

Al crear el job, `CrewPlanner` simula a la cuadrilla:
- **Zonas:** reparte las columnas del plan entre los builders con k-means balanceado (semillas *farthest-point*,
  capacidad del 115 % de la media).
- **Orden:** cada builder coloca el bloque **elegible** más cercano de su zona, con una penalización por altura
  (de abajo arriba, pero solo en lo local).
- **Elegible:** el bloque tiene soporte (la capa base o un vecino de cara ya puesto), y si es decoración, sus
  vecinos sólidos ya están puestos.
- **Alcance:** si el bloque está a ≤ 4,5 bloques de los ojos, lo pone sin moverse. Si no, camina al punto de parada
  libre más cercano desde el que lo alcanza.
- **Reequilibrio:** quien se queda sin trabajo roba el elegible más cercano de otra zona (subasta mínima).

El resultado es un horario determinista: un carril por builder, con dependencias y ticks de camino.

**Por qué este:**
- Es la idea de Baritone y del printer de Litematica (colocable = con soporte y al alcance, si no, caminar) más
  las etapas de Structurize (decoración después de su soporte), pero **local** en vez de por capa global.
- Hungarian o CBBA completos serían caros e innecesarios con ≤ 4 builders centralizados.
- Simularlo de antemano lo hace **determinista, reanudable y testeable** sin Minecraft.
- No cambia **cuántos** bloques por tick (el presupuesto de H1), solo **cuáles** y **quién**.
- Todo es reimplementación propia: Baritone es LGPL y MineColonies GPL, no se copió código.

## Piezas (`dev.t3craft.build`)

| Clase | Rol |
|---|---|
| `CrewPlanner` | Planificador puro (ints). Zonas, elegibilidad (misma posición, soporte, decoración), orden local, puntos de parada con alcance real (`REACH` 4,5 desde los ojos), separación entre builders (`SEPARATION` 2 celdas; nunca la misma celda), robo de trabajo y `force()` sin interbloqueos (ver abajo). `travel(...)` es la métrica común de recorrido. |
| `CrewSchedule` | Resultado inmutable: carriles, ticks de camino, punto de parada de cada paso, dependencias, orden global (topológico), dueño de cada entrada, inicio de cada builder, `forced` y `relaxed`. `CrewSchedule.of(BuildPlan, k)`: la decoración es `!state.canOcclude()`. |
| `CrewCursor` | Ejecuta el horario. Aplica la cabeza de un carril cuando sus dependencias están aplicadas y el builder terminó de caminar. Lo que ya está en el mundo se salta sin caminar (reanudar). Mismas reglas de crédito, saltos, bloqueo por materiales y pausa que `FrontCursor`. En supervivencia, el orden se corta en segmentos de 256 para el chequeo de materiales. |
| `DeferredCursor` | Los planes de más de 4096 entradas se planifican en un hilo aparte (`t3craft-crew-planner`). El job queda en `PENDING` mientras tanto. Si la planificación falla, se usa el orden por capa. |
| `JobCursor` | Interfaz común de `FrontCursor` (el de H1, con su `PlacementOrder` enchufable) y `CrewCursor`. |
| `BuildPlanner` | `LAYERS` (por defecto en la API: `FrontCursor` + `PlacementOrders.current()`) o `CREW`. |

Integración:
- `BuildJob(..., fronts, planner)` y `BuildManager.submit(..., fronts, planner)`. Los overloads anteriores siguen
  en `LAYERS`, así que los tests y llamadores de antes no cambian.
- `BuildListener.walking(job, front, index, standWorld, ticks)` (método default) y
  `BuildJob.tick(..., WalkCallback)`.
- **Cliente:**
  - `BuildersClient` lanza las cuadrillas con `CREW` (HQ, site, demo y `build-demo`);
  - con `walking`, `BuildCrew` calcula una ruta con el **A\* de los NPC** (`GridPathfinder` sobre
    `Collision.of(level)`, bloques reales) hasta el punto de parada;
  - `BuilderAvatar.follow` recorre los waypoints. La velocidad se ajusta para llegar cuando el servidor coloca
    (entre 0,22 y 0,6 bloques por tick);
  - solo hay teletransporte si el avatar lleva 60 ticks sin avanzar (atasco);
  - el último tramo hacia un andamio virtual va en línea recta (el avatar no tiene gravedad).

### Detalles de la elegibilidad
- **Aire (limpiar):** sin soporte. Lo hace el builder de la zona en cuanto puede, sin caminar. Es la etapa CLEAR.
  Las remociones del diff conservan su orden.
- Si una limpieza posterior le quita el soporte a una entrada que estaba en cola, sale de la cola hasta que
  cambie un vecino.
- **Ciclos decoración↔sólido** (un bloque apoyado sobre una valla): cuando nadie tiene nada elegible, se coloca
  primero la entrada con soporte más baja, saltándose la regla de decoración (`relaxed`). Solo si no hay ninguna
  con soporte se coloca la más baja sin soporte (`forced`: el plan la deja flotando).

## Lo que no cambia
- **Resultado final idéntico:** las entradas de una misma posición conservan su orden relativo. El test compara
  el mundo final con el plan.
- **Determinista y reanudable:** el mismo plan da el mismo horario. Al reanudar, lo que ya está se salta.
- **Presupuesto de H1:** el allowance por job sale de `BuildBudget` igual que antes. Caminar no consume
  presupuesto, y mientras un builder camina los demás siguen.
- **Orden de aparición:** un job solo pasa a `COMPLETED` tras aplicar la última entrada (test). La ampliación del
  site emite `built` al completarse, y `NpcGate` (H1) y `HqWorldAnchors` mantienen a los NPC de escritorio fuera
  hasta entonces. El orden de grupos y fases (`HqPlans.byGroup`, `PartSchedule`) no se tocó.

## Métrica de recorrido (20 proyectos, cuadrilla de 4 por grupo)
Recorrido total de los builders con el mismo evaluador (`CrewPlanner.travel`: para cada bloque fuera del alcance,
caminar en línea recta al punto más cercano desde el que se alcanza). Se compara con el orden por capa y franjas
(`Fronts`) de antes, sumado sobre todos los grupos del HQ.

| Layout | Entradas | Recorrido crew | Recorrido por capa | Crew / capa | Planificación (total / grupo máx.) |
|---|---:|---:|---:|---:|---:|
| classic | 73 821 | 6 876 | 19 535 | 35 % | 0,4 s / 46 ms |
| campus | 275 842 | 77 873 | 279 615 | 28 % | 2,5 s / 356 ms |
| ring | 350 226 | 47 253 | 272 825 | 17 % | 1,6 s / 319 ms |
| vertical | 240 153 | 27 271 | 74 871 | 36 % | 0,7 s / 113 ms |
| ensanche | 392 256 | 31 302 | 122 692 | 26 % | 1,2 s / 112 ms |
| village | 515 529 | 48 180 | 134 887 | 36 % | 3,1 s / 125 ms |

Los builders caminan entre **3 y 6 veces menos**. Medido con `HqBuildersAiTest` (lo imprime como `[builders-ai]`)
antes de los dos últimos ajustes (semillas *farthest-point* y revalidar el soporte en cola). El coordinador debe
volver a correrlo, pero las cifras deberían quedar en el mismo orden.

## Tests nuevos
- `dev.t3craft.build.CrewPlannerTest`:
  - invariantes en una casa sintética con 1, 2 y 4 builders;
  - el farol colgante va después del techo;
  - dos torres lejanas, cada builder en la suya (sin capa global);
  - lo flotante va al final sin interbloqueo;
  - limpiar y luego poner;
  - 40 planes aleatorios;
  - recorrido menor que por capa;
  - `CrewCursor`: cada entrada una vez, dependencias y caminatas; espera a que el builder llegue; reanudar sin
    caminar; frente bloqueado → pausa → resume; `COMPLETED` solo tras la última colocación.
- `dev.t3craft.hq.HqBuildersAiTest`:
  - para los 6 layouts con 20 proyectos y cada grupo con 4 builders: mundo final = plan, nunca sin soporte (salvo
    `forced`), decoración después de sus sólidos (salvo `relaxed`), todo al alcance desde el punto de parada,
    nunca colocar dentro del propio cuerpo, una entrada por dueño y nunca dos builders en la misma celda,
    dependencias antes que la entrada, y recorrido crew < por capa;
  - además, determinismo del horario.

## Pendientes
- Sin probar en juego (regla: no se lanza Minecraft). Falta ver cómo queda caminar con A\*, la velocidad y el
  andamio virtual.
- El alcance no hace raytrace (no comprueba que haya una cara visible), solo la distancia.
- Los andamios son virtuales: el avatar flota, no se colocan bloques temporales. Así el resultado final queda
  idéntico.
