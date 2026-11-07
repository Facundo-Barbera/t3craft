# feat/hq-design: modular HQ (roster → layout → plan → builders)

Package `dev.t3craft.hq`. Most of it is in the main source set. `HqPreviewCommand` and `HqAnchorSource`
are in the client source set. The generator produces the HQ **as data**. It never touches the world.
Only `/t3craft debug hq-preview` writes blocks, and only in a singleplayer superflat world.

```
roster (env, project, thread, settled)
  └─ HqInput.fromRoster / fromNpcs        canonical: sorted, de-duplicated, order-independent
       └─ HqLayout.allocate(in, previous)  modules, desk seats, archive slots   (plan(in) = no history)
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
                    |      hub      |   board at z=-4; stairs down to the archive at x=4..5
                    +---- door -----+
                        portico           entrance z=8, spawn z=11 (looking north)
           (basement under the hub, y=-5..-1: the archive)
```

- **Hub** (17×17, `y 0..10`): a timber hall with a stepped waxed-copper roof and a glazed lantern. It
  holds the "needs attention" board, benches, bookshelf corners and a railed stairwell down to the
  archive.
- **Archive** (basement, 17×5×17, `y -5..-1`): a library with shelf rows of bookshelves. When a slot
  holds a settled thread's computer, that slot shows black concrete facing the aisle. Hanging
  lanterns light the aisles.
- **Portico** and **spine** (a glass-roofed gallery with one 14-block row per pair of modules, and a
  sign over each module's doorway) work as before.
- **Module** = a wing, always the same size: inner wall with doorway, **3 segments × 2 desks = 6
  desks** (`HqLayout.DESKS`), end cap, and a garden strip. A module never grows. A project that
  needs more desks gets another identical module.
- **Workstation** (one per desk): a barrel drawer, two desk tops, the **computer** (black concrete
  when taken, grey when vacant), the status lamp (a waxed copper bulb), a fern and the chair.

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
5. **Archive.** A settled thread frees its desk (rule 2 can then remove the module). Its computer
   goes to the archive. A thread that already had an archive slot keeps it. New settled threads,
   ordered by `(project key, thread id)`, take the lowest free slot. A thread that leaves the roster
   frees its slot, and a thread that becomes active again leaves the archive. Capacity is
   `HqLayout.ARCHIVE_CAPACITY` = 148 slots (74 shelf columns × 2 tiers, in a fixed order). Beyond
   that, settled threads are only listed in `layout.overflow()`.

**Idempotence:** `HqInput` is canonical, so the same roster lines in any order give the same input,
the same layout and the same plan (byte-for-byte fingerprint). `HqLayout.plan(in)` (no history) is a
pure function of the roster, and `allocate(in, plan(in)).equals(plan(in))`. To keep the building
stable across changes, persist the layout (`toJson`/`fromJson`, format 2) and pass it as `previous`.

`HqLayout.diff(old, new)` returns: `addedModules`, `removedModules`, `freedDesks` (module, slot,
thread), `seated` (thread → seat), `archived` (computers that go to the archive), `unarchived`.

### Growth envelope (`HqLayout.envelope(maxModules)`)

This is the box that every plan with at most `maxModules` modules fits in. feat/site reserves it
before the first build:

```
rows = ceil(maxModules / 2)
x: -17 .. 17     (-8 .. 17 when maxModules == 1; -8 .. 8 when 0)
y:  -5 .. 10     (basement floor .. lightning rod)
z: -9 - 14*rows .. 13     (spine cap .. portico; -8 .. 13 when 0)
```

Lowest-free allocation keeps every module index below the largest number of modules the HQ ever had
at the same time, so `maxModules` is that peak, not the number of projects. Each extra pair of
modules adds 14 blocks to the north. The size is 35 × 16 × (22 + 14·rows). Tests check this against
random histories. Rotate the box with `HqSite` for world coordinates.

## Output for builders

`hq.BuildPlan` (placements in module order, with every cell listed, air included) is converted with
`HqBuild` / `HqPlans` into `dev.t3craft.build.BuildPlan`:

```java
HqGenerator.Result r = HqGenerator.plan(HqInput.fromRoster(roster), lastLayout);   // persist r.layout()
HqSite site = HqSite.at(r.plan(), AnchorNames.SPAWN, chosenCell, Direction.SOUTH);  // or new HqSite(origin, rotation)

// first build: one plan per group, in build order (hub, archive, portico, spine_0.., spine_cap, module_0..)
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
  look without a post-pass, in any order and any rotation. The railing uses copper bars because
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
| `bench_0..9` | hub bench seats (resting spots) | into the hub |
| `walk_hub_0..3` | open hub floor | S |
| `archive` | main aisle of the basement | N |
| `archive_<threadId>` | screen face of an archived computer | towards the aisle |
| `wing_<projectKey>`, `walk_<projectKey>` | doorway / corridor of the project's **first** module | into the module |
| `module@<i>`, `walk@<i>` | the same, for any module | into the module |
| `desk_<threadId>` | the chair, at foot level | towards the computer |
| `monitor_<threadId>` | centre of the computer's screen face | towards the chair |
| `lamp_<threadId>` | the status bulb (toggle `lit`) | towards the chair |

`BuildPlan.workstations()` lists **every** desk, taken or vacant: `Workstation(module, slot, threadId,
deskAnchor, computerPos, screen, lampPos)`. `screen` is the face centre of the computer with
`screenFacing()`. That is the contract for feat/workstation (live screen, click opens the chat). A
desk's positions depend only on `(module, slot)`. `plan.workstation(threadId)` looks one up.

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
removed again; `t4` stays archived). Superflat worlds are only 4 blocks deep, so the first preview lifts the HQ until its
basement fits above bedrock.

## Tests (`mod/src/test/java/dev/t3craft/hq/`)

- `HqLayoutTest`: roster order independence (shuffles, duplicate lines, labels), fixed point, dense
  modules, annexes of identical modules at the lowest free index, removal and reuse of empty
  modules, desk reuse, moving between projects, stable archive slots, overflow, layout diff,
  envelope formula, JSON round trip.
- `HqGeneratorTest` (boots vanilla `Bootstrap`): determinism (also shuffled roster → same plan and
  same `forRoster`), fixed footprint, envelope (also over a random 60-step history), fixed module
  sizes, no collisions (up to 20 projects), fixed anchors (board slots, benches, walk points,
  archive), desks/workstations, stairs and headroom down to the archive, archive slots facing free
  aisles, environment colours, baked shapes, annexes (a new desk, a new module touches only
  `module_n` / `spine_n` / `spine_cap`, a settled thread changes exactly two cells, a removed
  project), conversion to the builders' plan (layered, complete, per group), `HqSite` vs `BuildJob`.
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
   rotation `NONE`. `y = 0` is the hub floor and the basement reaches `y = -5`. In the default
   superflat world (`minY = -64`, grass at -61), the demo origin must have `y ≥ -58`, so the HQ stands
   on a 2-block tuff plinth and the portico floats 2 blocks up. The other options are a deeper flat
   preset or accepting that basement cells fail. Bump the demo plan version whenever generator
   output changes. I did not register the source myself: it belongs to the demo module, and the
   origin decision above is needed first.

## Pending

- The module type is only `WING`. "A floor above" is in the API (`Module.type()`) but not built.
- The archive has a fixed capacity, and overflow is only listed. Rotating old computers out is a
  product decision.
- On real terrain the builders should lay a foundation under `footprint()` (`y < -5`) or level the
  site. `HqBuild.AIR_ABOVE_GROUND` fills vacated cells with grass at `y ≤ 0`.
- Walls connect through block tags, so in game a wall next to a wall can bake differently than in
  tests. Today no two HQ walls touch.
- Not tested in game (no Minecraft client was started, per the rules). The world writes of the
  preview are covered only by the plan tests.
