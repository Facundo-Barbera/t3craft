#!/usr/bin/env bash
# Replays tools/refactor/moves.tsv on the current checkout: moves the Java files that are still at an old location
# (git mv + package line), rewrites every old FQN to the new one, renames simple names when a class was renamed, and
# adds the imports that a same-package reference loses when a class leaves its package.
#
#   tools/refactor/apply-moves.sh [--dry-run] [--moves FILE]
#
# Two uses:
# - A phase-3 move branch (#5): add the move lines to moves.tsv, run this script, commit.
# - An older branch (e.g. a paused design round): `git merge origin/main`, fix any conflict markers, run this script,
#   commit. The coordinator compiles the result on the PC.
#
# Text only: python3 + git, no java or gradle, so children may run it. It never touches docs/CHANGELOG-dev.md
# (history) or moves.tsv. It prints what it changed and warns about anything it could not do.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
exec python3 - "$@" <<'PY'
import os, re, subprocess, sys

args = sys.argv[1:]
dry = "--dry-run" in args
moves_file = "tools/refactor/moves.tsv"
if "--moves" in args:
    moves_file = args[args.index("--moves") + 1]

ROOTS = [f"mod/src/{s}/java" for s in ("main", "client", "hqrender", "uirender")]
SKIP = {"docs/CHANGELOG-dev.md", "tools/refactor/moves.tsv"}
TEXT = re.compile(r"\.(java|json|md|gradle|sh|py|txt|properties)$")
IDENT = r"[A-Za-z0-9_$]"

def git(*a, check=True):
    return subprocess.run(["git", *a], capture_output=True, text=True, check=check).stdout

def warn(msg):
    print("WARN " + msg, file=sys.stderr)

moves = []
for n, line in enumerate(open(moves_file, encoding="utf-8"), 1):
    line = line.rstrip("\n")
    if not line.strip() or line.lstrip().startswith("#"):
        continue
    parts = line.split("\t")
    if len(parts) < 2:
        sys.exit(f"{moves_file}:{n}: expected <old>\\t<new>")
    old, new = parts[0].strip(), parts[1].strip()
    if old.endswith(".*") != new.endswith(".*"):
        sys.exit(f"{moves_file}:{n}: package and class entries cannot mix")
    moves.append((old, new))

def find_src(fqn):
    rel = fqn.replace(".", "/") + ".java"
    for r in ROOTS:
        if os.path.isfile(os.path.join(r, rel)):
            return r, os.path.join(r, rel)
    return None, None

def files():
    return [f for f in git("ls-files").split("\n")
            if f and f not in SKIP and TEXT.search(f) and os.path.isfile(f)
            and not f.startswith("bridge/vendor/")]

def read(f):
    try:
        return open(f, encoding="utf-8").read()
    except UnicodeDecodeError:
        return None

changed = set()
def write(f, s):
    changed.add(f)
    if not dry:
        open(f, "w", encoding="utf-8").write(s)

def move_file(src, dst, new_pkg):
    print(f"move {src} -> {dst}")
    if dry:
        return
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run(["git", "mv", src, dst], check=True)
    s = open(dst, encoding="utf-8").read()
    s2 = re.sub(r"^package\s+[\w.]+;", f"package {new_pkg};", s, count=1, flags=re.M)
    if s2 != s:
        write(dst, s2)

# Expand package entries into class entries for files still at the old location.
class_moves = []
for old, new in moves:
    if old.endswith(".*"):
        op, np = old[:-2], new[:-2]
        for r in ROOTS:
            d = os.path.join(r, op.replace(".", "/"))
            if os.path.isdir(d):
                for name in sorted(os.listdir(d)):
                    if name.endswith(".java"):
                        cls = name[:-5]
                        class_moves.append((f"{op}.{cls}", f"{np}.{cls}"))
        class_moves.append((old, new))  # still used for the prefix rewrite below
    else:
        class_moves.append((old, new))

# 1. Move files that are still at an old location.
moved = []  # (new path, old package, new package)
for old, new in class_moves:
    if old.endswith(".*"):
        continue
    root, src = find_src(old)
    if not src:
        continue
    _, already = find_src(new)
    if already:
        warn(f"{old} and {new} both exist; left {src} alone")
        continue
    dst = os.path.join(root, new.replace(".", "/") + ".java")
    move_file(src, dst, new.rsplit(".", 1)[0])
    moved.append((dst, old.rsplit(".", 1)[0], new.rsplit(".", 1)[0]))

# 2. Rewrite FQNs, simple names and same-package imports.
all_files = files()
contents = {f: read(f) for f in all_files}
for old, new in class_moves:
    if old.endswith(".*"):
        op, np = old[:-2], new[:-2]
        rx = re.compile(r"(?<![\w.])" + re.escape(op) + r"\.(?=[A-Z])")
        for f, s in contents.items():
            if s and rx.search(s):
                contents[f] = rx.sub(np + ".", s)
        continue
    old_pkg, old_simple = old.rsplit(".", 1)
    new_pkg, new_simple = new.rsplit(".", 1)
    fq = re.compile(r"(?<![\w.$])" + re.escape(old) + r"(?!" + IDENT + r")")
    simple = re.compile(r"(?<![\w.$])" + re.escape(old_simple) + r"(?!" + IDENT + r")")
    for f, s in list(contents.items()):
        if not s:
            continue
        s2 = fq.sub(new, s)
        if f.endswith(".java"):
            pkg = re.search(r"^package\s+([\w.]+);", s2, re.M)
            pkg = pkg.group(1) if pkg else ""
            uses_simple = bool(simple.search(s2))
            # Same-package users lose visibility when the class leaves its package.
            if pkg == old_pkg and pkg != new_pkg and uses_simple and f"import {new};" not in s2:
                own = os.path.basename(f)[:-5]
                if own not in (old_simple, new_simple):
                    s2 = re.sub(r"^(package\s+[\w.]+;\n)", r"\1\nimport " + new + ";\n", s2, count=1, flags=re.M)
            # A rename also renames the simple name where the class is visible.
            if old_simple != new_simple and (f"import {new};" in s2 or pkg in (old_pkg, new_pkg)):
                s2 = simple.sub(new_simple, s2)
        elif old_simple != new_simple and new in s2:
            s2 = simple.sub(new_simple, s2)
        contents[f] = s2

# A moved class loses visibility of the classes that stayed in its old package: import the ones it uses.
for dst, op, np in moved:
    if op == np or dst not in contents or contents[dst] is None:
        continue
    stayed = set()
    for r in ROOTS:
        d = os.path.join(r, op.replace(".", "/"))
        if os.path.isdir(d):
            stayed |= {n[:-5] for n in os.listdir(d) if n.endswith(".java")}
    s2 = contents[dst]
    for cls in sorted(stayed):
        if re.search(r"(?<![\w.$])" + re.escape(cls) + r"(?!" + IDENT + r")", s2) and f"import {op}.{cls};" not in s2:
            s2 = re.sub(r"^(package\s+[\w.]+;\n)", r"\1\nimport " + op + "." + cls + ";\n", s2, count=1, flags=re.M)
    contents[dst] = s2

for f in all_files:
    s = contents[f]
    if s is not None and s != read(f):
        write(f, s)

for f in sorted(changed):
    print(f"edit {f}")

# 3. Leftovers worth a look.
for old, new in class_moves:
    if old.endswith(".*"):
        continue
    hits = [f for f, s in contents.items() if s and re.search(r"(?<![\w.$])" + re.escape(old) + r"(?!" + IDENT + r")", s)]
    for f in hits:
        warn(f"{old} still appears in {f}")
wild = [f for f, s in contents.items() if s and f.endswith(".java") and
        any(re.search(r"^import\s+" + re.escape(o[:-2] if o.endswith('.*') else o.rsplit('.', 1)[0]) + r"\.\*;", s, re.M)
            for o, _ in moves)]
for f in wild:
    warn(f"{f} has a wildcard import of a moved package; check it by hand")
print(("dry run: " if dry else "") + f"{len(changed)} files changed, {len(moves)} moves in {moves_file}")
PY
