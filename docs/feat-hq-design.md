# feat/hq-design: HQ generator (plan + anchors)

Package `dev.t3craft.hq` (main source set, plus `HqPreviewCommand` in the client source set).
The generator produces the HQ **as data**: a `BuildPlan` (an ordered list of `Placement`s relative
to an origin) and named `Anchor`s. It does not touch the world. The only thing that writes blocks
is the debug command `/t3craft debug hq-preview`, and it only runs in a singleplayer superflat world.

## What it builds

Origin `(0,0,0)` is the centre of the hub floor. `y = 0` is the floor layer; people stand at `y = 1`.
North is `-z`. Every module spans `y = 0..10`.

```
                        spine cap
    west wings  ...  [ spine row 1 ]  ...  east wings        wing i: row i/2,
  (odd index)   ...  [ spine row 0 ]  ...  (even index)      east if i is even, west if odd
                    +---------------+
                    |      hub      |   "needs attention" board at z=-4, facing the entrance
                    +---- door -----+
                        portico          entrance z=8, spawn z=11 (looking north)
```

- **Hub** (17×17): a timber hall (stripped dark oak posts and beams, tuff plinth, glass-pane windows,
  pale oak frieze) under a stepped waxed-copper roof with a glazed copper lantern and lightning rod
  on top. Inside: a copper chandelier over the light well, hanging lanterns, benches along the side
  walls, bookshelf corners, planters, a cyan runner from the door, and lamp pedestals at both
  doorways. The north doorway only opens once there is a spine.
- **Board**: a 7×3 polished deepslate panel in a timber frame, four copper "signal" bulbs on its
  copper header, and a glowing "Needs attention" sign. It faces the entrance.
- **Portico**: copper roof on timber posts, a path, azaleas and lantern posts. The spawn is under it.
- **Spine**: a gallery with a glass roof and timber cross-beams, one 14-block row per pair of wings,
  hanging copper lanterns and ferns. Above each wing's doorway there is a sign with the project's
  name and its environment. A cap closes the north end.
- **Wing** (one per project): an inner wall with the doorway, then `segments` modules of 4 blocks
  each (two desks per segment: one on the north wall, one on the south wall; a pillar column with a
  bookshelf, a potted azalea and a hanging lantern; a skylight; a carpet runner in the project's
  colour), then an end cap with windows. A garden strip (grass, bushes, lantern posts) separates it
  from the next row.
- **Desk** (one per slot): a barrel drawer and two dark oak desk tops, a monitor (black concrete
  when taken, grey when vacant), the **status lamp** (a waxed copper bulb, off), a fern and the
  chair (dark oak stairs).

## API

```java
HqInput in = HqInput.fromNpcs(views);                  // or HqInput.builder().thread(projectKey, label, env, threadId)
HqGenerator.Result r = HqGenerator.plan(in, previousLayout /* nullable */);
BuildPlan plan = r.plan();                             // placements(), anchors(), modules(), footprint(), fingerprint()
HqLayout keep = r.layout();                            // keep it (in memory or with toJson/fromJson) for the next change
List<Placement> todo = BuildPlan.diff(oldPlan, plan, rel -> AIR /* or ground at y<=0 */);
```

- `Placement(BlockPos rel, BlockState state, @Nullable CompoundTag be)` is the shared contract.
  `be` holds block entity data: today only the signs' text, waxed. Copy it before loading it.
- **Order** of `placements()`: modules in build order (hub, portico, spine rows, spine cap, then for
  each wing: inner wall, segments, desks, cap). Inside a module: structure first, then attached
  decoration, both bottom-up. Hanging blocks (chains, hanging lanterns) come last, top-down. This way
  every block comes after the block that holds it.
- **Every cell** of every module is listed, air included, so a plan builds the same thing on any
  terrain. `diff` turns cells that a newer plan no longer uses into the `vacated` block.

### Anchors (`AnchorNames`)

| name | where | `facing` |
|---|---|---|
| `spawn` | under the portico, cell `(0,1,11)` | north (into the hub) |
| `entrance` | hub door threshold `(0,1,8)` | south (outward) |
| `hub` | hub centre `(0,1,0)` | south |
| `board` | centre of the board's front face `(0.5, 3.5, -3.0)`, block `(0,3,-4)` | south (face normal) |
| `board_lamp_0..3` | the board's header bulbs, left to right as seen from the front | south |
| `wing_<projectKey>` | inside a wing's doorway | east/west (into the wing) |
| `desk_<threadId>` | the chair (seat block), foot level | towards the monitor |
| `monitor_<threadId>` | centre of the monitor's front face | towards the chair |
| `lamp_<threadId>` | the status bulb (toggle `lit`) | towards the chair |

`<threadId>` is the id given in `HqInput`. `fromNpcs` uses `NpcView.id()` (`env:threadId`), which is
unique across environments. `<projectKey>` is `NpcView.groupId()` (`env:projectId`).
`Anchor.block()` is the cell the anchor is about, and `Anchor.yaw()` gives the Minecraft yaw.

### Scaling by annexes (`HqLayout`)

- A project keeps its wing index. A new project takes the lowest free index. A project that is
  gone frees its wing (its cells go back to `vacated`).
- A thread keeps its desk slot. A new thread takes the lowest vacant slot. A thread that is gone
  leaves a vacant desk: in the diff, only its monitor changes.
- A full wing grows by one segment at the far end (the cap moves out 4 blocks). A wing never
  shrinks while its project exists. `HqLayout.allocate(input, null)` compacts.
- So the `diff` only touches the desk, segment or wing that changed. Tests check this cell by cell.

## Tests (`mod/src/test/java/dev/t3craft/hq/`)

`HqLayoutTest` (8 tests, pure) and `HqGeneratorTest` (27 cases; it boots vanilla `Bootstrap` in
JUnit, so it tests real `BlockState`s):

- **Determinism**: the same input twice gives equal placements, anchors and fingerprint. Shuffling
  the input order changes nothing.
- **Fixed footprint**: exact boxes for several project × thread counts, and a fixed size per module type.
- **No collisions**: positions are unique, each cell has exactly one owner module, placements =
  owned cells, holes are filled exactly by desk modules, and boxes only overlap through holes
  (`PlanBuilder` refuses any collision while generating). Tested up to 20 projects × 30 threads.
- **Anchors**: chairs, headroom, monitor in front at eye level, lamps are bulbs, walkable spawn,
  entrance and board front, sign text present, hanging lanterns supported.
- **Annexes**: a new thread changes only its desk; a full wing changes only the annex area; a gone
  thread changes only its monitor; a new project leaves existing wings, hub and portico untouched;
  a removed project's cells are vacated; existing anchors never move.

`./gradlew build` in `mod/` is green (all tests, old and new).

## `/t3craft debug hq-preview`

Only in a singleplayer world where `ServerLevel.isFlat()` is true; anywhere else it refuses.

- `hq-preview`: uses the live NPCs if there are any, otherwise a demo (3 projects × 4 threads).
- `hq-preview demo <projects> <threads>`, `hq-preview live`, `hq-preview clear`.
- The first run puts the origin so that you stand on the `spawn` anchor. Later runs in the same
  world reuse that origin and the last layout and apply only the diff. For example,
  `demo 3 4` → `demo 3 7` → `demo 4 2` shows desks, segments and wings being annexed and vacated.
- State lives in the command instance (no statics) and is dropped on disconnect.
- Placement goes through `PlanApplier`: place in order without neighbour updates, load `be`, then
  resolve shapes (panes, walls, stair corners) over the placed cells and their neighbours.

## Integration points

1. **Client init**: one line in `T3CraftClient.onInitializeClient`:
   `dev.t3craft.hq.HqPreviewCommand.register(npcs::all);`. The command merges into the existing
   `/t3craft` literal (Brigadier merges nodes), so `T3CraftCommands` is untouched. If another branch
   also adds a `debug` literal, the two merge too.
2. **World builder** (another module): call `HqGenerator.plan(HqInput.fromNpcs(...), lastLayout)`,
   persist `layout().toJson()` next to the world, and apply `BuildPlan.diff(lastPlan, plan, vacated)`
   at its chosen origin. It can pace this over ticks, but it must keep the three steps of
   `PlanApplier`. `PlanApplier` can be reused as is for instant builds.
3. **NPC behaviour**: walk to `desk_<id>` and sit facing `facing`. Use `entrance` and `spawn` for
   arrivals and `wing_<projectKey>` as a waypoint.
4. **Status lamps and board**: toggle `lit` on `lamp_<id>.block()` and on `board_lamp_i.block()`. Put
   text displays or item frames on the board's front face (`board`, a 7×3 area centred there) and on
   `monitor_<id>`.
5. **THIRD_PARTY_NOTICES.md**: the look is inspired by AgentCraft's StudioHqBuilder; no code was copied.

## Pending / ideas

- The generator does not lay a ground under the floor (`y < 0`). On uneven terrain the world builder
  should fill a foundation below `footprint()` or level the site.
- Wing labels are wall signs. A nicer label (a hanging sign or a text display) belongs to the module
  that renders text.
- Vacant desks have no anchor. If NPC behaviour wants free seats, add `desk_free_<wing>_<slot>`.
- Optional compaction (`allocate(input, null)`) is never triggered automatically. When to compact
  (for example "on world load if more than half the desks are vacant") is a product decision.
- Not tested in game: per the rules no Minecraft client was started. The preview command compiles and
  uses only verified 26.3 APIs. Its world writes are covered only by the plan tests, not by a live run.
