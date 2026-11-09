# Layout «village»: aldea orgánica en terrazas

> Las imágenes de esta investigación (PNG de renders) se quitaron del árbol el 2026-11-09 para aligerar los worktrees. Siguen en el tag `archive/research-renders-2026-11-09`: `git show archive/research-renders-2026-11-09:<ruta>.png > /tmp/x.png`.

Rama `layout/village`. El HQ crece como un pueblo de colina. En lo alto está la plaza mayor con el
ayuntamiento (el hub). Desde ella bajan calles que se ramifican y se vuelven a cerrar en bucles, hay
terrazas a distintas alturas unidas por escalinatas, y cada proyecto ocupa un caserío en su propio
solar. Aquí no hay espina ni pasillo que se alargue: el pueblo crece por solares en todas las
direcciones a la vez.

![4 → 10 → 20 proyectos](village_compare_4_10_20.png)

## Idea del layout

- **Rejilla de celdas de 21×21** alrededor del origen. Cada celda construida es un módulo y un grupo
  de construcción independiente: la columna completa desde `y = 0` (el suelo natural) hasta el tejado.
- **Hub = cima (nivel 4, `y = 12`)**: son 3×3 celdas en una sola terraza. En el centro, el
  ayuntamiento con la pared kanban (`thread_board`) al oeste, la pantalla grande al este, el tablero
  «Needs attention» al norte, los bancos, la columna libre del holograma, el faro sobre ella y una
  linterna-cúpula en el tejado. En las cuatro celdas de borde está la plaza, con la fuente y el
  spawn al sur. Las cuatro esquinas son los edificios singulares.
- **Calles**: se generan una sola vez con una semilla fija (`VillageTown.SEED`), sin depender del
  roster. Salen cuatro semillas, una por lado de la plaza, siempre un escalón por debajo de ella, de
  modo que la plaza queda como una acrópolis. En cada ronda, todas las puntas avanzan una celda
  (60 % recto, 40 % giro) y pueden ramificarse (30 %). Al salir hacia fuera, una calle baja una
  terraza con un 50 % de probabilidad. Si una punta llega a una calle de otra rama, o a una celda
  vecina de ella, **se enlaza y cierra un bucle**, siempre que la diferencia sea de una terraza como
  máximo. Así se forma una red con anillos, no un árbol. Donde se cruzan tres o más calles construidas
  aparece una **plazuela** con fuente y faroles, que además es un punto de paseo (`walk_square_*`).
- **Dentro de una celda de calle**: carriles de 5 de ancho trazados con curvas Bézier desde un centro
  desplazado (con jitter) hasta el centro de cada borde abierto. Fuera de los carriles hay jardín,
  árboles, setos, flores y faroles junto al camino.
- **Terrazas**: escalón de 3 bloques. Cada celda construida levanta su cuerpo de terraza (muro de
  contención en el borde, con hiladas alternas, y relleno). Donde un borde abierto baja una terraza,
  la celda alta talla una **escalinata de 3 peldaños, 5 de ancho**, con mejillas de piedra y faroles.
  El lado alto de cada escalón mira hacia arriba, así que el A* de los NPC la trata como peldaños
  ("landing") y no como saltos. Los bordes cerrados hacia una celda más baja llevan pretil.
- **Distritos** según el sector de la brújula. Cada uno cambia los muros, los postes, el tejado, el
  camino, el seto, el árbol y el muro de contención:
  | distrito | muros | tejado | camino | árbol/seto |
  |---|---|---|---|---|
  | norte «piedra» | stone bricks | pizarra (deepslate tiles) | adoquín y grava | abeto |
  | este «adobe» | mud bricks, anexos de azotea almenada | teja de ladrillo | arenisca | acacia y azalea |
  | sur «entramado» | calcita entre roble oscuro | roble oscuro | grava y andesita | roble |
  | oeste «ladrillo» | ladrillo y toba | cerezo | granito pulido | cerezo |

## Tipos de cuarto (edificios)

| cuarto (datos del roster) | edificio | dónde | capacidad |
|---|---|---|---|
| trabajo por proyecto | **caserío**: casa principal (6 escritorios), anexo B (4) y anexo C (4) | un solar por caserío | 14 por caserío; con más hilos, un segundo caserío en otro solar (`#2`) |
| coordinación (hub) | **ayuntamiento** | cima, centro | tablero, kanban, pantalla, bancos |
| revisión / PR | **lonja**: soportales abiertos con dos filas de escritorios y el paso al norte por el medio | plaza norte | 8 |
| tareas programadas | **molino-torre** con aspas, techo a cuatro aguas | esquina NE | 3 |
| errores / needs attention | **puesto de guardia** almenado con torreta | esquina SE | 3 |
| lounge | **taberna**: barra con barriles y mesas; los hilos están de pie en la barra (sin escritorio) | esquina NO | 8 |
| archivo | **ermita-biblioteca** con espadaña y campana. Dentro, una sola `archive_terminal` (el atril, que el módulo blocks sustituye) | esquina SO | todos los hilos archivados |

Los edificios cambian según el número de hilos. El anexo B aparece cuando se llena la casa principal
y el anexo C cuando se llena el B. La casa principal crece de 5 a 7 bloques de muro, suma chimenea a
partir de 5 hilos y torre con tejado a cuatro aguas a partir de 10. La forma del tejado (cumbrera a lo
largo o a lo ancho, a cuatro aguas, azotea) y el espejo del caserío dependen de la variante del solar.

Todos los escritorios siguen lo acordado: una sola **pantalla 3×2** (`Workstation.screenOriginFor` /
`screenCells`), **sin lámparas**, y el NPC de pie delante sin silla. Los fixtures (monitores, lámparas
del tablero, faro, terminal, pared kanban y carteles → placas) los pone `HqBlockFixtures` sin cambios,
porque se guían por las anclas y las workstations del plan.

Clasificar un hilo en un cuarto es responsabilidad del roster (`VillageLayout.Input.rooms`). Las
reglas reales serían las de `RoomRules` de feat/hq-spacious-rooms (error → guardia, espera de PR →
lonja, programado → molino, idle → taberna). Si un edificio singular se llena, los hilos que sobran
vuelven a su caserío.

## Reglas de crecimiento

1. **Orden fijo de solares.** Un solar es toda celda libre vecina de una calle. Mira a la calle más
   antigua y está en su misma terraza. Los solares se ordenan por la ronda de su calle (anillos
   interiores primero) y, dentro de cada ronda, por muestreo del más lejano sobre el ángulo
   de brújula (cada solar es el más alejado en ángulo de los ya ordenados). Por eso los solares
   consecutivos caen en direcciones distintas y el frente avanza en
   varias direcciones a la vez (un test lo comprueba).
2. **Un proyecto nuevo** se asienta en el solar libre de **rango más bajo**. Los solares interiores
   que hayan quedado libres se rellenan **antes** de expandir.
3. **Estable**: un caserío conserva su solar mientras tenga algún escritorio ocupado. Cada hilo
   conserva su escritorio. Sumar un proyecto nunca cambia otra celda de solar, ni un solo bloque (test
   `existingBuildingsNeverMoveWhenTheTownGrows`). Lo único que cambia son las calles nuevas.
4. **Liberar**: un proyecto que desaparece deja su solar libre, y el siguiente proyecto nuevo lo
   reutiliza (test).
5. **Calles construidas** = las que hay en el camino de cada solar ocupado hasta la plaza, más los
   bucles cuyos dos extremos ya están construidos. Los solares libres que dan a una calle ya construida
   se ven como **huertos** (trigo, frutales, seto), así que no quedan agujeros entre terrazas.
6. **De 10 a 20 proyectos**: los primeros solares rodean las cuatro calles semilla, en la terraza
   justo debajo de la plaza, repartidos alternando este, oeste, sur y norte. Con 10 proyectos ya está
   ocupado el primer anillo y empiezan las ramas. Entre 10 y 20 el frente baja a la segunda y tercera
   ronda: al oeste el barrio de cerezo cierra un bucle (dos calles paralelas unidas) con una fila de
   caseríos; al sur, una calle de grava se ramifica en dos y baja en escalinatas; al norte, otra rama
   sube hacia un caserío de pizarra; al este, la calle de arenisca baja terraza a terraza y alarga un
   callejón hasta el solar más lejano. Un proyecto de 15 hilos ocupa dos solares (21 caseríos con 20
   proyectos). Las plazuelas con fuente aparecen cuando una celda reúne tres calles construidas. El
   plano fijo las tiene, pero con este roster de 20 solo se activan algunas al seguir creciendo.

Determinismo: `VillageLayout.allocate(in, allocate(in, null)) == allocate(in, null)`, el orden del
roster no influye y el plan tiene la misma huella SHA‑256 (test).

## Tamaños

Envolvente de lo que se construye (bloques, `y` de 0 a 36; la cima está en `y = 12`):

| proyectos | caseríos | colocaciones | envolvente x × z |
|---|---|---|---|
| 4 | 4 | ~232 k | 147 × 105 (x −73..73, z −52..52) |
| 10 | 11 | ~354 k | 147 × 126 (x −73..73, z −52..73) |
| 20 | 21 | ~538 k | 273 × 210 (x −115..157, z −94..115) |

Pasa de 64, así que el límite actual de `site` no alcanza. Para este estilo, **`VillageFootprints`**
da `cell(i,j)`, `base()` (la cima de 3×3 celdas), `areas(layout)` (grupo → celda, sin solapes) y
`envelope(n)`, que reúne la cima, los n primeros solares, sus calles y sus huertos. Así reservaría
terreno el modo survival: al crear el sitio reserva `envelope(n)` para el máximo previsto. Cada grupo
es exactamente una celda, de modo que los builders pueden reclamar, limpiar y construir celdas por
separado y en paralelo. Crecer por encima de n exige ampliar la reserva, pero nunca mover nada.
Esto está documentado y todavía no se ha conectado en `site/builders`.

## Arquitectura (aditiva)

- `VillageTown`: el plano fijo del pueblo (calles, niveles y orden de solares).
- `VillageLayout`: la asignación estable (caseríos, escritorios, cuartos singulares y archivo).
- `VillageGenerator`: genera el `BuildPlan` con el mismo `PlanBuilder` (celdas sin colisiones, todas
  listadas), las mismas anclas (`spawn`, `entrance`, `hub`, `board*`, `bench_*`, `walk_hub_*`,
  `thread_wall*`, `hologram`, `beacon`, `archiveTerminal`, `archive`, `module@<solar>`,
  `wing_<proyecto>`, `walk_<proyecto>`, `desk_*`, `monitor_*`, `bigscreen_hub…`) y workstations.
- `VillageFootprints`: las áreas para site/builders. `VillageDemo`: los rosters del render y de los tests.
- Usa los bloques del kit (`HqKit`: escritorio, suelo, suelo de acento, paneles luminosos del techo,
  faroles, faroles de piedra, maceteros), con fallback vainilla automático.
- **Cherry y classic no cambian**: no se ha tocado `HqGeometry`, `HqLayout`, `HqGenerator`,
  `HqStyles` ni ningún estilo, y sus tests siguen igual. La aldea **no** está registrada en
  `HqStyles`, para no alterar la lista de estilos. Se usa desde `VillageGenerator.plan(...)` y desde
  hqRender (`--style village`). Para conectarla al runtime faltaría que `HqStore` guarde el
  `VillageLayout` y que `HqPlans` elija el generador según la config.

## Tests (`VillageTest`, 16 casos)

- la red cierra bucles (≥3 ciclos), tiene ≥3 terrazas y nunca enlaza calles con más de un escalón de
  diferencia;
- los solares consecutivos se reparten por la brújula y los anillos interiores van primero;
- determinismo, idempotencia e independencia del orden del roster;
- estabilidad 4 → 10 → 20: mismos caseríos, mismas workstations y plots idénticos bloque a bloque;
- liberar un solar y rellenarlo antes de crecer;
- annexes, segundo caserío y edificios singulares ocupados;
- **anclas libres, con suelo y alcanzables desde el spawn** con el validador de anclas
  (`HqAnchorPlacementTest.assertAnchorsValid`, que usa la `Collision` real de los NPC) con 4, 10 y 20
  proyectos;
- todo queda dentro de su celda y de la envolvente, y las áreas no se solapan;
- pantallas 3×2 sin lámparas: ninguna bombilla fuera del tablero y columna del holograma libre.

## Renders

`./gradlew hqRender -PhqArgs="--style village --out ../docs/research/hq-layouts/village"`. El render
hace crecer el mismo pueblo 4 → 10 → 20, asignando cada paso contra el anterior, con el módulo blocks
activado. Los PNG de más de 480 KB están guardados con 256 colores.

| vista | 4 proyectos | 10 proyectos | 20 proyectos |
|---|---|---|---|
| aérea isométrica (SE) | [4](village_4_aerial.png) | [10](village_10_aerial.png) | [20](village_20_aerial.png) |
| aérea desde el NO | [4](village_4_aerial_nw.png) | [10](village_10_aerial_nw.png) | [20](village_20_aerial_nw.png) |
| planta cortada 3 bloques sobre cada terraza | [4](village_4_plan.png) | [10](village_10_plan.png) | [20](village_20_plan.png) |
| interior del hub (ayuntamiento) | [4](village_4_hub.png) | [10](village_10_hub.png) | [20](village_20_hub.png) |
| caserío más grande, cortado a la altura de los escritorios | [4](village_4_hamlet.png) | [10](village_10_hamlet.png) | [20](village_20_hamlet.png) |
| caserío más grande, exterior | [4](village_4_hamlet_out.png) | [10](village_10_hamlet_out.png) | [20](village_20_hamlet_out.png) |
| especiales norte: taberna, lonja y molino | [4](village_4_singular_north.png) | [10](village_10_singular_north.png) | [20](village_20_singular_north.png) |
| especiales sur: biblioteca y guardia | [4](village_4_singular_south.png) | [10](village_10_singular_south.png) | [20](village_20_singular_south.png) |
| comparativa con la misma cámara y escala | [4 → 10 → 20](village_compare_4_10_20.png) | | |

## Límites conocidos

- Las celdas de calle son amplias (21×21) y a veces quedan muy verdes. Hay sitio para puestos de
  mercado o bancos si se quiere más densidad.
- La comparativa ocupa 621 KB aun con 256 colores (son tres vistas de 1500 px).
- Algún solar del orden fijo queda lejos (el callejón del este con 20 proyectos), porque las calles
  semilla del plano fijo tienen pocas parcelas en sus primeras rondas.
- La estabilidad es por solar. Si un caserío cruza un umbral de hilos (anexo, chimenea, torre), se
  reconstruye esa celda sin moverse de sitio.
