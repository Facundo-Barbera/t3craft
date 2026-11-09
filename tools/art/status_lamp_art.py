#!/usr/bin/env python3
"""kit/status-lamp: generates the status lamp textures (plain + animated strips), their .mcmeta and the six block models.

Standard library only (zlib/struct), light enough for the VPS. Run from the repo root:

    python3 tools/art/status_lamp_art.py

Design (a thread's state has to read at a glance and from afar: colour + silhouette + blink, in that order):
  * body: a lantern capsule, symmetric top/bottom, so it works standing on a screen AND hanging from a chain
    (the hub beacon); steel caps with gold rings.
  * lens colours are LampStatus' (docs/feat-blocks.md); only the lens (and its flares) glow, in-game too
    ("light_emission" on those elements only).
  * silhouette per state:  off  dark thin glass (no glow)        idle  thin dim gray candle, pause bars
                           working  band around the lens, cyan spinner that turns (4 frames)
                           done  plain green lens with a check mark, steady
                           attention  8-wide lens + 4 horns (a siren star), orange "!" that blinks slowly
                           error  wide squat lens + 8 claws (crown of spikes), red X that blinks fast
  * texture layout (16 px wide; frame = 16x16): rows 0-1 bright strip (band), row 2 plain lens colour (flares,
    spikes, top faces), rows 3-13 the glyph crop each model face points at. Frame 0 is the "lit" one (renders show it).
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mod", "src", "main", "resources", "assets", "t3craft")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")


# ---------------------------------------------------------------- png
def write_png(path, rows):
    """rows: list of rows of (r, g, b, a)."""
    h = len(rows)
    w = len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def lighten(c, t):
    return tuple(int(round(v + (255 - v) * t)) for v in c)


def darken(c, f):
    return tuple(int(round(v * f)) for v in c)


def px(c):
    return (c[0], c[1], c[2], 255)


# ---------------------------------------------------------------- glyphs (rows of '#', '.')
GLYPHS = {
    "check": ["....##", "...##.", "##.##.", ".###..", "..#..."],
    "bang": ["##", "##", "##", "##", "##", "..", "##", "##"],
    "x": ["##..##", ".####.", "..##..", "..##..", ".####.", "##..##"],
    "pause": ["##..##", "##..##", "##..##", "##..##", "##..##"],
}
# 6x6 ring, clockwise from the top-left corner (20 px)
RING = [(i, 0) for i in range(6)] + [(5, j) for j in range(1, 6)] + [(i, 5) for i in range(4, -1, -1)] + [(0, j) for j in range(4, 0, -1)]

# state -> lens crop (x0, y0, x1, y1) in texture px = lens face size in model px (1:1 texel density)
# name: (colour, crop, frames, frametime)
STATES = {
    "off": (0x2A2A2A, (5, 3, 11, 13), 1, 0),
    "idle": (0xAAAAAA, (5, 3, 11, 13), 1, 0),
    "working": (0x55FFFF, (4, 3, 12, 13), 4, 3),
    "attention": (0xFFAA00, (3, 3, 13, 13), 2, 10),
    "error": (0xFF5555, (3, 3, 13, 13), 2, 4),
    "done": (0x55FF55, (4, 3, 12, 13), 1, 0),
}


def lens_frame(name, f):
    col, (x0, y0, x1, y1), n, _ = STATES[name]
    c = rgb(col)
    lit = True
    if name in ("attention", "error") and f == 1:
        lit = False  # the dim half of the blink
    fill = c if lit else darken(c, 0.30)
    img = [[px(fill)] * 16 for _ in range(16)]
    if name == "off":
        fill = rgb(0x1B2026)
        img = [[px(fill)] * 16 for _ in range(16)]
        for i in range(0, 5):  # dead glass: a faint diagonal reflection
            for (x, y) in ((x0 + 1 + i, y0 + 1 + i), (x0 + 2 + i, y0 + 1 + i)):
                # (reflection pixels below are lighter than the glass so the lamp reads as dead glass, not a hole)
                if x0 <= x < x1 and y0 <= y < y1:
                    img[y][x] = px(rgb(0x4A4A4A))
    # rows 0-1: bright strip (the band), 2-3 already plain
    for y in range(2):
        for x in range(16):
            img[y][x] = px(lighten(fill, 0.38) if name != "off" else rgb(0x2A333B))
    if name != "off":
        # lens as glass: 1 px darker vertical edges, rows fade to 80 % at the top and bottom, a 2x2 core highlight
        edge = darken(fill, 0.70)
        for y in range(y0, y1):
            k = 0.80 if y in (y0, y0 + 1, y1 - 1, y1 - 2) else 1.0
            for x in range(x0, x1):
                img[y][x] = px(edge if x in (x0, x1 - 1) else darken(fill, k))
        for (x, y) in ((x0 + 1, y0 + 2), (x0 + 2, y0 + 2), (x0 + 1, y0 + 3), (x0 + 2, y0 + 3)):
            img[y][x] = px(lighten(fill, 0.40))
    # rows 14-15: the claws' darker shade
    for y in (14, 15):
        for x in range(16):
            img[y][x] = px(darken(fill, 0.55))
    # glyph
    glyph_col = darken(c, 0.30 if name != "attention" else 0.16) if lit else lighten(c, 0.15)
    gname = {"idle": "pause", "attention": "bang", "error": "x", "done": "check"}.get(name)
    cx = (x0 + x1) / 2.0
    cy = (y0 + y1) / 2.0
    if gname:
        g = GLYPHS[gname]
        gw, gh = len(g[0]), len(g)
        ox = int(round(cx - gw / 2.0))
        oy = int(round(cy - gh / 2.0))
        for j, row in enumerate(g):
            for i, ch in enumerate(row):
                if ch == "#":
                    img[oy + j][ox + i] = px(glyph_col)
    if name == "working":
        ox = int(round(cx - 3))
        oy = int(round(cy - 3))
        for k, (i, j) in enumerate(RING):
            head = ((k - 5 * f) % 20) < 7
            img[oy + j][ox + i] = px(glyph_col if head else darken(c, 0.62))
    return img


def write_textures():
    for name, (col, crop, n, ft) in STATES.items():
        rows = []
        for f in range(n):
            rows += lens_frame(name, f)
        write_png(os.path.join(TEX, "status_lamp_%s.png" % name), rows)
        meta = os.path.join(TEX, "status_lamp_%s.png.mcmeta" % name)
        if n > 1:
            with open(meta, "w") as fh:
                json.dump({"animation": {"frametime": ft}}, fh, indent=2)
                fh.write("\n")
        elif os.path.exists(meta):
            os.remove(meta)
    # base: rows 0-5 gold, 6-15 steel
    gold = rgb(0xC9A24A)
    steel = rgb(0x5A6068)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y < 6:
                c = gold
                if y == 0:
                    c = lighten(gold, 0.35)
                elif y == 5:
                    c = darken(gold, 0.7)
            else:
                c = steel
                if y == 6:
                    c = lighten(steel, 0.45)
                elif y == 15:
                    c = darken(steel, 0.7)
                elif (x + y) % 7 == 0:
                    c = lighten(steel, 0.08)
            row.append(px(c))
        rows.append(row)
    write_png(os.path.join(TEX, "status_lamp_base.png"), rows)


# ---------------------------------------------------------------- models
FACES = ("north", "south", "east", "west", "up", "down")


def element(frm, to, kind, crop=None, emit=0, skip=()):
    """kind: steel | gold | lens (glyph crop on the sides) | plain (lens colour) | bright (lens bright strip)."""
    faces = {}
    for fc in FACES:
        if fc in skip:
            continue
        if kind == "steel":
            w = abs(to[0] - frm[0]) if fc in ("north", "south", "up", "down") else abs(to[2] - frm[2])
            h = abs(to[1] - frm[1]) if fc not in ("up", "down") else abs(to[2] - frm[2])
            faces[fc] = {"texture": "#base", "uv": [0, 7, round(min(w, 8), 2), round(7 + min(h, 8), 2)]}
        elif kind == "gold":
            faces[fc] = {"texture": "#base", "uv": [0, 1, 2, 4]}
        elif kind == "lens":
            if fc in ("up", "down"):
                faces[fc] = {"texture": "#lens", "uv": [0, 2, 2, 3]}
            else:
                faces[fc] = {"texture": "#lens", "uv": list(crop)}
        elif kind == "plain":
            faces[fc] = {"texture": "#lens", "uv": [0, 2, 2, 3]}
        elif kind == "claw":
            faces[fc] = {"texture": "#lens", "uv": [0, 14, 2, 16]}
        else:
            faces[fc] = {"texture": "#lens", "uv": [0, 0, 2, 2]}
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if emit:
        e["light_emission"] = emit
    return e


def body(ring, small=False):
    """Caps and rings, symmetric about y = 8; plates are lens + 2 px (10 px; 8 px for the small idle/off candle)."""
    a = 4 if small else 3
    b = 16 - a
    return [
        element([a, 0, a], [b, 1, b], "steel"),
        element([a + 1, 1, a + 1], [b - 1, 2, b - 1], "steel"),
        element([a + 0.5, 2, a + 0.5], [b - 0.5, 3, b - 0.5], ring),
        element([a + 0.5, 13, a + 0.5], [b - 0.5, 14, b - 0.5], ring),
        element([a + 1, 14, a + 1], [b - 1, 15, b - 1], "steel"),
        element([a, 15, a], [b, 16, b], "steel"),
    ]


def model(name):
    col, crop, n, ft = STATES[name]
    emit = {"off": 0, "idle": 7, "working": 13, "attention": 15, "error": 15, "done": 13}[name]
    # attention: steel rings, so the orange does not read as brass
    els = body("steel" if name in ("attention", "off") else "gold", small=name in ("idle", "off"))
    x0, y0, x1, y1 = crop
    # lens: square in plan, y 3..13; the rings are wider than the lens, so up/down faces stay hidden except on 10 px lenses
    els.append(element([x0, 3, x0], [x1, 13, x1], "lens", crop=crop, emit=emit, skip=("up", "down") if x1 - x0 <= 8 else ()))
    if name == "working":
        els.append(element([3, 7, 3], [13, 9, 13], "bright", emit=emit))
    elif name == "attention":
        # four horns (3 x 5 px base tapering to a 2 x 3 tip): a siren star 16 px across
        for (fx, fz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            def horn(a0, a1, b0, b1, y0_, y1_):
                # a = axis of the horn, b = across; swap for the z horns
                if fx != 0:
                    lo, hi = sorted((8 + fx * a0, 8 + fx * a1))
                    return element([lo, y0_, b0], [hi, y1_, b1], "plain", emit=emit)
                lo, hi = sorted((8 + fz * a0, 8 + fz * a1))
                return element([b0, y0_, lo], [b1, y1_, hi], "plain", emit=emit)
            els.append(horn(5, 7, 6.5, 9.5, 5.5, 10.5))
            els.append(horn(7, 8, 7, 9, 6.5, 9.5))
    elif name == "error":
        # eight claws (up and down, darker red) and four corner nubs sticking out at mid height: a jagged outline
        for cx in (3, 11):
            for cz in (3, 11):
                els.append(element([cx, 13, cz], [cx + 2, 15, cz + 2], "claw", emit=emit))
                els.append(element([cx + 0.5, 15, cz + 0.5], [cx + 1.5, 16, cz + 1.5], "claw", emit=emit))
                els.append(element([cx, 1, cz], [cx + 2, 3, cz + 2], "claw", emit=emit))
                els.append(element([cx + 0.5, 0, cz + 0.5], [cx + 1.5, 1, cz + 1.5], "claw", emit=emit))
        for nx in (1, 13):
            for nz in (1, 13):
                els.append(element([nx, 6.5, nz], [nx + 2, 9.5, nz + 2], "claw", emit=emit))
    return {
        "parent": "minecraft:block/block",
        "textures": {"base": "t3craft:block/status_lamp_base", "lens": "t3craft:block/status_lamp_%s" % name, "particle": "t3craft:block/status_lamp_base"},
        "elements": els,
    }


def write_models():
    for name in STATES:
        with open(os.path.join(MODELS, "status_lamp_%s.json" % name), "w") as f:
            json.dump(model(name), f, indent=2)
            f.write("\n")


if __name__ == "__main__":
    write_textures()
    write_models()
    print("status lamp art written")
