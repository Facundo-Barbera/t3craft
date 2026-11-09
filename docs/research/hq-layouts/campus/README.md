# Layout «campus de barrios» (`hq.style = campus`)

> Las imágenes de esta investigación (PNG de renders) se quitaron del árbol el 2026-11-09 para aligerar los worktrees. Siguen en el tag `archive/research-renders-2026-11-09`: `git show archive/research-renders-2026-11-09:<ruta>.png > /tmp/x.png`.

Rama `layout/campus`. Estilo nuevo con **layout propio** (`CampusStyle`, `CampusGeometry`,
`CampusLayout`, `CampusGenerator`, `CampusFootprints`). Es aditivo: cherry y classic no cambian
(siguen en `HqStyles.all()` con sus tests intactos; el campus está en `HqStyles.layouts()` y se elige
con `"hq": { "style": "campus" }`).

## Idea

No hay espina ni pasillo. El HQ es un **campus sobre una rejilla hexagonal**:

- **Centro: la plaza y el hub.** Un salón de 27 × 27 con muros de 11 bloques, tejado escalonado,
  tambor acristalado y linterna con el faro (y = 25) sobre la columna libre del holograma. Dentro:
  el tablero «Needs attention» (muro norte, 5 bombillas, 18 puestos de pie), la pared kanban
  `thread_board` 9 × 4 (oeste), la pantalla grande 7 × 4 (este) y bancos frente a ambas. La plaza
  (49 × 49) tiene fuentes, árboles y farolas.
- **Pabellones especiales** en los seis vértices del hexágono del hub, cada uno con forma propia:
  archivo (rotonda con la terminal `archive_terminal` en el centro), revisión/PR (octógono con
  pantalla 5 × 3), tareas programadas (torre de 18 bloques con «reloj», una pantalla 3 × 3 en la
  fachada), errores/needs attention (planta en cruz, tejado rojo, faro de alerta), lounge (L abierta
  con bancos, fogata y estanque) y la **puerta de entrada** (torii, estanques, spawn).
- **Barrios**, uno por proyecto, en las celdas de la rejilla. Cada barrio (49 × 49) es un patio con
  fuente o árbol central, cuatro accesos con pórtico y un seto alrededor; en las cuatro esquinas hay
  parcelas: la **casa común** (siempre: pirámide alta, aguja con la bandera del color del entorno,
  pantalla grande del proyecto, placa) y hasta **tres naves de trabajo** de 19 × 13, 19 × 15 y
  19 × 17 (6 escritorios de pie con pantalla 3 × 2, sin sillas ni lámparas), cada una con su tipo de
  tejado (a dos aguas, a cuatro aguas, diente de sierra o terraza verde). Cada nave puede **subir un
  piso** con escalera exterior. Cada barrio tiene una paleta (cerezo, terracota, cobre, prismarina,
  resina, pálido) y una rotación propias, así que dos barrios vecinos nunca se parecen.
- **Red de caminos**: los caminos siguen las aristas de los hexágonos (no hay eje), con una plaza en
  cada cruce (fuente, árbol o farola). Entre dos puntos hay siempre varias rutas. En las aristas
  norte–sur hay **estanques con puentes** o **pérgolas** con flores; en el resto, árboles y farolas.
  Las parcelas libres del anillo interior son **parques** (lago con isla, paseo circular), así que
  con 4 proyectos el campus ya se ve lleno.

## Reglas de crecimiento

Índice de módulo = `parcela × 8 + edificio` (el `HqLayout` y su JSON no cambian).

1. **Orden fijo de parcelas.** Espiral por anillos alrededor del hub. Dentro de cada anillo van
   primero las seis esquinas alternando lados opuestos (E, O, NE, SO, NO, SE) y luego las celdas
   intermedias igual, para que un anillo a medias quede equilibrado. Anillo 1 = 6 parcelas, anillo 2
   = 12 y anillo 3 = 18.
2. **Proyecto nuevo** (en orden de clave) → **la parcela libre más baja**. Así las parcelas liberadas
   se reutilizan primero y un anillo se llena antes de abrir el siguiente.
3. **Estable.** Un proyecto conserva su parcela mientras tenga un hilo activo, y un hilo conserva su
   nave y su escritorio. Nada existente se mueve: lo único nuevo son las parcelas y caminos añadidos
   (lo comprueba un test bloque a bloque).
4. **Densificar, no expandir.** Los hilos nuevos ocupan el escritorio libre más bajo de las naves de
   su proyecto. Cuando están llenas se agrega la siguiente nave **de su propio barrio**: naves 0–2 en
   planta y 3–5 como segundo piso de esas mismas naves (6 × 6 = 36 escritorios por barrio). Un
   proyecto nunca abre otro barrio. Si se supera ese máximo, los hilos esperan sin escritorio, igual
   que en un site lleno.
5. **Liberar.** Un proyecto sin hilos activos libera su parcela, que pasa a ser parque (si queda
   dentro de los anillos abiertos) y la vuelve a ocupar el próximo proyecto que llegue. Una nave sin
   hilos desaparece; si su piso superior sigue ocupado, se mantiene la carcasa de la planta baja.
6. **Caminos.** Se dibujan las aristas de todas las celdas presentes (hub, barrios, parques). Cada
   arista pertenece a la primera celda presente que la tiene, así que un barrio nuevo solo añade
   caminos. Los árboles y la decoración nunca pisan terreno que algún día pueda necesitar un camino o
   una parcela (`CampusFootprints.reserved`).

**De 10 a 20 proyectos.** Con 10 ya está lleno el anillo 1 (6) y 4 parcelas del anillo 2 (las
esquinas E, O, NE y SO). Al llegar a 20 se completa el anillo 2 (12) y se abren las dos primeras
esquinas del anillo 3 (E y O). En el anillo 2, las parcelas que quedan libres mientras se completa
se convierten en parques. El proyecto de 26 hilos llena sus tres naves y sube dos pisos, siempre en
su barrio.

## Tamaños

| Proyectos | Anillos | Envolvente reservada (`CampusFootprints.envelope`) | Altura |
|---|---|---|---|
| 4 | 1 | 249 × 231 | 0..30 |
| 10 | 1–2 | 393 × 357 | 0..30 |
| 20 | 1–3 | 537 × 483 (lo construido es más corto en z: del anillo 3 solo usa las esquinas E y O) | 0..30 |

Las zonas fijas son: hub y plaza 49 × 49, pabellones 21 × 21, barrios 49 × 49 y una franja de 9 a
12 bloques de jardín y camino entre parcelas. Las naves tienen techos de 6 bloques; el hub, un atrio
de 24.

**Survival** (propuesta, sin conectar): el límite de 64 bloques del site solo sigue aplicando a los
estilos de espina. Para el campus habría que reservar `envelope(n)`, construir como `base` el hub,
los pabellones y la red del anillo 1, y después hacer una expansión de site por parcela (su
cuadrado 49 × 49 más su grupo `grounds_<p>`). Hasta entonces, el campus es un layout de demo o
creativo.

## Tipos de cuarto

| Tipo | Dónde | Anclas |
|---|---|---|
| Coordinación (hub) | centro | `board*`, `thread_wall*`, `bigscreen_hub…`, `bench_*`, `walk_hub_*`, `hub`, `hologram`, `beacon`, `entrance` |
| Trabajo por proyecto | naves del barrio | `desk_<hilo>`, `monitor_<hilo>`, `desk@i.s`, `module@i`, `walk@i` |
| Casa común del proyecto | lote 0 de cada barrio | `wing_<proyecto>`, `walk_<proyecto>`, `bigscreen_barrio@p…=project:<clave>` |
| Revisión / PR | pabellón NE | `walk_room_review[_i]`, `bigscreen_review…` |
| Tareas programadas | torre NO | `walk_room_scheduled[_i]`, `bigscreen_scheduled…`, `bigscreen_clock…` |
| Errores / needs attention | cruz SE | `walk_room_errors[_i]`, `bigscreen_errors…` |
| Lounge | L SO | `walk_room_lounge[_i]` |
| Archivo | rotonda N | `archiveTerminal`, `archive`, `walk_room_archive[_i]` |
| Entrada | puerta S | `spawn` |

## Infraestructura añadida (aditiva)

- `HqStyle.allocate / ownLayout / layoutPlan`: gancho de layout. Por defecto se comporta igual que
  antes. `HqGenerator`, `HqPlans` y el render lo usan.
- `PlanBuilder.sparse` y `BuildPlan.Module.cells`: módulos **dispersos**, dueños solo de las celdas
  que escriben (caminos, patios, escaleras exteriores), sin rellenar de aire su caja. Los estilos de
  espina no los usan.
- `hqRender`: opción `--projects 4,10,20` (roster de N proyectos con hilos variados, tres entornos e
  hilos archivados), vistas del campus y comparativa. Los PNG de más de 480 KB se guardan con 256
  colores.

Reproducir: `cd mod && ./gradlew hqRender -PhqArgs="--style campus --out build/hq-render/campus"`.

## Tests (`CampusLayoutTest`, 16)

Cubren: determinismo, idempotencia e independencia del orden del roster; espiral de parcelas y un
barrio por proyecto; estabilidad bloque a bloque al pasar de 4 a 10 y a 20 proyectos y al sumar
hilos; liberar y reutilizar una parcela; anclas libres, pisables y alcanzables a pie desde el spawn
con 4, 10 y 20 proyectos (validador `HqAnchorPlacementTest.assertAnchorsValid`), también con pisos
superiores y con una planta baja vaciada; todo dentro de su footprint, sin solaparse y con la red
fuera de las parcelas; una pantalla 3 × 2 por escritorio sin lámparas; fixtures del hub; bloques
vainilla parseables; y módulos dispersos.

## Renders

| Tamaño | Aérea | Planta cortada | Hub | Nave de trabajo | Cuarto especial |
|---|---|---|---|---|---|
| 4 | ![](campus_4p_aerial.png) | ![](campus_4p_plan.png) | ![](campus_4p_hub.png) | ![](campus_4p_hall.png) | ![](campus_4p_review.png) |
| 10 | ![](campus_10p_aerial.png) | ![](campus_10p_plan.png) | ![](campus_10p_hub.png) | ![](campus_10p_hall.png) | ![](campus_10p_archive.png) |
| 20 | ![](campus_20p_aerial.png) | ![](campus_20p_plan.png) | ![](campus_20p_hub.png) | ![](campus_20p_hall.png) | ![](campus_20p_lounge.png) |

- `campus_compare.png`: 4 → 10 → 20 desde la misma cámara y a la misma escala, generados en orden
  (cada uno a partir del layout anterior).
- `campus_20p_barrio.png` y `campus_20p_barrio_cut.png`: el barrio más poblado, con tres naves, pisos
  superiores con escalera exterior, casa común y patio.
- `campus_20p_specials.png`: la plaza y los seis pabellones.
