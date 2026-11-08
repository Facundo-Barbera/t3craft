# layout/blocks — «Ensanche»: manzanas con patio

Estilo `ensanche` (`"hq": { "style": "ensanche" }` en `config/t3craft.json`). Cherry y classic no cambian: siguen con su espina compartida, y sus tests y su salida son idénticos.

![Crecimiento 4 → 10 → 20](ensanche_growth.png)

## Idea

El HQ es un trozo de ciudad. Hay una **rejilla de manzanas** separadas por calles de 10 bloques: aceras, farolas, pasos de cebra y esquinas en chaflán. No hay calle principal ni pasillo. Nada crece en línea.

- **Plaza mayor (hub).** La celda central es una plaza enlosada con fuente, plátanos, bancos, soportales y dos paseos en diagonal. Al norte está la **casa consistorial**, de 27×17 por dentro y 11 bloques de altura libre. Dentro tiene el tablero «Needs attention» en el muro norte, la pared kanban (`thread_board`, 9×4) en el muro oeste, la pantalla grande (5×3) en el muro este y bancos frente a las dos. En el centro queda la columna libre del holograma, bajo una linterna acristalada con el faro (beacon) arriba. La terminal del archivo (`archive_terminal`) está en un nicho del muro sur, junto a la puerta.
- **Cuartos especiales en esquinas y chaflanes notables**, o con manzana propia:
  - **torre del reloj** (tareas programadas) en el chaflán SE de la plaza: sala con pantalla `schedule` y dos esferas-pantalla 3×3 a 20 bloques de altura;
  - **lonja de revisión** (PR) en el chaflán SO de la plaza, con su pantalla `review`;
  - **mercado cubierto** (lounge): ocupa una manzana propia, la celda (1, 0). Tiene naves de hierro y vidrio, puestos, bancos enfrentados, maceteros y una pantalla grande;
  - **plazuelas** en las celdas (n, −n). Las cruza un **paseo diagonal** que nace en la esquina NE de la plaza mayor y atraviesa los cruces de calles. En el centro de cada una hay un quiosco de música.
- **Manzanas de trabajo.** Cuatro edificios en molinete rodean un **patio ajardinado** con anillo de paseo, cruz de caminos, pozo-fuente, árboles y bancos. Los lados alternan 11 y 13 bloques de fondo, así que las salas miden 27×9 o 29×11. Cada edificio tiene:
  - una esquina exterior en **chaflán**, que en un tercio de las parcelas sube una planta más como torreón;
  - un **pasaje cubierto** de la calle al patio en su extremo interior. Con los cuatro pasajes, los patios forman una red peatonal además de las calles. En el pasaje está la **escalera**: un tramo de la planta baja a la 1 y otro de la 1 a la 2;
  - de 2 a 4 pisos (6 bloques por planta) y cubierta a dos aguas o azotea con parapeto;
  - salas con 6 puestos contra el muro de la calle, ventanas y dos puertas al patio, pantalla grande del proyecto junto a la puerta de la escalera y luces en el techo.
- **Barrios.** Cada manzana tiene su juego de materiales: cerezo, toba y cobre, terracota, ladrillo, estuco con pizarra o arenisca. Las seis manzanas que rodean la plaza son todas distintas. Las alturas, los torreones y las cubiertas varían por parcela.

## Reglas de crecimiento

Todo es determinista e idempotente: el mismo roster da el mismo edificio, y `allocate(in, allocate(in))` es igual a `allocate(in)`. El código está en `EnsancheGrid`, `EnsancheAllocator` y `EnsancheBuilder`.

1. **Orden fijo de manzanas: espiral cuadrada** alrededor de la plaza. La celda 0 es la plaza. El anillo 1 son las celdas 1..8, empezando por el norte y en sentido horario. El anillo 2 son las celdas 9..24, y así sucesivamente. La celda 2 es plazuela y la 3 es el mercado, y ambas existen siempre.
2. **Parcelas**: cada manzana de trabajo tiene 4 (S, O, N, E), numeradas `p = 4k + lado`. Un proyecto ocupa una o varias parcelas. Cada sala (módulo del HQ) es `índice = 3p + planta`, con 6 puestos por sala.
3. **Un proyecto nuevo** toma la parcela libre más baja de la espiral. Así **se rellenan las manzanas libres y los huecos antes de mover la frontera**: con 4 proyectos de 1 sala, los cuatro comparten la manzana 1, y el quinto va a la manzana 4.
4. **Un proyecto lleno crece**, por este orden:
   1. **Hacia arriba**: la siguiente planta libre de uno de sus edificios, hasta 3 plantas de trabajo.
   2. **Alrededor de su patio**: una parcela libre de una manzana donde ya está.
   3. **Al lado**: una parcela libre de una manzana de trabajo que comparte calle con una de las suyas.
   4. Si nada de eso es posible, la parcela libre más baja de la espiral.
5. **Estable**: los puestos y las salas que existen conservan su índice, así que **ningún edificio se mueve** al sumar proyectos. Cuando cambian las salas de un edificio, solo se reconstruye ese edificio (su grupo de construcción es `parcel_<p>`). Un solar que pasa a edificio no toca nada más; como mucho, los cristales del vecino conectan con el muro nuevo.
6. **Liberar**: un proyecto sin hilos activos pierde sus salas. Un edificio sin salas deja la parcela libre, que se ve como solar ajardinado con el letrero «Parcela libre» y la reutiliza el siguiente que la necesite (regla 3). Una manzana de trabajo sin parcelas en uso se queda como «Manzana libre», cercada, con sus calles.
7. **Qué se construye**: todas las celdas de la 0 a la `maxCell`, que es la más alta usada (como mínimo, la 3). Como cada celda de la espiral toca una anterior, el conjunto siempre está conectado por calles.

**De 10 a 20 proyectos** (render): con 10, se usan las celdas 0..5. La plaza, la manzana norte (cerezo), la plazuela, el mercado, la SE (toba) y la S (terracota) forman una L de 2×3. Los proyectos nuevos rellenan las parcelas libres de esas manzanas y luego ocupan la SO, la O y la NO, en el orden de la espiral. Con 20, el barrio es de **3×3 manzanas: cuatro calles que se cruzan, la plaza mayor, una plazuela con su diagonal y el mercado**. No queda ninguna avenida larga. El proyecto de 20 hilos necesita 4 salas: llena las 3 plantas de su edificio y la cuarta va a otra parcela, por las reglas 4.2 a 4.4.

## Tamaños

| Proyectos | Salas | Puestos | Celdas | Envolvente (x × z) | Altura máx. |
|---|---|---|---|---|---|
| 4  | 6  | 22  | 0..3 | 110 × 110 (x −27..82, z −82..27) | y 34 (pináculo de la torre del reloj) |
| 10 | 17 | 70  | 0..5 | 110 × 165 (x −27..82, z −82..82) | y 34 |
| 20 | 29 | 115 | 0..8 | 165 × 165 (x −82..82, z −82..82) | y 34 |

Una celda mide 55 × 55: una manzana de 45 más media calle de 5 por cada lado. Con 21 a 24 manzanas en uso, el siguiente paso abre el anillo 2 y la envolvente pasa a 275 × 275. `EnsancheGrid.envelope(maxCell)` da la envolvente para cualquier tamaño.

**Terreno en survival.** El site actual (`HqSiteFrame`, envolvente ≤ 64, 4 alas) sigue siendo el del estilo de espina, y este estilo no lo usa. Para usarlo en survival, el site reservaría **por celdas en el orden de la espiral**:
- una petición base con la celda 0 más las celdas 1..3 (110 × 110);
- cada celda nueva como un módulo de site de 55 × 55 (`EnsancheGrid.cellArea(k)`), con su propia instantánea y deshacer;
- las parcelas (`parcelArea(p)`) como subáreas dentro de su celda.

Hay que subir el límite de envolvente solo para este estilo (por ejemplo, 3 × 3 celdas = 165) o pedir las celdas a medida que la espiral las alcanza. Todos los grupos de construcción caben en su área. Lo comprueba el test `everythingStaysInsideItsFootprints`.

## Tipos de cuarto

| Tipo (roster) | Dónde | Detalle |
|---|---|---|
| Trabajo por proyecto | salas de los edificios de manzana | 6 puestos de pie con pantalla 3×2 (`Workstation.screenOriginFor` / `screenCells`), sin lámparas y sin sillas; pantalla grande `project:<clave>` |
| Coordinación (hub) | casa consistorial, plaza mayor | tablero, pared kanban, pantalla grande `env:`, holograma con columna libre, faro, bancos |
| Errores / needs attention | tablero de la casa consistorial | 4 bombillas del tablero y 14 puntos de espera |
| Revisión / PR | lonja del chaflán SO | pantalla `review`, estanterías |
| Tareas programadas | torre del reloj, chaflán SE | pantalla `schedule` y esferas 3×3 en dos fachadas |
| Lounge | mercado cubierto (celda 1,0) | bancos, maceteros, puestos, pantalla `lounge` |
| Archivo | nicho de la casa consistorial | una `t3craft:archive_terminal`, nada de estanterías por hilo |

Bloques: los suelos de las salas, los paneles iluminados del techo, las mesas, los faroles, los maceteros y las linternas del jardín salen de `HqKit`, que da bloques propios con el módulo blocks y vainilla sin él. Los fixtures (pantallas, tablero, faro, terminal, kanban) son los sustitutos de la paleta, y el módulo blocks los cambia por anclas, como en cherry. Las texturas no cambian.

## Arquitectura

- `HqStyle.town()` (por defecto `null`) es el gancho aditivo: si un estilo devuelve un `TownLayout`, `HqLayout.allocate(input, prev, style)` y `HqGenerator.generate` le pasan el trabajo. Cherry y classic devuelven `null` y siguen por el camino de siempre. El estante del archivo se extrajo a `HqLayout.shelve` sin cambiar su lógica.
- `HqStyles.towns()` lista los estilos con plano propio. `HqStyles.all()` (los de geometría compartida) no cambia, y `byId`/`parse` encuentran los dos tipos.
- El `HqLayout` y su JSON, los nombres de ancla, los `Workstation` y los fixtures son los mismos, así que los NPC, las pantallas, el módulo blocks y los builders no cambian. Hay anclas nuevas, todas `walk_*` y de pie: `walk_clock`, `walk_review`, `walk_lounge_0..3` y `walk_plazuela_<k>`. Las pantallas nuevas son `bigscreen_clock_n/clock_w/schedule/review/lounge/module@<i>`.
- NPC (A\* sobre bloques reales): las anclas de pie se validan con `PlanWalkability`, el validador del cliente. Tienen que estar libres, con suelo y alcanzables desde el spawn, en la calle al sur de la plaza, a pie por calles, pasajes, patios, puertas y escaleras. Se comprueba con 4, 10 y 20 proyectos y con salas en las plantas 0, 1 y 2.

## Tests (`EnsancheLayoutTest`)

Espiral y conectividad, parcelas más patio que llenan la manzana, orden de crecimiento (arriba → patio → al lado), relleno antes de la frontera, liberar y reutilizar, determinismo, idempotencia, orden del roster y JSON, **estabilidad de 4 → 10 → 20** (salas, puestos y bloques de los edificios sin cambios), **anclas válidas y alcanzables con 4, 10 y 20**, plantas altas por las escaleras, **todo dentro de su footprint** y **pantallas 3×2 sin lámparas**. `./gradlew build` da BUILD SUCCESSFUL con 1236 tests y 0 fallos.

## Renders

`./gradlew hqRender -PhqArgs="--style ensanche --out ../docs/research/hq-layouts/blocks"` (`TownRender`). Cada tamaño es el anterior con proyectos añadidos, partiendo del layout previo.

| Vista | 4 proyectos | 10 proyectos | 20 proyectos |
|---|---|---|---|
| Aérea isométrica (SE) | [4p](ensanche_4p_aerial.png) | [10p](ensanche_10p_aerial.png) | [20p](ensanche_20p_aerial.png) |
| Aérea (NO) | [4p](ensanche_4p_aerial_nw.png) | [10p](ensanche_10p_aerial_nw.png) | [20p](ensanche_20p_aerial_nw.png) |
| Planta cortada (y=3) | [4p](ensanche_4p_plan.png) | [10p](ensanche_10p_plan.png) | [20p](ensanche_20p_plan.png) |
| Hub (casa consistorial, cortada) | [4p](ensanche_4p_hub.png) | [10p](ensanche_10p_hub.png) | [20p](ensanche_20p_hub.png) |
| Plaza: torre del reloj, lonja, soportales | [4p](ensanche_4p_plaza.png) | [10p](ensanche_10p_plaza.png) | [20p](ensanche_20p_plaza.png) |
| Sala de trabajo (pantallas 3×2, agentes de pie) | [4p](ensanche_4p_room.png) | [10p](ensanche_10p_room.png) | [20p](ensanche_20p_room.png) |
| Sala del piso 1 y escalera del pasaje | [4p](ensanche_4p_room_floor1.png) | [10p](ensanche_10p_room_floor1.png) | [20p](ensanche_20p_room_floor1.png) |
| Mercado cubierto (lounge) | [4p](ensanche_4p_market.png) | [10p](ensanche_10p_market.png) | [20p](ensanche_20p_market.png) |
| Una manzana: patio, pasajes, 4 edificios | | | [20p](ensanche_20p_manzana.png) |
| Plazuela en el paseo diagonal | | | [20p](ensanche_20p_plazuela.png) |
| **Comparativa 4 → 10 → 20 (misma cámara y escala)** | | | [growth](ensanche_growth.png) |

Las salas de trabajo y del piso 1 salen iguales en los tres tamaños porque son la misma sala del primer proyecto, que no se mueve al crecer el HQ. Los PNG de más de 480 KB se guardan con una paleta de 256 colores.

Límites conocidos:
- el render dibuja la vasija decorada como una caja;
- las salas de las plantas que el proyecto aún no usa quedan vacías, con luz y sin puestos;
- el modo survival necesita la reserva por celdas descrita arriba, que no está implementada en esta rama.
