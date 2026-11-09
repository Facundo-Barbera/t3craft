#!/usr/bin/env python3
"""kit/glow-panel: generates the textures, models, blockstates, item definitions and loot tables of the two
emissive kit blocks, `t3craft:glow_strip` and `t3craft:glow_panel`.

Standard library only (zlib/struct), light enough for the VPS. Deterministic: re-running gives byte-identical files.
Run from the repo root:

    python3 tools/art/glow_art.py                 # writes the assets
    python3 tools/art/glow_art.py --preview DIR   # also writes enlarged PNGs (x12) to review the art by eye

Original pixel art and original models: AgentCraft (https://github.com/blendi-remade/agentcraft, MIT) has a
`glow_panel` and a `glow_strip` block, and that is the only thing taken from it: the idea. No texture, model or
code of AgentCraft is used (see THIRD_PARTY_NOTICES.md).

Design
  * Palette of the citadel: deepslate frame (dark, heavy), a 1 px cherry trim, and the light itself in two tones:
    warm = paper/amber, cool = T3 cyan.
  * glow_panel: a full cube whose `facing` face is a recessed diffuser (deepslate bevel, cherry trim, a prismatic
    louvred light field); the other five faces are plain deepslate. A ceiling or wall block that happens to be a light.
  * glow_strip: a bar 16 long, 3 high (the light) and 2 deep, hugging one edge of its cell: `facing` is where it looks,
    the wall it hugs is behind it, `half` = bottom (floor, skirting, step edge) or top (under a screen, under a beam,
    cove). The front and the face that looks along the run glow (emission 15 per element); the rear, the other long
    face and the two ends are a dark channel. The ends are plain faces, so a run of strips reads as one continuous
    bar (the faces where two strips meet are hidden inside the bar) and only the two ends of the run show their cap.
  * texture layout of the strip (16 x 16): rows 0-2 front (16 x 3), rows 4-5 lit long face (16 x 2), rows 8-10 end cap
    (x 0-1), rows 12-14 dark channel (16 x 3; the first 2 rows are used for the 2 deep faces).
"""
import argparse
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mod", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "t3craft")
DATA = os.path.join(ROOT, "data", "t3craft")
TEX = os.path.join(ASSETS, "textures", "block")


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


# ---- palette -----------------------------------------------------------------------------------
DEEPSLATE = [hexc(c) for c in ("#4A4E58", "#3A3D45", "#2B2D33", "#202228", "#15161A")]  # light .. dark
CHERRY = [hexc(c) for c in ("#D49A8C", "#C2867A", "#A86B62", "#8A5650")]
TONES = {
    # core (hot) .. edge (rim)
    "warm": [hexc(c) for c in ("#FFF8E2", "#FFEFC2", "#FFDD93", "#F5B84A", "#C98A2E")],
    "cool": [hexc(c) for c in ("#EFFFFE", "#C4F7F4", "#8DEBE6", "#4FB8B4", "#2F8A88")],
}


def write_png(path, rows):
    h = len(rows)
    w = len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def blank(w=16, h=16, fill=(0, 0, 0, 0)):
    return [[fill for _ in range(w)] for _ in range(h)]


def lcg(seed):
    """Tiny deterministic generator (no `random` module: the output must not depend on the Python version)."""
    s = seed & 0x7FFFFFFF

    def nxt(n):
        nonlocal s
        s = (s * 1103515245 + 12345) & 0x7FFFFFFF
        return (s >> 8) % n

    return nxt


# ---- textures ------------------------------------------------------------------------------------
def frame_texture():
    """Plain deepslate (sides of the panel): noise in three tones and a darker seam on the border."""
    img = blank()
    rnd = lcg(7)
    for y in range(16):
        for x in range(16):
            v = rnd(10)
            img[y][x] = DEEPSLATE[2] if v < 5 else (DEEPSLATE[1] if v < 8 else DEEPSLATE[3])
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            img[y][x] = DEEPSLATE[3]
    return img


def panel_front(tone):
    """16 x 16 front of the panel: deepslate bevel (2 px), cherry trim (1 px), 10 x 10 prismatic diffuser."""
    t = TONES[tone]
    img = blank()
    # bevel: light on the top and left edges, dark on the bottom and right, noise inside
    rnd = lcg(11)
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            if ring == 0:
                c = DEEPSLATE[1] if (x == 0 or y == 0) and x != 15 and y != 15 else DEEPSLATE[4]
            elif ring == 1:
                c = DEEPSLATE[2] if rnd(4) else DEEPSLATE[1]
            elif ring == 2:
                # cherry trim: lit on the top and left, shaded on the bottom and right
                lit_edge = (x == 2 or y == 2) and x != 13 and y != 13
                if lit_edge:
                    c = CHERRY[0] if (x + y) % 3 == 0 else CHERRY[1]
                else:
                    c = CHERRY[2] if (x + y) % 3 == 0 else CHERRY[3]
            else:
                c = None
            if c is not None:
                img[y][x] = c
    # diffuser 3..12: bright core, falling off to the amber/cyan rim; fine horizontal louvres every third row
    for y in range(3, 13):
        for x in range(3, 13):
            d = max(abs(x - 7.5), abs(y - 7.5))  # 0.5 .. 4.5
            # a wide bright field (an office light is flat, not a gem), softer towards the edge
            idx = 0 if d <= 3.2 else 1
            if y in (5, 10):  # two fine louvre lines, symmetric about the middle
                idx = 2
            img[y][x] = t[idx]
    # the diffuser's rim (outer ring of the field) takes the mid tone, the four corners the deep one
    for i in range(3, 13):
        for (x, y) in ((i, 3), (i, 12), (3, i), (12, i)):
            img[y][x] = t[2]
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img[y][x] = t[3]
    return img


def strip_texture(tone):
    t = TONES[tone]
    img = blank()
    # rows 0-2: front. dark lip (frame), hot core, amber under-glow
    for x in range(16):
        img[0][x] = DEEPSLATE[1]
        img[1][x] = t[0]
        img[2][x] = t[2]
    # rows 4-5: the lit long face
    for x in range(16):
        img[4][x] = t[1]
        img[5][x] = t[0]
    # rows 8-10, x 0-1: end cap, a dark bezel with a lit pinhole
    for y in range(8, 11):
        for x in range(2):
            img[y][x] = DEEPSLATE[2]
    img[8][0] = DEEPSLATE[0]
    img[8][1] = DEEPSLATE[1]
    img[9][0] = DEEPSLATE[1]
    img[9][1] = t[3]
    img[10][0] = DEEPSLATE[3]
    img[10][1] = DEEPSLATE[4]
    # rows 12-14: the dark channel (back, the other long face)
    rnd = lcg(23)
    for y in range(12, 15):
        for x in range(16):
            img[y][x] = DEEPSLATE[2] if rnd(4) else DEEPSLATE[3]
    for x in range(16):
        img[14][x] = DEEPSLATE[4]
    return img


# ---- models --------------------------------------------------------------------------------------
def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv is not None:
        f["uv"] = uv
    if cull is not None:
        f["cullface"] = cull
    return f


def panel_model(tone):
    """Full cube, `north` is the lit face (the blockstate turns it)."""
    return {
        "parent": "minecraft:block/block",
        "textures": {"front": "t3craft:block/glow_panel_" + tone, "frame": "t3craft:block/kit_glow_frame",
                     "particle": "t3craft:block/kit_glow_frame"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16],
             "faces": {"south": face("#frame", cull="south"), "east": face("#frame", cull="east"),
                       "west": face("#frame", cull="west"), "up": face("#frame", cull="up"),
                       "down": face("#frame", cull="down")}},
            {"from": [0, 0, 0], "to": [16, 16, 16],
             "faces": {"north": face("#front", cull="north")}, "light_emission": 15},
        ],
    }


def strip_model(tone, half):
    """Strip looking north, hugging the south wall (z 14..16). bottom: y 0..3; top: y 13..16 (uv flipped)."""
    y0, y1 = (0, 3) if half == "bottom" else (13, 16)
    flip = half == "top"
    front_uv = [0, 3, 16, 0] if flip else [0, 0, 16, 3]
    end_uv = [2, 11, 0, 8] if flip else [0, 8, 2, 11]
    # the long face that looks along the run: up for a bottom strip, down for a top one
    lit_side, dark_side = ("up", "down") if half == "bottom" else ("down", "up")
    lit_cull = None
    dark_cull = "down" if half == "bottom" else "up"
    return {
        "parent": "minecraft:block/block",
        "textures": {"t": "t3craft:block/glow_strip_" + tone, "particle": "t3craft:block/kit_glow_frame"},
        "elements": [
            {"from": [0, y0, 14], "to": [16, y1, 16],
             "faces": {"south": face("#t", [0, 12, 16, 15], "south"),
                       dark_side: face("#t", [0, 12, 16, 14], dark_cull),
                       "east": face("#t", end_uv), "west": face("#t", end_uv)}},
            {"from": [0, y0, 14], "to": [16, y1, 16],
             "faces": {"north": face("#t", front_uv), lit_side: face("#t", [0, 4, 16, 6], lit_cull)},
             "light_emission": 15},
        ],
    }


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


HORIZONTAL = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]


def panel_blockstate():
    v = {}
    rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    for facing in ("down", "east", "north", "south", "up", "west"):
        for tone in ("cool", "warm"):
            e = {"model": "t3craft:block/glow_panel_" + tone}
            e.update(rot[facing])
            v["facing=%s,tone=%s" % (facing, tone)] = e
    return {"variants": v}


def strip_blockstate():
    v = {}
    for facing, y in sorted(HORIZONTAL):
        for half in ("bottom", "top"):
            for tone in ("cool", "warm"):
                e = {"model": "t3craft:block/glow_strip_%s_%s" % (half, tone)}
                if y:
                    e["y"] = y
                v["facing=%s,half=%s,tone=%s" % (facing, half, tone)] = e
    return {"variants": v}


def loot(name):
    return {"type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "t3craft:" + name}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": "t3craft:blocks/" + name}


def item(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def build():
    for tone in ("warm", "cool"):
        write_png(os.path.join(TEX, "glow_panel_%s.png" % tone), panel_front(tone))
        write_png(os.path.join(TEX, "glow_strip_%s.png" % tone), strip_texture(tone))
        write_json(os.path.join(ASSETS, "models", "block", "glow_panel_%s.json" % tone), panel_model(tone))
        for half in ("bottom", "top"):
            write_json(os.path.join(ASSETS, "models", "block", "glow_strip_%s_%s.json" % (half, tone)), strip_model(tone, half))
    write_png(os.path.join(TEX, "kit_glow_frame.png"), frame_texture())
    write_json(os.path.join(ASSETS, "blockstates", "glow_panel.json"), panel_blockstate())
    write_json(os.path.join(ASSETS, "blockstates", "glow_strip.json"), strip_blockstate())
    write_json(os.path.join(ASSETS, "items", "glow_panel.json"), item("t3craft:block/glow_panel_warm"))
    write_json(os.path.join(ASSETS, "items", "glow_strip.json"), item("t3craft:block/glow_strip_bottom_warm"))
    for name in ("glow_panel", "glow_strip"):
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), loot(name))


# ---- preview -------------------------------------------------------------------------------------
def scaled(img, k):
    return [[px for px in row for _ in range(k)] for row in img for _ in range(k)]


def paste(dst, src, ox, oy):
    for y, row in enumerate(src):
        for x, px in enumerate(row):
            if px[3] and 0 <= oy + y < len(dst) and 0 <= ox + x < len(dst[0]):
                dst[oy + y][ox + x] = px


def crop(img, x0, y0, x1, y1):
    return [row[x0:x1] for row in img[y0:y1]]


def preview(outdir):
    k = 12
    bg = hexc("#1A1B1F")
    sheet = blank(16 * k * 4 + 40, 16 * k * 3 + 40, bg)
    ox = 8
    for i, tone in enumerate(("warm", "cool")):
        p = panel_front(tone)
        # 2 x 1 panels to judge the repeat and the frame
        for j in range(2):
            paste(sheet, scaled(p, k), ox + j * 16 * k, 8 + i * 16 * k)
    # strips: four tiles in a row (a continuous run), front rows and lit long face
    for i, tone in enumerate(("warm", "cool")):
        s = strip_texture(tone)
        front = crop(s, 0, 0, 16, 3)
        lit = crop(s, 0, 4, 16, 6)
        run_x = 16 * k * 2 + 24
        for j in range(2):
            paste(sheet, scaled(front, k), run_x + j * 16 * k, 8 + i * 6 * k)
            paste(sheet, scaled(lit, k), run_x + j * 16 * k, 8 + i * 6 * k + 4 * k)
    paste(sheet, scaled(frame_texture(), k), 8, 8 + 2 * 16 * k + 8)
    paste(sheet, scaled(crop(strip_texture("warm"), 0, 8, 2, 11), k * 2), 8 + 16 * k + 16, 8 + 2 * 16 * k + 8)
    paste(sheet, scaled(crop(strip_texture("warm"), 0, 12, 16, 15), k), 8 + 16 * k + 80, 8 + 2 * 16 * k + 8)
    write_png(os.path.join(outdir, "glow_sheet.png"), sheet)
    for tone in ("warm", "cool"):
        write_png(os.path.join(outdir, "glow_panel_%s_x%d.png" % (tone, k)), scaled(panel_front(tone), k))
        write_png(os.path.join(outdir, "glow_strip_%s_x%d.png" % (tone, k)), scaled(strip_texture(tone), k))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", metavar="DIR", help="also write enlarged PNGs of the textures into DIR")
    args = ap.parse_args()
    build()
    if args.preview:
        preview(args.preview)
    print("glow_strip / glow_panel assets written")
