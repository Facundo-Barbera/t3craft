# feat/builder-sites: builders work site by site (`build.order: "sites"`) (#25)

Three rounds patched the radial build (BLOCKED + retry, round radius, works + storey by storey) and none gave the
feeling Facundo wants: centred on the player but with a visible order, the building he walks into is the priority
and gets finished, crews show up where he is, and a building rises as a building, not as a disc of columns.

The proposal (`docs/plans/builder-scheduling.md`, with its 17 decisions marked as taken) says why: that feeling is
made of objects (a site, a crew, a front, a storey) and the radial cursor only has columns and one sort key. This
branch adds a second cursor next to it that has those objects.

**Not compiled or played.** It was written on a machine where nothing may be compiled. The coordinator compiles,
Facundo plays. See "Compile risks" at the end.

## Turning it on

`config/t3craft.json`:

```json
"build": { "order": "sites" }
```

- `"radial"` is still the default and is untouched (`RadialCursor`, `CrewCursor`, `FoundationCursor` and the avatar
  client were not edited). Switching back is that one key.
- `"sites"` uses everything `"radial"` uses around it: the demo merges the parts into one plan, one crew of up to
  12 builders, the player as the centre, the start-up ground primer. Only the cursor differs.
- Scope: free jobs (the demo HQ and other free crew jobs over 256 entries). Charged jobs (survival) and the real HQ
  are not affected.
- The build order is read when a job starts. Change the key, then `/t3craft debug build-tuning reload` (it prints
  `order sites`) or leave and enter the world again, then start the build again.

## What is in this branch

| phase of the plan | state |
|---|---|
| 1. Dispatch, one open site, front anchored at the access, storeys over the whole site | **done** |
| 2. Compiled program: real floors, stages, support resolved at compile time, finishing pass; the site's ground as its own wave | **done** |
| 3. Several open sites, a rear guard, crews by proximity, 70 / 30 pace | **done** |
| 4. Methods per kind of site (bridge, island, gardening), trades with skins, site signs | not planned here |

## How it works

Three classes in `dev.t3craft.build`:

| class | what | where it runs |
|---|---|---|
| `SiteProgram` | the sites of the plan, their footprints, bays and storeys; the order inside one site (`compile`) | off-thread, pure |
| `SiteDispatch` | which site is in focus, how many builders it gets | pure |
| `SiteCursor` | the queues of the open site, the streams, the builders, the counters | server thread |

### What a site is

A site (obra) comes from the works of the plan. The demo now gives every part its group name (`BuildPlan.named`,
`BuildPlan.workName`), so the rules can use it:

- a work with nothing for a builder (the plinth, the island) is no site;
- `module_<n>` works join the `lot_<m>` whose box holds their middle: a lot and its halls are one site;
- a `ring_<m>` work is cut into stretches between the nodes (`node_<k>`, `archive`), by the angle around the hub;
- scattered works are no site. By name: `grounds`, gardens, lamps, the plinth, the island. By shape: builder columns
  covering under 4 % of a box of 40 × 40 or more. Their builder entries form the **details** pass, worked last;
- every other work is a site as it is. A plan without works is one site.

### Being inside

The player is in a site when a column of its **footprint** is within 2 blocks. The footprint is the columns whose
lowest builder entry belongs to the site, plus the columns with only ground whose surface is the site's (a
courtyard). It is not the box: standing in the middle of a ring is not standing in the ring.

### Which site

`SiteDispatch.focus`:

1. the site the player is in, if it has work left;
2. else the site already in focus, while it has work and the player is within 32 blocks of it;
3. else the nearest site that was started and not finished, within 32 blocks;
4. else the nearest site not started.

Distances are compared in buckets of 8 blocks, and a new focus must be chosen for one second before it replaces
one that still has work.

### Opening a site

- The **access** is the column of the site nearest the player at that moment. It is frozen until the site closes.
- The program of the site is compiled off-thread for that access. Bays (tiles of 6 × 6 blocks) are ordered by
  distance from the access.
- The crew is assigned at once (2 at least, one more per `build.entriesPerBuilder` entries left, 6 at most, never
  more than the bays it can hand out) and walks to the access together.

### Inside the site

**Storeys.** A storey starts at a real floor of the building: a height where at least a sixth of the site's builder
columns (and 12 cells) hold a walkable floor cell (two passable cells over it, inside a 2 × 2 of walkable cells).
Floors closer than 3 blocks to the one below do not count. Where floors are further apart than a lift and a bit
(towers, tall halls, roofs), or there are none, the height is cut into lifts of 5 blocks (`build.siteStorey`).
The status line says which kind the current storey is: `(floor)` or `(lift)`.

**Stages.** Inside a storey the work goes in the order of a building site:

| stage | what |
|---|---|
| `slab` | the floor layer of a real storey (everything at its base height that is not a finish) |
| `structure` | opaque solids: walls, columns, roofs, stairs |
| `enclosure` | not opaque but with a body: glass, panes, fences, doors, leaves |
| `finishes` | what a body walks through, or what gives light: torches, lanterns, flowers, signs |
| `late` | what found no support in its own stage (below) |

**Waves.** The program of a site is a list of waves; a wave is one task per bay. Builders take tasks of the current
wave only, the first free one in wave order from the access. The next wave starts when every bay of this one is
done. So the whole site lays a slab, raises the structure of that storey, closes it, finishes it, and only then
starts the next storey.

**Support is resolved when the program is compiled, not while building.** An entry goes into a wave only if what
holds it (the cell below; else a side cell; for decoration also the cell above; falling blocks only the cell below)
is ground, or was placed in an earlier wave, or earlier in the same task. A stage is therefore several waves
("rounds"): a flat roof is the ring next to the walls first, then the next bays inwards. Consequences:

- the tasks of one wave never depend on each other, so builders never wait for a neighbour and nothing they place
  floats;
- there are no stalled columns and no wake-ups at run time;
- what finds no support in its stage (a lintel over a window, a roof spanning several bays) goes to the storey's
  `late` rounds; what still finds none waits for the next storey's `late` rounds (a lantern hanging from the floor
  above);
- what never finds any (a truly floating piece, or one that leans on a part of the details pass) is the site's
  **finishing pass**, its last wave (`finishing` in the status), placed bay by bay whatever holds it.

Other sites hold only where they are already placed when the program is compiled (#48; before, they were taken as
standing and a walkway leaning on an unbuilt tower went up floating). What leans on a building not built yet waits for
the late rounds and, at worst, the finishing pass.

**What does not change it.** Walking inside the site: nothing is re-ordered when the player moves. A task waits
until the ground of its bay is laid.

### Walking to another building

- The new site opens at once, with the builders that are free.
- The site left behind first **closes the storey it is on** with the crew it has (`closing` in the status). It gets
  no new builders meanwhile.
- When that storey is closed, its main crew leaves and a **rear guard** of 2 stays (`rear`) and keeps raising it,
  storey by storey, while the player is within 32 blocks.
- Further than 32 blocks, the rear guard closes its storey and the site is **frozen** (`leaving`, then gone from the
  status). It stays "started": it comes back first when the player is near it again (rules 1 and 3), compiled again
  from the new access.
- A started site near the player that nobody works gets a rear guard by itself, while a slot and two builders are
  free. Sites not started are never opened this way: only the focus starts a building.
- A site the crew never placed a block in is simply closed when the focus moves on.

**How many at once.** `build.sitesOpen` (2: the focus and the one being closed), plus one when the crew has builders
to spare: with 12 builders and a focus crew of 6 that is 3 (the focus and two others). The focus always opens at
once, so for a moment there can be one more; the farthest site over the limit closes at its next storey.
`build.sitesOpen: 1` is one site at a time: the new focus waits until the old one closed its storey.

**Pace.** While the focus and other sites are worked at once, the focus gets 70 % of the builders' blocks per tick
and the others 30 %; what one side cannot use goes to the other.

### The floor

All by streams, no builders:

| stream | what |
|---|---|
| corridor | the floor under the player and ahead of it while it walks. Same settings as the radial build. |
| carpet | the floor within `build.radiusMin + build.radiusStep` (9) blocks of the player, nearest first |
| trail | a strip 3 wide from the carpet's edge to the access of the nearest open site, up to 48 blocks, while the player is outside that site |
| site wave | ground and clearing of the open site's bays, in wave order from the access; it includes the **apron**: the free ground within 3 blocks of the site's footprint, so a finished building does not stand over the void |
| fill | the hidden terrain under a column whose ground is laid |
| last sweep | once every site is done, whatever is left of the plan: in rings around where the player stood when it started, then once more column by column |

"The floor" is the terrain, or the plan's floor at the player's level when that is not the terrain (the hub's floor
over a hidden room): the corridor and the carpet lay that floor too, and the site's tasks pass over it.

### Chunks not loaded

A placement that answers BLOCKED parks its column (streams) or its task (builders) for 20 ticks. Nothing is
dropped, so the job completes only when every entry was applied.

## Trying it

1. Set `build.order` to `"sites"` and run `/t3craft debug build-tuning reload`: the line must end in `order sites`.
2. A world where the HQ is not built yet shows the most: a fresh demo world (`/t3craft debug reset-hq`, which asks
   for confirmation and keeps a zip backup). `/t3craft demo rebuild` on a built world also runs, but blocks already
   right are skipped without builders, so there is little to see.
3. `/t3craft demo status` while it builds.

What to do in the game, and what should happen:

| do | expect |
|---|---|
| stand still in the hub | the crew arrives together at the side you came from; the floor spreads from there; the slab closes, the walls of that storey go up all around, then glass, then lights, and only then the next storey |
| walk 10–15 blocks inside the hub | nobody drops what they were doing; the status line does not change site, and `task-switches walk` stays 0 |
| walk into a module | the module opens at once with its crew; the hub shows `closing` until its storey is done, then `rear` with 2 builders |
| stay in the module | the module gets most of the blocks; the hub keeps rising slowly, a storey at a time |
| walk 40 blocks away from the hub | the hub shows `leaving`, closes its storey and disappears from the status; its skyline is level |
| walk back to the hub | it is the focus again, opened from the side you came back by |
| walk around between buildings | the carpet lays the floor under you and a trail runs to the open site; no `standing on air` in the log |
| look at a finished building | it has 3 blocks of ground around it; its last wave was `finishing` |

## The status line

`/t3craft demo status` prints, after the usual prefix (`…, sites, N part(s), building 37%`):

```
sites 3/41 | focus lot_2 storey 1/3 (floor) structure wave 3/22 bays-left 7 left 2110/2600 crew 5
 | rear hub storey 3/4 (floor) slab wave 15/31 bays-left 2 left 3020/8400 crew 2 | player in lot_2
 | builders 7/12 | off-site 0 storey-spread 1 task-switches walk 0 chunk 0 in-site 78% placed 5870 centre 31,12 level 1
```

| field | meaning |
|---|---|
| `sites a/b` | sites finished / sites of the plan (the details pass counts as one) |
| `focus <name> …` | the site the crew is sent to: storey it is on and whether that storey starts at a real floor or is a fixed lift, stage, wave, bays of that wave not done, builder entries left / total, crew. `planning`: its program is being compiled |
| `closing <name> …` | a site the focus left: it is finishing its storey with the crew it had |
| `rear <name> …` | a site worked by a rear guard of 2. `leaving`: it closes at the end of this storey (too far, or one site too many) |
| `player in <name>` / `player outside` | the site whose footprint the player stands in |
| `focus <name> waiting` | the focus could not open yet: too many sites are still closing their storey |
| `builders a/b` | builders holding a task / builders of the crew |
| `off-site` | **criterion: 0.** Builder placements in a site that was not open |
| `storey-spread` | **criterion: at most 1.** Largest difference seen between the storey a builder placed in and the lowest storey of that site with work left. The finishing pass is left out. 1 is normal (a piece waiting for the floor above). More than 1 means a piece waited two storeys for its support |
| `task-switches walk` | **criterion: 0.** Builders taken off a bay that still had entries because the focus changed |
| `task-switches chunk` | builders that left a bay because its chunk was not loaded (not a fault) |
| `in-site` | **criterion: the great majority.** Share of builder placements, while the player was inside a site, that went to that site. 100 % with one site open; about 70 % or more while a rear guard works elsewhere |
| `placed` | builder placements so far |
| `details` | builder placements of the details pass |
| `lifted-floor` | floors laid by the corridor or the carpet at the player's level |
| `waiting-chunks` | something was parked in the last second |

The criteria of the plan that are not a counter: "the site is left with its storey closed" (look at it), and "zero
`standing on air`" (the log).

## Config

New keys in `build`, all additive:

| key | default | meaning |
|---|---|---|
| `order` | `"radial"` | `"sites"` turns this on |
| `siteCrewMin` | 2 | builders an open site gets at least |
| `siteCrewMax` | 6 | ...and at most |
| `siteStorey` | 5 | height of a storey, blocks, where the site has no real floors |
| `sitesOpen` | 2 | sites open at once (one more when builders are to spare); 1 = one at a time |

Reused as they are: `entriesPerBuilder` (one more builder per this many entries the site has left), `radiusMin` +
`radiusStep` (the carpet's radius), the corridor keys, `groundBlocksPerTick`, `fillBlocksPerTick`,
`climbBlocksPerTick`, `radialCrewMax` (12 builders on a big plan). `build.fastFoundation`, the ring keys and
`radialMinBuilders` do nothing with `"sites"`.

Constants in the code: bay 6 blocks, keep radius 32, bucket 8, footprint margin 2, hold 1 s, apron 3, trail 48 × 3,
real floor = a sixth of the columns and 12 cells, smallest storey 3, 6 rounds per stage, rear guard 2, focus share
70 %.

## Stalls (#48)

Facundo played `"sites"` and saw the builders stand still for a while, then carry on, with good FPS. Nothing could be
measured where this was written, so the causes come from reading the code; the counters below are there to tell, in
the next session, which one it was.

### What was found, most likely first

| # | family | where | why builders stand | how long |
|---|---|---|---|---|
| 1 | B, planning | `SiteCursor.pick`, `taskOver` (wave barrier) | a wave is one task per bay and the next wave starts only when the **last** bay of this one is done. Builders that finish early can only help a held task (one helper, 8 entries left at least), so most of the crew stands through the tail of every wave. A storey has up to five stages, each several rounds: dozens of tails per storey | the slowest bay of the wave: a fraction of a second normally, several seconds with a tall bay, a slow pace (throttle) or a bay waiting for its ground or chunk |
| 2 | B, planning | `SiteCursor.tickSites` + `BuildBudget` (ground) | a task waits until its bay's ground and clearing are laid. The site's ground wave runs after the corridor and the carpet out of the same foundation budget; when the MSPT / FPS throttle cuts that budget (it falls ×0.7 a second and climbs back +0.1 a second) the corridor of a walking player can spend all of it, and the site's ground stops | while the throttle is low: seconds |
| 3 | B, planning | `SiteCursor.staff`, `release`, `dispatch` | at the end of a short wave the crew is sized down and builders leave the site; they come back only at the next dispatch (every 10 ticks), and builders let go by a closing site in a dispatch were staffed after the focus had already been staffed in that same dispatch | up to half a second per wave, repeatedly |
| 4 | B, planning | `SiteCursor.dispatch` (`hardCap`), `staff` | the focus cannot open while too many sites are still closing their storey (`focus <name> waiting`), and those sites get no new builders, so the freed builders stand until a storey of another site is closed at 30 % of the pace | a storey of a big site: 10 s and more; rare, when walking through several buildings in a row |
| 5 | B, planning | `SiteCursor.openSite` / `install` | between opening a site and its program arriving (off-thread, `Offload`, shared with other work) its crew stands (`planning`); then it walks to the access for 20 ticks | the compile (milliseconds) plus the queue, plus one second |
| 6 | A, tick | `SiteCursor.probe` | the resume probe read up to 1024 entries **per open site** per tick with no time check, and its skips used the ground wave's skip budget, so while it ran the ground wave of that tick stopped | a few ms per tick for a few ticks after a site opens; the stopped ground feeds cause 2 |
| 7 | A, tick | carpet, trail, site ground wave, last sweep rings | bounded by blocks and skips but not by wall-clock time, unlike the radial streams | small (bounded), but they could overrun the foundation time |

Not causes: the program of a site (`SiteProgram.compile`) and the whole analysis (`SiteProgram.of`) already run
off-thread; the crew's walk to the access is a fixed 20 ticks, it never waits for the last builder; no task is held by
a builder that does not work it (a holder either walks, at most 20 ticks, or places). Server lag coming from elsewhere
(chunk loading while flying, mobs) slows the builders through the throttle (`throttle` in the status), it does not
come from this code.

### What was fixed

- **Ahead of the wave** (cause 1). A builder of a site that would stand takes a task of the **next** wave of the same
  storey whose bay is settled: the current wave is done in that bay and in every bay of the site touching it. Support
  is only ever the cell below, above or beside, and the program resolved it on "every earlier wave placed", so such a
  task has everything it leans on. Never across a storey (the storey gate stays) and never into the finishing pass.
  The crew size counts that next wave too.
- **Lent builders** (causes 1 and 4). A builder that stood 40 ticks (2 s) on a site with nothing it can take goes to
  another open site with a free task (the focus first), does one task there and is free again.
- **Staffing every tick** (cause 3). Builders let go between dispatches are staffed on the next tick; a dispatch staffs
  twice, so what a closing site lets go reaches the focus at once.
- **The focus waiting** (cause 4). While the focus cannot open, the sites it waits for (`leaving`) take the free
  builders, as the focus would.
- **Ground for waiting builders** (cause 2). While builders of the focus wait for their bay's ground, its ground wave
  may use twice `build.groundBlocksPerTick` (inside the foundation budget); when the foundation budget is spent, a
  reserve of 8 blocks / 0.5 ms a tick for the focus's ground only.
- **Probe and streams inside the tick budget** (causes 6, 7). The probe has one cap of 1024 for all open sites, its
  own counts and the foundation time; carpet, trail, ground wave and the rings of the last sweep stop when the
  foundation time of the tick is spent.

Left: the wave barrier itself across storeys (by design); builders with no site because of the crew size
(`siteCrewMax` 6 + rear guards of 2: with 12 builders some always have no site, they are `free` in the status, by
design); the throttle's slow recovery (shared with `"radial"`).

### Reading the counters

After the usual fields, the line of `/t3craft demo status` gets:

```
| idle 2 free 3 idle-share 14% idle-ticks 812 by wave 61% ground 22% planning 9% chunk 8% longest 3.4s ground
  ahead 230 lent 4 ground-reserve 0 focus-wait 1.2s worst-tick 3.1ms compile 18ms (work 6, worst 40) throttle 100%/100%
```

| field | meaning | what to look for |
|---|---|---|
| `idle` | builders of a site that hold no task and are not walking, now | |
| `free` | builders with no site now (crew size, by design) | |
| `idle-share` | idle builder-ticks / builder-ticks of builders with a site, since the job started | under ~15 % is fine |
| `idle-ticks` | ticks with at least one idle builder | |
| `by <reason> %` | why they were idle, share of idle builder-ticks: `wave` (the wave's other tasks are held: barrier), `ground` (a free task waits for its bay's ground), `chunk` (parked, chunk not loaded), `planning` (program not compiled yet), `boundary` (a non-focus site at a storey boundary, waiting for the dispatch), `no-work` (the site has nothing left) | the largest is the cause to work on |
| `longest` | the longest a builder stood in a row, and why | several seconds with `ground` → the throttle or the foundation budget; with `wave` → a long tail |
| `ahead` / `lent` / `ground-reserve` | tasks taken ahead of the wave, builders lent to another site, ticks the ground reserve ran | |
| `focus-wait` | time the focus was chosen and could not hand out work (not open, or compiling) | |
| `worst-tick` | worst `SiteCursor.tick` of the last one or two minutes, ms | over ~10 ms: the cursor itself is heavy (family A); low while the game lags: the lag is elsewhere |
| `compile` | last site program: from opening to arrival (queue included), the compile itself, the worst arrival | hundreds of ms → the `Offload` queue |
| `throttle a%/b%` | build budget factor from MSPT / from client FPS | under 100 % → the server (or the client) is slow and the budget was cut |

A real server freeze shows as a high `worst-tick` only if it is in this cursor; otherwise `throttle` drops after it.

## Noise (#48)

Second report from the same session, flying over the citadel: "los builders están construyendo de forma muy rara,
con mucho ruido". Speckled roofs, floors and paths (holes all over a surface at once), many places half done at once
and none closed, beams, walkway decks and tower caps up in the air before what holds them.

### What was found

| # | what is seen | where | why |
|---|---|---|---|
| 1 | floor patches, walkway decks, tower caps floating; strips of ground under every flight | `SiteCursor.layLift`, `tickCorridor`, `tickCarpet` | the "floor at the player's level" follows the feet in the air too (after a 2-block climb), so a **flying** player had the walkable floor nearest its height (within 3 blocks) laid in a disc of 9 blocks and a corridor 20 ahead, with only one support block under it. The corridor, carpet and trail also laid the ground under the whole flight path |
| 2 | speckled plazas, paths and paved floors | `SiteProgram` constructor (kinds) + `Foundation` | only plain opaque full cubes are foundation. In a mixed pavement (full blocks with slabs, stairs, path blocks) the full cubes are ground, laid at once by the streams, and the rest are builder entries of their work (often a scattered one, so the details pass at the very end): half a floor for most of the build |
| 3 | striped stair roofs | `SiteProgram.Compiler.supported` | support was below, beside, or above for decoration. The rows of a stair roof only touch diagonally, so every row that does not reach a wall found no support, went to the `late` rounds and then to the finishing pass, the last wave of the site |
| 4 | a surface growing in a scattered order inside a bay | `SiteProgram` key (storey, stage, bay, height, **plan index**) and `Compiler.byBay` | inside one layer of a bay the order was the plan's. The plan is spatial per part (`Layers`), but a cell a later part writes again moves to the end of the merged plan (`BuildPlan.Builder.set`), so those cells came last. And a chain that grows against that order stopped after 4 passes (`MAX_PASSES`), the rest waiting for the next round |
| 5 | many sites started, none closed | `SiteDispatch.focus`, `SiteCursor.dispatch` (`HOLD`) | a new focus needed one second of hold; walking or flying through buildings opened each one for a moment, its crew placed a few blocks and it stayed "started" for good. Nothing capped the started sites |
| 6 | pieces against another building placed before it | `SiteProgram.Compiler.solid` | other sites counted as standing (documented under "Not done"): a walkway or wall leaning on a node or tower not built yet went up with its first stage |

Not found: no order comes from iterating a hash map or set (the plan builders keep insertion order, the canvas is
sorted, every program key is spatial but for the plan index tie-break of point 4); two builders on one task place
consecutive entries of one list, side by side, not interleaved across the bay; BLOCKED + retry only parks chunks not
loaded (20 ticks), it leaves holes only at the edge of the loaded area.

### What was fixed

- **Flying lays nothing** (1). After 20 ticks without touching the ground the player is `flying`: no corridor, carpet,
  trail or lifted floor, and the site under it does not count as "the one the player is in".
- **Pavements are ground** (2). A paving block that is not a full cube, lying right on a column's terrain stack,
  walkable, next to a column whose ground top is at its height, is ground: the whole pavement comes with the streams.
- **Diagonal support** (3). A block that is not decoration is held by a block one down and one aside: a stair or slab
  roof rises row by row from its eaves.
- **Serpentine from the access** (4). Inside a bay: bottom-up, and each layer in rows across the direction from the
  access to the bay, nearest row first, alternating direction; the plan index no longer decides. Up to 64 passes per
  bay and round, so a slab or roof that grows against the sweep is finished in its round.
- **Hysteresis and a cap** (5). A site not started yet must be the dispatch's choice for 3 s (6 rounds) before it takes
  the focus. With 3 sites started and not finished (`SiteDispatch.STARTED_MAX`), rules 3 and 4 close the nearest
  started one (at any distance) before they start a new one; the building the player walks into still opens.
- **Other sites hold only where placed** (6). Compiled with a snapshot of what is placed: an entry leaning on an
  unbuilt building goes to the late rounds and, at worst, to the finishing pass at the end of its site.

Left as designed: a wave is still the whole site (one task per bay), so a surface wider than a bay fills bay by bay
from the access; what never finds support is placed in the finishing pass, whatever holds it.

### Reading the counters

The status line gets, before the stall counters:

```
| open 2 started 3 opens 7 (1.4/min) late 220 remate 31 flying
```

and each open site its own ` late s1:12 s3:40 remate 5`.

| field | meaning | what to look for |
|---|---|---|
| `open` | sites open now | 2 or 3 normally |
| `started` | sites started and not finished | stays at 3 or under unless the player walks into new buildings |
| `opens (n/min)` | sites opened since the job started, and per minute | several per minute while flying = the hysteresis is not enough |
| `late` / `remate` | builder entries the compiled programs left to late rounds / to the finishing pass, all programs so far | a site with a large `remate` builds its pieces in the air at the end |
| `late sN:k` (per site) | its program's late entries per storey | a storey with many: its pieces lean on something placed later |
| `flying` | the player is airborne: nothing is laid around it | |

## Not done / open

- **Support across sites**: since #48 another site holds only where it is already placed when the program is compiled
  (see "Noise"). What leans on an unbuilt building goes to the late rounds and the finishing pass; a site built on top
  of another site can still go up first in that finishing pass.
- **Stages are told apart by block properties only** (opaque, collision, light): a lantern is a finish, a slab or a
  stair is structure, a carpet is enclosure. No list of block types.
- **Many small waves.** A storey is up to five stages and each can be several rounds. The crew is sized again at
  every wave, so builders stand idle for a moment on short waves (the last bays of a roof, the lights).
- **A rear guard is slow on purpose** (2 builders, 30 % of the pace shared among the sites that are not the focus).
  If buildings left behind take too long, raise `sitesOpen` or stay closer.
- **Islands and bridges** (decisions 12 and 13) are phase 4: an island is not a ground site yet, and a bridge is
  built like any other site.
- **No scaffolds or banners** (decision 15): the only sign of a site opening is the crew arriving together.
- **The ground between sites**, beyond the aprons, the carpet, the trail and the corridor, waits for the last sweep.
- **Small works are sites too.** The rules know the citadel's groups (`lot_` + `module_`, `ring_`, `node_`,
  `grounds`). A layout with many small groups (the classic one has a group per desk) gets a site, a crew arriving
  and a storey gate for each of them. Joining small works to the building around them is not done.
- **Commit `b591cdda` (phase 2) does not compile on its own**: an edit left a second copy of part of
  `SiteCursor.java`. The next commit (`51075508`) removes it. Build from the tip of the branch.
- **Numbers are first guesses**: storey 5, bay 6, crew 2–6, hold 1 s, the real-floor threshold, apron 3.
- The perf timers reuse the radial keys (`step.build.radial*.ns`).

## Compile risks

Nothing here was compiled. Where a mistake is most likely:

- `BuildTuning` gained 5 record components (`sites`, `siteCrewMin`, `siteCrewMax`, `siteStorey`, `sitesOpen`). A constructor
  with the previous 30 arguments was added so older callers keep compiling; a caller of the canonical constructor
  outside this repo's `main` and `client` source sets would break.
- `BuildPlan` has a new private constructor and `named` / `name` / `workName` / `Builder.nameWork`.
- `SiteCursor.java` and `SiteProgram.java` are long and use fastutil (`IntArrayList`, `LongArrayList`,
  `BooleanArrayList`, `Long2IntOpenHashMap`) and jspecify `@Nullable` on arrays and nested types, as
  `RadialCursor` does.
- Syntax was checked with a tree-sitter Java grammar; types were checked by reading only.

### Compile review (#45)

A second blind pass read `SiteCursor`, `SiteProgram`, `SiteDispatch`, `BuildTuning`, `BuildPlan` and the lines `#25`
added to `BuildJob`, `BuildCommands`, `DemoBuildRun` and `DemoHq` against the real code, and found nothing to change:

- every constructor call of `BuildTuning` (35 arguments in `DEFAULTS` and `parse`, the compatibility constructors
  delegating in order) and of `BuildPlan` matches its declaration; no other source set (`client`, `hqrender`,
  `uirender`) calls them;
- `SiteCursor` implements all of `JobCursor`; every `p.*`, `g.*` (the `Program` record), `SiteDispatch.*`, `PerfStats`,
  `BuildBudget`, `WalkLevels`, `CrewPlanner`, `CrewCursor.Walker` and `Offload` member it uses exists with that
  arity and type; every `final` field of `SiteProgram` is assigned once before it is read;
- `@Nullable` sits where `RadialCursor` puts it (`int @Nullable []`, `SiteProgram.@Nullable Program`,
  `CrewCursor.@Nullable Walker`), and no local is declared twice in nested scopes (checked with a throw-away
  tree-sitter script);
- the nested conditional in `BuildJob` that picks `SiteCursor.plan` or `RadialCursor.plan` is a valid poly conditional,
  and with `build.order: "radial"` (or `"parts"`) the job takes exactly the branches it took before.

What can still fail only javac or the game will show: Minecraft API calls (`BlockState.getLightEmission`,
`canOcclude`, `isAir`) are the same ones other classes in the repo already use.

## Config and restarts

**RESTART** (`BuildTuning` and `BuildPlan` changed shape; three new classes).
