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
"build": { "order": "radial", "radialRing": 5, "foundationBlocksPerTick": 192, "blocksPerTick": 6,
           "fastFoundation": false, "groundBlocksPerTick": 32, "fillBlocksPerTick": 128,
           "foundationRingsAhead": 1, "builderRingsAhead": 1, "fillRingsBehind": 2, "climbBlocksPerTick": 10 }
```
- `order`: `"radial"` (default) or `"parts"` (the previous behaviour, part by part; the demo keeps
  `NearPriority`).
- `radialRing`: 1–64 blocks.
- `foundationBlocksPerTick`: the cap for ground + fill together per tick (all jobs; MSPT/FPS throttle).
- `blocksPerTick`: the pace of one builder (floor), default 6.
- fix/build-radial-tune (see below): `fastFoundation` (now **false**), `groundBlocksPerTick` 1–4096,
  `fillBlocksPerTick` 0–65536, `foundationRingsAhead` 0–16, `builderRingsAhead` 0–16, `fillRingsBehind` 0–64,
  `climbBlocksPerTick` 0.5–64.

`/t3craft debug build-tuning reload` re-reads them. `order`, `radialRing` and `fastFoundation` are read when a
demo run or a job starts; the rest every tick.

## PerfStats steps
| step | thread |
|---|---|
| `build.radial` | server, one tick of a radial job |
| `build.radial_ground` | server, the walkable-surface stream of that tick (fix/build-radial-tune) |
| `build.radial_builders` | server, the builders of that tick (fix/build-radial-tune) |
| `build.radial_fill` | server, the hidden-fill stream of that tick (fix/build-radial-tune) |
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
- ~~Builders that run out of work in their ring take the next ring.~~ fix/build-radial-tune: they help on
  the tallest span of their ring first, and never go more than `builderRingsAhead` rings ahead.
- **The builder spot is simple:** the neighbouring cell on its side, feet one below the block or on top
  of what is built there. `BuildCrew` already leaps or walks straight when there is no ground.
- **The roof over the player can only close once the walls of the next ring stand.** That is the price of
  the no-floating rule.

---

# fix/build-radial-tune: the floor grows from the player's feet

## What Facundo saw and asked for (2026-11-09)
1. "El radio es muy amplio": the foundation stream ran over every ring at once, builders without work
   jumped ring after ring, terrain appeared far away very soon.
2. "Los builders tardan más en construir en vertical": 6 blocks/tick per column, a walk between columns,
   support waits beside each column, a scaffold walk every 4 blocks.
3. The base too must grow from where the player is, never all at once. **The player always has somewhere
   to walk**: the real ground of the plan grows from their feet outwards and the builders stay with it.
   **No artificial floors**: only plan blocks, in an order that keeps a walkable path.
4. The void island is ~10 deep: do not fill 10 hidden layers before there is a surface. **Surface first**,
   the hidden fill later, with low priority. Exception to "nothing floats", only for terrain: the surface
   (plus one support layer right under it) may go before its fill.

## How it works now
### Three parts per column
Planned off-thread (`Foundation.of` now always runs for radial jobs, it tells terrain apart):
- **fill**: the bottom terrain stack of the column (`Foundation` entries from the bottom, contiguous) except
  its top two entries: the island's hidden layers, podium fill;
- **ground**: the top two entries of that stack: the walkable surface and one support layer under it, so it
  never looks like a sheet. Ground is supported by definition (the terrain exception);
- **above**: the rest (walls, roofs, decoration, upper floors), bottom-up with the support rule, unchanged.
  Upper floors that `Foundation` classes as foundation are now built by the builders (visible).

### The front ring
`visRing` = the ring of the nearest column with ground or above work left that is not stalled: the ring
being finished. It is a pointer over the ring order (woken like the others), so it costs nothing per tick.
- **Ground stream** (no animation): ground and air heads (and above heads already right in the world, the
  resume probe) of rings ≤ `visRing + foundationRingsAhead` (1), at `groundBlocksPerTick` (32) out of the
  foundation budget. The floor runs a ring ahead of the builders, never further.
- **Builders**: rings ≤ `visRing + builderRingsAhead` (1). So ring 0 is finished before anybody touches
  ring 2. Stalled columns do not hold the front: a roof over the player that waits for the walls of ring 1
  lets the front move on to ring 1 (it closes when those walls stand).
- **Fill stream** (no animation): rings ≤ `visRing − fillRingsBehind` (2), or everywhere once no ground or
  above work is left, at `fillBlocksPerTick` (128) out of what the ground left of the foundation budget.
  Bottom-up per column (hidden; the order does not show).

### Spans and climbing
- Off-thread, 4-connected **tall columns** (≥ 4 solid entries over their ground) are grouped into spans of
  up to 8 columns: a piece of wall, a tower side. Every other column is a span of one.
- A builder claims a whole span (the free one nearest to it in the nearest ring with builder work), stands
  beside its middle column and raises it **in courses**: each placement takes the lowest head of the span,
  so the wall rises as one and the side support inside the span counts in the same pass (a lintel over a
  window goes as soon as its neighbours of that course are in).
- It **climbs in place** (virtual scaffold) when the course is more than 3 above its feet: a `Walker` event
  with the same x/z and 1 tick, and it places in the same turn. It walks only to a new span, or when a column
  is more than 4 blocks away on an axis.
- When its ring has no free span, a builder **helps** on the tallest span of that ring (≥ 6 entries left,
  at most 3 builders on it) instead of leaving for the next ring.
- Pace: `climbBlocksPerTick` (10) on tall spans, `blocksPerTick` (6) otherwise. `BuildJob.rate()` asks for
  that sum, so the global budget (`maxBlocksPerTick` 96, 8 ms) still caps it: 8 climbers want 80.

### Direction of travel
When the centre is re-ordered (moved > 4 blocks, or 3 s and ≥ 1 block), the new order centres half a ring
(2.5 blocks) ahead of the player along the way they moved. Walking towards the edge of what is built brings
that side's ground first. Cost: two multiplications on the game thread; the order is still off-thread.

### `build.fastFoundation`
Off by default. `true` brings back the fast base: every foundation entry goes in the streams, no ring
limit, ground and fill at the whole foundation budget (`foundationBlocksPerTick`).

## Pace (estimates, not measured in game)
Citadel with 4 projects: island ≈ 195 × 195 ≈ 38 000 columns, ~10 deep; hub (spawn) in the middle, so
rings are full disks up to ring ≈ 19 (radius ≈ 97), then partial to the corners (ring ≈ 27). Ring k has
≈ 78.5 × (2k+1) columns; ground = 2 entries per column, fill ≈ 8.

**Walkable** (ground only; 20 ticks = 1 s):
| | ground entries | at 32/tick | when it may start |
|---|---|---|---|
| ring 0 (r 5) | ≈ 160 | **5 ticks ≈ 0.25 s** | at once |
| ring 1 (r 10) | ≈ 470 | 15 ticks; rings 0+1 walkable at **≈ 1 s** | at once (1 ahead) |
| ring k | ≈ 157 × (2k+1) | ≈ 0.25 × (2k+1) s: ring 5 ≈ 2.7 s, ring 10 ≈ 5 s, ring 19 ≈ 10 s | once ring k−2 is finished (above work included) |
| whole surface | ≈ 76 000 | ≈ 2 400 ticks ≈ **2 min** if nothing waits for builders | |

Where a ring has buildings, its walkable time is the builders' time on ring k−2 (the floor is never the
bottleneck there); on open terrain (the island rim) the front moves at the ground pace above.

**Ring 0 closed** (hub around spawn, ≈ 80 columns, guess ≈ 400–600 visible entries): ground in 5 ticks,
then 8 builders at 6–10 blocks/tick within the 96 budget, ≈ 50–80/tick after a first walk of ≤ 20 ticks:
**≈ 1.5–2.5 s**. Before: ring 0 shared the builders with ring 1+ and they walked between columns, ≈ 5–10 s.

**Wall 10 high**:
- a span of 8 columns × 10 = 80 entries, one builder at 10/tick, climbing in place: **8 ticks ≈ 0.4 s**
  (+ the walk to it, ≤ 1 s); with 2 helpers ≈ 3 ticks;
- one column alone (a pillar): 10 entries, 1 tick at 10/tick;
- before: 6/tick per column, a scaffold walk (≈ 5–7 ticks) every 4 blocks, a walk to the next column and the
  side-support waits: ≈ 14 ticks per column, ≈ 110 ticks ≈ **5.5 s** for the same 8 × 10.

**Fill** (≈ 300 000–320 000 entries: island + podium) at 128/tick ≈ 2 500 ticks ≈ 2.1 min of work. It
runs while the front advances, 2 rings behind, so it ends ≈ 20–40 s after the last ring (the last two
outer rings' fill: ≈ 48 000 entries ≈ 19 s). **Whole build ≈ 2.5–3 min** when the ground pace leads, more if
the builders are slower than the floor (as before, set by the visible entries).

Knobs: `groundBlocksPerTick` 16 halves the floor pace (whole surface ≈ 4 min); 64 doubles it (keep ground +
fill ≤ `foundationBlocksPerTick`).

## Pending / not done
- **Not compiled or tried in game:** the coordinator compiles.
- The ground stream does not animate a builder (as the old foundation stream); builders stay within one
  ring of it, so they are seen next to the floor.
- Spans are static (built when the job plans), not per building: two buildings touching wall to wall may
  share a span. A span crossing a ring boundary only works its columns within the builder limit.
- `buildNearPlayer.radius` (48) still decides whether a player counts as in the build; the gating holds the
  build to the ring of the nearest pending column anyway, so a far centre no longer spreads the work.
