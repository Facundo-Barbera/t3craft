# AGENTS.md: rules for every agent working on t3craft

These rules come from Facundo and override any other instruction, brief or habit.

## Where things run (Facundo, 2026-11-09 evening; replaces "Execution on the VPS")

| machine | what runs there |
|---|---|
| **Mac mini** | The **coordinator**, the only Opus thread at xhigh (checkout `/path/to/t3craft`). It plans, briefs, reviews, merges and pushes. It runs nothing heavy itself: it drives the PC over SSH. **Coordination only:** the Mac runs no working subagents or child threads (Facundo and the coordinator only). |
| **VPS** (my-vps, T3 environment `vps-env`) | **Child threads only**, as many *small, short* threads: one task each, commit, push, end. At most **8** at once (T3 slows down with more), all on the latest **Sonnet** with effort medium or high, never xhigh. **No heavy process of any kind** (no gradle, java, renders, Minecraft), and no long-lived coordinator thread with a huge history. |
| **PC** (my-pc, WSL, `~/projects/t3craft`) | **All Gradle builds and all renders,** one heavy process at a time, through `tools/heavy.sh` and the render daemon (1 worker). Only the coordinator compiles there; renders arrive through the relay (rule 8). |
| **MBP** | Facundo. |

- **Every child, delegated task or subagent runs on the VPS** (Facundo, 2026-11-09), read-only reviews and surveys
  included: `delegate_task` against T3 environment `vps-env`, latest Sonnet, effort medium or high, at most 8 at
  once. The coordinator's in-process subagents would run on the Mac, so it does not use them for work.
- **Child threads, delegated tasks and subagents only write code, commit and push their branch:** no gradle, java,
  compiling or tests. The coordinator compiles pushed branches on the PC.
- **PC transport:** the PC's sshd is Windows OpenSSH with `wsl.exe` as shell, so `ssh pc 'cmd'`, scp and rsync do
  not work. Every remote step is a bash script fed on stdin: `ssh -T pc < script.sh` (see `tools/render/remote-submit.sh`).
- Work that still needs running is tracked in `docs/ops/pending-execution.md`.

## Heavy processes (incident 2026-11-09, see `docs/ops/incidents/2026-11-09-vps-saturation.md`)

The VPS is shared: T3 Code runs on it, along with every child thread. A "heavy process" is **anything that runs java,
gradle/gradlew, a render (`tools/render/*`, `hqRender`, `uiRender`) or Minecraft**. Heavy processes run **only on
the PC**, never on the VPS or the Mac mini.

1. **One heavy process at a time on the PC.** Every heavy process goes through `tools/heavy.sh`, which takes
   the global lock `/tmp/t3craft-heavy.lock` on the machine it runs on. If the lock is held, it exits 75 instead of
   queueing. **Nothing heavy runs on the VPS** (rule 8).
2. **Only the coordinator launches heavy processes.** Child threads, delegated tasks and subagents only write code
   and commit/push. They never run java, gradle, builds, tests or renders. Renders are the only exception, and only
   inside a render session Facundo approved (rule 5).
3. **Every heavy process is contained.** `tools/heavy.sh` wraps it like this:
   - nice 19, ionice idle;
   - its own systemd scope with `MemoryMax` (default 6G);
   - a hard `timeout` (default 20m);
   - cleanup on exit, failure, cancel or kill (it kills the whole process group, and runs `./gradlew --stop` for gradle).

   Never run `./gradlew …` or `java …` bare. Use, for example:
   `tools/heavy.sh --timeout 15m -- bash -c 'cd mod && ./gradlew build'`.
   Gradle daemons exit after 10 idle minutes (`org.gradle.daemon.idletimeout` in `mod/gradle.properties`).
4. **Check the load before launching.** If the 1-minute load is above **6**, or less than **8 GB** of RAM is
   available, wait and do not queue more work. `tools/heavy.sh` waits up to 10 min and then gives up with code 75.
   Do not start new child threads while the VPS load is above 6 either.
5. **No batch renders without Facundo's approval.**
   - `tools/render/submit.sh` refuses to run unless `~/.cache/t3craft-coord/renders-approved` exists on the VPS
     (where the children call it).
   - Only the coordinator creates that file (over SSH from the Mac mini), and only after Facundo approves a render
     session. In the file it writes the scope (which agents, roughly how many jobs, expiry). It deletes the file when
     the session ends.
   - In a session the daemon runs with its defaults: **1 worker, load limit 6**. Nobody raises `RENDER_WORKERS`,
     `RENDER_LOAD_MAX`, `HEAVY_LOAD_MAX` or similar limits without Facundo's explicit OK.
   - Agents keep **at most one render in flight**. They write no loops or scripts that submit renders in batch, and
     they do not resubmit in a loop when a render is refused. If `submit.sh` refuses (exit 2), they finish with code
     only and say so.
6. **Cancelling or interrupting a task kills what it launched.** After `task_cancel`, an interrupt, or removing a
   worktree, the coordinator checks `ps` for processes whose arguments or cwd mention that worktree or agent: java,
   gradle, `submit.sh` loops, `sleep`. It kills them, then runs `./gradlew --stop` in that worktree's `mod/`.
   `submit.sh` also stops waiting by itself when the process that launched it is gone.
7. **Agent count and model (Facundo, 2026-11-09).** At most **8 child agents at once**; launch a new one only when
   fewer than 8 are running, and close idle or looping ones. Children run on the latest Sonnet (effort medium or high,
   never xhigh); only the coordinator runs at xhigh. Heavy processes stay limited as above: one at a time, only on the
   PC (gradle by the coordinator through `tools/heavy.sh`, renders through the daemon), no servers on the VPS.
8. **Renders run on Facundo's PC, never on the VPS. This is a fixed rule (Facundo, 2026-11-09).**
   - **The VPS never again runs renders, the render daemon or Citadel Studio,** moratorium or not. The render
     server, its spool (`~/.cache/t3craft-render`) and the viewer Facundo watches live all live on the PC
     (my-pc, WSL, `~/projects/t3craft`).
   - Every render job goes to the PC, through the relay (`tools/render/submit.sh` → `tools/render/remote-submit.sh`).
   - The render daemon runs only on my-pc (WSL Ubuntu, Tailscale 100.64.0.20): one daemon, **1 worker**.
     `start-daemon.sh` refuses to start on the VPS.
   - Agents still call `tools/render/submit.sh` exactly as before. It relays to the PC through
     `tools/render/remote-submit.sh`:
     - the worktree's code travels over SSH as tar+base64 inside a stdin script (`T3CRAFT_RENDER_HOST`; rsync and
       scp do not work against the PC);
     - the job goes into the PC daemon's inbox;
     - the relay polls `status.json`;
     - only `status.json` and the text reports (`report*.txt`, `report*.json`) come back to the local outbox; the
       PNGs stay on the PC (rule 9).
   - Compiling the worktree happens on the PC. Renders still need Facundo's approval (rule 5).
9. **Threads do not open full-size images (incident 2026-11-09 evening).** Every image a thread reads (Read on a PNG,
   a screenshot, an attachment) is stored in T3's database. High-quality renders read by agents bloated the VPS's
   `statev2.sqlite` to ~6.2 GB and T3 hung; the DB was moved to
   `~/.t3/userdata/backups/statev2-20261109-1037-CST` and T3 restarted empty.
   - Judge a render from its text output first (`report.txt`, `report.json`, the plan report).
   - Never Read a full-resolution render PNG in a thread. Until the relay brings back small previews (pending, see
     `docs/ops/pending-execution.md`), children and their subagents do not open render PNGs at all.
   - Once previews exist: at most a few per job, only the downscaled copy (≤ 768 px on the long side).
   - Facundo looks at full-size renders in Citadel Studio on the PC.

## Other standing rules
- **Commits:** no `Co-Authored-By` or any other AI credit in commits.
- **History:** never rewrite `main` (no force push, no rebase of main); only merges.
- **Tests:** this repo has no unit tests. Adding or running them is forbidden; verification is compiling (done by the
  coordinator).
- **No dev servers or Minecraft on the VPS,** apart from what Facundo explicitly approves (Citadel Studio never runs
  there; it lives on the PC, rule 8).
- **T3 is read-only** for agents unless the task says otherwise.
- **No GitHub Actions or other paid GitHub services** (Facundo, 2026-11-09). Actions is disabled for the repo and
  `.github/` was deleted; never add workflows, Dependabot, code scanning, Pages or anything else that bills minutes
  or storage. Builds run only on the PC.
- **Backlog in GitHub issues** (Facundo, 2026-11-09): every piece of work has an issue in
  `Facundo-Barbera/t3craft`.
  - The child's brief and its branch name cite it (`#N`, e.g. `cleanup/1a-dead-code` for #3).
  - Each commit message ends with `Refs #N`.
  - The coordinator's merge into `main` closes it (`Closes #N` in the merge commit).
  - New ideas or bugs found along the way become new issues, not TODOs.
- **Code conventions** are in `docs/dev.md` ("Code conventions"). Package moves are recorded in
  `tools/refactor/moves.tsv`.
- **Config changes are additive.** Every branch adds a RELOAD or RESTART line to `docs/CHANGELOG-dev.md`.
