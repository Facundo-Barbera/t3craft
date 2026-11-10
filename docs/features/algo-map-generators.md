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
- **El pintor no cambió.** `RingCampusGenerator` / `hq.citadel.*` siguen dibujando los bloques exactamente como
  antes (mismos bloques, mismo hash, mismo `buildKey`), porque las piezas de la ciudadela están horneadas en
  ese código y reescribirlo sin compilar ni poder comparar renders era un riesgo que no valía la pena. Lo que
  hay ahora es el **modelo** (forma + huecos) en paralelo, derivado de la misma geometría. Pasar el pintado
  detrás de `PieceSource` es trabajo del catálogo de piezas, hueco a hueco.

## Islas flotantes (`floating-islands`)

Un mundo propio (carpeta `t3craft-islands`, void como el del HQ, creativo, pacífico, mediodía fijo). El HQ no
se toca; no hay forma de que los dos se mezclen (`VanillaDemoWorld.isDemoServer` compara la carpeta).

**Abrirlo:** `/t3craft islands` (lo crea la primera vez y lo abre después; desde otro mundo pide
`/t3craft islands confirm`, que guarda y sale de ese mundo), `/t3craft islands status`,
`/t3craft map list` y `/t3craft map open floating-islands`.

### Generador (`dev.t3craft.mapgen.islands`, puro): segundo corte (#31)

El detalle está en [`islands-worldgen.md`](islands-worldgen.md). En resumen:

- `IslandField implements MapGenerator<IslandField.Window>`: una isla por celda de una rejilla cuyo lado sale de
  la **mezcla de tamaños** (`IslandMix`, p. ej. `S40,M30,L20,XG10` o un preset) para que quepa su isla más grande
  con margen: 82 bloques si hay XG, 66 si lo más grande es L. La celda (0, 0) es siempre la isla **grande** de
  aparición.
- Formas orgánicas (`Island`): contorno con armónicos 2/3/5 y ruido de borde, panza cónica con estalactitas y
  raíces o puntas de espeleotema colgando, terrazas en L/XG; cinco paletas vanilla (`IslandBiome`: pradera,
  piedra/musgo, cerezo, nieve, desierto) elegidas por semilla.
- Huecos por tamaño (`IslandSlots`): `CENTRE`, `MODULE`, `DECOR` (árbol o acento) y `SPAWN`, colocados de modo
  que quepan dentro del contorno; el suelo bajo cada hueco se allana.
- Piezas reales: `IslandCatalog` rellena los huecos con variantes del catálogo de la ciudadela; `IslandProps`
  usa el catálogo y deja los props provisionales solo como respaldo.

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
- (Segundo corte, #31: las piezas ya vienen del catálogo; ver `islands-worldgen.md`.)
- Sin título/botón en el menú: se abre con comando. Sin configuración (`demo.*` no aplica a este mundo).
- Sin pruebas unitarias (el repo no las tiene); la verificación es compilar.

## Pendiente

1. Compilar en el PC: `./gradlew compileJava compileClientJava` (coordinador, con `tools/heavy.sh`).
2. Jugarlo: `/t3craft islands`; comprobar que la isla de aparición aparece antes de entrar, que al andar
   hacia un borde sale el puente hacia la vecina y que las islas lejanas se van construyendo.
3. Enganchar el catálogo de piezas a `PieceSource` (islas primero; luego el pintado del HQ hueco a hueco).

## Cambios fuera de las carpetas nuevas

- `VanillaDemoWorld` pasa a `public` y publica `exists`, `open`, `leaveWorld`, `worldRoot`, `isDemoServer`,
  `applySetup`; gana `createVoidWorld(mc, folder, name, seed, parent)` (misma receta de mundo void que el HQ).
- `HqStyle.mapLayout` / `RingCampusStyle.mapLayout` (ver arriba).
- `fabric.mod.json`: nuevo entrypoint cliente `dev.t3craft.client.islands.IslandsClient` (RESTART).
