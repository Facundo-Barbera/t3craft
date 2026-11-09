# Híbrido «Ciudadela en anillos con barrios» (`hybrid-ringcampus`)

> Las imágenes de esta investigación (PNG de renders) se quitaron del árbol el 2026-11-09 para aligerar los worktrees. Siguen en el tag `archive/research-renders-2026-11-09`: `git show archive/research-renders-2026-11-09:<ruta>.png > /tmp/x.png`.

Rama `design/hybrid-ringcampus`. Es un layout **nuevo** del registro (`HqLayouts`, id
`hybrid-ringcampus`, nombre «Ciudadela en anillos» / «Ringed citadel»), que se elige desde el menú
HQ como los demás. No modifica ninguno de los layouts que ya existen.

Código: `RingCampusGeometry` (geometría pura, zonas, marcos de barrio, regla de crecimiento),
`RingCampusGenerator` (el plan y la pasada de luz), `RingCampusStyle` (gancho de layout: paleta cherry,
colocador, envolvente y alturas), `PlanLight` (luz de bloque de un plan, compartida por el
generador, los tests y el render) y `RingCampusLayoutTest`. En hqRender, `RingCampusViews`.

## Qué toma de cada diseño

| De | Qué |
|---|---|
| **ring** | El esqueleto: el hub sobre un podio (y = 4) en el centro, el **patio hundido** con canal de agua y cerezos, los **puentes** norte y sur al hub, el **anillo interior elevado**, las 8 avenidas radiales que bajan en **rampas de medio bloque** transitables, la calle interior y los anillos con su calle. Desde el aire se leen los anillos y las radiales. |
| **campus** | Los **barrios**: cada sector del anillo es un cluster de 2 a 4 edificios de distintos tamaños alrededor de **su patio**, con caminos pavimentados entre ellos y jardines en los huecos. También la regla de **densificar el barrio** en lugar de abrir otro. |
| **steampunk / spacious-rooms** | El **hub como nave alta**: una basílica con nave central abovedada (arranca a 12 bloques del suelo y llega a 17 en la clave), naves laterales más bajas, arcadas de troncos de cerezo, claristorio con paneles iluminados y rosetones en los hastiales. |
| **mediterranean** | El **claustro del archivo**: patio cuadrado de 15 × 15 con muros blancos, cuatro puertas, galería porticada con tejado de ladrillo y un estanque en el centro. La terminal `archive_terminal` está en la galería norte. |
| **ring (nodos)** | La **torre** de revisión, la **rotonda** de tareas programadas y el **patio hundido** del lounge, cada uno con su forma propia. Las mantiene en los cruces de las radiales con la calle del anillo 1. |
| **vertical / ensanche** | Las salas de trabajo que **suben un piso** con una escalera recta que entra en el plano, y edificios de alturas distintas dentro de un mismo barrio. |

El estilo visual base es **cherry**: la paleta del estilo es exactamente la de `CherryStyle`. Cada
barrio elige uno de tres juegos de materiales según su lote:

- paneles shoji del kit con tejado de cerezo;
- terracota del color de su entorno con tejado de pizarra;
- calcita con tejado de ladrillo.

Así, dos barrios vecinos no se parecen.

## Planta

```
                     anillo 2: 12 barrios grandes (radiales cada 30°)
                calle del anillo 1 · nodos: N torre · E rotonda · S claustro · O lounge
           anillo 1: 8 barrios (radiales cada 45°)
        calle interior · rampas por las 8 radiales · terrazas ajardinadas
     ANILLO ELEVADO (y=4): paseo con barandilla al patio y postes de luz
  PATIO HUNDIDO (y=0): jardín, canal, cerezos; puentes N y S al hub (y=4); escaleras en las diagonales
HUB: basílica sobre podio, nave abovedada (12→17), tablero, kanban, pantalla grande, holograma, faro
```

| Radio | Qué |
|---|---|
| ≤ 13 | hub (octógono achaflanado de 27 × 27) |
| ≤ 21 | patio hundido |
| ≤ 27 | anillo elevado |
| ≤ 35 | rampas y terrazas |
| ≤ 40 | calle interior |
| 40 – 79 | anillo 1: 8 lotes, centros a r = 59 |
| 79 – 85 | calle del anillo 1, con los nodos a r = 82 |
| 85 – 129 | anillo 2: 12 lotes, centros a r = 107 |
| 129 – 135 | calle del anillo 2 |
| 135 – 181 | anillo 3, solo con más de 20 proyectos: 16 lotes, centros a r = 158 |

### Un barrio

```
            [ nave 2 (exterior) ]
 [ nave 0 ]   patio + cerezo     [ nave 1 ]
            [ portal con letrero ]   ← lado del hub
```

- **Patio**: un cuadrado pavimentado de 11 × 11 (13 × 13 en el anillo 2) con un cerezo o un estanque
  con macetas en el centro.
- **Portal** del lado del hub: un pasaje abovedado de 3 de ancho, con tejado a cuatro aguas y el
  letrero colgante del proyecto (nombre, número de escritorios y entorno) bajo el arco.
- **Naves de trabajo**: tres posiciones (izquierda, derecha y exterior), cada una con la puerta al
  patio. Por dentro miden 9 × 10 en el anillo 1 y 11 × 12 en los anillos 2 y 3. Tienen 6 escritorios
  de pie con su pantalla 3 × 2: dos contra la pared del fondo y cuatro contra las laterales. La línea
  central queda libre para la escalera. Cada nave lleva su tejado: a dos aguas, a cuatro aguas o
  azotea verde con parapeto.
- **Piso superior**: una escalera recta de 6 peldaños por la línea central, con hueco en el forjado,
  y otros 6 escritorios arriba.
- Las posiciones de nave libres son **jardines** con un cerezo. Los caminos entre edificios están
  pavimentados y las esquinas son jardín con arbustos.

## Reglas de crecimiento

El índice de módulo es `lote · 8 + b`. `b` = 0, 1 o 2 es la planta baja de la nave 0, 1 o 2, y
`b` = 3, 4 o 5 es su piso superior. El `HqLayout` y su JSON no cambian.

1. **Proyecto nuevo**: ocupa el lote libre más bajo. Los lotes se numeran anillo por anillo y, dentro
   de cada anillo, en un orden fijo que los reparte alrededor del círculo:
   - anillo 1: posiciones 0, 4, 2, 6, 1, 5, 3, 7, así que con 4 proyectos ya sale un molinete con un
     barrio por cuadrante;
   - anillo 2: posiciones 0, 6, 3, 9, 1, 7, 4, 10, 2, 8, 5, 11.

   Cuando se llena el anillo 1 se abre el anillo 2, y los **huecos** que deja un proyecto que se va se
   **rellenan** antes que cualquier lote nuevo.
2. **Proyecto grande**: **densifica su barrio** en este orden: nave 0, nave 1, **piso** sobre la nave 0,
   nave 2 y pisos sobre las naves 1 y 2. Son hasta 36 escritorios, y nunca ocupa otro lote. Si ni así
   alcanza, los hilos que sobran esperan sin escritorio, igual que en un site lleno.
3. **Estable**: `HqLayout.allocate` conserva índices y escritorios. Las celdas de una nave dependen
   solo de sus módulos, y las de un lote, solo de su anillo y de los nodos del anillo 1, nunca de
   cuántos anillos haya abiertos. Al crecer de 4 a 10 y a 20 proyectos no cambia ningún bloque de
   las naves, el hub, el núcleo, los nodos ni el archivo (lo comprueba un test bloque a bloque).
4. **Liberar**: el lote de un proyecto que se va vuelve a ser jardín (con un cerezo) y se reutiliza.

**Con 4, 10 y 20 proyectos** (roster de los renders):

| Proyectos | Anillos | Barrios | Envolvente xz | Alto (y) |
|---|---|---|---|---|
| 4 | 1 | 4 de 8 | 187 × 187 | −3 … 29 |
| 10 | 2 | 8 + 2 de 12 | 287 × 287 | −3 … 29 |
| 20 | 2 | 8 + 12 de 12 (lleno) | 287 × 287 | −3 … 29 |
| capacidad | 3 | 36 barrios × 36 escritorios | 391 × 391 | −3 … 29 |

Radio = calle del último anillo + 8 de césped. **Escala**: con 20 proyectos ocupa 287 × 287, contra
537 × 483 del campus: el 32 % de su superficie. Es algo más que el ring (231 × 231) porque cada
barrio es un cluster con patio y no una cuña. Con 20 proyectos los dos anillos quedan exactamente
llenos y no se abre el 3.

## Luz, decoración y NPC

- **Luz ≥ 10 en toda celda caminable.** Toda la luz va en bloques empotrados:
  - luces de rana nacarada (*pearlescent froglight*) en suelos y techos, en una retícula
    `(4x + 5z) mod 41 = 0` que deja cada celda abierta a 4 pasos o menos de una luz;
  - paneles shoji iluminados en muros, claristorio y portales;
  - postes de tronco con luz arriba, en el anillo elevado y en los nodos diagonales.

  Al final, el generador recorre todo lo transitable desde el spawn con pasos de un bloque y calcula
  la luz de cada grupo de obra con **sus propias** luces (`PlanLight`). Donde una celda queda por
  debajo de 10, empotra una luz en el suelo, bajo la losa, en el suelo de al lado o en el techo. Así
  la luz de una nave no depende de sus vecinas y crecer no la cambia. **No hay linternas sueltas**:
  ni `lantern`, ni la linterna de papel ni la de jardín del kit.
- **Decoración con soporte**: macetas, azaleas y alfombras de musgo siempre sobre un bloque, letreros
  colgantes bajo un dintel y pantallas grandes exentas sobre una viga. Los arbustos no se ponen sobre
  caminos ni delante de puertas o escritorios, y las copas de los árboles empiezan en y = 4.
- **NPC**:
  - el spawn está en la calle interior sur (0, 1, 38);
  - al hub se llega por la rampa sur, el anillo elevado y el puente;
  - a los barrios, por la calle interior, las avenidas y el portal;
  - a los pisos superiores, por la escalera.

  Todas las anclas pasan por el validador real (`HqAnchorPlacementTest.assertAnchorsValid`, con la
  `Collision` del cliente).

## Tipos de sala

| Tipo | Dónde | Forma |
|---|---|---|
| Coordinación (hub) | centro | basílica con nave abovedada (12→17), tablero «Needs attention» al norte, kanban al oeste, pantalla grande al este con bancos, holograma y faro sobre la clave |
| Trabajo por proyecto | naves de los barrios | rectangulares, 1 o 2 pisos, 6 escritorios por piso |
| Revisión / PR | nodo N | torre cuadrada de 13 × 13 y 20 de alto, entreplanta, tejado piramidal y aguja con luz; pantalla `review` de 7 × 4 |
| Tareas programadas | nodo E | rotonda con cúpula y un reloj de luces en el suelo; pantalla `schedule` de 5 × 3 sobre viga |
| Archivo | nodo S | claustro mediterráneo con la terminal |
| Lounge | nodo O | patio hundido (anfiteatro hasta y = −3) alrededor de una columna de luz |
| (paseo) | patio, anillo elevado, patios de barrio, nodos diagonales | jardines, canal, columnas de luz |

**Grupos de obra**: `hub`, `cloister` (patio, puentes, anillo elevado, rampas y calle interior),
`archive`, `node_<k>`, `ring_<m>` (avenidas, calle y lotes vacíos), `lot_<id>` (patio, caminos,
jardines y portal de un barrio), `module_<índice>` (un piso de una nave) y `grounds`.

## Tests (`RingCampusLayoutTest`)

- **registro**: está en el registro con su propio estilo y paleta cherry, y los demás layouts siguen;
- **crecimiento**:
  - el anillo 1 se llena antes del 2 y los proyectos grandes densifican su barrio;
  - el orden de llenado reparte los lotes;
  - un lote liberado se rellena y un barrio nunca ocupa otro lote;
- **geometría**: en los 36 lotes de los 3 anillos, naves, portal y patio quedan dentro de su lote, sin
  solaparse, y escritorios, puestos y escalera quedan dentro de la nave;
- **determinismo e idempotencia**;
- **estabilidad bloque a bloque** al crecer de 4 a 10 y a 20 proyectos (naves, hub, núcleo, nodos y
  archivo);
- **anclas**:
  - válidas y alcanzables a pie con 4, 10 y 20 proyectos;
  - los pisos superiores se alcanzan por su escalera;
  - el hub conserva sus fijaciones;
- **envolvente**: todo dentro de la envolvente y de las alturas, cada `module_*` dentro de su lote y
  sin módulos solapados;
- **escritorios**: una pantalla 3 × 2 por escritorio, sin lámparas;
- **luz**: ≥ 10 en toda celda alcanzable desde el spawn (con la `Collision` real, sin contar el césped
  exterior);
- **linternas y decoración**: sin linternas sueltas y con la decoración apoyada.

Además, el layout pasa por los tests comunes de `HqLayoutsPlanTest`, que recorren todos los layouts
del registro. A `HqLayoutsTest.orderAndIds` solo se le añadió el id nuevo al final de la lista.

## Renders

Se generan con hqRender, en headless y sin Minecraft:

```
cd mod && ./gradlew hqRender -PhqArgs="--layout hybrid-ringcampus --projects 4,10,20 --out ../docs/research/hq-hybrids/ringcampus"
```

Con `--layout hybrid-ringcampus`, `LayoutViews` delega en `RingCampusViews`. Si no se pasa
`--projects`, usa 4, 10 y 20. Cada roster se asigna contra el anterior, así que es el mismo HQ
creciendo. El roster es el de `Growth.roster`, con 20 nombres y de 1 a 13 hilos (13 hilos ya dan un
piso superior). Los PNG de más de 480 KB se guardan con 256 colores. Para generar solo una vista,
añade `--only <subcadena>`, por ejemplo `--only _hub` o `--only compare`.

| Archivo | Cámara y corte | Qué |
|---|---|---|
| `ringcampus_compare.png` | azimut 30°, elevación 38°, la misma escala para los tres | 4 → 10 → 20 desde la misma cámara |
| `ringcampus_04p_aerial.png`, `ringcampus_10p_aerial.png`, `ringcampus_20p_aerial.png` | 30° / 38°, el plan entero | vista aérea |
| `ringcampus_04p_plan.png`, `…_10p_plan.png`, `…_20p_plan.png` | cenital, corte en y = 7 | planta |
| `ringcampus_NNp_hub.png` | 25° / 52°, x, z ∈ [−15, 15], corte en y = 13 | interior del hub: nave, arcadas, tablero, kanban, pantalla, bancos, holograma |
| `ringcampus_NNp_hub_section.png` | desde el sur (0° / 8°), x ∈ [−30, 30], corte en z = 0 | sección de la nave (12→17), naves laterales, puentes y patio hundido |
| `ringcampus_NNp_barrio.png` | 210° / 34°, `lotBounds` del barrio con más módulos | el barrio entero: portal, naves y patio |
| `ringcampus_NNp_barrio_cut.png` | 200° / 58°, el mismo barrio, corte en y = 5 | planta baja del barrio |
| `ringcampus_NNp_hall.png` | 160° / 55°, la nave 0 de ese barrio, corte en y = 5 | sala de trabajo: 6 escritorios con pantalla 3 × 2 y agentes de pie |
| `ringcampus_20p_upper.png` | 160° / 50°, la primera nave con piso superior, corte en y = 11 | piso superior y escalera |
| `ringcampus_04p_special.png` | 35° / 52°, 21 × 21 alrededor del nodo N, corte en y = 9 | torre de revisión |
| `ringcampus_10p_special.png` | igual en el nodo E, corte en y = 7 | rotonda de tareas programadas |
| `ringcampus_20p_special.png` | igual en el nodo S, corte en y = 5 | claustro del archivo con la terminal |
| `ringcampus_20p_lounge.png` | 60° / 45°, nodo O, corte en y = 6 | lounge en patio hundido |
| `ringcampus_NNp_light.png` | cenital, 3 px por bloque | heatmap de luz a nivel de paso: rojo < 10, amarillo de 10 a 12, verde ≥ 13 |

`NN` = `04`, `10` y `20`.

**Galería** (se llenará cuando el coordinador genere los PNG):

| Tamaño | Aérea | Planta | Hub | Sala de trabajo | Sala especial | Luz |
|---|---|---|---|---|---|---|
| 4 | ![](ringcampus_04p_aerial.png) | ![](ringcampus_04p_plan.png) | ![](ringcampus_04p_hub.png) | ![](ringcampus_04p_hall.png) | ![](ringcampus_04p_special.png) | ![](ringcampus_04p_light.png) |
| 10 | ![](ringcampus_10p_aerial.png) | ![](ringcampus_10p_plan.png) | ![](ringcampus_10p_hub.png) | ![](ringcampus_10p_hall.png) | ![](ringcampus_10p_special.png) | ![](ringcampus_10p_light.png) |
| 20 | ![](ringcampus_20p_aerial.png) | ![](ringcampus_20p_plan.png) | ![](ringcampus_20p_hub.png) | ![](ringcampus_20p_hall.png) | ![](ringcampus_20p_special.png) | ![](ringcampus_20p_light.png) |

Más vistas:

- `ringcampus_compare.png`, la comparativa;
- `ringcampus_NNp_hub_section.png`, la sección de la nave;
- `ringcampus_NNp_barrio.png` y `ringcampus_NNp_barrio_cut.png`, el barrio entero y su planta baja;
- `ringcampus_20p_upper.png`, un piso superior;
- `ringcampus_20p_lounge.png`, el lounge.

## Survival (pendiente)

Como ring y campus, este layout no cabe en `HqSiteFrame`, que sigue limitado a la espina; el
survival lo dibuja con classic. Para reservar terreno, el frame pediría:

1. una base circular de radio 93, que con 4 proyectos ya es la ciudadela completa;
2. una corona por anillo abierto, `[ringInner(m), streetOuter(m) + 8]`;
3. un `SiteModule` por lote, con el rectángulo `RingCampusGeometry.lotBounds`.
