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
* **Shapes**: fences, walls, panes, bars and stairs whose state differs from what vanilla gives them among
  their real neighbours (`Block.updateFromNeighbourShapes` against the plan). `cornersMissing` counts those
  that should connect on 2 or 3 sides and do not: the round fences without corners.
* **Floating**: groups of blocks connected to nothing that reaches the ground (y <= 0); **unsupported**:
  blocks vanilla's `canSurvive` refuses where they stand (grouped by type, with a coordinate).
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
