#!/usr/bin/env python3
"""Draws the 16x16 textures of t3craft's device blocks (monitor, status lamp, board lamp, archive
terminal, thread board) in the "citadel" palette: deepslate lacquer casings, fresh and oxidized
copper trims, cherry wood, teal t3 accent.

Original pixel art for t3craft (MIT): nothing is copied or derived from Mojang's textures.
Fully deterministic, no randomness at all: every pixel comes from a hand-written grid or a structured
formula. Stdlib only (zlib + struct), so re-running it gives byte-identical PNGs:

    python3 mod/tools/device_textures.py

Light comes from the top-left: top/left edges catch light, bottom/right edges fall into shadow.

Layout constraints kept from the renderers / models (do not move these without checking the code):
- monitor_front: the 1 px outer ring is the bezel frame. The connected-screen edge models
  (monitor_edge_*.json) sample rows 0/15 (cols 2..14) and cols 0/15 (rows 2..14) as their frame
  strip, and MonitorRenderer draws its screen from 1 model px inside the edge, so pixels 1..14
  stay a dark recessed screen.
- monitor_screen: the front of a cell inside a merged screen wall; borderless and tileable.
- monitor_side: pixel (0,0) is the 1x1 uv of the edge strips' side faces.
- status/board lamps: LampRenderer adds its glow over the lens at pixels 3..12 (quad 3..13) on
  every horizontal face, so the glass lens stays inside that square, centred. Status colours keep
  LampStatus' meaning: off, idle (grey), working (cyan), attention (amber), done (green), error (red).
- archive_terminal: the base front/sides sample rows 9..15 of body/side, the screen housing samples
  rows 0..8, the top shows rows 0..5 in front of the housing (keyboard); ArchiveTerminalRenderer
  draws over the whole housing front on a 0xFF0B1418 background, so the screen stays that glass.
- thread_board_front: ThreadBoardRenderer covers the face (brass rim, walnut, lanes); the texture
  is the idle look with the same lane/card layout.
"""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "t3craft",
                   "textures", "block")


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def ramp(*cs):
    return [hexc(c) for c in cs]


# ---- palette (index 0 = lightest unless noted) ---------------------------------------------
DEEP = ramp("#4A4752", "#3C3944", "#302D37", "#26232C", "#1E1B22")      # lacquer / deepslate casing
CONTOUR = hexc("#141218")
COPPER = ramp("#F6B38A", "#E8915E", "#D47A4C", "#C46A3F", "#9A4E2E", "#6E3820")  # 0 = hot glint
VERDI = ramp("#7CC2AA", "#5BA58E", "#4A937F", "#3F8070", "#2E5E53")     # verdigris patina
CHERRY = ramp("#F0C4B4", "#D49A8C", "#B8807A", "#A86B62", "#8A5650", "#74443F")
TEAL = ramp("#9FEAE4", "#4FB8B4", "#2F8A88", "#1F5E5E")
GLASS = ramp("#22343C", "#18262D", "#121C21", "#0E1519", "#0A1013")     # 0 = brightest reflection
ARCHIVE_BG = hexc("#0B1418")                                            # ArchiveTerminalRenderer.BACKGROUND
AMBER_TEXT = ramp("#FFC870", "#FFB040", "#B07424")
PAPER = ramp("#F6EEE1", "#EEE3D1", "#D9CBB4")
SAGE = hexc("#8FA98B")

# Status lens ramps: 0 = darkest rim, 1 = dim rim, 2 = body, 3 = bright core, 4 = hot centre/highlight.
STATUS = {
    "off":       ramp("#121116", "#18171D", "#1E1D24", "#25242C", "#4C4A58"),
    "idle":      ramp("#4E5866", "#6C7888", "#8E9AAA", "#B4BECB", "#E6ECF2"),
    "working":   ramp("#1A6A70", "#2C9EA4", "#46D2D4", "#8CF4F0", "#E4FFFD"),
    "attention": ramp("#8A4A0A", "#C4740F", "#F2A21C", "#FFC85A", "#FFF0C0"),
    "done":      ramp("#1F6230", "#33923E", "#55CC55", "#9AF08A", "#E8FFE0"),
    "error":     ramp("#6E1A1C", "#A8292B", "#E04848", "#FF8A80", "#FFE4E0"),
}


class Img:
    def __init__(self, fill=(0, 0, 0, 0)):
        self.p = [[fill for _ in range(16)] for _ in range(16)]

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[y][x] = c

    def get(self, x, y):
        return self.p[y][x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def bevel(self, x0, y0, x1, y1, light, dark, corner_light=None, corner_dark=None):
        """1 px ring: top/left edges `light`, bottom/right `dark` (raised). Swap for a recess."""
        for x in range(x0, x1 + 1):
            self.set(x, y0, light)
            self.set(x, y1, dark)
        for y in range(y0, y1 + 1):
            self.set(x0, y, light)
            self.set(x1, y, dark)
        if corner_light is not None:
            self.set(x0, y0, corner_light)
        if corner_dark is not None:
            self.set(x1, y1, corner_dark)
        # the two "mixed" corners take the middle of the ramp feel: keep the dark side's
        self.set(x1, y0, dark)
        self.set(x0, y1, dark)

    def grid(self, x0, y0, rows, mapping):
        """Paints a hand-written char grid; chars missing from `mapping` (e.g. '.') are skipped."""
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch in mapping:
                    self.set(x0 + dx, y0 + dy, mapping[ch])

    def png(self, name):
        raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in self.p)

        def chunk(tag, data):
            c = tag + data
            return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

        data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        with open(os.path.join(OUT, name + ".png"), "wb") as f:
            f.write(data)
        return name


def rivet(img, x, y, metal=COPPER):
    """2x2 domed rivet: glint top-left, shade bottom-right."""
    img.set(x, y, metal[0])
    img.set(x + 1, y, metal[2])
    img.set(x, y + 1, metal[2])
    img.set(x + 1, y + 1, metal[5] if len(metal) > 5 else metal[-1])


def screw(img, x, y):
    """1 px dark screw head with a lit pixel above-left: reads as a recessed screw on lacquer."""
    img.set(x, y, CONTOUR)
    img.set(x - 1, y - 1, DEEP[0])


def casing(img, fill=DEEP[2]):
    """Full lacquer panel with a raised 1 px bevel ring."""
    img.rect(0, 0, 15, 15, fill)
    img.bevel(0, 0, 15, 15, DEEP[1], DEEP[4], corner_light=DEEP[0], corner_dark=CONTOUR)


# ---- monitor ---------------------------------------------------------------------------------
def glass_field(img, x0, y0, x1, y1, scan_every=3, scan_phase=0):
    """Dark screen glass with faint scanlines (structured, no noise)."""
    for y in range(y0, y1 + 1):
        tone = GLASS[2] if (y - y0) % scan_every == scan_phase else GLASS[3]
        for x in range(x0, x1 + 1):
            img.set(x, y, tone)


def monitor_front():
    img = Img()
    # ring 0: the bezel frame (also the edge strips of merged walls)
    img.rect(0, 0, 15, 15, DEEP[1])
    img.bevel(0, 0, 15, 15, DEEP[0], DEEP[3], corner_light=COPPER[1], corner_dark=COPPER[4])
    img.set(15, 0, COPPER[3])
    img.set(0, 15, COPPER[3])
    # ring 1: recessed inner lip (shadow on top/left, light catches bottom/right)
    img.bevel(1, 1, 14, 14, CONTOUR, DEEP[2])
    img.set(14, 1, DEEP[4])
    img.set(1, 14, DEEP[4])
    # screen glass 2..13
    glass_field(img, 2, 2, 13, 13, 3, 1)
    # top-left glare: two short diagonal streaks
    for i in range(5):
        img.set(3 + i, 6 - i, GLASS[1])
    for i in range(3):
        img.set(3 + i, 8 - i, GLASS[1])
    img.set(3, 3, GLASS[0])
    # prompt "> _" in teal, bottom-left
    img.set(3, 11, TEAL[2])
    img.set(4, 12, TEAL[2])
    img.set(3, 13, TEAL[2])
    img.set(6, 13, TEAL[1])
    img.set(7, 13, TEAL[1])
    # power LED on the lower lip
    img.set(12, 14, TEAL[1])
    img.set(13, 14, TEAL[3])
    return img.png("monitor_front")


def monitor_screen():
    """Borderless, tileable glass for cells inside a merged screen wall."""
    img = Img()
    for y in range(16):
        tone = GLASS[2] if y % 4 == 3 else (GLASS[4] if y % 4 == 0 else GLASS[3])
        for x in range(16):
            img.set(x, y, tone)
    return img.png("monitor_screen")


def vent_row(img, y, cols, slot=CONTOUR, lip=DEEP[0]):
    for x0, x1 in cols:
        for x in range(x0, x1 + 1):
            img.set(x, y, slot)
            img.set(x, y + 1, lip)


def monitor_side():
    img = Img()
    casing(img)
    img.set(0, 0, DEEP[1])  # 1x1 uv of the edge strips' side faces: plain casing tone
    slots = [(2, 3), (5, 6), (9, 10), (12, 13)]
    vent_row(img, 3, slots)
    vent_row(img, 11, slots)
    # copper badge with a teal t3 dot in the middle band
    img.rect(5, 7, 10, 8, COPPER[2])
    for x in range(5, 11):
        img.set(x, 7, COPPER[1])
    img.set(5, 8, COPPER[1])
    img.set(10, 7, COPPER[3])
    for x in range(5, 11):
        img.set(x, 9, DEEP[4])          # drop shadow under the badge
    img.set(11, 8, DEEP[4])
    img.set(11, 9, DEEP[4])
    img.set(7, 8, TEAL[1])
    img.set(8, 8, TEAL[2])
    img.set(10, 8, VERDI[2])            # a touch of patina at the badge's low corner
    for (x, y) in ((2, 1), (13, 1), (2, 14), (13, 14)):
        img.set(x, y, CONTOUR)
    return img.png("monitor_side")


# ---- status lamp -----------------------------------------------------------------------------
# The 10x10 glass lens drawn at (3,3): digits index the status ramp, 'h' = hot highlight
# (always ramp[4]), 'c' = copper clamp. '4' is the glowing core (dimmed to 3 when off).
LENS = [
    "c11111111c",
    "1hh2222210",
    "1h22333210",
    "1223333210",
    "1233443210",
    "1233443210",
    "1223333210",
    "1222332110",
    "1122221100",
    "c00000000c",
]


def status_lamp(state):
    r = STATUS[state]
    img = Img()
    # ring 0: deepslate casing edge
    img.rect(0, 0, 15, 15, DEEP[2])
    img.bevel(0, 0, 15, 15, DEEP[1], DEEP[4], corner_light=DEEP[0], corner_dark=CONTOUR)
    # rings 1-2: copper frame, raised outer lip and a shaded inner lip
    img.rect(1, 1, 14, 14, COPPER[2])
    img.bevel(1, 1, 14, 14, COPPER[1], COPPER[4], corner_light=COPPER[0], corner_dark=COPPER[5])
    img.bevel(2, 2, 13, 13, COPPER[4], COPPER[2])
    img.set(13, 2, COPPER[3])
    img.set(2, 13, COPPER[3])
    # rivets on the frame corners
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y)
    # verdigris collecting along the lower and right edges of the frame
    for (x, y, c) in ((4, 14, VERDI[3]), (5, 14, VERDI[2]), (10, 14, VERDI[3]), (14, 9, VERDI[3]),
                      (14, 10, VERDI[2]), (12, 14, VERDI[1]), (14, 12, VERDI[2])):
        img.set(x, y, c)
    # lens
    core = r[3] if state == "off" else r[4]
    mapping = {"0": r[0], "1": r[1], "2": r[2], "3": r[3], "4": core, "h": r[4], "c": COPPER[1]}
    if state == "off":
        mapping["h"] = r[4]
        mapping["3"] = r[2]       # unlit: no bright core, only the glass dome and a dull glint
    img.grid(3, 3, LENS, mapping)
    return img.png("status_lamp_" + state)


# ---- board lamp ------------------------------------------------------------------------------
# A smaller, flatter round bulb on the thread wall's lacquer plate. 'k' = copper bezel (shaded by
# position), digits/'h' as for the status lens. Drawn at (3,3).
BULB = [
    "...kkkk...",
    ".kkhh21kk.",
    ".kh43322k.",
    "k24443321k",
    "k23444321k",
    "k22333221k",
    "k12222210k",
    ".k111100k.",
    ".kk0000kk.",
    "...kkkk...",
]


def board_lamp(state):
    r = STATUS[state]
    img = Img()
    # lacquer plate
    img.rect(0, 0, 15, 15, DEEP[2])
    img.bevel(0, 0, 15, 15, DEEP[0], DEEP[4], corner_light=DEEP[0], corner_dark=CONTOUR)
    img.bevel(1, 1, 14, 14, DEEP[1], DEEP[3])
    img.set(14, 1, DEEP[2])
    img.set(1, 14, DEEP[2])
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        screw(img, x, y)
    # bezel ring: lit toward the top-left, dark toward the bottom-right, patina at the very bottom
    for dy, row in enumerate(BULB):
        for dx, ch in enumerate(row):
            if ch != "k":
                continue
            s = (dx - 4.5) + (dy - 4.5)
            c = COPPER[1] if s < -3 else COPPER[2] if s < 0 else COPPER[3] if s < 3 else COPPER[4]
            if s >= 5:
                c = VERDI[3]
            img.set(3 + dx, 3 + dy, c)
    # soft drop shadow of the bezel on the plate (down-right)
    for dy, row in enumerate(BULB):
        for dx, ch in enumerate(row):
            if ch == "k":
                x, y = 4 + dx, 4 + dy
                inside = 0 <= dy + 1 < 10 and 0 <= dx + 1 < 10 and BULB[dy + 1][dx + 1] != "."
                if not inside and img.get(x, y) == DEEP[2]:
                    img.set(x, y, DEEP[3])
    core = r[3] if state == "off" else r[4]
    mapping = {"0": r[0], "1": r[1], "2": r[2], "3": r[2] if state == "off" else r[3], "4": core, "h": r[4]}
    img.grid(3, 3, BULB, mapping)
    return img.png("board_lamp_" + state)


# ---- archive terminal ------------------------------------------------------------------------
def archive_terminal_body():
    img = Img()
    img.rect(0, 0, 15, 15, DEEP[2])
    # rows 0..8: housing sides / housing top. Copper trim along the top edge, vents lower down.
    for x in range(16):
        img.set(x, 0, COPPER[1] if x < 15 else COPPER[3])
        img.set(x, 1, COPPER[3])
    img.set(0, 0, COPPER[0])
    for x in range(16):
        img.set(x, 2, DEEP[4])            # shadow under the trim
    img.set(0, 1, COPPER[2])
    for x in range(1, 15):
        img.set(x, 8, DEEP[3])            # bottom of the housing panel
    for y in range(2, 9):
        img.set(0, y, DEEP[1])
        img.set(15, y, DEEP[3])
    vent_row(img, 4, [(3, 5), (7, 8), (10, 12)])
    img.set(13, 6, VERDI[3])
    # rows 9..15: front of the base
    for x in range(16):
        img.set(x, 9, DEEP[0])            # lit top lip
        img.set(x, 15, CONTOUR)           # bottom contour
    for y in range(9, 15):
        img.set(0, y, DEEP[1])
        img.set(15, y, DEEP[4])
    img.set(0, 9, DEEP[0])
    img.set(15, 9, DEEP[2])
    # archive card slot with copper lips
    for x in range(3, 13):
        img.set(x, 11, COPPER[2])
        img.set(x, 12, CONTOUR)
        img.set(x, 13, COPPER[4])
    img.set(3, 11, COPPER[1])
    img.set(12, 13, COPPER[5])
    img.set(2, 12, DEEP[4])
    img.set(13, 12, DEEP[1])
    # status pips (teal = online, amber = archive activity), screws
    img.set(13, 10, TEAL[1])
    img.set(11, 10, AMBER_TEXT[1])
    screw(img, 2, 14)
    screw(img, 13, 14)
    return img.png("archive_terminal_body")


def archive_terminal_screen():
    img = Img()
    img.rect(0, 0, 15, 15, DEEP[2])
    img.bevel(0, 0, 15, 15, DEEP[1], DEEP[4])
    img.bevel(1, 1, 14, 14, CONTOUR, DEEP[2])           # recessed lip (the visible bezel)
    img.rect(2, 2, 13, 13, ARCHIVE_BG)
    # header in amber, then three entries (teal title, dim detail)
    for x in range(3, 10):
        img.set(x, 3, AMBER_TEXT[1])
    img.set(3, 3, AMBER_TEXT[0])
    for x in range(3, 13):
        img.set(x, 4, GLASS[2])                          # rule under the header
    for y in (6, 9, 12):
        for x in range(3, 12):
            img.set(x, y, TEAL[2])
        img.set(3, y, TEAL[1])
        if y < 12:                                       # dim detail line under each entry
            for x in range(4, 10):
                img.set(x, y + 1, GLASS[0])
    img.set(12, 12, TEAL[0])                             # cursor
    img.set(2, 2, GLASS[1])
    img.set(3, 2, GLASS[1])
    img.set(2, 3, GLASS[1])
    return img.png("archive_terminal_screen")


def archive_terminal_side():
    img = Img()
    img.rect(0, 0, 15, 15, DEEP[2])
    # rows 0..8: back of the screen housing (sampled at cols 1..14): trim, vent grill, cable port
    for x in range(16):
        img.set(x, 0, COPPER[1] if x < 15 else COPPER[3])
        img.set(x, 1, DEEP[4])
        img.set(x, 8, DEEP[3])
    for y in range(1, 9):
        img.set(0, y, DEEP[1])
        img.set(15, y, DEEP[3])
    for y in (2, 4):
        vent_row(img, y, [(3, 12)])
    for x in (5, 8, 11):                                 # grill ribs
        img.set(x, 2, DEEP[3])
        img.set(x, 4, DEEP[3])
    # cable notch at the bottom centre with a copper grommet and a teal cable
    img.grid(6, 6, ["kCCk", "kCCk", ".tt."], {"k": COPPER[3], "C": CONTOUR, "t": TEAL[2]})
    img.set(6, 6, COPPER[1])
    img.set(7, 7, TEAL[3])
    img.set(8, 7, TEAL[3])
    # rows 9..15: base sides/back
    for x in range(16):
        img.set(x, 9, DEEP[0])
        img.set(x, 15, CONTOUR)
    for y in range(9, 15):
        img.set(0, y, DEEP[1])
        img.set(15, y, DEEP[4])
    vent_row(img, 11, [(3, 5), (7, 8), (10, 12)])
    screw(img, 2, 14)
    screw(img, 13, 14)
    img.set(13, 13, VERDI[3])
    return img.png("archive_terminal_side")


KEYBOARD = [
    "ffffffffffffff",
    "fkgkgkgkgkgkgD",
    "fgkgkgkgkgktTD",
    "fkgsssssssgktD",
    "fDDDDDDDDDDDDD",
]


def archive_terminal_top():
    img = Img()
    img.rect(0, 0, 15, 15, DEEP[2])
    img.bevel(0, 0, 15, 15, DEEP[1], DEEP[4], corner_light=DEEP[0], corner_dark=CONTOUR)
    # keyboard on the visible strip in front of the housing (rows 0..5)
    img.grid(1, 1, KEYBOARD, {
        "f": COPPER[2],    # copper keyboard frame, lit edge
        "D": COPPER[4],    # frame shadow side
        "k": DEEP[0],      # key caps
        "g": DEEP[1],      # key caps, alternate row tone
        "s": DEEP[0],      # space bar
        "t": TEAL[1],      # enter key
        "T": TEAL[2],
    })
    img.set(1, 1, COPPER[1])
    for x in range(2, 15):
        img.set(x, 6, DEEP[3])            # shadow line in front of the housing
    # under the housing: plain lacquer with two seams (only edges show)
    for x in range(1, 15):
        img.set(x, 10, DEEP[3])
    return img.png("archive_terminal_top")


# ---- thread board ----------------------------------------------------------------------------
LANES = [(2, TEAL[1]), (5, AMBER_TEXT[1]), (8, STATUS["error"][2]), (11, SAGE)]
CARDS = {2: (5, 8, 11), 5: (5, 8), 8: (5,), 11: (5, 8)}   # lane x -> card top rows


def cherry_frame(img):
    img.rect(0, 0, 15, 15, CHERRY[2])
    for x in range(16):
        img.set(x, 0, CHERRY[0])
        img.set(x, 15, CHERRY[5])
    for y in range(16):
        img.set(0, y, CHERRY[1])
        img.set(15, y, CHERRY[4])
    img.set(15, 0, CHERRY[3])
    img.set(0, 15, CHERRY[4])


def thread_board_front():
    img = Img()
    cherry_frame(img)
    # copper corner brackets
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        img.set(x, y, COPPER[1] if (x, y) == (0, 0) else COPPER[3] if y == 0 or x == 0 else COPPER[4])
    # ring 1: dark recessed lip
    img.bevel(1, 1, 14, 14, CONTOUR, DEEP[2])
    img.set(14, 1, DEEP[4])
    img.set(1, 14, DEEP[4])
    img.rect(2, 2, 13, 13, DEEP[3])
    for x in (4, 7, 10):                  # lane grooves
        for y in range(3, 14):
            img.set(x, y, DEEP[4])
    for x, colour in LANES:               # lane header bars
        img.set(x, 3, colour)
        img.set(x + 1, 3, colour)
    for x in range(2, 14):
        img.set(x, 2, DEEP[2])            # lit top edge of the field
    for x, rows in CARDS.items():         # paper cards with a shadow line under each
        for y in rows:
            img.set(x, y, PAPER[0])
            img.set(x + 1, y, PAPER[1])
            img.set(x, y + 1, PAPER[1])
            img.set(x + 1, y + 1, PAPER[2])
            img.set(x, y + 2, CONTOUR)
            img.set(x + 1, y + 2, CONTOUR)
    img.set(2, 5, COPPER[1])              # pin on the first card
    return img.png("thread_board_front")


GRAIN = [2, 2, 3, 2, 2, 1, 2, 3, 3, 2, 2, 1, 2, 3]   # base tone per interior column (CHERRY index)


def thread_board_side():
    img = Img()
    cherry_frame(img)
    for x in range(1, 15):
        base = GRAIN[x - 1]
        for y in range(1, 15):
            tone = base
            if (y + x * 5) % 9 in (0, 1):  # darker grain streaks, two pixels long, staggered per column
                tone = min(base + 1, 4)
            img.set(x, y, CHERRY[tone])
    # knot
    img.grid(9, 10, [".a.", "aba", ".a."], {"a": CHERRY[4], "b": CHERRY[5]})
    # copper strap across the middle with rivets
    for x in range(1, 15):
        img.set(x, 7, COPPER[2])
        img.set(x, 8, COPPER[4])
        img.set(x, 9, CHERRY[4])          # strap shadow on the wood
    img.set(1, 7, COPPER[1])
    for x in (3, 12):
        img.set(x, 7, COPPER[0])
        img.set(x, 8, COPPER[5])
    img.set(8, 8, VERDI[3])
    return img.png("thread_board_side")


def main():
    written = [monitor_front(), monitor_screen(), monitor_side()]
    for s in ("off", "idle", "working", "attention", "done", "error"):
        written.append(status_lamp(s))
        written.append(board_lamp(s))
    written += [archive_terminal_body(), archive_terminal_screen(), archive_terminal_side(), archive_terminal_top(),
                thread_board_front(), thread_board_side()]
    for n in written:
        print("wrote", n + ".png")


if __name__ == "__main__":
    main()
