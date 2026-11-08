# fix/hq-build-stutter — hitches when builders start and finish work

Facundo: "whenever the builders start and finish work, the game hitches badly."

The integrated server and the client share the CPU, and either one blocking for 30–200 ms in a single
tick or frame is a visible hitch. Placing blocks during a build already had a budget (`BuildBudget`, 8 ms
per tick, `TickGovernor`). The **start** and **end** of each job, though, did one-off work proportional
to the plan or to the whole HQ, all inside one tick. This branch measures every one of those steps,
moves the pure work to background threads, and spreads the work that touches the world or entities
over several ticks.

## What was found (start → end of one HQ part)

| # | Step | Where / thread | Size | Before | Now |
|---|------|----------------|------|--------|-----|
| 1 | `new BuildJob`: `CrewSchedule.of` (k-means + simulation of the whole plan) for plans ≤ 4096 cells | **client thread** (`BuildCrew.tick` → `submitter.get()` → `BuildManager.submit`) once the crew arrives | O(plan · k) | inline, tens of ms for a typical HQ update or small part | off-thread (`DeferredCursor`) above 256 cells (`BuildPlanner.SYNC_PLAN_LIMIT`) |
| 2 | `BlockCosts.of` + `MaterialMeter` (charged/survival jobs) | client thread, same call | O(plan) | inline | `Offload`; the job places nothing until the meter is there (1–2 ticks) |
| 3 | Site module → crew plan (`BuildPlanBridge.toBuildPlan`: parse every cell's state + `layered()` sort) | **server thread**, inside the tick that started the expansion | O(module box) | inline | `Offload`; the crew/paced builder starts on the server thread once ready (`CrewBridge.PendingHandle`) |
| 4 | HQ in-place writer: writes → `BuildPlan.of(...).layered()` | server thread | O(writes) | inline | `Offload`, same pattern |
| 5 | `HqTarget.compute` (`HqPlans.generate`: generator, `HqLighting` flood fill, packs) | server thread, `HqConstruction.tick` after each roster change / applied update | O(HQ) | inline | `Offload`; phase SETTLING until it arrives (the tick runs once a second, so ≤ 1 s later); same inputs → same target |
| 6 | `SiteService.claim` ×2 per HQ update (dry run, then real), each reading and decompressing **every snapshot and plan of the site** from disk | server thread, at the **end** of every part (after `onExpansionBuilt` → in-place sync of the whole HQ) | O(HQ) disk + O(HQ) world reads | 2 full reloads | snapshots/plans kept in `layerCache` between claims (snapshots never change under their key; plans only change in `claim` itself, on the cached object, then saved; dropped if the save fails) |
| 7 | Crew appears / leaves: 4 `BuilderAvatar` (`RemotePlayer`) created + added + particles in one frame; same on despawn | client thread | crew size | all at once | one per tick (`BuildCrew.SPAWNS_PER_TICK`) |
| 8 | `HqPresence.apply` when a wing / the base finishes: `HqWorldAnchors`, `HqStations`, `HqBindings.world` walk the whole plan | client thread | O(HQ) | inline | built off-thread (`HqPresence.Prepared`), installed on the client thread when ready; the gate (`allows`) follows the **installed** HQ, so nobody spawns before anchors exist and a new wing does not shut the gate |
| 9 | NPCs (avatar + panel) when the gate opens / a wing brings many | client thread, `NpcRegistry.reconcile` | roster | all in one tick | 3 per tick (`NpcRegistry.SPAWNS_PER_TICK`), the rest on the next ticks |
| 10 | Workstations (screen, prop, hitbox) | client thread, `WorkstationRenderer.tick` | roster | all in one tick | 3 per tick (`WorkstationRenderer.SPAWNS_PER_TICK`); a moved station keeps its old place until its turn |

Still synchronous, now measured:

- `SiteService.expand` (`step.site.expand`): scan of the module box, `Snapshot.capture`, and the **fsynced**
  snapshot/plan/journal saves. This is the site's crash-safety contract ("snapshot saved and verified
  before any change"), so it stays on the server thread. If it shows up red, the next step is a two-phase
  expand (capture on the server thread, save on a worker, start the build when the save is confirmed).
- The real `claim` still saves dirty plans with fsync (`step.site.claim`), for the same durability reason.
- `HqConstruction` diff (`step.hq.diff`): one pass over target + applied placements, building `Cell`
  records (cheap per cell; measured in case a huge HQ makes it matter).
- The `BuildListener.finished` callbacks (`step.build.finished`) and job start (`step.build.started`).
- `HqConstruction` creation at world load (`step.hq.load`): one `HqPlans.generate` of the saved layout.

Block placement flags (`ServerLevelSink.BUILD_FLAGS = UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`) were reviewed:
no neighbour updates are sent while building (no `UPDATE_NEIGHBORS`), and light is not suppressed (the
final lighting has to be right). The light engine queues its work per placement, which the 8 ms
placement budget already measures. No change.

Determinism and resume: the crew schedule, HQ target and converted plans are pure functions of their
inputs, so computing them on another thread or a tick later gives the same result and the same world.
Resume goes through the same code (matching cells are skipped), and cancelling a site build while its
plan is still being converted reports a cancelled build, as before.

The demo world's own flow (`client/demo`, `DemoBuildRun`) was not changed (it belongs to the
`feat/hq-void-world` thread). It benefits from 1, 2, 7, 8, 9 and 10, because it goes through the same builders.

## Reading the metrics

Every step above is timed with `dev.t3craft.debug.PerfStats`. It is always on and cheap: two `nanoTime`
calls and two map lookups per step.

- **latest.log**: any step on a game thread (server or client) that takes longer than 20 ms
  (`PerfStats.STALL_WARN_MS`) logs
  `WARN [perf] slow step hq.update 34.2 ms on Server thread (over 20 ms: the game hitches)`.
  `grep "slow step" logs/latest.log` lists every hitch with its cause.
- **`/t3dev perf steps`**: the longest single run of each step since the last `/t3dev perf reset`, plus
  the last 16 slow steps. Red means over 20 ms on a game thread. Steps ending in `_async` ran on a
  background worker (they never block the game; they show how long the result took to arrive).
- **`/t3dev perf`**: as before, per-second rates; the step timers appear as `step.<name> x ms/s`, and
  `step.stalls` counts the slow steps.

Steps (`step.<name>`):

| name | thread | what |
|------|--------|------|
| `build.job_new` | client (crew) / server | `new BuildJob` (should be ≪ 1 ms now) |
| `build.schedule_async` | worker | `CrewSchedule.of` |
| `build.meter_async` | worker | costs + material meter |
| `build.started`, `build.finished` | server | job listeners at start / end |
| `build.manager_tick` | server | a whole `BuildManager` tick, placing included (budget 8 ms) |
| `hq.construct_tick` | server | one HQ reconcile (once a second) |
| `hq.target_async` | worker | HQ target plan |
| `hq.diff`, `hq.update` | server | in-place diff; claims + cost + writer start |
| `hq.load` | server | HQ construction created (world load) |
| `site.expand`, `site.claim` | server | site expansion start; cell claims |
| `site.plan_async` | worker | site module / writes → crew plan |
| `crew.spawn`, `crew.despawn`, `crew.events` | client | builders appearing / leaving; build events (walk routes) |
| `hq.presence`, `hq.presence_async` | client / worker | HQ presence installed / prepared |
| `npc.reconcile`, `stations.tick` | client | NPC spawns; workstation tick |

How to check in game: `/t3dev perf reset`, let the builders start and finish a part (or `/t3craft hq
build`), then run `/t3dev perf steps`. Nothing should be red. If something is red, the log line names
the step and the thread.
