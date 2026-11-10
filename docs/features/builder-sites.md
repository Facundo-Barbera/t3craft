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
| 3. Several open sites, a rear guard, crews by proximity, 70 / 30 pace | not yet |
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

The open site finishes the storey it is on, closes, and the new one opens. A closed site that is not finished stays
"started": it comes back first when the player is near it again (rule 3), compiled again from the new access.

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
| walk into a module | the hub finishes its storey, `closing` shows in the status, then the module opens with its crew |
| walk back to the hub | it opens again (it is "started"), from the side you came back by |
| walk around between buildings | the carpet lays the floor under you and a trail runs to the open site; no `standing on air` in the log |
| look at a finished building | it has 3 blocks of ground around it; its last wave was `finishing` |

## The status line

`/t3craft demo status` prints, after the usual prefix (`…, sites, N part(s), building 37%`):

```
sites 3/41 | open hub storey 2/4 (floor) structure wave 7/31 bays-left 9 left 5210/8400 crew 6 | focus hub (player inside)
 | builders 6/12 | off-site 0 storey-spread 0 task-switches walk 0 chunk 0 in-site 100% placed 3190 centre 4,12 level 1
```

| field | meaning |
|---|---|
| `sites a/b` | sites finished / sites of the plan (the details pass counts as one) |
| `open <name> …` | the open site: storey it is on and whether that storey starts at a real floor or is a fixed lift, stage, wave, bays of that wave not done, builder entries left / total, crew. `planning`: its program is being compiled. `closing`: the focus moved on, it closes at the end of the storey |
| `focus <name>` | the site the dispatch wants, and whether the player is inside it, in another site, or outside all |
| `builders a/b` | builders holding a task / builders of the crew |
| `off-site` | **criterion: 0.** Builder placements in a site that was not open |
| `storey-spread` | **criterion: at most 1.** Largest difference seen between the storey a builder placed in and the lowest storey of that site with work left. The finishing pass is left out. 1 is normal (a piece waiting for the floor above). More than 1 means a piece waited two storeys for its support |
| `task-switches walk` | **criterion: 0.** Builders taken off a bay that still had entries because the focus changed |
| `task-switches chunk` | builders that left a bay because its chunk was not loaded (not a fault) |
| `in-site` | **criterion: near 100 %.** Share of builder placements, while the player was inside a site, that went to that site |
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
| `siteStorey` | 5 | height of a storey, blocks |

Reused as they are: `entriesPerBuilder` (one more builder per this many entries the site has left), `radiusMin` +
`radiusStep` (the carpet's radius), the corridor keys, `groundBlocksPerTick`, `fillBlocksPerTick`,
`climbBlocksPerTick`, `radialCrewMax` (12 builders on a big plan). `build.fastFoundation`, the ring keys and
`radialMinBuilders` do nothing with `"sites"`.

Constants in the code: bay 6 blocks, keep radius 32, bucket 8, footprint margin 2, hold 1 s, apron 3, trail 48 × 3,
real floor = a sixth of the columns and 12 cells, smallest storey 3, 6 rounds per stage.

## Not done / open

- **Support across sites is optimistic**: another site counts as standing (see above). A site built on top of
  another site (a hall over a basement of a different group) can go up before what is under it. The citadel has no
  such pair (a group there is a zone on the map), other layouts may.
- **Stages are told apart by block properties only** (opaque, collision, light): a lantern is a finish, a slab or a
  stair is structure, a carpet is enclosure. No list of block types.
- **Many small waves.** A storey is up to five stages and each can be several rounds. The crew is sized again at
  every wave, so builders stand idle for a moment on short waves (the last bays of a roof, the lights).
- **One site at a time.** A building left for another waits with its storey closed until the player is near again.
  The rear guard is phase 3.
- **The ground between sites**, beyond the aprons, the carpet, the trail and the corridor, waits for the last sweep.
- **Numbers are first guesses**: storey 5, bay 6, crew 2–6, hold 1 s, the real-floor threshold, apron 3.
- The perf timers reuse the radial keys (`step.build.radial*.ns`).

## Compile risks

Nothing here was compiled. Where a mistake is most likely:

- `BuildTuning` gained 4 record components (`sites`, `siteCrewMin`, `siteCrewMax`, `siteStorey`). A constructor
  with the previous 30 arguments was added so older callers keep compiling; a caller of the canonical constructor
  outside this repo's `main` and `client` source sets would break.
- `BuildPlan` has a new private constructor and `named` / `name` / `workName` / `Builder.nameWork`.
- `SiteCursor.java` and `SiteProgram.java` are long and use fastutil (`IntArrayList`, `LongArrayList`,
  `BooleanArrayList`, `Long2IntOpenHashMap`) and jspecify `@Nullable` on arrays and nested types, as
  `RadialCursor` does.
- Syntax was checked with a tree-sitter Java grammar; types were checked by reading only.

## Config and restarts

**RESTART** (`BuildTuning` and `BuildPlan` changed shape; three new classes).
