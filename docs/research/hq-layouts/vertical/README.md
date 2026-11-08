# Layout vertical: torres con atrio y puentes (`layout/vertical`)

Estilo `vertical` (`"hq": {"style": "vertical"}` en `config/t3craft.json`). No es una piel sobre la
espina de alas: tiene su propia geometría (`TowerLayout`) y su propio generador (`TowerGenerator`).
`HqGenerator.generate` delega en él solo cuando el estilo activo es `vertical`. Cherry y classic no
cambian: siguen en `HqStyles.all()` con su geometría compartida, y `vertical` se registra aparte en
`HqStyles.layouts()`. Se puede seleccionar por id, pero no entra en los tests que comparan estilos
celda a celda.

![Crecimiento 4 → 10 → 20](vertical_growth_4_10_20.png)

## Idea

El HQ crece **hacia arriba**, no a lo largo de un pasillo.

- **T1 "Atrio"** (siempre construida, 43 × 31). Un atrio de 13 × 13 abierto de la planta baja a la
  azotea aloja el **hub**:
  - abajo, el tablero "Needs attention" frente a la entrada, la pantalla grande y los bancos;
  - en el balcón del piso 1, la **pared kanban** (`thread_wall`), visible a través del atrio;
  - la columna del holograma (`x = z = 0`) queda libre en toda la altura hasta el **faro**, en la
    cúspide de una cúpula escalonada de vidrio sobre la azotea;
  - una **escalera monumental de ida y vuelta** sube por el lado sur del atrio: dos carriles, los
    pisos pares suben hacia el este y los impares hacia el oeste. No hay ascensores: los NPC suben
    andando.
- Alrededor del atrio, cada piso tiene un anillo de galería con barandilla y una **logia norte con
  jardín colgante** (barandilla, macetas y hojas colgando del dintel). A los lados quedan dos
  **medios pisos** (este y oeste): cada uno es una parcela de proyecto con 6 escritorios.
- **Cuartos especiales en lugares singulares**:

  | Cuarto | Dónde | Anclas |
  |---|---|---|
  | Coordinación (hub) | planta baja del atrio de T1 + balcón kanban del piso 1 | `board*`, `bench_*`, `walk_hub_*`, `hologram`, `beacon`, `thread_wall*`, `bigscreen_hub…` |
  | Needs attention | tablero del hub, en el eje de la entrada | `board`, `board_lamp_*`, `board_slot_*` |
  | Revisión / PR | **puente acristalado** que cruza el atrio en el piso 3 (deja libre la columna del holograma) | `walk_review_*`, banco |
  | Tareas programadas | **azotea mirador** de T1: pabellón con la pantalla `bigscreen_schedule…`, cúpula y antena | `walk_schedule_*` |
  | Lounge | **terraza** sobre el podio, al nivel del piso 1, con pérgolas y bancos que miran a la plaza | `walk_lounge_*`, `bench_*` |
  | Archivo | **podio** (ala oeste del vestíbulo): terminal `archive_terminal`, sin estanterías por hilo | `archiveTerminal`, `archive` |
  | Trabajo por proyecto | medios pisos de las torres | `desk_*`, `monitor_*`, `wing_*`, `walk_*`, `module@*`, `bigscreen_module@…` |

- **Podio y plaza**. Delante de T1 hay un podio de 43 × 8 con el vestíbulo, el archivo y un café; su
  azotea es el lounge. Delante del podio, una plaza de 89 × 23 con ejes, glorieta central, parterres
  con cerezos, bancos y farolas. El spawn está en la plaza.
- **T2 "Jardín"** (este, retranqueada al norte; 35 × 31; 7 pisos de 7 bloques; la más alta): fachada
  de madera y vidrio verde, pozo de luz con árbol, azotea ajardinada con corona de madera.
- **T3 "Piedra"** (oeste, más retranqueada; 39 × 31; 4 pisos de 9 bloques; techos altos): deepslate
  y vidrio azul con ventanas estrechas, azotea almenada con depósito de agua.
- **Uniones**: un pasillo del podio en planta baja por torre (`podium_east`, `podium_west`) y
  **skybridges** acristalados a distintas alturas:
  - T1 piso 2 → T2 piso 2 (baja 2);
  - T1 piso 5 → T2 piso 6 (sube 2);
  - T1 piso 3 → T3 piso 3 (sube 3).

  Los puentes salvan la diferencia de cota con escalones. El conjunto forma un pequeño skyline
  (alturas de azotea 48, 56 y 45) y no una barra.

### Tipos de planta por piso (variedad)

Cada piso de cada torre tiene un tipo fijo, así que el mismo proyecto siempre ve la misma planta:

- **Planta abierta**: tres islas de escritorios espalda con espalda, alfombra con el color del entorno
  y macetas junto a la fachada.
- **Salas pequeñas**: un pasillo y tres salas de vidrio, con una isla por sala.
- **Mezzanine**: una tarima de un escalón (escalera de bloques `stairs` a lo largo) con dos filas de
  escritorios mirando a la fachada.

| Torre | Pisos 1… |
|---|---|
| T1 | abierta, salas, mezzanine, abierta, salas |
| T2 | mezzanine, abierta, salas, mezzanine, abierta, salas, abierta |
| T3 | salas, abierta, mezzanine, abierta |

Un medio piso libre no queda vacío: es un **jardín colgante** con parterres de musgo, árboles bajos y
bancos, y deja libres los pasos hacia los puentes.

Todo escritorio lleva **una pantalla de 3 × 2** (`Workstation.screenOriginFor`/`screenCells`) y
ninguna lámpara, y el NPC trabaja de pie, sin silla. Las únicas bombillas del plan son las del
tablero.

## Reglas de crecimiento

- **Parcela = índice de módulo.** `HqLayout.allocate` (sin cambios) ya da a cada módulo nuevo el
  **índice libre más bajo** y libera el índice de un módulo que se queda sin hilos. `TowerLayout`
  asigna a cada índice un medio piso fijo:
  - índices 0–9 → T1, pisos 1–5, primero el este y luego el oeste, de abajo arriba;
  - índices 10–23 → T2, pisos 1–7;
  - índices 24–31 → T3, pisos 1–4.

  La capacidad total es de 32 medios pisos. Un módulo con índice ≥ 32 no se construye.
- **Orden**: primero se llenan los pisos de T1. Cuando hace falta el índice 10, **nace T2**, con su
  pasillo de podio y sus dos skybridges. Cuando hace falta el 24, **nace T3**.
- **De 10 a 20 proyectos** (roster de muestra, con anexos porque algunos proyectos tienen más de 6
  hilos):
  - 4 proyectos ocupan 6 medios pisos de T1;
  - 10 proyectos ocupan 13 (T1 llena y T2 con 3);
  - 20 proyectos ocupan 26 (T2 casi llena y T3 con 2).
- **Estabilidad.** Un módulo conserva su índice, y con él su medio piso, sus escritorios y sus
  anclas. Sumar proyectos solo cambia:
  - medios pisos libres que pasan de jardín a sala de trabajo (grupo `parcel_i` → `module_i`);
  - grupos nuevos (torre, pasillo de podio, puentes);
  - en la fachada de T1, las celdas de las puertas de los puentes y pasillos, que pasan de vidrio a
    aire (siguen en el grupo de T1);
  - la conexión de algunos paneles de vidrio que tocan un vecino nuevo.

  El test `growingNeverMovesWhatAlreadyStands` lo comprueba celda a celda.
- **Huecos.** Si un proyecto desaparece, su medio piso vuelve a ser jardín (`parcel_i`) y es el
  primero que ocupa el siguiente proyecto nuevo (`aProjectThatLeavesFreesItsHalfFloorForTheNextOne`).
  Una torre existe mientras alguno de sus medios pisos tenga un módulo; T1 existe siempre.
- **Determinista e idempotente**: el mismo roster da el mismo plan en cualquier orden, y regenerar
  desde el layout guardado no cambia nada.

## Tamaños

| Proyectos (muestra) | Medios pisos | Torres | Envolvente xz (HQ) |
|---|---|---|---|
| 4 | 6 | T1 | x −44..44, z −15..46 (89 × 62) |
| 10 | 13 | T1 + T2 | x −44..71, z −29..46 (116 × 76) |
| 20 | 26 | T1 + T2 + T3 | x −73..71, z −37..46 (145 × 84) |

Alturas (y relativa al suelo del hub; el plan no baja de y = 0):

| Torre | Pisos | Azotea | Punto más alto |
|---|---|---|---|
| T1 | y = 8, 16, 24, 32, 40 | y = 48 | cúpula hasta y = 55 (faro); antena **y = 62** |
| T2 | cada 7 desde y = 7 | y = 56 | corona hasta y = 62 |
| T3 | y = 9, 18, 27, 36 | y = 45 | depósito hasta y = 50 |

**Y máxima = 62** (`TowerLayout.TOP`). Con el suelo del HQ a y ≈ 64–100, la cota absoluta queda
en ≈ 126–162. Cabe con margen bajo el límite de 320 del mundo; hasta un origen en y = 257 cabe.

## Terreno y modo survival

Los grupos se mantienen dentro de sus áreas (`TowerLayout.base()`, `towerArea(T2)` y
`towerArea(T3)`; lo comprueba un test). El site de survival actual (`HqSiteFrame`, envolvente
≤ 64, `SiteLimits.HEADROOM = 12`) no admite esta escala y **no se ha tocado**. Para usar este
estilo en survival, el site reservaría:

1. **Una parcela base** de 89 × 62 (plaza, podio y T1) con 63 bloques de altura libre.
2. **Dos reservas de crecimiento** que solo se nivelan y construyen cuando nace la torre:
   - este (T2, su pasillo y sus puentes): x 22..71, z −29..1;
   - oeste (T3, su pasillo y su puente): x −73..−22, z −37..−7.

   Cada torre es una expansión del site con su snapshot y su paso de deshacer, como hoy un ala.
   Los medios pisos son grupos, así que llenar un medio piso es una expansión pequeña dentro de la
   reserva de su torre.
3. Subir `HEADROOM` por estilo (63) y el tope de volumen y de chunks por snapshot para la torre.
   La alternativa es partir cada torre en un snapshot por piso, que encaja con los grupos ya
   separados por piso.

## Renders (`./gradlew hqRender -PhqArgs="--style vertical --out ../docs/research/hq-layouts/vertical"`)

El roster de muestra es `TowerLayout.sampleRoster(n)`: 4 entornos, de 1 a 9 hilos por proyecto y un
hilo archivado cada tres proyectos. Los tres tamaños se generan seguidos, cada uno a partir del
layout del anterior, como crece de verdad.

| Vista | 4 proyectos | 10 proyectos | 20 proyectos |
|---|---|---|---|
| Aérea | [full](vertical_4p_full.png), [NO](vertical_4p_full_nw.png) | [full](vertical_10p_full.png), [NO](vertical_10p_full_nw.png) | [full](vertical_20p_full.png), [NO](vertical_20p_full_nw.png) |
| Planta baja cortada (y=4) | [plan](vertical_4p_plan_ground.png) | [plan](vertical_10p_plan_ground.png) | [plan](vertical_20p_plan_ground.png) |
| Planta piso 2 (y=19) | [plan](vertical_4p_plan_f2.png) | [plan](vertical_10p_plan_f2.png) | [plan](vertical_20p_plan_f2.png) |
| Corte E-O por el atrio | [corte](vertical_4p_section_ew.png) | [corte](vertical_10p_section_ew.png) | [corte](vertical_20p_section_ew.png) |
| Corte N-S por el atrio | [corte](vertical_4p_section_ns.png) | [corte](vertical_10p_section_ns.png) | [corte](vertical_20p_section_ns.png) |
| Corte por los skybridges | — | [corte](vertical_10p_section_bridges.png) | [corte](vertical_20p_section_bridges.png) |
| Hub (planta baja) | [hub](vertical_4p_hub.png) | [hub](vertical_10p_hub.png) | [hub](vertical_20p_hub.png) |
| Atrio en altura | [atrio](vertical_4p_atrium.png) | [atrio](vertical_10p_atrium.png) | [atrio](vertical_20p_atrium.png) |
| Cuartos de trabajo | [abierta](vertical_4p_room_0.png), [salas](vertical_4p_room_2.png), [mezzanine](vertical_4p_room_4.png) | [T2 mezzanine](vertical_10p_room_10.png), [T1 salas](vertical_10p_room_3.png) | [T3 salas](vertical_20p_room_24.png), [T2 abierta](vertical_20p_room_22.png) |
| Cuartos especiales | [mirador](vertical_4p_lookout.png), [lounge](vertical_4p_lounge.png), [archivo](vertical_4p_archive.png), [jardín colgante](vertical_4p_room_6.png) | [puente de revisión](vertical_10p_review.png), [skybridges T1→T2](vertical_10p_skybridge.png) | [skybridge T1→T3](vertical_20p_skybridge.png), [jardín colgante T3](vertical_20p_room_26.png) |
| Comparativa | [4 → 10 → 20](vertical_growth_4_10_20.png) | | |

Los PNG de más de 480 KB se guardan con 256 colores (median cut en `VerticalRender`).

## Tests (`VerticalLayoutTest`)

- Determinismo, idempotencia e independencia del orden del roster (0, 4, 10 y 20 proyectos).
- Nacimiento de torres (1 → 2 → 3) y orden de parcelas.
- Estabilidad al crecer de 4 a 10 y de 10 a 20, celda a celda; escritorios y estaciones intactos.
- Liberación y reutilización de un medio piso.
- Anclas libres, pisables y alcanzables a pie desde el spawn, con 1, 4, 10 y 20 proyectos y en un
  historial mixto. Usa el validador `HqAnchorPlacementTest.assertAnchorsValid`: A* del cliente
  sobre colisiones reales, escaleras y puentes incluidos.
- Grupos dentro de su footprint, Y máxima = `TOP` y hojas persistentes.
- Pantallas 3 × 2 por escritorio, sin lámparas, con el NPC de pie frente a la pantalla.
- Hub: tablero, holograma con columna libre hasta el faro, kanban en el balcón del piso 1 y
  pantallas del hub y del mirador.
- Solo bloques vanilla sin el módulo blocks.
