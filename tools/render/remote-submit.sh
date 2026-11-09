#!/usr/bin/env bash
# Remote relay for the render service: renders run on Facundo's PC (WSL, my-pc), NEVER on the VPS.
# tools/render/submit.sh execs this by default, so agents use submit.sh exactly as before (same options,
# same output, same exit codes, results in the same local outbox folder).
#
# What it does for one request:
#   1. checks over SSH that the PC daemon is alive and that this agent has nothing pending there;
#   2. rsyncs the agent's worktree (no .git, build, .gradle, run) into a per-agent git worktree on the PC,
#      ~/t3craft-jobs/<agent>, created once from the PC repo (the daemon only serves worktrees of its repo);
#   3. drops the request into the PC daemon's inbox, with the worktree path rewritten to the PC copy;
#   4. polls the PC status.json (mirrored into the local outbox so Citadel Studio sees progress) and, when done,
#      copies the whole PC outbox folder (PNGs, report.txt, logs) back to the local outbox.
# Compiling happens on the PC (its daemon, one worker). The VPS only runs ssh/rsync.
#
# Settings (environment):
#   T3CRAFT_RENDER_HOST         ssh target (default user@100.64.0.20, i.e. my-pc over Tailscale)
#   T3CRAFT_RENDER_REMOTE_REPO  the PC's repo, the daemon baseline (default ~/projects/t3craft on the PC)
#   T3CRAFT_RENDER_REMOTE_JOBS  where the per-agent copies live on the PC (default ~/t3craft-jobs)
#   T3CRAFT_RENDER_HOME         local spool (default ~/.cache/t3craft-render); the PC uses its own ~/.cache/t3craft-render
set -uo pipefail

RENDER_HOME="${T3CRAFT_RENDER_HOME:-$HOME/.cache/t3craft-render}"
HOST="${T3CRAFT_RENDER_HOST:-user@100.64.0.20}"
R_REPO="${T3CRAFT_RENDER_REMOTE_REPO:-projects/t3craft}"
R_JOBS="${T3CRAFT_RENDER_REMOTE_JOBS:-t3craft-jobs}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
kind=hq
priority=preview
agent="${T3CRAFT_AGENT:-}"
worktree=""
wait_for=1
timeout_s=1800
launcher=$PPID

SSH=(ssh -o BatchMode=yes -o ConnectTimeout=10 -o ServerAliveInterval=15
	-o ControlMaster=auto -o "ControlPath=$HOME/.ssh/t3craft-render-%C" -o ControlPersist=120)
LOW=(nice -n 19)
ionice -c3 true 2>/dev/null && LOW+=(ionice -c3)

while [ $# -gt 0 ]; do
	case "$1" in
		--kind) kind="${2:?--kind needs hq or ui}"; shift 2 ;;
		--preview) priority=preview; shift ;;
		--final) priority=final; shift ;;
		--worktree) worktree="${2:?--worktree needs a path}"; shift 2 ;;
		--agent) agent="${2:?--agent needs a name}"; shift 2 ;;
		--timeout) timeout_s="${2:?--timeout needs seconds}"; shift 2 ;;
		--no-wait) wait_for=0; shift ;;
		-h|--help) sed -n '2,22p' "$HERE/submit.sh" | sed 's/^# \{0,1\}//'; exit 0 ;;
		--) shift; break ;;
		*) break ;;
	esac
done

if [ -z "$worktree" ]; then
	worktree="$(cd "$HERE" && git rev-parse --show-toplevel 2>/dev/null)" || { echo "submit: not inside a git worktree; pass --worktree DIR" >&2; exit 2; }
fi
worktree="$(cd "$worktree" && pwd)" || { echo "submit: no such worktree" >&2; exit 2; }
[ -f "$worktree/mod/build.gradle" ] || { echo "submit: not a t3craft worktree (no mod/build.gradle): $worktree" >&2; exit 2; }
[ -n "$agent" ] || agent="$(basename "$worktree")"
agent="$(printf '%s' "$agent" | tr -c 'A-Za-z0-9._-' '_' | sed 's/-[0-9][0-9]*$//')"
[ -n "$agent" ] || agent=agent

rhome="$("${SSH[@]}" "$HOST" 'printf %s "$HOME"' 2>/dev/null)" || {
	echo "submit: cannot reach the render PC ($HOST) over ssh. Renders run there, never on the VPS; ask the coordinator." >&2
	exit 3
}
R_SPOOL="$rhome/.cache/t3craft-render"
case "$R_REPO" in /*) ;; *) R_REPO="$rhome/$R_REPO" ;; esac
case "$R_JOBS" in /*) ;; *) R_JOBS="$rhome/$R_JOBS" ;; esac
rwt="$R_JOBS/$agent"

# 1. PC daemon alive (judged with the PC's own clock) and nothing pending for this agent there
check="$("${SSH[@]}" "$HOST" python3 -I - "$R_SPOOL" "$agent" <<'PY'
import json, os, sys, time, glob
spool, agent = sys.argv[1], sys.argv[2]
try:
    d = json.load(open(os.path.join(spool, "daemon.json")))
    alive = d.get("state") == "running" and time.time() * 1000 - d.get("nowMillis", 0) < 30000
except Exception:
    alive = False
if not alive:
    print("dead"); sys.exit(0)
for st in glob.glob(os.path.join(spool, "outbox", agent + "-*", "status.json")):
    try:
        s = json.load(open(st))
    except Exception:
        continue
    if s.get("state") in ("queued", "compiling", "rendering"):
        print("busy " + os.path.basename(os.path.dirname(st))); sys.exit(0)
print("ok")
PY
)"
case "$check" in
	ok) ;;
	dead)
		echo "submit: the render daemon on the PC ($HOST) is not running. Ask the coordinator; do not run gradle or java yourself." >&2
		exit 3 ;;
	busy*)
		echo "submit: rejected: agent $agent already has request ${check#busy } queued or running on the render PC; only one at a time. Wait for it." >&2
		exit 2 ;;
	*)
		echo "submit: could not read the render PC's state ($check)" >&2
		exit 3 ;;
esac

# 2. the per-agent worktree on the PC (once), then the agent's files on top of it
"${SSH[@]}" "$HOST" bash -s -- "$R_REPO" "$rwt" <<'SH' || { echo "submit: could not prepare $2 on the render PC" >&2; exit 1; }
set -e
repo="$1"; wt="$2"
if [ ! -e "$wt/.git" ]; then
	mkdir -p "$(dirname "$wt")"
	git -C "$repo" worktree add --detach "$wt" HEAD >/dev/null
fi
SH
packs_local=""
args=("$@")
for i in "${!args[@]}"; do
	if [ "${args[$i]}" = "--packs" ] && [ $((i + 1)) -lt ${#args[@]} ]; then
		packs_local="${args[$((i + 1))]}"
		args[$((i + 1))]="$R_JOBS/$agent.packs"
	fi
done
"${LOW[@]}" rsync -az --delete \
	--exclude '/.git' --exclude 'build/' --exclude '.gradle/' --exclude '/mod/run/' \
	--exclude 'node_modules/' --exclude '/docs/research/' \
	-e "${SSH[*]}" "$worktree/" "$HOST:$rwt/" || { echo "submit: rsync of the worktree to the render PC failed" >&2; exit 1; }
if [ -n "$packs_local" ]; then
	"${LOW[@]}" rsync -az --delete -e "${SSH[*]}" "${packs_local%/}/" "$HOST:$R_JOBS/$agent.packs/" || { echo "submit: rsync of --packs failed" >&2; exit 1; }
fi

# 3. the request, written atomically into the PC inbox
id="$agent-$(date +%s)$(printf '%03d' $((RANDOM % 1000)))"
python3 -I - "$rwt" "$kind" "$priority" "${args[@]}" <<'PY' | "${SSH[@]}" "$HOST" "mkdir -p '$R_SPOOL/inbox' && cat > '$R_SPOOL/inbox/.$id.tmp' && mv '$R_SPOOL/inbox/.$id.tmp' '$R_SPOOL/inbox/$id.json'" \
	|| { echo "submit: could not leave the request on the render PC" >&2; exit 1; }
import json, sys
worktree, kind, priority, *args = sys.argv[1:]
json.dump({"worktree": worktree, "kind": kind, "priority": priority, "args": args}, sys.stdout, indent=1)
PY
outdir="$RENDER_HOME/outbox/$id"
mkdir -p "$outdir"
echo "submit: request $id ($kind, $priority) left for the render PC ($HOST); results in $outdir"
[ "$wait_for" = 1 ] || exit 0

# 4. poll the PC status, mirror it (and the daemon heartbeat) locally, bring everything back when final
fetch() {
	"${SSH[@]}" "$HOST" "cat '$R_SPOOL/outbox/$id/status.json' 2>/dev/null" >"$outdir/.status.tmp" 2>/dev/null \
		&& [ -s "$outdir/.status.tmp" ] && mv "$outdir/.status.tmp" "$outdir/status.json"
	"${SSH[@]}" "$HOST" "cat '$R_SPOOL/daemon.json' 2>/dev/null" >"$RENDER_HOME/.daemon.tmp" 2>/dev/null \
		&& [ -s "$RENDER_HOME/.daemon.tmp" ] && mv "$RENDER_HOME/.daemon.tmp" "$RENDER_HOME/daemon.json"
	rm -f "$outdir/.status.tmp" "$RENDER_HOME/.daemon.tmp"
}
show() {
	python3 -I - "$outdir/status.json" <<'PY'
import json, sys
try:
    s = json.load(open(sys.argv[1]))
except Exception:
    print("waiting\x1fthe PC daemon has not picked the request up yet\x1fno")
    sys.exit(0)
final = "yes" if s.get("state") in ("done", "error") else "no"
print("%s\x1f%s\x1f%s" % (s.get("state", "?"), s.get("message", "").replace("\n", " "), final))
PY
}

last=""
waited=0
while :; do
	fetch
	line="$(show)"
	if [ "$line" != "$last" ]; then
		IFS=$'\x1f' read -r state message final <<<"$line"
		echo "[$state] $message"
		last="$line"
	fi
	IFS=$'\x1f' read -r state message final <<<"$line"
	[ "$final" = yes ] && break
	if [ "$waited" -ge "$timeout_s" ]; then
		echo "submit: gave up waiting after ${timeout_s}s; the request is still on the render PC ($id)" >&2
		exit 4
	fi
	if ! kill -0 "$launcher" 2>/dev/null || [ "$(ps -o ppid= -p $$ | tr -d ' ')" = 1 ]; then
		echo "submit: the launching process is gone; stop waiting (the request stays on the render PC)" >&2
		exit 4
	fi
	sleep 5
	waited=$((waited + 5))
done

"${LOW[@]}" rsync -az -e "${SSH[*]}" "$HOST:$R_SPOOL/outbox/$id/" "$outdir/" \
	|| echo "submit: warning: could not copy the results back from the render PC" >&2
# the PC and the VPS both keep the spool at ~/.cache/t3craft-render: rewrite paths if the homes differ
if [ "$R_SPOOL" != "$RENDER_HOME" ]; then
	for f in "$outdir/status.json" "$outdir/report.txt"; do
		[ -f "$f" ] && sed -i "s#$R_SPOOL#$RENDER_HOME#g" "$f"
	done
fi

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
elif s.get("report"):
    print("report: " + s["report"])
PY
case "$state" in
	done) exit 0 ;;
	*) case "$message" in rejected*) exit 2 ;; *) exit 1 ;; esac ;;
esac
