# Revisión a mano: ramas WIP de la ciudadela y dos arreglos

Fecha: 2026-11-09. Revisión manual durante la moratoria: **no se compiló, no se renderizó y no se ejecutó nada**.
Todo sigue **pendiente de compilar** (coordinador, `tools/heavy.sh`) y, en las piezas de la ciudadela, de un render
`hqRender` con el informe de `PlanReport` (zonas oscuras, bloques flotando, `unsupported`, anchors).

## Método

Cada rama se leyó en su worktree contra `origin/main`. Las comprobaciones fueron:

- **Nombres de MC 26.3**: cada `Blocks.X`, `BlockStateProperties.X`, import `net.minecraft.*` y nombre cualificado se
  comparó con el jar de fuentes de la caché de loom
  (`mod/.gradle/loom-cache/minecraftMaven/.../minecraft-common-*-26.3-sources.jar`, listado con `unzip -l` y leído
  con `unzip`; sin `javap`). En 26.3 los bloques de color y de cobre son colecciones (`ColorCollection`,
  `WeatheringCopperCollection`). Los usos `Blocks.STAINED_GLASS.pick(DyeColor)`, `Blocks.CARPET/BED/WALL_BANNER.pick(..)`
  y `Blocks.LIGHTNING_ROD/COPPER_CHAIN.waxed().unaffected()` son correctos.
- **API interna**: llamadas `Clase.metodo(`, `c.*`, `c.kit.*` y `c.style.*` contra las declaraciones de la rama; imports
  estáticos y constantes `Clase.CONSTANTE`; aridad de `c.set/fill/put/anchor/walkSpot/...` contra las sobrecargas de
  `CitadelCanvas` (con el script probado sobre un caso erróneo).
- **Imports**: nombres simples usados sin import, ni del mismo paquete ni de `java.lang`.
- **Sintaxis**: llaves, paréntesis y corchetes balanceados (sin contar comentarios ni cadenas) y ausencia de marcadores
  de conflicto.
- **Juego**: anchors (`spawn`, `hub`, `entrance`, `board*`, `thread_wall_front`, `archive*`, `module_*`, `walk*`,
  `desk_*`), walk spots, escritorios y workstations, caminos (puertas, pasillos, rampas) y el kernel del daemon
  (`BuildPlan`, `Anchor`, `AnchorNames`, `Placement`, `HqGenerator`).
- **Solapamientos**: `git merge-tree --write-tree` de cada rama con `main` y de cada par de ramas. Se revisaron a mano
  los merges limpios que tocan el mismo archivo.

Ninguna rama tenía errores de compilación detectables a mano. Los cambios hechos son menores (abajo). El riesgo
principal no es compilar cada rama por separado, sino **integrar `citadel/scale` con el swarm**: `scale` elimina
constantes públicas que todo el swarm usa.

## Tablas por rama

### `citadel/scale` (escala 1.5× configurable, WIP rescatado)

| | |
|---|---|
| Estado | **Lista para compilar** sola sobre `main`. Se integra **la última**, como port (ver orden). |
| Corregido | Faltaba su línea en `CHANGELOG-dev.md`: añadida (RELOAD). Commit `b9ba98b`. |
| Riesgo de compilación | Bajo. Todos los usos de lo eliminado (`RingCampusGeometry.PODIUM/PATIO_R/GALLERY_R/RAMP_R/STREET/FLOOR_H/STAIR_STEPS`, `CitadelCanvas.Y0/FH`, `CitadelFinish.SPAWN`, `CitadelHub.AISLE_TOP/SPRING`, `vaultY(int)`, `arcadeZ(int)`, `CitadelNodes.door` de 3 args, `Node` de 4 componentes) están portados dentro de la rama, también en `hqrender`. `LayoutParams.Spec` gana el componente `unit` con un constructor de 6 args que delega, así que los usos existentes compilan. `(Number) v` sobre un `int` es un cast válido (boxing más widening). |
| Riesgo de juego | **Alto por diseño.** El valor por defecto es `150` (×1.5): al recargar, **toda ciudadela existente se replanifica más grande**. Se mueven anchors, escritorios, la SPAWN (`(0,1,38)` → `(0,1,56)`) y los módulos, y el siguiente plan reconstruye el HQ. Con k=1 la fórmula devuelve el plan original (spawn `round(37.5)=38`, entrance `hubX-1=11`, desks 2/3/7). **Recomendación:** dejar el valor por defecto en `100` hasta ver los renders a 1.5 y subirlo con aprobación de Facundo. Otros puntos: `RingCampusStyle.bottom()/top()` leen `dims()` (correcto con el parámetro del layout); lo que no escala a propósito (lámparas, árboles, pantallas 3×2) puede quedar pequeño a 2.5. |

### `citadel/1-light` (luz invisible `minecraft:light` + ambientación)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Nada (commit de revisión vacío `fb18977`). |
| Riesgo de compilación | Bajo. `Blocks.LIGHT` y `LightBlock.LEVEL` existen; `HqLightMap.at(BlockPos)`, `ofView`, `addSource` y `gain` existen (los puentes públicos nuevos delegan); `net.minecraft.core.@Nullable BlockPos` es sintaxis válida; `Box` es un record público. |
| Riesgo de juego | Medio. (1) La luz funcional pasa a bloques `minecraft:light` colgados en el aire de cada grupo; se reservan anchors y desks (`reserved`) y solo se ocupa aire explícito del grupo. `fillAir` crea ese aire hasta y=5 en cada columna, así que las calles abiertas también se pueden iluminar. (2) `CitadelAmbience` pone postes en el borde de la vía pública y deshace cualquiera que deje una celda sin conexión. Verificar con `PlanReport` que no queden celdas oscuras ni desconectadas. (3) Los braseros (`CAMPFIRE` encendido) están por encima de la cabeza; confirmar en el render. (4) Interacción con `fix/new-thread-hitch`: cada `minecraft:light` cuenta como bloque que cambia la luz, así que la deuda de luz del `BuildBudget` frenará algo la construcción de la ciudadela. Es aceptable, pero hay que vigilar el tiempo de construcción. |

### `citadel/1-paths` (jerarquía de calles, pavimento, mobiliario)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | `CitadelPaving.vergeCover` devuelve `@Nullable BlockState` (devolvía `null` sin anotación). Commit `6cace3d`. |
| Riesgo de compilación | Bajo. Se quitan los imports estáticos de `BAMBOO` y `slab` de `CitadelGrounds`, y no quedan usos. `CAUSEWAY_D`, `Lot.a/b/reach` y `CitadelCanvas.leaves/terracotta` existen. |
| Riesgo de juego | Medio-bajo. Quita el retículo de luz del suelo en calles y patio: con la pasada de luz de `main` (froglights incrustados) o con la de `1-light` queda cubierto, pero hay que mirar las celdas oscuras en `PlanReport`. Hay toriis y bancos en las avenidas y losas de bordillo en el verde de los lotes: comprobar en el render que las puertas de los barrios siguen libres. Usa radios fijos (`PATIO_R`, `GALLERY_R`, `RAMP_R`, `AVENUE_HALF`), así que no escala. |

### `citadel/2-hub` (basílica: cubierta, torres, fachada, interior)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | El javadoc de `CitadelHub` enlazaba métodos movidos a `HubFacade` (`hubWallColumn`, `naveGable`, `aisleCap`, `portals`). No rompe javac porque no hay `-Xdoclint`, pero estaba mal. Commit `1b6ab9a`. |
| Riesgo de compilación | Bajo. Los imports estáticos eliminados solo se nombran en javadoc. No se usan fuera los métodos públicos eliminados. `vaultY` cambia de forma, pero conserva su firma. |
| Riesgo de juego | Medio-bajo. Bancos (x=±2..4, z=7 y 9), rincones (x=±8..11, z=±8), macetas y pilastras: dejan libres el eje de la nave (`|x|≤1`), la entrada `(0,y0+1,11)`, los walk spots `(±4,±4)`, los bancos y sus anchors (`x=±7/±8`, `|z|≤3`), la columna del holograma, la cola del tablero, la pantalla este y el muro de hilos oeste. Los contrafuertes (x=14..15) y las torres (±9,±14) quedan fuera de los puentes N/S. Revisar el pathfinding alrededor de los bancos en el render. |

### `citadel/2-nodes` (torre de revisión, rotonda, claustro, lounge, pilares)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Nada (commit `d86ec8b`). |
| Riesgo de compilación | Bajo. `RotundaRoom` llama a `CitadelNodes.door(dx, dz, 6)` (3 args, existe en esta rama). `pillar` gana parámetros y su único llamador está actualizado. |
| Riesgo de juego | Bajo. Las anchors y walk spots de los nodos son idénticos a `main` (pantallas de review y schedule, `ARCHIVE_TERMINAL`/`ARCHIVE`, lounge en y=-2). La torre llega a y=39 con la aguja, dentro de `top()=40`. `NodeDecor` solo escribe dentro del nodo y antes de los fixtures. Como en `main`, la pantalla de review tapa la puerta norte de la torre; las otras tres puertas siguen libres. |

### `citadel/2-houses` (fachada e interior de las salas por estilo)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Faltaba su línea en `CHANGELOG-dev.md`: añadida (RELOAD). Commit `a9022a2`. |
| Riesgo de compilación | Bajo. Usa `CitadelCanvas.Mats` (record público), `Room.cell/c/w/d/back/side` y nombres cualificados (`dev.t3craft.hq.citadel.barrio.*`). |
| Riesgo de juego | Bajo-medio. `HallInterior.furnish` corre antes de los desks y respeta las columnas de los 6 slots, el pasillo de la puerta y la escalera. Las anchors `module_*` y `walk_module_*` no cambian. Solapamiento con `citadel/lead-office-hook` (ver conflictos). |

### `citadel/2-roofs-yards` (cubiertas, patios, portería)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Nada (commit `87208d2`). |
| Riesgo de compilación | Bajo. Los métodos de `CitadelBarrios` quedan como envoltorios públicos que delegan, con las mismas firmas. Las llamadas cruzadas `BarrioGate` → `CitadelBarrios.barrioSign/rotation` → `BarrioGate.barrioSign` no hacen recursión. |
| Riesgo de juego | Bajo. El cartel colgante y su `signTag` siguen siendo fixture como en `main`. El paso de la portería tiene 3 de alto. Los bancos del patio están en `|b|=2`, `|a|≥g-2`, así que el walk anchor (2,2) y la cruz de caminos quedan libres. La variante cenador ocupa (±1,±1), lo mismo que ocupaban los macizos de `main`. |

### `citadel/2-nature` (seis especies de árbol, parcelas vacías, terrazas, patio)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Nada (commit `ccf4c5e`). |
| Riesgo de compilación | Bajo. Se quitan de `CitadelGrounds` los imports `b`, `colX`, `colZ`, `Lot` y `List`, y no quedan usos. `CitadelGrounds.tree` se mantiene (lo usan los barrios). |
| Riesgo de juego | Bajo. Las hojas colgantes del cerezo llorón están a y≥4, por encima de la cabeza. Las raíces (troncos tumbados a y=1) solo ocupan las 4 celdas vecinas del tronco. Las plantas solo se ponen sobre tierra y nunca sobre pavimento, así que el walk anchor de los patios no se ve afectado. La capa `GROUND` cambia el suelo bajo el árbol (raíz y podzol) aunque esté pavimentado, lo que es solo cosmético. `nature.Dims` tiene escala propia fija en 1.0, a la espera del port de `scale`. |

### `citadel/2-office` (oficina de Facundo, pagoda en la terraza SSE)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Nada (commit `e31f6b8`). |
| Riesgo de compilación | Bajo. La anchor vive en `CitadelOffice.OFFICE` / `OFFICE_BALCONY` (constantes propias, no en `AnchorNames`). El kernel no valida nombres de anchor, así que son aditivas. `Blocks.BED.pick(..)` con `BED_PART` HEAD/FOOT y `Blocks.WALL_BANNER.pick(..)` son correctos; `c.layout` existe. |
| Riesgo de juego | Bajo-medio. Centro (12,28), puerta a nivel del paseo (y0) mirando al norte, altura máxima ≈ y 30. **El zócalo 9×9 (m≤4) entra 1–2 columnas en la calle interior** (esquina SE, r≈35.3–35.8 frente a 35): la calle tiene 5 de ancho, así que no la corta, pero hay que mirarlo en el render. Al integrar: mover las anchors a `AnchorNames.OFFICE` y `standing()`, como pide su propio javadoc. `feat/lead-office` (ya en `main`) no usa ningún nombre de anchor `office*` (comprobado en `AnchorNames` y `CitadelLeadOffice`), así que no hay colisión. |

### `fix/plan-connect` (conexiones de vallas, muros, paneles y escaleras)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Nada (commit `0b7a0b1`). |
| Riesgo de compilación | Bajo. `Block.column`, `Block.boxZ`, `Shapes.rotateHorizontal`, `WallBlock.PROPERTY_BY_DIRECTION/UP`, `CrossCollisionBlock.PROPERTY_BY_DIRECTION` y `StairBlock.FACING/HALF/SHAPE` existen en 26.3. `BuildPlan.replacing(UnaryOperator<Placement>)` y `Placement(rel, state, be)` también. `PlanConnect` es pública (el daemon carga el kernel en otro class loader; ver `fix/render-kernel-access`). |
| Riesgo de juego | Medio. Afecta a **todos los layouts** (`HqGenerator` aplica `PlanConnect.apply` a cada plan, packs incluidos) y cambia la firma del plan, así que los HQ existentes verán diffs de bloques conectados en el siguiente plan. `PlanProbe` deja de usar `updateShape` de vanilla. |

### `fix/new-thread-hitch` (detector de trabones)

| | |
|---|---|
| Estado | **Lista para compilar.** |
| Corregido | Nada (commit `9a932a0`). |
| Riesgo de compilación | Bajo. `Offload.supply(String, Supplier)`, `BuildPlan.fingerprint()`, `PerfStats.step/count` y `HitchWatch.step` existen (este último es package-private, pero está en el mismo paquete `debug`). Los nombres cualificados con `@Nullable` (`java.util.concurrent.@Nullable CompletableFuture`) son válidos. |
| Riesgo de juego | Medio (concurrencia). El demo planifica fuera del hilo del cliente: el lambda de `DemoHqSource.prepareLater` lee campos mutables (`previous`, `lift`, `previousFixtures`, `previousPack`, `island`) desde otro hilo, y `HqLayouts.use(effective)` es estado global fijado antes de lanzar. Si el roster cambia mientras tanto, `DemoHqBindings` devuelve el resultado anterior hasta que llega el nuevo, que es lo esperado. Probar a crear varios hilos seguidos en el demo. La deuda de luz del `BuildBudget` solo actúa con `autoThrottle`. |

### `citadel/1-connect` (sustituida por `fix/plan-connect`)

| | |
|---|---|
| Estado | **Descartar.** |
| Corregido | Nada (no se toca). |
| Riesgo de compilación | No aplica. Tiene código de depuración (`TMPDEMO` mete vallas y muros de prueba en el plan a (-10,40,-10); `TMPDIAG` con `System.out`). |
| Riesgo de juego | No aplica. **Lo que tiene y `fix/plan-connect` no:** solo el estado `in_wall` de las puertas de valla entre muros (lo daba el `updateShape` de vanilla; las reglas explícitas de `plan-connect` conectan muros y vallas con la puerta, pero no bajan la puerta). Es cosmético; si se quiere, es una regla de pocas líneas en `PlanConnect`. Todo lo demás (vallas, muros LOW/TALL/post, paneles y barrotes, esquinas de escaleras) está en `plan-connect`, con mejores reglas. |

## Conflictos y solapamientos

Textuales, según `git merge-tree`:

| Par | Archivo | Resolución recomendada |
|---|---|---|
| Casi todas las ramas, entre sí y con `main` | `docs/CHANGELOG-dev.md` | Conservar todas las líneas, la más reciente arriba. |
| `1-light` + `fix/plan-connect` | `CitadelFinish.java` (solo imports) | Conservar `import dev.t3craft.hq.plan.PlanConnect;` y quitar `import dev.t3craft.hq.plan.PlanLight;` (`1-light` ya no lo usa). Orden resultante en `build()`: `fillAir`, `CitadelAmbience.lights`, `PlanShapes.resolve`, `PlanConnect.apply`, grupos, `light`. Es correcto: las luces invisibles no conectan con nada. |
| `scale` + `1-light` / `1-paths` / `2-hub` / `2-nodes` / `2-houses` / `2-roofs-yards` / `2-nature` | `CitadelFinish`, `CitadelGrounds`, `CitadelHub`, `CitadelNodes`, `CitadelBarrios` | No resolver a mano archivo por archivo: rehacer `scale` encima del swarm (ver orden). |
| `citadel/lead-office-hook` + `2-houses` / `2-roofs-yards` | `CitadelBarrios.hall` (merge limpio salvo el CHANGELOG) | Merge limpio. Semántica: en un módulo lead, `HallInterior.furnish` (2-houses) amuebla antes de que `CitadelLeadOffice.place` ponga su oficina. Comprobar en el render que no se pisan. Si chocan, saltar `furnish` en los módulos lead. |
| `citadel/lead-office-hook` + `scale` | `CitadelBarrios` | Conflicto real: portar el hook dentro del port de `scale` (`FH` → `c.fh`). |

Merges limpios que tocan el mismo archivo, revisados a mano:

- **`1-paths` + `2-nature`** (`CitadelGrounds`): el resultado compila a mano (imports coherentes, nada sin usar
  que falte, llaves balanceadas). Los alcorques de `1-paths` (`PATIO_TREE_R` = 19.3) coinciden con los cerezos de
  `2-nature` (`polar(19.3, (k+0.5)π/4)`). Sugerencia opcional: que `CitadelStreetscape.tree` use `nature.Trees`.
- **`2-houses` + `2-roofs-yards`** (`CitadelBarrios`): limpio. `2-houses` usa nombres cualificados y `2-roofs-yards`
  imports, así que no chocan. Los paquetes `barrio/Hall*` y `barrio/Barrio*` conviven sin nombres repetidos.

**Semántica de `scale` frente al swarm** (lo que hay que portar al rehacer `scale`):

| Rama | Usa símbolos que `scale` elimina o cambia |
|---|---|
| `1-light` | `FH` y `SPAWN` en `CitadelFinish` (se reescribe `walkable`/`light`). |
| `1-paths` | `Y0`, `PATIO_R`, `GALLERY_R`, `RAMP_R` y `AVENUE_HALF` en `CitadelGrounds`, `CitadelPaving` y `CitadelStreetscape`. |
| `2-hub` | `Y0`, `AISLE_TOP`, `SPRING`, `vaultY(int)` y `arcadeZ(int)` en `CitadelHub` y en todo `hub/*` (`HubDims` tiene medidas fijas). |
| `2-nodes` | `CitadelNodes.door` de 3 args en `RotundaRoom`; medidas fijas en `nodes/*`. |
| `2-houses` | `FH` y `STAIR_STEPS` en `CitadelBarrios`, `HallFacade` y `HallInterior`. |
| `2-roofs-yards` | `FH` y `STAIR_STEPS` en `CitadelBarrios`. |
| `2-nature` | `Y0`, `PATIO_R`, `GALLERY_R` y `RAMP_R` en `CitadelGrounds`; `nature.Dims` fijo en 1.0 (debe leer `RingCampusGeometry.dims()`). |
| `2-office` | `Y0` y `GALLERY_R` en `CitadelOffice` (calcula `CX`/`CZ` en un `static final`, que habría que convertir en métodos o en campos del canvas). |

## Orden de integración recomendado

Cada paso es un merge a `main` seguido de compilación por el coordinador (`tools/heavy.sh`). Los pasos de la
ciudadela necesitan además un render y su `PlanReport`, con aprobación de Facundo.

1. **`fix/plan-connect`**: 4 commits por detrás, kernel pequeño, aislado. Conflicto: solo el CHANGELOG.
2. **`fix/new-thread-hitch`**: cliente y build, independiente. Conflicto: solo el CHANGELOG.
3. **`citadel/1-light`**: conflicto de imports en `CitadelFinish` con el paso 1 (resolución arriba).
4. **`citadel/1-paths`**, luego **`citadel/2-nature`**: mismo archivo, merge limpio.
5. **`citadel/2-hub`** y **`citadel/2-nodes`**: independientes.
6. **`citadel/2-houses`**, luego **`citadel/2-roofs-yards`**: mismo archivo, merge limpio. Después,
   `citadel/lead-office-hook` si entra en esta tanda.
7. **`citadel/2-office`**: mover sus anchors a `AnchorNames` en el propio merge o en un commit aparte.
8. **`citadel/scale` como port** (rama nueva, p. ej. `citadel/scale-port`, desde `main` ya integrado): conservar
   `RingCampusGeometry.Dims`, los helpers del canvas, `LayoutParams.Spec.unit`, el parámetro `scale`, `RingCampusStyle`
   y `RingCampusViews` tal cual, y aplicar su regla (`c.s/c.sy/c.sd`, `c.y0`, `c.fh`, `g.*`) a los archivos del swarm
   de la tabla anterior. Es más barato que portar ocho ramas a la vez sobre `scale`, porque el swarm se diseñó a
   escala 1. Al terminar, la firma del plan a `scale=1.0` debe coincidir con la del paso 7. **Valor por defecto
   recomendado: 1.0** hasta que Facundo apruebe 1.5 viendo el render.
9. **`citadel/1-connect`**: no se integra; se puede borrar (rama y worktree
   `/home/user/.t3/worktrees/t3craft/citadel-1-connect`) cuando se decida lo de `in_wall`.
