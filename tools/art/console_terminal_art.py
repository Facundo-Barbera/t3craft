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
  console_terminal_screen  screen at x 2-13, y 3-7 (12 x 5 px = the glass), LED swatches at (0,8) amber and (2,8) accent
  console_terminal_screen_violet / _gold  the same screen with another title bar + LED (accent variants, weighted in the blockstate)
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
BEZEL_STEEL = (52, 58, 68)
TEAL = (64, 214, 208)
TEAL_MID = (32, 128, 136)
TEAL_DIM = (16, 62, 72)
AMBER = (255, 176, 64)
AMBER_DIM = (150, 96, 34)
SCREEN_BG = (8, 22, 30)
SCREEN_BG2 = (10, 29, 38)
FIELD = (12, 58, 66)
FIELD2 = (18, 84, 92)
TEAL_DIM2 = (40, 120, 128)
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
    """Monitor frame: mid steel with a lit top/left edge and a dark outline, so the screen reads as a monitor, not as a hole."""
    img = new(BEZEL_STEEL)
    for y in range(16):
        for x in range(16):
            img[y][x] = px(shade(BEZEL_STEEL, noise(x, y)))
    for i in range(16):
        img[0][i] = px(STEEL_HI)
        img[15][i] = px(STEEL_EDGE)
        img[i][0] = px(STEEL_HI)
        img[i][15] = px(STEEL_EDGE)
    for i in range(1, 15):
        img[1][i] = px(shade(BEZEL_STEEL, 12))
        img[14][i] = px(shade(BEZEL_STEEL, -14))
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


# accent variants: the title bar, the T3 tick and the LED swatch change; the field and the text stay teal so it still reads as a T3 console.
# name -> (title bar, tick, led).  "" is the T3 teal one (the first variant of the blockstate).
ACCENTS = {
    "": (TEAL_MID, TEAL_HI, TEAL),
    "violet": ((110, 88, 200), (222, 208, 255), (170, 140, 255)),
    "gold": ((176, 124, 40), (255, 230, 170), (255, 200, 90)),
}


def screen(accent=""):
    """Glass: x 2-13, y 3-7 (12 x 5). A structured terminal UI on a flat teal field: a title bar (in the accent colour), a prompt
    line (chevron, amber cursor), two text lines of different length, nothing random. Reads as an interface, not noise."""
    bar, tick, led = ACCENTS[accent]
    img = new(BEZEL)
    gx, gy = 2, 3
    W, H = 12, 5
    for y in range(H):
        for x in range(W):
            img[gy + y][gx + x] = px(FIELD)

    def put(dx, dy, c):
        img[gy + dy][gx + dx] = px(c)

    # row 0: title bar with a bright tick at the left
    for dx in range(W):
        put(dx, 0, bar)
    put(0, 0, tick)
    put(1, 0, tick)
    # row 1: text line, long
    for dx in range(1, 10):
        put(dx, 1, TEAL)
    # row 2: text line, short
    for dx in range(1, 6):
        put(dx, 2, TEAL)
    # row 3: prompt: chevron + amber cursor
    put(1, 3, TEAL_HI)
    put(3, 3, AMBER)
    put(4, 3, AMBER)
    # row 4: status line, dim, with an amber status pixel at the right
    for dx in range(1, 8):
        put(dx, 4, TEAL_DIM2)
    put(10, 4, AMBER)
    # LED swatches for the model's two chin lights: amber at (0,8), the accent at (2,8)
    for (x, y) in ((0, 8), (1, 8), (0, 9), (1, 9)):
        img[y][x] = px(AMBER)
    for (x, y) in ((2, 8), (3, 8), (2, 9), (3, 9)):
        img[y][x] = px(led)
    return img


# ---------------------------------------------------------------- model
def face(tex, uv, cull=None):
    f = {"uv": uv, "texture": "#" + tex}
    if cull:
        f["cullface"] = cull
    return f


def box6(frm, to, faces, emission=0, rotation=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    if emission:
        e["light_emission"] = emission
    return e


def model(accent=""):
    """Desk + a slim monitor: foot plate, neck, a thin frame leaning back 8 degrees with a chin, a small rear shell. The hitbox
    (ConsoleTerminalBlock: cabinet 0-7 + a 2-14 x 7-15 x 8-13 volume) is untouched: everything stays inside it."""
    side = [0, 9, 16, 16]
    lean = {"angle": 8, "axis": "x", "origin": [8, 9.5, 9.6]}
    scr = "screen"
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
            "from": [1, 4, 1], "to": [15, 8.5, 7],
            "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 7.75, 4]},
            "faces": {
                "up": face("keys", [1, 0, 15, 6]),
                "north": face("body", [1, 9, 15, 13.5]),
                "south": face("body", [1, 9, 15, 13.5]),
                "west": face("body", [0, 9, 6, 13.5]),
                "east": face("body", [10, 9, 16, 13.5]),
                "down": face("body", [1, 0, 15, 6]),
            },
        },
        # --- the monitor stand: a flat foot plate, a neck
        box6([4.5, 7, 8.6], [11.5, 7.7, 12.4], {
            "up": face("top", [4, 8, 12, 12]), "north": face("body", [4, 9, 12, 10]), "south": face("body", [4, 9, 12, 10]),
            "west": face("body", [8, 9, 12, 10]), "east": face("body", [8, 9, 12, 10]),
        }),
        box6([6.8, 7.7, 10], [9.2, 10.5, 11.6], {
            "north": face("body", [6, 6, 9, 10]), "south": face("body", [6, 6, 9, 10]),
            "west": face("body", [8, 6, 10, 10]), "east": face("body", [8, 6, 10, 10]),
        }),
        # --- the monitor: a thin frame (1.5 px) leaning back; width 14 (bezel 1 px each side), 1 px chin below the glass
        box6([1, 9.5, 9.3], [15, 16, 10.8], {
            "north": face("bezel", [1, 1, 15, 7.5]),
            "south": face("body", [1, 1, 15, 7.5]),
            "up": face("bezel", [1, 0, 15, 2]),
            "down": face("body", [1, 0, 15, 2]),
            "west": face("bezel", [0, 1, 2, 7.5]),
            "east": face("bezel", [0, 1, 2, 7.5]),
        }, rotation=lean),
        # rear shell: a narrower, slightly deeper back so the profile is a tapered LCD and not a slab
        box6([3.5, 10.4, 10.8], [12.5, 14.6, 12], {
            "south": face("body", [3, 3, 12, 8]), "north": face("body", [3, 3, 12, 8]),
            "up": face("top", [3, 3, 12, 5]), "down": face("body", [3, 3, 12, 5]),
            "west": face("body", [3, 3, 5, 8]), "east": face("body", [3, 3, 5, 8]),
        }, rotation=lean),
        # the glass, 12 x 5, just in front of the bezel (emissive)
        box6([2, 10.5, 9.2], [14, 15.5, 9.3], {"north": face(scr, [2, 3, 14, 8])}, emission=15, rotation=lean),
        # two chin LEDs (emissive): amber left, the accent right
        box6([2.3, 9.7, 9.2], [3.8, 10.3, 9.3], {"north": face(scr, [0, 8, 2, 9])}, emission=15, rotation=lean),
        box6([12.2, 9.7, 9.2], [13.7, 10.3, 9.3], {"north": face(scr, [2, 8, 4, 9])}, emission=15, rotation=lean),
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
            "screen": "t3craft:block/console_terminal_screen" + ("_" + accent if accent else ""),
        },
        "elements": elements,
    }


def blockstate():
    """Orientation by `facing`; the accent is a weighted pick among three models (the game picks one per position, so a bank of
    consoles is not three clones). No new property, so ConsoleTerminalBlock and the kernel are untouched."""
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        lst = []
        for accent in ACCENTS:
            v = {"model": "t3craft:block/console_terminal" + ("_" + accent if accent else ""), "weight": 1}
            if y:
                v["y"] = y
            lst.append(v)
        variants["facing=" + facing] = lst
    return {"variants": variants}


# ---------------------------------------------------------------- review sheets
def main():
    tex = {"body": body(), "front": front(), "top": top(), "keys": keys(), "bezel": bezel()}
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    for name, img in tex.items():
        write_png(os.path.join(TEX, "console_terminal_%s.png" % name), img)
    for accent in ACCENTS:
        img = screen(accent)
        tex["screen" + ("_" + accent if accent else "")] = img
        write_png(os.path.join(TEX, "console_terminal_screen%s.png" % ("_" + accent if accent else "")), img)
        with open(os.path.join(MODELS, "console_terminal%s.json" % ("_" + accent if accent else "")), "w") as f:
            json.dump(model(accent), f, indent=2)
            f.write("\n")
    with open(os.path.join(ROOT, "blockstates", "console_terminal.json"), "w") as f:
        json.dump(blockstate(), f, indent=2)
        f.write("\n")
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        names = list(tex)
        sheet = [[(18, 20, 24, 255)] * (len(names) * 17 * 12) for _ in range(16 * 12)]
        for n, name in enumerate(names):
            big = magnify(tex[name], 12)
            for y, row in enumerate(big):
                for x, p in enumerate(row):
                    sheet[y][n * 17 * 12 + x] = p
        write_png(os.path.join(out, "textures.png"), sheet)
        print("preview:", out)
    print("wrote", len(tex), "textures and", len(ACCENTS), "models")


if __name__ == "__main__":
    main()
