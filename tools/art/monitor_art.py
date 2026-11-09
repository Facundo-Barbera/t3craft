#!/usr/bin/env python3
"""kit/monitor: generates the monitor textures, block models and the blockstate.

Standard library only (zlib/struct), light enough for the VPS. Run from the repo root:

    python3 tools/art/monitor_art.py

Design: a desk monitor in graphite/steel. A monitor block is a full cube whose north face (the front) is the
glass; the frame is a raised lip (1 px) made of strips that exist only on the SIDES THAT ARE NOT CONNECTED to
another monitor, so a wall of monitors is one big glass with one continuous frame:

    top / left / right bezel   2 px (lip stands 1 px proud of the glass: the glass looks recessed)
    bottom chin                3 px; its lowest pixel is a "foot": a steel ledge 3 px deep (the stand, or a plinth
                               on a wall). The renderer puts a status LED and the T3 mark on the chin.

Corner ownership: the left and right strips are full height and own the corners; the top and bottom strips stop
at a corner only when that side's strip exists (variants `_l`, `_r`, `_lr`), so the frame has no overlaps.

North-facing model (viewer looks south): the viewer's left is +x. A north face maps u = 16 - x, v = 16 - y.

Textures (16x16):  monitor_front   the frame as seen from the front (strips and the item sample it)
                   monitor_screen  the glass (the renderer paints over it; headless renders show this)
                   monitor_side / monitor_top / monitor_back   the casing
                   monitor_trim    small swatches for the lip faces and the foot (rows noted below)
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mod", "src", "main", "resources", "assets", "t3craft")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")
STATES = os.path.join(ROOT, "blockstates")


# ---------------------------------------------------------------- png
def write_png(path, rows):
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
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def grid(fill):
    return [[fill for _ in range(16)] for _ in range(16)]


# ---------------------------------------------------------------- palette
LIGHT = rgb(0x9097A2)    # top-left highlight of the bezel
STEEL = rgb(0x646A74)
BODY = rgb(0x4B4F58)     # flat casing
SHADE = rgb(0x363942)
DEEP = rgb(0x23252B)
LIP = rgb(0x0F1012)      # inner lip (towards the glass)
GLASS = rgb(0x0B0D0F)
GLASS2 = rgb(0x0E1114)
ACCENT = rgb(0x3FB7C2)   # T3 teal


# ---------------------------------------------------------------- textures
def frame_pixel(x, y):
    """The frame seen from the front: bezel 2 px (top / sides), chin 3 px, glass inside."""
    if y > 12:  # chin: inner shadow, flat, outer edge
        return SHADE if y == 13 else (BODY if y == 14 else DEEP)
    if y < 2:
        if x == 15:
            return BODY
        return LIGHT if y == 0 else STEEL
    if x < 2:
        return LIGHT if x == 0 else STEEL
    if x > 13:
        return BODY if x == 14 else DEEP
    return GLASS


def make_front():
    return [[frame_pixel(x, y) for x in range(16)] for y in range(16)]


def make_screen():
    g = grid(GLASS)
    for y in range(16):
        if y % 4 == 3:
            g[y] = [GLASS2] * 16
    return g


def box_casing(vents):
    g = grid(BODY)
    for i in range(16):
        g[0][i] = g[15][i] = g[i][0] = g[i][15] = STEEL
    for (y, x0, x1) in vents:
        for x in range(x0, x1):
            g[y][x] = LIP
            if y + 1 < 15:
                g[y + 1][x] = SHADE
    return g


def make_side():
    # clean brushed casing: an inset panel line, no vents (the sides show on a lone monitor and at the ends of a wall)
    g = box_casing([])
    for i in range(3, 13):
        g[3][i] = g[12][i] = SHADE
        g[i][3] = g[i][12] = SHADE
    return g


def make_top():
    g = box_casing([])
    for i in range(1, 15):
        g[7][i] = SHADE
        g[8][i] = LIGHT if i % 2 == 0 else STEEL
    return g


def make_back():
    g = box_casing([(3, 3, 13), (12, 3, 13)])
    # label plate with a "T3" mark (5 rows)
    for y in range(5, 12):
        for x in range(3, 12):
            g[y][x] = DEEP
    t = ["###.###", ".#....#", ".#..###", ".#....#", ".#..###"]
    for j, row in enumerate(t):
        for i, c in enumerate(row):
            if c == "#":
                g[6 + j][4 + i] = ACCENT
    return g


def make_trim():
    """Swatches. rows 0-3 foot top (brushed steel, front edge brightest), row 5 foot front, row 7 foot end,
    row 9 lip inner faces (dark), row 11 lip top (bright), row 13 lip outer side."""
    g = grid(rgb(0x000000))
    top = [rgb(0x5C616A), rgb(0x6F747E), rgb(0x868C96), rgb(0xA9B0BA)]
    for y in range(4):
        for x in range(16):
            c = top[y]
            if y == 1 and x % 4 == 0:
                c = rgb(0x757A84)
            g[y][x] = c
    g[5] = [rgb(0x41444C)] * 16
    g[7] = [rgb(0x30323A)] * 16
    g[9] = [LIP] * 16
    g[11] = [rgb(0xA0A6B0)] * 16
    g[13] = [rgb(0x565B64)] * 16
    return g


# ---------------------------------------------------------------- models
def north_uv(xa, xb, ya, yb):
    return [16 - xb, 16 - yb, 16 - xa, 16 - ya]


TRIM_FOOT_TOP = "#trim", [0, 0, 16, 4]
TRIM_FOOT_FRONT = "#trim", [0, 5, 16, 6]
TRIM_FOOT_END = "#trim", [0, 7, 4, 8]
TRIM_LIP_INNER = "#trim", [0, 9, 16, 10]
TRIM_LIP_TOP = "#trim", [0, 11, 16, 12]
TRIM_LIP_SIDE = "#trim", [0, 13, 16, 14]


def face(spec):
    tex, uv = spec
    return {"texture": tex, "uv": uv}


def element(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def strip_left():
    """Viewer's left (x 14..16), full height."""
    return element([14, 0, -1], [16, 16, 0], {
        "north": {"texture": "#frame", "uv": north_uv(14, 16, 0, 16)},
        "east": face(TRIM_LIP_SIDE), "west": face(TRIM_LIP_INNER),
        "up": face(TRIM_LIP_TOP), "down": face(TRIM_LIP_INNER)})


def strip_right():
    return element([0, 0, -1], [2, 16, 0], {
        "north": {"texture": "#frame", "uv": north_uv(0, 2, 0, 16)},
        "west": face(TRIM_LIP_SIDE), "east": face(TRIM_LIP_INNER),
        "up": face(TRIM_LIP_TOP), "down": face(TRIM_LIP_INNER)})


def span(l, r):
    """x range of a top / bottom strip: it stops at a corner whose side strip exists."""
    return (2 if r else 0), (14 if l else 16)


def strip_up(l, r):
    xa, xb = span(l, r)
    return element([xa, 14, -1], [xb, 16, 0], {
        "north": {"texture": "#frame", "uv": north_uv(xa, xb, 14, 16)},
        "up": face(TRIM_LIP_TOP), "down": face(TRIM_LIP_INNER)})


def strip_down(l, r):
    """Chin (y 1..3) and the foot under it (y 0..1, 4 px deep)."""
    xa, xb = span(l, r)
    chin = element([xa, 1, -1], [xb, 3, 0], {
        "north": {"texture": "#frame", "uv": north_uv(xa, xb, 1, 3)},
        "up": face(TRIM_LIP_INNER)})
    foot = element([0, 0, -4], [16, 1, 0], {
        "north": face(TRIM_FOOT_FRONT), "up": face(TRIM_FOOT_TOP), "down": face(TRIM_LIP_INNER),
        "east": face(TRIM_FOOT_END), "west": face(TRIM_FOOT_END)})
    return [chin, foot]


def model(elements, extra_textures=None):
    t = {"particle": "t3craft:block/monitor_side", "frame": "t3craft:block/monitor_front", "trim": "t3craft:block/monitor_trim"}
    if extra_textures:
        t.update(extra_textures)
    return {"parent": "minecraft:block/block", "textures": t, "elements": elements}


def body():
    return element([0, 0, 0], [16, 16, 16], {
        "north": {"texture": "#screen", "uv": [0, 0, 16, 16]},
        "south": {"texture": "#back", "uv": [0, 0, 16, 16]},
        "east": {"texture": "#side", "uv": [0, 0, 16, 16]},
        "west": {"texture": "#side", "uv": [0, 0, 16, 16]},
        "up": {"texture": "#top", "uv": [0, 0, 16, 16]},
        "down": {"texture": "#top", "uv": [0, 0, 16, 16]}})


BODY_TEXTURES = {
    "screen": "t3craft:block/monitor_screen", "back": "t3craft:block/monitor_back",
    "side": "t3craft:block/monitor_side", "top": "t3craft:block/monitor_top"}


def suffix(l, r):
    return ("_l" if l else "") + ("_r" if r else "")


def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    for name, rows in (("front", make_front()), ("screen", make_screen()), ("side", make_side()), ("top", make_top()),
                       ("back", make_back()), ("trim", make_trim())):
        write_png(os.path.join(TEX, "monitor_%s.png" % name), rows)

    # the cube with the glass and the casing, no frame (the strips are added by the blockstate)
    write_json(os.path.join(MODELS, "monitor_panel.json"), model([body()], BODY_TEXTURES))
    write_json(os.path.join(MODELS, "monitor_edge_left.json"), model([strip_left()]))
    write_json(os.path.join(MODELS, "monitor_edge_right.json"), model([strip_right()]))
    for l in (False, True):
        for r in (False, True):
            write_json(os.path.join(MODELS, "monitor_edge_up%s.json" % suffix(l, r)), model([strip_up(l, r)]))
            write_json(os.path.join(MODELS, "monitor_edge_down%s.json" % suffix(l, r)), model(strip_down(l, r)))
    # the item: the whole monitor, every side framed
    item = [body(), strip_left(), strip_right(), strip_up(True, True)] + strip_down(True, True)
    write_json(os.path.join(MODELS, "monitor.json"), model(item, BODY_TEXTURES))

    # blockstate (multipart): panel + the strips of the sides not connected; up/down pick the corner variant
    parts = []
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        def apply(m):
            a = {"model": "t3craft:block/" + m}
            if y:
                a["y"] = y
            return a

        parts.append({"when": {"facing": facing}, "apply": apply("monitor_panel")})
        for side in ("up", "down"):
            for l in (False, True):
                for r in (False, True):
                    # left/right = "false" means NO neighbour there, so that side has a strip
                    parts.append({"when": {"facing": facing, side: "false", "left": "false" if l else "true", "right": "false" if r else "true"},
                                  "apply": apply("monitor_edge_%s%s" % (side, suffix(l, r)))})
        parts.append({"when": {"facing": facing, "left": "false"}, "apply": apply("monitor_edge_left")})
        parts.append({"when": {"facing": facing, "right": "false"}, "apply": apply("monitor_edge_right")})
    write_json(os.path.join(STATES, "monitor.json"), {"multipart": parts})


if __name__ == "__main__":
    main()
