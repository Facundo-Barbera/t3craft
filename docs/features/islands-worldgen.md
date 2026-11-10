# feat/islands-worldgen: el generador de islas, segundo corte (#31)

Segundo corte del **generador** del mapa de islas flotantes (`floating-islands`, ver
[`algo-map-generators.md`](algo-map-generators.md)). Trae islas con formas orgánicas, tamaños S/M/L/XG que se
pueden configurar, una semilla y una mezcla por mundo, y huecos que se llenan con piezas **reales** del catálogo de
la ciudadela. Los puentes los lleva otro hilo (#32): este no toca `Bridge`, `BridgePlanner` ni `IslandsRuntime`.
El mundo HQ y el pintor de la ciudadela no cambian; las islas siguen siendo un tipo de mapa aparte.

> **Sin compilar ni jugar.** Escrito en la VPS sin Gradle ni Minecraft. Falta compilar en el PC
> (`compileJava compileClientJava`, el coordinador, con `tools/heavy.sh`) y probarlo en juego (ver "Probarlo").

## Formas (`Island`, `IslandNoise`)

Todo sale de la semilla y de la celda, sin `Random` ni reloj.

- **Contorno.** El radio cambia con el ángulo según tres armónicos: el 2 da un óvalo, el 3 y el 5 dan lóbulos.
  Las amplitudes suman como mucho un 25 % del radio. Encima va un **ruido de borde** fino de dos octavas
  (`IslandNoise.signed2`, escala de 5 bloques) de hasta `1 + 0,06·R` bloques, así que el borde sale mordido y
  ninguna isla es un disco. La isla de aparición tiene la mitad de amplitud, para que su plaza quede holgada.
- **Panza.** Es un cono, o un cuenco: el exponente `belly` va de 0,75 a 1,35 por isla. Mide 2 bloques en el
  borde y unos `size.depth()` en el centro, con bultos de ruido de hasta ±2.
- **Estalactitas.** Cuelgan en manchas de ruido (escala 2,6) y alguna punta suelta, nunca en el 15 % exterior.
  Miden hasta `depth/3 + 1`. Bajo el bloque más bajo de algunas columnas cuelga la **punta** del bioma (raíces
  colgantes o espeleotema puntiagudo), y casi siempre bajo una estalactita.
- **Terrazas** (L: 1 escalón, XG: 2). El suelo interior sube un bloque por escalón en manchas de ruido, mezcladas
  con la cercanía al centro. Nunca suben en el 30 % exterior, así que **el borde está siempre en `top`**, donde
  aterrizan los puentes. En la isla de aparición, los 9 bloques alrededor del centro son llanos (la plaza).
- **Suelo allanado bajo los huecos** (`Island.Pad`). La huella de cada hueco, y un bloque alrededor, queda al nivel
  de terraza de su centro. El origen del hueco es `top + 1 + ese nivel`.
- **Orden de colocación** (`IslandBlocks.terrain`). Primero lo sólido, capa a capa de abajo arriba. Luego lo que
  cuelga, porque su soporte ya está. Luego las plantas de la superficie, y por último las piezas.

### Paletas (`IslandBiome`)

Cada celda elige su bioma por semilla. En 6 de cada 10 celdas sigue al de su región de 3×3 celdas, así que los
biomas forman grupos sueltos. Solo bloques vanilla:

| bioma | superficie | bajo ella | cuerpo | panza / estalactitas | cuelga | bajo edificios | plantas |
|---|---|---|---|---|---|---|---|
| `meadow` (pradera) | césped | tierra, tierra gruesa | piedra, andesita | tierra enraizada, tierra | raíces colgantes | camino de tierra | hierba, flores |
| `mossy` (piedra/musgo) | musgo, adoquín musgoso | adoquín | piedra, adoquín | piedra, toba | espeleotema (punta) | ladrillo musgoso | alfombra de musgo, helecho |
| `cherry` (cerezo) | césped | tierra | piedra, calcita | calcita, piedra | raíces colgantes | barro compacto | pétalos rosas |
| `snow` (nieve) | nieve | nieve, tierra | piedra, hielo compacto | hielo compacto, hielo azul | — | tablones de abeto | capa de nieve |
| `desert` (desierto) | arena | arenisca | bandas de terracota | terracota | — | arenisca lisa | arbusto muerto |

Las plantas solo van donde sobreviven, y nunca sobre la huella de un hueco ni en el anillo exterior.

## Tamaños y mezcla (`IslandSize`, `IslandMix`)

| tamaño | código | radio | panza | alcance máx. (`reach`) |
|---|---|---|---|---|
| pequeña | `S` | 6 | 7 | 10 |
| mediana | `M` | 10 | 11 | 16 |
| grande | `L` | 16 | 15 | 23 |
| extra grande | `XG` (o `XL`) | 22 | 20 | 31 |

- **Mezcla:** cuatro pesos relativos, `S40,M30,L20,XG10`. También valen `s50 l50`, `S:40`, `S40%` o `XL10`, y los
  tamaños con peso 0 se pueden omitir. **Presets:** `default` (40/30/20/10), `archipelago` (60/30/10/0),
  `small` (80/20/0/0), `even` (25 cada uno), `continents` (10/20/40/30), `giants` (0/10/40/50).
- **Espaciado coherente con los tamaños:** el lado de la celda es `2·(reach + MARGIN + MIN_JITTER)` del tamaño más
  grande de la mezcla, y nunca menos que L, porque la isla de aparición es L. Con XG en la mezcla son 82 bloques;
  si lo más grande es L, 66. Cada isla se desplaza hasta donde su tamaño lo permite (máx. 12). **Entre vecinas
  quedan siempre al menos 12 bloques de vacío** (`2·MARGIN`), así que siempre hay algo que cruzar con un puente.
- La isla de aparición es siempre **L**, esté o no la L en la mezcla.
- La mezcla y la semilla se guardan por mundo en `IslandsState` (ver abajo).

## Huecos (`IslandSlots`) y piezas (`IslandCatalog`)

Cada tamaño pide sus huecos (cuadrados; "medio" es medio lado: 7×7 es medio 3). Cada hueco tiene un ángulo y una
distancia preferidos. Se coloca en el primer sitio, buscando hacia dentro y un poco a los lados, cuya huella cabe
dentro del contorno con un bloque de margen y deja un bloque de suelo libre con los demás huecos. Si no cabe, se
encoge hasta su mínimo; si aun así no cabe, se descarta.

| isla | huecos |
|---|---|
| S | `tree_0` (DECOR, medio 2) + `accent_0` (DECOR, medio 1) |
| M | `centre` (CENTRE, medio 3) + `tree_0` + `accent_0..1` |
| L | `centre` (CENTRE, medio 5, alto 18) + `module_0..1` (MODULE, medio 3) + `tree_0..1` + `accent_0..1` |
| XG | `centre` (CENTRE, medio 7, alto 22) + `module_0..3` (MODULE, medio 4, en los ejes) + `tree_0..3` (diagonales) + `accent_0..3` |
| aparición (L) | `spawn` (en `surface + (0,0,4)`, siempre libre) + `centre` (CENTRE, medio 4, al norte, mirando al sur: el **hueco central** del edificio principal) + `module_0..1` (este/oeste) + `tree_0..3` + `lamp_0..1` flanqueando la aparición |

Etiquetas de cada hueco: el tamaño (`small`…), el bioma (`meadow`…) y una pista (`building`, `tree` o `accent`).
Los de la isla de aparición llevan además `spawn`. Los MODULE y los DECOR miran al centro de la isla; el CENTRE
mira a un lado elegido por semilla (en la aparición, al sur).

**Piezas reales.** `IslandProps.INSTANCE`, la `PieceSource` que usa el runtime, pregunta primero a
`IslandCatalog`. Este es un adaptador de **solo lectura** sobre `hq.citadel.catalog` (`Catalog.of`, `Variant`,
`Plot`, `CitadelCanvas`), la misma construcción que usa el escenario de piezas (`piece.VariantPiece`, que no se
toca):

1. **Familias por rol** (las repetidas pesan más):
   - CENTRE de ≥ 9: casas, más gazebo, centro de plaza y fuente (en la aparición, solo casas).
   - CENTRE pequeño: gazebo, fuente, pozo, estatua, jardín.
   - MODULE: anexo, puesto, pozo, fuente, jardín, gazebo, estatua, y casas si mide ≥ 9.
   - DECOR `tree`: árboles.
   - DECOR `accent`: farol, jardinera, banco, mástil, poste indicador.
2. **Variante.** Entre las que caben en el hueco (`minW ≤ ancho`, `minD ≤ fondo`, `minH ≤ alto`) se elige por
   peso. Cuenta ×4 si su id nombra una palabra del bioma: `cherry`/`japanese`/`torii` en cerezo, `pine` en nieve,
   `stone`/`mossy` en musgo, etc.
3. **Construcción.** Se construye en un `Plot` tan grande como permite el hueco (sin pasar del máximo de la
   variante), mirando al norte y centrado en el origen, y `SlotPlacer` lo gira hacia el `facing` del hueco. Los
   edificios (CENTRE, MODULE) llevan debajo, en `y = -1`, un piso con el pavimento del bioma.
4. **Respaldo.** Si ninguna familia tiene una variante que quepa, o si construirla falla (se avisa una vez en el
   log), se usa el sustituto provisional del primer corte: árbol, pabellón o farola.

La elección es determinista: depende de la semilla del campo y de la posición del hueco.

## Comandos

| comando | qué hace |
|---|---|
| `/t3craft islands` | Crea el mundo la primera vez (semilla por defecto, mezcla `default`) y lo abre después. Desde otro mundo pide `confirm`, como antes. |
| `/t3craft islands new [seed] [mix]` | Campo nuevo con esa semilla (un número tal cual, `random`, o cualquier palabra, que usa su hash; si se omite, aleatoria) y esa mezcla (`S40,M30,L20,XG10` o un preset; si se omite, `default`). Si no hay mundo de islas, lo crea con ellas. Si lo hay, **lo regenera en el sitio**. Desde otro mundo lo guarda y sale sin pedir `confirm`: el propio comando es la confirmación. |
| `/t3craft islands regen` | Dentro del mundo de islas: borra todas las islas y puentes y vuelve a generar el campo con la semilla y la mezcla del mundo. Sirve tras un cambio de código o para deshacer ediciones. |
| `/t3craft islands status` | Lo de antes (islas y puentes construidos o en curso) más semilla, mezcla y lado de celda. Durante una regeneración, el % borrado. |

Si la mezcla es inválida, el comando lo dice y lista los presets.

### Regeneración (`IslandsClient.Regen`, hilo del servidor)

Para el runtime (que guarda lo que tenga). Calcula cajas a borrar con el campo **viejo**, que se reconstruye con
la semilla y la mezcla del estado:

- la caja de cada isla construida, incluidas su panza, sus estalactitas y la altura de sus piezas, con 4 bloques
  de margen;
- la de las islas a ≤ 170 bloques de un jugador, que pueden estar a medias (de estas solo se miran los chunks
  cargados);
- el pasillo entre cada par de vecinas, donde pueden estar sus puentes.

Lo borrado se queda en aire, con un presupuesto por tick (80 000 lecturas, 6 000 borrados) y sin actualizaciones
de vecinos, así que nada cae ni suelta objetos. Los jugadores que pueden volar vuelan mientras tanto. Al terminar:

1. `IslandsState.reset` con la semilla y la mezcla nuevas;
2. `IslandsWorld.use`;
3. `IslandsRuntime.start`, que construye de golpe la isla de aparición nueva y pone en ella el spawn del mundo;
4. los jugadores se teletransportan a ella.

**Se borra todo lo que haya en esas cajas, también lo que construyó el jugador.** Si se cierra el mundo a medias,
el estado sigue nombrando el campo viejo; `/t3craft islands regen` lo termina.

## Estado (`IslandsState`, `t3craft-islands.json`)

```json
{"generator": "islands-v2", "seed": "80740369183859", "mix": "S40,M30,L20,XG10", "islands": [...], "bridges": [...]}
```

La semilla se guarda como cadena. Un archivo sin `seed`/`mix` (o sin archivo) no tiene ajustes todavía: al arrancar
recibe los de `islands new` si los hay, y si no, los de por defecto. La instancia de estado del runtime los lee y
los vuelve a escribir tal cual.

## Enganches y contrato con los puentes (#32) y el runtime

`IslandsRuntime` no se toca. Sigue creando su campo con `new IslandField(IslandsWorld.SEED, IslandsWorld.BASE_Y)`
y usando `IslandProps.INSTANCE`. Para que ese código, sin cambiar, use el mundo abierto:

- `IslandsWorld.SEED` ya **no es una constante**: es `static volatile`. `IslandsClient` la fija desde el estado
  (`IslandsWorld.use`) antes de `IslandsRuntime.start`. La semilla por defecto pasa a `DEFAULT_SEED`.
- `new IslandField(seed, baseY)` usa la mezcla de la sesión (`IslandField.useSessionMix`, que fija
  `IslandsWorld.use`). Es un apaño **provisional**. Lo limpio es que el runtime pase a `IslandsWorld.field()`
  (o a `new IslandField(seed, baseY, mix)`); queda para quien lleve `IslandsRuntime`.
- `IslandProps.INSTANCE` ahora es "catálogo primero, provisional después", sin cambiar de tipo.

Lo que la API de `Island`/`IslandField` mantiene para los puentes: `id`, `gx`, `gz`, `size`, `cx`, `cz`, `top`,
`contains(x, z, slack)`, `surface()`, `distanceTo`, `area()`, `radius()`, `depth(x, z)`, y en el campo `origin()`,
`islandAt`, `within`, `neighbours`, `HEIGHT_RANGE` (sigue en 4).

Añadido sin romper nada:
- en `Island`: `biome()`, `form()`, `pads()`, `slots()`, `spawn()`, `lift(x, z)`, `surfaceY(x, z)` (superficie con
  terrazas), `bottom(x, z)`, `hang(x, z)`, `lowestY()`, `highestY()`;
- en `IslandField`: `cell()`, `mix()`, `cellFor(mix)` y `cellOfId(id)`.

Cambio **no aditivo**: `IslandField.CELL` (constante 64) desaparece. Ahora el lado es `cell()`, que depende de la
mezcla, y `cellOf` pasa a ser de instancia. En `main` nadie fuera de `IslandField` usaba ninguno de los dos.

**El borde de cada isla siempre está en `top`**, porque las terrazas no llegan al 30 % exterior, así que un puente
a la altura de `top` aterriza a ras. Distancias: entre vecinas hay ≥ 12 bloques de vacío. Con XG en la mezcla
(celda de 82) dos islas pequeñas vecinas pueden quedar a ~90 bloques de borde a borde; si eso es demasiado para
un puente, #32 puede pedir celdas más pequeñas o islas puente.

## Defaults elegidos (sin preguntar)

- "Isla de aparición grande con hueco central": tamaño L con un hueco `CENTRE` (el edificio principal), desplazado
  al norte para que el punto de aparición fijo del runtime (`surface + (0,0,4)`) quede en una plaza libre.
- `islands new` con mundo existente lo regenera en el sitio, en vez de borrar la carpeta del mundo: así ningún
  guardado se borra.
- `new` sin semilla elige una aleatoria; una palabra se convierte con su hash (como la semilla vanilla).
- Mundos del primer corte (sin `seed`/`mix` en el estado): toman los valores por defecto. Como las formas y el lado
  de celda cambiaron, conviene `/t3craft islands regen` (el primer corte no llegó a jugarse).

## Limitaciones conocidas

- El runtime (`IslandsRuntime`, del hilo #32) coloca `Placement.state()` y **no aplica los datos de bloque**: los
  carteles o estandartes de alguna variante salen en blanco hasta que el runtime aplique `blockEntity()`.
- Los huecos son cuadrados. Las casas reciben un plot cuadrado del tamaño del hueco, sin pasar de su máximo.
- La búsqueda de sitio de los huecos cuesta algo; por eso `IslandField` guarda en caché hasta 4096 islas.
- Sin pruebas unitarias (el repo no las tiene); la verificación es compilar.

## Riesgos de compilación (revisado a mano, sin compilar)

- Nombres de bloques y propiedades de 26.3: `Blocks.SHORT_GRASS`, `DEAD_BUSH`, `HANGING_ROOTS`, `POINTED_DRIPSTONE`
  con `BlockStateProperties.VERTICAL_DIRECTION` y `DRIPSTONE_THICKNESS`/`DripstoneThickness.TIP`, `PINK_PETALS`
  con `FLOWER_AMOUNT`.
- `MutableComponent.withColor(int)` (el color del texto sale de `ChatTheme.warmStudio().text()`).
- `ServerPlayer.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true)`: la misma firma que usa el runtime.
- Una clase anónima `LinkedHashMap` con `serialVersionUID` estático: requiere Java 16 o posterior.

## Probarlo

1. Compilar en el PC: `./gradlew compileJava compileClientJava`.
2. En juego:
   - `/t3craft islands new 42 S40,M30,L20,XG10`: crea el mundo (o lo regenera) con semilla 42 y esa mezcla.
   - `/t3craft islands`: abrirlo, si no estabas dentro.
   - `/t3craft islands status`: debe decir `seed 42, mix default (S40,M30,L20,XG10), cell 82 blocks`.
3. Comprobar:
   - la isla de aparición es grande, con una casa del catálogo al norte y una plaza libre donde apareces;
   - las islas no son discos y tienen panza con estalactitas;
   - hay biomas distintos;
   - hay casas, gazebos, árboles y faroles del catálogo, no los props provisionales;
   - las XG tienen terrazas.
4. `/t3craft islands new 7 small` y `/t3craft islands new 7 giants`: islas más juntas y más separadas,
   respectivamente.
5. `/t3craft islands regen`: borra y vuelve a construir el mismo campo.
