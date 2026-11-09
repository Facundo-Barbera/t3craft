# Revisión a mano de las 16 ramas WIP del catálogo (2026-11-09)

Hecha durante la moratoria: **no se compiló ni se renderizó nada**. Todo lo que dice este documento sale de leer
el código y de compararlo con los nombres reales de Minecraft 26.3. Para eso usé el `-sources.jar` de la caché de
loom (`mod/.gradle/loom-cache/minecraftMaven/.../minecraft-common-*-26.3-sources.jar`), con `unzip` y `grep`.
Ninguna rama está verificada hasta que el coordinador la compile.

## Qué se revisó en todas las ramas

Pasé chequeos con grep/perl sobre todos los archivos nuevos y después leí a mano lo que marcaban:

- **Nombres de MC 26.3:**
  - Cada `Blocks.X` existe en `Blocks.java`.
  - Los bloques con color o cobre se usan como colecciones: `Blocks.WOOL.pick(DyeColor)` y
    `Blocks.CUT_COPPER.waxed().oxidized()`. En 26.3 no existen constantes como `PINK_WOOL` o `WAXED_*`.
  - Cada `BlockStateProperties.X` existe.
  - Cada import `net.minecraft.*` existe.
- **Tipos sin importar:** no apareció ninguno. Todos los avisos del chequeo eran palabras de comentarios.
- **Llamadas sin declarar:** busqué métodos sin calificar que no estuvieran declarados ni importados de forma
  estática, y llamadas `XxxKit.m` a métodos que no existen. No apareció ninguna.
- **Aridad:**
  - `p.set` (5 argumentos), `p.put` (6), `p.fill` (8), `p.at`, `p.hash`, `p.rng` y `p.has` se llaman siempre con
    el número correcto de argumentos.
  - Las llamadas a los métodos de los kits y de cada clase coinciden con alguna sobrecarga declarada, salvo en
    house-timber, que está corregida.
- **Propiedades que existen en el bloque (un fallo aquí revienta en runtime, no al compilar):**
  - `FACING` solo se usa en barriles.
  - `HORIZONTAL_FACING` se usa en stairs, trapdoors, puertas, escaleras de mano, cofres, telar, smoker, calabaza
    tallada, glazed terracotta, yunque, lectern y chiseled bookshelf.
  - `UP` y `*_WALL` solo se usan en muros. `NORTH/EAST/...` se usa en fences, panes, barras y enredaderas.
  - `AGE_3`/`AGE_7` se usan en los cultivos correctos y `FLOWER_AMOUNT` (1–4) en `PINK_PETALS`.
  - `LEVEL_CAULDRON` (2–3), `LEVEL_COMPOSTER`, `MOISTURE`, `BAMBOO_LEAVES` y `STAIRS_SHAPE` también son correctos.
  - Los helpers `stair/slab/fence/wall/trap/door/leaves(Blocks.X)` reciben siempre un bloque de la familia
    correcta.
- **Forma de los archivos:** llaves, paréntesis y corchetes están balanceados y no hay TODOs. Tampoco hay métodos
  vacíos: los únicos `{}` son records y constructores privados. Ningún archivo está truncado.
- **`switch` como expresión sin `default`:** solo hay uno (`GardenVegRaisedBeds.crop`) y cubre el enum entero.
- **Contrato:**
  - Ningún archivo escribe con `c.set`/`c.put` ni pone anchors, fixtures o workstations.
  - No hay `Math.random`, `new Random` ni estado `static` mutable.
  - Los buffers (`CherryKit.Vox`) y los helpers `set/put` de los kits escriben con `p.set`/`p.put`.
  - Las fences, muros y panes llevan sus lados puestos. Los muros sin lados son postes sueltos a propósito.
  - La única luz visible es el froglight dentro del farol tōrō, que es decorativo y no va en el suelo.
- **Registro:** `CatalogEntries` tiene una línea por variante, en orden alfabético, y el id coincide con `id()` y
  la familia con `family()`.

**Riesgo común a todas las ramas:** no se comprobaron los tipos de los argumentos a fondo (solo el número), ni la
inferencia de lambdas y genéricos, ni los `int`/`long`/`double` en expresiones mixtas. Tampoco se comprobó la
calidad visual. Es lo que tiene que cubrir el primer `./gradlew build` y el primer render.

**Conflictos de merge previsibles (no son de compilación):**
- `CatalogEntries.java`: cada rama añade 2 o 3 líneas en el mismo bloque. Se resuelve uniendo las líneas en orden
  alfabético.
- `docs/CHANGELOG-dev.md`: cada rama añade una línea al principio de `## Entries`. Se resuelve uniéndolas.

## Por rama

### cat/house-timber: lista para compilar
| | |
|---|---|
| Variantes | house_timber_arcade, house_timber_corner, house_timber_overhang (+ `TimberKit`) |
| Corregido | Las tres variantes llamaban a `TimberKit.gable` con 14 argumentos, pero el kit solo tenía la forma de 15 (con `Block alt`): **error de compilación**. Añadí una sobrecarga sin `alt` que delega con `alt = null`. |
| Riesgo que queda | Bajo. `TimberKit.wall` asume que `postCell.at(i, y)` nunca devuelve null (se cumple en los tres usos). |
| Commit | `c18d6ef` |

### cat/house-stone: lista para compilar
| | |
|---|---|
| Variantes | house_stone_manor_court, house_stone_tower, house_stone_weathered (+ `StoneKit`) |
| Corregido | Nada en el código. Añadí la línea RELOAD que faltaba en `CHANGELOG-dev.md`. |
| Riesgo que queda | Bajo. Usa nombres totalmente calificados (`...properties.WallSide`, `DoorHingeSide`), que existen. |
| Commit | `5f71526` |

### cat/house-japanese: lista para compilar
| | |
|---|---|
| Variantes | house_japanese_engawa, house_japanese_teahouse, house_machiya (+ `JapaneseKit`) |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. El id `house_machiya` no lleva `japanese` (el contrato lo permite: el prefijo de familia es `house_`). |
| Commit | `5169162` |

### cat/house-terrace: lista para compilar
| | |
|---|---|
| Variantes | house_terrace_cascade, house_terrace_podium, house_terrace_tiers (+ `TerraceKit`) |
| Corregido | Nada. No hice commit: la rama ya traía su línea en el CHANGELOG. |
| Riesgo que queda | Bajo. `TerraceKit.Links` usa `HashSet`, pero cada celda se escribe una sola vez, así que el resultado no depende del orden. |
| Commit | `070db27` (sin cambios) |

### cat/house-workshop: lista para compilar
| | |
|---|---|
| Variantes | house_forge_atelier, house_library_copper, house_scriptorium (+ `HouseWorkshopKit`) |
| Corregido | Nada. No hice commit. |
| Riesgo que queda | Bajo. El cobre va por `WeatheringCopperCollection` (`.waxed().exposed()`…), que es correcto en 26.3. `facing()` solo se aplica a bloques con `HORIZONTAL_FACING`. |
| Commit | `3a0c73e` (sin cambios) |

### cat/house-tower: lista para compilar
| | |
|---|---|
| Variantes | house_tower_bridge, house_tower_lookout, house_tower_stair (+ `HouseTowerKit`) |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. En `HouseTowerLookout`, el record anidado `G` tiene un método `wall(int,int)` y el kit importa de forma estática `wall(Block, …)`. La llamada de la línea 482 está fuera de `G`, así que resuelve al import estático. |
| Commit | `09e8987` |

### cat/stall-market: lista para compilar
| | |
|---|---|
| Variantes | stall_cloth_lanterns, stall_fruit_awning, stall_tea_bar |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. |
| Commit | `4653148` |

### cat/fountain-tiered: lista para compilar
| | |
|---|---|
| Variantes | fountain_octagon_statue, fountain_tiered_basins, fountain_wall_mascaron (+ `FountainKit`) |
| Corregido | Las hojas de azalea de `FountainOctagonStatue` iban sin `PERSISTENT` y se pudrirían en el mundo. Ahora usan `CitadelCanvas.leaves`. |
| Riesgo que queda | Bajo. Ver fountain-pond: esta rama se queda con el nombre `FountainKit`. |
| Commit | `3c91514` |

### cat/fountain-pond: lista para compilar
| | |
|---|---|
| Variantes | fountain_koi_pond, fountain_mossy_cascade, fountain_stream_stones |
| Corregido | La rama creaba `catalog/fountain/FountainKit.java`, y fountain-tiered crea **otro** `FountainKit` distinto en el mismo paquete. Al mergear las dos habría un conflicto add/add y una de ellas dejaría de compilar. Lo renombré a **`PondKit`** (archivo, clase e imports estáticos). Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. |
| Commits | `fd63ecc` (rename), `b4d4a0c` (CHANGELOG) |

### cat/tree-cherry: lista para compilar
| | |
|---|---|
| Variantes | tree_cherry_grand, tree_cherry_leaning, tree_cherry_weeping (+ `CherryKit`) |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. `CherryKit.Vox` es un buffer local que vuelca con `p.set`. |
| Commit | `e6c7b6c` |

### cat/tree-mixed: lista para compilar
| | |
|---|---|
| Variantes | tree_birch_grove, tree_maple_red, tree_pine_tiered (+ `MixedTreeKit`) |
| Corregido | Nada. No hice commit. |
| Riesgo que queda | Bajo. |
| Commit | `c547ed6` (sin cambios) |

### cat/tree-garden: lista para compilar
| | |
|---|---|
| Variantes | tree_azalea_blossom, tree_cloud_pine_pot, tree_willow_pond |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. Las enredaderas del sauce llevan `UP`/lados, y `VineBlock` los tiene. |
| Commit | `5d64707` |

### cat/garden-zen: lista para compilar
| | |
|---|---|
| Variantes | garden_flower_rose_arch, garden_veg_raised_beds, garden_zen_karesansui |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. El `switch` exhaustivo sobre el enum `Crop`, sin `default`, compila en Java 25. |
| Commit | `ea48ef0` |

### cat/lamp-street: lista para compilar
| | |
|---|---|
| Variantes | lamp_street_avenue_double, lamp_street_brazier_tripod, lamp_street_toro |
| Corregido | `LampStreetToro` usaba `Blocks.WHITE_CANDLE`, que **no existe en 26.3**: error de compilación. Lo cambié por `Blocks.DYED_CANDLE.pick(DyeColor.WHITE)` con `LIT = true` y añadí el import de `DyeColor`. |
| Riesgo que queda | Bajo. |
| Commit | `ecdea65` |

### cat/gate-arch: lista para compilar
| | |
|---|---|
| Variantes | gate_arch_battlement, gate_torii_cherry |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. Comprobé a mano que el vano central queda libre a w = 5…11 y h ≥ 8. |
| Commit | `d44f58c` |

### cat/plaza-center: lista para compilar
| | |
|---|---|
| Variantes | plaza_center_glazed_rosette, plaza_center_monumental_sundial, plaza_center_octagon_bandstand |
| Corregido | Nada en el código. Añadí la línea en el CHANGELOG. |
| Riesgo que queda | Bajo. El sub-paquete se llama `plazacenter`, no `plaza_center`. Es legal y no lo cambié. `plaza_center_glazed_rosette` solo acepta 15×15. |
| Commit | `b47badf` |

## Resumen

- **Para descartar:** ninguna rama. **Dudosas:** ninguna. **16 de 16 listas para compilar.**
- No encontré ninguna variante a medias: no hay métodos vacíos ni código truncado. No quité nada de
  `CatalogEntries`.
- Corregí dos errores de compilación seguros: `TimberKit.gable` y `WHITE_CANDLE`.
- Arreglé un choque de clases entre ramas (`FountainKit` → `PondKit`) y unas hojas que se pudrirían.
- Añadí 10 líneas al CHANGELOG.
- Todo está pendiente del primer `build` y del primer render del coordinador, cuando Facundo levante la moratoria
  (`docs/pending-execution.md`).
