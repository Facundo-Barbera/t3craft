# Shared render service

One long-lived JVM renders for every agent. Dozens of agents see their designs (HQ plans, and UI
screens through the plug below) while the VPS (12 cores, 47 GB, shared) stays responsive.

> **Rule, no exceptions: agents never call gradle, java or `./gradlew hqRender`.** The only way to a
> render is `tools/render/submit.sh`. Compiling is done by the service (one gradle at a time, niced) and
> by the coordinator. `tools/render/start-daemon.sh` is the coordinator's.

## For agents: ask for a render

```bash
# preview (default): low resolution, fast. Use it while you iterate.
tools/render/submit.sh --layout hybrid-ringcampus --projects 4 --only citadel

# final: full resolution (HqRender's own defaults). Only when the design is settled.
tools/render/submit.sh --final --layout hybrid-ringcampus --projects 4,10,20

# with the layout packs of a folder (materials / params / .nbt rooms), exactly as in game
tools/render/submit.sh --layout hybrid-ringcampus --projects 4 --packs "$HOME/.config/t3craft/hq-layouts"
```

Everything after the options is passed to the renderer as is (for `--kind hq`, the options of
`HqRender`: `--layout`, `--style`, `--projects`, `--only` (view-name filter), `--scale`, `--ss`,
`--packs`, `--vanilla`, `--no-npcs`, plus whatever the piece-view branch adds, e.g. region or piece
selectors). `--out` is ignored: results always go to the request's outbox folder.

The script writes the request, waits (`sleep 5` on `status.json`, printing each state change) and, when
the render is done, prints the PNG paths and the report path. Open the PNGs with the image viewer of your
tool. Exit code: `0` done, `1` render or compile error (the javac errors are printed), `2` rejected,
`3` daemon not running (ask the coordinator), `4` you stopped waiting (`--timeout`, default 1 h; the
request keeps going).

Options of `submit.sh`: `--kind hq|ui`, `--preview|--final`, `--worktree DIR` (default: the worktree the
script is in), `--agent NAME` (default: `$T3CRAFT_AGENT`, else the worktree's folder name), `--timeout SEC`,
`--no-wait`.

### What gets rendered

Your **worktree's code**. If it has changes under `mod/src/` or `mod/build.gradle` compared with the
daemon's baseline (commits and/or uncommitted files, including new untracked ones), the service compiles it
(`processResources processClientResources compileJava compileClientJava compileHqrenderJava`, plus
`compileUirenderJava` when your `build.gradle` has that source set) and runs it. Compile errors come back in
`status.json` (`log`) and `compile.log`. If only packs (`.json` / `.nbt`, anything under a `hq-layouts`
folder) or arguments change, nothing is compiled.

Commit or not, the service sees your working tree. You do not need to push.

## Protocol (files)

Spool: `~/.cache/t3craft-render/` (`$T3CRAFT_RENDER_HOME` to move it).

**Request** — `inbox/<agent>-<n>.json`, written atomically (write a `.tmp` and rename; `submit.sh` does):

```json
{ "worktree": "/abs/path/of/your/worktree",
  "kind": "hq",
  "priority": "preview",
  "args": ["--layout", "hybrid-ringcampus", "--projects", "4,10", "--only", "citadel"] }
```

Besides raw `args`, these fields are accepted and turned into the same flags (in this order, `args` last):
`layout`, `style`, `projects` (string or array), `view`/`only`, `region`, `piece`, `scale`, `ss`, `packs`;
and the booleans `vanilla`, `no-npcs`. `kind` is `hq` (default) or `ui`; `priority` is `preview` (default) or
`final`. The id is the file name without `.json`; the agent is the id without the trailing `-<n>`.

**Answer** — `outbox/<id>/`:

| file | what |
| --- | --- |
| `status.json` | `state`: `queued` → `compiling` → `rendering` → `done` \| `error`; `message`; `log` (short tail: javac errors or the last output lines); `position` while queued; `cached`; `files` (absolute PNG paths); `report`; timestamps |
| `*.png` | the images (PNGs over 480 KB are re-saved with 256 colours, as `hqRender` does) |
| `report.txt` | header (worktree, HEAD, how the code was obtained, final arguments, timings) + the renderer's whole output |
| `run.log`, `compile.log` | raw logs |
| `request.json` | the request as received |

`status.json` is rewritten atomically; read it whole. `state` `error` also covers refusals; their
`message` starts with `rejected:`.

The daemon also keeps `daemon.json` (heartbeat every 2 s): `state`, `pid`, `queued` (queue length),
`running` (**array**, one object per job in progress: `id`, `agent`, `kind`, `priority`, `state`, `message`,
`startedAt`), `runningId` (string, the first running id or `""`; what `running` used to be), `workers`,
`threadsPerJob`, `served`, `load1`, `loadLimit`, `loadPaused`.

## Limits (to look after the machine)

- **N jobs at once, one compilation at a time.** `RENDER_WORKERS` workers (default 3) each run one job;
  compiles queue on a single lock (one shared gradle daemon, `--offline`; message `waiting for the compiler`).
  `RENDER_PARALLELISM` render threads in total (default 6) are split between the workers (6 / 3 → 2 per job,
  each job in a fork-join pool of its own). The daemon JVM and gradle run `nice -n 19` / `ionice -c3`; heap
  `-Xmx` = 2 GB + 1.5 GB per worker (`RENDER_XMX` to override).
- **Load gate at the start of each job.** While the 1-minute load average is above 8 (`RENDER_LOAD_MAX`) no
  new job starts if another one is running: the service drops to **one** active job, never zero, so the queue
  keeps moving (queued messages say `machine busy (load > 8): one job at a time`; `daemon.json` `loadPaused`).
  A job that has started is not paused.
- **One pending request per agent.** A second request while the first is queued or running is rejected with
  a clear message; wait for the first (`submit.sh` waits by default). Repeated requests answered from the
  cache do not count.
- **Order: round-robin between agents**, and within a turn the usual order: previews first, then finals; a
  final that has waited 10 minutes is not overtaken any more. The turn is the number of jobs the agent started
  in the last 30 min (fewest first), so an agent who just had a render goes behind everyone still waiting.
- **Previews are low resolution** (`--scale 0.8 --ss 1`) unless you pass `--scale`/`--ss` yourself; finals
  use the renderer's defaults. A job over 20 min (preview) / 60 min (final) is stopped.
- **Cache by hash**: baseline commit + kind + priority + arguments + the worktree's HEAD + its uncommitted
  diff + untracked files + the contents of the `--packs` folder. A repeated request returns the stored
  result immediately (`cached: true`).
- **Outbox older than 24 h is deleted** (and its cache entries).
- **The queue survives restarts** (see "Restarts" below).
- Request files with bad JSON, a missing or foreign worktree, or a `--packs` folder that does not exist are
  rejected before they queue.

## How the service works

Several jobs run at once (one per worker), so whatever is global is either per job or behind a lock:

| shared thing | how |
| --- | --- |
| gradle daemon | `compileLock` (fair): one compile at a time |
| a worktree's `mod/build` | per-worktree lock for the whole job: two requests on the same worktree run one after the other |
| `HqFixtures` (pinned) | `--vanilla` switches it: such a job takes `globalsLock` for writing, renders **alone** and restores the daemon's fixtures; every other render takes it for reading |
| `HqLayoutPacks` | per job (fresh class loader) unless it is pinned (the boot log says which); then `--packs` renders alone too and the packs are reset after |
| `System.out` / `err` | installed once (`StdRouter`): output of a job's threads goes to its `run.log`, the rest to `daemon.log` |
| `HqRender`, `PieceViews`, `BlockModels` statics | per job: they live in the job's class loader |
| `ModelCache` | concurrent maps of immutable data, shared on purpose |
| hash cache, `status.json` | written atomically with a unique temp name per write |

Each job's class loader and fork-join pool are dropped when it ends (the pool's threads are stopped before the
loader closes), so its classes and images can be collected.

1. **Boot once.** `dev.t3craft.tools.render.RenderDaemon` boots the vanilla registries and registers the
   mod's blocks (`Boot`, same as `hqRender`) a single time. Vanilla block models and textures are decoded once
   per JVM too (`ModelCache`, used by `BlockModels`).
2. **Code per job.** A fresh `RenderClassLoader` per job: the worktree's compiled classes and resources
   first for every `dev.t3craft.*` class, Minecraft and libraries from the daemon. The loader is dropped after
   the job, so nothing leaks between agents (`HqFixtures` is restored after `--vanilla`). With no code changes the job
   runs the daemon's baseline classes in the same kind of fresh loader (packs and other statics start clean).
3. **Kernel.** The registered blocks belong to the daemon's copy of `dev.t3craft.blocks.*` and of the few
   HQ classes they touch (computed at boot from the bytecode: `BuildPlan`, `Anchor`, `Placement`,
   `Workstation`, `HqFixtures`, `HqSite`, `AnchorNames`, `Box` and nested classes; the log lists them). They are
   shared by every job and **cannot be hot-reloaded**. Generators, styles, geometries, packs, lighting,
   views... are not in it. If your change alters a kernel class the job fails with `your change touches
   classes the daemon loads once at start...`: ask the coordinator to restart the daemon from a checkout
   that has your change (or to run the full gradle `hqRender` once). Rebasing on `main` fixes the other
   cause: a worktree older than the daemon's baseline looks like "changed kernel".

## Restarts: the queue is kept

The queue lives on disk, in the outbox. When the daemon starts it **recovers** what the previous one left
unfinished, before reading the inbox:

- Every `outbox/<id>/status.json` in `queued`, `compiling` or `rendering` is queued again **with the same
  id**, in its original arrival order (`queuedAt`; previews first, finals after 10 min as usual), with
  `message: "requeued after daemon restart"` (later `queued: position n of m; requeued after daemon restart`).
  A `submit.sh` waiting on that id simply sees the state move on; nothing to do. A job that was compiling or
  rendering starts again from the beginning (`restarts` in `status.json` counts the interruptions).
- No valid `request.json` in the folder (missing, bad JSON, bad fields) → `error`: *the render daemon restarted
  and could not resume this request: … Send it again (a new number in the file name)*. The same happens to a
  request older than 24 h, to one whose worktree no longer passes the checks (`rejected: …`), and to one
  interrupted mid-job by more than 2 restarts (it may be what brings the daemon down). A job whose render thread
  got stuck (the daemon restarts itself, exit 75) ends in `error` at once and is not resumed.
- **One pending request per agent** still holds: if an agent had two unfinished requests, the newest stays
  queued and the older ones end in `error` with `superseded by <id>`.
- Writing the same `inbox/<id>.json` again while that id is queued or running is ignored (the inbox copy is
  deleted; the request already in the queue answers). Once it is finished, the same id is rejected as
  "already handled" as before: use a new number.

**Orderly stop** (`start-daemon.sh --stop`, the `STOP` file): the daemon stops reading the inbox, the jobs in
progress finish, and the requests still waiting stay in the outbox as `queued` (*the render daemon stopped and
resumes this request when it starts again*); the next start takes them up. New inbox files wait in the inbox.
While it finishes, `daemon.json` says `stopping`. A kill (or a crash) loses nothing either: the running job is
`compiling`/`rendering` on disk and is started again.

## Plug for the UI renderer (`tools/ui-render`)

`kind: "ui"` calls, in the job's class loader,

```java
public static void render(String[] args) throws Exception   // dev.t3craft.tools.uirender.UiRender
```

with the request's `args` plus `--out <outbox folder>` and `--preview` or `--final`. It runs on the booted
JVM (vanilla registries and mod blocks are ready; do not boot again) and writes its PNGs under `--out`. If
the class is not in the worktree, the job fails with a clear message. If the UI renderer needs another
source set, name it `uirender` (the compile step adds `compileUirenderJava` when `mod/build.gradle` mentions
it) or change `JobRunner.entryOf` / `compile`; one place each.

## For the coordinator

```bash
tools/render/start-daemon.sh            # background; from a CLEAN checkout (its HEAD is the baseline)
tools/render/start-daemon.sh --status   # heartbeat
tools/render/start-daemon.sh --stop     # exits after the running jobs; the queue resumes on the next start
RENDER_WORKERS=4 RENDER_PARALLELISM=8 tools/render/start-daemon.sh   # settings are read at start
```

- Settings (environment when starting): `RENDER_WORKERS` (3), `RENDER_PARALLELISM` (6, total render threads),
  `RENDER_LOAD_MAX` (8), `RENDER_XMX` (2g + 1.5g per worker). The boot line in `daemon.log` shows the result.

- It runs `./gradlew --offline renderDaemonClasspath` (writes `mod/build/render-daemon/{classpath,java}.txt`,
  compiling the checkout first), then `exec`s the Java 25 binary niced. Log: `~/.cache/t3craft-render/daemon.log`.
- Restart it after merging into the baseline checkout (kernel changes, new baseline). It restarts itself by
  itself (exit code 75) if a render thread cannot be stopped: that job ends in `error`, the other running jobs
  are left `queued` and resume after the restart.
- Nothing in the game or the mod jar depends on it: it lives in the `hqrender` source set.
