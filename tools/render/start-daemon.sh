#!/usr/bin/env bash
# COORDINATOR ONLY. Starts (or stops) the shared render daemon; agents never run this, gradle or java.
#
#   tools/render/start-daemon.sh [--foreground]   start it (in the background unless --foreground)
#   tools/render/start-daemon.sh --stop           ask it to finish the current job and exit
#   tools/render/start-daemon.sh --status         print its heartbeat
#
# Run it from the checkout whose code should be the baseline (normally main/integration, CLEAN: the
# daemon decides "does this worktree need compiling?" by diffing against this checkout's HEAD).
# One JVM, nice 19 / ionice idle, -Xmx3g, render threads capped (RENDER_PARALLELISM, default 6).
# It restarts itself on exit code 75 (a render thread that would not stop). Log: $RENDER_HOME/daemon.log.
# Protocol and limits: docs/tools/render-service.md
set -euo pipefail

RENDER_HOME="${T3CRAFT_RENDER_HOME:-$HOME/.cache/t3craft-render}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$RENDER_HOME"

mode=background
case "${1:-}" in
	--foreground) mode=foreground ;;
	--stop) touch "$RENDER_HOME/STOP"; echo "start-daemon: STOP left; the daemon exits after its current job"; exit 0 ;;
	--status) cat "$RENDER_HOME/daemon.json" 2>/dev/null || echo "no heartbeat in $RENDER_HOME"; exit 0 ;;
	--supervise) mode=supervise ;;
	"") ;;
	*) echo "usage: $0 [--foreground|--stop|--status]" >&2; exit 2 ;;
esac

NICE=(nice -n 19)
if ionice -c3 true 2>/dev/null; then
	NICE+=(ionice -c3)
fi

launch_loop() {
	cd "$REPO/mod"
	local cp java rc
	while :; do
		"${NICE[@]}" ./gradlew --offline --console=plain -q renderDaemonClasspath
		cp="$(cat build/render-daemon/classpath.txt)"
		java="$(cat build/render-daemon/java.txt)"
		rm -f "$RENDER_HOME/STOP"
		set +e
		"${NICE[@]}" "$java" -Xmx3g -Djava.awt.headless=true \
			-Djava.util.concurrent.ForkJoinPool.common.parallelism="${RENDER_PARALLELISM:-6}" \
			-Dt3craft.render.repo="$REPO" \
			-cp "$cp" dev.t3craft.tools.render.RenderDaemon
		rc=$?
		set -e
		echo "$(date -Is) render daemon exited with $rc"
		[ "$rc" -eq 75 ] || break
		sleep 5
	done
}

if [ "$mode" = supervise ] || [ "$mode" = foreground ]; then
	launch_loop
	exit 0
fi

# refuse a second daemon
if [ -f "$RENDER_HOME/daemon.json" ]; then
	if python3 -I - "$RENDER_HOME/daemon.json" <<'PY'
import json, sys, time
d = json.load(open(sys.argv[1]))
sys.exit(0 if d.get("state") == "running" and time.time() * 1000 - d.get("nowMillis", 0) < 30000 else 1)
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
