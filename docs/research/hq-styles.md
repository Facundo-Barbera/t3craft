# research/hq-styles: estilos para un HQ más bonito

Investigación y propuesta para que Facundo elija un estilo. **No implementa nada.** Base:
`origin/main` @ 51e654d (Minecraft **26.3**, Fabric 0.20.1, mod **MIT**). Todos los bloques
citados se comprobaron contra los `blockstates/*.json` del jar de 26.3.
`docs/research/agentcraft-world-elements.md` todavía no está en main, así que no se tuvo en cuenta.

## TL;DR

- **No hay fuentes de terceros que sirvan.** No encontré schematics ni structure templates
  *modulares de oficina* con una licencia compatible con un mod MIT. Lo que tiene buena licencia
  no encaja en estilo ni en formato, y lo que encaja en estilo es NC o no tiene licencia (ver §1).
- **Propuesta: diseñarlos nosotros en código**, con la misma geometría de hoy (huella, alturas y
  anclas idénticas) y una capa nueva `HqStyle` (paleta + 4–5 "piezas" intercambiables).
  Así **site, nivelación, envolvente, HqFixtures, workstations y NPC no cambian**.
- Hay 3 estilos (§2): **A. Estudio de cristal** (oficina moderna), **B. Taller de cobre**
  (evolución industrial del actual) y **C. Campus de cerezo** (japonés).
- **Recomendación: A**, sobre la infraestructura `HqStyle`. B queda como variante barata y C como
  segundo estilo cuando A esté estable (§4).

## 1. Fuentes externas evaluadas

| Fuente | Autor · versión · fecha | Licencia | Estilo / formato | ¿Sirve? |
|---|---|---|---|---|
| [Trek](https://modrinth.com/project/h2jngREY) ([GitHub](https://github.com/hotsu0p/Trek)) | hotsu0p · Beta 0.6.2 · actualizado hace unas 2 semanas (≈ oct 2026) · soporta 26.3 | MIT | Más de 150 estructuras por bioma (torres, casas, pirámides) en un datapack con jigsaw. | **No.** Estilo rústico o de aventura, nada de oficinas ni módulos repetibles. El repo de GitHub solo tiene README y licencia (las `.nbt` están en el release), así que la procedencia de las piezas no se puede auditar. |
| [Towns and Towers](https://modrinth.com/datapack/towns-and-towers) | Stardust Labs y otros | CC-BY-**NC-SA**-4.0 | Aldeas y puestos | **No.** NC y SA no se pueden combinar con un mod MIT redistribuible. |
| [Structory](https://modrinth.com/datapack/structory) | Stardust Labs | Licencia propia de Stardust Labs | Ruinas y cabañas | **No.** No es una licencia libre y no encaja en estilo. |
| [mc-building-gadgets-templates](https://github.com/christophermlee/mc-building-gadgets-templates) | Christopher M. Lee · 4 commits | **CC0-1.0** | Una sola plantilla ("seaopolis") para Building Gadgets | **No.** Licencia ideal, pero tiene una sola pieza, no es modular, no es de oficina y su formato no es `.nbt` vanilla. |
| Planet Minecraft, "Modern Office Building" (CUBIC BRAINSTORM) y casi todos los "modern" | varios | Mayormente CC BY-**NC-SA** 4.0 o ninguna | `.schem`/`.litematic`, edificios enteros | **No.** NC/SA, o sin licencia, lo que significa *todos los derechos reservados*. Además son monolitos, no módulos de 6 escritorios. |
| [mc-schematics.com](https://mc-schematics.com/), spaleforce, blockical, BuiltByBit (gratis) | varios | Sin licencia visible | Casas modernas | **No.** Sin licencia, no se pueden redistribuir. |
| Estructuras vanilla (trial chambers, aldeas) | Mojang | EULA de Minecraft | `.nbt` | **No** como asset propio. Usarlas *in situ* está bien, pero no copiarlas a nuestro jar. |

**Conclusión:** diseñar nosotros. Los estilos de §2 se *inspiran* en arquitecturas reales
(oficina de muro cortina, nave industrial con techo en diente de sierra, *machiya* y engawa
japoneses) y no copian ningún build concreto. No hace falta atribución nueva.
Si en el futuro aparece una fuente CC0, CC-BY o MIT, el formato propuesto en §3.3 (opción B)
permite enchufarla.

## 2. Estilos

### Geometría común (no cambia en ningún estilo)

Un módulo (ala este, `u` crece hacia fuera de la espina y `v` va de norte a sur) ocupa **14 × 14 de
planta, `y 0..10`** (hoy el techo del ala está en `y = 6`). El hub mide 17 × 17 × 11, el pórtico
9 × 5 y la espina tiene 7 de ancho y una fila cada 14.

```
u→   0  1  2  3  4  5  6  7  8  9 10 11 12 13
v0   W  W  W  W  P  W  W  W  P  W  W  W  P  W      W muro   P pilar
v1   W  b  M  L  S  b  M  L  S  b  M  L  S  W      b cajón  M monitor (computerPos)  L lámpara
v2   W  .  c  .  .  .  c  .  .  .  c  .  .  W      c silla (ancla desk_<thread>)  S estante+maceta
v3   W  .  .  .  .  .  .  .  .  .  .  .  .  W
v4   P  .  .  .  .  .  .  .  .  .  .  .  .  P
v5   D  =  =  =  =  =  =  =  =  =  =  =  =  W      D puerta desde la espina
v6   D  =  =  =  =  =  =  =  =  =  =  =  =  P      = alfombra del color del entorno
v7   D  =  =  =  =  =  =  =  =  =  =  =  =  W
v8   P  .  .  .  .  .  .  .  .  .  .  .  .  P
v9   W  .  .  .  .  .  .  .  .  .  .  .  .  W
v10  W  .  c  .  .  .  c  .  .  .  c  .  .  W
v11  W  b  M  L  S  b  M  L  S  b  M  L  S  W
v12  W  W  W  W  P  W  W  W  P  W  W  W  P  W
v13  g  g  g  g  g  g  g  g  g  g  g  g  g  g      franja de jardín entre filas
```

Los estilos solo cambian **qué bloque va en cada celda de muro, techo, suelo y decoración**, y la
silueta del techo dentro de `y ≤ 10`. Escritorio, silla, monitor, lámpara, puerta, pasillo y
franja de jardín quedan en las mismas celdas.

### A. Estudio de cristal (oficina moderna de cristal y concreto) ⭐

Oficina contemporánea: muro cortina de cristal ahumado, losas blancas, franjas de luz cálida en
el techo y jardineras de musgo. Es la que mejor combina con los monitores `t3craft:monitor`
(pantallas oscuras sobre fondo claro) y la que deja ver mejor a los NPC desde fuera.

| Rol | Bloques (26.3) |
|---|---|
| Estructura / pilares | `quartz_pillar`, `smooth_quartz` |
| Muros opacos | `white_concrete`, `light_gray_concrete` (zócalo) |
| Muro cortina | `gray_stained_glass_pane` (paneles), `tinted_glass` en las esquinas |
| Suelo | `polished_andesite`, `smooth_stone`; pasillo con `white_carpet` y franja del color del entorno |
| Techo | `smooth_quartz_slab` plano, lucernario de `light_gray_stained_glass` |
| Luz | `pearlescent_froglight` / `ochre_froglight` en franja empotrada, `sea_lantern` en el hub |
| Escritorio | `smooth_quartz_slab` (tapa), `pale_oak_shelf` como cajón, silla `pale_oak_stairs` |
| Detalle | `waxed_copper_bars` (barandas), `moss_carpet` + `azalea` en jardineras |
| Letrero | `pale_oak_wall_hanging_sign` sobre la puerta (texto teñido del entorno, como hoy) |

Corte transversal del ala (mirando por `u`, de `v0` a `v12`):

```
y7   .  .  .  .  .  .  .  .  .  .  .  .  .
y6   ▬  ▬  ▬  ▬  ░  ░  ░  ░  ░  ▬  ▬  ▬  ▬     ▬ smooth_quartz_slab   ░ lucernario
y5   Q  ✦  ■  ■  ■  ■  ■  ■  ■  ■  ■  ✦  Q     ✦ froglight empotrado  ■ white_concrete
y4   Q  |  .  .  .  .  .  .  .  .  .  |  Q     | gray_stained_glass_pane (muro cortina)
y3   Q  |  .  .  .  .  .  .  .  .  .  |  Q
y2   Q  M  .  .  .  .  .  .  .  .  .  M  Q     M monitor sobre la tapa
y1   ▒  t  c  .  .  =  =  =  .  .  c  t  ▒     ▒ light_gray_concrete  t tapa  c silla
y0   ▓  ▓  ▓  ▓  ▓  ▓  ▓  ▓  ▓  ▓  ▓  ▓  ▓     ▓ smooth_stone
     v0 v1 v2 v3 v4 v5 v6 v7 v8 v9 v10 v11 v12
```

Hub (planta, `y 0`, entrada al sur): atrio a doble altura con una pared de pantallas como tablero
(en vez de deepslate pulido, un marco de `quartz_pillar` alrededor de las `board_lamp`), bancos
`pale_oak_stairs` y techo plano con un gran lucernario de cristal en `y 10`.

```
      x-8 ............................... x8
z-8   Q ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ Q
z-4   Q . . . . [■ ■ ■ B B B ■ ■ ■] . . . . Q    tablero: marco quartz + board_lamp (B)
z-1   Q . h . . . . . . . . . . . . h . Q        h jardinera musgo + azalea
z 0   Q . . . ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ ▫ . . . Q          ▫ banco
z 4   Q . h . . . . . . . . . . . . h . Q
z 8   Q ■ ■ ■ ■ ■ ■ |   | ■ ■ ■ ■ ■ ■ ■ Q        puerta de cristal (dos paneles con hueco)
```

| | |
|---|---|
| Esfuerzo | **M**: paleta más 3 piezas nuevas (muro de ala, techo de ala y techo/tablero del hub). |
| Pros | Luminoso y legible por dentro, combina con los monitores, techo plano (sin riesgo de altura) y pocas formas difíciles. |
| Contras | Mucho blanco: en terreno natural puede verse "pegado". Se mitiga con jardín y pilares de cuarzo. Muchos paneles de cristal (formas ya horneadas por `PlanShapes`, sin coste extra). |
| RESTART | No: solo datos y código del plan (hot reload de clases o RELOAD). |

### B. Taller de cobre (industrial/steampunk, evolución del actual)

Toma el lenguaje actual ("Warm Studio": timber + cobre encerado) y lo lleva a una **nave de
taller**. Techo en **diente de sierra** con lucernarios hacia el norte, cobre en distintas pátinas
(todo `waxed_*`, sin envejecimiento), rejillas, cadenas y tejas de pizarra.

| Rol | Bloques (26.3) |
|---|---|
| Estructura | `stripped_dark_oak_log`, `deepslate_tiles` (zócalo) |
| Muros | `waxed_cut_copper`, `waxed_exposed_cut_copper`, `waxed_copper_grate` (ventilación) |
| Suelo | `spruce_planks`, `polished_tuff` en los pasillos |
| Techo | `waxed_weathered_cut_copper_stairs` en diente de sierra + `glass` vertical |
| Luz | `waxed_copper_lantern` colgado de `waxed_copper_chain`, `waxed_copper_bulb` |
| Detalle | `waxed_copper_bars`, `chiseled_bookshelf`, `spruce_shelf`, `barrel` |

Corte del ala con diente de sierra (tres dientes a lo largo de `v`, máximo en `y = 8`):

```
y8   .  .  .  ◢  │  .  .  ◢  │  .  .  ◢  │      ◢ escalera de cobre   │ cristal vertical
y7   .  .  ◢  ▬  │  .  ◢  ▬  │  .  ◢  ▬  │
y6   .  ◢  ▬  ▬  │  ◢  ▬  ▬  │  ◢  ▬  ▬  │
y5   ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬      ▬ viga
y1-4 (como hoy: muro de cobre con ventanas, escritorio, silla)
```

| | |
|---|---|
| Esfuerzo | **S**: casi todo es paleta. Solo es nueva la pieza "techo del ala". |
| Pros | Cambio mínimo y coherente con lo ya construido (las alas existentes se reconocen), y el cobre combina con `status_lamp`. |
| Contras | Más oscuro por dentro; las pátinas pueden verse "sucias". Las escaleras del diente de sierra requieren que `PlanShapes` hornee bien las esquinas (ya lo hace). |
| RESTART | No. |

### C. Campus de cerezo (japonés)

Pabellones de madera de cerezo con galería exterior (*engawa*), paneles tipo *shoji* (calcita
entre listones), tejado a dos aguas con alero curvo de tejas de deepslate y un jardín de cerezos y
bambú en la franja `v13`.

| Rol | Bloques (26.3) |
|---|---|
| Estructura | `stripped_cherry_log`, `cherry_planks` |
| Muros | `calcite` / `white_terracotta` (shoji) con `cherry_trapdoor` como celosía |
| Suelo | `bamboo_mosaic` (tatami), `bamboo_planks` |
| Techo | `deepslate_tile_stairs` y `deepslate_tile_slab` con alero curvo; cumbrera de `polished_blackstone_bricks` |
| Luz | `waxed_copper_lantern`, `ochre_froglight` dentro de `cherry_trapdoor` (farol) |
| Jardín | `cherry_leaves` (**`persistent=true`**), `bamboo`, `moss_carpet`, `mud_brick_wall` |

Corte del ala (a dos aguas, cumbrera sobre el pasillo en `y = 9`):

```
y9   .  .  .  .  .  .  ▲  .  .  .  .  .  .       ▲ cumbrera
y8   .  .  .  .  .  ◢  ▬  ◣  .  .  .  .  .
y7   .  .  .  ◢  ◢  ▬  ▬  ▬  ◣  ◣  .  .  .
y6   ◢  ◢  ◢  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ◣  ◣  ◣      alero que sobresale 0 bloques (cabe en v0..v12)
y5   C  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  ▬  C      C cherry log
y1-4 C  shoji / celosía  ...                   C
```

| | |
|---|---|
| Esfuerzo | **M–L**: techo curvo en ala, espina y hub (el hub con tejado de cuatro aguas es lo más caro) más el jardín. |
| Pros | El más distintivo y "bonito" en capturas. La madera cálida combina con cualquier bioma. |
| Contras | El techo inclinado tiene que caber en `y ≤ 10` y no puede salirse de la planta (no hay vuelo de alero fuera de `v0..v12`, porque la franja `v13` es de la fila vecina). Las hojas deben ser `persistent` o se caen. Las trampillas como celosía no deben estar en el suelo, porque confunden a `LevelWalkable`. Es más oscuro por dentro. |
| RESTART | No. |

### Comparación

| | A. Cristal | B. Cobre | C. Cerezo |
|---|---|---|---|
| Belleza / novedad | alta | media | **muy alta** |
| Legibilidad NPC (luz, vista desde fuera) | **muy alta** | media | media |
| Encaje con monitor y lámparas | **muy alto** | alto | medio |
| Esfuerzo | M | **S** | M–L |
| Riesgo de altura o silueta | **bajo** (plano) | medio | alto |
| Licencia | propia (MIT) | propia (MIT) | propia (MIT) |

## 3. Encaje en el sistema actual

### 3.1 Qué **no** cambia (en los tres estilos)

| Pieza | Por qué sigue igual |
|---|---|
| `HqLayout` (asignación, 6 escritorios, anexos, archivo, `envelope`, JSON v2) | El estilo no toca quién se sienta dónde. |
| `HqGeometry`, `HqFootprints`, `HqSiteFrame` | Misma huella y `TOP = 10`. La envolvente de site y la nivelación por módulo no cambian. |
| Anclas (`desk_`, `monitor_`, `lamp_`, `board*`, `bench_*`, `walk_*`, `archive_*`) y `Workstation` | Escritorio, silla, monitor y lámpara en las mismas celdas, y el tablero en `z = -4`. |
| `HqFixtures` (`t3craft:monitor`, `status_lamp`, `board_lamp`) | Sustituye por posición (`computerPos`, `lampPos`, `board_lamp_i`). No le importa el estilo. |
| Rotación / `HqSite` / `BuildJob` | Todo sale como estados con `facing`; `state.rotate` ya funciona. Las alas oeste ya se espejan en el generador. |
| Builders, snapshots, undo, `HqPlans.expansion` | El plan sigue siendo una lista de celdas por grupo. |

**Invariantes que cada estilo debe cumplir** (convertirlos en tests parametrizados por estilo):
nada fuera de `HqFootprints.slot(i)`/`base()`, nada por encima de `y = 10`, headroom ≥ 2 en
pasillo, puerta y silla, sin bloques transitables raros en el suelo de paso (trampillas,
alfombras sobre escaleras), hojas `persistent=true`, cobre siempre `waxed`, conexiones que no
dependan de tags (cercas y muros de piedra van a barras, como hoy) y las mismas anclas y
workstations que el estilo actual.

### 3.2 Qué cambia

```
Palette (final class)            →  interface HqStyle  (WarmStudio = el actual, GlassStudio, CopperWorks, CherryCampus)
HqGenerator.k = new Palette()    →  HqGenerator(HqStyle style)
  wingEndWall / segment (muro+techo) / hub roof / board frame / garden strip
                                 →  métodos de "pieza" del estilo, con implementación por defecto = la actual
HqPlans.generate(layout)         →  generate(layout, HqStyle.active())   (o estilo guardado por sitio)
```

- **Estilo persistido por sitio**: `construction.json` y `HqStore` guardan `style` (por defecto
  `warm_studio`, así los HQ existentes no cambian). Cambiar de estilo en un HQ ya construido es un
  `restyle` explícito: el diff rehace casi todas las celdas como *cambio in-place* (`SiteService.claim`)
  y la cuadrilla tarda en proporción (unas 3–4 k celdas por ala). Hay que avisarlo antes de hacerlo.
- Comando: `/t3craft hq style <id>` (preview con `hq-preview demo … style <id>` en superflat) y
  opción en el config.
- Subir la versión del plan de demo (la que recibe `HqStructureSource`), porque cambia la salida del generador.

### 3.3 Formato: ¿`.nbt` parametrizado o código?

| | **A. Código (recomendado)** | **B. Structure template `.nbt` parametrizado** |
|---|---|---|
| Cómo | Piezas Java por estilo (como hoy `segment`/`wingEndWall`). | Construir la pieza en el juego, guardarla con un *structure block* y cargarla con `StructureTemplate` en `data/t3craft/structure/hq/<style>/segment.nbt`. Las anclas se marcan con bloques marcadores (structure blocks en modo DATA, con `metadata = desk:0`, `monitor:0`, …) y se reemplazan al generar. |
| Determinismo e idempotencia | Directo, pura función del layout. | Sí, si se ordena por posición. |
| Tests JUnit | Funciona con `Bootstrap` (como ahora). | Leer el NBT necesita registros y `HolderGetter<Block>`, que complican el test fuera del juego. |
| Espejo de alas oeste | Ya resuelto. | `StructurePlaceSettings.mirror` con estados espejados. Viable, pero las anclas también hay que espejarlas. |
| Variación (color del entorno, monitor encendido o apagado) | Trivial. | Pasada de sustitución por marcadores (como `HqFixtures`). |
| Editable por no programadores | No. | **Sí**, en el juego. |
| Recomendación | **Ahora.** | Cuando haya un segundo autor de builds, o si aparece una fuente CC0/CC-BY. |

## 4. Recomendación y plan

**Elegir A (Estudio de cristal)**, implementado sobre la capa `HqStyle`:

1. Es la que mejor **cumple la petición**: se ve claramente "más bonito y moderno" y es coherente
   con un HQ de agentes con monitores.
2. Su **techo plano** no arriesga la envolvente, y es la más **luminosa y legible** para ver a
   los NPC trabajar.
3. La capa `HqStyle` deja **B y C** como incrementos de datos. C (cerezo) sería el segundo estilo
   recomendado.

Plan en pasos (cada uno, una rama de worktree con tests en verde):

| # | Rama | Qué | Esfuerzo |
|---|---|---|---|
| 1 | `feat/hq-style-core` | `Palette` → `HqStyle` + `WarmStudio` (el actual, **byte a byte igual**: test de fingerprint), `HqGenerator(HqStyle)`, piezas con implementación por defecto y tests de invariantes (§3.1) parametrizados por estilo. | S |
| 2 | `feat/hq-style-glass` | `GlassStudio`: paleta + muro y techo del ala, espina con lucernario, hub con atrio y marco del tablero. `hq-preview demo … style glass`. | M |
| 3 | `feat/hq-style-select` | `style` en `construction.json`/`HqStore` (por defecto `warm_studio`), `/t3craft hq style <id>` con confirmación de restyle, opción de config y subir la versión del plan de demo. | S |
| 4 | (opcional) `feat/hq-style-cherry` / `-copper` | C o B sobre la misma base. | M–L / S |
| 5 | Verificación en el juego por Facundo | Superflat `demo 2 9` con cada estilo, 4 rotaciones, monitores con `t3craft:monitor` activos. | – |

Ninguno necesita **RESTART** (no se registran bloques, ítems ni BE nuevos, solo código del plan y
datos). Con el paso 3 hace falta una RELOAD para el comando nuevo, según CHANGELOG-dev.

## 5. Licencias y atribución

| Fuente | Atribución requerida | Dónde |
|---|---|---|
| Estilos A, B y C (diseño propio) | Ninguna (código y diseño propios, MIT del mod). | — |
| Inspiración de "Warm Studio" (AgentCraft, MIT) | Ya está en `THIRD_PARTY_NOTICES.md` y en el Javadoc de `Palette`. **Mantenerla** en `WarmStudio` al mover la clase. | `THIRD_PARTY_NOTICES.md` (existente) |
| Fuentes descartadas (§1) | — (no se usan) | — |
| Futuro `.nbt` de terceros (si fuera CC0, CC-BY o MIT) | CC-BY: autor, título, enlace, licencia e indicación de cambios. MIT: aviso de copyright y texto de licencia. CC0: ninguna (cortesía: autor y enlace). | Nueva sección en `THIRD_PARTY_NOTICES.md` + `data/t3craft/structure/hq/<style>/CREDITS.txt` |
| **No aceptables** | CC BY-NC*, *-SA, "sin licencia", EULA de Mojang (assets) | — |
