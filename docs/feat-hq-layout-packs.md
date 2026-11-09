# HQ layout packs (feat/hq-layout-packs)

Iterate on the HQ maps without touching Java and without restarting the game: materials, a set of
look parameters and whole rooms come from files in `config/t3craft/hq-layouts/`, are watched every
second and applied to the built HQ as a diff. **No files = the HQ is exactly the code's** (same
blocks, same plan fingerprints, same demo hashes).

```
config/t3craft/hq-layouts/
  classic.json               one file per layout id (all sections optional)
  campus.json
  ...
  rooms/
    campus/hall_3.nbt        a structure-block room that replaces module "hall_3" of campus
  _defaults/                 written by "Export layouts": reference copies, never read as packs
    campus.json              every role / palette field / parameter at the code's value
    MODULES.txt              module ids and boxes of every layout (sample HQ), for rooms
```

Layout ids: `classic`, `campus`, `ring`, `vertical`, `ensanche`, `village`, `hybrid-ringcampus`,
`hybrid-towncenter`, `hybrid-terraces`. A `.json` with another name is reported and ignored.

## Workflow

1. HQ menu (title screen → T3Craft HQ) → **Export layouts**. It writes `_defaults/*.json`,
   `_defaults/MODULES.txt`, the `rooms/<layout>/` folders, and `<layout>.json` for every layout
   that has no file yet (existing files are never overwritten). The exported values are the code's,
   so loading them changes nothing.
2. Edit `<layout>.json` (or drop a `.nbt` in `rooms/<layout>/`) while the game runs.
3. Within a second the client notices (file sizes and modification times), reloads, shows a toast
   ("HQ layouts: reloaded campus") and the HQ world re-plans: only the cells that changed are placed.
   **Reload layouts** in the HQ menu does the same at once.
4. Preview without the game: `./gradlew hqRender -PhqArgs="--layout campus --packs <path>/config/t3craft/hq-layouts"`.

## File format

```jsonc
{
  "layout": "campus",                       // informative
  "roles":   { "FLOOR": "minecraft:oak_planks", "ROOF": "minecraft:mud_brick_stairs" },
  "palette": { "woodFloor": "minecraft:birch_planks" },
  "blocks":  { "minecraft:calcite": "minecraft:white_concrete" },
  "params":  { "roof.azaleaEvery": 4, "lighting.grid": 4 },
  "rooms":   { "hall_3": { "rotation": "clockwise_90", "offset": [0, 0, 0] } }
}
```

Comments (`//`) and trailing commas are accepted. Keys starting with `_` are ignored (the export
puts the parameter ranges in `_params`).

### Materials: `roles`, `palette`, `blocks`

Values are block states in command syntax: `minecraft:spruce_stairs[facing=north]` (namespace
optional). Replacement is **by block**, after generation and lighting:

- `roles`: any `HqStyle.Role` (`FLOOR, FOOTING, WALL, COLUMN, BEAM, ROOF, ROOF_TRIM, RIDGE, WINDOW,
  DOOR, DECOR, PATH, GARDEN, GARDEN_TREE, LIGHT, DESK, BENCH, BOARD, COMPUTER_ON, COMPUTER_OFF,
  STATUS_LAMP, SCREEN, BEACON, TERMINAL, PLAQUE`). Every block of the plan whose block is that role's
  block in the style that draws this layout becomes the new block.
- `palette`: any material field of `dev.t3craft.hq.Palette` (`plinth`, `woodFloor`, `darkPlanks`,
  `copper`, `stairBlock`, `copperStairs`, ...; `air` excluded). Same rule: that field's block is replaced.
  Palette is the material set of the **classic style** (`hq.style = classic`); the export writes it
  only in `classic.json`, but the key works in every pack.
- `blocks`: any block → any block, for materials that are not a role (the per-barrio materials
  of campus, ring stone, village timber, ...).

Precedence when two entries name the same source block: `blocks` > `palette` > `roles`.

**Properties.** A replacement written without properties (`minecraft:oak_stairs`) keeps each
replaced block's own properties when the new block has them (stairs keep their facing/half/shape,
logs their axis, slabs their type, leaves `persistent`), so roofs stay roofs. Properties written
explicitly are forced on every block replaced (`minecraft:oak_slab[type=top]`). The export writes
block ids only, which is why loading it is a no-op.

Notes: roles that share a block (in cherry `SCREEN` and `COMPUTER_ON` are both black concrete)
cannot be told apart, changing one changes both. Lighting is computed with the code's materials:
swapping an opaque block for glass is fine, swapping glass or air-like decoration for an opaque
block can leave a dark spot. Screens, desks and the board are swapped by the blocks module anyway
when it is on.

### Parameters: `params`

Read while the plan is generated (`dev.t3craft.hq.pack.LayoutParams`, never a `static final`) and
clamped to a safe range: a strange value can only reach the nearest end of the range. Only
spacings and densities of things that never carry an anchor, a desk or a path are exposed, and the
built-in lighting (`HqLighting`) runs after them, so fewer decorative lamps still leave every
walkable cell at block light ≥ 10.

| layout | key | default | range | changes |
|---|---|---|---|---|
| every layout | `lighting.grid` | 5 | 3..5 | spacing of the floor-lamp grid (smaller: more lamps) |
| every layout | `lighting.lattice` | 9 | 5..9 | floor-lamp lattice on outdoor grounds (campus-style sparse groups) |
| classic | `portico.petalsEvery` | 3 | 2..9 | pink petals on 1 portico garden cell in N (rest moss) |
| classic | `spine.inlayEvery` | 4 | 3..12 | classic style: copper inlay every N blocks down the spine |
| campus | `roof.azaleaEvery` | 7 | 3..31 | flat roof gardens: flowering azalea on 1 cell in N |
| campus | `roof.mossEvery` | 3 | 2..15 | flat roof gardens: moss carpet on 1 cell in N |
| campus | `plaza.lampPostEvery` | 6 | 4..12 | hub plaza: lamp post every N blocks on its outer ring |
| ring | `terrace.bushEvery` | 23 | 11..97 | terraces: azalea bush on 1 cell in N |
| ring | `patio.planterEvery` | 11 | 7..47 | inner patio: planter / azalea on 1 cell in N |
| vertical | `facade.lightEvery` | 4 | 3..9 | lit panels in the tower facades every N blocks |
| ensanche | `hall.ceilingLightEvery` | 6 | 3..9 | hall ceiling light panels on an N×N grid |
| village | `hall.ceilingLightEvery` | 4 | 3..8 | hall ceiling light panels on an N×N grid |
| village | `lanes.lanternEvery` | 11 | 5..41 | garden lanterns beside the lanes: 1 cell in N |
| hybrid-ringcampus | `streets.lightEvery` | 41 | 17..97 | lights let into the streets: 1 cell in N |
| hybrid-ringcampus | `terrace.bushEvery` | 23 | 11..97 | terraces: azalea bush on 1 cell in N |
| hybrid-ringcampus | `catalog.enabled` | 1 | 0..1 | catalog placement (CitadelPlacement): 1 on, 0 off (the city as before the catalog) |
| hybrid-towncenter | `hall.ceilingLampEvery` | 6 | 4..9 | civic hall ceiling lamps on an N×N grid |
| hybrid-terraces | `temple.ceilingLightEvery` | 4 | 3..8 | temple ceiling light panels on an N×N grid |

An unknown key for that layout is an error (reported, last good pack kept).

**Not exposed (still code + restart):** hall widths, storey heights, lot depths, hub sizes and
every other measure that NPC anchors, desk slots, footprints (`*Footprints`), the survival site
frame and the walkability validators share. Changing one of those safely means moving anchors
and envelopes together, which is a code change. To reshape the inside of a module without
touching that geometry, use a room.

### Rooms: `rooms/<layoutId>/<moduleId>.nbt`

Design a room in game, save it with a structure block (**Save** mode; the file lands in
`<world>/generated/minecraft/structure/<name>.nbt`), copy it to
`config/t3craft/hq-layouts/rooms/<layoutId>/<moduleId>.nbt`. The module ids and their boxes
(minimum corner and size, plan coordinates, for a sample HQ) are listed in
`_defaults/MODULES.txt` (e.g. classic: `hub`, `portico`, `spine_0`, `wing_0_seg_1`,
`wing_0_desk_2`; campus: `hall_<index>`, pavilions, the hub; module ids of grown parts follow the
same pattern).

Placement:

- The structure's corner `(0,0,0)` goes on the module box's **minimum corner** (`min x, min y,
  min z` in plan coordinates; the HQ entrance faces north `-z` in plan coordinates), plus
  `offset` when the JSON gives one. `rotation` (`none`, `clockwise_90`, `180`,
  `counterclockwise_90`) turns the structure inside its own box (and its blocks) first.
- A file in the folder is used even without a `rooms` entry (no rotation, no offset).
- Only cells the module owns are written; cells outside the box, cells of another module and
  sparse grounds (campus paths) are left alone.
- Cells the structure does not list (**structure void**) keep what the generator put there; air in
  the structure clears the cell.
- Block entity data is kept (signs, chests, barrels...).
- The room is placed **before** lighting, so a dark room still gets its lamps.

Anchors and desks (the convention):

- **Desks never move**: a desk's screen (3×2), its desk top, the computer cell, the standing cell
  and the cell above it always keep the generator's blocks, whatever the room says. The same holds
  for the cell of every anchor and the head room over standing spots (benches, board slots,
  entrance, spawn, walk points...).
- **Data markers move anchors**: a structure block in **DATA** mode inside the room is never placed
  (its cell becomes air). Its *Custom Data Tag* (metadata) names the anchor:
  - `anchor:<name>` moves that anchor of the plan to the marker's cell, keeping its facing;
  - `anchor:<name>:<north|south|east|west>` moves and turns it (the direction turns with `rotation`).

  Desk anchors (`desk@<module>.<slot>`, `screen@<module>.<slot>`, `desk_<thread>`,
  `monitor_<thread>`) cannot be moved; other metadata is ignored. Anchor names are the ones of
  `AnchorNames` (`hub`, `board_front`, `board_slot_0`, `bench_0`, `walk_hub_0`, `module@<i>`,
  `walk@<i>`, `wing_<project>`, `spawn`, `entrance`, `archive`, `thread_wall_front`, ...).
- Keep the room walkable yourself: the standing cells are protected, the rest of the floor is
  yours. Check it in game (the NPCs) or in hqRender.

## Hot reload, errors and fingerprints

- `HqLayoutPackWatcher` (client) reads the folder at client init and polls it every 20 ticks.
  A change of any `.json` / `.nbt` (not `_defaults/`) reloads every pack.
- A file that fails (JSON syntax, unknown block, role, field or parameter, bad structure file)
  keeps **the last good pack of that layout** (a broken `.nbt` keeps its last good room) and the
  error is shown as a toast plus a red chat line, `file:line: what` (for JSON syntax errors, Gson's
  `line N column M`).
- Every plan generated with a pack carries the pack's hash (`BuildPlan.packHash()`), mixed into
  `BuildPlan.fingerprint()`. Without a pack nothing is mixed in, so existing worlds keep their
  fingerprints.
- **Demo / creative HQ world** (`DemoClient`): within a second of a reload it re-plans; the demo
  hash includes the pack hash and the marker records it (`"pack"`). The old plan is regenerated
  with the pack it was built with (packs seen this session are remembered), and the difference is
  applied through `HqDiff` (removals, then builds), part by part. If the old pack is unknown
  (it changed while the game was closed), the HQ is rebuilt whole (same as a fixtures change).
- **Survival site** (`HqConstruction`): a reload invalidates the target, so the next construction
  tick computes the new design and the existing in-place update pass places the changed cells
  (same path as any other design change). After a restart with a changed pack, use the existing
  rebuild (HQ menu → Reset/Rebuild) if the world should follow.
- Determinism: the same pack and roster give the same plan (no clock, no randomness; rooms and
  remaps iterate in a fixed order).

## What still needs a restart

- Any Java change (generators, styles, geometry, anchors, new layouts) — as before.
- This feature itself (new client tick watcher, new menu buttons, lang keys): **RESTART** once.

After that, every edit in `config/t3craft/hq-layouts/` is applied live.

## Code map

- `dev.t3craft.hq.pack.HqLayoutPacks` — folder, reload/poll, active packs, history, export.
- `dev.t3craft.hq.pack.LayoutPack` — parsed JSON (+ rooms), hash, `file:line` errors.
- `dev.t3craft.hq.pack.LayoutParams` — parameter specs, clamps and defaults.
- `dev.t3craft.hq.pack.RoomTemplate` — structure `.nbt` reader (26.x `id/properties` and legacy
  `Name/Properties` palettes), data markers.
- `dev.t3craft.hq.pack.BlockStateText` — block state text parse/format/carry.
- `dev.t3craft.hq.PackApply` — rooms → lighting → materials → hash, called by `HqGenerator.generate`.
- `dev.t3craft.hq.PackDefaults` — export content (role/palette defaults, parameters, module list).
- `dev.t3craft.client.hq.layout.HqLayoutPackWatcher` — client watcher, toasts, menu actions.
- `HqMenuScreen` — **Export layouts** / **Reload layouts**.
- `HqRender --packs DIR` — the same folder in the headless renderer (use with `--layout <id>`; the
  older per-style galleries call their generators directly and ignore packs).
