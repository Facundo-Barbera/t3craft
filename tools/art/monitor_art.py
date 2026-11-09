#!/usr/bin/env python3
"""kit/monitor2: generates the monitor textures, block models and the blockstate.

Standard library only (zlib/struct), light enough for the VPS. Run from the repo root:

    python3 tools/art/monitor_art.py

Design: a desk monitor in graphite/steel. A monitor block is a full cube for collision, but its MODEL lives wholly inside
the cube (nothing sticks out of the block, which also keeps the headless renderer, that only tests a quad in the cell
that owns it, from cutting slivers at the seams of a wall):

    z 4..16   the casing; its front (z = 4) is the glass
    z 3..4    the frame: a 1 px lip, so the glass looks recessed
    z 1..11   the stand's base plate (a bottom-row block only), 2 px proud of the lip, z 6..10 the neck

The frame is made of strips that exist only on the SIDES THAT ARE NOT CONNECTED to another monitor (blockstate
properties up/down/left/right = false), so a wall of monitors is one big glass with one continuous frame, and every strip
picks its texture rows from what is next to it (connected textures), so a wall has no seams:

    top / left / right bezel   2 px
    bottom chin                2 px (y 3..5) with the status LED; the glass starts at y = 5 (MonitorLod.CHIN_PX = 5)
    stand                      below the chin, on a bottom-row block only (down = false): the casing is lifted 3 px and
                               rests on a dark neck over a 10 x 10 px base plate (narrower than the block: a stand, not a
                               skirt); a block with a monitor under it has a full-height casing, no chin, no stand.

Corner ownership: the left and right strips are full height and own the corners; the top and bottom strips stop at a
corner only when that side has a strip (variants `_l`, `_r`, `_l_r`). Side strips come in four variants by the neighbours
above/below (`_u`, `_d`, `_ud`): they only get a top / bottom cap where the frame really ends.

North-facing model (viewer looks south): the viewer's left is +x. A north face maps u = 16 - x, v = 16 - y.

Textures (16x16):  monitor_front   the frame as seen from the front. Rows: 0 light bezel, 1 steel bezel, 2-10 the side
                                   posts (cols 0-1 left, 14-15 right) and the glass, 11-12 the chin; strips sample
                                   single rows (uniform ones) so nothing repeats a mark at a seam
                   monitor_screen  the glass (the renderer paints over it; headless renders show this)
                   monitor_side / monitor_top / monitor_back   the casing; side and top are uniform along the wall
                   monitor_trim    swatches: rows 0-9 base plate top, 10 neck front, 11 plate edge, 12 dark (undersides,
                                   neck sides), 13 lip inner face (bright edge round the glass), 14 lip top, 15 lip side
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mod", "src", "main", "resources", "assets", "t3craft")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")
STATES = os.path.join(ROOT, "blockstates")

GLASS_Z = 4   # the glass plane; the lip is z 3..4
LIFT = 3      # a bottom-row casing is lifted this much: chin band y 3..5


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
LIP = rgb(0x0F1012)      # darkest casing detail (vent slits)
INNER = rgb(0x7C828C)    # inner lip faces: a bright edge round the glass
GLASS = rgb(0x0B0D0F)
GLASS2 = rgb(0x0E1114)
ACCENT = rgb(0x3FB7C2)   # T3 teal


# ---------------------------------------------------------------- textures
def frame_pixel(x, y):
    """The frame seen from the front: bezel 2 px (top / sides), chin 2 px (rows 11-12), glass inside."""
    if y >= 13:
        return DEEP
    if y == 12:
        return SHADE
    if y == 11:
        return STEEL
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
    """Uniform along y (stacked blocks show no band); the face samples cols 0..11 (z 4..16), edges a shade lighter."""
    g = grid(BODY)
    for y in range(16):
        g[y][0] = STEEL
        g[y][11] = STEEL
    return g


def make_top():
    """Uniform along x (a wall's top is one plate); the face samples rows 4..15 (z 4..16): a light front edge, a dark rear."""
    g = grid(BODY)
    for x in range(16):
        g[4][x] = STEEL
        g[5][x] = STEEL
        g[15][x] = SHADE
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
    g = grid(rgb(0x000000))
    # rows 0-9: base plate top, brightest at the front edge (row 0), darker towards the back
    top = [0xC4CAD3, 0xA9B0BA, 0x9AA1AB, 0x868C96, 0x7A808A, 0x6F747E, 0x666B75, 0x5E636C, 0x565B64, 0x4E525A]
    for y in range(10):
        for x in range(16):
            c = rgb(top[y])
            if y in (3, 6) and x % 4 == 0:  # brushed grain
                c = rgb(top[y - 1])
            g[y][x] = c
    # row 10: neck front, lit from the left (viewer's left is the low x of u = 16 - x... the face is mirrored, see the model)
    neck = [0x30343C] * 16
    neck[6], neck[7], neck[8], neck[9] = 0x454B55, 0x3A3F48, 0x30343C, 0x272A31
    g[10] = [rgb(c) for c in neck]
    g[11] = [rgb(0x4A4E57)] * 16     # plate edge (front, ends)
    g[12] = [rgb(0x1A1C21)] * 16     # undersides, neck sides: in shadow
    g[13] = [INNER] * 16             # lip inner faces
    g[14] = [rgb(0xA0A6B0)] * 16     # lip top
    g[15] = [rgb(0x565B64)] * 16     # lip outer side
    return g


# ---------------------------------------------------------------- models
def north_uv(xa, xb, ya, yb):
    return [16 - xb, 16 - yb, 16 - xa, 16 - ya]


def trim(row, u0=0, u1=16):
    """A face sampling one row of the trim swatches (uniform along the row, any width)."""
    return {"texture": "#trim", "uv": [u0, row, u1, row + 1]}


TRIM_PLATE_EDGE = 11
TRIM_DARK = 12
TRIM_LIP_INNER = 13
TRIM_LIP_TOP = 14
TRIM_LIP_SIDE = 15


def frame(u0, v0, u1, v1):
    return {"texture": "#frame", "uv": [u0, v0, u1, v1]}


def element(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


LZ0 = GLASS_Z - 1  # lip front
LZ1 = GLASS_Z      # lip back = glass plane


def side_strip(left, up_n, down_n):
    """The post at the viewer's left (x 14..16) or right (x 0..2). up_n / down_n: a monitor is there, so the strip
    continues instead of ending in a cap. Texture rows come from monitor_front: caps from its bezel / chin rows, the
    middle from one uniform row of the post."""
    xa, xb = (14, 16) if left else (0, 2)
    u0, u1 = (0, 2) if left else (14, 16)
    outer, inner = ("east", "west") if left else ("west", "east")
    yb = 0 if down_n else LIFT
    ym0 = yb if down_n else yb + 2
    ym1 = 16 if up_n else 14
    out = []

    def sides(extra=None):
        f = {"north": None, outer: trim(TRIM_LIP_SIDE, 0, 1), inner: trim(TRIM_LIP_INNER, 0, 1)}
        if extra:
            f.update(extra)
        return f

    mid = sides()
    mid["north"] = frame(u0, 5, u1, 6)
    out.append(element([xa, ym0, LZ0], [xb, ym1, LZ1], mid))
    if not up_n:
        cap = sides({"up": trim(TRIM_LIP_TOP, 0, 2)})
        cap["north"] = frame(u0, 0, u1, 2)
        out.append(element([xa, 14, LZ0], [xb, 16, LZ1], cap))
    if not down_n:
        cap = sides({"down": trim(TRIM_DARK, 0, 2)})
        cap["north"] = frame(u0, 11, u1, 13)
        out.append(element([xa, yb, LZ0], [xb, yb + 2, LZ1], cap))
    return out


def span(l, r):
    """x range of a top / bottom strip: it stops at a corner whose side strip exists."""
    return (2 if r else 0), (14 if l else 16)


def strip_up(l, r):
    xa, xb = span(l, r)
    return [element([xa, 14, LZ0], [xb, 16, LZ1], {
        "north": frame(2, 0, 14, 2),
        "up": trim(TRIM_LIP_TOP, 0, 16), "down": trim(TRIM_LIP_INNER, 0, 16)})]


def strip_down(l, r):
    """The chin band (y 3..5): the glass sits above it, the stand below it."""
    xa, xb = span(l, r)
    return [element([xa, LIFT, LZ0], [xb, LIFT + 2, LZ1], {
        "north": frame(2, 11, 14, 13),
        "up": trim(TRIM_LIP_INNER, 0, 16), "down": trim(TRIM_DARK, 0, 16)})]


def stand():
    """Neck and base plate under a lifted casing: the plate (10 x 10, 1 px thick, 2 px proud of the lip) and a 4 x 4 neck
    set back under the casing; the casing's own underside is dark, so the neck reads as a stem in shadow."""
    plate = element([3, 0, 1], [13, 1, 11], {
        "up": {"texture": "#trim", "uv": [3, 0, 13, 10]},
        "north": trim(TRIM_PLATE_EDGE, 3, 13), "south": trim(TRIM_PLATE_EDGE, 3, 13),
        "east": trim(TRIM_PLATE_EDGE, 0, 10), "west": trim(TRIM_PLATE_EDGE, 0, 10),
        "down": trim(TRIM_DARK, 3, 13)})
    neck = element([6, 1, 6], [10, LIFT, 10], {
        "north": trim(10, 6, 10), "south": trim(TRIM_DARK, 6, 10),
        "east": trim(TRIM_DARK, 6, 10), "west": trim(TRIM_DARK, 6, 10)})
    return [plate, neck]


def model(elements, extra_textures=None):
    t = {"particle": "t3craft:block/monitor_side", "frame": "t3craft:block/monitor_front", "trim": "t3craft:block/monitor_trim"}
    if extra_textures:
        t.update(extra_textures)
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": t, "elements": elements}


def body(lifted):
    """The casing, z 4..16; its north face is the glass. Lifted (bottom row): it rests on the stand, 3 px above the floor."""
    y0 = LIFT if lifted else 0
    h = 16 - y0
    faces = {
        "north": {"texture": "#screen", "uv": [0, 0, 16, h]},
        "south": {"texture": "#back", "uv": [0, 0, 16, h]},
        "east": {"texture": "#side", "uv": [0, 0, 12, h]},
        "west": {"texture": "#side", "uv": [0, 0, 12, h]},
        "up": {"texture": "#top", "uv": [0, GLASS_Z, 16, 16]}}
    if lifted:
        faces["down"] = trim(TRIM_DARK, 0, 16)
    return element([0, y0, GLASS_Z], [16, 16, 16], faces)


BODY_TEXTURES = {
    "screen": "t3craft:block/monitor_screen", "back": "t3craft:block/monitor_back",
    "side": "t3craft:block/monitor_side", "top": "t3craft:block/monitor_top"}


def suffix(l, r):
    return ("_l" if l else "") + ("_r" if r else "")


def vsuffix(u, d):
    return ("_u" if u else "") + ("_d" if d else "")


def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    for name, rows in (("front", make_front()), ("screen", make_screen()), ("side", make_side()), ("top", make_top()),
                       ("back", make_back()), ("trim", make_trim())):
        write_png(os.path.join(TEX, "monitor_%s.png" % name), rows)

    # the casing with the glass, no frame (the strips and the stand are added by the blockstate)
    write_json(os.path.join(MODELS, "monitor_panel.json"), model([body(False)], BODY_TEXTURES))
    write_json(os.path.join(MODELS, "monitor_panel_lift.json"), model([body(True)], BODY_TEXTURES))
    write_json(os.path.join(MODELS, "monitor_stand.json"), model(stand()))
    for u in (False, True):
        for d in (False, True):
            write_json(os.path.join(MODELS, "monitor_edge_left%s.json" % vsuffix(u, d)), model(side_strip(True, u, d)))
            write_json(os.path.join(MODELS, "monitor_edge_right%s.json" % vsuffix(u, d)), model(side_strip(False, u, d)))
    for l in (False, True):
        for r in (False, True):
            write_json(os.path.join(MODELS, "monitor_edge_up%s.json" % suffix(l, r)), model(strip_up(l, r)))
            write_json(os.path.join(MODELS, "monitor_edge_down%s.json" % suffix(l, r)), model(strip_down(l, r)))
    # the item: the whole monitor, every side framed, on its stand
    item = [body(True)] + side_strip(True, False, False) + side_strip(False, False, False) + strip_up(True, True) + strip_down(True, True) + stand()
    write_json(os.path.join(MODELS, "monitor.json"), model(item, BODY_TEXTURES))

    # blockstate (multipart): casing + the strips of the sides not connected; up/down pick the corner variant, left/right
    # strips their caps; a bottom-row block (down = false) gets the chin band and the stand
    parts = []
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        def apply(m):
            a = {"model": "t3craft:block/" + m}
            if y:
                a["y"] = y
            return a

        parts.append({"when": {"facing": facing, "down": "true"}, "apply": apply("monitor_panel")})
        parts.append({"when": {"facing": facing, "down": "false"}, "apply": apply("monitor_panel_lift")})
        parts.append({"when": {"facing": facing, "down": "false"}, "apply": apply("monitor_stand")})
        for side in ("up", "down"):
            for l in (False, True):
                for r in (False, True):
                    # left/right = "false" means NO neighbour there, so that side has a strip
                    parts.append({"when": {"facing": facing, side: "false", "left": "false" if l else "true", "right": "false" if r else "true"},
                                  "apply": apply("monitor_edge_%s%s" % (side, suffix(l, r)))})
        for side in ("left", "right"):
            for u in (False, True):
                for d in (False, True):
                    parts.append({"when": {"facing": facing, side: "false", "up": "true" if u else "false", "down": "true" if d else "false"},
                                  "apply": apply("monitor_edge_%s%s" % (side, vsuffix(u, d)))})
    write_json(os.path.join(STATES, "monitor.json"), {"multipart": parts})


if __name__ == "__main__":
    main()
