# fix/hq-load-build-portal: the 16% freeze, the HQ that never finished, a portal that is part of the map

Facundo, after trying `main` at `00ee7b1c` (2026-11-09). Found and fixed by reading the code only: **not compiled
or played** (no processes allowed); the coordinator compiles and Facundo plays. Each cause below says how sure it is.

## 1. Entering the HQ stops at 16% for ~30 s

**Cause (read from the code, very likely):** `DemoClient`'s `SERVER_STARTED` hook runs on the integrated server's
thread while the world loads, and it generated the **whole empty-HQ plan** (`HqPlans.generate(HqLayout.EMPTY)`:
the citadel's ground, fixtures, lighting, plan connect, layout packs) **three or four times in a row**:

| call | from |
|---|---|
| `HqCentre.of` | the spawn at the centre of the HQ |
| `DemoPlacement.viewpoint(spec, minY)` | `oldSpawns(...)` |
| `DemoPlacement.viewpoint(spec, minY)` | `voidStart(...)` (void worlds) |

The comment in `DemoClient.start` already measures one citadel plan at **4.3 s** (fix/demo-reconcile-offthread);
the old spawn code needed one, the centre-of-HQ spawn (feat/hq-spawn-start) added more, and the citadel kept
growing. While they run nothing else on the server thread moves, so the client's loading screen (which counts the
chunks the server sends) sits at 16% and the game looks hung. The preview drop is **not** the cause: its
`SERVER_STARTED` hook only reads a small JSON file, its tick does nothing until somebody is near the showroom
(2000 blocks away), and the folder is scanned on a worker thread.

**Fix:**
- New `dev.t3craft.hq.HqEmptyPlan`: the empty plan is computed **once**, on a background worker (`Offload`), keyed
  by style, fixtures, kit and layout-pack generation; the last 3 are kept. `HqCentre.of`,
  `DemoPlacement.viewpoint(spec, minY)` and `DemoHqSource.prepareLater` use it.
- It is **started ahead of time**: when the client has started (`CLIENT_STARTED`) and when the player opens or
  creates the HQ world (`DemoClient.open`), so by `SERVER_STARTED` it is normally done and the hook costs
  microseconds. If it is not done, `of()` waits for the one running computation instead of starting another.

**Not verified:** how long the plan really takes on Facundo's PC, and that nothing else on the server thread is
slow at join. If the freeze remains: `/t3dev perf steps` (`step.hq.load_async.ns` is the new async timer; the
`SERVER_STARTED` time is in the log between "Preparing spawn" and the first tick), or the HitchWatch log.

## 2. The HQ is never finished; builders stop even next to unfinished work

**Cause (read from the code, certain that it happens; likely the cause of what Facundo saw):**
`ServerLevelSink.place` returned `FAILED` for a block in a chunk that is **not loaded**
(`level.isLoaded(pos)` false). `FAILED` is final for every cursor: the entry counts as applied
(`RadialCursor.applied` → `colsDone`), is never retried, and it even wakes its neighbours as if it had been placed.
The radial build (default `build.order: "radial"`) works ring by ring from the player, and chunks exist only
within the server's view distance of the player. So whenever the build reached columns farther than the loaded
chunks (the player at one edge of a 190-block HQ, a low render distance, a trip to the showroom 2000 blocks away
while the build runs), those columns were **dropped**. The job still ends `COMPLETED`, `DemoBuildRun`
writes the "HQ built" marker, and nothing ever looks at the holes again: no builder works, because as far as the
build is concerned there is no work left, even if you stand in the middle of the hole. The player-centred ordering
was not wrong, it just made the far side the last and the most likely to be unloaded.

**Fix:**
- `BlockSink.loaded(pos)` (default true; `ServerLevelSink` = `level.isLoaded`).
- In `BuildJob`, a free **radial** job (and its start-up primer) answers `BLOCKED` for an unloaded cell **without
  placing**. Every radial stream already treats `BLOCKED` as "keep the entry, look again next tick", so the column
  waits (nothing is lost, no hang: `idle()`/`forceOne` only fire with all columns stalled, and a waiting column is
  not stalled). When the player walks there and the chunks load, the build continues by itself, nearest first.
- `FAILED` is left for real failures (outside build height, `setBlock` refused).

**Not done / open:**
- Nothing asks the server to *keep* chunks loaded around the build (a forced chunk or ticket), so with the player
  far from an unfinished part that part waits for them; the build resumes when they come back. A ticket API was not
  used because it could not be checked without compiling. If Facundo wants the build to go on while he is away:
  force-load the chunks of the active ring and release them when the job ends.
- The older paths (`parts` order, survival/charged jobs, jobs under 256 entries) still drop blocks in unloaded
  chunks. The demo's default and the creative HQ use the radial cursor.
- A world already marked "built" with holes is not repaired by this fix: `/t3craft demo rebuild` builds again and
  skips what is already right (the holes get filled).
- Not reproduced: I could not run the game. If builders still idle near unfinished work with loaded chunks, the
  next suspects are in `RadialCursor`: `pick`'s `PICK_SCAN` window and the player-level band
  (`WalkLevels.inBand`).

## 3. The portal is part of the map

See `docs/features/ingame-preview-drop.md` ("Getting there"). Summary:
- `/t3craft drop portal` and the chat button **[Place portal here]** are gone.
- The arch is fixed at the **north end of the inner street, at the foot of the north ramp** (plan coordinates
  `(0, 0, -(rampR + innerStreetR) / 2)`, i.e. `(0, 0, -38)` at scale 1, `(0, 0, -75)` at scale 2), opening
  towards the hub. It is the mirror image of the spawn anchor and is taken from it (`DemoWorldInfo.portalSpot`).
- It is not in the HQ plan (no hash, fingerprint or marker change); `DropShowroom.maintainPortal` keeps it
  standing, only with its chunks loaded and the street under it built.
- A portal placed by hand in an earlier version is removed once.
- The drop folder and the showroom stay: `/t3craft drop go`, `back`, `reload`, `rebuild`, `folder`.

## Config and restarts
No config keys. **RESTART** (new class `HqEmptyPlan`, a removed command, a new client lifecycle hook).
