# feat/algo-map-generators: generadores algorítmicos de mapa

Los mapas dejan de ser un edificio horneado: un **generador** define la **forma** del terreno y los
**huecos (slots)** donde van las piezas; las piezas (edificio central, módulos, árboles, farolas...) las
elige después una `PieceSource` (el catálogo de piezas, que vive en otra rama y no se espera aquí).

Esta rama trae la interfaz, el HQ descrito en ese modelo y un tipo de mapa nuevo, **islas flotantes**.
El mundo HQ sigue siendo el predeterminado y no cambia.

> **Sin compilar ni jugar.** Esta rama se escribió sin compilar ni lanzar Minecraft (reglas de la VPS).
> Falta la compilación en el PC (`compileJava compileClientJava`) y una prueba en juego: ver "Pendiente".

## Interfaz (`dev.t3craft.mapgen`, source set `main`, puro)

| tipo | qué es |
|---|---|
| `MapGenerator<P>` | `id()` y `MapLayout generate(P params)`. Puro y determinista: sin mundo, sin reloj, sin azar fuera de `Noise` con la semilla. |
| `MapLayout` | resultado: `generatorId`, `seed`, `TerrainShape shape`, `List<Slot> slots`, `List<Link> links`, `spawn`. Solo datos, sin bloques. |
| `TerrainShape` | el terreno como función de la columna: `bounds()` y `column(x, z)` → `Span(bottom, top)` o `null` (vacío). |
| `Slot` | un hueco: `id` estable, `Role`, `origin` (capa sobre el suelo), `facing`, `halfAcross`, `halfDeep`, `height`, `group` (isla, lote), `parent`, `tags`. |
| `Slot.Role` | `CENTRE`, `MODULE`, `NODE`, `GATE`, `PLAZA`, `DECOR`, `SPAWN`. |
| `Link` / `LinkKind` | unión entre grupos: `SOLID` (ya se camina) u `ON_DEMAND` (no hay camino hasta que el jugador se acerca: puente). |
| `Piece` | pieza colocable: `id`, `Size(across, deep, height)` y bloques en el **marco de pieza**. |
| `PieceSource` | `pieceFor(Slot, MapLayout)`: lo que implementa el catálogo. Devuelve `null` para dejar el hueco vacío. |
| `SlotPlacer` | `place(slot, piece)` gira la pieza al `facing` del hueco y la mueve a su `origin`; `fill(map, source)` rellena todos los huecos. |
| `MapTypes` | registro de tipos de mapa (`hybrid-ringcampus`, `floating-islands`) con el comando que abre cada uno. |

**Marco de pieza** (lo que el catálogo debe respetar): `(0,0,0)` es el `origin` del hueco, o sea la capa justo
encima del suelo (una pieza puede escribir `y = -1` para sustituir la superficie); `+x` este, `+z` sur; el
**frente** (la puerta) mira al **norte**. `SlotPlacer` lo gira hacia el `facing` del hueco. La huella va
centrada: una pieza de ancho `across` cubre `x ∈ [-(across-1)/2, across/2]`. Una pieza cabe si
`size ≤ (2·halfAcross+1, 2·halfDeep+1, height)` (`Slot.fits`).

## El HQ en ese modelo

- `hq.CitadelMapGenerator implements MapGenerator<HqLayout>` lee la **misma geometría** que usa el pintor
  (`RingCampusGeometry`): la forma son las zonas (`RingCampusGeometry.zone`) y los huecos son
  `hub` (CENTRE), `node_0..7` (NODE), y por cada lote abierto `lot_n` (MODULE), `lot_n_court` (PLAZA),
  `lot_n_gate` (GATE) y `lot_n_hall_0..2` (MODULE, hijos del lote), más `spawn`. Un hueco lleva el tag
  `occupied` si un módulo del roster está en él. Los lotes se unen al hub con enlaces `SOLID`.
- Gancho: `HqStyle.mapLayout(HqLayout)` (por defecto `null`); `RingCampusStyle` lo implementa.
- **El pintor, por defecto, no cambió.** Con `mapgen.citadel.pieces = "legacy"` (el predeterminado)
  `RingCampusGenerator` / `hq.citadel.*` dibujan los bloques exactamente como antes (mismos bloques, mismo hash,
  mismo `buildKey`). Con `"slots"` algunos huecos se llenan a través de `PieceSource`: ver la sección siguiente.
- Los huecos de sala y de portal (`roomSlot`) ya no pasan una fila más allá del muro del fondo cuando la sala
  tiene fondo par (`halfDeep = (d - 1) / 2`).

## La ciudadela por huecos (`mapgen.citadel.pieces`, issue #37)

**Clave** (aditiva, solo lectura): `config/t3craft.json` → `"mapgen": {"citadel": {"pieces": "legacy" | "slots"}}`.
Predeterminado `"legacy"`. Se lee en cada plan, así que cambiarla es RELOAD: el siguiente plan ya la usa (sin
reiniciar). La propiedad `-Dt3craft.citadel.pieces=legacy|slots` gana al archivo (útil para renders sin Fabric).
Un valor desconocido es `legacy` con un aviso en el log.

**Cómo funciona** (`hq.citadel.CitadelSlots`): con `"slots"`, el generador calcula también el `MapLayout` de la
ciudadela (`CitadelMapGenerator`) y, en los sitios de abajo, el pintor pide la pieza a la `PieceSource` del hueco
(`CitadelSlots.source()`, por defecto `CitadelPieceSource`; el catálogo real se enchufa con
`CitadelSlots.use(source)`). Le pasa el hueco movido a su propio marco (pinta plano, `y = 1`; el relieve sube cada
terraza al final) y recortado a su **caja libre**, que puede ser menor que el hueco del mapa. Si la fuente no tiene
nada que quepa, se pinta la pieza de siempre (camino viejo). Si la pieza taparía una columna de ancla, el spawn,
una sala construida o una celda reservada, se vuelve a pedir con la caja un bloque menor por lado, hasta una sola
celda, y si no hay suerte, camino viejo. Si la pieza cruza un escalón del relieve, también camino viejo. La pieza se
sube con su terraza como un bloque (`CitadelCanvas.rigid`), igual que las del catálogo.

| hueco | dónde | caja que se pide | familias (en orden; la primera sale de la semilla) | en vez de |
|---|---|---|---|---|
| `lot_n_court` (`PLAZA`) | centro del patio de cada barrio | `±s(1)` (5×5 a escala 2), alto `sy(8)`; reserva las anclas del lote (ala y paseo en `(2, 2)`) | fuente, pozo, estatua, árbol | árbol, estanque, fuente o cenador del centro; los cuadrantes y los faroles de las esquinas se quedan |
| `lot_n_hall_h` (`MODULE`, sin `occupied`) | sitio de una sala aún no construida | el interior de la sala, frente al patio, alto `2·floorH` | casa, cenador, anexo, jardín | el jardín (o la casa de la ciudadela densa); el césped, el musgo y los arbustos de las esquinas se quedan |
| `lot_n` (`MODULE`, sin `occupied`) | centro de un lote vacío | tamaño del patio (`±court`), alto `sy(12)`; solo con `catalog.enabled = 1` (el predeterminado) | centro de plaza, fuente, cenador, jardín | nada: luego `CitadelPlacement` compone el resto del lote alrededor (solo usa suelo blando libre) |

Siempre por el camino viejo, en los dos modos: `hub` (`CENTRE`), `node_0..7` (`NODE`), `lot_n_gate` (`GATE`),
las salas `occupied` (mesas, pantallas, anclas), los lotes `occupied` y `spawn`. `CitadelPieceSource` devuelve
`null` para todos ellos, y también para cualquier hueco con el tag `occupied`. Un hueco con el tag
`family:<familia>` (`family:fountain`) solo acepta esa familia.

**Qué pieza** (`CitadelPieceSource`): para cada familia, cada variante del catálogo (`Catalog.of`) con peso > 0
recibe la caja más grande de su rango que quepa en el hueco (primero el frente, luego el fondo; el alto es el del
hueco). Entre las que caben elige por peso con la semilla del hueco. La variante se construye sola en un
`CitadelCanvas` aparte, con el frente al norte y centrada en el origen (el marco de pieza), y vuelve como
`mapgen.Piece`. El pintor la gira y la coloca con `SlotPlacer.place`, y deduce la capa de cada bloque, porque
`Piece` no la lleva: faroles colgados y carteles de techo cuelgan; carteles de pared, estandartes y antorchas de
pared van después de los sólidos.

**Determinismo:** la semilla es `PieceSource.seedFor(slot, map)`: semilla del mapa (0 en la ciudadela), columna
`x`, `z` del origen del hueco e id del hueco. No usa la altura, así que un hueco conserva su semilla cuando el
relieve lo sube. El orden de los huecos es el del pintor. Mismo roster y misma configuración, mismo plan.

**Presupuesto por tick:** no cambia. Esto solo cambia qué celdas tiene el plan, y los constructores lo colocan con
su presupuesto de siempre.

**Interfaz compartida (aditivo):** `PieceSource.seedFor(Slot, MapLayout)` (método estático nuevo; `pieceFor` no
cambia). Nada más en `mapgen`.

**Compararlo en el juego:** poner `"slots"` (o `"legacy"`) en `config/t3craft.json` y, dentro del mundo HQ,
`/t3craft demo rebuild` (vuelve a generar el plan y lo construye encima). Para una comparación limpia, sin restos
del otro modo: `/t3craft debug reset-hq` (+ `confirm`). Sin tocar el mundo: `/t3craft piece reload` y luego
`/t3craft piece show hq_barrio_small` (o `hq_barrio_large`, `hq_ring_1`).

## Islas flotantes (`floating-islands`)

Un mundo propio (carpeta `t3craft-islands`, void como el del HQ, creativo, pacífico, mediodía fijo). El HQ no
se toca; no hay forma de que los dos se mezclen (`VanillaDemoWorld.isDemoServer` compara la carpeta).

**Abrirlo:** `/t3craft islands` (lo crea la primera vez y lo abre después; desde otro mundo pide
`/t3craft islands confirm`, que guarda y sale de ese mundo), `/t3craft islands status`,
`/t3craft map list` y `/t3craft map open floating-islands`.

### Generador (`dev.t3craft.mapgen.islands`, puro)

- `IslandField implements MapGenerator<IslandField.Window>`: una isla por celda de una rejilla de 64 bloques.
  El tamaño sale del hash de la celda: **pequeña** (radio 5, 40 %), **mediana** (9, 30 %), **grande** (14, 20 %)
  y **extra grande** (20, 10 %). El centro se desplaza hasta ±10 bloques dentro de la celda, la altura varía
  ±4 sobre y=64. La celda (0, 0) es siempre la isla mediana de aparición.
- Las islas nunca se tocan: cada una queda dentro de su celda con un margen de 6 bloques (el contorno ondula
  con el ángulo, hasta ±12 %), así que entre vecinas hay al menos 12 bloques de vacío. **No hay suelo entre
  islas**: dos islas de celdas vecinas (4 direcciones) solo están *enlazadas* (`ON_DEMAND`).
- Forma: contorno circular que ondula con el ángulo; el fondo se afina en cono con algo de ruido. Bloques en
  `IslandBlocks` (césped, dos de tierra, piedra con vetas de toba, fondo de toba).
- Huecos por tamaño: pequeña, un árbol; mediana, centro + árbol; grande, centro mayor + 2 módulos + 2 árboles;
  extra grande, centro mayor + 4 módulos en los ejes + 4 árboles en las diagonales; la isla de aparición
  añade `spawn`.
- `IslandProps` es la `PieceSource` provisional (árbol, pabellón, farola sobre losa) para que el primer corte
  tenga algo en las islas; el catálogo la sustituirá.

### Puentes en tiempo real (`BridgePlanner`, `IslandsRuntime`)

- `BridgePlanner.plan(a, b)`: línea recta entre centros, recorrida a un bloque por paso sobre el eje largo;
  solo se coloca cubierta en las columnas fuera de ambas islas. Cubierta de 3 de ancho (abeto al centro, roble
  oscuro a los lados), a la altura del césped de la primera isla, subiendo o bajando de bloque en bloque hasta
  la de la otra; un poste de valla con ménsula cada tres pasos y un farol en cada segundo poste. Un puente por
  pareja (`b:<idA>|<idB>`, ids ordenados), venga el jugador de donde venga.
- `IslandsRuntime` (hilo del servidor, solo mientras el servidor corre la carpeta `t3craft-islands`):
  - **Islas**: cada segundo se encolan las islas a ≤ 140 bloques de un jugador que el mundo aún no tiene, la
    más cercana primero, y se construyen 1500 bloques por tick, solo en chunks cargados. La isla de aparición
    se construye de golpe antes de que entre el jugador (y fija el spawn del mundo sobre ella).
  - **Puentes**: cuando un jugador de pie en una isla construida se acerca a ≤ 9 bloques del punto donde sale
    un puente hacia una vecina, ese puente se construye **desde ese borde hacia la otra isla**, 2 secciones por
    tick (se ve crecer), la isla de destino pasa al frente de la cola y sale un aviso en la barra de acción.
  - **Caídas**: quien cae por debajo de todas las islas (y no vuela, planea, va montado ni es espectador)
    vuelve al último sitio donde estuvo en suelo firme (o a la isla de aparición).
- `IslandsState` guarda en `t3craft-islands.json` (junto a `level.dat`) las islas y puentes ya construidos.
  El campo es determinista, así que es lo único que hay que recordar: lo que está en el archivo no se vuelve
  a construir (las ediciones del jugador se respetan); lo que no (nunca construido, o cortado a medias) se
  construye de nuevo entero. Colocar es idempotente.

## Primer corte: límites conocidos

- Los puentes son rectos, sin barandilla continua (postes cada tres pasos) y sin escalones suaves: el desnivel
  máximo entre islas es 8, así que a veces hay que saltar.
- Solo se enlazan las 4 vecinas de celda; no hay islas aisladas ni más de un puente por borde.
- Las piezas son las provisionales de `IslandProps`; `PieceSource` es el punto de enchufe del catálogo.
- Sin título/botón en el menú: se abre con comando. Sin configuración (`demo.*` no aplica a este mundo).
- Sin pruebas unitarias (el repo no las tiene); la verificación es compilar.

## Pendiente

1. Compilar en el PC: `./gradlew compileJava compileClientJava` (coordinador, con `tools/heavy.sh`).
2. Jugarlo: `/t3craft islands`; comprobar que la isla de aparición aparece antes de entrar, que al andar
   hacia un borde sale el puente hacia la vecina y que las islas lejanas se van construyendo.
3. Enganchar el catálogo de piezas a `PieceSource` (islas primero). La ciudadela ya pinta patios, sitios de sala
   libres y centros de lotes vacíos por huecos con `mapgen.citadel.pieces = "slots"` (#37); falta probarlo en el
   juego, y quedan por pasar el hub, los nodos, los portales y las salas.

## Cambios fuera de las carpetas nuevas

- `VanillaDemoWorld` pasa a `public` y publica `exists`, `open`, `leaveWorld`, `worldRoot`, `isDemoServer`,
  `applySetup`; gana `createVoidWorld(mc, folder, name, seed, parent)` (misma receta de mundo void que el HQ).
- `HqStyle.mapLayout` / `RingCampusStyle.mapLayout` (ver arriba).
- `fabric.mod.json`: nuevo entrypoint cliente `dev.t3craft.client.islands.IslandsClient` (RESTART).
