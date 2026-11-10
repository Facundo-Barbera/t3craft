#!/usr/bin/env bash
# Citadel Studio (coordinator only): start it niced in the background, or stop / check it.
#
#   tools/studio/start.sh [--host IP] [--port N]   start (default $T3CRAFT_STUDIO_HOST or 127.0.0.1, port 8765)
#   tools/studio/start.sh --status                 is it running? where?
#   tools/studio/start.sh --stop                   stop it
#
# Log and pidfile: ~/.cache/t3craft-studio/{studio.log,studio.pid}. Docs: docs/tools/studio.md
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE="$HOME/.cache/t3craft-studio"
PIDFILE="$STATE/studio.pid"
LOG="$STATE/studio.log"
mkdir -p "$STATE"

running() {
	[ -f "$PIDFILE" ] || return 1
	local pid
	pid="$(cat "$PIDFILE" 2>/dev/null)"
	[[ "$pid" =~ ^[0-9]+$ ]] || return 1
	kill -0 "$pid" 2>/dev/null || return 1
	# make sure the pid is still ours, not a recycled one
	tr '\0' ' ' <"/proc/$pid/cmdline" 2>/dev/null | grep -q "studio.py" || return 1
	echo "$pid"
}

case "${1:-}" in
	--status)
		if pid="$(running)"; then
			echo "studio: running (pid $pid)"
			grep "Citadel Studio on" "$LOG" 2>/dev/null | tail -n1
			exit 0
		fi
		echo "studio: not running"; exit 1 ;;
	--stop)
		if pid="$(running)"; then
			kill "$pid"
			for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$pid" 2>/dev/null || break; sleep 0.3; done
			rm -f "$PIDFILE"
			echo "studio: stopped (pid $pid)"
		else
			rm -f "$PIDFILE"
			echo "studio: not running"
		fi
		exit 0 ;;
	-h|--help)
		sed -n '2,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

if pid="$(running)"; then
	echo "studio: already running (pid $pid); use --stop first"
	exit 0
fi
echo "--- $(date -Is) start $*" >>"$LOG"
setsid nohup nice -n 10 python3 -I "$HERE/studio.py" "$@" >>"$LOG" 2>&1 </dev/null &
echo $! >"$PIDFILE"
sleep 1
if pid="$(running)"; then
	tail -n1 "$LOG"
	echo "studio: started (pid $pid); log $LOG"
else
	echo "studio: failed to start; last lines of $LOG:" >&2
	tail -n 15 "$LOG" >&2
	rm -f "$PIDFILE"
	exit 1
fi
