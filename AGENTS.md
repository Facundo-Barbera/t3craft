# AGENTS.md: rules for every agent working on t3craft

These rules come from Facundo and override any other instruction, brief or habit.

## VPS: heavy processes (incident 2026-11-09, see `docs/incidents/2026-11-09-vps-saturation.md`)

The VPS is shared: T3 Code runs on it, along with every agent. A "heavy process" is **anything that runs java,
gradle/gradlew, a render (`tools/render/*`, `hqRender`, `uiRender`) or Minecraft**.

1. **One heavy process at a time on the whole VPS.** Every heavy process goes through `tools/heavy.sh`, which takes
   the global lock `/tmp/t3craft-heavy.lock`. If the lock is held, it exits 75 instead of queueing.
   The render daemon holds that lock for as long as it is alive: while it runs, nobody compiles or builds anything else.
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
   - The render daemon and `tools/render/submit.sh` refuse to run unless `~/.cache/t3craft-coord/renders-approved`
     exists.
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

## Other standing rules
- **Commits:** no `Co-Authored-By` or any other AI credit in commits.
- **History:** never rewrite `main` (no force push, no rebase of main); only merges.
- **Tests:** this repo has no unit tests. Adding or running them is forbidden; verification is compiling (done by the
  coordinator).
- **No dev servers or Minecraft on the VPS,** apart from what Facundo explicitly approves (e.g. Citadel Studio).
- **T3 is read-only** for agents unless the task says otherwise.
- **Config changes are additive.** Every branch adds a RELOAD or RESTART line to `docs/CHANGELOG-dev.md`.
