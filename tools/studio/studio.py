#!/usr/bin/env python3
"""Citadel Studio: a small web page to watch and steer the citadel design swarm.

Python 3 standard library only. Reads the shared render service spool (~/.cache/t3craft-render),
serves its PNGs as they are (HTTP caching, no image processing), keeps feedback notes per agent in
~/.cache/t3craft-coord/feedback/<agent>.md and writes render requests to the spool inbox.

    python3 tools/studio/studio.py [--host 100.64.0.30] [--port 8765]

Started by the coordinator with tools/studio/start.sh. Docs: docs/tools/studio.md
"""
from __future__ import annotations

import argparse
import email.utils
import json
import mimetypes
import os
import re
import sys
import threading
import time
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, unquote

HOME = Path.home()
RENDER_HOME = Path(os.environ.get("T3CRAFT_RENDER_HOME", HOME / ".cache" / "t3craft-render"))
COORD_HOME = Path(os.environ.get("T3CRAFT_COORD_HOME", HOME / ".cache" / "t3craft-coord"))
WORKTREES = Path(os.environ.get("T3CRAFT_WORKTREES", HOME / ".t3" / "worktrees" / "t3craft"))
BASELINE_WORKTREE = WORKTREES / "render-baseline"
STATIC = Path(__file__).resolve().parent / "static"

OUTBOX = RENDER_HOME / "outbox"
INBOX = RENDER_HOME / "inbox"
DAEMON_JSON = RENDER_HOME / "daemon.json"
AGENTS_JSON = COORD_HOME / "studio-agents.json"
FEEDBACK_DIR = COORD_HOME / "feedback"

AGENT_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")
JOB_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$")
FILE_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,199}\.(png|txt|json|log)$")
LAYOUT_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,63}$")
PROJECTS_RE = re.compile(r"^\d{1,3}(,\d{1,3}){0,15}$")
NAME_RE = re.compile(r"^[A-Za-z0-9_.-]{1,64}$")
REGION_RE = re.compile(r"^-?\d{1,5},-?\d{1,5},-?\d{1,5}:-?\d{1,5},-?\d{1,5},-?\d{1,5}$")
ONLY_RE = re.compile(r"^[A-Za-z0-9_]{1,32}(,[A-Za-z0-9_]{1,32}){0,11}$")
ACTIVE = ("queued", "compiling", "rendering")
STATIC_FILES = {"app.js", "app.css", "favicon.svg"}
MAX_BODY = 64 * 1024
MAX_NOTE = 8000

_feedback_lock = threading.Lock()
_request_lock = threading.Lock()
_job_cache: dict[str, tuple[tuple, dict]] = {}
_job_cache_lock = threading.Lock()


# ---------------------------------------------------------------- spool reading

def agent_of(job_id: str) -> str:
    return re.sub(r"-\d+$", "", job_id)


def read_json(path: Path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def iso_ms(value) -> int | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        v = value.replace("Z", "+00:00")
        # Java may print nanoseconds; datetime takes at most microseconds
        v = re.sub(r"(\.\d{6})\d+", r"\1", v)
        return int(datetime.fromisoformat(v).timestamp() * 1000)
    except ValueError:
        return None


def ms_field(st: dict, key: str) -> int | None:
    v = st.get(key)
    if isinstance(v, (int, float)):
        return int(v)
    return iso_ms(v)


def job_info(job_id: str) -> dict | None:
    """Summary of one outbox folder, cached on the folder's and status.json's mtimes."""
    d = OUTBOX / job_id
    try:
        dst = d.stat()
    except OSError:
        return None
    try:
        sst = (d / "status.json").stat()
        skey = (sst.st_mtime_ns, sst.st_size)
    except OSError:
        skey = None
    key = (dst.st_mtime_ns, skey)
    with _job_cache_lock:
        hit = _job_cache.get(job_id)
        if hit and hit[0] == key:
            return hit[1]
    st = read_json(d / "status.json") or {}
    req = read_json(d / "request.json") or {}
    try:
        names = sorted(os.listdir(d))
    except OSError:
        names = []
    pngs = [n for n in names if n.endswith(".png") and FILE_RE.match(n)]
    pngs.sort(key=lambda n: (n.startswith("compare_"), n))
    args = req.get("args") if isinstance(req.get("args"), list) else []
    extra = {k: v for k, v in req.items() if k not in ("args", "worktree", "kind", "priority")}
    info = {
        "id": job_id,
        "agent": agent_of(job_id),
        "state": st.get("state") or ("waiting" if not skey else "?"),
        "message": st.get("message", ""),
        "log": st.get("log", ""),
        "cached": bool(st.get("cached")),
        "position": st.get("position"),
        "priority": st.get("priority") or req.get("priority") or "preview",
        "kind": st.get("kind") or req.get("kind") or "hq",
        "worktree": req.get("worktree", ""),
        "args": [str(a) for a in args],
        "fields": extra,
        "queuedAt": ms_field(st, "queuedAt"),
        "startedAt": ms_field(st, "startedAt"),
        "finishedAt": ms_field(st, "finishedAt"),
        "mtime": int(dst.st_mtime * 1000),
        "images": pngs,
        "report": "report.txt" in names,
        "files": [n for n in names if FILE_RE.match(n) and not n.endswith(".png")],
    }
    info["when"] = info["finishedAt"] or info["startedAt"] or info["queuedAt"] or info["mtime"]
    with _job_cache_lock:
        _job_cache[job_id] = (key, info)
    return info


def all_jobs() -> list[dict]:
    try:
        ids = [n for n in os.listdir(OUTBOX) if JOB_RE.match(n)]
    except OSError:
        ids = []
    with _job_cache_lock:
        for gone in set(_job_cache) - set(ids):
            del _job_cache[gone]
    jobs = [j for j in (job_info(i) for i in ids) if j]
    jobs.sort(key=lambda j: j["when"] or 0, reverse=True)
    return jobs


def running_jobs(d: dict) -> list[dict]:
    """Every job in progress: `running` is an array of objects (several workers); older daemons wrote one id string."""
    r = d.get("running")
    out: list[dict] = []
    if isinstance(r, list):
        for e in r:
            if isinstance(e, dict) and isinstance(e.get("id"), str):
                out.append({k: e.get(k) for k in ("id", "agent", "kind", "priority", "state", "message", "startedAt")})
            elif isinstance(e, str) and e:
                out.append({"id": e})
    elif isinstance(r, str) and r:
        out.append({"id": r})
    elif isinstance(d.get("runningId"), str) and d["runningId"]:
        out.append({"id": d["runningId"]})
    return out


def daemon_state() -> dict:
    d = read_json(DAEMON_JSON) or {}
    now = int(time.time() * 1000)
    beat = d.get("nowMillis") if isinstance(d.get("nowMillis"), (int, float)) else None
    alive = d.get("state") == "running" and beat is not None and now - beat < 30000
    return {
        "state": d.get("state", "missing") if alive or not d else "stopped",
        "alive": alive,
        "queued": d.get("queued"),
        "running": running_jobs(d),
        "workers": d.get("workers"),
        "loadPaused": bool(d.get("loadPaused")),
        "served": d.get("served"),
        "load1": d.get("load1"),
        "loadLimit": d.get("loadLimit"),
        "baseline": (d.get("baseline") or "")[:10],
        "beat": beat,
    }


def fixed_agents() -> dict[str, dict]:
    data = read_json(AGENTS_JSON)
    out: dict[str, dict] = {}
    if isinstance(data, list):
        for e in data:
            if isinstance(e, dict) and isinstance(e.get("agent"), str) and AGENT_RE.match(e["agent"]):
                out[e["agent"]] = e
    return out


def derive_title(agent: str) -> tuple[str, str]:
    """citadel-2-roofs-yards -> ("Roofs yards", "fase 2")."""
    rest = agent[len("citadel-"):] if agent.startswith("citadel-") else agent
    parts = [p for p in rest.split("-") if p]
    phase = ""
    if parts and parts[0].isdigit():
        phase = "fase " + parts.pop(0)
    title = " ".join(parts) or agent
    return title[:1].upper() + title[1:], phase


def agent_meta(agent: str, fixed: dict[str, dict] | None = None) -> dict:
    fixed = fixed_agents() if fixed is None else fixed
    e = fixed.get(agent, {})
    title, phase = derive_title(agent)
    worktree = e.get("worktree") if isinstance(e.get("worktree"), str) else ""
    if not worktree and (WORKTREES / agent).is_dir():
        worktree = str(WORKTREES / agent)
    brief = e.get("brief") if isinstance(e.get("brief"), str) else ""
    return {
        "agent": agent,
        "title": e.get("title") if isinstance(e.get("title"), str) and e.get("title") else title,
        "phase": phase,
        "brief": bool(brief and safe_brief(brief)),
        "worktree": worktree,
        "fixed": agent in fixed,
    }


def safe_brief(path: str) -> Path | None:
    try:
        p = Path(path).resolve(strict=True)
    except (OSError, RuntimeError):
        return None
    if p.suffix == ".md" and p.is_file() and p.is_relative_to(COORD_HOME.resolve()):
        return p
    return None


def state_payload() -> dict:
    jobs = all_jobs()
    fixed = fixed_agents()
    by_agent: dict[str, list[dict]] = {}
    for j in jobs:
        by_agent.setdefault(j["agent"], []).append(j)
    # every agent with jobs in the outbox (design-*, t3-*, prueba-*, citadel-*, facundo-*...), plus the fixed list
    names = list(by_agent)
    names += [a for a in fixed if a not in names]
    cards = []
    for a in names:
        js = by_agent.get(a, [])
        last = js[0] if js else None
        with_img = next((j for j in js if j["images"]), None)
        fb = feedback_summary(a)
        cards.append({
            **agent_meta(a, fixed),
            "jobs": len(js),
            "renders": sum(len(j["images"]) for j in js),
            "last": slim(last) if last else None,
            "lastImages": {"id": with_img["id"], "images": with_img["images"][:6]} if with_img else None,
            "feedback": fb,
        })
    # most recent activity first; agents with no job yet (fixed list only) at the end
    latest = {a: (js[0]["when"] or 0) for a, js in by_agent.items() if js}
    cards.sort(key=lambda c: (-latest.get(c["agent"], 0), c["agent"]))
    return {"daemon": daemon_state(), "agents": cards, "now": int(time.time() * 1000)}


def slim(j: dict) -> dict:
    return {k: j[k] for k in ("id", "state", "message", "queuedAt", "startedAt", "finishedAt", "when", "priority", "position", "cached")}


# ---------------------------------------------------------------- feedback

NOTE_HEAD = re.compile(r"^### note (\S+)\s*$")
READ_RE = re.compile(r"^<!-- read: (.*?) -->\s*$")


def feedback_path(agent: str) -> Path:
    return FEEDBACK_DIR / (agent + ".md")


def parse_feedback(agent: str) -> list[dict]:
    """Notes in file order; a note is read when a `<!-- read: ... -->` line comes after it."""
    try:
        text = feedback_path(agent).read_text(encoding="utf-8")
    except OSError:
        return []
    notes: list[dict] = []
    cur: dict | None = None
    for line in text.splitlines():
        m = NOTE_HEAD.match(line)
        if m:
            cur = {"at": m.group(1), "ref": "", "text": [], "read": None}
            notes.append(cur)
            continue
        r = READ_RE.match(line)
        if r:
            for n in notes:
                if n["read"] is None:
                    n["read"] = r.group(1).strip() or "?"
            cur = None
            continue
        if cur is None:
            continue
        if line.startswith("ref: ") and not cur["text"] and not cur["ref"]:
            cur["ref"] = line[5:].strip().strip("`")
            continue
        cur["text"].append(line)
    for n in notes:
        n["text"] = "\n".join(n["text"]).strip()
        n["refUrl"] = ref_url(n["ref"])
    return notes


def feedback_summary(agent: str) -> dict:
    notes = parse_feedback(agent)
    return {"total": len(notes), "unread": sum(1 for n in notes if not n["read"])}


def ref_url(ref: str) -> str:
    p = outbox_file_from_path(ref) if ref else None
    if not p:
        return ""
    return "/files/%s/%s" % (p.parent.name, p.name)


def outbox_file_from_path(path: str) -> Path | None:
    try:
        p = Path(path).resolve(strict=True)
    except (OSError, RuntimeError):
        return None
    if p.parent.parent == OUTBOX.resolve() and JOB_RE.match(p.parent.name) and FILE_RE.match(p.name) and p.suffix == ".png":
        return p
    return None


def add_feedback(agent: str, text: str, ref: str) -> dict:
    text = text.replace("\r\n", "\n").replace("\r", "\n").strip()
    if not text:
        raise ValueError("la nota está vacía")
    if len(text) > MAX_NOTE:
        raise ValueError("la nota es demasiado larga (máx. %d caracteres)" % MAX_NOTE)
    ref_path = ""
    if ref:
        # accept a /files/<id>/<name> URL or an absolute path inside the outbox
        m = re.match(r"^/files/([^/]+)/([^/]+)$", ref)
        cand = str(OUTBOX / m.group(1) / m.group(2)) if m and JOB_RE.match(m.group(1)) and FILE_RE.match(m.group(2)) else ref
        p = outbox_file_from_path(cand)
        if not p:
            raise ValueError("la imagen de referencia no está en el outbox")
        ref_path = str(p)
    # a line that looks like our markers would confuse the parser: indent it
    lines = []
    for line in text.split("\n"):
        if NOTE_HEAD.match(line) or READ_RE.match(line) or line.startswith("<!--"):
            line = "    " + line
        lines.append(line)
    stamp = datetime.now().astimezone().isoformat(timespec="seconds")
    block = "### note %s\n" % stamp
    if ref_path:
        block += "ref: `%s`\n" % ref_path
    block += "\n" + "\n".join(lines) + "\n\n"
    path = feedback_path(agent)
    with _feedback_lock:
        FEEDBACK_DIR.mkdir(parents=True, exist_ok=True)
        new = not path.exists()
        with open(path, "a", encoding="utf-8") as f:
            if new:
                f.write("# Feedback for %s\n\n"
                        "Notes from Facundo (Citadel Studio). Read them all; when done, append a line\n"
                        "`<!-- read: <ISO timestamp> -->` at the end of this file. Protocol: docs/tools/studio.md\n\n" % agent)
            f.write(block)
    return {"at": stamp, "ref": ref_path}


# ---------------------------------------------------------------- render requests

def build_request(body: dict) -> tuple[str, dict]:
    def s(key: str, limit: int = 200) -> str:
        v = body.get(key, "")
        if v is None:
            return ""
        if not isinstance(v, (str, int, float)):
            raise ValueError("campo %s inválido" % key)
        v = str(v).strip()
        if len(v) > limit:
            raise ValueError("campo %s demasiado largo" % key)
        return v

    for_agent = s("for", 80)
    if for_agent and not AGENT_RE.match(for_agent):
        raise ValueError("agente inválido")
    if for_agent.startswith("facundo-"):
        req_agent = for_agent
    else:
        base = for_agent[len("citadel-"):] if for_agent.startswith("citadel-") else (for_agent or "studio")
        req_agent = "facundo-" + (re.sub(r"-\d+$", "", base)[:40] or "studio")
    if not AGENT_RE.match(req_agent):
        raise ValueError("agente inválido")

    layout = s("layout", 64) or "hybrid-ringcampus"
    if not LAYOUT_RE.match(layout):
        raise ValueError("layout inválido")
    args = ["--layout", layout]
    projects = s("projects", 80).replace(" ", "")
    if projects:
        if not PROJECTS_RE.match(projects):
            raise ValueError("proyectos: números separados por coma, p. ej. 4,10,20")
        args += ["--projects", projects]

    target = s("target", 16) or "all"
    report = bool(body.get("report"))
    if target == "group":
        g = s("group", 64)
        if not NAME_RE.match(g):
            raise ValueError("grupo inválido")
        args += ["--group", g]
    elif target == "anchor":
        a = s("anchor", 64)
        if not NAME_RE.match(a):
            raise ValueError("anchor inválido")
        try:
            radius = int(s("radius", 4) or "12")
        except ValueError:
            raise ValueError("radio inválido")
        if not 1 <= radius <= 256:
            raise ValueError("radio fuera de rango (1–256)")
        args += ["--anchor", a, "--radius", str(radius)]
    elif target == "region":
        r = s("region", 80).replace(" ", "")
        if not REGION_RE.match(r):
            raise ValueError("región: x1,y1,z1:x2,y2,z2")
        args += ["--region", r]
    elif target == "all":
        report = True
    else:
        raise ValueError("objetivo inválido")

    only = s("only", 200).replace(" ", "")
    if only:
        if not ONLY_RE.match(only):
            raise ValueError("vistas: nombres separados por coma, p. ej. iso_se,plan")
        args += ["--only", only]
    name = s("name", 64)
    if name:
        if not NAME_RE.match(name):
            raise ValueError("nombre inválido")
        args += ["--name", name]
    if report:
        args.append("--report")

    priority = s("priority", 16) or "preview"
    if priority not in ("preview", "final"):
        raise ValueError("calidad inválida")

    wt = s("worktree", 400)
    if not wt:
        wt = agent_meta(for_agent)["worktree"] if for_agent else ""
        wt = wt or str(BASELINE_WORKTREE)
    try:
        wtp = Path(wt).resolve(strict=True)
    except (OSError, RuntimeError):
        raise ValueError("el worktree no existe")
    if not wtp.is_dir() or not wtp.is_relative_to(WORKTREES.resolve()) or wtp == WORKTREES.resolve():
        raise ValueError("el worktree debe estar dentro de %s" % WORKTREES)

    return req_agent, {"worktree": str(wtp), "kind": "hq", "priority": priority, "args": args}


def pending_for(req_agent: str) -> str | None:
    try:
        for n in os.listdir(INBOX):
            stem = n[1:] if n.startswith(".") else n
            stem = stem.removesuffix(".tmp").removesuffix(".json")
            if agent_of(stem) == req_agent and not n.endswith(".rejected"):
                return stem
    except OSError:
        pass
    for j in all_jobs():
        if j["agent"] == req_agent and j["state"] in ACTIVE:
            return j["id"]
    return None


def submit_request(body: dict) -> dict:
    req_agent, req = build_request(body)
    if not daemon_state()["alive"]:
        raise ValueError("el servicio de render no está corriendo (pídele al coordinador que lo arranque)")
    with _request_lock:
        other = pending_for(req_agent)
        if other:
            raise ValueError("ya hay una petición pendiente de %s (%s); espera a que termine" % (req_agent, other))
        INBOX.mkdir(parents=True, exist_ok=True)
        job_id = "%s-%d" % (req_agent, int(time.time() * 1000))
        tmp = INBOX / (".%s.tmp" % job_id)
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(req, f, indent=1)
        os.replace(tmp, INBOX / (job_id + ".json"))
    return {"id": job_id, "agent": req_agent, "request": req}


# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "CitadelStudio/1"
    protocol_version = "HTTP/1.1"
    timeout = 30  # idle keep-alive connections give their thread back

    def log_message(self, fmt, *args):
        if os.environ.get("STUDIO_QUIET"):
            return
        sys.stderr.write("%s %s\n" % (self.log_date_time_string(), fmt % args))

    # -- responses

    def common_headers(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "DENY")

    def send_bytes(self, code: int, data: bytes, ctype: str, extra: dict | None = None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.common_headers()
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def send_json(self, obj, code: int = 200):
        data = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_bytes(code, data, "application/json; charset=utf-8", {"Cache-Control": "no-store"})

    def send_error_json(self, code: int, message: str):
        self.send_json({"error": message}, code)

    def send_file(self, path: Path, ctype: str | None = None, cache: str = "private, max-age=60"):
        try:
            st = path.stat()
        except OSError:
            return self.send_error_json(404, "no existe")
        etag = '"%x-%x"' % (st.st_mtime_ns, st.st_size)
        last = email.utils.formatdate(st.st_mtime, usegmt=True)
        headers = {"ETag": etag, "Last-Modified": last, "Cache-Control": cache}
        inm = self.headers.get("If-None-Match")
        ims = self.headers.get("If-Modified-Since")
        fresh = False
        if inm is not None:
            fresh = etag in [t.strip() for t in inm.split(",")] or inm.strip() == "*"
        elif ims:
            try:
                fresh = int(st.st_mtime) <= email.utils.parsedate_to_datetime(ims).timestamp()
            except (TypeError, ValueError):
                fresh = False
        if fresh:
            self.send_response(304)
            self.common_headers()
            for k, v in headers.items():
                self.send_header(k, v)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        ctype = ctype or mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        if ctype.startswith("text/") and "charset" not in ctype:
            ctype += "; charset=utf-8"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(st.st_size))
        self.common_headers()
        for k, v in headers.items():
            self.send_header(k, v)
        self.end_headers()
        if self.command == "HEAD":
            return
        with open(path, "rb") as f:
            while True:
                chunk = f.read(256 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)

    def end_headers(self):
        if getattr(self, "_page", False):
            self.send_header("Content-Security-Policy",
                             "default-src 'self'; img-src 'self' data:; script-src 'self'; connect-src 'self'; "
                             "style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; "
                             "frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        super().end_headers()

    # -- routing

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        try:
            self.route_get()
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:  # never kill the thread silently
            self.log_message("error on %s: %r", self.path, e)
            try:
                self.send_error_json(500, "error interno")
            except OSError:
                pass

    def route_get(self):
        self._page = False
        path = unquote(urlsplit(self.path).path)
        if path in ("/", "/render") or path.startswith("/agent/"):
            if path.startswith("/agent/") and not AGENT_RE.match(path[len("/agent/"):]):
                return self.send_error_json(404, "agente inválido")
            self._page = True
            return self.send_file(STATIC / "index.html", "text/html; charset=utf-8", "no-cache")
        if path.startswith("/static/"):
            name = path[len("/static/"):]
            if name not in STATIC_FILES:
                return self.send_error_json(404, "no existe")
            return self.send_file(STATIC / name, cache="no-cache")
        if path == "/favicon.ico":
            return self.send_file(STATIC / "favicon.svg", "image/svg+xml", "private, max-age=86400")
        if path == "/api/state":
            return self.send_json(state_payload())
        if path.startswith("/api/agent/"):
            agent = path[len("/api/agent/"):]
            if not AGENT_RE.match(agent):
                return self.send_error_json(404, "agente inválido")
            jobs = [j for j in all_jobs() if j["agent"] == agent]
            return self.send_json({
                **agent_meta(agent),
                "jobs": jobs,
                "notes": parse_feedback(agent),
                "daemon": daemon_state(),
                "baselineWorktree": str(BASELINE_WORKTREE),
                "now": int(time.time() * 1000),
            })
        if path.startswith("/api/job/"):
            job_id = path[len("/api/job/"):]
            if not JOB_RE.match(job_id):
                return self.send_error_json(404, "job inválido")
            info = job_info(job_id)
            if not info:
                pending = (INBOX / (job_id + ".json")).exists()
                return self.send_json({"id": job_id, "state": "waiting" if pending else "missing",
                                       "message": "el servicio aún no tomó la petición" if pending else "no existe",
                                       "images": [], "args": []})
            return self.send_json(info)
        if path.startswith("/files/"):
            parts = path[len("/files/"):].split("/")
            if len(parts) != 2 or not JOB_RE.match(parts[0]) or not FILE_RE.match(parts[1]):
                return self.send_error_json(404, "no existe")
            p = (OUTBOX / parts[0] / parts[1])
            try:
                real = p.resolve(strict=True)
            except (OSError, RuntimeError):
                return self.send_error_json(404, "no existe")
            if real.parent.parent != OUTBOX.resolve() or not real.is_file():
                return self.send_error_json(404, "no existe")
            ctype = "image/png" if real.suffix == ".png" else ("application/json" if real.suffix == ".json" else "text/plain; charset=utf-8")
            return self.send_file(real, ctype, "private, max-age=300")
        if path.startswith("/brief/"):
            agent = path[len("/brief/"):]
            if not AGENT_RE.match(agent):
                return self.send_error_json(404, "agente inválido")
            e = fixed_agents().get(agent, {})
            p = safe_brief(e.get("brief", "")) if isinstance(e.get("brief"), str) else None
            if not p:
                return self.send_error_json(404, "sin brief")
            return self.send_file(p, "text/plain; charset=utf-8", "no-cache")
        if path.startswith("/feedback/"):
            agent = path[len("/feedback/"):].removesuffix(".md")
            if not AGENT_RE.match(agent) or not feedback_path(agent).is_file():
                return self.send_error_json(404, "sin notas")
            return self.send_file(feedback_path(agent), "text/plain; charset=utf-8", "no-cache")
        return self.send_error_json(404, "no existe")

    def do_POST(self):
        try:
            self.route_post()
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:
            self.log_message("error on POST %s: %r", self.path, e)
            try:
                self.send_error_json(500, "error interno")
            except OSError:
                pass

    def route_post(self):
        self._page = False
        self.close_connection = True  # an early refusal leaves the body unread
        if self.headers.get("X-Studio") != "1":
            return self.send_error_json(403, "falta el header X-Studio")
        ctype = (self.headers.get("Content-Type") or "").split(";")[0].strip()
        if ctype != "application/json":
            return self.send_error_json(415, "se espera application/json")
        try:
            length = int(self.headers.get("Content-Length") or "0")
        except ValueError:
            return self.send_error_json(400, "Content-Length inválido")
        if length <= 0 or length > MAX_BODY:
            return self.send_error_json(413, "cuerpo vacío o demasiado grande")
        raw = self.rfile.read(length)
        try:
            body = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, ValueError):
            return self.send_error_json(400, "JSON inválido")
        if not isinstance(body, dict):
            return self.send_error_json(400, "se espera un objeto JSON")
        path = urlsplit(self.path).path
        try:
            if path == "/api/feedback":
                agent = body.get("agent")
                if not isinstance(agent, str) or not AGENT_RE.match(agent):
                    return self.send_error_json(400, "agente inválido")
                text = body.get("text")
                ref = body.get("ref") or ""
                if not isinstance(text, str) or not isinstance(ref, str):
                    return self.send_error_json(400, "campos inválidos")
                return self.send_json({"ok": True, **add_feedback(agent, text, ref)})
            if path == "/api/render":
                return self.send_json({"ok": True, **submit_request(body)})
        except ValueError as e:
            return self.send_error_json(400, str(e))
        return self.send_error_json(404, "no existe")


def main():
    ap = argparse.ArgumentParser(description="Citadel Studio (docs/tools/studio.md)")
    ap.add_argument("--host", default="100.64.0.30", help="IP to listen on (the Tailscale one; never 0.0.0.0)")
    ap.add_argument("--port", type=int, default=8765)
    a = ap.parse_args()
    if a.host in ("0.0.0.0", "::", ""):
        ap.error("refusing to listen on every interface; pass the Tailscale IP")
    srv = ThreadingHTTPServer((a.host, a.port), Handler)
    srv.daemon_threads = True
    print("Citadel Studio on http://%s:%d/  (spool %s)" % (a.host, a.port, RENDER_HOME), flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()


if __name__ == "__main__":
    main()
