# feat/build-radial: build around the player, ring by ring

## What Facundo asked for (2026-11-09)
After feat/build-near-player: "funciona mucho más rápido pero no era lo que esperaba".
- **No more part by part.** Build around the player: first, and finished, everything within about
  5 blocks (ground, walls, roofs and decoration of those columns, bottom-up).
- **Grow outwards** in rings (5 → 10 → 15…) once the ring is complete. When the player moves, the centre
  moves with them: their new 5-block radius goes first, what was pending elsewhere waits.
- **A visible pace**: neither endless nor instant. The fast foundation stays, much slower and
  configurable (the island in ~1–2 min instead of 5–9 s), in the same radial order; the builders are
  still seen working on the visible part.

## How it works
### One plan, one crew (demo)
`DemoBuildRun` with `build.order: "radial"` (the default) merges every part into one plan, off-thread
(`build.radial_merge_async`). Later parts win a shared cell, so the world ends as it would with the parts
one after another. One crew then builds that plan. `"parts"` keeps the old part-by-part run (with
`NearPriority`) as the fallback.

Every other free crew job bigger than `SYNC_PLAN_LIMIT` (256 entries) is also radial inside itself: site
builds, and HQ builds in creative. Those callers still submit one job per part.

### `RadialCursor` (new `JobCursor`)
Planned off-thread in the job's `DeferredCursor` (`build.radial_plan_async`):
- `Foundation.of(plan)` (the same split as before; `build.fastFoundation: false` sends everything but
  air to the builders);
- columns `(x, z)` with their entries sorted bottom-up;
- a cell → entry hash;
- the first ring order.

**Order.** Columns are sorted by ring, `floor(horizontal distance to the centre / build.radialRing)`, then
by distance (quarter blocks), then by index. With `radialRing` 5:
- ring 0 = everything within 5 blocks;
- ring 1 = within 10 blocks;
- and so on.

**Inside a column: strictly bottom-up, never floating.** The head (lowest entry not applied) may go when:
- it is the column's lowest entry (it rests on the terrain, or it is the bottom of the void island); or
- the cell below holds an applied solid entry; or
- where the column has a gap (a roof over a hall, a balcony, a bridge): one of its 4 side cells does, and
  for decoration also the cell above (hanging lanterns);
- falling blocks (sand, gravel, concrete powder) need the cell below;
- air (clearing) needs nothing and supports nothing.

A head that is not supported **stalls its column**. The column wakes when a neighbour cell of the head is
applied: every applied entry checks its 6 neighbours, which is 6 hash lookups. Stalled columns never hold
the expansion back. For example, the roof over the player waits for the walls of the next ring, and then
the roof spreads cell by cell from the walls inwards. If every column left is stalled (a plan with truly
floating pieces), the nearest one is forced, as the crew planner does.

Group dependencies (the island before what stands on it) come out of this on their own: the island is the
bottom of every column.

**Two streams over the same order.** Each stream has its own pointer and is woken when a column becomes
its turn:
1. **Foundation**: foundation entries, air, and builder entries already right in the world (resume:
   checked through the `already` probe, at most 1024 world reads per tick). No builder takes part, and
   `frontOf` is -1, so there is no animation and no client event. Budget:
   `BuildBudget.foundationBlocks()`, which is `build.foundationBlocksPerTick`, `foundationMsPerTick` and the
   MSPT/FPS throttle, as in feat/build-near-player.
2. **Builders**:
   - Each builder claims the free column of the nearest open ring that is closest to where it stands. If
     the current ring has no free column left (every column claimed, stalled or done), it takes one from
     the next ring.
   - It walks to the cell beside the column on its own side. The `Walker` event goes to `BuildCrew`, as
     with `CrewCursor`.
   - It places the column bottom-up, at most `blocksPerTick` blocks per tick per builder, out of the
     crews' budget (`BuildBudget.claim`).
   - It raises its spot when the head is more than 3 above its feet (scaffold).
   - It lets the column go when the column is done, stalled, or its head is foundation again.

**Centre.**
- `BuildManager` asks each radial job's level every tick for its nearest non-spectator player within
  `buildNearPlayer.radius` (48) horizontally and 32 vertically of the job's box. That is a few
  multiplications per player.
- `BuildJob.focus` turns the position into plan coordinates.
- `RadialCursor.focus` starts an off-thread re-order (`build.radial_order_async`) only when:
  - the centre moved more than 4 blocks since the last order; or
  - 3 s passed and it drifted at least 1 block;
  - never sooner than 0.75 s after the last order, and one at a time.
- The game thread installs the finished order at the start of its next tick. Its scans start again from
  the new ring 0, and the old pending columns simply rank later.
- Hysteresis comes from the rings and from the 4-block threshold.
- **No player in the build:** the centre is the plan origin when the origin is inside the footprint (the
  demo's hub, where the player spawns), otherwise the middle of the footprint.

### Game-thread cost
- No world reads besides the placements themselves and at most 1024 probes per tick.
- No sorting.
- Bounded scans: 8192 columns per tick for the foundation stream (plus a time check every 256), and 1024
  per builder pick.
- About 10 hash lookups per applied entry.
- `stateOf` caches a head found supported, so a pick does not redo the support check of a whole ring.

Timer `build.radial` covers both streams, every tick.

## Pace (estimates, not measured in game)
Citadel with 4 projects: base ≈ 395 000 entries, of which the void island is ≈ 360 000
(docs/feat-build-near-player.md).

| | feat/build-near-player | feat/build-radial |
|---|---|---|
| foundation blocks/tick | 4096 (cap 10 ms) | **192** (cap 10 ms) |
| void island | 5–9 s | 360 000 / 192 ≈ 1 875 ticks ≈ **94 s** |
| whole base | 10–20 s | 395 000 / 192 ≈ 2 060 ticks ≈ **1.7 min** |
| builder pace (floor) | 12 blocks/tick | **6** blocks/tick |
| visible, 8 builders | up to 96/tick | up to 48/tick before walks (≈ the pre-near-player 48) |

The island is no longer laid layer by layer over the whole footprint. It now grows as a disk from under the
player outwards, each column 10 deep in one go. The builders start on the walls of ring 0 as soon as their
ground is there, so something stands next to the player within seconds.

- Want the island in ~1 min: `foundationBlocksPerTick` ≈ 300.
- Want it in ~2 min: ≈ 150.

## Config (additive, `config/t3craft.json`)
```json
"build": { "order": "radial", "radialRing": 5, "foundationBlocksPerTick": 192, "blocksPerTick": 6 }
```
- `order`: `"radial"` (default) or `"parts"` (the previous behaviour, part by part; the demo keeps
  `NearPriority`).
- `radialRing`: 1–64 blocks.
- `foundationBlocksPerTick`: the default went from 4096 to 192.
- `blocksPerTick`: the default went from 12 to 6.

`/t3craft debug build-tuning reload` re-reads them. `order` is read when a demo run or a job starts.

## PerfStats steps
| step | thread |
|---|---|
| `build.radial` | server, one tick of a radial job |
| `build.radial_plan_async` | async, plan analysis |
| `build.radial_order_async` | async, re-order around the moved centre |
| `build.radial_merge_async` | async, merging the demo's parts |

## Pending / not done
- **Not compiled or tried in game:** the coordinator compiles.
- **Survival (charged jobs) keeps the crew path.** Materials are checked per segment of a fixed simulated
  order, and a dynamic order has no fixed segments. Moving survival to radial would need a material check
  per column, or per ring.
- **`HqConstruction` (survival or creative HQ updates) still submits one job per part.** Each free job is
  radial inside itself, but the parts are not merged as the demo's are.
- **Builders that run out of work in their ring take the next ring.** So with 8 builders and a small
  ring 0, some already work on ring 1 while ring 0 finishes. Otherwise they would stand idle.
- **The builder spot is simple:** the neighbouring cell on its side, feet one below the block or on top
  of what is built there. `BuildCrew` already leaps or walks straight when there is no ground.
- **The roof over the player can only close once the walls of the next ring stand.** That is the price of
  the no-floating rule.
