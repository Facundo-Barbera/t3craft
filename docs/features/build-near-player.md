# feat/build-near-player: faster base, build near the player

## Problem (Facundo, 2026-11-09)
In the new void world the builders take a long time to start, build far from the player, and the base
alone (void island, floor, podium) takes minutes.

## Where the time went (read from the code, not measured in game)
Case: the citadel (`hybrid-ringcampus`) with 4 projects, so only ring 1 is open.
- Footprint radius ≈ 40 + 39 + 6 + 8 = 93, so the box is ≈ 187 × 187.
- The island adds `MARGIN` 4: ≈ 195 × 195 ≈ 38 000 columns, almost all 10 deep, so **≈ 360 000 blocks**.
- The ground layer at y = 0 is ≈ π·93² ≈ 27 000 cells, plus ≈ 5–10 000 of podium and promenade fill.
- **Base ≈ 395 000 entries.**

Before:
1. **Start ≥ 10 s plus the planning time.**
   - `BuildCrew` submits the job only once the crew "arrived".
   - In the void, `groundY` comes from an empty heightmap (the bottom of the world), so the crew never
     arrives and always waits out `ARRIVE_TIMEOUT_TICKS` = 200 ticks.
   - After that, `CrewPlanner` simulates the island's 360 000 entries off-thread. Every pick scans the
     zone's pool, which holds thousands of entries: estimated at tens of seconds.
   - `DemoBuildRun` puts the island first and everything depends on it, so nothing else moves meanwhile.
2. **Placing.**
   - The crew gets at most 4 fronts × 12 = 48 blocks/tick, the global `maxBlocksPerTick` of 48.
   - Island: ≥ 360 000 / 48 = 7 500 ticks ≈ **6.3 min** at full budget. Walks, up to 20 ticks each,
     push it to ≈ 7–8 min.
   - Citadel ground ≈ 35 000 / 48 ≈ 730 ticks ≈ 40 s, plus the walks.
3. **Base before ≈ 7–9 min**, with the start included.

After:
1. **Start: tick 1.**
   - The job is submitted when the crew is created. The builders appear by the player, one per tick, and
     walk in while the job runs.
   - `Foundation.of` (≈ 395 000 hash lookups, O(n)) and the crew plan of the visible rest run
     off-thread. Estimate < 1 s (`build.foundation_split_async`).
   - The island's visible rest is only the rim hedge and froglights (≈ 800 entries), so its crew plan
     is trivial.
2. **Foundation.**
   - `min(4096 blocks, 10 ms)` per server tick, all jobs together, with the MSPT and FPS throttle.
   - At 2.5–5 µs per `setBlock` (`BuildBudget.nanosPerBlock` measures it in game), that is 2 000–4 000
     blocks/tick.
   - Island: 360 000 / (2 000–4 000) ≈ 90–180 ticks ≈ **5–9 s**. The ground layer and fill: ≈ 1–2 s.
3. **Base after ≈ 10–20 s, about 25–40× faster.**
4. **Visible parts** (facades, roofs, decoration):
   - the global budget goes from 48 to 96 blocks/tick (still capped at 8 ms/tick by the measured cost);
   - crews go from 4 to up to 8 builders (`crewFor`: one more per ~3 000 visible entries) with 8 fronts;
   - each builder works at ≥ 12 blocks/tick;
   - work stealing is capped at ≈ 24 blocks.
   - Expect up to 2× throughput on the visible part, depending on the walks.

**These numbers are estimates.** Measure in game with `/t3craft debug perf`:
- `build.foundation`: game thread, must stay ≤ `foundationMsPerTick`;
- `build.foundation_split_async`;
- `build.priority_async`;
- `build.manager_tick`.

## What changed
- **`Foundation`** (pure, off-thread). An entry is foundation if all of these hold:
  - it is a plain opaque full cube (no block entity, no light, not a falling block);
  - its cell is unique in the plan;
  - it is supported (the bottom layer, or the cell below is foundation; terrain also spreads sideways
    within a layer);
  - it is terrain (grass, dirt, stone, tuff, deepslate…), the bottom layer, hidden (six solid
    neighbours), or the top of a foundation stack.

  Wall courses are not foundation (they have a block above and are not terrain), so the builders keep
  them.
- **`FoundationCursor`** (free `CREW` jobs over `SYNC_PLAN_LIMIT`).
  - Phase 1 places the foundation bottom-up in plan order, so a layer is finished before the next. It
    uses the same sink, with the same guards (site), flags (`BUILD_FLAGS`: no neighbour updates) and
    effect cap. It has its own budget (`BuildBudget.foundationBlocks/Nanos`). Its `frontOf` is -1, so
    there is no animation and no client event per block.
  - Phase 2 runs a `CrewCursor` over the visible sub-plan, whose `CrewPlanner` treats the foundation
    as built ground (`external`).
  - While in phase 1 the job asks the crew budget for 1 block/tick.
- **Charged (survival) jobs keep the old crew path**, because materials are checked per segment.
- **`BuildCrew`**:
  - submits on the first tick;
  - spawns 3 blocks from the player when the player is within `buildNearPlayer.radius` of the site;
  - in a column with no ground, uses the player's or the site's height.
- **`CrewPlanner` focus**:
  - the first zone seed is the column nearest the player;
  - each builder starts at the column of its zone nearest the player;
  - the order stays nearest-first and supported, so nothing floats.
- **`NearPriority` + `PartSchedule.next(…, rank)`**:
  - the demo starts the ready part nearest the player;
  - the ranking is computed on `Offload` every ≥ 3 s, and only after the player moved > 16 blocks;
  - distances are compared in 8-block buckets, as hysteresis;
  - with no player near, the build order is used.
- **`CrewCursor.STEAL_MAX_TICKS`** = 40.

## Config (additive, `config/t3craft.json`)
```json
"build": { "maxBlocksPerTick": 96, "frontsPerJob": 8, "fastFoundation": true, "foundationBlocksPerTick": 4096,
           "foundationMsPerTick": 10, "blocksPerTick": 12, "crew": 8 },
"buildNearPlayer": { "enabled": true, "radius": 48 }
```
`/t3craft debug build-tuning reload` re-reads it.

## Pending
- Not compiled or tried in game: the coordinator compiles.
- Survival HQ (`HqConstruction` expansions) still builds wings in index order. Only the crew's start
  near the player applies there.
- Charged jobs get no fast foundation.
- No light or neighbour pass at the end of each layer. Plain opaque cubes need none: the light engine
  updates on `setBlock`, and neighbour shapes do not change for full cubes.
