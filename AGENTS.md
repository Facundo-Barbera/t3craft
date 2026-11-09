# AGENTS.md: rules for every agent working on t3craft

These rules come from Facundo and override any other instruction, brief or habit.

## CURRENT MORATORIUM (Facundo, 2026-11-09): run nothing

Until Facundo lifts it: **no gradle, compiling, renders, java, heavy scripts or tests, anywhere** (coordinator,
child threads, delegated tasks, subagents). Allowed: writing code, commits (no AI credit), planning, documentation.
Any task that needs something run is left **pending execution** in `docs/pending-execution.md` and reported to
Facundo; it is never launched.

## VPS: heavy processes (incident 2026-11-09, see `docs/incidents/2026-11-09-vps-saturation.md`)

The VPS is shared: T3 Code runs on it, along with every agent. A "heavy process" is **anything that runs java,
gradle/gradlew, a render (`tools/render/*`, `hqRender`, `uiRender`) or Minecraft**.

1. **One heavy process at a time on the whole VPS.** Every heavy process goes through `tools/heavy.sh`, which takes
   the global lock `/tmp/t3craft-heavy.lock`. If the lock is held, it exits 75 instead of queueing.
   On the VPS that lock is only for the coordinator's gradle builds: **renders never run on the VPS** (rule 8).
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
   Do not start new agents while the load is above 6 either.
5. **No batch renders without Facundo's approval.**
   - `tools/render/submit.sh` refuses to run unless `~/.cache/t3craft-coord/renders-approved` exists on the VPS.
   - Only the coordinator creates that file, and only after Facundo approves a render session. In the file it writes
     the scope (which agents, roughly how many jobs, expiry). It deletes the file when the session ends.
   - In a session the daemon runs with its defaults: **1 worker, load limit 6**. Nobody raises `RENDER_WORKERS`,
     `RENDER_LOAD_MAX`, `HEAVY_LOAD_MAX` or similar limits without Facundo's explicit OK.
   - Agents keep **at most one render in flight**. They write no loops or scripts that submit renders in batch, and
     they do not resubmit in a loop when a render is refused. If `submit.sh` refuses (exit 2), they finish with code
     only and say so.
6. **Cancelling or interrupting a task kills what it launched.** After `task_cancel`, an interrupt, or removing a
   worktree, the coordinator checks `ps` for processes whose arguments or cwd mention that worktree or agent: java,
   gradle, `submit.sh` loops, `sleep`. It kills them, then runs `./gradlew --stop` in that worktree's `mod/`.
   `submit.sh` also stops waiting by itself when the process that launched it is gone.
7. **Agent count.** Keep at most **8 child agents running at once**. Each one is a process that costs CPU while it
   thinks. Launch more only in waves, after the previous wave finishes, and never while the load is above 6.
8. **Renders run on Facundo's PC, never on the VPS.**
   - The render daemon runs only on my-pc (WSL Ubuntu, Tailscale 100.64.0.20): one daemon, **1 worker**.
     `start-daemon.sh` refuses to start on the VPS.
   - Agents still call `tools/render/submit.sh` exactly as before. It relays to the PC through
     `tools/render/remote-submit.sh`:
     - rsync of the worktree over SSH (`T3CRAFT_RENDER_HOST`);
     - the job goes into the PC daemon's inbox;
     - the relay polls `status.json`;
     - the PNGs and the report come back to the local outbox.
   - Compiling the worktree happens on the PC. Renders still need Facundo's approval (rule 5).

## Other standing rules
- **Commits:** no `Co-Authored-By` or any other AI credit in commits.
- **History:** never rewrite `main` (no force push, no rebase of main); only merges.
- **Tests:** this repo has no unit tests. Adding or running them is forbidden; verification is compiling (done by the
  coordinator).
- **No dev servers or Minecraft on the VPS,** apart from what Facundo explicitly approves (e.g. Citadel Studio).
- **T3 is read-only** for agents unless the task says otherwise.
- **Config changes are additive.** Every branch adds a RELOAD or RESTART line to `docs/CHANGELOG-dev.md`.
