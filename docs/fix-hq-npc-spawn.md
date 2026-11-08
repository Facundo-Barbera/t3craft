# fix/hq-npc-spawn — NPCs bajo la sala principal sin camino a sus escritorios

## Síntoma

En el mundo HQ con el layout **ring** (y, como se vio después, también campus, vertical, ensanche y
village), los agentes aparecían debajo o dentro del edificio (en el zócalo del claustro, junto al
podio del hub) y no llegaban nunca a sus escritorios.

## Causas raíz (dos, independientes)

### 1. El punto de vista del demo usaba la geometría de classic

`DemoPlacement.viewpoint(spec)` coloca al jugador (`tp` del primer build y `setworldspawn`) 10
bloques delante del pórtico de **classic** (`HqPlans.demoSite()` pone el frente del pórtico de
classic en z = 0). Los NPC nacen en la rejilla provisional (`GridLayout`) **alrededor del jugador**,
y eso pasa antes de que el HQ esté en pie. Los layouts con planta propia se extienden mucho más allá
de esa línea: en ring ese punto cae en el anillo del claustro (r 21–28), a nivel del patio y bajo la
galería, sin suelo donde pisar (`Collision.floor` = NaN). Después el HQ se construye alrededor de los
NPC. Los que quedan en celdas cerradas no tienen ruta (`Route.NONE`), y `NpcBrain` nunca teletransporta:
se quedaban ahí para siempre. Classic también tenía un hueco de la rejilla dentro del hub, pero
menos grave.

Arreglo: `DemoPlacement.viewpoint(spec, worldMinY)` usa, para los layouts con planta propia, el
ancla `spawn` del plan del layout activo en el sitio del demo (una celda libre y validada). Classic
conserva su punto de vista exacto.

### 2. El A* de los NPC era más corto que el validador (el agujero del validador)

`HqAnchorPlacementTest.assertAnchorsValid` / `PlanWalkability.reachable` hacen un **relleno BFS sin
límites** desde el spawn. El A* real (`GridPathfinder`) buscaba solo dentro de la caja que envuelve
los dos extremos **+16 bloques** (`PAD`) y como mucho 16 000 celdas. En los pisos superiores de los
sectores de ring, ensanche y vertical, y en parcelas lejanas de campus y village, el único camino
pasa por la escalera del fondo del edificio o rodea la manzana, a más de 16 bloques de la caja.
Ejemplo: en ensanche, con 4 proyectos, el BFS encuentra un camino que llega a z = 86 cuando la caja
acababa en z = 77. El A* agotaba el conjunto abierto y respondía NONE, incluso pasando por la vía
(la entrada del hub). El validador daba esos escritorios por alcanzables y no lo eran.

Arreglo: cuando el conjunto abierto se vacía y algún movimiento quedó recortado por la caja, la
búsqueda la amplía (×3: 16 → 48 → 144) y sigue desde los nodos recortados (`GridPathfinder.grow`).
`MAX_NODES` sube a 48 000 para las rutas largas, como las de la torre de vertical con 20 proyectos y
las parcelas de campus a más de 150 bloques. El presupuesto por tick de `Navigation` (2500 nodos y
2 ms) no cambia: una búsqueda larga se reparte entre más ticks.

**`HopViaReplanTest.longRoutesGoThroughTheEntrance`.** Este test daba por hecho que una abertura a
40 bloques quedaba fuera de "una búsqueda acotada" y exigía NONE en la ruta directa antes de probar
la vía. Esa premisa era un detalle del fixture (el tamaño de la caja de entonces), no el
comportamiento que se quiere proteger, que es este: cuando ninguna búsqueda acotada alcanza el
destino, la ruta entra por la vía (la entrada de la base). Con la caja creciente, la búsqueda
directa ya encuentra una abertura a 40 bloques, y eso es justo el arreglo. El test conserva su
intención con la abertura más allá de la caja máxima (`MAX_PAD + 16`): ahí la ruta directa sigue
siendo NONE y la vía mete al NPC. Un test nuevo, `theSearchBoxGrowsToAnOpeningPastThePad`, fija el
caso de los 40 bloques como ruta directa. Como esa ruta por la entrada mide unos 330 bloques, el tope fijo de 2000
ticks de `NavigationTest.walk` (unos 290 bloques a `Motion.SPEED`) la cortaba antes del final.
Ahora el tope es el doble del tiempo que la ruta necesita a paso normal, con un mínimo de 2000.

## Red de seguridad: recolocación

`NpcBrain.stranded()` se activa tras 6 respuestas "sin ruta" seguidas (unos 12 s; los empujoncitos no
cortan la racha) o tras 8 puntos de deambular inalcanzables seguidos.
`BehaviorDirector.rescue` pone entonces al NPC en `AnchorSource.spawn()`: el ancla `spawn` del HQ,
desde la que todo es alcanzable, y solo cuando la base está en pie. El salto se hace de golpe
(`teleported`, el avatar hace snap) y el NPC vuelve a planificar desde allí. Si ya está a menos de
3 bloques del spawn no se mueve, así que un destino imposible no provoca saltos en bucle.

## Tests

`DemoHqReachabilityTest`, con `DemoHqWorld`: el HQ tal como lo construye el demo, en memoria
(superflat, lift, plinto y grupos horneados con `DemoHq.full`):

- 6 layouts × 4/10/20 proyectos: el spawn es pisable (suelo, pie y cabeza libres) y el punto de
  vista no queda dentro del HQ. Desde el spawn, la `Navigation` real (A*, presupuesto, caja y vía)
  encuentra ruta a cada escritorio y a cada ancla de pie (hub, bancos, tablero, lounge, puertas y
  paseos), y el NPC la recorre hasta el final.
- `theOldViewpointWasInsideTheBuilding`: reproduce la causa 1; classic conserva su punto de vista.
- `npcsLeftOnTheGridInsideTheRingAreRescuedAndReachTheirDesks`: NPC en la rejilla vieja dentro del
  ring; algunos sin ruta, todos rescatados y en su escritorio.
- `aStrandedNpcAlreadyAtTheWayInStaysPut`.

Antes del arreglo del A*, 10 de los 18 casos fallaban: todos los layouts menos classic tenían
escritorios inalcanzables.

## Render

No se ha hecho. La nueva política del coordinador prohíbe ejecutar hqRender desde el hilo worker.
