# feat/hq-void-world — the official HQ floats in the void

The official HQ world (demo mode, "T3Craft HQ") is now created as a **void world** (the classic
superflat "The Void" preset). The HQ stands on a floating skyblock-style island that grows with it.
Since feat/hq-spawn-start the player spawns in the **centre of the HQ, on a single block**, and the
build waits until they walk or press **[Start building]** (see below). Older worlds keep the small
starter island (tree, chest, bridge) they already have. The survival site mode (random terrain) does
not change.

Code: `mod/src/client/java/dev/t3craft/client/demo/` — `VoidIsland` (shapes, pure data),
`VoidGuard` (keeps players out of the void), plus small changes in `VanillaDemoWorld`,
`DemoWorldSpec`, `DemoConfig`, `DemoMarker`, `DemoHq`, `DemoHqSource` and `DemoClient`.

## World

- New HQ worlds: a superflat world with one air layer and biome `minecraft:the_void`, **no
  structures and no decoration** (the vanilla preset's little stone start platform is left out, so
  nothing gets in the HQ's way). Built in `VanillaDemoWorld.voidSettings` with
  `FlatLevelGeneratorSettings.withBiomeAndLayers` and `WorldDimensions.replaceOverworldGenerator`.
- **Existing HQ worlds are not converted.** Whether a world is void is read from the save each time
  it starts (`VanillaDemoWorld.isVoid`: the overworld is a flat generator whose layers are all air).
  An old flat HQ world stays flat, with exactly the same origin, hash and build as before.
- Config, additive (`config/t3craft.json`, section `demo`):

  ```json
  "demo": {
    "world": "void",      // what a NEW HQ world is: "void" (default) or "flat" (the old superflat)
    "origin": [0, 64, 8]  // optional; without it the origin is y 64 in a void world, -60 in a flat one
  }
  ```

  An invalid `world` falls back to `"void"` and logs the key. `origin`, when set, applies to both
  kinds of world, as before.
- In a void world the origin's ground level is `y = 63` (the island's grass), the HQ floor at 64,
  and the void can be seen below. In demo coordinates (relative to the origin) the ground stays at
  `y = -1`, exactly where the superflat grass was, so no HQ layout had to change.

## The HQ island

`VoidIsland.island`, built by `DemoHq.withIsland` as the **first part** of the build:

- Covers the HQ's footprint (`DemoHq.demoArea`: the plan's footprint box baked to demo coordinates)
  plus a margin of 3–4 blocks, with a wobbly edge in 2×2 blobs.
- Grass on top (`y = -1`), two blocks of dirt, then stone with tuff speckles; it tapers downward:
  2 blocks deep at the edge, up to 10 inward. Columns where the plan already has blocks below ground
  (the sunken amphitheatre of ring and ring campus, down to `y = -3`) are left alone and the island
  goes 2 blocks below them. Every wobble comes from a fixed seed (`VoidIsland.SEED`): the same HQ
  always gets the same island.
- **Rim**: a hedge of persistent azalea leaves on the island's outer edge (`y = 0`), with a
  pearlescent froglight every few cells as a garden light. It is one block high: NPC routes never
  leave the island (the NPC A* only walks cells with a floor, and the hedge also stops a body that
  drifts), while the player can still hop over it. It is left open where the bridge from the starter
  island arrives.
- **It grows with the HQ**: the marker state records the island's core area (`state.island`,
  demo coordinates). On the next build the area is the union of the new footprint and the old one,
  so the island never shrinks (released modules leave grass, not holes). Whenever the area grows
  (new modules, wings, districts, rings) the island part goes in front of the diff, and it clears the
  old rim that is now inland. A full build (first build, `/t3craft demo rebuild`, a non-diffable
  rebuild) always includes it; already placed blocks are skipped.
- Same build path as everything else: it is a builders' part (layered, bottom first: the bottom
  layer of a part counts as supported and every block above is supported by its neighbours), and
  `PartSchedule` keeps every HQ part that touches it after it, so the builders lay the ground first
  and nothing ever stands over the void.
- What the layouts assume about outside ground: everything walkable is inside the plan's footprint
  (gardens and terraces in classic/cherry, the village hill — which the plan builds itself from
  `y = 0` up —, the campus grounds, the town squares, the terraces' stairways), and the island covers
  all of the footprint plus its margin, so there is ground under every walkable cell. Plan cells are
  never overwritten by the island. Floating plan parts (terraces' mill on its own sky island,
  skybridges) are part of the plan, as before.
- `HqLighting` and NPC walkability work on the plan, which is unchanged; the island only adds
  ground under it (and froglights on the rim). Mob spawning is off in the HQ world anyway.
- The hash of a void HQ carries a `-void` suffix, so a void HQ is never taken for a flat one.

## Spawn in the centre of the HQ (feat/hq-spawn-start)

Code: `HqCentre` (where the centre is, which block goes under it), `DemoClient.spawnAtCentre`,
`VanillaDemoWorld.placeCentreBlock` / `setWorldSpawn`.

- **The centre** is the hub of the HQ: the `hub` anchor (centre of the hub floor) of the layout's
  empty plan, baked to world coordinates exactly like the NPC anchors (`DemoHq.worldSite`); if a
  layout has no hub, or the plan keeps something solid in the feet or head cell, its `spawn` anchor.
  The layout is the one built in that world (`HqLayoutState` in the marker), else the configured one
  (`hqLayout`). Applies to void **and** flat HQ worlds.
- **One block under the feet**, placed by the integrated server on `SERVER_STARTED` (before the
  player joins) only when that cell is empty: the plan's own block for that floor cell (rotated to the
  world) when it is a plain full block without block entity data, so the builders find it already in
  place and skip it; otherwise a neutral smooth stone block, which the plan's floor replaces when the
  builders get there. If the spawn is still the centre and that block went missing before the floor
  was built, it is put back on the next start.
- **World spawn** on that cell (`ServerLevel.setRespawnData`, the 26.3 equivalent of
  `setDefaultSpawnPos`), facing into the HQ, `respawn_radius` 0: first join, respawn and reconnects go
  there.
- **Existing HQ worlds:** nobody is teleported. The world spawn moves to the centre only if it still
  is where the mod put it before (the starter island's spawn, or the old viewpoint in front of the
  site); a spawn set by hand with `/setworldspawn` is left alone. The decision is taken once per world
  and recorded in the marker (`t3craft-demo.json`, `"centre": [x, y, z]`).
- **Starter island: dropped for new worlds.** It existed to give the player ground away from the
  build site; now the player stands where the build starts and the island grows from under their
  feet (the radial build is centred on the player), so a second island off to the side, with a bridge
  into a site that is not there yet, only got in the way. Old worlds that have one keep it (marker
  `starter`), it still gets swallowed by a growing HQ island as before, and it stays the void guard's
  fallback. In new worlds the void guard falls back to the centre block.
- The first-build teleport (to the starter island / viewpoint) now only happens with
  `hq.startBuild: "immediate"`; with the default the player already stands at the centre, and pulling
  them back after they walked would be wrong.

## Build start: wait for the player (feat/hq-spawn-start)

Code: `HqStartGate` (state + config), `DemoClient.armGate` / `pollGate` / `confirmStart`.

- The build due when the player enters the HQ world (first build, a build left half done, or a diff
  that piled up while they were away) no longer starts by itself. First the world has to finish
  loading (the existing settle ticks and the roster gate), then the mod waits for **either** signal:
  - **(a) confirm**: the chat message has a clickable **[Start building]** button
    (`ClickEvent.RunCommand` → client command `/t3craft build start`), or the key **Start the HQ
    build** (Options › Controls › T3Craft, default **G**, rebindable);
  - **(b) walk**: more than 3 blocks (horizontal) from where the player stood when the wait began,
    or a movement key (W/A/S/D as bound) held for 10 ticks with no screen open.
- While waiting a discreet hint is shown on the action bar and in chat: "The build will start when
  you walk or press [Start]" / "La construcción comenzará cuando camines o pulses [Comenzar]".
  `/t3craft demo status` says the build is waiting.
- Leaving before the build starts (or in the middle of it) keeps it pending: the marker is only
  written when the build completes, and the wait is armed again on the next visit.
- Later builds (a thread took or released a station, a layout pack changed) start on their own as
  before, but only while the player is in the HQ world (the reconcile runs on the client tick with
  the HQ world loaded) and never while the entry build is still waiting for the player.
  `/t3craft demo rebuild` is an explicit request and starts at once.
- Config, additive (`config/t3craft.json`, section `hq`):

  ```json
  "hq": {
    "startBuild": "on_walk_or_confirm"   // default; "immediate" = start as soon as the world is loaded (old behaviour)
  }
  ```

  An invalid value falls back to the default and logs the key once. Read every time a build is due,
  so a change applies on the next entry without a restart.

## Starter island (worlds created before feat/hq-spawn-start)

- Placed by the **integrated server** as soon as a new void HQ world starts (`SERVER_STARTED`, before
  the player joins) — not by the builders, so there is ground under the player from the first tick.
  Recorded in the world marker (`t3craft-demo.json`, `"starter": [x, y, z]`, its centre grass cell).
- Radius 3, grass / dirt / stone tapering below, an oak tree (persistent leaves), a decorative empty
  chest facing the HQ, a lantern, and a railed spruce bridge (3 wide, fences on both sides) over a
  4-block gap to the HQ island's edge, in line with the HQ's door.
- Where: north of the HQ's door (the HQ grows to `+z`), in front of the **empty** HQ of the
  configured layout (`VoidIsland.starterFor`), beyond the island's margin and the bridge: outside
  anything the builders place. If a layout that grows in every direction (ring, village…) later
  reaches it, the HQ island swallows it: the island part clears its tree, chest and rails and its
  ground becomes part of the island.
- World spawn on it (`respawn_radius` 0, so never a random spot over the void). On the first visit
  the player is put there (instead of the old viewpoint in front of the site) and the crew sets off
  from there.

## Never falling into the void (`VoidGuard`)

Decision: **always on in the official HQ world when it is a void world**, not only while it is being
built — the island has edges for as long as the world exists.

- Server side (integrated server tick, `ServerTickEvents.END_SERVER_TICK`), so it works in
  singleplayer. It only exists while the integrated server runs the HQ world and that world is void
  (`DemoClient` starts it on `SERVER_STARTED`, stops it on `SERVER_STOPPING` and for any other world):
  outside the HQ world the mod does nothing, as the fix/hq-gate-npcs rule (`HqPresence`) requires.
- Movement is never blocked: no walls, no invisible barriers. Every 10 ticks the guard remembers the
  last spot each player stood safely on (on the ground, solid block below, room for the body).
- When a player in the overworld drops below `originY − 20` (under the deepest island, well above
  vanilla's void damage at `minY − 64`), they are put back on that last safe spot — else on the
  starter island, else on the HQ spawn (and if none of those has ground, a single grass block is
  placed under the starter spawn) — with speed and fall distance reset, no damage, and a note on the
  action bar (`t3craft.demo.void.rescued`). One rescue per player per second at most.
- **Flight is never touched**: players flying (creative flight), gliding (elytra), riding something,
  or in spectator mode are ignored, so flying below the island is fine.

## Reset HQ

`HqReset` / the HQ menu (Reset HQ, Rebuild with <layout>) delete the HQ world and create it again
with `DemoConfig.newWorldSpec()`: a void world (unless `demo.world` is `"flat"`), so a reset flat HQ
becomes a void one. The new world gets a fresh starter island (in front of the chosen layout) and the
builders build the island with the HQ. The chosen layout (`hqLayout`) and the layout packs
(`config/t3craft/hq-layouts`) are applied exactly as before: the island follows whatever footprint
the plan has.

## How to try it

1. Restart the game (new command `/t3craft build start`, new key, record shapes changed).
2. HQ menu → Reset HQ (or delete `saves/t3craft-hq`), then Open HQ: you appear in the centre of
   the (not yet built) HQ, on one block over the void; chat offers **[Start building]**. Walk a few
   steps, click the button or press G: the builders lay the island from under your feet and then
   the HQ.
3. Leave before starting, come back: you are on the same block and the build waits again. With
   `"hq": {"startBuild": "immediate"}` it starts as soon as the world has loaded.
4. `/t3craft demo status` shows `(void)` and the island area in the hq line.
5. Walk off the edge (not flying): a moment later you are back on solid ground, no damage. Fly
   below the island in creative: nothing happens.
6. Grow the HQ (new threads): the island grows first, then the new modules (on their own, no wait).
7. An existing HQ world keeps its build; its world spawn moves to the hub only if it was still the
   old one (starter island / viewpoint); `"world": "flat"` keeps new worlds flat.
