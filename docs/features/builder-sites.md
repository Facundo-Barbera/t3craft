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

Other sites are taken as standing when support is looked up, so a wall that leans on the building next door is not
held back; if that building is not there yet, the wall stands alone for a while.

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

## Not done / open

- **Support across sites is optimistic**: another site counts as standing (see above). A site built on top of
  another site (a hall over a basement of a different group) can go up before what is under it. The citadel has no
  such pair (a group there is a zone on the map), other layouts may.
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
