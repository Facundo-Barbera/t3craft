# fix/hq-build-parallel: el HQ se construye en varias partes a la vez

Feedback de Facundo: "Las edificaciones grandes tardan mucho en construirse, siento que deberíamos
paralelizar múltiples partes a la par". Y después: "Siento que tal vez deberíamos de generar las
estructuras antes que los trabajadores porque se ve feo que queden ahí esperando a que se termine el
edificio".

## Dónde se iba el tiempo (main, 4ba5183)
El HQ del mundo HQ (donde viven los 6 layouts) lo construye `DemoBuildRun`: el plan de `DemoHq.full`
(zócalo + un plan por grupo: edificio, torre, manzana, ala, camino…) **parte por parte, una detrás de
otra**. Cada parte:
1. trae su cuadrilla de 4 builders, que caminan unos 14 bloques hasta el borde (`BuildCrew`, hasta 200 ticks);
2. coloca a `demo.blocksPerTick` (2) × 4 frentes = **8 bloques por tick**, aunque el presupuesto global
   (`build.maxBlocksPerTick`) es 48.

Campus con 20 proyectos son 74 partes y 126 206 bloques colocados (más 149 636 celdas de aire que se
saltan gratis): unos 17 minutos de obra.

## Qué cambia

### 1. Partes en paralelo (`build/PartSchedule`, `DemoBuildRun`)
- Cada parte tiene su caja. La parte `j` **depende** de toda parte anterior `i < j` cuya caja, agrandada
  `MARGIN = 1` bloque, toca la suya. Una parte arranca cuando terminaron sus dependencias.
- `DemoBuildRun` lleva hasta `crews = min(4, maxConcurrentJobs + 1)` cuadrillas a la vez (4 con los
  valores por defecto): 3 colocan, que son los huecos del presupuesto, y la cuarta va llegando mientras
  tanto. Siempre se toma la parte lista de índice más bajo (determinista). Así, barrios, torres y
  manzanas lejanas suben juntas, y lo que se toca sigue en orden.
- **Resultado idéntico.** Dos partes que escriben la misma celda se solapan siempre, así que la posterior
  corre después, como en secuencia. Las que corren a la vez tocan celdas disjuntas (y ni siquiera son
  vecinas), así que su orden no cambia nada. Mismo plan, mismos bloques: solo cambian el orden y la
  concurrencia. Dentro de cada parte, los frentes siguen siendo franjas disjuntas.
- **Ritmo por frente**: `max(demo.blocksPerTick, maxBlocksPerTick / frentes)` = 12. Una cuadrilla sola
  puede usar todo el presupuesto, y varias lo reparten con equidad max-min (`BuildBudget.share`).
- **Reanudable**: la marca del mundo se escribe solo cuando terminan todas las partes. Si se sale a
  mitad de la obra, la próxima visita vuelve a lanzar el plan entero en el mismo orden de dependencias:
  lo que ya está bien se salta y lo demás se coloca.

### 2. Presupuesto por tick (`BuildBudget`)
Se mantiene lo de feat/parallel-build: 48 bloques por tick como mucho entre todos, 3 jobs colocando,
8 efectos (sonido + partículas) por tick y bajada por MSPT. Se añade:
- **Límite de tiempo**: `MS_PER_TICK = 8 ms` de colocación por tick. `ServerLevelSink` mide cada
  `setBlock`, que incluye la luz, el marcado de la sección y el paquete al cliente
  (`BuildBudget.work`). Con la media móvil de ns por bloque, el tope pasa a ser
  `min(bloques, 8 ms / coste por bloque)`.
- **FPS**: mientras corre la obra, `DemoBuildRun` informa los FPS del cliente (`clientFps`). Por debajo
  de 30 FPS el presupuesto baja ×0,7 por segundo; por encima de 40 sube +10 %; entre los dos se queda
  quieto. Se aplica el mínimo entre los factores de MSPT y de FPS. Sin dato de FPS (no hay obra) se
  recupera solo.
- Son constantes en el código, sin claves nuevas en la config. Con `autoThrottle: false` se desactivan
  los dos.

### 3. Sin retroceder en fix/hq-fps
fix/hq-fps (aa19310) arregló la caída de FPS **después** de construir: backoff y tope de tiempo del A*,
culling y LOD de los BER, caché de pantallas. No tocó la obra. Lo que sí podría empeorar con más
concurrencia ya está acotado:
- **Actualizaciones de vecinos**: siguen apagadas (`UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`).
- **Sonidos y partículas**: siguen siendo 8 por tick entre todas las cuadrillas, aunque haya 4.
- **Re-render de secciones y luz**: crecen con los bloques por tick, que siguen con el mismo tope de 48
  (que ya se alcanzaba con un `build-demo` y un in-place a la vez). Además hay un tope en ms y la
  bajada por FPS.
- **Pathfinding**: los builders no usan el A* de los NPC. Durante la obra no hay NPCs en el HQ
  (ver punto 4), así que el A* no corre sobre una obra a medias.

### 4. Primero la estructura, después los NPCs (`client/npc/NpcGate`)
- **Obra completa** (primera vez, `rebuild`): `HOLD_ALL`. No aparece ningún NPC hasta que el HQ está
  terminado; los que hubiera desaparecen. Al terminar se instalan las anclas, se abre la compuerta y
  todos aparecen en su sitio.
- **Ampliación por cambio de roster** (diff silencioso): `HOLD_NEW`. Los que ya trabajan siguen; el
  nuevo no aparece hasta que su escritorio está construido.
- Al salir del mundo, al reiniciar o si la obra se corta, la compuerta se abre.
- `NpcRegistry` solo filtra a quién instancia (`NpcGate.shown`). **Dónde** aparece cada NPC (el spawn
  alcanzable, el camino hasta el escritorio) es cosa de fix/hq-npc-spawn: esta rama no toca la
  posición.
- Se eligió "todos al final" en vez de "cada uno cuando su ala está lista". Con la obra en paralelo, el
  final llega mucho antes, y el camino de la entrada al escritorio cruza el hub y los caminos, que
  pertenecen a otras partes.

### 5. Interfaz enchufable para el orden (`PlacementOrder`), para fix/hq-builders-ai
- `PlacementOrder` decide **qué entrada coloca después cada builder (frente)**: `peek(front)`,
  `advance(front)`, `done()`, `stage()`, `pendingInStage()`, `frontOf(i)`.
- El contrato está en el javadoc: cada índice una sola vez, nunca el mismo en dos frentes y el orden se
  mantiene en la misma posición.
- `FrontCursor` solo se ocupa de crédito y presupuesto, round-robin, frentes bloqueados, pausa y
  contadores. Con cualquier orden funciona igual.
- La implementación por defecto es `LayeredOrder`: el `Fronts` de siempre, con barrera por capa.
- Para usar otra, `PlacementOrders.use(factory)` una vez al arrancar: todo `BuildJob` nuevo la toma.
- `PlacementOrderContractTest.check(factory, seed)` es público, para que otro orden pase el mismo
  contrato.
- Qué partes corren a la vez (`PartSchedule`) y el presupuesto por tick (`BuildBudget`) siguen aparte.

## Benchmark (headless, sin lanzar MC)
`./gradlew hqBuildBench` (también corre dentro de `test`) escribe `mod/build/reports/hq-build-bench.md`.
Usa `HqBuildBenchTest` + `HqBuildSim`.

**Cómo se calcula:**
- **Entrada.** El plan real del mundo HQ de cada layout, con 20 proyectos (el roster de
  `HqLayoutsPlanTest`): `DemoHq.full(plan, lift 0)`.
- **Código real.** Se usa el código de verdad, tick a tick: `PlacementOrders.current()` + `FrontCursor`
  por parte (como `BuildJob`), `BuildBudget.allocate` con `BuildTuning.DEFAULTS` (como `BuildManager`) y
  `PartSchedule` + `DemoBuildRun.crews/frontRate` (como `DemoBuildRun`).
- **Mundo.** Superflat en memoria: suelo bajo y = 0 y aire encima. Una celda que ya está bien es un
  salto gratis, con el mismo tope por tick que `ServerLevelSink`.
- **Modelado, no simulado.** La caminata de la cuadrilla: `ceil(|14 − r| / 0,22)` ticks, con `r` la
  media de las semi-anchuras de la parte y un tope de 200 (`BuildCrew`). A eso se suma 1 tick para que
  el servidor tome el job. Una parte terminada libera su cuadrilla en el tick siguiente.
- **"Antes".** La misma simulación con la política de main: 1 cuadrilla y 2 bloques por tick por frente.
- **Tiempo simulado.** Ticks / 20 (a 20 TPS).
- **Bloques/tick.** Media sobre los ticks en los que se colocó algo, y máximo.
- **Frentes activos.** Frentes que aplicaron al menos una entrada en el tick: media sobre todos los
  ticks, y máximo.
- **Comprobaciones en cada caso:**
  - el mundo final es igual al plan aplicado en orden (0 celdas distintas);
  - se colocan los mismos bloques antes y después;
  - ningún tick pasa de `effectiveMax`;
  - nunca corren a la vez dos partes que se tocan.

| layout | partes | entradas | colocados | ticks antes | ticks después | tiempo antes | tiempo después | speed-up | bloques/tick antes (media/máx) | bloques/tick después (media/máx) | frentes antes (media/máx) | frentes después (media/máx) | partes a la vez (máx) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| classic | 43 | 73821 | 25765 | 4841 | 930 | 242 s | 47 s | 5.2× | 7.9 / 8 | 46.7 / 48 | 2.7 / 4 | 3.8 / 12 | 3 |
| campus | 74 | 275842 | 126206 | 20308 | 3295 | 1015 s | 165 s | 6.2× | 8.0 / 8 | 47.0 / 48 | 3.1 / 4 | 8.5 / 12 | 4 |
| ring | 55 | 350226 | 94686 | 14413 | 2912 | 721 s | 146 s | 4.9× | 8.0 / 8 | 46.5 / 48 | 3.4 / 4 | 6.4 / 12 | 4 |
| vertical | 47 | 240153 | 64983 | 9303 | 1470 | 465 s | 74 s | 6.3× | 7.9 / 8 | 45.7 / 48 | 3.6 / 4 | 10.6 / 12 | 4 |
| ensanche | 40 | 392256 | 84858 | 12256 | 2456 | 613 s | 123 s | 5.0× | 7.9 / 8 | 43.7 / 48 | 3.6 / 4 | 7.9 / 12 | 4 |
| village | 48 | 515529 | 196800 | 26042 | 4496 | 1302 s | 225 s | 5.8× | 8.0 / 8 | 45.5 / 48 | 3.9 / 4 | 10.7 / 12 | 4 |

**Objetivo de campus: ≥ 5× menos ticks** (`HqBuildBenchTest.CAMPUS_SPEEDUP`; se mide 6,2×).
- Después, la obra va pegada al presupuesto: 47 de 48 bloques por tick de media. El mínimo teórico con
  48 por tick es 126 206 / 48 ≈ 2 630 ticks, y se tarda 3 295. Lo que falta hasta ahí son las caminatas
  y las dependencias de los caminos, que cruzan todo.
- Un objetivo más alto exigiría subir `build.maxBlocksPerTick`, que es config y queda a criterio de
  Facundo. Con 5× queda margen sobre lo medido, por si cambian los planes de los layouts.
- Ring y ensanche ganan algo menos (~5×). Ring tiene partes enormes (suelo del anillo, 54 456 y 26 304
  entradas) que tocan todo y se construyen solas. Ensanche tiene muchas manzanas pegadas entre sí.
- En juego, el presupuesto real puede ser menor si el servidor o el cliente van justos (tope en ms,
  MSPT, FPS): en ese caso la obra cede, como debe.

## Tests
- `PartScheduleTest`:
  - quién espera a quién;
  - 200 rondas aleatorias en las que cualquier intercalado da el resultado secuencial y las partes en
    curso nunca se tocan;
  - planes vacíos.
- `PlacementOrderContractTest`:
  - el contrato de `LayeredOrder` (reutilizable);
  - la barrera por capa;
  - los jobs usan el orden instalado.
- `BuildBudgetTest` (nuevos):
  - el tope en ms limita los bloques y se recupera;
  - los FPS bajos reducen el presupuesto, que se recupera, con banda muerta.
- `HqBuildBenchTest`:
  - la tabla de los 6 layouts: plan idéntico, presupuesto, no solapamiento y más rápido;
  - campus ≥ 5×;
  - salir a mitad de la obra y volver termina el mismo mundo, saltando lo hecho.
- `NpcGateTest`: `HOLD_ALL` / `HOLD_NEW` / `OPEN` y el modo que elige el demo.

## Pendientes
- Sin probar en juego (no se lanzó Minecraft): falta ver 4 cuadrillas a la vez y el ajuste por FPS en una
  máquina real.
- El HQ de supervivencia (site + `CrewBridge`) sigue haciendo una ampliación a la vez, por el journal
  del site (ver docs/features/parallel-build.md). Allí solo se construye classic.
