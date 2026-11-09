#!/usr/bin/env python3
"""kit/merge-station: generates the merge station's textures, its four block models, the blockstate and the item model.

Standard library only (zlib/struct), light enough for the VPS. Run from the repo root:

    python3 tools/art/merge_station_art.py                 # writes the assets
    python3 tools/art/merge_station_art.py --preview DIR   # also writes enlarged PNGs (textures, front and top views) for review

Design (a review desk: it has to say "something waits for you" from across the control room, in colour first):
  * cabinet: slate steel, two doors (a merge glyph on the left one, vents on the right), brass lip along the front edge
    of the desk, so it reads as a desk and not as a box;
  * an upright screen housing at the back with a flat screen (the client draws the PR count over it);
  * a big square button on the desk and a lamp post at each front corner. State = colour of the lamps and of the screen frame:
        none    dim grey, nothing glows
        open    green, blinks slowly (2-frame strip), the loudest one: a PR is waiting for review
        draft   cool steel blue, steady, hatched (work in progress)
        merged  violet, steady, a check on the button
  * the lamps (and only they, plus the screen) glow in game ("light_emission" per element).
Everything is original pixel art drawn here; nothing is copied from AgentCraft or from Mojang.
"""
import json
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mod", "src", "main", "resources", "assets", "t3craft")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")
BLOCKSTATES = os.path.join(ROOT, "blockstates")
ITEMS = os.path.join(ROOT, "items")

STATES = ["none", "open", "draft", "merged"]
# state -> (colour, light emission of the lamp elements, 0 = none)
STATE_COLOR = {"none": 0x8A8F94, "open": 0x55FF55, "draft": 0x7C9CD8, "merged": 0xB583FF}
EMISSION = {"none": 0, "open": 14, "draft": 10, "merged": 8}
POST_H = {"none": 1, "open": 6, "draft": 3, "merged": 4}  # lamp post height (px) per state

# ---------------------------------------------------------------- palette
SLATE = 0x3A424D
SLATE_DARK = 0x262C34
SLATE_LIGHT = 0x566170
SLATE_EDGE = 0x1B2026
BRASS = 0xC9A24A
BRASS_LIGHT = 0xE8C872
BRASS_DARK = 0x8D6B2A
LEATHER = 0x1E2328
LEATHER_LIGHT = 0x2C333A
GLASS = 0x0B1418


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


def read_png(path):
    """Reads back one of our PNGs (8-bit RGBA, filter 0) into rows of (r, g, b, a)."""
    with open(path, "rb") as f:
        data = f.read()
    pos = 8
    idat = b""
    w = h = 0
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        d = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", d[:8])
        elif t == b"IDAT":
            idat += d
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride = 1 + w * 4
    rows = []
    for y in range(h):
        line = raw[y * stride + 1:(y + 1) * stride]
        rows.append([tuple(line[i * 4:i * 4 + 4]) for i in range(w)])
    return rows


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def lighten(c, t):
    return tuple(int(round(v + (255 - v) * t)) for v in c)


def darken(c, f):
    return tuple(int(round(v * f)) for v in c)


def px(c):
    if isinstance(c, int):
        c = rgb(c)
    return (c[0], c[1], c[2], 255)


def noise(x, y, seed):
    """Deterministic 0..1 value per pixel."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def blank(fill, w=16, h=16):
    return [[px(fill)] * w for _ in range(h)]


def put(img, x, y, c):
    if 0 <= y < len(img) and 0 <= x < len(img[0]):
        img[y][x] = px(c)


def rect(img, x0, y0, x1, y1, c):
    """Fills x0..x1-1, y0..y1-1."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(img, x, y, c)


def speckle(img, base, amount, seed):
    for y in range(len(img)):
        for x in range(len(img[0])):
            k = (noise(x, y, seed) - 0.5) * 2 * amount
            c = rgb(base) if isinstance(base, int) else base
            img[y][x] = px(tuple(max(0, min(255, int(round(v * (1 + k))))) for v in c))


# ---------------------------------------------------------------- glyphs
MERGE_GLYPH = [  # two branches joining: 5 wide, 5 tall
    "#...#",
    "#...#",
    ".#.#.",
    "..#..",
    "..#..",
]
CHECK_GLYPH = [  # 4x3
    "...#",
    "#.#.",
    ".#..",
]


def stamp(img, glyph, x0, y0, c):
    for j, row in enumerate(glyph):
        for i, ch in enumerate(row):
            if ch == "#":
                put(img, x0 + i, y0 + j, c)


# ---------------------------------------------------------------- textures
def tex_body():
    img = blank(SLATE)
    speckle(img, SLATE, 0.05, 1)
    rect(img, 0, 0, 16, 1, SLATE_DARK)       # top and bottom seam
    rect(img, 0, 15, 16, 16, SLATE_EDGE)
    rect(img, 0, 1, 16, 2, SLATE_LIGHT)
    for x in range(16):
        put(img, x, 8, darken(rgb(SLATE), 0.82))  # a horizontal panel line (the sides)
    for y in range(16):
        put(img, 8, y, darken(rgb(SLATE), 0.9))
    for (x, y) in ((2, 3), (13, 3), (2, 12), (13, 12)):
        put(img, x, y, SLATE_LIGHT)
        put(img, x + 1 if x < 8 else x - 1, y, SLATE_DARK)
    return img


def tex_front():
    """Cabinet front: visible rows 7..15 (the model uses uv y 7..16)."""
    img = blank(SLATE)
    speckle(img, SLATE, 0.04, 2)
    rect(img, 0, 0, 16, 7, SLATE)                 # hidden rows
    rect(img, 0, 7, 16, 8, SLATE_EDGE)            # shadow under the brass lip
    rect(img, 0, 15, 16, 16, SLATE_EDGE)          # plinth shadow
    for (x0, x1) in ((1, 7), (9, 15)):            # two doors
        rect(img, x0, 8, x1, 15, SLATE_DARK)
        rect(img, x0 + 1, 9, x1 - 1, 14, SLATE)
        speckle_box(img, x0 + 1, 9, x1 - 1, 14, SLATE, 0.05, 3)
        rect(img, x0, 8, x1, 9, SLATE_LIGHT)      # lit top edge of each door
    rect(img, 7, 8, 9, 15, BRASS)                 # brass centre plate (lock stile)
    rect(img, 7, 8, 8, 15, BRASS_LIGHT)
    rect(img, 8, 8, 9, 15, BRASS_DARK)
    put(img, 7, 11, BRASS_DARK)
    put(img, 8, 11, BRASS_DARK)
    stamp(img, MERGE_GLYPH, 2, 9, BRASS_LIGHT)    # left door: the merge glyph
    for y in (10, 12):                            # right door: vents
        rect(img, 10, y, 14, y + 1, SLATE_EDGE)
    return img


def speckle_box(img, x0, y0, x1, y1, base, amount, seed):
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = (noise(x, y, seed) - 0.5) * 2 * amount
            c = rgb(base)
            img[y][x] = px(tuple(max(0, min(255, int(round(v * (1 + k))))) for v in c))


def tex_top():
    """Desk top: dark leather with a brass border; the button sits on it."""
    img = blank(LEATHER)
    speckle(img, LEATHER, 0.07, 4)
    for i in range(16):
        put(img, i, 0, BRASS_DARK)
        put(img, i, 15, BRASS_DARK)
        put(img, 0, i, BRASS_DARK)
        put(img, 15, i, BRASS_DARK)
    for i in range(1, 15):
        put(img, i, 1, LEATHER_LIGHT)
        put(img, 1, i, LEATHER_LIGHT)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, BRASS_LIGHT)
    # a faint outline of the button and of the two pylons (the elements stand on these cells)
    for x in range(5, 11):
        put(img, x, 2, LEATHER_LIGHT)
    return img


def tex_bezel():
    """Screen housing: dark steel with a brass edge on top."""
    img = blank(SLATE_DARK)
    speckle(img, SLATE_DARK, 0.05, 5)
    rect(img, 0, 0, 16, 1, BRASS_DARK)
    rect(img, 0, 1, 16, 2, BRASS)
    rect(img, 0, 2, 16, 3, BRASS_DARK)
    rect(img, 0, 15, 16, 16, SLATE_EDGE)
    for y in range(3, 15):
        put(img, 0, y, SLATE_LIGHT)
        put(img, 15, y, SLATE_EDGE)
    return img


def tex_brass():
    img = blank(BRASS)
    speckle(img, BRASS, 0.025, 6)
    rect(img, 0, 0, 16, 1, BRASS_LIGHT)
    rect(img, 0, 1, 16, 2, lighten(rgb(BRASS), 0.12))
    rect(img, 0, 14, 16, 16, BRASS_DARK)
    return img


def tex_screen(state):
    """Screen face (the model uses the crop x 2..14, y 1..6): a frame in the state colour and a faint diff inside."""
    c = rgb(STATE_COLOR[state])
    img = blank(GLASS)
    # frame
    rect(img, 2, 1, 14, 2, c)
    rect(img, 2, 5, 14, 6, darken(c, 0.55))
    rect(img, 2, 1, 3, 6, darken(c, 0.8))
    rect(img, 13, 1, 14, 6, darken(c, 0.8))
    # a faint diff view: + lines green, - lines red, context grey
    dim = 0.45 if state != "none" else 0.22
    put_line(img, 3, 3, 8, rgb(0x55FF55), dim)
    put_line(img, 4, 3, 6, rgb(0xFF5555), dim)
    put_line(img, 4, 7, 11, rgb(0x8A8F94), dim * 0.8)
    return img


def put_line(img, y, x0, x1, c, k):
    for x in range(x0, x1):
        put(img, x, y, darken(c, k))


def tex_lamp(state, frame=0):
    """Lamp: edge ring, flat centre, glyph on the button's top crop (x 6..9, y 3..5); the lamp posts use the flat crop x 9..11, y 8..12."""
    c = rgb(STATE_COLOR[state])
    if state == "none":
        c = darken(c, 0.42)
    if state == "open" and frame == 1:
        c = darken(c, 0.55)  # the dim half of the blink
    img = blank(darken(c, 0.6))
    rect(img, 2, 2, 14, 14, darken(c, 0.82))
    rect(img, 3, 3, 13, 13, c)
    if state != "none":
        rect(img, 4, 4, 6, 6, lighten(c, 0.35))     # a highlight (kept off the glyph and the pylon crop)
    if state == "draft":
        for y in range(3, 13):                       # hatching: work in progress
            for x in range(3, 13):
                if (x + y) % 4 == 0:
                    put(img, x, y, darken(c, 0.55))
    if state == "merged":
        stamp(img, CHECK_GLYPH, 6, 3, darken(c, 0.35))
    if state == "open":
        rect(img, 6, 3, 10, 6, darken(c, 0.75) if frame == 0 else darken(c, 0.55))
        stamp(img, ["#..", ".#.", "#.."], 7, 3, lighten(c, 0.55) if frame == 0 else darken(c, 0.35))  # a ">" : go and look
    return img


def write_textures():
    os.makedirs(TEX, exist_ok=True)
    write_png(os.path.join(TEX, "merge_station_body.png"), tex_body())
    write_png(os.path.join(TEX, "merge_station_front.png"), tex_front())
    write_png(os.path.join(TEX, "merge_station_top.png"), tex_top())
    write_png(os.path.join(TEX, "merge_station_bezel.png"), tex_bezel())
    write_png(os.path.join(TEX, "merge_station_brass.png"), tex_brass())
    for s in STATES:
        write_png(os.path.join(TEX, "merge_station_screen_%s.png" % s), tex_screen(s))
        if s == "open":
            strip = tex_lamp(s, 0) + tex_lamp(s, 1)  # 16x32: two frames
            write_png(os.path.join(TEX, "merge_station_lamp_open.png"), strip)
            with open(os.path.join(TEX, "merge_station_lamp_open.png.mcmeta"), "w") as f:
                json.dump({"animation": {"frametime": 12}}, f, indent=2)
                f.write("\n")
        else:
            write_png(os.path.join(TEX, "merge_station_lamp_%s.png" % s), tex_lamp(s))


# ---------------------------------------------------------------- models
def auto_uv(face, frm, to):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    if face == "north":
        return [16 - x1, 16 - y1, 16 - x0, 16 - y0]
    if face == "south":
        return [x0, 16 - y1, x1, 16 - y0]
    if face == "east":
        return [16 - z1, 16 - y1, 16 - z0, 16 - y0]
    if face == "west":
        return [z0, 16 - y1, z1, 16 - y0]
    if face == "up":
        return [x0, z0, x1, z1]
    return [x0, 16 - z1, x1, 16 - z0]  # down


def element(frm, to, faces, emission=0):
    """faces: {face: texture or (texture, uv or None, cullface or None)}."""
    out = {}
    for face, spec in faces.items():
        if isinstance(spec, str):
            spec = (spec, None, None)
        tex, uv, cull = spec
        f = {"uv": uv if uv is not None else auto_uv(face, frm, to), "texture": tex}
        if cull:
            f["cullface"] = cull
        out[face] = f
    e = {"from": list(frm), "to": list(to), "faces": out}
    if emission:
        e["light_emission"] = emission
    return e


def model(state):
    em = EMISSION[state]
    side_uv = [0, 7, 16, 16]
    elements = [
        # cabinet
        element([0, 0, 0], [16, 9, 16], {
            "down": ("#body", [0, 0, 16, 16], "down"),
            "up": ("#top", [0, 0, 16, 16], None),
            "north": ("#front", side_uv, "north"),
            "south": ("#body", side_uv, "south"),
            "east": ("#body", side_uv, "east"),
            "west": ("#body", side_uv, "west"),
        }),
        # screen housing at the back
        element([1, 9, 7], [15, 16, 14], {
            "north": "#bezel", "south": "#bezel", "east": "#bezel", "west": "#bezel", "up": "#bezel",
        }),
        # the screen, a half pixel proud of the housing
        element([2, 10, 6.5], [14, 15, 7], {
            "north": ("#screen", [2, 1, 14, 6], None),
        }, emission=8 if state != "none" else 0),
        # brass lip along the front edge of the desk
        element([0, 9, 0], [16, 10, 1], {
            "north": ("#brass", [0, 6, 16, 7], None), "south": "#brass", "east": "#brass", "west": "#brass", "up": "#brass",
        }),
        # the button
        element([6, 9, 3], [10, 10, 6], {
            "north": "#lamp", "south": "#lamp", "east": "#lamp", "west": "#lamp", "up": ("#lamp", [6, 3, 10, 6], None),
        }, emission=em),
    ]
    # two lamp posts at the front corners of the desk (clear of the screen, so no text hides behind them): a lamp and a brass cap.
    # The post height is part of the state, so the four states differ in silhouette and not only in colour: none a stub, draft and
    # merged medium, open tall (6, the cap ends at the top of the block) (the loudest: a PR waits for you).
    h = POST_H[state]
    for x0 in (0, 14.5):
        elements.append(element([x0, 9, 2], [x0 + 1.5, 9 + h, 3.5], {
            "north": ("#lamp", [9, 8, 11, 8 + h], None), "south": ("#lamp", [9, 8, 11, 8 + h], None),
            "east": ("#lamp", [9, 8, 11, 8 + h], None), "west": ("#lamp", [9, 8, 11, 8 + h], None),
        }, emission=em))
        elements.append(element([x0, 9 + h, 2], [x0 + 1.5, 10 + h, 3.5], {
            "north": ("#brass", [0, 2, 2, 3], None), "south": ("#brass", [0, 2, 2, 3], None),
            "east": ("#brass", [0, 2, 2, 3], None), "west": ("#brass", [0, 2, 2, 3], None), "up": ("#brass", [0, 0, 2, 2], None),
        }))
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "t3craft:block/merge_station_body",
            "body": "t3craft:block/merge_station_body",
            "front": "t3craft:block/merge_station_front",
            "top": "t3craft:block/merge_station_top",
            "bezel": "t3craft:block/merge_station_bezel",
            "brass": "t3craft:block/merge_station_brass",
            "screen": "t3craft:block/merge_station_screen_%s" % state,
            "lamp": "t3craft:block/merge_station_lamp_%s" % state,
        },
        "elements": elements,
    }


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def write_models():
    for s in STATES:
        write_json(os.path.join(MODELS, "merge_station_%s.json" % s), model(s))
    variants = {}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rot.items():
        for s in STATES:
            v = {"model": "t3craft:block/merge_station_%s" % s}
            if y:
                v["y"] = y
            variants["facing=%s,pr=%s" % (facing, s)] = v
    write_json(os.path.join(BLOCKSTATES, "merge_station.json"), {"variants": variants})
    write_json(os.path.join(ITEMS, "merge_station.json"), {"model": {"type": "minecraft:model", "model": "t3craft:block/merge_station_none"}})


# ---------------------------------------------------------------- previews (enlarged textures, front and top views)
def scale_rows(rows, k):
    out = []
    for row in rows:
        big = [p for p in row for _ in range(k)]
        for _ in range(k):
            out.append(list(big))
    return out


def tex_cache():
    cache = {}

    def load(name):
        if name not in cache:
            rows = read_png(os.path.join(TEX, name + ".png"))
            cache[name] = rows[:16]  # first frame of a strip
        return cache[name]

    return load


def view(state, side, load, k=24):
    """Orthographic elevation: the north faces ('front') or the up faces ('top') of the model, painter's order."""
    m = model(state)
    tex = {key: val.split("/")[-1] for key, val in m["textures"].items()}
    img = [[(24, 28, 34, 255)] * (16 * k) for _ in range(16 * k)]
    els = m["elements"]
    if side == "front":
        els = sorted(els, key=lambda e: -e["from"][2])  # far first
        face = "north"
    else:
        els = sorted(els, key=lambda e: e["from"][1])   # low first
        face = "up"
    for e in els:
        f = e["faces"].get(face)
        if not f:
            continue
        frm, to = e["from"], e["to"]
        t = load(tex[f["texture"][1:]])
        u0, v0, u1, v1 = f["uv"]
        if side == "front":
            x0, x1, y0, y1 = 16 - to[0], 16 - frm[0], 16 - to[1], 16 - frm[1]   # viewer at north: right = -x
        else:
            x0, x1, y0, y1 = frm[0], to[0], frm[2], to[2]                        # top view: north up
        for py in range(int(y0 * k), int(y1 * k)):
            for pxx in range(int(x0 * k), int(x1 * k)):
                fx = (pxx / k - x0) / max(1e-6, x1 - x0)
                fy = (py / k - y0) / max(1e-6, y1 - y0)
                tu = u0 + fx * (u1 - u0)
                tv = v0 + fy * (v1 - v0)
                col = t[min(15, int(tv))][min(15, int(tu))]
                if 0 <= py < 16 * k and 0 <= pxx < 16 * k:
                    img[py][pxx] = col
    return img


def write_preview(directory):
    os.makedirs(directory, exist_ok=True)
    load = tex_cache()
    names = ["body", "front", "top", "bezel", "brass"] + ["screen_%s" % s for s in STATES] + ["lamp_%s" % s for s in STATES]
    k = 12
    sheet = []
    per_row = 5
    rows_of = [names[i:i + per_row] for i in range(0, len(names), per_row)]
    for group in rows_of:
        band = [[(16, 16, 20, 255)] * ((16 * k + 8) * per_row) for _ in range(16 * k + 8)]
        for i, n in enumerate(group):
            big = scale_rows(load("merge_station_" + n), k)
            for y in range(16 * k):
                for x in range(16 * k):
                    band[y + 4][i * (16 * k + 8) + x + 4] = big[y][x]
        sheet.extend(band)
    write_png(os.path.join(directory, "merge_station_textures.png"), sheet)
    for s in STATES:
        front = view(s, "front", load)
        top = view(s, "top", load)
        both = [front[y] + [(0, 0, 0, 255)] * 8 + top[y] for y in range(len(front))]
        write_png(os.path.join(directory, "merge_station_view_%s.png" % s), both)


def main(argv):
    write_textures()
    write_models()
    if "--preview" in argv:
        write_preview(argv[argv.index("--preview") + 1])
    print("merge station assets written")


if __name__ == "__main__":
    main(sys.argv[1:])
