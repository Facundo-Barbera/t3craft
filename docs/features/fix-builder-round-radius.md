# fix/builder-round-radius: a round work radius around the player, builders that follow and grow

After trying `fix/hq-load-build-portal` (`822cc454`) the builders still did not finish the HQ. That fix made a
block in an unloaded chunk wait instead of being dropped, but it did not look at what *waiting* does to the rest of
the radial build. This change reads the whole mechanism again and gives it the shape Facundo asked for. **Not
compiled or played** (no processes allowed on this machine): the coordinator compiles, Facundo plays.

## What was wrong (read from `RadialCursor`, `BuildJob`, `ServerLevelSink`)

1. **One unloaded chunk stalled everything behind it.** `BLOCKED` made the ground stream, the fill stream and the
   corridor `break outer` without moving their pointer: every tick they stopped at the same column. The columns
   behind it, even loaded ones (the loaded area is a square of chunks, the order is a circle), never ran.
2. **A builder that took such a column kept it for ever.** `tickBuilders` did `continue` on `BLOCKED`, so the
   builder held its span, placed nothing and never looked at another column.
3. **The front ring stuck on it too.** `visRing` is "the ring of the nearest column with work that is not stalled";
   a column waiting for its chunk is not stalled, so `visRing` stayed there and the builders (`visRing +
   builderRingsAhead`) and the ground (`+ foundationRingsAhead`) never went past it.
4. **The ring was a poor "radius".** Rings of 5 blocks, plus 1 ring ahead for the builders (so 2 rings working at
   once), recentred 2.5 blocks *ahead* of the player: not a circle around the player, and the reset on moving was
   only "scan again from ring 0", with no notion of staying near the player until it is built.
5. **Always 8 builders**, however little (or much) there was to do near the player.
6. **No chunk tickets exist** anywhere in the mod. Chunks are loaded only by the player's view distance. This change
   does not add any (see "Not done").

## How it works now

Everything below is in `RadialCursor`; the settings are in `build` (`BuildTuning`), all additive.

### The radius
The column order is sorted by distance, so "the columns within R blocks" is a prefix of it (`distAt`, quarter
blocks). The work radius `reachQ` holds the streams and the builders to that prefix:

| | |
|---|---|
| **Minimum** | `build.radiusMin` = **5 blocks**. A build starts with it and falls back to it. |
| **Stay** | While any column inside the radius still has ground or above work (not done, not stalled, not waiting for a chunk), the radius does not grow: the builders stay there. |
| **Grow** | When that disc is complete the radius jumps to the nearest unfinished column plus `build.radiusStep` (**4 blocks**), and holds again until that disc is complete. Repeated, the build expands a few blocks at a time until nothing is left. An empty gap is crossed in one step, not block by block. |
| **Move** | The off-thread re-order that follows a move of 4 blocks (or 1 block after 3 s) is now centred **exactly on the player** (the old half-ring lead is gone) and, when installed, the radius is reset to the minimum around the new position. Builders holding a span farther than the new radius let it go and come back to the player. |
| **End** | Nothing left that can be worked: no limit, so the hidden fill and whatever woke up behind finish. |

**Floor first.** Inside a column the ground always goes before what is over it (a column's head is its ground until
the ground stream has placed it), and the ground stream may run `build.foundationRingsAhead` (1) ring past the
radius, so the floor of the next annulus is usually down before the builders get there. Builders take the player's
vertical band first, as before (`WalkLevels.inBand`), but the radius waits for the whole disc, high roofs included.
The hidden fill trails the radius by `build.fillRingsBehind` (2) rings, as it did the front ring.

`build.builderRingsAhead` now defaults to **0** (builders work inside the radius, not one ring past it); the key and
its meaning (rings past the radius) are unchanged for anyone who sets it.

### Chunks that are not loaded (the part the previous fix left open)
A placement that answers `BLOCKED` now **parks its column** for 20 ticks (`park`), in the corridor, the ground
stream, the fill stream and the builders alike:
- the stream or the builder goes on with the next column instead of stopping or waiting;
- a parked column is skipped by the radius scan, so it never holds the radius back;
- every 10 ticks the parked columns that waited long enough are woken (`unpark` → `touched`) and tried again; nothing
  was applied, so nothing is lost; the cost of a retry is one `loaded` check;
- a status line shows `waiting-chunks N` while there are some;
- the "force a stalled piece" scan runs only every 10 ticks while columns are parked.

### Number of builders
The crew (`fronts`) is the capacity; how many of them take new spans (`active`) is worked out every 10 ticks from
the builder work left within 24 blocks of the player (entries that are not ground, fill or air:
`buildLeft`, kept per column):

`active = clamp(radialMinBuilders + pending / entriesPerBuilder, radialMinBuilders, crew)`

With the defaults: **3 builders** at least, **one more per 1200 entries** of builder work near the player. A hut
stays at 3; the centre of the HQ (walls, towers and roofs by the thousand) reaches the whole crew. It grows at once
and shrinks one builder per look, and a builder that is no longer "in use" still finishes the span it holds. The
job's rate (`rate()`) counts only the builders in use.

The crew itself is bigger for a big radial plan: `build.radialCrewMax` (**12**) instead of 8 when the plan has
60 000 entries or more (`BuildTuning.radialCrewFor` / `radialFrontsFor`, used by `BuildersClient`; sites, charged
jobs and small plans are untouched). More avatars appear one per tick at the start; the ones not in use stand idle.

## Config (additive, `config/t3craft.json`, section `build`)
| key | default | range | read |
|---|---|---|---|
| `radiusMin` | 5 | 1–64 | every tick |
| `radiusStep` | 4 | 1–32 | every tick |
| `radialMinBuilders` | 3 | 1–16 | every tick |
| `entriesPerBuilder` | 1200 | 50–1 000 000 | every tick |
| `radialCrewMax` | 12 | 1–16 | when a job is submitted |
| `builderRingsAhead` | **0** (was 1) | 0–16 | every tick |

`/t3craft debug build-tuning reload` re-reads them. The status line (`/t3craft demo status`) now reads
`radial cols a/b centre x,z radius R builders k/n [waiting-chunks m]`.

## Not done / open
- **No chunk tickets.** The API (`ServerChunkCache` tickets in 26.3) could not be checked without compiling. With the
  radius around the player the work stays inside the chunks the player has loaded, as long as the radius does not
  outgrow the view distance; if it does, the columns beyond it are parked until the player walks there. If Facundo
  wants the build to go on while he is away, or with a very low view distance, add a ticket for the chunks of the
  radius and release it when the job ends: a separate issue.
- **Not played.** The numbers (5 / 4 / 3 / 1200 / 12) are first guesses: the radius may feel slow with few builders
  at 5 blocks (raise `radiusStep`, or `radiusMin`), or the crew may be too small or too large for the HQ's centre
  (`entriesPerBuilder`, `radialMinBuilders`). The 24-block counting radius is a constant (`CROWD_Q`).
- The strict "everything in the disc before growing" can hold the radius on a very tall tower next to the player;
  that is the rule as asked. If it feels too slow, lower `entriesPerBuilder` (more builders sooner) or, in a
  follow-up, let the radius grow once the player's vertical band of the disc is done.
- The older paths (`parts` order, charged jobs, plans under 256 entries) are unchanged.

## Config and restarts
Additive keys only. **RESTART** (the `BuildTuning` record gained components, `RadialCursor` changed shape).
