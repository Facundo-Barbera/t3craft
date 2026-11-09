# feat/hq-design: modular HQ (roster → layout → plan → builders)

Package `dev.t3craft.hq`. Most of it is in the main source set. `HqPreviewCommand` and `HqAnchorSource`
are in the client source set. The generator produces the HQ **as data**. It never touches the world.
Only `/t3craft debug hq-preview` writes blocks, and only in a singleplayer superflat world.

```
roster (env, project, thread, settled)
  └─ HqInput.fromRoster / fromNpcs        canonical: sorted, de-duplicated, order-independent
       └─ HqLayout.allocate(in, previous)  modules, desk seats, shelf slots     (plan(in) = no history)
            └─ HqGenerator.generate(layout) → hq.BuildPlan   placements + anchors + workstations + groups
                 ├─ HqPlans.byGroup / expansion / forRoster → dev.t3craft.build.BuildPlan (layered)
                 ├─ HqSite(origin, rotation)  world cells/anchors, same transform as BuildJob
                 └─ HqAnchorSource(plan, site) → npc.behavior.AnchorSource
```

## Coordinates and layout

The HQ origin `(0,0,0)` is the centre of the hub floor. `y = 0` is the floor layer, so people stand at
`y = 1`. North is `-z`. The unrotated HQ has its entrance on the south side (`+z`) and grows north.

```
                         spine cap
     module 3  ...  [ spine row 1 ]  ...  module 2        module i: spine row i/2,
     module 1  ...  [ spine row 0 ]  ...  module 0        east (+x) if i is even, west if odd
                    +---------------+
                    |      hub      |   board at z=-4
                    +shelf door shelf   "Archived" shelf: z=7, 3<=|x|<=6, both sides of the door
                        portico           entrance z=8, spawn z=11 (looking north)
           (no basement: y=0 is the lowest layer)
```

- **Hub** (17×17, `y 0..10`): a timber hall with a stepped waxed-copper roof and a glazed lantern. It
  holds the "needs attention" board, benches, bookshelf corners and the "Archived" shelf.
- **"Archived" shelf** (group `archive`, modules `archive_shelf_east` / `archive_shelf_west`, each
  4×4×1 set into holes of the hub): the inside of the hub's south wall, both sides of the entrance
  (`z = 7`, `3 ≤ |x| ≤ 6`), three tiers (`y = 1..3`) facing north into the hub, so you see it on
  your way out and the NPCs see it from the board. An empty slot is a bookshelf; a slot holding a
  settled thread's computer shows black concrete (the blocks module swaps it for a monitor bound
  to that thread through the `archive_<threadId>` anchor). Over the east half hangs an "Archived
  threads" sign; over the west half an "Older threads / +N / click to list" sign, the click target
  for the settled threads that no longer fit (`layout.overflow()`, N = its size).
- **Why not a basement.** The first version put the archive in a 17×5×17 basement under the hub
  with a stairwell. In the demo world it was hard to reach and badly joined to the hub floor, and
  a room nobody visits does not show anything. It is gone from the layout (no config option): the
  HQ has nothing below `y = 0`, the hub floor is solid, and the envelope starts at `y = 0`.
- **Portico** and **spine** (a glass-roofed gallery with one 14-block row per pair of modules, and a
  sign over each module's doorway) work as before.
- **Module** = a wing, always the same size: inner wall with doorway, **3 segments × 2 desks = 6
  desks** (`HqLayout.DESKS`), end cap, and a garden strip. A module never grows. A project that
  needs more desks gets another identical module.
- **Workstation** (one per desk): a barrel drawer, two desk tops, the **computer** (black concrete
  when taken, grey when vacant), the status lamp (a waxed copper bulb), a fern, and the free cell in front of the
  computer where the NPC works **standing** (no chairs: it is a game, chairs add nothing).

### Anchor rule

Every **standing** anchor (`AnchorNames.standing(name)`: spawn, entrance, hub, board front and
slots, benches, stroll points, doors, desks, the spot in front of the "Archived" shelf) is a cell
an NPC can walk into, judged by the client navigation's own `Collision` (0.6 × 1.8 body, real
collision boxes, body sweep between cells): the body fits exactly at the anchor, standing on a
floor, and the cell is reachable from the spawn. Anchors on a carpet (the wing runner)
are raised onto its top (`PlanBuilder` lifts them), so the body never overlaps a block. So no
anchor is ever a stair or a chair: the desk anchor is the free cell in front of the computer (the
NPC works standing, facing it), and `bench_*` is the cell in front of each hub bench. `HqAnchorPlacementTest` checks this with a flood fill over
the plan for many rosters, annexes and a history. Surface anchors (board, lamps, monitors, screens,
archived computers, shelf slots and signs) mark a block face and are not standing spots.

Without an HQ, the grid fallback (`GridLayout`, `dev.t3craft.client.npc.behavior`) grounds each
desk with `Ground.near(Collision)`: the walkable cell of that column nearest the origin height,
so desks land in free cells of the real world. Open (owned by npc behaviour, not the HQ): a column
with no walkable cell within `MAX_STEP` falls back to the origin height unchecked, and `board()` /
`home()` use the origin height without probing.

### Environments

Environments are told apart by colour and sign, never by separate buildings. The carpet runner in a
module's corridor and the text on its spine sign use the environment's dye colour,
`DyeColor.byId((envKey.hashCode() * 0x9E3779B9) >>> 28)`. `String.hashCode` is fixed by the Java spec,
so an environment always keeps its colour. Two environments can share a colour, but `vps` (blue) and
`acmesite` (black) do not. The sign shows the project name, `#n` on annex modules (n ≥ 2) and the
environment label.

## Allocation rules (`HqLayout.allocate(input, previous)`)

1. **Desks are kept.** An active thread that had a desk in one of its project's modules keeps it.
2. **Modules are kept** at their grid index while at least one of their desks is taken. A module
   with no active thread left is removed and its index is freed.
3. **Desk reuse.** New active threads of a project, taken in thread-id order, each get the free desk
   with the lowest `(module index, slot)` among that project's modules.
4. **Annex.** If the project has no free desk, a new identical module is added at the **lowest free
   grid index**, and the thread takes slot 0. Projects are served in key order.
5. **"Archived" shelf.** A settled thread frees its desk (rule 2 can then remove the module). Its
   computer goes onto the shelf, which shows the `HqLayout.ARCHIVE_CAPACITY` = 24 **most recently
   archived** threads. Each shelved thread has an archive number `Archived.seq()`. A thread already
   on the shelf keeps its slot and number (a shelved computer never moves). New settled threads,
   ordered by `(project key, thread id)`, get the next numbers. If they do not all fit, the lowest
   numbers (the oldest) leave the shelf for `layout.overflow()`, and the newcomers take the lowest
   free slots. Threads in `overflow` stay there, since they are older than everything on the
   shelf, except that a slot freed because a shelved thread left the roster or became active again
   is refilled from `overflow` in thread-id order. A thread that leaves the roster leaves both, and
   one that becomes active again gets a desk. Without history (`plan(in)`) "oldest" means first in
   `(project key, thread id)` order.

**Idempotence:** `HqInput` is canonical, so the same roster lines in any order give the same input,
the same layout and the same plan (byte-for-byte fingerprint). `HqLayout.plan(in)` (no history) is a
pure function of the roster, and `allocate(in, plan(in)).equals(plan(in))`. To keep the building
stable across changes, persist the layout (`toJson`/`fromJson`, format 3) and pass it as `previous`.
A format 2 layout (the basement era) is still read: its modules and desks are kept and its archive
is dropped, so the settled threads are shelved afresh on the next `allocate`.

`HqLayout.diff(old, new)` returns: `addedModules`, `removedModules`, `freedDesks` (module, slot,
thread), `seated` (thread → seat), `archived` (computers that go onto the shelf), `unarchived` (computers that leave the shelf: active
again, gone, or pushed to the older list).

### Growth envelope (`HqLayout.envelope(maxModules)`)

This is the box that every plan with at most `maxModules` modules fits in. feat/site reserves it
before the first build:

```
rows = ceil(maxModules / 2)
x: -17 .. 17     (-8 .. 17 when maxModules == 1; -8 .. 8 when 0)
y:   0 .. 10     (floor .. lightning rod)
z: -9 - 14*rows .. 13     (spine cap .. portico; -8 .. 13 when 0)
```

Lowest-free allocation keeps every module index below the largest number of modules the HQ ever had
at the same time, so `maxModules` is that peak, not the number of projects. Each extra pair of
modules adds 14 blocks to the north. The size is 35 × 11 × (22 + 14·rows). Tests check this against
random histories. Rotate the box with `HqSite` for world coordinates.

## Output for builders

`hq.BuildPlan` (placements in module order, with every cell listed, air included) is converted with
`HqBuild` / `HqPlans` into `dev.t3craft.build.BuildPlan`:

```java
HqGenerator.Result r = HqGenerator.plan(HqInput.fromRoster(roster), lastLayout);   // persist r.layout()
HqSite site = HqSite.at(r.plan(), AnchorNames.SPAWN, chosenCell, Direction.SOUTH);  // or new HqSite(origin, rotation)

// first build: one plan per group, in build order (hub, archive [the shelf], portico, spine_0.., spine_cap, module_0..)
Map<String, dev.t3craft.build.BuildPlan> parts = HqPlans.byGroup(r.plan());
// later changes: only the groups that changed, each one on its own (snapshot / undo per expansion)
Map<String, dev.t3craft.build.BuildPlan> step = HqPlans.expansion(lastPlan, r.plan(), HqBuild.AIR_ABOVE_GROUND);
for (var p : step.values()) Builders.service().build("HQ", dim, site.origin(), site.rotation(), p, 40, 3);

// demo world: the whole HQ of a roster relative to the fixed origin
List<dev.t3craft.build.BuildPlan> all = HqPlans.forRoster(roster);
```

- Plans are **relative to the HQ origin**. The builder applies `origin + pos.rotate(rotation)` and
  `state.rotate(rotation)`, which is exactly what `BuildJob` does. `HqSite` uses the same transform
  for cells, points (continuous points rotate about the cell centre) and facings. A test checks it
  against `BuildJob.worldPos` for all four rotations.
- `HqSite.facing(dir)` gives the rotation that turns the entrance towards `dir`. `HqSite.at(plan,
  anchor, worldCell, dir)` puts a named anchor on a given world cell.
- **Order:** `Order.LAYERS` (the default) uses `BuildPlan.layered()`: bottom-up, snaking inside each
  layer. `Order.MODULES` keeps the plan's own module order.
- **Shapes are baked into the plan.** Glass panes, copper bars, walls and stair corners get their
  connections at generation time (`PlanShapes` runs vanilla's `updateFromNeighbourShapes` against a
  read-only view of the plan). The builders' no-neighbour-update placement therefore gives the final
  look without a post-pass, in any order and any rotation. Railings use copper bars because
  fences connect through block tags, which are not loaded in the JUnit tests.
- Groups: `hub`, `archive`, `portico`, `spine_<row>`, `spine_cap`, `module_<index>`
  (`HqGenerator.group(i)`). `BuildPlan.groupAt(rel)` tells which group owns a cell.
- Sign text is in `Placement.be()` (moved to `build.Placement.blockEntity()`). The `ServerLevelSink`
  of the builders loads it.

## Anchors (`AnchorNames`) and workstations

| name | where | facing |
|---|---|---|
| `spawn`, `entrance`, `hub` | portico `(0,1,11)`, hub door `(0,1,8)`, hub centre | N, S, S |
| `board` | centre of the board's front face `(0.5,3.5,-3)` | S (face normal) |
| `board_front`, `board_slot_0..13` | standing spots in front of the board, nearest first | N (looking at it) |
| `board_lamp_0..3` | the board's bulbs, left to right seen from the front | S |
| `bench_0..9` | in front of the hub benches (resting spots) | into the hub |
| `walk_hub_0..3` | open hub floor | S |
| `archive` | standing spot in front of the shelf `(4,1,6)` | S (looking at it) |
| `shelf@<i>` (`archiveShelfSlot(i)`, i = 0..23) | face of shelf slot i, taken or not; i = eye-level tier, then top, then bottom, nearest the door first, east before west | N (into the hub) |
| `archive_<threadId>` | screen face of a shelved computer (same cell as its `shelf@<slot>`) | N |
| `shelf_header` | the "Archived threads" sign `(4,4,7)` | N |
| `shelf_more` | the "Older threads / +N / click to list" sign `(-4,4,7)`: click target for `overflow()` | N |
| `wing_<projectKey>`, `walk_<projectKey>` | doorway / corridor of the project's **first** module | into the module |
| `module@<i>`, `walk@<i>` | the same, for any module | into the module |
| `desk_<threadId>` | the free cell in front of the computer, standing, at foot level | towards the computer |
| `monitor_<threadId>` | centre of the computer's screen face | towards the NPC |
| `lamp_<threadId>` | the status bulb (toggle `lit`) | towards the NPC |

`BuildPlan.workstations()` lists **every** desk, taken or vacant: `Workstation(module, slot, threadId,
deskAnchor, computerPos, screen, lampPos)`. `screen` is the face centre of the computer with
`screenFacing()`. That is the contract for feat/workstation (live screen, click opens the chat). A
desk's positions depend only on `(module, slot)`. `plan.workstation(threadId)` looks one up.

For feat/archive-view (settled threads read from T3): use `shelf@<i>` for the shelf positions and
`layout.archive()` (slot → thread) to know which thread is on which slot, `layout.overflow()` for
the paginated "older" list opened from `shelf_more`. Slot positions depend only on `i`.

`HqAnchorSource(plan, site)` adapts this to `dev.t3craft.client.npc.behavior.AnchorSource`:

| method | source |
|---|---|
| `desk` | `desk_<npcId>` |
| `board` / `boardSlots` | `board_front` / `board_slot_*` |
| `home` | `walk_<groupId>` |
| `wanderRadius` | 5 |
| `rest` | a bench picked by a stable hash of `npcId` |

Unknown ids return null, so the director's ring fallback is used. Register it with
`T3CraftClient.get().npcs().behavior().setAnchorSource(src)`.

## `/t3craft debug hq-preview`

It only works in a singleplayer world where `ServerLevel.isFlat()` is true.

- `hq-preview`: the live NPCs, or a demo when there are none.
- `hq-preview demo <projects> <threads>`: projects alternate between `vps` and `acmesite`, and every
  fifth thread is settled.
- `hq-preview live` and `hq-preview clear`.

Each run applies only the diff against the previous preview. It prints one line per module (index,
side, environment, project, desks taken, `[new]`) and the layout diff (modules ±, desks freed,
seated, archived). Try `demo 2 6` → `demo 2 9` (8 active threads: an annex module) → `demo 2 6` (the annex is
removed again; `t4` stays on the shelf). Superflat worlds are only 4 blocks deep, so the first preview keeps the HQ's
floor above bedrock.

## Tests (`mod/src/test/java/dev/t3craft/hq/`)

- `HqLayoutTest`: roster order independence (shuffles, duplicate lines, labels), fixed point, dense
  modules, annexes of identical modules at the lowest free index, removal and reuse of empty
  modules, desk reuse, moving between projects, settling frees the desk (and removes an emptied
  module), stable shelf slots, newest-on-the-shelf with the oldest pushed to the older list and
  refilled from it, full shelf as a fixed point, layout diff, envelope formula, JSON round trip,
  reading format 2 layouts.
- `HqGeneratorTest` (boots vanilla `Bootstrap`): determinism (also shuffled roster → same plan and
  same `forRoster`), fixed footprint, envelope (also over a random 60-step history), fixed module
  sizes, no collisions (up to 20 projects), fixed anchors (board slots, benches, walk points,
  archive spot), desks/workstations, no basement (nothing below `y = 0`, solid hub floor), fixed
  named shelf slots inside the hub facing free cells, the shelf's signs and "+N" count, environment colours, baked shapes, annexes (a new desk, a new module touches only
  `module_n` / `spine_n` / `spine_cap`, a settled thread changes exactly two cells, a removed
  project), conversion to the builders' plan (layered, complete, per group), `HqSite` vs `BuildJob`.
- `HqAnchorPlacementTest`: the anchor rule above (free, standable, reachable from the spawn) over
  many rosters, annexes and a history; NPCs standing at their desks facing the computer; determinism and
  idempotence.
- `HqAnchorSourceTest`: world anchors under the four entrance directions, and fallbacks.

## Integration points

1. **Client init**: unchanged. `HqPreviewCommand.register(npcs::all)` is already in `T3CraftClient`.
2. **feat/site**: reserve `HqLayout.envelope(N)` rotated by `HqSite`, then choose `origin` and
   `rotation` (or use `HqSite.at(...)`).
3. **Builders / world HQ owner**: persist `layout().toJson()`. Build `HqPlans.byGroup` the first time,
   then `HqPlans.expansion(lastPlan, plan, vacated)` for each change.
4. **feat/workstation**: `plan.workstations()` → `site.cell(computerPos)` and `site.anchor(screen)`.
5. **NPC behaviour**: `setAnchorSource(new HqAnchorSource(plan, site))`.
6. **feat/demo-world** (`HqStructureSource` is in main but not registered): use
   `new HqStructureSource(HQ_PLAN_VERSION, () -> HqPlans.forDemo(roster))`. `forDemo` bakes
   `HqPlans.demoSite()` into the plans, so they follow the demo/builders convention: entrance facing
   north, portico front on `z = 0`, growth towards `+z`, `x = 0` on the centre line, built with
   rotation `NONE`. `y = 0` is the hub floor and the lowest layer (no basement), so in the default
   superflat world (`minY = -64`) the HQ needs no lift. `DemoHq.PLAN_VERSION` went to 2 when the
   basement was removed, so an existing demo HQ is rebuilt whole. Its lift can drop too (default
   superflat: 2 → 0), so the new HQ may stand lower than the old one and leave leftovers of it: use
   "Reset HQ world" (`/t3craft debug reset-hq`) for a clean demo world. Bump the demo plan version whenever generator
   output changes. I did not register the source myself: it belongs to the demo module, and the
   origin decision above is needed first.

## Pending

- The module type is only `WING`. "A floor above" is in the API (`Module.type()`) but not built.
- The shelf holds 24 computers; older settled threads are only listed (`overflow()`, the "+N" on
  the `shelf_more` sign). The paginated list itself is feat/archive-view's.
- On real terrain the builders should lay a foundation under `footprint()` (`y < 0`) or level the
  site. `HqBuild.AIR_ABOVE_GROUND` fills vacated cells with grass at `y ≤ 0`.
- Walls connect through block tags, so in game a wall next to a wall can bake differently than in
  tests. Today no two HQ walls touch.
- Not tested in game (no Minecraft client was started, per the rules). The world writes of the
  preview are covered only by the plan tests.
