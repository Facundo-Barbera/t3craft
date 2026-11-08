# Investigación: IA de los builders (asignación, orden de colocación y movimiento)

Pedido de Facundo: "Mejorar el algoritmo de movimiento y construcción de los builders para que sea más realista.
Actualmente solo ponen bloques en orden de capa y no respetan la proximidad de los builders. Busquemos algún
algoritmo que ya haya hecho esto."

## Punto de partida (main, 4ba5183)

- `Layers`: ordena el plan por `y`, después por `z`, serpenteando en `x`.
- `Fronts` y `FrontCursor`: cada capa (segmento con la misma `y`) se corta en N franjas contiguas, una por builder,
  con una **barrera por capa**. Ningún frente empieza la capa `y+1` hasta que todos terminaron la `y`.
- `BuildCrew` (cliente): al llegar el evento `placed`, el builder de ese frente va en línea recta (`noPhysics`) a un
  punto **fuera** de la huella, a la altura `y-2`, y mueve el brazo. El servidor coloca sin esperar a que llegue.

Problemas que se ven en juego:
1. El orden es global por capa. En un edificio de 60×40, una franja de la capa recorre todo el ancho, y la capa
   siguiente vuelve al principio. El builder cruza la obra entera en cada capa.
2. El builder se queda fuera del edificio y "coloca" a 20 o 30 bloques de distancia. El bloque aparece antes de
   que llegue.
3. No hay noción de soporte. Como la capa es global, una antorcha o un farol se colocan en cuanto llega su `y`,
   aunque la pared o el techo en los que se apoyan sean de otra capa (el techo de un farol colgante está arriba).
4. Los builders no usan el A* de los NPC: atraviesan paredes.

## Algoritmos existentes

### Baritone: `BuilderProcess` (LGPL-3.0)
- Construye schematics comparando el mundo con el esquema. En cada tick calcula el conjunto de posiciones
  "incorrectas" cerca del jugador.
- **Alcanzabilidad:** solo intenta colocar lo que está a distancia de alcance desde donde está el jugador (raytrace
  a una cara). Si no hay nada al alcance, arma un `GoalComposite` con metas `GoalPlace`/`GoalAdjacent` para cada
  posición colocable y deja que su A* (`PathingBehavior`) elija la más barata. Así el recorrido lo decide el coste
  del camino y no el orden del plan.
- **Soporte:** un bloque solo es "colocable" si tiene una cara vecina sólida contra la que hacer click (como un
  jugador). Lo que no tiene soporte espera a que lo tenga. Primero rompe lo que sobra y después coloca.
- Opciones `buildInLayers`, `layerOrder` y `layerHeight`: construir por capas es **opcional** y por defecto no se
  usa. El orden natural es "lo colocable más cercano".
- Usa andamios (`buildIgnoreBlocks` y bloques de relleno o *throwaway*) para alcanzar zonas altas.
- **Licencia:** LGPL-3.0. No se copia código, solo se reimplementa la idea "colocable = con soporte y al alcance; si
  no hay nada, caminar al más barato".

### MineColonies: IA del builder y Structurize (GPL-3.0)
- `AbstractEntityAIStructure` y el `StructurePlacer`/`BlueprintIterator` de Structurize recorren el blueprint en
  **etapas**: `CLEAR` (limpiar), `BUILD_SOLID` (bloques sólidos), `CLEAR_WATER`, `CLEAR_NON_SOLIDS`, `DECORATE` (lo
  no sólido: antorchas, faroles, puertas, carteles, alfombras) y `SPAWN` (entidades). Lo colgante y la decoración
  van siempre **después** de lo sólido.
- Dentro de una etapa itera por capas (`y`, `z`, `x`).
- Movimiento: el builder camina a un "proxy" (puntos alrededor del edificio, `getProxy`/`walkToProxy`) y trabaja
  dentro de un radio desde ahí. Es la misma idea que el `perimeterSpot` actual y tiene el mismo defecto: alcance
  irreal y recorrido por capa global.
- Un solo builder por obra (los ayudantes no reparten la zona).
- **Licencia:** GPL-3.0 (MineColonies y Structurize). Incompatible con copiar código aquí. Se toma solo la idea de las
  **etapas** (sólidos → decoración).

### Litematica: printer (LGPL-3.0, forks como `litematica-printer`)
- En cada tick coloca los bloques del esquema que están **dentro del alcance** del jugador, ordenados por distancia,
  con un tope de bloques por tick.
- Antes de colocar comprueba que haya una cara de apoyo (*placeable side*) y orienta al jugador para que el estado
  resultante (facing, mitad de losa) sea el del esquema.
- No planifica movimiento: el jugador camina y el printer rellena lo que tiene cerca. Es justamente el
  comportamiento local que queremos cuando el builder está quieto.
- **Licencia:** LGPL-3.0 (forks variados). Solo la idea.

### Create: schematicannon (MIT)
- Dispara bloques desde un punto fijo, con alcance configurable. Recorre el schematic por capas.
- Los bloques que **dependen de otro** (antorchas, carteles, puertas, lo que `BlockMovementChecks` considera
  pegado a una cara) se **aplazan** a una pasada posterior, y las entidades van al final. Es la misma regla de
  etapas de MineColonies, aplicada con una comprobación de "pegado".
- **Licencia:** MIT. Se podría reutilizar, pero no hace falta: la idea es sencilla y la reimplementamos.

### GDMC (Generative Design in Minecraft) y GDPC
- Los asentamientos del concurso (y librerías como GDPC, MIT) generan **parcelas** y **distritos** con clustering
  (k-means o Voronoi sobre el terreno, agrupando por altura y bioma) y colocan los edificios en lote por HTTP, al
  instante. No animan la obra.
- Lo útil aquí es la partición del terreno en zonas compactas con Voronoi o k-means, que aplicamos a la huella
  de la obra para repartirla entre builders.

### Asignación de tareas multiagente
- **Partición por zonas:** k-means o Voronoi con un centro por agente. Con **capacidad** (k-means balanceado) las
  zonas tienen tamaños parecidos. Barato (pocas iteraciones de Lloyd) y da áreas compactas, así que hay poco
  recorrido dentro de la zona.
- **Orden dentro de la zona:** TSP greedy, es decir, vecino más cercano. Con precedencias (soporte) es un *TSP con
  restricciones de precedencia*; el greedy "el elegible más cercano" es la heurística estándar, con un factor de
  aproximación malo en teoría pero muy buen resultado en rejillas densas.
- **Hungarian** (Kuhn-Munkres, O(n³)): asignación óptima uno a uno de agentes a tareas. Sirve para asignar los
  **cúmulos iniciales** a builders, pero no para miles de bloques.
- **Subasta** (Bertsekas) o **CBBA** (consensus-based bundle algorithm): cada agente puja por tareas según su coste
  marginal. La versión mínima es *work stealing*: el que se queda sin trabajo puja por el bloque elegible más
  cercano de otra zona y lo gana si su coste es menor y no invade al dueño.
- **Reservas:** tablas de reserva de *Cooperative A\** (WHCA\*, Silver 2005) para celdas en el tiempo, y reserva
  exclusiva de tareas. Lo más simple que evita peleas es que **cada bloque tenga un único dueño en cada momento**
  y que el **punto donde se para cada builder** guarde una separación mínima con los puntos de los demás.

### Licencias, resumen

| Fuente | Licencia | ¿Copiar código? | Qué tomamos |
|---|---|---|---|
| Baritone | LGPL-3.0 | No | colocable = soporte + alcance; caminar al punto más barato |
| MineColonies / Structurize | GPL-3.0 | No | etapas: sólidos, decoración |
| Litematica printer | LGPL-3.0 | No | colocar lo que está al alcance, por distancia |
| Create schematicannon | MIT | Podría, no hace falta | aplazar lo pegado a su soporte |
| GDMC / GDPC | varias / MIT | No hace falta | zonas con Voronoi o k-means |
| Hungarian, subastas, WHCA\* | algoritmos publicados | — | asignación, *work stealing*, reservas |

Todo lo que sigue es una reimplementación propia.

## Algoritmo elegido: "zonas + elegible más cercano con soporte", simulado de antemano

**Planificador determinista** (`dev.t3craft.build.CrewPlanner`, Java puro y testeado). Al crear el job simula a la
cuadrilla sobre el plan y produce un **horario**: para cada builder, la secuencia de bloques, el punto donde se para
para cada uno y los ticks que camina antes. El servidor sigue ese horario dentro del presupuesto por tick, y el
cliente anima a cada builder hacia su punto con el A* de los NPC.

1. **Zonas (k-means balanceado).** Las columnas `(x, z)` del plan se reparten entre los builders con unas
   iteraciones de Lloyd, con capacidad máxima `⌈n/k⌉·1,15` por zona. Los centros iniciales salen de los puntos de
   llegada de cada builder, repartidos por el perímetro y empezando por el frente. Así cada builder tiene un área
   compacta y propia (una celda de Voronoi con capacidad).
2. **Elegibilidad (soporte y etapas).** Una entrada es elegible si:
   - la entrada anterior en la **misma posición** ya se aplicó (limpiar con aire y después poner, o el orden de
     remociones del diff);
   - tiene **soporte**: está en la capa más baja del plan (apoya en el terreno o el pad) o tiene un vecino de cara
     ya colocado (no aire);
   - si es **decoración** (no sólida: antorcha, farol, alfombra, puerta, cartel, cristal fino, etc.), todos sus
     vecinos de cara **sólidos** del plan ya están puestos. Es la etapa DECORATE de Structurize, pero local y no
     global.

   El aire (limpiar) no necesita soporte. Lo que el plan deja flotando (sin ningún vecino sólido en el plan
   final) se coloca **al final**, en un paso marcado como *forzado*, para no bloquear la obra.
3. **Orden local.** El builder elige, entre los elegibles de su zona, el de menor coste
   `distancia(ojos, bloque) + 2·(y − y_mínima elegible de su zona)`. La penalización vertical da "de abajo arriba en
   lo local" sin barrera global.
   - Si está al **alcance** (4,5 bloques desde los ojos, el alcance de supervivencia), lo coloca sin moverse.
   - Si no, busca el **punto de parada** más cercano a él desde el que alcanza el bloque. Un punto de parada es una
     celda libre (pies y cabeza) apoyada en algo ya colocado o en el terreno, o un andamio virtual (el avatar
     flota, sin bloques extra) si no hay otra. Camina hasta él: los ticks de camino son
     `⌈distancia octil / 0,3⌉`.
4. **Sin peleas.**
   - Cada entrada tiene **un solo dueño** cuando se elige: solo se toma de la propia zona o se roba de otra, en
     cuyo caso sale de ella.
   - Un punto de parada nunca está a menos de 2 bloques del punto actual o del destino de otro builder (reserva de
     celdas). Los tests lo comprueban sobre el horario.
5. **Reequilibrio (subasta mínima o *work stealing*).** Si un builder no tiene nada elegible en su zona, puja por
   el elegible más cercano de las demás zonas. Lo gana si está a más de 3 bloques del builder dueño, para no
   meterse en su sitio. Así el que termina antes ayuda en vez de esperar.
6. **Ejecución.** El horario son *carriles* (uno por builder) con dependencias. El servidor (`CrewCursor`) aplica la
   cabeza de cada carril cuando sus dependencias (soporte, misma posición y vecinos de la decoración) ya están
   aplicadas y cuando terminó de caminar. Todo dentro de la asignación del presupuesto global (`BuildBudget`), así
   que el rendimiento por tick no cambia.
   - Como el horario es un orden topológico de las dependencias, siempre hay un carril que puede avanzar: no hay
     interbloqueos.
   - Lo que ya está en el mundo (reanudar) se salta gratis y sin caminar.

### Por qué este y no otro
- **Hungarian bloque a bloque** es O(n³) con n de miles: inviable. Para k ≤ 4 builders, k-means balanceado ya da
  zonas casi óptimas en microsegundos.
- **Una subasta o CBBA completos** son para agentes que se comunican y replanifican. Aquí todo es determinista y
  centralizado, así que el robo de trabajo da el mismo efecto (nadie espera de brazos cruzados) sin el coste.
- **TSP exacto con precedencias** es NP-difícil. El greedy del elegible más cercano es lo que hacen el printer de
  Litematica y Baritone, y en rejillas densas queda cerca del óptimo.
- **Simular de antemano** en vez de decidir en vivo hace que el resultado sea **determinista y reanudable** (el
  mismo plan da el mismo horario) y que se pueda **testear** sin Minecraft: alcance, soporte, reservas y recorrido.
- **El presupuesto de H1 se respeta:** el planificador solo cambia **qué** entrada toca y **quién** la pone, no
  **cuántas** por tick.
- **El resultado final es idéntico** porque las entradas de una misma posición conservan su orden relativo, y
  la última de cada posición es la del plan.

### Métrica de recorrido
Ver `docs/feat-hq-builders-ai.md`: recorrido total de los builders con 20 proyectos, por layout, comparado con el
orden por capa más franjas de antes, con el mismo evaluador de alcance.

## Builders independientes y rápidos (fix/hq-builders-independent)

**Síntomas (Facundo).** (1) "Los builders son como dependientes entre sí": uno espera a que otro, a veces al otro
lado del mapa, ponga una pieza. (2) "Se ve mucho mejor, pero es mucho más lento" que el cursor por frentes de
fix/hq-build-parallel (a8d23dd).

**Causas.**
- *Head-of-line blocking.* `CrewCursor` ejecutaba carriles fijos: la cabeza solo se aplicaba con sus dependencias
  hechas. Los tiempos reales (presupuesto, caminatas, segmentos de materiales) no son los simulados, así que la
  cabeza esperaba a una dependencia del carril de otro builder y el builder se quedaba parado con trabajo listo.
- *Caminar bloquea colocar.* Cada paso fuera de alcance esperaba `⌈d / 0,3⌉` ticks completos (`WALK_PER_TICK`).
- *Una colocación por parada.* Tras caminar, el builder ponía la entrada siguiente del carril, aunque estuviera
  lejos y tuviera otras listas a mano.
- *Créditos perdidos.* Si todos caminaban, el crédito del `BuildBudget` de ese tick se tiraba (tope `max(1, a)`).

**Decisión: los carriles son un plan, no un contrato.** El planificador no cambia (zonas, orden, puntos de
parada). `CrewCursor` decide en vivo, de forma determinista (frentes y carriles en orden fijo), el siguiente
objetivo de cada builder libre:
1. **Ráfaga:** una entrada lista que alcanza **sin caminar**, entre las 48 siguientes de su carril, o en la
   ventana de un vecino que no la alcanza. En cada parada pone todo lo listo a su alrededor, varias por tick si hay
   crédito.
2. **Ventana propia:** la primera entrada lista de las 16 siguientes de su carril. Las saltadas se reintentan
   en la siguiente elección.
3. **Robar la dependencia que bloquea:** si la cabeza no está lista, busca (BFS acotada a 48 nodos) un ancestro
   listo de la cabeza que alcance antes o igual que su dueño, o a menos de ~6 bloques. Lo toma si le cuesta menos
   que la entrada de su ventana.
4. **Robar trabajo:** si no tiene nada, toma la entrada lista más cercana de la ventana de otro carril, siempre que
   llegue antes o igual que el dueño o que no sea una de las 4 siguientes del dueño.

Reclamar es exclusivo (`by[i]`): nadie pone dos veces lo mismo, y `frontOf` dice quién la reclamó. El punto de
parada es, entre los planificados de las ventanas (del suyo y del dueño de la entrada) que alcanzan la entrada,
el que **cubre más entradas pendientes**, así la siguiente parada sirve una ráfaga. Si empata, gana el más
cercano, y nunca se usa la celda de otro builder. El builder **trota** a 0,6 bloques por tick, **coloca en
cuanto el bloque entra en alcance** (antes de llegar) y nunca espera más de **20 ticks** por una caminata.
Si el cliente necesitaría más de 1,2 bloques por tick para llegar a tiempo, el avatar **salta** al punto
(`BuilderAvatar.leapTo`). El crédito de un builder que camina lo usa el que está listo: un solo builder listo
puede gastar todo el tick. El `Walker` avisa cada objetivo nuevo con el destino elegido (`BuildCrew` sigue el A*).

**Garantías.** El presupuesto global por tick no cambia (crédito = asignación de `BuildBudget`). Sin deadlock: la
entrada pendiente de menor rango del segmento está lista y es la cabeza del carril de su dueño (o ya la reclamó
otro, que la pondrá), y lo forzado sigue al final del orden. Los materiales siguen igual: segmentos verificados al
empezar, `BLOCKED` pausa solo ese frente (que conserva su objetivo) y la obra se pausa cuando ningún otro puede
seguir. Al reanudar, lo que ya está bien en el mundo se salta sin caminar. El resultado final es idéntico: las
entradas de una misma posición siguen encadenadas por dependencias.

**Antes y después (estimación a mano, classic con 20 proyectos, 4 grupos en paralelo × 4 builders, presupuesto
48/tick por grupo).** Son 73 821 entradas y 6 876 bloques de recorrido (tabla de `docs/feat-hq-builders-ai.md`).
- *Frentes por capa (a8d23dd):* no caminan y les limita el presupuesto: 73 821 / (4 × 48) ≈ **385 ticks**.
- *Crew antes:* 6 876 / 0,3 ≈ 22 900 ticks-builder caminando ÷ 16 builders ≈ 1 430 ticks por builder, sin
  solaparse con colocar (el crédito se perdía). A eso se suman las esperas entre carriles: unos **1 500–1 800 ticks,
  4–5× más lento**.
- *Crew ahora:* caminar a 0,6 bloques por tick lo divide entre 2. Colocar al entrar en alcance ahorra ~2 de los ~5
  bloques de una caminata media, otro ×1,6. Las paradas que cubren más reducen las caminatas en ~20 %, un ×1,25.
  Queda 1 430 / (2 × 1,6 × 1,25) ≈ **360 ticks** de caminata por builder, y el robo y la ventana evitan las
  esperas. Como el crédito pasa a quien está listo, caminar y colocar se solapan: total ≈ max(385, 360) más un
  margen ≈ **420–500 ticks, 1,1–1,3× los frentes**. El tope de 20 ticks por caminata acota el peor caso, porque
  una entrada nunca cuesta más que eso.

No se añadieron builders por tamaño de plan: con el presupuesto global como límite, más builders solo reparten el
mismo crédito. `frontsPerJob` sigue siendo la palanca. Velocidades y ventanas son constantes de `CrewCursor`
(`RUN_PER_TICK`, `MAX_WALK_TICKS`, `WINDOW`, `BURST_WINDOW`), sin claves de configuración nuevas.
