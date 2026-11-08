# layout/ring — Ciudad en anillos

Estilo `ring` (`hq.style: "ring"` en `config/t3craft.json`). Es un layout propio y no reparte
la geometría de la espina (`HqGeometry`/`HqFootprints`). Cherry y classic no cambian: siguen en
`HqStyles.all()` con sus tests y su salida intactos. `ring` se elige por id (`HqStyles.byId`,
`HqStyles.layouts()`).

Código: `RingStyle` (paleta y regla de crecimiento), `RingGeometry` (geometría polar pura, zonas,
orden de sectores, escritorios y escaleras), `RingGenerator` (plan) y `RingLayoutTest`. Ganchos
aditivos: `HqLayout.Placer` (dónde va un módulo nuevo; por defecto, el índice libre más bajo de
siempre), `HqStyle.placer()`, el desvío en `HqGenerator.generate` y un índice de grupos opcional en
`BuildPlan` (para `groupAt` con miles de módulos finos).

## Idea

```
                 ring 2: 16 sectores (techo-jardín plano, muros de terracota del entorno)
            ring 1: 8 sectores (bóveda de cobre a lo largo del arco)
        calle interior · rampas por las 8 radiales · terrazas ajardinadas
     CLAUSTRO elevado (y=4): galería circular completa con arcada al patio
  PATIO HUNDIDO (y=0): jardín, canal de agua, cerezos; puentes N y S al hub (y=4)
HUB octogonal sobre podio, cúpula de cobre con nervios, faro en la linterna
```

- **Hub** (radio 13, piso y=4, cúpula hasta y=30). Pared de atención (`board`) sobre el arco norte,
  pared kanban (`thread_wall`) al oeste, pantalla grande al este con bancos enfrente, holograma con
  columna libre en el centro y faro en la linterna de la cúpula. El portal principal da al sur.
- **Patio hundido** (r 13–21, y=0), a 4 bloques bajo el podio, con canal anular, cerezos y flores. Lo
  cruzan **puentes** desde el hub, y desde la galería se baja por **escaleras** en las 4 diagonales.
- **Claustro** (r 21–28, piso y=4). Es la galería de 360° con arcada de 32 columnas, cubierta de
  pizarra y cúpulas pequeñas sobre las escaleras. Debajo hay un porche transitable a nivel del patio.
- **Avenidas radiales**. Las 8 del anillo 1 bajan de y=4 a y=0 en **rampas de medio bloque**
  (losas: valen para cualquier ángulo y el A* de los NPC las sube). Las del anillo 1 se prolongan
  por los anillos siguientes, y el anillo 2 suma 8 radiales intermedias que nacen en la calle del
  anillo 1.
- **Sectores en cuña**, uno por proyecto. Cada uno tiene puerta al arco interior con letrero
  colgante, puerta trasera a la calle del anillo y puertas laterales a las avenidas. Hay 6
  escritorios por piso en 3 filas de 2, cada uno con su pantalla 3×2 y el agente de pie, con el
  pasillo central libre. Los pisos van cada 7 bloques con escalera en zigzag al fondo (dos carriles
  alternos con hueco en la losa), y los techos son altos: el último piso llega hasta la bóveda.
- **Cuartos especiales en los nodos** donde las radiales del anillo 1 cruzan la calle del anillo
  1, distintos de forma y tamaño:
  - N: **torre de revisión/PR** (13×13, 21 de alto, entreplanta, tejado piramidal y aguja),
    pantalla `review` 7×4;
  - E: **rotonda de tareas programadas** (circular, cúpula de cobre, anillo-reloj de faroles),
    pantalla `schedule` 5×3;
  - S: **rotonda del archivo** (octogonal baja) con la terminal `t3craft:archive_terminal`;
  - O: **lounge en patio hundido**, un anfiteatro circular de medios escalones hasta y=-3 alrededor
    de una columna-farol;
  - diagonales: **plazas** con fuente y 4 árboles. En las calles exteriores, obeliscos.
- **Barrios que se distinguen**. El anillo 1 tiene paneles shoji del kit, vigas y bóveda de cobre
  con lucernario, y una banda del color del entorno bajo el alero. El anillo 2 tiene muros de
  terracota del color del entorno y techo-jardín plano con parapeto. El anillo 3 en adelante, adobe
  (mud bricks) y tejado a dos aguas de pizarra. Las parcelas libres son jardines con seto y farol.
  Se priorizan los bloques del kit (`HqKit`: suelos, paneles, vigas, faroles, macetas, barandas,
  escritorios) y hay fallback vainilla cuando el módulo de bloques está apagado.

## Reglas de crecimiento

El índice de módulo codifica `piso·100 + sector`. Los sectores se numeran anillo por anillo
(anillo 1: 0–7, anillo 2: 8–23, anillo 3: 24–47, anillo 4: 48–79). Dentro de un anillo el orden
fijo reparte los sectores por posición angular con bits invertidos (0, 4, 2, 6, 1, 5, 3, 7), así que
con 4 proyectos ya sale un molinete simétrico. `RingGeometry.place`:

1. **Proyecto nuevo**: ocupa el sector libre más bajo en el orden fijo. Cuando se llena el anillo 1
   se abre el anillo 2, y los huecos que deja un proyecto que desaparece se rellenan antes que
   cualquier sector nuevo.
2. **Proyecto grande** (más de 6 hilos): **suma un piso a su sector**, hasta 3 pisos (18
   escritorios). Si aún no basta, ocupa un **sector contiguo libre** del mismo anillo, y si no hay
   ninguno, cualquier sector libre.
3. **Estable**: `HqLayout.allocate` conserva índices y escritorios, así que lo que existe no se
   mueve. Las celdas de un sector dependen solo de su anillo y de los nodos que lo flanquean, nunca
   de cuántos anillos hay abiertos. Abrir el anillo 2 no toca el anillo 1 (el test compara celda a
   celda cada edificio, cuarto especial, el archivo y el claustro).
4. **Liberar**: un proyecto que se va libera su sector (y sus pisos), que vuelve a ser parcela
   jardín y se reutiliza.
5. Los anillos abiertos van hasta el anillo del sector más alto en uso.

**De 10 a 20 proyectos**: con 10, el anillo 1 está lleno y el anillo 2 recién abierto (2 de 16
sectores). Los 10 nuevos ocupan sectores del anillo 2 en el orden fijo; los proyectos de 7, 8, 9 y
13 hilos crecen en pisos y no gastan sectores. Con 20 proyectos (25 módulos) quedan 4 parcelas
libres en el anillo 2 y no hace falta abrir el anillo 3.

## Tamaños (envolvente, incluido el césped)

| Proyectos | Anillos | Módulos | Envolvente xz | Alto (y) | Celdas del plan |
|---|---|---|---|---|---|
| 4 | 1 | 5 | 167 × 167 | −3 … 30 | ~171 000 |
| 10 | 2 | 14 | 231 × 231 | −3 … 30 | ~326 000 |
| 20 | 2 | 25 | 231 × 231 | −3 … 30 | ~347 000 |
| (capacidad) | 4 | 80 sectores × 3 pisos | 359 × 359 | −3 … 30 | — |

Radio = `41 + 32·anillos + 10`. El anillo *m* ocupa r ∈ [41+32(m−1), 67+32(m−1)] y su calle 6 bloques más.

## Tipos de cuarto

| Tipo (roster) | Dónde | Forma |
|---|---|---|
| Coordinación (hub) | centro | octógono con cúpula sobre podio |
| Trabajo por proyecto | sectores de los anillos | cuña, 1–3 pisos, 6 escritorios por piso |
| Revisión / PR | nodo N | torre cuadrada alta |
| Tareas programadas | nodo E | rotonda circular con cúpula |
| Errores / needs attention | hub | pared `board` sobre el arco norte |
| Lounge | nodo O | patio hundido (anfiteatro) |
| Archivo | nodo S | rotonda octogonal con terminal |
| (paseo) | patio, claustro, plazas | jardines, canal, arcada |

## Construcción y survival

- **Grupos** (unidades de obra en paralelo): `hub`, `cloister`, `archive`, `node_<q>_<k>`,
  `ring_<m>` (avenidas, calle, explanadas y parcelas libres del anillo m), `module_<i>` (un piso de
  un sector) y `grounds` (césped). Cada celda pertenece al grupo de la zona de su columna (y del
  piso en los sectores). Los módulos del plan son cajas de una columna de ancho por tramo de z, sin
  solaparse, y el test comprueba que cada colocación cae dentro de una caja de su grupo y que los
  `module_*` quedan dentro de su cuña.
- **Survival (pendiente, sin cablear)**. `HqSiteFrame` sigue limitado a la espina (envolvente ≤ 64,
  4 alas), y el anillo no cabe. Para reservar terreno, el frame del anillo pediría:
  1. una base circular de radio 51 (hub, claustro, calle interior y anillo 1 con su calle y sus
     nodos), que con 4 proyectos ya es la ciudad completa;
  2. una expansión por anillo, la corona [`ringInner(m)`, `streetOuter(m)+10`], reservada cuando se
     abre ese anillo;
  3. dentro de la corona, un `SiteModule` por sector con el rectángulo `RingGeometry.sectorBounds`
     para hacer y deshacer por proyecto.

  Haría falta subir `SiteRequest.MAX_ENVELOPE` solo para este estilo (167 → 231 → 295 → 359) y
  dejar el modo survival en un anillo hasta que el jugador amplíe la parcela. `HqTarget` no se ha
  tocado: con el estilo `ring`, sus índices bajos (0–3) caen en sectores del anillo 1 y siguen
  siendo válidos.
- **NPC**: el validador de anclas (`HqAnchorPlacementTest.assertAnchorsValid`, que usa la `Collision`
  real del cliente) pasa con 4, 10 y 20 proyectos. Las anclas del tercer piso se alcanzan desde la
  entrada subiendo por rampas, galería y escaleras.

## Renders

`./gradlew hqRender -PhqArgs="--style ring --projects 4,10,20 --out ../docs/research/hq-layouts/ring"`
(opción nueva `--projects`: rosters crecientes, cada uno asignado contra el anterior). Los PNG de más
de 480 KB se guardan con 256 colores.

| Archivo | Qué |
|---|---|
| `ring_compare.png` | 4 → 10 → 20 desde la misma cámara y escala |
| `ring_04p_aerial.png`, `ring_10p_aerial.png`, `ring_20p_aerial.png` | vista aérea isométrica completa |
| `ring_04p_plan.png`, `ring_10p_plan.png`, `ring_20p_plan.png` | planta cortada a y=6 |
| `ring_*_hub.png` | interior del hub (corte y=11): kanban, pantalla grande, bancos, holograma |
| `ring_*_sector.png` | sector de trabajo, planta baja: 6 escritorios con pantalla 3×2 y agentes de pie |
| `ring_*_sector_floors.png` | el mismo sector con sus pisos |
| `ring_04p_special.png` | torre de revisión (corte y=9) |
| `ring_10p_special.png` | rotonda de tareas programadas (corte y=7) |
| `ring_20p_special.png` | lounge en patio hundido |
| `ring_20p_archive.png` | rotonda del archivo con la terminal |
| `ring_20p_cloister.png` | claustro, puentes y patio hundido |
