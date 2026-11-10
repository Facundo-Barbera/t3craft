# In-game preview drop (feat/ingame-preview-drop)

> For the game's own buildings and pieces (the HQ's central building, catalog variants, the kit, pack rooms) use the piece catalog
> instead: `docs/features/piece-catalog-preview.md`. This showroom (120 wide, sides up to 256) stays for schematics from outside the game.

Look at schematics inside the T3Craft HQ world **without restarting the game and without `hqRender`**: drop
files in a folder, walk through a portal, and a showroom ("recinto") with one pedestal and one sign per file
follows the folder live.

## The drop folder

```
<game dir>/config/t3craft/preview-drop/
  README.txt            written the first time the game starts (never overwritten)
  castle.schem          a Sponge schematic v2 / v3 (WorldEdit, FAWE, Axiom, most editors)
  tower.nbt             a vanilla structure block file (Save mode)
  props/well.nbt        one level of sub-folders is read too (the name shown is "props/well")
  _old/anything.nbt     anything starting with _ is ignored
```

- `<game dir>` is the Minecraft instance folder; for the dev client it is `mod/run/`.
- Print the exact path in game with `/t3craft drop folder` (clickable: opens it in the file manager).
- Formats: `.nbt` (read by `RoomTemplate`, the same reader the layout packs use) and `.schem` (`SpongeSchem`).
  Air is dropped; entities are ignored; block entities (signs, chests, ...) are kept.
- Limits: sides up to 256, 1.5 million solid blocks, 64 MB per file. A file over a limit is reported, not loaded.
- A new, replaced or deleted file is noticed within about a second (names, sizes and modification times; a
  file modified in the last 0.7 s is left for the next look, so a copy in progress is never read half-written).
- A file that cannot be read (unknown block, truncated, not a schematic) keeps **its last good version** and
  the error shows as a toast plus a red chat line, `file: what`. Other files are not affected.

## Getting there

1. Open the HQ world (title screen → T3Craft HQ). About 5 seconds after joining, chat shows
   `Preview drop: N schematic(s) ...` with the buttons **[Showroom]** and **[Open folder]**.
2. **The portal is a fixed piece of the main HQ** (fix/hq-load-build-portal, Facundo 2026-11-09: the player no
   longer places it). A crying obsidian arch (5 wide, 6 tall, 2 thick, opening 3×4) stands at the **north end of
   the inner street, at the foot of the north ramp**, on the street's axis, opening towards the hub: the mirror
   image of the spawn anchor, which is the south end of that street ("where NPCs appear"). In plan coordinates
   (hub = origin, entrance south = +z) its centre is `(0, 0, -(rampR + innerStreetR) / 2)`, which is `(0, 0, -38)`
   at scale 1 and `(0, 0, -75)` at the default scale 2; the code takes it from the plan's `spawn` anchor
   (`DemoWorldInfo.portalSpot`), so it follows the layout's scale. The street is 5 wide at scale 1 and 10 at
   scale 2, so the 5-wide arch always fits. Walking through it takes you to the showroom; the showroom's arch
   brings you back to the hub side of it (3 blocks in front, looking at the hub).
   - Why there: it is always part of the core (present with zero projects), at ground level (no podium, no
     terrace), clear of the NPC spawn, desks, rings and nodes, and it is the first thing you see coming down
     the north ramp. A layout without its own ground plan (cherry) gets it 7 blocks in front of the spawn.
   - It is **not in the HQ plan**: the plan, its fingerprints and the demo marker are unchanged. The drop module
     keeps it standing instead: every 2 s, only once all the chunks it needs are loaded and the street under its
     posts is built, it lays any cell of the arch that differs. So a build or rebuild that overwrote a cell of it
     (street furniture is plan content) is undone within two seconds, and it never floats over a half-built HQ.
   - Until the street under it is built there is no arch and the portal sends nobody; `/t3craft drop go` works
     at any time.
   - Worlds from the earlier version: a portal a player had placed (`t3craft-drop.json`, key `portal`) is removed
     once when its chunks are loaded, and forgotten.
   - The README the game wrote into the drop folder is never overwritten: an old one still talks about
     `/t3craft drop portal`. Delete it and the game writes the new text on the next start.
3. **No portal needed:** `/t3craft drop go` does the same from anywhere in the HQ world, `/t3craft drop back`
   returns (to the portal's side once it stands, else to where the trip started, else to the world spawn).

## The showroom

- Where: `OFFSET_X = -2000` blocks west of the HQ origin, at the island's ground level (`origin.y - 1`). Far
  from any HQ footprint (ring campus, 3 rings, scale 3 is well under 1000 blocks), same height as the island so
  the void guard (`VoidGuard`, only looks at y) is happy. Chunks load when you arrive.
- What: a polished-andesite floor with a stone-brick border and a low stone-brick wall with lanterns, one
  pedestal per file (rows left to right, wrapping at 120 blocks), the schematic on its pedestal un-rotated
  (its `(0,0,0)` corner at the pedestal's north-west top), and an oak sign south of each pedestal: name,
  size `WxHxD`, format and block count. At the south end, a return arch with a gold plate.
- A schematic is shown as it was saved: what it built facing south is what you see standing at its sign.
- **Hot reload.** The folder is read on a worker thread (a big file never stalls the game); the showroom is
  rebuilt as a **diff** (only changed blocks, a few thousand per server tick). It is only touched while you
  are in or near it (24 blocks) or just asked to go there: while you are in the HQ, a changed file marks it
  stale and the next visit builds it. So: stay in the showroom, drop a file, and watch it appear; or drop it
  and walk through the arch.
- Anything you build by hand in the showroom is yours; blocks the scene did not place are not touched, except
  that a removed exhibit's blocks (and only those) are cleared. `/t3craft drop rebuild` checks every block of the
  scene against the world again.
- Mob spawning is already off in the HQ world.

## Commands

| command | what |
|---|---|
| `/t3craft drop` | folder, the schematics read (name, size, format, blocks), broken files, showroom and portal state |
| `/t3craft drop go` / `back` | to the showroom / back |
| `/t3craft drop reload` | read every file again |
| `/t3craft drop rebuild` | forget what was built and compare the whole showroom with the world |
| `/t3craft drop folder` | print the folder (clickable) |

`go`, `back` and `rebuild` only work in the HQ world (integrated server running the configured demo
folder, void or flat); elsewhere they say so and do nothing. The folder reading runs everywhere, but writes no
block outside the HQ world.

## Config and restarts

- No config keys. (The offset and limits are constants in `DropShowroom` / `Schematic`.)
- **RESTART once** (a new client init hook, server tick hook and commands). After that every change in the
  drop folder applies live.

## Code map

- `dev.t3craft.drop.Schematic`: one file's blocks (read dispatch, limits).
- `dev.t3craft.drop.SpongeSchem`: `.schem` v2 / v3 reader (palette, var-int block data, block entities).
- `dev.t3craft.drop.DropFolder`: the folder, README, incremental scan, last-good-version rule.
- `dev.t3craft.drop.DropScene`: showroom geometry (`Scene`), the walk-through arch (`Portal`), `diff`.
- `dev.t3craft.client.drop.DropShowroom`: the world side (server thread): diff queue, arches, teleports, the
  per-world file, upkeep of the fixed HQ portal (`maintainPortal`).
- `DemoWorldInfo.portalSpot`: where the fixed portal stands (from the plan's spawn anchor, via `HqEmptyPlan`).
- `dev.t3craft.client.drop.DropClient`: client init, worker, toasts/chat, commands.
- `dev.t3craft.client.demo.DemoWorldInfo`: read-only "is this the HQ world, and where is its origin".

## Not verified yet

Written and reviewed by reading only: the rule for this task was no processes (no Gradle, Minecraft or JVM),
so it has **not been compiled or played**. First thing to do on the PC: compile, then in the HQ world run
look for the fixed portal at the north end of the inner street (fix/hq-load-build-portal), drop a `.nbt` and a `.schem`, walk through, edit/replace/delete a file while standing in
the showroom, and put a broken file in to see the error path. Things most worth a look in game: that the arch
passes the walk-through box test at sprint speed, sign text on the pedestals, and `.schem` files from the
editor Facundo uses (v2 vs v3, block entity data of old signs is not translated).
