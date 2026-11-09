#!/usr/bin/env python3
"""Draws the 16x16 textures of the kit's paper lanterns (standing andon, hanging chochin, wall bracket).

Original pixel art for t3craft (MIT), drawn by hand-written rules: nothing is copied or derived from
Mojang's textures. Deterministic (fixed seeds), stdlib only, so re-running it gives byte-identical PNGs:

    python3 mod/tools/kit_lantern_textures.py

Writes mod/src/main/resources/assets/t3craft/textures/block/kit_lantern_*.png (new names only: the
older kit_lantern_side/end of blocks_kit_textures.py are left alone).

Every texture is drawn at the model's texel density (1 texel = 1/16 block), and each paper face is
drawn for the exact uv window its model element uses, so nothing is stretched.
"""
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "t3craft", "textures", "block")


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def pal(*cs):
    return [hexc(c) for c in cs]


# ---- palette (dark -> light inside every ramp) ----------------------------------------------
# lacquered cherry: a warm, slightly purple brown-black, with a rose highlight
LACQ = pal("#21141A", "#2E1B22", "#3D242B", "#4F2F33", "#6A3F3E", "#8A5650")
BRASS = pal("#6E4E1E", "#9C7A31", "#C9A24A", "#E3C46A", "#F7E3A0")
IRON = pal("#1C1B20", "#2A292F", "#3A3940", "#4E4D55", "#6A6972")
# cherry wood of the structure (posts, ribs): a deep rose-brown, lighter than the black finishes but darker than the paper
CHERRY = pal("#3E1E23", "#51282D", "#673338", "#7E4144", "#96524F", "#B06A63")
# dark stone of the tall lantern's plinth (blackstone-like)
STONE = pal("#1B1A21", "#26252D", "#33323C", "#44434E", "#5A5964")
# backlit paper per tone: edge (cool, dim) -> hot (near the flame)
TONES = {
    "paper": pal("#C98F4E", "#E2AE62", "#F2C97C", "#FBDD98", "#FFEDB8", "#FFF7DC"),
    "rose": pal("#B5555F", "#D2737A", "#E8939A", "#F5B2B4", "#FFCFCC", "#FFE9E2"),
    "vermilion": pal("#8E2118", "#B5321F", "#D6482A", "#EC6A36", "#FA9450", "#FFC27A"),
}
# the dark wooden ribs seen through lit paper, per tone
RIB = {
    "paper": pal("#8C5433", "#A8693E"),
    "rose": pal("#7A3640", "#94464E"),
    "vermilion": pal("#5A120E", "#741A13"),
}
INK = pal("#1A1216", "#2A1C20")
# the waist band of the vermilion chochin: dark red-brown, so the stripes still read from afar
VBAND = pal("#5C1812", "#7A2218")


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

    def png(self, name):
        raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in self.p)

        def chunk(tag, data):
            c = tag + data
            return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

        data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        with open(os.path.join(OUT, name + ".png"), "wb") as f:
            f.write(data)


def clamp(i, n):
    return max(0, min(n - 1, i))


def glow(ramp, x, y, cx, cy, rx, ry, rng, bias=0.0):
    """A ramp index from the distance to the hot spot (cx, cy), elliptical, with a little fibre jitter."""
    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
    v = (1.0 - min(1.0, d)) * (len(ramp) - 1) + bias + rng.uniform(-0.45, 0.45)
    return ramp[clamp(int(round(v)), len(ramp))]


# ---- wood and metal --------------------------------------------------------------------------
def lacquer():
    """Lacquered cherry for posts, rails and roofs: vertical grain, a lit edge column every 4 px."""
    rng = random.Random(301)
    img = Img()
    for x in range(16):
        base = [2, 3, 2, 1][x % 4]
        for y in range(16):
            k = base
            r = rng.random()
            if r < 0.12:
                k -= 1
            elif r > 0.92:
                k += 1
            img.set(x, y, LACQ[clamp(k, 5)])
        if x % 4 == 1:  # the rubbed highlight of the lacquer
            for y in range(16):
                if rng.random() > 0.35:
                    img.set(x, y, LACQ[4])
    return img


def roof_top():
    """Top of the andon's roof board (uv 2..14): a bevelled lacquer board, lit edge NW, dark edge SE."""
    rng = random.Random(302)
    img = lacquer()
    for i in range(2, 14):
        img.set(i, 2, LACQ[5])
        img.set(2, i, LACQ[4])
        img.set(i, 13, LACQ[0])
        img.set(13, i, LACQ[1])
    for y in range(3, 13):
        for x in range(3, 13):
            if rng.random() < 0.1:
                img.set(x, y, LACQ[3])
    return img


def finial():
    """Top of the finial block (uv 5..11): brass ring round a dark vent, the lantern's chimney."""
    img = Img()
    img.rect(0, 0, 15, 15, LACQ[2])
    img.rect(5, 5, 10, 10, BRASS[2])
    for i in range(5, 11):
        img.set(i, 5, BRASS[3])
        img.set(5, i, BRASS[3])
        img.set(i, 10, BRASS[1])
        img.set(10, i, BRASS[1])
    img.set(5, 5, BRASS[4])
    img.rect(7, 7, 8, 8, INK[0])
    return img


def brass():
    rng = random.Random(303)
    img = Img()
    for y in range(16):
        for x in range(16):
            k = [2, 3, 3, 2, 1][(x + y // 4) % 5]
            if rng.random() < 0.1:
                k += 1
            img.set(x, y, BRASS[clamp(k, 5)])
    return img


def iron():
    """Wrought iron of the wall bracket: dark with a cold highlight on the top edge."""
    rng = random.Random(304)
    img = Img()
    for y in range(16):
        for x in range(16):
            k = 1 + (1 if (x + 2 * y) % 5 == 0 else 0) + (1 if rng.random() < 0.15 else 0)
            img.set(x, y, IRON[clamp(k, 5)])
    for x in range(16):
        img.set(x, 0, IRON[4])
        img.set(x, 4, IRON[3])
        img.set(x, 8, IRON[4])
        img.set(x, 12, IRON[3])
    return img


def band():
    """Black lacquer band of the chochin (top and bottom rings), a thin gold line in the middle."""
    rng = random.Random(305)
    img = Img()
    for y in range(16):
        for x in range(16):
            img.set(x, y, INK[rng.random() < 0.3])
    for y in range(16):
        if y % 4 == 1:
            for x in range(16):
                img.set(x, y, LACQ[3] if (x + y) % 3 else BRASS[1])
    # the cord's twist
    for y in range(16):
        img.set(8, y, LACQ[1] if y % 2 else INK[0])
    return img


def cherry():
    """Cherry wood of the structure: vertical grain, a pale rubbed edge column every 4 px, a few knots."""
    rng = random.Random(306)
    img = Img()
    for x in range(16):
        base = [3, 2, 3, 4][x % 4]
        for y in range(16):
            k = base
            r = rng.random()
            if r < 0.14:
                k -= 1
            elif r > 0.9:
                k += 1
            img.set(x, y, CHERRY[clamp(k, 6)])
        if x % 4 == 3:
            for y in range(16):
                if rng.random() > 0.3:
                    img.set(x, y, CHERRY[5 if rng.random() < 0.4 else 4])
    for kx, ky in ((5, 4), (12, 11)):
        img.set(kx, ky, CHERRY[0])
        img.set(kx, ky + 1, CHERRY[1])
    return img


def stone():
    """Dark stone bricks (8 x 4 px, offset rows) with a light top edge, for the tall lantern's plinth."""
    rng = random.Random(307)
    img = Img()
    for y in range(16):
        row = y // 4
        for x in range(16):
            k = 2 + (1 if rng.random() < 0.2 else 0) - (1 if rng.random() < 0.2 else 0)
            img.set(x, y, STONE[clamp(k, 5)])
        off = 4 if row % 2 else 0
        for x in range(16):
            if y % 4 == 3:
                img.set(x, y, STONE[0])  # mortar
            elif (x + off) % 8 == 7:
                img.set(x, y, STONE[0])
            elif y % 4 == 0:
                img.set(x, y, STONE[3])  # lit top edge
    return img


# ---- paper ------------------------------------------------------------------------------------
def tall():
    """
    Side of the tall lantern's paper box (body element 2..14 wide, 4..12 high of the upper half,
    uv [2, 4, 14, 12]): lit paper with a hot core, two kumiko rails and a centre stile in the top panel.
    """
    rng = random.Random(312)
    ramp = TONES["paper"]
    rib = RIB["paper"]
    img = Img()
    img.rect(0, 0, 15, 15, ramp[1])
    for y in range(4, 12):
        for x in range(2, 14):
            img.set(x, y, glow(ramp, x, y, 7.5, 8.0, 7.0, 5.6, rng, bias=1.1))
    for x in range(2, 14):
        img.set(x, 4, ramp[2])
        img.set(x, 7, rib[1])
        img.set(x, 10, rib[1])
    for y in range(4, 7):
        img.set(7, y, rib[0])
        img.set(8, y, rib[1])
    for y in range(4, 12):
        img.set(2, y, ramp[1] if y % 3 else ramp[0])
        img.set(13, y, ramp[1] if y % 3 else ramp[0])
    for x, y in ((7, 8), (8, 8), (7, 9), (8, 9), (6, 8), (9, 9), (7, 6), (8, 6)):
        img.set(x, y, ramp[5])
    return img


def andon():
    """
    Standing andon side (body element 3..13 wide, 2..13 high, uv [3, 3, 13, 14]).
    Lit paper whose hot spot sits low (the flame), two kumiko rails, and the corner posts' shadow
    on the paper's edge columns.
    """
    rng = random.Random(311)
    ramp = TONES["paper"]
    rib = RIB["paper"]
    img = Img()
    img.rect(0, 0, 15, 15, ramp[1])
    for y in range(3, 14):
        for x in range(3, 13):
            img.set(x, y, glow(ramp, x, y, 7.5, 9.5, 6.2, 7.5, rng, bias=0.6))
    # posts' shadow and the top rail's shade
    for y in range(3, 14):
        img.set(3, y, ramp[1] if y % 3 else ramp[0])
        img.set(12, y, ramp[1] if y % 3 else ramp[0])
    for x in range(4, 12):
        img.set(x, 3, ramp[2])
    # kumiko: two rails and a short centre stile in the upper panel only
    for x in range(3, 13):
        img.set(x, 6, rib[1])
        img.set(x, 11, rib[1])
    for y in range(3, 6):
        img.set(7, y, rib[0])
        img.set(8, y, rib[1])
    # the flame's warm core, a small soft blot under the middle rail
    img.set(7, 9, ramp[5])
    img.set(8, 9, ramp[5])
    img.set(7, 8, ramp[5])
    return img


def chochin(tone):
    """
    Hanging chochin side, for the barrel of five tiers (y 3..13 = uv rows 3..12; widths 6, 8, 10, 8, 6
    centred, so each tier crops the middle of this texture). Bamboo ribs every other row, round
    shading: dim at the left/right edge of the widest tier and towards the shoulders, hot in the belly.
    Rose carries a pale blossom band, vermilion a dark ink waist band; paper is plain.
    """
    rng = random.Random(320 + len(tone))
    ramp = TONES[tone]
    rib = RIB[tone]
    img = Img()
    img.rect(0, 0, 15, 15, ramp[1])
    for y in range(2, 14):
        for x in range(1, 15):
            img.set(x, y, glow(ramp, x, y, 7.5, 8.0, 6.8, 5.6, rng, bias=1.1))
    for y in range(2, 14):
        if y % 2 == 0:  # a rib: darker where the barrel turns away
            for x in range(1, 15):
                img.set(x, y, rib[0] if abs(x - 7.5) > 3.5 else rib[1])
    if tone == "vermilion":
        for x in range(1, 15):
            img.set(x, 8, VBAND[1] if x % 3 else VBAND[0])
            img.set(x, 9, VBAND[0] if abs(x - 7.5) > 3.5 else VBAND[1])
    elif tone == "rose":
        cream = hexc("#FFF4EC")
        for x in range(1, 15):
            if x % 3 == 1:
                img.set(x, 9, cream)
                img.set(x - 1, 9, ramp[5])
                img.set(x + 1, 9, ramp[5])
    return img


def box(tone):
    """
    Wall lantern body, a round barrel (element 3..13 wide, 4..9 high, between two narrower tiers; uv rows 6..12,
    cols 3..12): lit paper, a kumiko rail and a dim lower edge.
    """
    rng = random.Random(340 + len(tone))
    ramp = TONES[tone]
    rib = RIB[tone]
    img = Img()
    img.rect(0, 0, 15, 15, ramp[1])
    for y in range(6, 13):
        for x in range(3, 13):
            img.set(x, y, glow(ramp, x, y, 7.5, 9.0, 5.6, 4.4, rng, bias=1.1))
    for x in range(3, 13):
        img.set(x, 9, rib[1])
        img.set(x, 12, ramp[1])
        img.set(x, 6, ramp[2])
    for y in range(7, 12):
        img.set(3, y, ramp[1] if y != 9 else rib[0])
        img.set(12, y, ramp[1] if y != 9 else rib[0])
    for y in range(7, 9):
        img.set(7, y, rib[0])
        img.set(8, y, rib[1])
    for x, y in ((7, 10), (8, 10), (7, 11), (8, 11), (6, 10)):
        img.set(x, y, ramp[5])
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    textures = {
        "kit_lantern_lacquer": lacquer(),
        "kit_lantern_roof": roof_top(),
        "kit_lantern_finial": finial(),
        "kit_lantern_brass": brass(),
        "kit_lantern_iron": iron(),
        "kit_lantern_band": band(),
        "kit_lantern_andon": andon(),
        "kit_lantern_cherry": cherry(),
        "kit_lantern_stone": stone(),
        "kit_lantern_tall": tall(),
    }
    for tone in TONES:
        textures["kit_lantern_chochin_" + tone] = chochin(tone)
        textures["kit_lantern_box_" + tone] = box(tone)
    for name, img in textures.items():
        img.png(name)
    print("wrote", len(textures), "textures to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
