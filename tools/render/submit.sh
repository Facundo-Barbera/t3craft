#!/usr/bin/env bash
# Ask the shared render service (on the render PC) for a render and wait for it. For agents: you never run gradle or java.
#
#   tools/render/submit.sh [options] [--] <renderer args>
#
# Options (before the renderer args):
#   --kind hq|ui      what to render (default hq)
#   --preview         low resolution, fast (default)
#   --final           full resolution (heavier: only when the design is settled)
#   --worktree DIR    the worktree whose code to render (default: the one this script is in)
#   --agent NAME      your name, for the one-request-at-a-time rule (default: $T3CRAFT_AGENT or the worktree's folder name)
#   --timeout SEC     give up waiting after SEC seconds (default 3600; the request keeps going)
#   --no-wait         only leave the request, print its id and outbox folder
#
# Examples:
#   tools/render/submit.sh --layout hybrid-ringcampus --projects 4 --only hub,aerial
#   tools/render/submit.sh --final --layout hybrid-ringcampus --projects 4,10,20
#   tools/render/submit.sh --layout hybrid-ringcampus --projects 4 --packs "$HOME/.config/t3craft/hq-layouts"
#
# Prints the PNG paths and the report path when done. Exit codes: 0 done, 1 render/compile error,
# 2 request rejected, 3 daemon not running, 4 gave up waiting.
# Protocol and limits: docs/tools/render-service.md
set -uo pipefail

RENDER_HOME="${T3CRAFT_RENDER_HOME:-$HOME/.cache/t3craft-render}"
# AGENTS.md "VPS: heavy processes": renders only in a session Facundo approved
if [ ! -f "$HOME/.cache/t3craft-coord/renders-approved" ]; then
	echo "submit: renders are not approved right now (AGENTS.md, VPS rules). Do not retry or loop; finish with code only and say so." >&2
	exit 2
fi
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Renders run on Facundo's PC (my-pc, WSL), never on the VPS: by default this hands the request to the
# remote relay, which syncs the worktree there and brings the results back to the same local outbox.
# T3CRAFT_RENDER_MODE=local is only for running ON the render PC itself.
if [ "${T3CRAFT_RENDER_MODE:-remote}" != local ]; then
	exec "$HERE/remote-submit.sh" "$@"
fi
kind=hq
priority=preview
agent="${T3CRAFT_AGENT:-}"
worktree=""
wait_for=1
timeout_s=1800
launcher=$PPID

usage() { sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
	case "$1" in
		--kind) kind="${2:?--kind needs hq or ui}"; shift 2 ;;
		--preview) priority=preview; shift ;;
		--final) priority=final; shift ;;
		--worktree) worktree="${2:?--worktree needs a path}"; shift 2 ;;
		--agent) agent="${2:?--agent needs a name}"; shift 2 ;;
		--timeout) timeout_s="${2:?--timeout needs seconds}"; shift 2 ;;
		--no-wait) wait_for=0; shift ;;
		-h|--help) usage; exit 0 ;;
		--) shift; break ;;
		*) break ;;
	esac
done

if [ -z "$worktree" ]; then
	worktree="$(cd "$HERE" && git rev-parse --show-toplevel 2>/dev/null)" || { echo "submit: not inside a git worktree; pass --worktree DIR" >&2; exit 2; }
fi
worktree="$(cd "$worktree" && pwd)" || { echo "submit: no such worktree" >&2; exit 2; }
[ -n "$agent" ] || agent="$(basename "$worktree")"
agent="$(printf '%s' "$agent" | tr -c 'A-Za-z0-9._-' '_' | sed 's/-[0-9][0-9]*$//')"
[ -n "$agent" ] || agent=agent

# is the daemon alive? (its heartbeat is rewritten every 2 s)
alive="$(python3 -I - "$RENDER_HOME/daemon.json" <<'PY'
import json, sys, time
try:
    d = json.load(open(sys.argv[1]))
    ok = d.get("state") == "running" and time.time() * 1000 - d.get("nowMillis", 0) < 30000
    print("yes" if ok else "no")
except Exception:
    print("no")
PY
)"
if [ "$alive" != "yes" ]; then
	echo "submit: the render daemon is not running (no fresh heartbeat in $RENDER_HOME/daemon.json)." >&2
	echo "        Ask the coordinator to start it (tools/render/start-daemon.sh). Do not run gradle or java yourself." >&2
	exit 3
fi

mkdir -p "$RENDER_HOME/inbox"
id="$agent-$(date +%s)$(printf '%03d' $((RANDOM % 1000)))"
tmp="$RENDER_HOME/inbox/.$id.tmp"
python3 -I - "$tmp" "$worktree" "$kind" "$priority" "$@" <<'PY'
import json, sys
out, worktree, kind, priority, *args = sys.argv[1:]
json.dump({"worktree": worktree, "kind": kind, "priority": priority, "args": args}, open(out, "w"), indent=1)
PY
mv "$tmp" "$RENDER_HOME/inbox/$id.json"
outdir="$RENDER_HOME/outbox/$id"
echo "submit: request $id ($kind, $priority) left for the daemon; results in $outdir"
[ "$wait_for" = 1 ] || exit 0

show() { # prints state and message; the 3rd line says whether the state is final
	python3 -I - "$outdir/status.json" <<'PY'
import json, sys
try:
    s = json.load(open(sys.argv[1]))
except Exception:
    print("waiting\x1fthe daemon has not picked the request up yet\x1fno")
    sys.exit(0)
final = "yes" if s.get("state") in ("done", "error") else "no"
print("%s\x1f%s\x1f%s" % (s.get("state", "?"), s.get("message", "").replace("\n", " "), final))
PY
}

last=""
waited=0
while :; do
	line="$(show)"
	if [ "$line" != "$last" ]; then
		IFS=$'\x1f' read -r state message final <<<"$line"
		echo "[$state] $message"
		last="$line"
	fi
	IFS=$'\x1f' read -r state message final <<<"$line"
	if [ "$final" = yes ]; then
		break
	fi
	if [ "$waited" -ge "$timeout_s" ]; then
		echo "submit: gave up waiting after ${timeout_s}s; the request is still in the service: $outdir/status.json" >&2
		exit 4
	fi
	# orphan guard: if whoever launched us is gone (task cancelled, session restarted), stop waiting
	if ! kill -0 "$launcher" 2>/dev/null || [ "$(ps -o ppid= -p $$ | tr -d ' ')" = 1 ]; then
		echo "submit: the launching process is gone; stop waiting (the request stays in the service)" >&2
		exit 4
	fi
	sleep 5
	waited=$((waited + 5))
done

python3 -I - "$outdir/status.json" <<'PY'
import json, sys
s = json.load(open(sys.argv[1]))
if s.get("log"):
    print("--- log ---")
    print(s["log"])
if s["state"] == "done":
    print("images:")
    for f in s.get("files", []):
        print("  " + f)
    if s.get("report"):
        print("report: " + s["report"])
else:
    if s.get("report"):
        print("report: " + s["report"])
PY
case "$state" in
	done) exit 0 ;;
	*) case "$message" in rejected*) exit 2 ;; *) exit 1 ;; esac ;;
esac
