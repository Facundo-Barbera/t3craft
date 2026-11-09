#!/usr/bin/env python3
"""kit/console-terminal: generates the textures and the block model of t3craft:console_terminal.

Standard library only (zlib/struct), light enough for the VPS. Run from the repo root:

    python3 tools/art/console_terminal_art.py            # textures + model
    python3 tools/art/console_terminal_art.py --preview /tmp/console_preview   # also magnified PNGs to review by eye

The block is a command desk: a slate steel cabinet (vent slits and a teal LED panel on the front), a slanted keyboard
(a wedge, -22.5 degrees like the vanilla lectern's top) with a lit space bar and an amber Enter key, two indicator
LEDs, and an upright teal-lit screen in a dark bezel that shows a T3-teal prompt ("> _" with an amber cursor and a status
line). The screen and the LEDs are emissive in the model ("light_emission"). Original pixel art: the idea of a console
terminal comes from AgentCraft (MIT), nothing was copied.

Texture layout (all 16x16):
  console_terminal_body    slate steel plate: cabinet sides/back/bottom (rows 9-15 are the 7 px the cabinet shows), housing
  console_terminal_front   cabinet front, rows 9-15: vents + LED panel
  console_terminal_top     slate top plate: cabinet top, housing top
  console_terminal_keys    keyboard top: region x 1-15, y 0-8 (14 x 8 px = the wedge's top)
  console_terminal_bezel   dark bezel: housing front
  console_terminal_screen  screen at x 2-13, y 3-7 (12 x 5 px = the glass), LED swatches at (0,8) amber and (2,8) teal
"""
import json
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mod", "src", "main", "resources", "assets", "t3craft")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")


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


def px(c):
    return (c[0], c[1], c[2], 255)


def new(fill):
    return [[px(fill)] * 16 for _ in range(16)]


def magnify(img, k):
    return [[p for p in row for _ in range(k)] for row in img for _ in range(k)]


# ---------------------------------------------------------------- palette (slate steel + T3 teal + amber)
STEEL = (62, 69, 80)
STEEL_HI = (84, 93, 106)
STEEL_LO = (44, 49, 58)
STEEL_EDGE = (30, 34, 41)
BEZEL = (22, 25, 31)
BEZEL_HI = (38, 43, 51)
TEAL = (64, 214, 208)
TEAL_MID = (32, 128, 136)
TEAL_DIM = (16, 62, 72)
AMBER = (255, 176, 64)
AMBER_DIM = (150, 96, 34)
SCREEN_BG = (8, 22, 30)
SCREEN_BG2 = (10, 29, 38)
FIELD = (14, 70, 78)
FIELD2 = (18, 84, 92)
TEAL_HI = (180, 255, 250)
TEAL_MID2 = (96, 190, 190)
KEY = (116, 126, 142)
KEY_ALT = (98, 108, 124)
DECK = (24, 28, 34)


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def noise(x, y):
    """Deterministic +-2 grain so flat plates do not look like a fill."""
    h = (x * 73856093) ^ (y * 19349663)
    h = (h ^ (h >> 7)) & 7
    return (-2, -1, 0, 0, 0, 1, 1, 2)[h]


def plate(border=STEEL_EDGE, hi=STEEL_HI, fill=STEEL):
    img = new(fill)
    for y in range(16):
        for x in range(16):
            img[y][x] = px(shade(fill, noise(x, y)))
    for i in range(16):
        img[0][i] = px(border)
        img[15][i] = px(border)
        img[i][0] = px(border)
        img[i][15] = px(border)
    for i in range(1, 15):
        img[1][i] = px(hi)
        img[i][1] = px(shade(hi, -14))
        img[14][i] = px(shade(fill, -12))
    return img


def body():
    img = plate()
    # rivets in the four corners
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img[y][x] = px(STEEL_HI)
        img[y + 1][x + 1] = px(STEEL_EDGE)
    return img


def front():
    """The cabinet front is the 16 x 7 crop rows 9-15 (model uv [0,9,16,16])."""
    img = plate()
    # vent slits on the left: two dark slots with a lit lip between them
    for y in (10, 13):
        for x in range(2, 7):
            img[y][x] = px(STEEL_EDGE)
    for x in range(2, 7):
        img[11][x] = px(shade(STEEL, 8))
        img[12][x] = px(shade(STEEL, -6))
    # LED panel on the right: dark frame, teal-dim glass, one bright line
    for y in range(10, 15):
        for x in range(8, 14):
            img[y][x] = px(STEEL_EDGE)
    for y in range(11, 14):
        for x in range(9, 13):
            img[y][x] = px(TEAL_DIM)
    for x in range(9, 13):
        img[12][x] = px(TEAL)
    img[11][9] = px(TEAL_MID)
    img[13][12] = px(TEAL_MID)
    return img


def top():
    img = plate(hi=shade(STEEL_HI, 8), fill=shade(STEEL, 6))
    return img


def bezel():
    img = new(BEZEL)
    for y in range(16):
        for x in range(16):
            img[y][x] = px(shade(BEZEL, noise(x, y)))
    for i in range(16):
        img[0][i] = px(shade(BEZEL, -8))
        img[15][i] = px(shade(BEZEL, -8))
        img[i][0] = px(shade(BEZEL, -8))
        img[i][15] = px(shade(BEZEL, -8))
    for i in range(1, 15):
        img[1][i] = px(BEZEL_HI)
    return img


def keys():
    """Keyboard top, region x 1-14, y 0-5 (14 x 6). Row 0 is the north (player) edge: the space bar row; then two rows of keys."""
    img = new(DECK)
    for y in range(16):
        for x in range(16):
            img[y][x] = px(shade(DECK, noise(x, y)))
    # y 0: modifier keys and the lit space bar
    for x in (1, 2, 12, 13, 14):
        img[0][x] = px(KEY_ALT)
    for x in range(4, 11):
        img[0][x] = px(TEAL_MID)
    for x in range(5, 10):
        img[0][x] = px(TEAL)
    # y 2 and y 4: 2 px keys with a 1 px gap, staggered like a real keyboard
    for y, off in ((2, 1), (4, 2)):
        x = off
        k = 0
        while x + 1 <= 13:
            c = KEY if k % 2 == 0 else KEY_ALT
            img[y][x] = px(c)
            img[y][x + 1] = px(c)
            x += 3
            k += 1
    # Enter: amber, right end of the first row of keys
    img[2][12] = px(AMBER)
    img[2][13] = px(AMBER)
    return img


def screen():
    """Glass: x 2-13, y 3-7 (12 x 5). A teal-lit terminal: lit field, chevron prompt, amber cursor, text lines, status bar.
    The field is mid-teal (not near black) so the screen reads as a lit rectangle from 6+ blocks."""
    img = new(BEZEL)
    gx, gy = 2, 3
    W, H = 12, 5
    for y in range(H):
        for x in range(W):
            img[gy + y][gx + x] = px(FIELD2 if y % 2 else FIELD)

    def put(dx, dy, c):
        img[gy + dy][gx + dx] = px(c)

    # row 0: chevron top, bright title text
    put(0, 0, TEAL_HI)
    for dx in (3, 4, 5, 6, 7):
        put(dx, 0, TEAL_HI)
    # row 1: chevron tip, amber cursor
    put(1, 1, TEAL_HI)
    put(3, 1, AMBER)
    put(4, 1, AMBER)
    # row 2: chevron bottom, dim text
    put(0, 2, TEAL_HI)
    for dx in (4, 5, 6, 8, 9):
        put(dx, 2, TEAL)
    # row 3: more text
    for dx in (1, 2, 3, 5, 6, 7, 8):
        put(dx, 3, TEAL_MID2)
    # row 4: status bar with a bright tick
    for dx in range(W):
        put(dx, 4, TEAL_MID)
    put(9, 4, TEAL_HI)
    put(10, 4, TEAL_HI)
    put(11, 4, TEAL_HI)
    # LED swatches for the model's two indicator lights
    for (x, y) in ((0, 8), (1, 8), (0, 9), (1, 9)):
        img[y][x] = px(AMBER)
    for (x, y) in ((2, 8), (3, 8), (2, 9), (3, 9)):
        img[y][x] = px(TEAL)
    return img


# ---------------------------------------------------------------- model
def face(tex, uv, cull=None):
    f = {"uv": uv, "texture": "#" + tex}
    if cull:
        f["cullface"] = cull
    return f


def model():
    side = [0, 9, 16, 16]
    elements = [
        {   # cabinet
            "from": [0, 0, 0], "to": [16, 7, 16],
            "faces": {
                "down": face("body", [0, 0, 16, 16], "down"),
                "up": face("top", [0, 0, 16, 16]),
                "north": face("front", side, "north"),
                "south": face("body", side, "south"),
                "west": face("body", side, "west"),
                "east": face("body", side, "east"),
            },
        },
        {   # slanted keyboard: a wedge tilted like the vanilla lectern's top; the front edge sits just above the cabinet top
            "from": [1, 7, 1], "to": [15, 8.5, 7],
            "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 7.75, 4]},
            "faces": {
                "up": face("keys", [1, 0, 15, 6]),
                "north": face("body", [1, 12, 15, 14]),
                "south": face("body", [1, 12, 15, 14]),
                "west": face("body", [0, 12, 6, 14]),
                "east": face("body", [10, 12, 16, 14]),
                "down": face("body", [1, 0, 15, 6]),
            },
        },
        {   # screen housing
            "from": [1, 7, 8], "to": [15, 15, 13],
            "faces": {
                "up": face("top", [1, 3, 15, 8]),
                "north": face("bezel", [1, 1, 15, 9]),
                "south": face("body", [1, 1, 15, 9]),
                "west": face("body", [3, 1, 8, 9]),
                "east": face("body", [8, 1, 13, 9]),
            },
        },
        {   # indicator LEDs on top of the housing (emissive): amber and teal
            "from": [2.5, 15, 9.5], "to": [4, 15.8, 11],
            "faces": {
                "up": face("screen", [0, 8, 2, 10]),
                "north": face("screen", [0, 8, 2, 10]),
                "south": face("screen", [0, 8, 2, 10]),
                "west": face("screen", [0, 8, 2, 10]),
                "east": face("screen", [0, 8, 2, 10]),
            },
            "light_emission": 15,
        },
        {
            "from": [12, 15, 9.5], "to": [13.5, 15.8, 11],
            "faces": {
                "up": face("screen", [2, 8, 4, 10]),
                "north": face("screen", [2, 8, 4, 10]),
                "south": face("screen", [2, 8, 4, 10]),
                "west": face("screen", [2, 8, 4, 10]),
                "east": face("screen", [2, 8, 4, 10]),
            },
            "light_emission": 15,
        },
        {   # the glass, 12 x 5, just in front of the bezel (emissive)
            "from": [2, 9, 7.9], "to": [14, 14, 8],
            "faces": {
                "north": face("screen", [2, 3, 14, 8]),
            },
            "light_emission": 15,
        },
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "t3craft:block/console_terminal_body",
            "body": "t3craft:block/console_terminal_body",
            "front": "t3craft:block/console_terminal_front",
            "top": "t3craft:block/console_terminal_top",
            "keys": "t3craft:block/console_terminal_keys",
            "bezel": "t3craft:block/console_terminal_bezel",
            "screen": "t3craft:block/console_terminal_screen",
        },
        "elements": elements,
    }


# ---------------------------------------------------------------- review sheets
def elevation(tex):
    """A rough front elevation (units x 8 px): cabinet front, keyboard strip, bezel with glass. Not a render, a layout check."""
    k = 8
    W, H = 16 * k, 16 * k
    canvas = [[(18, 20, 24, 255)] * W for _ in range(H)]

    def blit(img, ux, uy, uw, uh, dx, dy_top):
        # img crop (ux,uy,uw,uh) pasted with its top-left at model x=dx, model y (from the top) = dy_top, at k px per texel
        for j in range(uh):
            for i in range(uw):
                p = img[uy + j][ux + i]
                for b in range(k):
                    for a in range(k):
                        yy = (dy_top + j) * k + b
                        xx = (dx + i) * k + a
                        if 0 <= yy < H and 0 <= xx < W:
                            canvas[yy][xx] = p

    # cabinet front: model y 0..7 -> from the top 9..16
    blit(tex["front"], 0, 9, 16, 7, 0, 9)
    # keyboard top seen from the front: 1 unit strip (rows 0..) is too thin; show keys as a strip above the cabinet
    blit(tex["keys"], 1, 0, 14, 1, 1, 8)
    # housing bezel: x 2..14, model y 7..14.5 -> top 1.5..9: use 12 x 7
    blit(tex["bezel"], 2, 1, 12, 7, 2, 1)
    # glass: x 3..13, y 9.5..13.5 -> top 2.5..6.5
    blit(tex["screen"], 3, 4, 10, 4, 3, 3)
    return canvas


def main():
    tex = {"body": body(), "front": front(), "top": top(), "keys": keys(), "bezel": bezel(), "screen": screen()}
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    for name, img in tex.items():
        write_png(os.path.join(TEX, "console_terminal_%s.png" % name), img)
    with open(os.path.join(MODELS, "console_terminal.json"), "w") as f:
        json.dump(model(), f, indent=2)
        f.write("\n")
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        # one sheet: the six textures side by side at 12x
        names = list(tex)
        sheet = [[(18, 20, 24, 255)] * (len(names) * 17 * 12) for _ in range(16 * 12)]
        for n, name in enumerate(names):
            big = magnify(tex[name], 12)
            for y, row in enumerate(big):
                for x, p in enumerate(row):
                    sheet[y][n * 17 * 12 + x] = p
        write_png(os.path.join(out, "textures.png"), sheet)
        write_png(os.path.join(out, "elevation.png"), elevation(tex))
        print("preview:", out)
    print("wrote", len(tex), "textures and the model")


if __name__ == "__main__":
    main()
