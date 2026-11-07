# feat/parallel-build: construcción en paralelo con presupuesto global

Petición de Facundo: que los edificios (HQ, sitios de agentes, builders en supervivencia y el demo) coloquen
varios bloques a la vez en vez de uno por uno, sin tirar el TPS.

## Qué cambia

### Frentes por job (`Fronts`, `FrontCursor`)
- Un `BuildJob` se reparte en **N frentes**. Un *segmento* es un tramo consecutivo del plan con la misma `y`
  (la misma idea de "capa" que `MaterialMeter`). Dentro de cada segmento, las entradas se cortan en N trozos
  contiguos del orden del plan. Con `Layers` (filas por `z`, serpenteando en `x`), cada trozo es una
  **franja de filas**: un área compacta para un constructor.
- **Barrera por capa**: ningún frente empieza el segmento `s+1` hasta que todos terminaron el `s`. Nunca se
  coloca encima antes que debajo. Si un plan pone algo de arriba hacia abajo (colgantes), cada vuelta de
  `y` es un segmento nuevo y el orden entre capas se mantiene tal cual.
- Las entradas en la misma posición ("limpiar con aire y después colocar") nunca se separan en frentes
  distintos.
- Por tick, la asignación se convierte en crédito y los frentes con trabajo dan un paso cada uno por turno
  (round-robin que arranca después del último servido). El crédito no se acumula en pausa y los bloques
  ya colocados (`SKIPPED`) siguen siendo gratis, con un tope por tick, igual que en `BuildCursor`.
- `BLOCKED` pausa **solo ese frente** y los demás siguen. Cuando todos los frentes que tienen trabajo
  están bloqueados, el cursor pasa a `PAUSED` (`blocked()`). `resume()` desbloquea y reintenta las mismas
  entradas.
- Cada índice del plan se aplica **exactamente una vez**, con cualquier combinación de pausas, bloqueos y
  asignaciones. Hay un test aleatorio de 300 rondas.
- La cuadrilla tiene **un constructor por frente**: el frente se calcula como
  `BuildTuning.frontsFor(crew) = min(frontsPerJob, crew)`. El evento `placed` lleva el frente
  (`job.frontOf(index)`) y `BuildCrew` manda al constructor de ese frente. Así cada uno trabaja su franja,
  en lugar del round-robin global de antes.
- `BuildJob.rate() = blocksPerTick × frentes`: cada frente va al ritmo que pidió el llamador.

### Presupuesto global por tick (`BuildBudget`, `BuildTuning`, `TickGovernor`)
- `BuildBudget.global()` es compartido por **todos** los que escriben bloques en el hilo del servidor:
  - `BuildManager` (cuadrillas: site/HQ con `CrewBridge`, demo, `build-demo`);
  - `PacedBuilder` de site (respaldo sin cuadrilla);
  - `PacedHqWriter` (respaldo in-place del HQ).
- Por tick, el primero que llega llama a `beginTick(tickCount, mspt)`. Es idempotente dentro del mismo tick.
- Cada runner hace `claim` de lo que quieren sus jobs. Un runner recibe como mucho
  `presupuesto / runners que pidieron el tick anterior`, así que ninguno deja sin nada a otro.
- Dentro de un runner, `allocate` reparte con **equidad max-min** (water filling). Nadie recibe más que su
  `rate()`, y lo que deja un job chico se reparte entre los demás.
- **Jobs concurrentes**: solo los primeros `maxConcurrentJobs` en `PENDING`/`RUNNING` (en orden de envío)
  colocan bloques. El resto queda en `PENDING` hasta que haya un hueco. Un job pausado (por el jugador o
  esperando materiales) **no ocupa hueco**, así no bloquea la cola.
- **Efectos**: `ServerLevelSink` y `EffectsBlockWorld` piden `BuildBudget.effect()` antes de cada
  sonido + ráfaga de partículas: como mucho `maxEffectsPerTick` por tick entre todos los jobs. El bloque se
  coloca igual; solo se omite el efecto.
- **Autoajuste por MSPT** (`TickGovernor`): cada 20 ticks mira `MinecraftServer.getCurrentSmoothedTickTime()`.
  - Por encima de `targetMspt` (40 ms), el presupuesto ×0,7.
  - Por debajo del 75 % del objetivo (30 ms), sube +10 % hasta el 100 %.
  - Entre medio se mantiene (sin oscilar).
  - Nunca baja de 1/16 ni de 1 bloque por tick.

### Configuración (aditiva, sección `build` de `config/t3craft.json`)
```json
"build": {
  "maxBlocksPerTick": 48,
  "maxConcurrentJobs": 3,
  "frontsPerJob": 4,
  "maxEffectsPerTick": 8,
  "autoThrottle": true,
  "targetMspt": 40
}
```
- Si falta la sección o una clave, se usa el valor por defecto. Si un valor es inválido, se usa el valor
  por defecto y el aviso nombra solo la clave.
- Solo lectura: el `ConfigStore` de feat/config conserva las secciones desconocidas.
- Se relee al arrancar el cliente, en cada `SERVER_STARTED` (entrar a un mundo) y con
  `/t3craft debug build-tuning reload`.
- `/t3craft debug build-tuning` muestra el máximo, el presupuesto vigente tras el autoajuste y el % de
  throttle.
- `build-status` ahora muestra los frentes de cada job.

### Valores por defecto y por qué
| Clave | Valor | Motivo |
|---|---|---|
| `frontsPerJob` | 4 | Un frente por constructor: las cuadrillas del HQ (`CrewBridge.CREW`) y del demo (`demo.crew`) son de 4, y `build-demo` de 3 (le tocan 3 frentes). Con 4 frentes, cada job va **4× más rápido** sin cambiar el ritmo por frente que ya pedía cada llamador. |
| `maxBlocksPerTick` | 48 | Un job del HQ o del site quiere 8 × 4 = 32 por tick. 48 deja correr ese job entero más un in-place o el demo (2 × 4 = 8) a la vez. Con `UPDATE_CLIENTS \| UPDATE_KNOWN_SHAPE` (sin updates de vecinos), un `setBlock` cuesta del orden de microsegundos, incluida la luz: 48 por tick son bastante menos de 1 ms de los 50 ms del tick. Unos 960 bloques/s en total. |
| `maxConcurrentJobs` | 3 | Site (o HQ), un in-place del HQ y el demo o `build-demo` pueden avanzar juntos. Más jobs solo reparten el mismo presupuesto en más trozos. |
| `maxEffectsPerTick` | 8 | 8 sonidos y ráfagas por tick (160/s) ya se oyen como una obra en marcha. Con 32-48 bloques por tick y efectos sin tope, el servidor mandaría cientos de paquetes de partículas por segundo y el motor de sonido se saturaría. |
| `autoThrottle` / `targetMspt` | true / 40 ms | Deja un 20 % de margen sobre el tick de 50 ms (20 TPS). Si el servidor ya va justo (granjas, chunks generándose), la obra cede antes de que caiga el TPS. |

Antes, el HQ y el site iban a 8 bloques por tick, el demo a 2 y `build-demo` a 0,5. Ahora, con los valores
por defecto, van a 32, 8 y 1,5 respectivamente, dentro del tope global de 48. `PacedHqWriter.PER_TICK`
pasa de 8 a 32, también dentro del presupuesto.

## Qué no se rompe
- **Snapshots y undo del site**: `SiteService.expand` sigue guardando y verificando el snapshot *antes* de
  `builder.submit`. Los frentes solo cambian el orden dentro del plan ya guardado. El guard "no pisar
  cambios del jugador" se sigue preguntando justo antes de cada celda (en el sink envuelto o en
  `PacedBuilder.step`), así que undo y move funcionan igual.
- **Resume (idempotente)**: reanudar vuelve a mandar el mismo plan. Lo que ya está en el mundo se salta
  (`SKIPPED` / `alreadyThere`) y el resto se coloca una sola vez. Lo cubren tests con frentes en `BuildJob`
  y en `PacedBuilder`. Pausar y reanudar un job conserva la posición de cada frente.
- **Materiales (supervivencia)**:
  - al empezar cada capa se comprueba que alcance para **todo lo que le queda a la capa, en todos los
    frentes**: el job espera antes de una capa, no a mitad;
  - cada frente cobra bloque a bloque (`canPay` + `paid`);
  - si a un frente se le acaba su ítem a mitad de capa, ese frente se pausa y los demás siguen con lo que
    tienen;
  - cuando todos los frentes que tienen trabajo esperan, el job pasa a `waitingForMaterials` con
    `missingMaterials()`, reintenta cada 20 ticks y se reanuda solo, igual que antes;
  - la pausa del jugador sigue ganándole a la reanudación automática.
- **Efectos**: con tope por tick (ver arriba).
- **Animación**: cada frente tiene su constructor.
- **API anterior**: los constructores y `submit` de antes siguen y usan 1 frente. `BuildCursor`,
  `MaterialMeter.before/retry` y `PacedBuilder(world, bpt)` (1 frente, sin presupuesto compartido) se
  mantienen, y sus tests pasan sin cambios.

## Límite conocido: módulos del HQ en paralelo
`HqConstruction` sigue haciendo **una ampliación de site a la vez**: el journal del site (`BUILDING` de la
ampliación de arriba) es lo que garantiza "snapshot antes de escribir" y deshacer por ampliación, y
`SiteService.expand` responde "busy" mientras hay una obra. Ampliar en paralelo exigiría un journal con
varias ampliaciones en obra. Eso lo decide site y queda fuera de esta rama.

Sí pueden correr a la vez, hasta `maxConcurrentJobs` y dentro del presupuesto, la ampliación en curso, los
in-place del HQ, el demo y `build-demo`. Cada módulo ya va 4× más rápido gracias a los frentes.

## Cambios estructurales
- `build`, clases nuevas: `Fronts`, `FrontCursor`, `BuildBudget` (estático `global()`, como `Survival`),
  `BuildTuning` (record) y `TickGovernor`.
- `BuildJob`:
  - usa `FrontCursor` en vez de `BuildCursor`;
  - constructor nuevo con `fronts`;
  - métodos nuevos `tick(allowance, sink, cb)`, `rate()`, `fronts()`, `frontOf()` y `frontWaiting()`.
- `BuildManager`: `submit(..., wrap, fronts)` y un constructor con `BuildBudget`. Reparte el presupuesto
  global y respeta `maxConcurrentJobs`.
- `MaterialMeter`: métodos `affordable(int[] pending, …)` y `canPay`.
- `PacedBuilder`: constructor `(world, bpt, fronts, Allocator)`. `SiteRuntime.paced` lo usa con el
  presupuesto global.
- `PacedHqWriter`: toma del presupuesto global y `PER_TICK` pasa a 32.
- `ServerLevelSink` y `EffectsBlockWorld`: tope de efectos por tick.
- `BuildersClient` y `BuildCrew`: frentes según la cuadrilla y un constructor por frente.
- Comando nuevo `/t3craft debug build-tuning [reload]`, bajo el `/t3craft debug` existente.
- Config: sección nueva `build` (aditiva).
- Sin entrypoints, registros ni keybinds nuevos. Aun así: **RESTART**, porque cambia la forma de las clases
  (`BuildJob` cambia de campos) y hay un comando nuevo que se registra al iniciar el cliente.

## Tests
- `FrontsTest` (5):
  - capas → segmentos repartidos en frentes;
  - una capa que vuelve es un segmento nuevo;
  - la misma posición queda en el mismo frente;
  - partición exacta con planes aleatorios;
  - con un frente, el orden es el secuencial de antes.
- `FrontCursorTest` (7):
  - frentes en paralelo en el mismo tick;
  - barrera por capa;
  - un frente bloqueado espera y el job se pausa después;
  - resume sin duplicar ni saltar (aleatorio);
  - sin ráfagas tras una pausa y skips acotados;
  - asignación fraccionaria y cero;
  - plan vacío y cancelación.
- `BuildBudgetTest` (7):
  - reparto max-min;
  - huecos concurrentes;
  - presupuesto compartido entre jobs y runners;
  - `beginTick` idempotente y tope de efectos;
  - autoajuste por MSPT: baja y se recupera;
  - banda muerta y desactivado;
  - config aditiva y validada.
- `ParallelBuildJobTest` (5):
  - 4 frentes = 4× más rápido respetando capas, un frente por constructor;
  - presupuesto global y límite de concurrencia entre jobs;
  - resume con frentes;
  - materiales por frente con pausa del frente y después del job;
  - comprobación de la capa entera entre frentes.
- `ParallelPacedBuilderTest` (3): frentes por capa en el site, presupuesto y concurrencia, resume tras una
  interrupción.

## Pendientes
- Sin probar en juego (no se lanzó Minecraft): falta ver la animación de los 4 constructores por franjas y
  el autoajuste con un servidor cargado.
- Con presupuestos altos, la colocación sigue sin esperar a que llegue el constructor (como antes). Ahora
  cada constructor solo cubre su franja.
- Si hace falta, los módulos del HQ en paralelo requieren cambios en el journal del site (ver arriba).
