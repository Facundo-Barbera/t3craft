#!/usr/bin/env bash
# Runs ONE heavy process (java, gradle, render) on the shared VPS, following AGENTS.md "VPS: heavy processes".
#
#   tools/heavy.sh [--timeout 20m] [--mem 6G] [--wait 600] -- <command...>
#
# - Global lock /tmp/t3craft-heavy.lock: if another heavy process holds it, exit 75 (do not queue).
# - Load/RAM gate: waits (up to --wait seconds, default 600) while load1 > HEAVY_LOAD_MAX (6) or
#   MemAvailable < HEAVY_MIN_FREE_GB (8); then gives up with exit 75.
# - Runs in its own systemd scope (MemoryMax, low CPU/IO weight), nice 19, ionice idle, hard `timeout`.
# - Cleans up on exit, failure, Ctrl-C or kill: kills the whole process group and, for gradle, `./gradlew --stop`.
set -uo pipefail

TIMEOUT=20m
MEM=6G
WAIT=600
while [ $# -gt 0 ]; do
	case "$1" in
		--timeout) TIMEOUT="$2"; shift 2 ;;
		--mem) MEM="$2"; shift 2 ;;
		--wait) WAIT="$2"; shift 2 ;;
		--) shift; break ;;
		*) break ;;
	esac
done
[ $# -gt 0 ] || { echo "usage: tools/heavy.sh [--timeout 20m] [--mem 6G] [--wait 600] -- <command...>" >&2; exit 2; }

LOCK=/tmp/t3craft-heavy.lock
LOAD_MAX="${HEAVY_LOAD_MAX:-6}"
MIN_FREE_GB="${HEAVY_MIN_FREE_GB:-8}"

exec 9>"$LOCK"
if ! flock -n 9; then
	echo "heavy: another heavy process holds $LOCK ($(cat "$LOCK.owner" 2>/dev/null)); not queueing" >&2
	exit 75
fi
echo "$$ $(date -Is) $*" >"$LOCK.owner"

busy() {
	local load avail
	load=$(cut -d' ' -f1 /proc/loadavg)
	avail=$(awk '/MemAvailable/ {print int($2/1048576)}' /proc/meminfo)
	awk -v l="$load" -v m="$LOAD_MAX" 'BEGIN{exit !(l > m)}' && { echo "load $load > $LOAD_MAX"; return 0; }
	[ "$avail" -lt "$MIN_FREE_GB" ] && { echo "MemAvailable ${avail}G < ${MIN_FREE_GB}G"; return 0; }
	return 1
}
waited=0
while reason=$(busy); do
	if [ "$waited" -ge "$WAIT" ]; then
		echo "heavy: machine still busy after ${WAIT}s ($reason); giving up" >&2
		rm -f "$LOCK.owner"
		exit 75
	fi
	echo "heavy: waiting, $reason" >&2
	sleep 15
	waited=$((waited + 15))
done

GRADLE_DIR=""
for a in "$@"; do
	case "$a" in *gradlew*) GRADLE_DIR="$(pwd)" ;; esac
done

child=""
cleanup() {
	trap - EXIT INT TERM HUP
	if [ -n "$child" ] && kill -0 "$child" 2>/dev/null; then
		kill -TERM -- -"$child" 2>/dev/null
		sleep 5
		kill -KILL -- -"$child" 2>/dev/null
	fi
	if [ -n "$GRADLE_DIR" ]; then
		# the wrapper lives in mod/ in this repo; stop the daemon from wherever gradlew is
		for g in "$GRADLE_DIR" "$GRADLE_DIR/mod"; do
			if [ -x "$g/gradlew" ]; then
				(cd "$g" && timeout 60 ./gradlew --stop >/dev/null 2>&1)
				break
			fi
		done
	fi
	rm -f "$LOCK.owner"
}
trap cleanup EXIT INT TERM HUP

setsid systemd-run --user --scope -q -p MemoryMax="$MEM" -p CPUWeight=20 -p IOWeight=20 \
	nice -n 19 ionice -c3 timeout -k 30s "$TIMEOUT" "$@" &
child=$!
wait "$child"
rc=$?
exit "$rc"
