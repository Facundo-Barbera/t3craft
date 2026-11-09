#!/usr/bin/env bash
# COORDINATOR ONLY. Starts (or stops) the shared render daemon; agents never run this, gradle or java.
#
#   tools/render/start-daemon.sh [--foreground]   start it (in the background unless --foreground)
#   tools/render/start-daemon.sh --stop           ask it to finish the current job and exit (the queue
#                                                 stays in the outbox and resumes on the next start)
#   tools/render/start-daemon.sh --status         print its heartbeat
#
# Run it from the checkout whose code should be the baseline (normally main/integration, CLEAN: the
# daemon decides "does this worktree need compiling?" by diffing against this checkout's HEAD).
# One JVM, nice 19 / ionice idle. Settings (environment, read at start):
#   RENDER_WORKERS      jobs at once (default 1, AGENTS.md "VPS: heavy processes"; compiles go one at a time)
#   RENDER_PARALLELISM  render threads in total, split between the workers (default: cores - 1)
#   RENDER_LOAD_MAX     above this 1-min load no job starts while another runs (default 6)
#   RENDER_XMX          max heap (default 8g)
# Runs ONLY on the render PC (my-pc, WSL), never on the VPS (AGENTS.md). One daemon, 1 worker. On the PC it
# holds the PC's own /tmp/t3craft-heavy.lock while alive. Approval of render sessions is enforced on the VPS
# side, in tools/render/submit.sh (~/.cache/t3craft-coord/renders-approved there).
# It restarts itself on exit code 75 (a render thread that would not stop). Log: $RENDER_HOME/daemon.log.
# Protocol and limits: docs/tools/render-service.md
set -euo pipefail

RENDER_HOME="${T3CRAFT_RENDER_HOME:-$HOME/.cache/t3craft-render}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$RENDER_HOME"

mode=background
case "${1:-}" in
	--foreground) mode=foreground ;;
	--stop) touch "$RENDER_HOME/STOP"; echo "start-daemon: STOP left; the daemon exits after its running jobs (queued requests stay queued and resume on the next start)"; exit 0 ;;
	--status) cat "$RENDER_HOME/daemon.json" 2>/dev/null || echo "no heartbeat in $RENDER_HOME"; exit 0 ;;
	--supervise) mode=supervise ;;
	"") ;;
	*) echo "usage: $0 [--foreground|--stop|--status]" >&2; exit 2 ;;
esac

int_or() { case "$1" in ''|*[!0-9]*) echo "$2" ;; *) if [ "$1" -ge 1 ]; then echo "$1"; else echo "$2"; fi ;; esac; }
WORKERS="$(int_or "${RENDER_WORKERS:-}" 1)"
# the daemon only runs on the render PC: use its cores (all but one) and a roomy heap
PARALLELISM="$(int_or "${RENDER_PARALLELISM:-}" $(( $(nproc) > 2 ? $(nproc) - 1 : 1 )))"
XMX="${RENDER_XMX:-8g}"
export RENDER_WORKERS="$WORKERS" RENDER_PARALLELISM="$PARALLELISM"
export RENDER_LOAD_MAX="${RENDER_LOAD_MAX:-6}"
HEAVY_LOCK=/tmp/t3craft-heavy.lock

NICE=(nice -n 19)
if ionice -c3 true 2>/dev/null; then
	NICE+=(ionice -c3)
fi

launch_loop() {
	cd "$REPO/mod"
	local cp java rc
	while :; do
		"${NICE[@]}" ./gradlew --offline --console=plain -q renderDaemonClasspath 9>&- # gradle's daemon must not inherit the heavy lock
		cp="$(cat build/render-daemon/classpath.txt)"
		java="$(cat build/render-daemon/java.txt)"
		rm -f "$RENDER_HOME/STOP"
		set +e
		echo "$(date -Is) render daemon: $WORKERS worker(s), $PARALLELISM render thread(s), -Xmx$XMX"
		"${NICE[@]}" "$java" "-Xmx$XMX" -Djava.awt.headless=true \
			-Djava.util.concurrent.ForkJoinPool.common.parallelism="$PARALLELISM" \
			-Dt3craft.render.workers="$WORKERS" \
			-Dt3craft.render.parallelism="$PARALLELISM" \
			-Dt3craft.render.repo="$REPO" \
			-cp "$cp" dev.t3craft.tools.render.RenderDaemon
		rc=$?
		set -e
		echo "$(date -Is) render daemon exited with $rc"
		[ "$rc" -eq 75 ] || break
		sleep 5
	done
	# leave no gradle daemon behind (the per-job compiles start one)
	timeout 60 ./gradlew --stop >/dev/null 2>&1 9>&- || true
}

if [ "$mode" = supervise ] || [ "$mode" = foreground ]; then
	# hold the global heavy-process lock for the daemon's whole life (fd 9 is inherited by the JVM)
	exec 9>"$HEAVY_LOCK"
	if ! flock -n 9; then
		echo "$(date -Is) start-daemon: another heavy process holds $HEAVY_LOCK ($(cat "$HEAVY_LOCK.owner" 2>/dev/null)); not starting" >&2
		exit 75
	fi
	echo "$$ $(date -Is) render daemon" >"$HEAVY_LOCK.owner"
	trap 'rm -f "$HEAVY_LOCK.owner"' EXIT
	launch_loop
	exit 0
fi

# never on the VPS (AGENTS.md: renders run on the render PC)
if [ "$(hostname)" = "${T3CRAFT_VPS_HOSTNAME:-vps-env}" ] || [ -n "${T3CRAFT_IS_VPS:-}" ]; then
	echo "start-daemon: this is the VPS; the render daemon runs only on the render PC (my-pc). See AGENTS.md." >&2
	exit 1
fi

# refuse a second daemon
if [ -f "$RENDER_HOME/daemon.json" ]; then
	if python3 -I - "$RENDER_HOME/daemon.json" <<'PY'
import json, sys, time
d = json.load(open(sys.argv[1]))
sys.exit(0 if d.get("state") in ("running", "stopping") and time.time() * 1000 - d.get("nowMillis", 0) < 30000 else 1)
PY
	then
		echo "start-daemon: a daemon is already running (see --status); use --stop first" >&2
		exit 1
	fi
fi

# the baseline must be clean, or "no code changes" would be judged against the wrong tree
if [ -n "$(git -C "$REPO" status --porcelain -- mod/src mod/build.gradle mod/gradle.properties)" ] && [ "${T3CRAFT_RENDER_ALLOW_DIRTY:-0}" != 1 ]; then
	echo "start-daemon: $REPO has uncommitted changes under mod/; commit them or set T3CRAFT_RENDER_ALLOW_DIRTY=1" >&2
	exit 1
fi

nohup setsid "${BASH_SOURCE[0]}" --supervise >>"$RENDER_HOME/daemon.log" 2>&1 </dev/null &
echo "start-daemon: starting from $REPO (baseline $(git -C "$REPO" rev-parse --short HEAD)); log: $RENDER_HOME/daemon.log"
echo "              it is ready when 'start-daemon.sh --status' shows state running (first start compiles, then boots Minecraft)"
