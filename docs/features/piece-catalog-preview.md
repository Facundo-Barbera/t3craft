# Piece catalog (feat/piece-catalog-preview)

Look at **one piece of the game at a time, at real scale, inside the T3Craft HQ world**, and change piece or reload it
**without restarting the client**. It is the way to iterate on the game's own buildings. The preview drop
(`docs/features/ingame-preview-drop.md`) stays for schematics that come from outside the game; its 120-block showroom is
not used here.

**Not compiled or played:** written by reading the code only (no processes allowed on the machine that wrote it).
The coordinator compiles; Facundo plays. See "Not verified yet".

## What is in the catalog

Everything that exists in the code and can be built without a world:

| group | pieces | where it comes from |
|---|---|---|
| `hq` | `hq_hub` (the central building), `hq_core` (hub + cloister + archive), `hq_cloister` (patio, bridges, gallery, ramps, terraces, inner street), `hq_node_tower`, `hq_node_rotunda`, `hq_node_archive`, `hq_node_lounge`, `hq_node_pillar`, `hq_ring_1` (street and avenues of ring 1, a big piece), `hq_barrio_small` (courtyard, gatehouse, one hall), `hq_barrio_large` (halls on two floors), `hq_lead_office` | the citadel's plan, cut by its own construction groups (`hub`, `cloister`, `node_<k>`, `lot_<id>` + `module_<i>`); the barrios from a plan with one project (4 / 13 threads) or a lead |
| `classic` | `classic_hub`, `classic_archive`, `classic_portico`, `classic_spine`, `classic_wings`, `classic_all` | the classic spine HQ with two projects (5 and 9 threads) |
| `kit` | `kit_swatch` | every `HqKit.Role` of the active kit (the blocks module's own blocks when it is on, vanilla stand-ins otherwise), one per plinth with a sign |
| `pack` | one `pack_<layout>_<module>` per room of the layout packs active now | `config/t3craft/hq-layouts/rooms/...` (docs/features/hq-layout-packs.md); shown as saved, not rotated |
| one per family | all 75 variants of the citadel catalog: `house_*` (20), `tree_*` (9), `fountain_*`, `lamp_*`, `stall_*`, `annex_*`, `garden_*`, `gate_*`, `gazebo_*`, `bridge_*`, `bench_*`, `planter_*`, `plaza_center_*`, `signpost_*`, `banner_pole_*`, `statue_*`, `tower_top_*`, `well_*` | `hq.citadel.catalog.Catalog` (docs/tools/citadel-catalog.md), built into a plot of the size you ask for |

A new variant registered in `CatalogEntries` appears in the catalog by itself (restart); nothing else to register.

## Commands

All client commands, HQ world only (elsewhere they say so and do nothing).

| command | what |
|---|---|
| `/t3craft piece` | what is on the stage, the groups with their counts (clickable), the command list |
| `/t3craft piece list [group]` | the pieces of a group; each id is clickable and shows the piece |
| `/t3craft piece show <id> [size] [front] [seed]` | puts the piece on the stage and takes you to it. Tab completes ids. `size`: `min`, `mid` (default), `max` or `WxDxH` (frontage x depth x height, up to 200); `front`: `n e s w` (default `s`); `seed`: a number (default 1). Size, front and seed only matter for catalog variants |
| `/t3craft piece next` / `prev` | the next / previous piece of the same group (from nothing: the first / last of `hq`) |
| `/t3craft piece reload` | builds the current piece **again from the code** and applies only the blocks that changed. You stay where you are |
| `/t3craft piece size <..>` / `front <..>` / `seed <..>` | the same piece with another plot size, facing, or seed |
| `/t3craft piece go` / `back` | to the stage's lobby / back to where the trip started (the arch in the lobby does the same) |
| `/t3craft piece clear` | takes everything off the stage |

About 8 seconds after joining the HQ world, chat shows `Piece catalog: N pieces ...` with **[Hub]** and **[Catalog]** buttons.
After each piece, chat shows its size and block count with **[prev] [next] [reload] [back]**.

## The stage

- **Where:** `OFFSET_X = -2000`, `OFFSET_Z = +3000` from the HQ origin, at the island's ground level (`origin.y - 1`), far from the HQ,
  from the preview-drop showroom (which starts at `z = 0` and grows south) and from each other.
- **Size:** as big as the piece needs: the piece plus 14 blocks of grass all round, at least 45 wide, and a 16-row lobby on the
  south side. Up to 600 blocks a side (the central building of the HQ is around 130-200). There is no 256 limit and no 1.5 million block limit.
- **What is on it:** a grass floor with a stone-brick border; the **ruler** on the north and west border, counted from the piece's own corner
  (a chiseled brick every 10 blocks, gold every 50, a lantern on the gold ones); in the lobby the arrival spot (you land looking north at
  the piece), a sign with the piece's name, size and block count, and the return arch ("Back to the HQ").
- **Orientation:** a catalog variant has its ground at the floor layer and faces the side you asked for (`front`); a part of the citadel keeps
  the plan's own heights (so the sunken lounge goes below the floor). Parts of the citadel are the plan's own cells of a construction group,
  from 8 layers under the ground up: exactly what the HQ builders place. The plan's air above the ground is not stored; the ground layer's air is
  (it clears a sunken room).
- Air above the stage is yours: anything you build by hand on the stage is removed the next time the piece changes.

## Changing piece and reloading

The stage holds one **scene** (piece + floor + lobby). Changing it is a diff against what the world holds, applied on the server
thread within a 25 ms budget per tick (a million blocks do not stall the game, they just take a few seconds):

1. **place** every cell of the new scene whose block differs from the world's;
2. **clear** every cell of the previous scene the new one does not have;
3. **sweep** (only when the previous scene is not known: the first piece of a session, or a switch while a change was still going up) the
   recorded area of the stage, clearing anything the new scene does not have. The area is saved with the world
   (`t3craft-piece.json`), so leftovers of an earlier session are cleaned too.

The piece is **built on a worker thread** (a part of the citadel needs the whole plan: a few seconds the first time; the empty citadel's plan is
the one the game already computes for the HQ world). Plans are kept for the session; `reload` generates them again.

**What reloads without a restart:**
- A different piece, size, front or seed: always.
- Layout packs: they are re-read by the game every second; `reload` (or the next `show`) uses the new pack. Pack rooms are re-read at each build.
- **Code changes**: `reload` runs the generator again. With the dev client started from the IDE in debug mode, a **hot swap** of a changed method body
  (docs/dev.md, "UI cycle") is picked up by the next `reload`, and only the blocks that changed are written. A changed class *shape* (new fields,
  new methods, new variant classes) needs a client restart; then the new variant also shows in the list.

## Code map

- `dev.t3craft.piece` (main source set, no world, no client code):
  - `Piece` the interface (`id`, `group`, `title`, `note`, `build(spec, fresh)`); `PieceSpec` the parameters; `PieceModel` the blocks, compact
    (packed position + state per cell, sorted positions for lookups); `PieceScene` the stage around a model (floor, ruler, lobby, arch, signs).
  - `PieceCatalog` the registry. `VariantPiece` (catalog variants), `PlanPiece` (parts of the citadel / classic plan by construction group), `KitPiece`,
    `PackPiece`.
- `dev.t3craft.client.piece` (client source set):
  - `PieceClient` init, worker thread, commands, chat. `PieceStage` the world side (server thread): scene queue, the three-step diff, the
    return arch, trips, the per-world file.
- Reused as they are: `DropScene.portal` (the arch), `PlanApplier` (placing), `DemoWorldInfo` (is this the HQ world, where is its origin),
  `HqEmptyPlan`, `HqGenerator`, `Catalog`.

## Config and restarts

- No config keys. (The stage's offset, margins and the time budget are constants in `PieceStage` / `PieceScene`.)
- **RESTART once** (a new client init hook, server tick hook and commands). After that every piece, size, front, seed and `reload` applies live.

## Not verified yet

Written and reviewed by reading only; **not compiled or played**. First things to do on the PC: compile, then in the HQ world:
`/t3craft piece show hq_hub` (does the central building fit, how long the first build takes, is the lobby up before the teleport),
`/t3craft piece next` through the `house` group, `/t3craft piece front e`, `size 12x12x14`, `reload` after a hot swap, `clear`, and the arch.
Things most worth a look: the time to cut `hq_core` and `hq_ring_1` (millions of cells: memory of the dev client), signs on the lobby
(rotation), the ruler marks, `classic_*` (the plan's groups were read from the generator, not run), and `hq_barrio_*` (which lot the sample
project lands in does not matter, every `lot_*` / `module_*` group of that plan is taken).
