# Acrópolis en terrazas (`hybrid-terraces`)

Layout híbrido de la rama `design/hybrid-terraces`. Es un layout **nuevo** del registro
(`HqLayouts.HYBRID_TERRACES`, id `hybrid-terraces`) que se elige desde el menú HQ como los demás,
con las claves `t3craft.hq.layout.hybrid-terraces.name/.desc` en en_us, es_es y es_mx. No toca
ningún layout existente: el único test ajeno que cambia es la lista de ids de `HqLayoutsTest`, que
ahora incluye el nuevo id al final.

Código:

| Pieza | Qué hace |
|---|---|
| `TerraceTown` | Plano fijo (no depende del roster): celdas de 29×29, nivel de cada terraza, agrupación en terrazas, enlaces y orden de los solares. |
| `TerraceStyle` | Gancho `HqStyle`: asignación (barrios por solar), envolvente, `bottom`/`top`, paleta cherry. |
| `TerraceGenerator` | Dibuja el plan (`HqLayout` → `BuildPlan`) y hace el pase de luz. |
| `PlanNav`, `PlanLight` | Alcanzabilidad con las reglas de `Collision` del cliente y luz de bloque al estilo vanilla. Los usan el generador y los tests. |
| `TerraceFootprints` | API pública para herramientas: áreas, suelo por columna y mapa de luz de las celdas caminables. |
| `TerracesRender` | Vistas de `hqRender --style hybrid-terraces`. |

## Qué toma de cada diseño

| De | Qué toma | Cómo queda aquí |
|---|---|---|
| **village** | Terrazas a varias alturas, edificios singulares por tipo de sala, materiales por distrito | Cada anillo baja un nivel (4 bloques) y en los anillos exteriores una terraza de cada dos baja otro. Hay escalinatas de 7 de ancho talladas en la terraza alta, y los barrios usan los materiales de distrito de village (piedra, adobe, entramado, ladrillo). |
| **skyislands** | Islas a distinta altura unidas por puentes | Tres islas flotantes al nivel de la acrópolis, con plataforma redondeada y panza rocosa que se estrecha hacia abajo: el **molino** detrás del templo y dos islas de proyecto (este y oeste). Cada una tiene un puente de cerezo de 5 de paso sobre un jardín más bajo, que tiene un estanque y pilares. |
| **mesoamerican** | Pirámide con atrio y plaza procesional | El **templo** de 21×21 y 12 de alto lleva un tejado escalonado en cuatro gradas con un santuario y la baliza arriba. Tiene la plaza procesional delante y debajo el eje propileos → atrio → fuente. |
| **gothic / spacious-rooms** | Escala de crucero (nave alta) | La nave del templo mide 19×19 por dentro, con 12 de alto, columnas de cuarzo y ventanales. Dentro están la pizarra de atención y 14 bancos. |
| **ring** | Hub monumental legible y crecimiento por anillos | Los solares se abren anillo a anillo alrededor de la acrópolis. Hay un tholos con cúpula escalonada sobre el holograma, con la columna libre y la baliza colgada de la cúpula. |
| **campus** | Barrios con patio y relleno de huecos | Cada barrio rodea un patio con edificios de **tres tamaños**: la sala principal (6 puestos, gablete), el anexo B (4 puestos) y la torre C (4 puestos, 9 de alto). Un solar liberado se vuelve a ocupar antes de crecer hacia fuera. |
| **ensanche / mediterranean** | Plazas, stoas, muros con puerta | Hay dos **stoas** columnadas frente a la plaza: la oeste con la pared kanban y la este con la pantalla grande. Cada barrio tiene un murete con portada y un cartel con el proyecto. |

## Forma

```
              anillo 3          (0,-3) isla del MOLINO  (nivel 6, flota)
                                   ║ puente sobre jardín (nivel 4)
   anillo 2   ┌──────┬───────┬─────╨─────┬───────┬──────┐
              │terraza NO (3 solares) TEMPLO  terraza NE  │  nivel 4
   anillo 1   │  BIBLIOTECA  │  (nivel 6) │  GUARDIA      │  esquinas: nivel 5
   isla O ═══ │ STOA kanban  │ GRAN PLAZA │ STOA pantalla │ ═══ isla E
              │  TABERNA     │ PROPILEOS  │  LONJA        │
              │terraza SO    │ ATRIO+fuente│  terraza SE  │  nivel 4
              └──────────────┴────────────┴───────────────┘
              anillos 3..5: terrazas de 2-3 solares, niveles 3/2, 2/1, 1/0
```

- **Acrópolis (nivel 6, y=24):** gran plaza, templo al norte, stoas al oeste y al este, y los
  propileos al sur con el spawn.
- **Salas especiales**, cada una en su propia terraza:
  - esquinas de nivel 5: biblioteca-ermita con el terminal de archivo (NO), guardia (NE),
    taberna (SO) y lonja de review (SE);
  - el molino, en su isla.

  Cada esquina tiene escalinatas a las dos piezas de la acrópolis que toca y a los solares de
  debajo.
- **Barrios:** solares de los anillos 2 a 5, agrupados en terrazas de 2 o 3 solares con el mismo
  suelo y bordes abiertos. Cada barrio da la cara a la celda por la que se llega (su puerta mira al
  camino).
- **Circulación en red:** dos celdas construidas cuyos niveles se diferencian como mucho en uno
  siempre quedan unidas, por borde abierto o por escalinata, y las islas se unen por puente. Por
  eso la red se cierra en bucles sola. Toda celda tiene un paseo perimetral de 4 de ancho que cruza
  la cabeza de las escalinatas, y la balaustrada solo está donde la caída es de 2 o más bloques.
  En el plano completo ningún solar queda a más de 3·anillo enlaces de la plaza: **no hay ramas
  largas**, el defecto de village con 20 proyectos.

## Reglas de crecimiento

1. **El plano es fijo** (`TerraceTown`). El roster solo decide qué solares están ocupados.
2. **Orden de los solares:** primero por anillo, después por distancia en enlaces a la plaza y,
   dentro de cada grupo, por muestreo del ángulo más lejano. Así el 2.º, el 3.º y el 4.º solar caen
   en direcciones distintas y la acrópolis crece hacia abajo y hacia los lados en varias
   direcciones a la vez. Las islas E y O son los dos primeros solares del anillo 3.
3. **Asignación** (`TerraceStyle.allocate`, determinista e idempotente):
   - un hilo conserva su puesto;
   - un barrio conserva su solar mientras tenga algún puesto ocupado;
   - un hilo nuevo ocupa el puesto libre más bajo de su proyecto (sala, luego anexo B, luego
     torre C);
   - si no queda ninguno, abre un barrio en el **solar libre de rango más bajo**, de modo que los
     solares interiores liberados se rellenan antes de abrir anillo.
4. **Qué se construye:** las piezas fijas, los solares ocupados y las celdas del camino fijo de cada
   uno hasta la plaza (como jardín mientras estén libres), más el jardín que cruza cada puente.
   Sumar un proyecto **nunca mueve** nada: solo añade celdas o anexos.
5. Los proyectos de más de 14 hilos abren un segundo barrio.

## Envolvente

| Proyectos | Barrios | Celdas construidas | Celdas (x · z) | Bloques |
|---|---|---|---|---|
| 4 | 4 | 16 | −2..2 · −3..2 | 145 × 174 |
| 10 | 11 | 23 | −2..2 · −3..2 | 145 × 174 |
| 20 | 21 | 35 | −3..3 · −3..3 | 203 × 203 |

Datos con el roster de `hqRender` (`CampusViews.roster`, un proyecto de 26 hilos ocupa dos barrios).
Con 4 proyectos la envolvente ya incluye la isla del molino al norte y una terraza por cuadrante; con
10 se rellenan solares dentro de la misma envolvente; con 20 se suman las islas E y O y el anillo 3.

Alturas: `bottom = 0` (suelo natural) y `top = 64`. La acrópolis está en y=24 y el santuario del
templo llega a y≈51.

## Luz, NPC y decoración

- **Luz ≥ 10 en toda celda caminable.** Tras dibujar, el generador calcula con `PlanNav` todas las
  celdas a las que un NPC llega desde el spawn y, donde la luz (`PlanLight`, de noche) no llega a
  10, empotra una luz en el suelo:
  - froglight perlada fuera, shroomlight dentro;
  - preferiblemente en una rejilla de 4×4, para que quede regular;
  - además, cada escalinata lleva una luz empotrada en cada mejilla, y el templo tiene paneles de
    luz en el techo.

  **No hay linternas sueltas.**
- **NPC:** spawn en los propileos y camino real por escalinatas y puentes a cada escritorio. Las
  reglas de `PlanNav` son las de `Collision` del cliente.
- **Escritorios:** pantalla 3×2 por escritorio, sin lámparas ni sillas; el agente trabaja de pie.
  El archivo es el terminal de la biblioteca (`t3craft:archive_terminal` con el módulo blocks).
- **Decoración con apoyo:** carteles sobre muro, macetas, campana y brasero sobre bloque, y nada en
  el paseo ni delante de puertas o pantallas.

## Tests (JUnit)

`TerracesLayoutTest` (nuevo):

- `registeredSelectableAndCherry`: registro, menú (al final de la lista), estilo propio y paleta
  cherry.
- `acropolisOnTopSpecialsOnDistinctTerracesIslandsByBridges`: la acrópolis es lo más alto, el templo
  está al norte, cada sala especial está en una terraza distinta y las islas solo se alcanzan por su
  puente, que cruza un jardín más bajo.
- `terracesHoldTwoOrThreeBarriosAndEverySolarReachesThePlazaWithoutLongBranches`: terrazas de 2-3
  barrios, todo solar alcanzable, profundidad ≤ 3·anillo y rango por anillos.
- `consecutiveSolaresOpenInSeveralDirections`: los 4 primeros solares caen en ≥ 3 cuadrantes.
- `theNetworkHasLoops` (10 y 20 proyectos): número ciclomático ≥ 5 / ≥ 7, red conexa.
- `deterministicIdempotentAndStableWhileGrowing` (0/4/10/20): misma huella, idempotente, y ni los
  barrios ni los puestos se mueven al crecer.
- `aFreedInteriorSolarIsRefilledFirst`, `bigProjectsGrowAnnexesThenASecondBarrio`.
- `anchorsFootprintScreensAndLight` (4/10/20): anclas válidas y alcanzables
  (`HqAnchorPlacementTest`), todo dentro de la envolvente y de la altura, pantallas 3×2 sin lámpara,
  **luz ≥ 10 en toda celda caminable** (`PlanWalkability` + `PlanLight`) e islas y acrópolis
  alcanzables.
- `noLooseLanternsAndNoFloatingDecoration` (4/20).
- `theEnvelopeOnlyGrows`.

Además, `HqLayoutsPlanTest` (compartido, parametrizado con `HqLayouts.all()`) ya cubre el nuevo
layout con 4, 10 y 20 proyectos.

## Renders

Comandos exactos, desde `mod/`. El primero genera la galería y la comparativa: grow 4 → 10 → 20, cada
paso asignado contra el anterior, con el pipeline del juego y los fixtures activos.

```sh
./gradlew hqRender -PhqArgs="--style hybrid-terraces --out ../docs/research/hq-hybrids/terraces"
```

El segundo, opcional, saca la vista genérica del registro (aérea más planta a y=3) con la misma
cámara que los demás layouts:

```sh
./gradlew hqRender -PhqArgs="--layout hybrid-terraces --projects 4,10,20 --out ../docs/research/hq-hybrids/terraces"
```

Cámaras de `TerracesRender` (azimut, elevación y escala en píxeles por bloque, antes de `--scale`):

| Vista | Recorte | Cámara |
|---|---|---|
| `aerial` | toda la huella | 35°, 34°, escala 4.6 / 3.8 / 3.0 (4/10/20) |
| `aerial_nw` | toda la huella | 215°, 30°, la misma escala |
| `plan` | 3 bloques sobre el suelo de cada terraza | cenital, escala × 1.15 |
| `hub` | celda del templo, y = 21..31 | 25°, 52°, 14 |
| `plaza` | x = −43..43, z = −14..43, y = 21..41 | 200°, 32°, 7.5 |
| `barrio` | el barrio con más puestos, del suelo −3 al suelo +4 | 30°, 55°, 15 |
| `barrio_out` | ese barrio con margen 2, del suelo −6 al suelo +16 | 40°, 30°, 12 |
| `library` | celda (−1,−1), y = 17..25 | 200°, 55°, 14 |
| `mill` | celdas (0,−3) y (0,−2), y = 4..42 | 120°, 22°, 6.5 |
| `light` | mapa cenital de las celdas caminables (rojo < 10, amarillo 10–11, verde ≥ 12) | 3 px por bloque |
| `compare` | huella de 20 proyectos para los tres tamaños | 35°, 34°, escala 2.6 |

Los PNG de más de 480 KB se reescriben a 256 colores (`CampusViews.save`).

| Vista | 4 proyectos | 10 proyectos | 20 proyectos |
|---|---|---|---|
| Aérea SE | `terraces_4p_aerial.png` | `terraces_10p_aerial.png` | `terraces_20p_aerial.png` |
| Aérea NO | `terraces_4p_aerial_nw.png` | `terraces_10p_aerial_nw.png` | `terraces_20p_aerial_nw.png` |
| Planta por terraza | `terraces_4p_plan.png` | `terraces_10p_plan.png` | `terraces_20p_plan.png` |
| Templo (hub) por dentro | `terraces_4p_hub.png` | `terraces_10p_hub.png` | `terraces_20p_hub.png` |
| Gran plaza, tholos, kanban y pantalla | `terraces_4p_plaza.png` | `terraces_10p_plaza.png` | `terraces_20p_plaza.png` |
| Barrio: puestos 3×2, agentes de pie | `terraces_4p_barrio.png` | `terraces_10p_barrio.png` | `terraces_20p_barrio.png` |
| Barrio por fuera | `terraces_4p_barrio_out.png` | `terraces_10p_barrio_out.png` | `terraces_20p_barrio_out.png` |
| Sala especial: biblioteca (archivo) | `terraces_4p_library.png` | `terraces_10p_library.png` | `terraces_20p_library.png` |
| Isla del molino y puente | `terraces_4p_mill.png` | `terraces_10p_mill.png` | `terraces_20p_mill.png` |
| Mapa de luz | `terraces_4p_light.png` | `terraces_10p_light.png` | `terraces_20p_light.png` |
| Vista genérica (`--layout`) | `layout_hybrid-terraces_4p_aerial.png`, `layout_hybrid-terraces_4p_plan.png` | `layout_hybrid-terraces_10p_aerial.png`, `layout_hybrid-terraces_10p_plan.png` | `layout_hybrid-terraces_20p_aerial.png`, `layout_hybrid-terraces_20p_plan.png` |

Comparativa 4 → 10 → 20 con la misma cámara: `terraces_compare_4_10_20.png`.

## Pendiente o posible

- Las salas especiales no tienen ocupantes: el roster en vivo manda a todos los hilos a trabajar,
  igual que en village. El gancho para asignarlos está en el plano (cada sala tiene su celda y su
  grupo).
- Se podrían añadir más islas en los anillos 4 y 5 (por ejemplo, en las diagonales) si con más de
  20 proyectos se quiere más verticalidad.
