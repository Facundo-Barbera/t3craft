#!/usr/bin/env python3
"""kit/decision-podium: generates the decision podium's display textures (+ .mcmeta), the brass texture, the six
per-state block models and the blockstate (facing x status).

Standard library only (zlib/struct), light enough for the VPS. Run from the repo root:

    python3 tools/art/decision_podium_art.py                 # writes assets
    python3 tools/art/decision_podium_art.py --preview DIR   # also writes magnified PNGs (textures + three views) to DIR

The idea (a podium where the agent that needs a decision waits, with a bell and a "!") comes from AgentCraft's decision
podium (MIT); nothing is copied: the model, the pixels and this generator are ours.

Design (a thread's state has to read at a glance and from afar: colour + glyph + blink, like status_lamp_art.py):
  * body: a lectern. Stone plinth, dark bark shaft, cherry desk, an upright display panel at the back. The body reuses
    the kit textures (kit_stone, kit_bark, kit_cherry_wood), so it matches the rest of the HQ.
  * display: 14 x 6 px, the colours are LampStatus' (docs/features/blocks.md). Only the display and the ring marks glow.
      off        dark glass (no glow)                      idle     dim gray, pause bars
      working    cyan bar that runs across (4 frames)      done     green, check mark
      attention  orange, three "!" that blink slowly       error    red, X that blinks fast
  * a brass bell stands on the desk, left of the display. When the thread needs the player (attention, error) two glowing
    ring marks flank the bell: the silhouette says "the bell is ringing" even when the glyph is not in view.
  * texture layout (16 x 16 per frame): the whole frame is the plain lens colour (ring marks sample its top rows), the display
    is drawn at x 1..14, y 5..10, and the model's front face of the panel crops exactly that. Frame 0 is the "lit" one.
"""
import json
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "mod", "src", "main", "resources", "assets", "t3craft")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")
STATES_DIR = os.path.join(ROOT, "blockstates")


# ---------------------------------------------------------------- png
def write_png(path, rows):
    """rows: list of rows of (r, g, b, a)."""
    h = len(rows)
    w = len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px_ in row for c in px_) for row in rows)

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def read_png(path):
    """Reads an 8-bit RGB/RGBA PNG (the kit textures) as rows of (r, g, b, a)."""
    d = open(path, "rb").read()
    i = 8
    idat = b""
    w = h = ct = 0
    while i < len(d):
        n = struct.unpack(">I", d[i:i + 4])[0]
        t = d[i + 4:i + 8]
        c = d[i + 8:i + 8 + n]
        if t == b"IHDR":
            w, h, _, ct = struct.unpack(">IIBB", c[:10])
        elif t == b"IDAT":
            idat += c
        i += 12 + n
    raw = zlib.decompress(idat)
    bpp = 4 if ct == 6 else 3
    rows = []
    prev = bytearray(w * bpp)
    pos = 0
    for _ in range(h):
        f = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + w * bpp])
        pos += w * bpp
        for x in range(len(line)):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c_ = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c_
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c_)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else (b if pb <= pc else c_))) & 255
        rows.append([tuple(line[x * bpp:x * bpp + bpp]) + ((255,) if bpp == 3 else ()) for x in range(w)])
        prev = line
    return rows


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def lighten(c, t):
    return tuple(int(round(v + (255 - v) * t)) for v in c)


def darken(c, f):
    return tuple(int(round(v * f)) for v in c)


def px(c):
    return (c[0], c[1], c[2], 255)


# ---------------------------------------------------------------- glyphs
GLYPHS = {
    "bang": ["##", "##", "##", "..", "##"],
    "check": ["....##", "...##.", "##.##.", ".###..", "..#..."],
    "x": ["##..##", ".####.", "..##..", "..##..", ".####.", "##..##"],
    "pause": ["##..##", "##..##", "##..##", "##..##", "##..##"],
}

# name: (colour, frames, frametime)  (colours are LampStatus')
STATES = {
    "off": (0x2A2A2A, 1, 0),
    "idle": (0xAAAAAA, 1, 0),
    "working": (0x55FFFF, 4, 3),
    "attention": (0xFFAA00, 2, 10),
    "error": (0xFF5555, 2, 4),
    "done": (0x55FF55, 1, 0),
}
EMIT = {"off": 0, "idle": 6, "working": 12, "attention": 15, "error": 15, "done": 12}
# the panel on the texture: x 1..14, y 5..10 (14 x 6)
PX0, PY0, PW, PH = 1, 5, 14, 6


def stamp(img, glyph, ox, oy, col):
    for j, row in enumerate(GLYPHS[glyph]):
        for i, ch in enumerate(row):
            if ch == "#":
                img[PY0 + oy + j][PX0 + ox + i] = px(col)


def panel_frame(name, f):
    """One 16 x 16 frame: the plain lens colour everywhere, the display drawn in its 14 x 6 window."""
    col, n, _ = STATES[name]
    c = rgb(col)
    lit = not (name in ("attention", "error") and f == 1)  # the dim half of the blink
    if name == "off":
        fill = rgb(0x1B2026)
    elif name == "working":
        fill = darken(c, 0.34)
    elif name == "idle":
        fill = darken(c, 0.42)
    else:
        fill = c if lit else darken(c, 0.30)
    plain = fill if name not in ("working", "idle") else c if name == "working" else rgb(0x8A8A8A)
    img = [[px(plain)] * 16 for _ in range(16)]
    if name == "off":
        img = [[px(rgb(0x22303A))] * 16 for _ in range(16)]
    # the display: darker 1 px edge, a lighter top row (glass), the fill
    edge = darken(fill, 0.62) if name != "off" else rgb(0x0F1318)
    for y in range(PH):
        for x in range(PW):
            e = x in (0, PW - 1) or y == PH - 1
            c_ = edge if e else (lighten(fill, 0.22) if y == 0 and name != "off" else fill)
            img[PY0 + y][PX0 + x] = px(c_)
    glyph_col = darken(c, 0.30 if name != "attention" else 0.14) if lit else lighten(c, 0.12)
    if name == "off":  # dead glass: a faint diagonal reflection
        for k in range(4):
            img[PY0 + 1 + k][PX0 + 3 + k] = px(rgb(0x4A4A4A))
            img[PY0 + 1 + k][PX0 + 4 + k] = px(rgb(0x3A4148))
    elif name == "attention":  # three "!": an alarm
        for ox in (2, 6, 10):
            stamp(img, "bang", ox, 0, glyph_col)
    elif name == "error":
        stamp(img, "x", 4, 0, glyph_col)
    elif name == "done":
        stamp(img, "check", 4, 0, glyph_col)
    elif name == "idle":
        stamp(img, "pause", 4, 0, rgb(0xCFCFCF))
    elif name == "working":
        # a bar of 4 lit columns running left to right, wrapping; the track is dim
        for y in (2, 3):
            for k in range(4):
                x = 1 + (f * 3 + k) % (PW - 2)
                img[PY0 + y][PX0 + x] = px(lighten(c, 0.30) if k in (1, 2) else c)
    return img


def write_textures():
    for name, (col, n, ft) in STATES.items():
        rows = []
        for f in range(n):
            rows += panel_frame(name, f)
        write_png(os.path.join(TEX, "decision_podium_%s.png" % name), rows)
        meta = os.path.join(TEX, "decision_podium_%s.png.mcmeta" % name)
        if n > 1:
            with open(meta, "w") as fh:
                json.dump({"animation": {"frametime": ft}}, fh, indent=2)
                fh.write("\n")
        elif os.path.exists(meta):
            os.remove(meta)
    # brass: quadrant (0..7, 0..7) the bell's body, (8..15, 0..7) the dark handle and clapper, (0..7, 8..15) a cream paper
    brass = rgb(0xDDA821)
    steel = rgb(0x3A3F46)
    paper = rgb(0xE8DDB8)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y < 8 and x < 8:
                c = brass
                if y == 0:
                    c = lighten(brass, 0.40)
                elif y == 7:
                    c = darken(brass, 0.62)
                elif x == 0 or x == 7:
                    c = darken(brass, 0.82)
                elif (x, y) in ((2, 2), (3, 2), (2, 3)):
                    c = lighten(brass, 0.55)
            elif y < 8:
                c = steel if (x + y) % 5 else lighten(steel, 0.15)
            elif x < 8:
                c = paper
                if y == 8 or x == 0:
                    c = lighten(paper, 0.4)
                elif y == 15 or x == 7:
                    c = darken(paper, 0.82)
                elif y in (10, 12, 14) and 1 <= x <= 5:
                    c = darken(paper, 0.55)  # lines of text
            else:
                # (8..15, 8..15): the engraved plaque on the shaft: bright rim, darker field, two engraved lines
                c = darken(brass, 0.78)
                if x == 8 or y == 8:
                    c = lighten(brass, 0.45)
                elif x == 15 or y == 15:
                    c = darken(brass, 0.55)
                elif y in (10, 12) and 10 <= x <= 13:
                    c = darken(brass, 0.45)
            row.append(px(c))
        rows.append(row)
    write_png(os.path.join(TEX, "decision_podium_brass.png"), rows)


# ---------------------------------------------------------------- models
FACES = ("north", "south", "east", "west", "up", "down")


def box(frm, to, tex, uv_side=None, uv_top=None, emit=0, skip=(), uv_front=None):
    """A box whose six faces use texture variable `tex`. uv_side/uv_top: [u0, v0, u1, v1]; uv_front overrides the north face."""
    faces = {}
    for fc in FACES:
        if fc in skip:
            continue
        uv = uv_top if fc in ("up", "down") else uv_side
        if fc == "north" and uv_front is not None:
            uv = uv_front
        f = {"texture": tex}
        if uv is not None:
            f["uv"] = list(uv)
        faces[fc] = f
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if emit:
        e["light_emission"] = emit
    return e


def model(name):
    """A lectern, not a pot: a stepped plinth, a slim shaft with a collar and a brass plaque, a desk that slopes up toward the
    display (-22.5 degrees, like the console's keyboard), a slip and a big brass bell on it, and a framed display on top. The
    silhouette is the same in every state, so the lectern reads as a lectern even with the display off. Everything stays inside
    DecisionPodiumBlock's hitbox (plinth 1-15 x 0-2, shaft 4-12 x 2-8, desk 1-15 x 8-10, panel 1-15 x 10-16 x z 8-14)."""
    emit = EMIT[name]
    slope = {"angle": -22.5, "axis": "x", "origin": [8, 9, 5.5]}
    els = [
        # plinth: two steps
        box([1, 0, 1], [15, 1, 15], "#stone", [0, 0, 14, 1], [1, 1, 15, 15]),
        box([2.5, 1, 2.5], [13.5, 2, 13.5], "#stone", [1, 0, 12, 1], [2, 2, 14, 14]),
        # shaft: slim, with a cherry collar under the desk and a brass plaque on the front
        box([4.5, 2, 5.5], [11.5, 7, 12.5], "#wood", [0, 0, 7, 5], [4, 5, 12, 13]),
        box([4, 2, 5], [12, 3, 13], "#bark", [0, 0, 8, 1], [4, 5, 12, 13]),   # dark foot band
        box([3.6, 6.4, 4.6], [12.4, 8, 13.4], "#bark", [0, 0, 8, 1.6], [4, 5, 12, 13]),   # dark collar under the desk
        {"from": [5.5, 3.6, 5.2], "to": [10.5, 6.2, 5.5], "faces": {"north": {"texture": "#brass", "uv": [8, 8, 13, 13]}}},
        # under the sloped desk: a filler so there is no daylight between the shaft and the slope
        box([2, 8, 4], [14, 9, 13], "#wood", [0, 0, 12, 1], [2, 4, 14, 13]),
        # the desk: a slab that slopes up toward the display
        {**box([1, 8.6, 2], [15, 9.6, 9], "#wood", [0, 0, 14, 1], [1, 2, 15, 9]), "rotation": slope},
        # a cream permission slip lying on the slope, right of the display's centre
        {**box([9, 9.6, 3.5], [13, 9.75, 7.5], "#brass", [0, 8, 4, 8.15], [0, 8, 4, 12]), "rotation": slope},
        # the bell, kept to the left edge so it does not stand in front of the glyphs: base ring, dome, knob, clapper
        box([1, 9, 3], [4.6, 9.8, 6.6], "#brass", [0, 5, 4, 5.8], [0, 0, 4, 4]),
        box([1.4, 9.8, 3.4], [4.2, 12, 6.2], "#brass", [0, 0, 3, 2.2], [0, 0, 3, 3]),
        box([2.2, 12, 4.2], [3.4, 12.8, 5.4], "#brass", [8, 0, 9, 0.8], [8, 0, 9, 1]),
        box([2.4, 9.6, 4.4], [3.2, 10.4, 5.2], "#brass", [8, 2, 9, 3], [8, 2, 9, 3], skip=("up",)),
        # a dark lip along the low edge of the sloped desk (the reading rail)
        {**box([1, 8.6, 1.5], [15, 9.9, 2.2], "#bark", [0, 0, 14, 1.3], [1, 1, 15, 2]), "rotation": slope},
    ]
    # the display panel: the front face crops the 14 x 6 window of the lens texture, the rest is cherry
    panel = box([1, 10, 8], [15, 16, 14], "#wood", [0, 0, 14, 6], [1, 2, 15, 8])
    panel["faces"]["north"] = {"texture": "#lens", "uv": [PX0, PY0, PX0 + PW, PY0 + PH]}
    if emit:
        panel["light_emission"] = emit
    els.append(panel)
    # brass frame around the display: it only covers the window's own 1 px edge, so no glyph is hidden
    for frm, to, uv in (
        ([0.7, 10, 7.4], [1.7, 16, 8], [0, 0, 1, 6]),       # left
        ([14.3, 10, 7.4], [15.3, 16, 8], [0, 0, 1, 6]),     # right
        ([0.7, 15.2, 7.4], [15.3, 16, 8], [0, 0, 14, 1]),   # top rail
        ([0.7, 10, 7.4], [15.3, 10.8, 8], [0, 0, 14, 1]),   # bottom rail
    ):
        els.append(box(frm, to, "#bark", [0, 0, 1, 1], [0, 0, 1, 1]))
    if name in ("attention", "error"):
        # ring marks: two glowing bars flank the bell (the bell is ringing), lens colour
        for (x0, x1) in ((0.2, 1.0), (5.0, 5.8)):
            els.append(box([x0, 11.5, 4.5], [x1, 14.5, 6], "#lens", [0, 0, 2, 2], [0, 0, 2, 2], emit=15))
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "stone": "t3craft:block/kit_stone",
            "bark": "t3craft:block/kit_bark",
            "wood": "t3craft:block/kit_cherry_wood",
            "brass": "t3craft:block/decision_podium_brass",
            "lens": "t3craft:block/decision_podium_%s" % name,
            "particle": "t3craft:block/kit_cherry_wood",
        },
        "elements": els,
    }


def write_models():
    for name in STATES:
        with open(os.path.join(MODELS, "decision_podium_%s.json" % name), "w") as f:
            json.dump(model(name), f, indent=2)
            f.write("\n")
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for name in STATES:
            v = {"model": "t3craft:block/decision_podium_%s" % name}
            if y:
                v["y"] = y
            variants["facing=%s,status=%s" % (facing, name)] = v
    with open(os.path.join(STATES_DIR, "decision_podium.json"), "w") as f:
        json.dump({"variants": variants}, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- preview (orthographic painter, standard library)
def load_textures(name):
    base = os.path.join(TEX)
    kit = {k: read_png(os.path.join(base, "kit_%s.png" % k)) for k in ("stone", "bark", "cherry_wood")}
    lens = read_png(os.path.join(base, "decision_podium_%s.png" % name))[:16]  # frame 0
    return {
        "t3craft:block/kit_stone": kit["stone"],
        "t3craft:block/kit_bark": kit["bark"],
        "t3craft:block/kit_cherry_wood": kit["cherry_wood"],
        "t3craft:block/decision_podium_brass": read_png(os.path.join(base, "decision_podium_brass.png")),
        "t3craft:block/decision_podium_%s" % name: lens,
    }


def sample(tex, u, v):
    """u, v in texture px (0..16)."""
    x = min(15, max(0, int(u)))
    y = min(15, max(0, int(v)))
    return tex[y][x]


def render_view(m, textures, view, scale=14):
    """view: 'front' (looking at the north faces, from -z), 'side' (east faces, from +x), 'top' (up faces, from above).
    Painter's algorithm: elements sorted far to near; each paints the face towards the viewer."""
    size = 16 * scale
    img = [[(236, 236, 240, 255)] * size for _ in range(size)]
    for hy in range(0, size, scale):  # a faint 1-block grid
        for x in range(size):
            img[hy][x] = (222, 222, 228, 255)
    els = m["elements"]
    tex_of = m["textures"]

    def tex_for(face):
        t = face["texture"]
        return textures[tex_of[t[1:]]]

    # depth key: for the front view the far elements have large z; side view: small x; top: small y
    def depth(e):
        if view == "front":
            return -e["to"][2]
        if view == "side":
            return e["from"][0]
        return e["from"][1]

    for e in sorted(els, key=depth):
        fc = {"front": "north", "side": "east", "top": "up"}[view]
        face = e["faces"].get(fc)
        if face is None:
            continue
        f, t = e["from"], e["to"]
        uv = face.get("uv", [0, 0, 16, 16])
        tx = tex_for(face)
        if view == "front":  # screen x = 16 - x (viewer from the north sees +x on the left), screen y = 16 - y
            xs, xe, ys, ye = 16 - t[0], 16 - f[0], 16 - t[1], 16 - f[1]
        elif view == "side":  # viewer from the east: screen x = 16 - z
            xs, xe, ys, ye = 16 - t[2], 16 - f[2], 16 - t[1], 16 - f[1]
        else:  # from above, north up: screen x = x, screen y = z
            xs, xe, ys, ye = f[0], t[0], f[2], t[2]
        for py in range(int(round(ys * scale)), int(round(ye * scale))):
            for pxx in range(int(round(xs * scale)), int(round(xe * scale))):
                if not (0 <= py < size and 0 <= pxx < size):
                    continue
                fu = (pxx / scale - xs) / max(1e-6, (xe - xs))
                fv = (py / scale - ys) / max(1e-6, (ye - ys))
                c = sample(tx, uv[0] + fu * (uv[2] - uv[0]), uv[1] + fv * (uv[3] - uv[1]))
                if c[3] == 0:
                    continue
                if e.get("light_emission"):
                    c = (min(255, c[0] + 10), min(255, c[1] + 10), min(255, c[2] + 10), 255)
                img[py][pxx] = c
    return img


def magnify(rows, k):
    out = []
    for row in rows:
        big = [c for c in row for _ in range(k)]
        out.extend([big] * k)
    return out


def preview(outdir):
    os.makedirs(outdir, exist_ok=True)
    # 1. every display texture frame, magnified 16x
    sheet = []
    for name, (col, n, ft) in STATES.items():
        frames = read_png(os.path.join(TEX, "decision_podium_%s.png" % name))
        for f in range(n):
            sheet.append(magnify([r[PX0 - 1:PX0 + PW + 1] for r in frames[16 * f + PY0 - 1:16 * f + PY0 + PH + 1]], 12))
    gap = [(255, 255, 255, 255)] * 4
    width = max(len(r[0]) for r in sheet)
    rows = []
    for s in sheet:
        rows.extend(s)
        rows.extend([[(255, 255, 255, 255)] * width] * 6)
    write_png(os.path.join(outdir, "podium_displays.png"), [r + [(255, 255, 255, 255)] * (width - len(r)) for r in rows])
    # 2. each state in three views
    for name in STATES:
        m = model(name)
        tx = load_textures(name)
        views = [render_view(m, tx, v) for v in ("front", "side", "top")]
        h = len(views[0])
        joined = [sum((v[y] + [(255, 255, 255, 255)] * 6 for v in views), []) for y in range(h)]
        write_png(os.path.join(outdir, "podium_%s.png" % name), joined)
    print("preview written to", outdir)


if __name__ == "__main__":
    write_textures()
    write_models()
    print("decision podium art written")
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
