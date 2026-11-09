# Citadel catalog

A catalog of small designs for the citadel (`hybrid-ringcampus`): houses, annexes, market stalls, fountains, wells, trees,
gardens, lamps, benches, statues, gates, bridges, plaza centres, planters, signposts, banner poles, gazebos, tower tops and
roofs. Each design is a **variant** in its own file, written by one agent. Later, the **family leaders** place them in the
city with `Catalog.pick`. Building a variant does not change how the city is generated.

Code: `mod/src/main/java/dev/t3craft/hq/citadel/catalog/`

| class | what |
| --- | --- |
| `Plot` | the box a variant builds in: `x0, y0, z0`, `w × d × h`, `front`, `seed`. Local coordinates, rotation, clipping, `rng`, `hash` |
| `Family` | the families, each with its typical box size in the javadoc |
| `Variant` | the interface: `id`, `family`, `minW/minD/minH/maxW/maxD`, `weight`, `build(c, p)` |
| `Catalog` | `of(Family)`, `pick(Family, Plot[, weights])`, `byId`, `all` |
| `CatalogEntries` | the registry: one line per variant |
| `house/HouseTimberCottage`, `lamp/LampCherryPost` | the two reference variants (the quality bar) |

## Create a variant in 5 steps

1. **Pick the family and a name.** The id is lower case and starts with the family: `well_roofed_stone`,
   `tree_cherry_weeping`. The class is the id in CamelCase, in the family's sub-package:
   `catalog/well/WellRoofedStone.java`. Create the folder if it does not exist yet (`catalog/<family in lower case>/`).
2. **Copy a template.** `lamp/LampCherryPost` for small furniture, `house/HouseTimberCottage` for buildings. Implement
   `id()`, `family()` and the size range. `minH` is the height the design needs. Look up the typical box in the
   `Family` javadoc and cover most of it.
3. **Write `build(c, p)` facing NORTH.** The front is `v = 0`. `u` runs left → right as you look out through the front.
   `y = 0` is the first layer above the ground. Write blocks only through `p.set(c, u, y, v, state)`, `p.fill(...)` and
   `p.put(..., layer)`. They rotate the state for the real `front` and drop anything outside the box. Directions in states
   are local: `NORTH` means towards the front. For variety, use only `p.rng(salt)` and `p.hash(u, y, v, n)`.
4. **Register it.** Add **one line** to `CatalogEntries.java`, in alphabetical order of the id. Do not reorder or
   reformat anything else:
   ```java
   new dev.t3craft.hq.citadel.catalog.well.WellRoofedStone(), // well_roofed_stone
   ```
5. **Render it and iterate** (next section) until it passes the quality rules. Do at least 3 visual iterations, then
   commit.

## The contract

- **Only inside the plot.** Never call `c.set` / `c.fill` directly. The gallery reports `clipped` (writes `Plot` dropped)
  and `outside` (blocks set straight on the canvas outside the box). Both should be 0.
- **Any size in its range.** It must look right for every `w` in `minW..maxW`, every `d` in `minD..maxD` and every
  `h ≥ minH`. Extra height may stay empty. `Catalog.pick` only gives a variant plots that fit (`Variant.fits`).
- **Deterministic.** The same box, front and seed always produce the same blocks. No `Math.random`, no static state.
- **No game anchors, workstations or fixtures** unless a family leader asks for them. A variant is decoration;
  `p.anchorPos(u, y, v)` gives the position if a leader does ask.
- **The ground is not yours.** `y = -1` is outside the box. Paths around the plot are the leader's job.
- **Layers:** `CitadelCanvas.SOLID` (the default) for most blocks, `DECOR` for signs and things stuck to a wall,
  `HANGING` for lanterns and chains hanging from above.
- **Connections by hand.** The plan keeps states exactly as you give them, so fences, panes and walls need their sides
  set (`BlockStateProperties.NORTH…`). A pane with no sides set draws as a thin post.

## Render it

Use the shared render service only. Never call gradle or java yourself:

```bash
tools/render/submit.sh -- --catalog WELL:well_roofed_stone      # just yours (fast)
tools/render/submit.sh -- --catalog WELL                        # your whole family, to compare
tools/render/submit.sh -- --catalog WELL:well_roofed_stone --front all   # the 4 orientations, one row each
tools/render/submit.sh --final -- --catalog WELL:well_roofed_stone --scale 1.5 --ss 2   # full detail
```

- **Spec:** `--catalog FAMILY[:id][,FAMILY2[:id2]…]`. A bare id also works.
- **Builds:** each variant is built in up to 3 box sizes spread over its range (min, middle, max; capped at the family's
  typical height and 24 wide). Each size gets 2 seeds. Every build sits on a grass pad ringed by a path, and the id,
  size and seed are written under it.
- **Options:**
  - `--sizes N` and `--seeds N` change the number of sizes and seeds.
  - `--front N|E|S|W|all` sets the orientation. The default is `S`, so the facade faces the `iso_se` camera.
  - `--only iso_se` keeps only the views whose name contains that text.
- **Output:**
  - `catalog_iso_se.png`, `catalog_iso_nw.png` and `catalog_plan.png`, zoomed to fit automatically. `--scale` multiplies
    the zoom: 1 ≈ 1400 px across, and previews use 0.8. `--ss` sets supersampling.
  - `report.txt` lists, for every build, the block count, the `clipped` and `outside` counts and any build error, plus the
    palette of each variant. The last line says `OK: every build stayed inside its plot.` or gives a `WARNING`.

Check `--front all` at least once. A stair, door, log or lantern that points the wrong way in the E/S/W rows means the
state was not set in local terms.

## Quality rules

The art direction is mandatory: `~/.cache/t3craft-coord/citadel-art-direction.md`. In short:

- **Depth on every face:** no flat wall bigger than 3×3. Use protruding posts, sills, lintels, beams, cornices of stairs
  or upside-down slabs, and recessed doors.
- **Palette of 3–5 materials per surface, dark → light from bottom to top.** Deepslate and polished stone at the base,
  a middle body, and light trim on top. Mix textures (cracked, mossy, chiseled) with `p.hash`, never 100% uniform. The
  citadel palette is cherry pink, dark deepslate, calcite and light stone, with bamboo and gold accents and tinted glass.
- **Roofs with character:** eaves of 1–2 blocks, stairs and slabs, a marked ridge, chimneys, dormers and finials. Never
  a plain prism.
- **Silhouette:** vary the heights and the masses. Two variants of a family must not share a silhouette.
- **Transitions and human scale:** a plinth, steps and planters where a building meets the ground. Lanterns, signs,
  shutters and pots next to doors.
- **Light:** no visible light blocks in floors. Visible light is decorative: lanterns, lamps, stained glass.
- **Score your render 1–10** like a build-contest judge. Below 7 is not done.

The two reference variants carry `[depth]`, `[palette]` and `[finish]` comments showing these techniques.

## For family leaders

```java
Plot p = Plot.ofWorld(x, y, z, sizeX, sizeZ, h, Direction.SOUTH, seed);  // world footprint; w/d swap for E/W fronts
Variant v = Catalog.pick(Family.HOUSE, p);                               // null when nothing fits
if (v != null) v.build(canvas, p);
```

- **Choosing:** `Catalog.pick(f, p, Map.of("house_timber_cottage", 3))` overrides the weights. `Catalog.of(f)` lists a
  family's variants and `Catalog.byId(id)` picks one by id.
- **Coordinates:** `new Plot(x0, y0, z0, w, d, h, front, seed)` takes local `w` (frontage) and `d` (depth). `y0` is the
  canvas y of local `y = 0`, the first layer above the ground.

## Placement

`citadel/CitadelPlacement` is the family leader: after the barrios and empty lots are painted, `RingCampusGenerator` calls
`CitadelPlacement.city(canvas)`, which places catalog variants in the space that is still free. You can switch it off with
the pack param `catalog.enabled = 0` (`LayoutParams.RINGCAMPUS_CATALOG`, hybrid-ringcampus). The city is then exactly as it
was before the catalog.

**What the leader asks for.** The tables below list the plot sizes it requests, as local `w × d × h` (frontage × depth ×
height; `h` is the room it leaves). A plot only gets a variant whose `minW..maxW`, `minD..maxD` and `minH` cover it. Sizes
the family does not cover are simply skipped. **If you design a variant, cover these ranges.** A wide range gets used
far more than a narrow one. When a family has nothing that fits, the leader places nothing, with three exceptions that
have fallbacks: `TREE` gets the citadel's cherry tree (needs ≥ 5 × 5 × 7), `GARDEN` gets a hedge garden (≥ 5 × 5), and
`PLANTER` gets the kit's planter.

### Barrio lots (a project's lot)

The halls, desks, workstations, anchors and walk spots stay as they are, and so do the courtyard, the gatehouse and the
paved arms. A strip of 2 grass cells round the arms also stays clear. The leader works on the four corner squares between
the arms and on the belt between the cluster and the lot's edge. It keeps 1 cell from the streets and 1 cell between plots.

1. **Corridor.** A paved path, 3 wide with a 1-block curb on each side. It runs from the end of the gate arm straight to
   the street, so every hall door has a path at least 2 wide to the street: courtyard, gatehouse, arm, corridor.
2. **Furniture along the corridor** (every 4 cells, both sides, facing the path). Lamps alternate with benches or
   planters. More lamps line the gate arm between the gatehouse and the cluster's edge.
3. **Plots**, in this order. Each one goes as close to the paths as it can, except trees and the small gardens, which are
   scattered by seed:

| family | asked size (w × d × h) | how many |
| --- | --- | --- |
| `FOUNTAIN` (or `WELL`, chosen by the lot's seed) | 5–7 × 5–7 × 7 (well: 3–5 × 3–5 × 8) | 1 |
| `ANNEX` | 5–8 × 5–7 × 10 | 2 (ring 1), 3 (rings 2–3) |
| `GARDEN` | 6–11 × 5–9 × 5 | 2 (ring 1), 3 (rings 2–3) |
| `TREE` | 5–7 × 5–7 × 14 | 4 + 2 × ring |
| `GARDEN` (small) | 5–7 × 5–7 × 5 | 2 |
| `BENCH` | 2–3 × 1 × 3 | 3, plus the corridor ones |
| `PLANTER` | 1–2 × 1 × 3 | 6, plus the corridor ones |
| `LAMP` (corridor, gate arm) | 1 × 1 × 5 | every 4 cells |

4. **Ground texture.** The grass left over gets patches 4 cells across: meadow, flowers, moss, bare earth or quiet grass,
   using plants with no collision only. A last pass then thins plain grass until it covers at most 30% of the lot.

The front of a plot faces the nearest path. In the corner squares that means the gate arm or the nearer arm; beside the
corridor it means the corridor; on the belt it means the cluster.

### Empty lots

The lot's seed picks one of three compositions. Each is packed from the lot's centre outwards:

| composition | plots |
| --- | --- |
| park | `FOUNTAIN` 5–9 × 5–9 × 7, `GARDEN` 8–12 × 7–12 × 5, 3 `BENCH`, 6 + 2 × ring `TREE` (scattered), 2 small `GARDEN` 5–8 |
| small plaza | a paved square of 13 (ring 1) or 15 (rings 2–3), down to 11, with a 2-cell walk ring. In the middle goes a `PLAZA_CENTER` (7–11 square × 10), else a `FOUNTAIN` of that size, else a tree. Each corner gets a `LAMP` 1 × 1 × 5. Round it go 4 `BENCH`, 2 `GARDEN` 6–10 × 5–8 and 6 + ring `TREE` |
| orchard | `WELL` 3–5 × 3–5 × 8, 10 + 2 × ring `TREE` 5–6 × 5–6, 2 `GARDEN` 6–10 × 5–8, 2 `BENCH` |

Every composition also gets 4 `PLANTER` and the same ground texture.

### Streets and avenues

All of these sit on the edge band. The middle of every street stays clear.

| family | where | asked size |
| --- | --- | --- |
| `GATE` | where each **cardinal** avenue enters its ring of barrios. It sits 1 cell in from the ring's inner edge, or 5 when that spot is refused (ring 1 south, by the spawn). The path runs front → back through its middle; the front faces the hub | 7, 9 or 5 wide × 3, 2 or 4 deep × 10 |
| `PLAZA_CENTER` (else `FOUNTAIN`) | at each crossing of a ring street with its avenues, when the square plus a 2-cell walk ring fits on that ring's street, avenue and free lot grass (lot grass gets paved). It may slide up to 10 cells inwards along the avenue. Ring 1's crossings hold the node rooms, so none go there | 11, 9 or 7 square × 10 |
| `LAMP` | on both edges of every avenue and ring street, every 10 blocks, and on the inner street's outer edge. None within 5 blocks of a crossing or a ramp, 2 of a node room or 4 of the spawn | 1 × 1 × 5 |
| `BENCH` | between lamps, every 3rd interval, facing the street. Benches only go where the whole box stays on the edge band, which in practice means straight stretches near the cardinals | 3 (else 2) × 1 × 3 |

### Hub

On the outer rim of the promenade, one spot between each pair of ramps (slightly off the light posts) gets a `STATUE`
2 × 2 × 8. A spot that cannot take a statue gets a `LAMP` 1 × 1 × 5 (a brazier) instead. The inner part of the
promenade stays a free walk. The nodes are not touched.

### Rules the leader follows

Before building a plot, it checks on the canvas (`zoneAt`, `at`) that the plot is in the right zone, on unpaved ground
(grass, moss or a lattice light) or on a street's edge, and that nothing stands above it up to its height. Azalea bushes
of the lot ground are the exception: they are cleared. A plot is never placed on a hall column, an anchor column or the
spawn. Everything is deterministic: one seed per lot or street position and a fixed scan order. A variant that throws is
logged and skipped, and the city still builds.
