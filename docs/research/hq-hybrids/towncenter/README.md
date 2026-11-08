# design/hybrid-towncenter: «Centro urbano con torres»

Layout `hybrid-towncenter` (en el menú HQ: «Centro urbano con torres»; en `config/t3craft.json`: `"hqLayout": "hybrid-towncenter"`). Es un layout **nuevo** del registro `HqLayouts`. Los demás layouts no cambian ni se tocan sus tests.

![Crecimiento 4 → 10 → 20](hybrid-towncenter_growth.png)

## Qué toma de cada diseño

| De | Qué | Dónde está |
|---|---|---|
| **ensanche** (layout/blocks) | La base: manzanas de 45 × 45 con patio ajardinado, calles de 10 con chaflanes, pasos de cebra y aceras, pasajes cubiertos de la calle al patio, cuatro parcelas en molinete por manzana, materiales por barrio, espiral de manzanas, mercado cubierto y plazuelas en el paseo diagonal | `EnsancheGrid` (se reutiliza tal cual), `TowncenterBuilder` (calles, patio, mercado, plazuelas y solares son las piezas del ensanche, sin linternas) |
| **gothic / steampunk / mesoamerican** | La escala del hub: un edificio cívico monumental en la plaza mayor, la **casa consistorial**, con planta de cruz de 33 × 33 (nave de 13 de ancho y transepto de 33), 14 de alto libre, y un **cimborrio** acristalado de 13 × 13 sobre el crucero que sube hasta y = 29 con la linterna y el faro | `hubCell`, `hall` |
| **vertical** | La verticalidad selectiva: torre con **atrio** (escalera en zigzag dentro de un vacío de dos carriles que atraviesa todas las plantas, bajo un lucernario), **skybridges** entre torres vecinas y el podio (la planta baja de la manzana, con azoteas ajardinadas) | `tower`, `towerRoom`, `bridges` |
| **steampunk** | El **puesto de señales**: cabina acristalada sobre zócalo de ladrillo, tejado a cuatro aguas con mástil de señal; dentro, el tablero «Needs attention» | `signalBox`, `board` |
| **ring / campus / village** | Edificios singulares por tipo de sala y crecimiento en espiral que rellena huecos | `library`, `clockTower`, `TowncenterAllocator` |
| **feat/hq-spacious-rooms** | Salas grandes por tipo de dato real: tablero en su propio edificio, archivo como terminal en una biblioteca, tareas programadas en la torre del reloj | — |

Estilo visual: paleta cherry, como todos los layouts. Cada manzana conserva los materiales de su barrio del ensanche: ladrillo, estuco, arenisca, cerezo, toba y cobre, y terracota.

## El plano

- **Plaza mayor (celda 0).** Las cuatro calles que la rodean desembocan en ella y el paseo diagonal arranca en su esquina NE. La casa consistorial ocupa el centro con seis puertas: dos en la nave (N y S) y dos en cada brazo del transepto.
  - El crucero tiene el holograma, con su columna libre bajo la linterna, y el faro arriba.
  - La pared kanban (`thread_board`, 9 × 4) está al fondo del brazo oeste y la pantalla grande del hub (5 × 3) al fondo del brazo este. Cada una tiene seis bancos delante.
  - En la nave hay columnas, arcos sobre el crucero y luz empotrada en el suelo y el techo.
  - Delante de la puerta sur, un atrio con fuente. En la esquina NE, un jardín con plátanos y bancos.
- **Edificios singulares en las esquinas de la plaza:**
  - **Biblioteca** (NO): el archivo. Sala de lectura con estanterías contra los muros, mesas y la `archive_terminal` en el muro sur. Dos puertas: a la calle norte y al callejón de la nave.
  - **Puesto de señales** (SO): needs attention y errores. El tablero ocupa el muro oeste, con 4 bombillas y 14 puntos de espera, y la cabina tiene vidrio en todo el contorno.
  - **Torre del reloj** (SE): tareas programadas. Una sala con la pantalla `schedule`, un fuste hasta y = 38 con **cuatro esferas** 3 × 3 a y = 33, el campanario y un chapitel de cobre (pararrayos a y = 47). Es el hito más alto.
  - **Mercado cubierto** (celda (1, 0)): el lounge. Naves de hierro y vidrio, puestos, bancos enfrentados y la pantalla `lounge`. La luz va en la cumbrera, no en linternas.
- **Manzanas de trabajo.** Cuatro edificios en molinete alrededor del patio.
  - Cada edificio es un **podio** de una planta: la sala de trabajo de 6 puestos contra el muro de la calle, dos puertas al patio, el pasaje cubierto, la pantalla grande del proyecto en la mampara del pasaje y una cubierta a dos aguas o una azotea con jardineras.
  - Cuando su proyecto necesita más salas, el podio levanta una **torre** sobre su esquina en chaflán (la esquina de la manzana): 16 de largo, el fondo del edificio, de 3 a 5 plantas.
  - **Planta de la torre:** 3 puestos contra el muro del patio, 2 contra el de la calle y 1 contra el muro interior. La **escalera del atrio** va en medio, y la pantalla grande del proyecto, sobre la puerta del skybridge.
- **Skybridges.** Por la simetría del molinete, la cara de calle de una torre y la cara de testero de la torre de enfrente tienen la puerta en el mismo sitio. Cuando hay dos torres enfrentadas, una **pasarela acristalada** cruza la calle en la planta 2 (y = 12). Si no hay torre enfrente, la puerta es una ventana (`plug_<p>_d|a`). Con 20 proyectos del roster del render salen 6 torres y una pasarela entre la manzana S y la SE.

## Reglas de crecimiento

Todo es determinista e idempotente: `allocate(in, allocate(in)) == allocate(in)`, y el orden del roster no importa. El código está en `TowncenterGrid` y `TowncenterAllocator`.

1. **Espiral 2D de manzanas** alrededor de la plaza, la misma del ensanche. La celda 0 es la plaza, la 2 es la plazuela y la 3, el mercado; las tres existen siempre.
2. **Salas:** índice `= 6·p + planta`, donde `p = 4·celda + lado`. La planta 0 es el podio y las plantas 1 a 5 son la torre. Cada sala tiene 6 puestos.
3. **Proyecto nuevo:** toma la parcela libre más baja de la espiral. Primero **se densifica la manzana** que se está llenando y **se rellenan los huecos** que dejan los proyectos que se van. Solo después se abre la siguiente manzana.
4. **Proyecto lleno:**
   1. **crece hacia arriba**: la planta libre más baja de su edificio (el podio y después la torre, planta a planta, hasta la 5);
   2. con la torre completa, toma una parcela libre **de su propia manzana**;
   3. después, una parcela libre de **una manzana vecina** (que comparte calle);
   4. y si no hay ninguna, la parcela libre más baja de la espiral.
5. **Estable:** las salas y los puestos que ya existen conservan su índice, así que **nada se mueve**. El grupo de construcción de un edificio, `parcel_<p>`, contiene el podio y la torre. Una torre nueva en la manzana de enfrente solo cambia una ventana (`plug_…`) por una pasarela (`bridge_…`), sin tocar los edificios.
6. **Altura de la torre:** `max(mínimo de la parcela, planta más alta usada)`. El mínimo depende solo de la parcela y va de 3 a 5 plantas, así que el skyline no queda plano y la torre solo crece cuando sus salas lo piden. Las plantas sin sala quedan vacías, con luz y con la escalera.
7. **Qué se construye:** las celdas de la 0 a `maxCell`, siempre conectadas por calles.

## Envolvente

Las torres van de y = 25 a y = 37, el cimborrio llega a y = 30 y el reloj, a y = 47. `HqStyle.top() = 48`.

| Proyectos | Salas | Puestos | Celdas | Envolvente (x × z) | Torres | Pasarelas |
|---|---|---|---|---|---|---|
| 4  | 6  | 22  | 0..3 | 110 × 110 (x −27..82, z −82..27) | 2 | 0 |
| 10 | 17 | 70  | 0..5 | 110 × 165 (x −27..82, z −82..82) | 4 | 0 |
| 20 | 29 | 115 | 0..7 | 165 × 165 (x −82..82, z −82..82) | 6 | 1 |

`TowncenterGrid.envelope(maxCell)` da la envolvente para cualquier tamaño. Las cifras de salas, celdas, torres y pasarelas salen de simular el asignador con el roster del render; las confirman el log de `hqRender` y el test `twentyProjectsMakeASkylineWithASkybridge`. Como en el ensanche, el modo survival usa la espina clásica (`HqSiteFrame.style()`).

## Luz y decoración

- **Ni una linterna suelta.** La luz va integrada:
  - paneles en los techos de las salas, de la casa consistorial y de los edificios singulares;
  - incrustaciones en el suelo de la nave;
  - bordillos iluminados en las calles;
  - el brocal iluminado del pozo del patio;
  - la luz en la cumbrera del mercado y en el techo del quiosco;
  - el mástil del puesto de señales.
- **Pase de iluminación** (`TowncenterLight`). Antes de cerrar cada módulo, toda celda pisable a nivel de calle o bajo techo recibe **luz de bloque ≥ 10**, contando solo los bloques del propio módulo. Así, lo que se construye al lado solo puede sumar luz y un edificio nuevo no cambia a otro. Donde falta luz, el pase cambia un bloque macizo por una lámpara empotrada: el suelo bajo la celda, el techo o un muro. Nunca toca troncos, columnas, fixtures, estanterías ni bloques con una planta encima.
- **Decoración con apoyo.** Las flores y los carteles de pie van sobre suelo y los carteles de pared, sobre muro macizo. No hay nada en el paso de puertas, escritorios ni pantallas.
- **Pantallas 3 × 2** por escritorio, sin lámparas de estado. Los NPC trabajan de pie y no hay sillas.

## Tests nuevos (`TowncenterLayoutTest`)

- `layoutIsRegisteredSelectableAndCherry`: registro, `forLayout`, paleta cherry y listas existentes sin cambios.
- `aProjectFillsItsPodiumThenGrowsUpThenTakesItsManzanaThenNextDoor`: orden podio → torre → manzana propia → vecina.
- `newProjectsRefillTheLowestFreeParcel`: densificar la manzana y rellenar huecos sin mover a nadie.
- `twentyProjectsMakeASkylineWithASkybridge`: con el roster del render, ≥ 4 torres, al menos una pasarela y altura > 30.
- `generationIsDeterministicIdempotentAndIgnoresRosterOrder`: determinismo, idempotencia, orden del roster y JSON.
- `growingFrom4To10To20ProjectsNeverMovesABuilding`: salas, puestos y bloques de los edificios sin cambios al crecer.
- `anchorsAreFreeStandableAndReachableFromTheSpawn` (4, 10 y 20): todas las anclas de pie, libres y alcanzables con el A\* del cliente (`PlanWalkability`).
- `everyTowerFloorIsReachableByTheAtriumStair`: 36 hilos, plantas 0 a 5, todas alcanzables.
- `facingTowersAreJoinedByAWalkableSkybridge`: las puertas se alinean, la pasarela existe, su centro es alcanzable a pie y tiene luz.
- `everythingStaysInsideItsFootprints`: envolvente, altura y cada grupo dentro de su área (parcela, patio, celda, par de celdas en las pasarelas).
- `desksHaveOne3x2ScreenNoLampsAndThereAreNoLanterns`.
- `decorationIsSupported`.
- `everyReachableCellHasBlockLightOfAtLeastTen` (4, 10 y 20): luz de bloque de todo el plano en cada celda alcanzable desde el spawn.
- `noDirectionIsLeftWithoutAStreetIntoThePlaza`.

Además, `HqLayoutsPlanTest` (paramétrico sobre `HqLayouts.all()`) prueba el layout con 4, 10 y 20 proyectos, y `HqLayoutsTest` incluye el id nuevo en el orden del menú y las claves de idioma.

## Renders

Los genera el coordinador. Desde `mod/`:

```sh
# galería completa: 4 → 10 → 20 proyectos con el roster de TownRender (cada tamaño parte del anterior)
./gradlew hqRender -PhqArgs="--style hybrid-towncenter --out ../docs/research/hq-hybrids/towncenter"
# opcional, la vista genérica del registro (aérea y planta, la misma cámara que el resto de layouts)
./gradlew hqRender -PhqArgs="--layout hybrid-towncenter --projects 4,10,20 --out ../docs/research/hq-hybrids/towncenter"
```

`--only <subcadena>` limita las vistas (por ejemplo, `--only growth` o `--only 20p_skyline`). Las cámaras están en `TowncenterRender`:

| Vista | Cámara (azimut, elevación) y recorte |
|---|---|
| `aerial` | 45°, 35°; HQ entero |
| `aerial_nw` | 225°, 35°; HQ entero |
| `skyline` | 215°, 14°; HQ entero, vista baja |
| `plan` | cenital; corte a y = 3 |
| `hub` | 25°, 55°; x −23..23, z −23..23, corte a y = 13 |
| `plaza` | 200°, 28°; x/z −28..28, sin corte |
| `room` | sala del podio con más puestos, corte bajo su techo |
| `tower_room` | sala de torre más alta, corte bajo su techo |
| `atrium` | corte vertical por la mitad de la torre más alta (mitad de calle fuera) |
| `signal` | 60°, 50°; x −23..−8, z 7..23, corte a y = 6 |
| `library` | 150°, 50°; x −23..−8, z −23..−11, corte a y = 8 |
| `market` | 30°, 50°; celda (1, 0), corte a y = 7 |
| `manzana` (solo 20) | 45°, 38°; celda 5 entera |
| `growth` | 45°, 35°; la misma cámara y escala para 4, 10 y 20 |

Los PNG de más de 480 KB se guardan con una paleta de 256 colores.

| Vista | 4 proyectos | 10 proyectos | 20 proyectos |
|---|---|---|---|
| Aérea (SE) | [4p](hybrid-towncenter_4p_aerial.png) | [10p](hybrid-towncenter_10p_aerial.png) | [20p](hybrid-towncenter_20p_aerial.png) |
| Aérea (NO) | [4p](hybrid-towncenter_4p_aerial_nw.png) | [10p](hybrid-towncenter_10p_aerial_nw.png) | [20p](hybrid-towncenter_20p_aerial_nw.png) |
| Skyline (vista baja) | [4p](hybrid-towncenter_4p_skyline.png) | [10p](hybrid-towncenter_10p_skyline.png) | [20p](hybrid-towncenter_20p_skyline.png) |
| Planta (y = 3) | [4p](hybrid-towncenter_4p_plan.png) | [10p](hybrid-towncenter_10p_plan.png) | [20p](hybrid-towncenter_20p_plan.png) |
| Hub: casa consistorial | [4p](hybrid-towncenter_4p_hub.png) | [10p](hybrid-towncenter_10p_hub.png) | [20p](hybrid-towncenter_20p_hub.png) |
| Plaza: reloj, señales, biblioteca | [4p](hybrid-towncenter_4p_plaza.png) | [10p](hybrid-towncenter_10p_plaza.png) | [20p](hybrid-towncenter_20p_plaza.png) |
| Sala del podio (pantallas 3 × 2, agentes de pie) | [4p](hybrid-towncenter_4p_room.png) | [10p](hybrid-towncenter_10p_room.png) | [20p](hybrid-towncenter_20p_room.png) |
| Sala de la torre | [4p](hybrid-towncenter_4p_tower_room.png) | [10p](hybrid-towncenter_10p_tower_room.png) | [20p](hybrid-towncenter_20p_tower_room.png) |
| Corte por el atrio | [4p](hybrid-towncenter_4p_atrium.png) | [10p](hybrid-towncenter_10p_atrium.png) | [20p](hybrid-towncenter_20p_atrium.png) |
| Puesto de señales (needs attention) | [4p](hybrid-towncenter_4p_signal.png) | [10p](hybrid-towncenter_10p_signal.png) | [20p](hybrid-towncenter_20p_signal.png) |
| Biblioteca (archivo) | [4p](hybrid-towncenter_4p_library.png) | [10p](hybrid-towncenter_10p_library.png) | [20p](hybrid-towncenter_20p_library.png) |
| Mercado cubierto (lounge) | [4p](hybrid-towncenter_4p_market.png) | [10p](hybrid-towncenter_10p_market.png) | [20p](hybrid-towncenter_20p_market.png) |
| Una manzana con torres | | | [20p](hybrid-towncenter_20p_manzana.png) |
| **Comparativa 4 → 10 → 20 (misma cámara)** | | | [growth](hybrid-towncenter_growth.png) |
| Vista del registro (`--layout`) | [aérea](layout_hybrid-towncenter_4p_aerial.png) · [planta](layout_hybrid-towncenter_4p_plan.png) | [aérea](layout_hybrid-towncenter_10p_aerial.png) · [planta](layout_hybrid-towncenter_10p_plan.png) | [aérea](layout_hybrid-towncenter_20p_aerial.png) · [planta](layout_hybrid-towncenter_20p_plan.png) |

No incluye heatmap de luz: el test `everyReachableCellHasBlockLightOfAtLeastTen` hace esa comprobación celda a celda.

## Límites conocidos

- Con el roster del render solo hay **una** pasarela con 20 proyectos. Hacen falta dos torres en esquinas enfrentadas, y eso depende de qué proyectos crecen y dónde. Con más proyectos grandes salen más.
- Las torres no tienen puerta a la azotea del podio: se entra por la escalera del atrio desde la sala de la planta baja.
- El render dibuja algunos bloques decorados como cajas, igual que en el ensanche.
