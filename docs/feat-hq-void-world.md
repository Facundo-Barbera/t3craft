# feat/hq-void-world — the official HQ floats in the void

The official HQ world (demo mode, "T3Craft HQ") is now created as a **void world** (the classic
superflat "The Void" preset). The HQ stands on a floating skyblock-style island that grows with it,
and the player starts on a small starter island with a tree and a chest. The survival site mode
(random terrain) does not change.

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

## Starter island

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

1. Restart the game (new server tick hook, record shapes changed).
2. HQ menu → Reset HQ (or delete `saves/t3craft-hq`), then Open HQ: you appear on the starter
   island (tree, chest, bridge) over the void; the builders lay the island and then the HQ.
3. `/t3craft demo status` shows `(void)` and the island area in the hq line.
4. Walk off the edge (not flying): a moment later you are back on solid ground, no damage. Fly
   below the island in creative: nothing happens.
5. Grow the HQ (new threads): the island grows first, then the new modules.
6. An existing flat HQ world opens exactly as before; `"world": "flat"` keeps new worlds flat.
