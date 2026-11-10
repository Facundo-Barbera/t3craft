# fix/25-builder-unfinished-columns: the radial build works building by building (#25)

Facundo played `main` with the round radius merged: it felt a little better but still stuck, with whole columns of
blocks never placed around the player. The direction he gave afterwards: **do not patch the holes**. The problem is
the algorithm: the build feels noisy, with no visible order. The round radius made a disc of loose columns the unit of
work, so walls of five buildings rose at the same time at the same ring, and a half-built building was left behind as soon
as the disc moved. Empty columns are a symptom of that disorder, not the goal.

**Not compiled or played** (no processes allowed on this machine): the coordinator compiles, Facundo plays.

## The idea

The unit of work is a **work (obra)**: a building, tower or wing. The HQ already is a list of them: `DemoHq.full` gives
the plinth/island and then one plan per group of the HQ plan (`hub`, `portico`, `archive`, `spine_<row>`, `module_<n>`, …,
`HqPlans.byGroup`). `DemoBuildRun.merge` used to melt them into one plan and lose that; now each entry keeps the index of
its part as its work (`BuildPlan.Builder.set(…, work)`, `BuildPlan.workOf`, kept by `layered()`).

The radial build stays centred on the player, but:

1. **The work the player is in comes first**, and is finished before the build disperses.
2. **A work that was started is not abandoned** while a fresh one nearer the player begins.
3. **Then the next nearest.** Groups of builders show up where the player is, one building at a time.
4. **One coherent front** inside the work: from the player's side outwards, storey by storey.

## How it works (`RadialCursor`, `WorkRank`)

### Which work first (`WorkRank`, plain ints, off-thread)
Each work has a box (its columns) and counts of builder entries (`workTotal`, `workLeft`: not ground, fill or air). A
column belongs to the work of its **lowest builder entry**; columns with only ground, fill or air belong to no work.
Works are ranked in tiers:

| tier | what | inside the tier |
|---|---|---|
| 0 | the player is in it (inside its box, 1 block of margin, from 3 under to 4 over its height) | smaller box first (a room before the hall around it) |
| 1 | started (`left < total`) and unfinished, within 32 blocks of the player | nearest first (buckets of 8 blocks) |
| 2 | not started | nearest first (buckets of 8 blocks), build order breaks ties |
| 3 | nothing for a builder (finished, or only ground) | last |

Buckets and the tier-1 rule are the hysteresis: the choice does not flip while the player walks about, and a started
building stays ahead of unstarted ones.

### The order is by work, then by distance
The column order the whole cursor already uses (`order`, `distAt`, `ringAt`) is now sorted by **rank, then distance
to the player inside the rank**:

- rank 0: ground that belongs to no work within `radiusMin + radiusStep` (9) blocks of the player: the floor under
  and around the player always goes first;
- rank 1…W: the works, in the `WorkRank` order;
- rank `FAR`: the rest of the ground.

`distAt` (quarter blocks) is `rank × 2^18 + distance`, so it is still non-decreasing along the order, and every piece that
works with `distAt` (the radius, the ground stream limit, the fill stream, the builders' limit) keeps working unchanged,
but "inside the radius" now means *in this work, within R blocks of the player*:

- the radius starts at 5 blocks, **stays while the work still has something that can be worked inside it**, and when that
  disc is complete grows by 4 blocks (the existing round-radius rule). The front therefore opens at the player's side of
  the building and spreads outwards;
- it reaches the **next work only when this one has nothing left** that can be worked now (done, stalled for support or
  waiting for a chunk): the next one by the ranking at the last re-order;
- the floor (ground stream, 1 ring ahead of the radius) and the hidden fill (2 rings behind) follow the same front, so
  the floor laid is that of the building being raised, not of the whole island;
- the corridor (floor under and ahead of a walking player) is unchanged and independent.

### Re-order
When the player moves ~4 blocks (as before) the order is recomputed off-thread with the player's position, height and a
snapshot of `workLeft`. It is installed with the radius back at its minimum: if the player walked into another
building, that building is rank 1 now; the one left half-built is tier 1 and comes right after. The very first order is
made as soon as a player is known (before, it waited until the player was 4 blocks from the plan's fallback centre).

### Storey by storey (`updateGate`)
Inside the work, builders do not run one tower up alone. Every 4 ticks the lowest head a builder may place now (a BUILD
column inside the builders' reach) is found, and no builder places a head higher than **that + 6 blocks**
(`STOREY`). The lowest head always passes, so this cannot stop the work; it makes the building rise as a whole, floor by
floor. It looks only at the builders' reach, so a low column far away cannot hold the near ones back.

### Size of the group
`active` builders = `build.radialMinBuilders` (3) + one per `build.entriesPerBuilder` (1200) entries **the current work
still has** (before: all the work within 24 blocks of the player). A small hut keeps 3, the centre of the HQ gets the whole
crew. Same grow-at-once, shrink-one-per-look rule.

### Without grouping
A plan with no work info (any caller other than the demo's merged HQ) has `works() == 0`: every rank is 0, the order is
the plain distance order, there is no storey limit and the crew is sized as before. Same behaviour as the round radius.

## What this does and does not claim about the empty columns
I did not find, and did not fix, a specific bug that leaves a column empty: the BLOCKED/park path reads correct
(a parked column is never marked applied, so the job cannot complete, and the demo marker is written only on
`COMPLETED`). What the old order did was spread the work: a column of a building waited for a neighbour or a storey
below while the disc moved on, and "forced" stalled heads only happens when everything else is done. Working a
building at a time, storey by storey, should leave that column finished with the rest of its building. If columns are
still empty after the build says it is done, the status line (below) and the log are the next thing to look at, not a
fill step.

## Status line
`/t3craft demo status`: `radial cols a/b centre x,z work k/W left L/T radius R builders n/m [waiting-chunks p]`:
the work being built (`k` of `W`), its builder entries left of its total, and the radius **inside that work**.
`works W (ground)` means the front is on ground that belongs to no work.

## Config
No new config keys: `STOREY` (6), the 32-block keep radius, the 8-block bucket and the 9-block ground-first radius are
constants (`RadialCursor`, `WorkRank`). `build.radiusMin`, `radiusStep`, `radialMinBuilders` and `entriesPerBuilder`
keep their meaning (inside a work).

## Not done / open
- **One work at a time.** The builders are not spilled onto the next work when the current one cannot employ them all
  (a builder with no free span is idle). That is on purpose for a calm look; a spill (let the radius jump to the next
  work when builders are idle for a while) is a small follow-up if it feels slow.
- **Not played.** Numbers are first guesses. Storey 6 may be too high for the HQ's floors (try 4); the keep radius 32 may
  be too wide.
- **Seen in the log, not touched here:** the ~94 ms server tick when entering the showroom (`DropShowroom` /
  `PlanApplier`), the "standing on air" force-sends (consistent with floors not yet laid where the player walks: the
  corridor lays the floor ahead; a work's own floor is laid when its turn comes), and the missing
  `mod\build\resources\client` classpath entry at start-up (IDE run configuration).
- Entries of the plinth/island are terrain (ground and fill), not a work: they are laid around the player, as before.

## Config and restarts
**RESTART** (`RadialCursor` and `BuildPlan` changed shape). To try it on a world marked built with gaps:
`/t3craft demo rebuild`; stand near a building and watch the status line: the work number changes only when the
current one is done or when you walk into another.
