# hqRender: piece views and plan report

`./gradlew hqRender -PhqArgs="..."` draws the HQ headless (no Minecraft window). Besides the full galleries
(`--style`, `--layout`, see the javadoc of `HqRender`), it can look at **one piece** of the plan and write a
**report** about it. Both exist so a designer of a door or a stretch of wall sees only that, in seconds, and
gets the problems the eye misses. The code is in `mod/src/hqrender/java/dev/t3craft/tools/hqrender/`
(`PieceViews`, `PlanReport`, `PlanSource`, `ImageCompare`); `PlanProbe` (in `dev.t3craft.hq`) is the read-only
bridge to the package-private helpers (`PlanNav`, `PlanShapes`, the group index).

> If `docs/tools/render-service.md` exists (the shared render service), the same flags are documented there
> too, as the service calls this code.

## Flags

A target flag switches to **piece mode**. The plan is the one of `--layout` (default `hybrid-ringcampus`, the
citadel) grown through `--projects` (default `10`; give the whole chain, e.g. `4,10,20`, to grow it the way
the game does, each roster allocated against the previous layout).

| Flag | Meaning |
| --- | --- |
| `--region x1,y1,z1:x2,y2,z2` | That box (plan-relative, inclusive, corners in any order). |
| `--module ID` | A module of `BuildPlan.modules()` by id (`id@...` pieces match too), padded by `--pad N` (default 1). |
| `--group NAME` | The blocks a group owns (`hub`, `module_3`, …; `BuildPlan.groups()`), same padding. |
| `--anchor NAME [--radius N]` | A cube of radius N (default 10) around an anchor; a unique part of the name is enough. |
| `--name NAME` | File-name prefix of the PNGs (default derived from the target). |
| `--cut-y Y` | Height of the plan view cut (default: 5 above the base of the piece). |
| `--section-at N` | Coordinate of the vertical section plane (default: the middle). |
| `--only a,b` | Only the views whose suffix contains a word: `iso_se`, `iso_ne`, `iso_nw`, `iso_sw`, `plan`, `section`. |
| `--scale F` | In piece mode, multiplies the automatic fit (about 1100 px across the piece): `1` = full, `0.5` = small preview. (In the full galleries it keeps its old meaning, default `1.6`.) |
| `--ss N` | Supersampling per axis; `--ss 1` is fastest. |
| `--no-npcs` | No NPC stand-ins. |
| `--compare DIR` | After the run, put each PNG written next to the one of the same name in `DIR` (before / after): `compare_NAME.png`, plus `compare.txt` (pixels that differ, and whether the two `report.json` have the same plan signature). Works in every mode. |
| `--report` | Piece mode on the whole plan. |
| `--report-only` | Report only, no images (whole plan unless a target is given). |
| `--no-report` | Images only. |
| `--no-ticks`, `--ticks-limit N`, `--crew N`, `--bpt N` | Build simulation: skip it; skip it above N entries (default 600000); builders (4); blocks per tick (48). |
| `--low-light N` | Light threshold for walkable cells (default 10). |

Written, in `--out` (default `build/hq-render`): `NAME_iso_se.png`, `NAME_iso_ne.png`, `NAME_iso_nw.png`,
`NAME_iso_sw.png` (four corners), `NAME_plan.png`, `NAME_section.png`, `report.txt`, `report.json`.

```
./gradlew hqRender -PhqArgs="--region -10,0,-10:10,12,10 --scale 0.5 --ss 1"
./gradlew hqRender -PhqArgs="--module module_3 --out build/hq-after --compare build/hq-before"
./gradlew hqRender -PhqArgs="--anchor walk_hub_0 --radius 12 --only plan,iso_se"
./gradlew hqRender -PhqArgs="--report-only --projects 4,10"
```

## Look: lighting, shadows, AO, filter

`Renderer` is a CPU ray caster (no GPU): one ray per sub-pixel walks the block grid and hits the textured quads
of each cell. `BlockModels` builds those quads from the **real vanilla models and textures** in the client jar
(blockstate variants and multipart, parents, element from/to, per-face uv, uv rotation, element rotations with
rescale, variant x/y rotations, `uvlock`, tint indices). Textures are sampled **nearest** (texel-exact, the
Minecraft look); smoothness comes from supersampling. Grass and foliage use the plains colours (no biome map,
the render has no world).

All of the following are flags of every mode (galleries, `--layout`, piece mode, `--catalog`). **They are on by
default (`--final`) and off in `--preview`.** Without either flag, `--ss 1` (what the render service adds to its
previews) counts as a preview. The service passes `--preview` to `hq` previews itself.

| Flag | On (final) | Off (preview) |
| --- | --- | --- |
| `--sky` / `--no-sky` | Linear-light shading: a warm sun from the south-west at about 45°, a cool sky hemisphere plus a warm ground bounce, a soft shoulder tonemap (linear up to 0.6, then eases into white; no toe, so shadows stay readable), and a light sky-gradient background. Faces the model marks `"shade": false` (plants, torches) are lit evenly. | The old flat look: Minecraft-like face factors (top 1, south/north 0.82, east/west 0.68, bottom 0.55) times a sun term, on a flat light-grey background. |
| `--shadows` / `--hard-shadows` / `--no-shadows` | Soft sun shadows: `--shadow-rays N` (default 2) rays per sub-sample toward a sun disc of 3° radius, stratified and jittered by sample, so a 3×3 supersampled pixel averages 18 rays and the penumbra widens with the distance to the occluder. Leaves cast dappled shadows (cutout texels are tested); glass and water do not. | One hard shadow ray (as before). `--no-shadows`: none. |
| `--ao` / `--no-ao` | Ambient occlusion in the spirit of smooth lighting, per pixel: the cells in front of the face are read at **half-block** resolution (contact shadows in joints, under slabs and stairs) and at **block** resolution (the broad gradient at wall bases and inner corners). Each corner of the cell holding the pixel gets Minecraft's vertex rule (two sides plus the diagonal); the four corners are interpolated bilinearly. A cell counts as solid from its opaque model elements (`BlockModels.occupancy`: which of its 8 half-block sub-cells are covered); glass, plants and fences do not occlude. In sky mode AO darkens the sky light fully and the sun only a little. | None. |
| `--bloom` / `--no-bloom` | A faint halo around emissive blocks (lanterns, froglights, glowstone…): their colour, gaussian-blurred (sigma about half a block), screen-blended at 15 %. | None. |
| `--filter mitchell\|tent\|box` | `mitchell`: Mitchell–Netravali (B = C = 1/3) over a 1.25 px radius, resolved in linear light. Edges are cleaner than the box average and stay crisp. | `box`: the plain average of the sub-samples. |

Cost (render PC, 11 threads, `--ss 3`): the hub of `--layout hybrid-ringcampus --projects 4 --only hub`
(6 images) took 13.0 s before and 15.1 s after; the catalog gallery
`--catalog HOUSE:house_timber_cottage,LAMP:lamp_cherry_post` took 2.2 s before and 4.7 s after. The extra cost
is the shadow rays. `--shadow-rays 1` or `--hard-shadows` halves it.

`Renderer.look` is a static that `HqRender` sets at the start of every run. A JVM therefore renders with one
look at a time, which suits the render service (1 worker on the PC).

## The report

Information for the designer, not a test: nothing fails. With a target, counts and lists cover that box;
reachability and the shape/floating checks look at the whole plan, so a piece is judged among its neighbours.

* **Signature**: SHA-256 of every cell (position and block state, air included, sorted by position, so the
  order the generator wrote them in does not matter) for the whole plan, for the scope, and per group; also
  `BuildPlan.fingerprint()` (order, anchors and pack too). Same `cells` before and after a refactor = the plan
  did not change, block by block (phase 0 of the citadel swarm must keep it). Per-group hashes say which
  piece moved when it did.
* **Blocks**: count per block type; light sources: how many, how many have an exposed face ("seen"), how many
  are lamps in the floor under a walkable cell, and per 100 walkable cells (the "full of lamps" measure).
* **Light**: walkable cells (reachable on foot from the `spawn` anchor, `PlanNav` rules) with block light
  (`HqLightMap`) under `--low-light`: histogram, clusters (cells, box, first cell) and coordinates.
* **Anchors**: every standing anchor and every desk reachable from the spawn (`direct`, or `adjacent` for
  seats and benches); the unreachable ones are listed.
* **Shapes**: fences, walls, panes, bars and stairs whose state differs from what `PlanConnect` (fix/plan-connect,
  see `docs/feat-plan-connect.md`) gives them among their real neighbours. `cornersMissing` counts those
  that should connect on 2 or 3 sides and do not: the round fences without corners.
* **Connect**: fences, walls and panes connected on no side although a neighbour they connect to is beside
  them (`connect:` line on stdout too). Must be 0; nothing is caught per cell.
* **Floating**: groups of blocks connected to nothing that reaches the ground (y <= 0); **unsupported**:
  blocks vanilla's `canSurvive` refuses where they stand (grouped by type and by group, with a coordinate; `byBlock`, `byGroup` and
  the first 2000 in `list` in report.json). Headless the block/fluid tags are unbound, so plants, lily pads, sugar cane and bamboo
  are judged by `PlanProbe.tagBasedOk` with the tag read from the vanilla data (`supports_vegetation`, …) instead of vanilla's `is(TAG)`.
* **Split pieces** (`split` in report.json): groups of hanging cells near a terrace step that the column lift would have torn and the relief lifted with their support (`CitadelRelief.lastSplit`), what `CitadelSettle` did with floating groups (fell / got a post / removed / left), and horizontal log beams with nothing solid under them or at their ends (`touch nothing` ones are also in FLOATING; `hang from a wall` ones are held from the side or above).
* **Build**: ticks the crew (`CrewSchedule` + `CrewCursor`, no world) needs for the scope, with `--crew`
  builders at `--bpt` blocks per tick, and the blocks it walks.

## From Java

```java
HqRender.bootstrap(false);                                   // once per JVM
var planned = PlanSource.plan(null, List.of(4, 10));         // null = the citadel
var session = PieceViews.open();                             // keeps the block models
var rq = new PieceViews.Request();
rq.target = PieceViews.Target.module("module_3");
rq.out = Path.of("build/piece");
rq.scale = 0.5;
PieceViews.Result r = session.render(planned.plan(), rq);    // images + report files
PlanReport report = PlanReport.analyze(planned.plan(), null, new PlanReport.Options());   // report alone
ImageCompare.run(Path.of("build/before"), Path.of("build/piece"), 0);                      // before / after
```
