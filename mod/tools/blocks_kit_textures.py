#!/usr/bin/env python3
"""Draws the 16x16 textures of t3craft's HQ blocks kit (cherry campus style).

Original pixel art for t3craft (MIT), drawn procedurally: nothing is copied or derived from
Mojang's textures. Deterministic (fixed seeds), stdlib only (zlib + struct), so re-running it gives
byte-identical PNGs:

    python3 mod/tools/blocks_kit_textures.py

Writes into mod/src/main/resources/assets/t3craft/textures/block/kit_*.png.
"""
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "t3craft", "textures", "block")


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


# ---- palette -------------------------------------------------------------------------------
CHERRY = [hexc(c) for c in ("#F0C4B4", "#E2AE9E", "#D49A8C", "#C2867A", "#A86B62")]
CHERRY_DARK = [hexc(c) for c in ("#8A5650", "#74443F", "#5E3633")]
OUTLINE = hexc("#3E2427")
LACQUER = [hexc(c) for c in ("#3A2A30", "#2E2228", "#251B20")]
BRASS = [hexc(c) for c in ("#E3C46A", "#C9A24A", "#9C7A31")]
PAPER = [hexc(c) for c in ("#F6EEE1", "#EEE3D1", "#E4D6C0")]
PAPER_LIT = [hexc(c) for c in ("#FFF8E2", "#FFEFC2", "#FFE3A0")]
STONE = [hexc(c) for c in ("#B9B8BE", "#A6A5AC", "#94939B", "#7F7E87", "#6A6972")]
MOSS = [hexc(c) for c in ("#7E9A55", "#678446")]
BLOSSOM = [hexc(c) for c in ("#FFE1EA", "#F8BFD2", "#EE9DBA", "#D97AA0")]
BARK = [hexc(c) for c in ("#5A3B3A", "#4A302F", "#6B4846")]
TEAL = [hexc(c) for c in ("#7FE0DC", "#4FB8B4", "#2F8A88")]
INK = [hexc(c) for c in ("#2A2226", "#221B1F")]
CLAY = [hexc(c) for c in ("#B5674A", "#9C553D", "#7F4432")]
GLOW = [hexc(c) for c in ("#FFE9A8", "#FFD46E", "#F5B84A")]


class Img:
    def __init__(self, fill=(0, 0, 0, 0)):
        self.p = [[fill for _ in range(16)] for _ in range(16)]

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def noise(self, x0, y0, x1, y1, colors, rng, weights=None):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, rng.choices(colors, weights=weights)[0])

    def png(self, name):
        raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in self.p)

        def chunk(tag, data):
            c = tag + data
            return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

        data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        with open(os.path.join(OUT, name + ".png"), "wb") as f:
            f.write(data)


def grain(img, x0, y0, x1, y1, rng, vertical=False, base=CHERRY):
    """Wood: long streaks of one tone with darker knots, along x (or y)."""
    if vertical:
        for x in range(x0, x1 + 1):
            tone = rng.choice(base[1:4])
            for y in range(y0, y1 + 1):
                c = tone if rng.random() > 0.18 else rng.choice(base)
                img.set(x, y, c)
    else:
        for y in range(y0, y1 + 1):
            tone = rng.choice(base[1:4])
            for x in range(x0, x1 + 1):
                c = tone if rng.random() > 0.18 else rng.choice(base)
                img.set(x, y, c)


def cherry_wood():
    """Generic cherry board (desk sides, railing, plaque frame)."""
    rng = random.Random(11)
    img = Img()
    grain(img, 0, 0, 15, 15, rng)
    for y in (4, 10):
        for x in range(16):
            img.set(x, y, CHERRY[4] if x % 5 else CHERRY_DARK[0])
    return img


def desk_top():
    rng = random.Random(12)
    img = Img()
    grain(img, 1, 1, 14, 14, rng)
    img.rect(0, 0, 15, 0, CHERRY_DARK[1])
    img.rect(0, 15, 15, 15, CHERRY_DARK[1])
    img.rect(0, 0, 0, 15, CHERRY_DARK[1])
    img.rect(15, 0, 15, 15, CHERRY_DARK[1])
    # monitor pad (dark felt) with a teal status notch, where the monitor rests
    img.rect(4, 2, 11, 6, INK[0])
    img.rect(5, 3, 10, 5, INK[1])
    img.set(7, 6, TEAL[1])
    img.set(8, 6, TEAL[1])
    return img


def desk_leg():
    rng = random.Random(13)
    img = Img()
    grain(img, 0, 0, 15, 15, rng, vertical=True, base=CHERRY_DARK + [CHERRY[4], CHERRY[3]])
    for y in (0, 15):
        img.rect(0, y, 15, y, BRASS[2])
    return img


def shoji(lit):
    """Paper between a cherry lattice: 2 x 3 panes, frame on the border."""
    rng = random.Random(21 if lit else 22)
    img = Img()
    paper = PAPER_LIT if lit else PAPER
    img.noise(0, 0, 15, 15, paper, rng, weights=[6, 3, 1])
    frame = CHERRY[4] if lit else CHERRY_DARK[0]
    bar = CHERRY[3] if lit else CHERRY[4]
    img.rect(0, 0, 15, 0, frame)
    img.rect(0, 15, 15, 15, frame)
    img.rect(0, 0, 0, 15, frame)
    img.rect(15, 0, 15, 15, frame)
    img.rect(7, 1, 8, 14, bar)
    for y in (5, 10):
        img.rect(1, y, 14, y, bar)
    if lit:
        # warm bloom in the centre of each pane
        for cx, cy in ((3, 2), (11, 2), (3, 7), (11, 7), (3, 12), (11, 12)):
            img.set(cx, cy, GLOW[0])
            img.set(cx + 1, cy + 1, GLOW[0])
    return img


def floor_cherry():
    """Cherry floor tile: inner boards in a bordered frame (darker rim, brass corner pins)."""
    rng = random.Random(31)
    img = Img()
    grain(img, 2, 2, 13, 13, rng)
    for x in range(2, 14):
        img.set(x, 7, CHERRY[4])
    img.rect(0, 0, 15, 1, CHERRY_DARK[0])
    img.rect(0, 14, 15, 15, CHERRY_DARK[0])
    img.rect(0, 0, 1, 15, CHERRY_DARK[0])
    img.rect(14, 0, 15, 15, CHERRY_DARK[0])
    img.rect(1, 1, 14, 1, CHERRY_DARK[1])
    img.rect(1, 14, 14, 14, CHERRY_DARK[2])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img.set(x, y, BRASS[1])
    return img


def floor_stone():
    """Polished stone tile with the t3craft mark (a small teal "T" with three dots) in one corner."""
    rng = random.Random(32)
    img = Img()
    img.noise(0, 0, 15, 15, STONE[:3], rng, weights=[3, 5, 2])
    img.rect(0, 0, 15, 0, STONE[0])
    img.rect(0, 0, 0, 15, STONE[0])
    img.rect(0, 15, 15, 15, STONE[4])
    img.rect(15, 0, 15, 15, STONE[4])
    # mark
    img.rect(10, 10, 13, 10, TEAL[1])
    img.rect(11, 11, 12, 13, TEAL[2])
    img.set(10, 13, TEAL[0])
    img.set(13, 13, TEAL[0])
    img.set(13, 12, TEAL[0])
    return img


def beam_side():
    rng = random.Random(41)
    img = Img()
    grain(img, 0, 0, 15, 15, rng, vertical=True)
    img.rect(0, 0, 0, 15, CHERRY_DARK[0])
    img.rect(15, 0, 15, 15, CHERRY_DARK[1])
    return img


def beam_end():
    img = Img()
    # rings
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            c = CHERRY_DARK[0] if d > 7 else (CHERRY[1] if int(d) % 2 else CHERRY[3])
            img.set(x, y, c)
    img.rect(7, 7, 8, 8, CHERRY_DARK[1])
    return img


def cap_side():
    """Column cap: black lacquer with a brass band and a cherry lip."""
    img = Img()
    rng = random.Random(51)
    img.noise(0, 0, 15, 15, LACQUER, rng, weights=[2, 5, 2])
    img.rect(0, 5, 15, 5, BRASS[0])
    img.rect(0, 6, 15, 6, BRASS[2])
    img.rect(0, 0, 15, 1, CHERRY[3])
    img.rect(0, 2, 15, 2, CHERRY_DARK[0])
    for x in (3, 8, 13):
        img.set(x, 10, BRASS[1])
    return img


def cap_top():
    img = Img()
    rng = random.Random(52)
    img.noise(0, 0, 15, 15, LACQUER, rng, weights=[2, 5, 2])
    img.rect(2, 2, 13, 2, BRASS[1])
    img.rect(2, 13, 13, 13, BRASS[2])
    img.rect(2, 2, 2, 13, BRASS[1])
    img.rect(13, 2, 13, 13, BRASS[2])
    img.rect(7, 7, 8, 8, TEAL[1])
    return img


def lantern_side():
    """Paper lantern body: warm paper with horizontal ribs."""
    img = Img()
    rng = random.Random(61)
    img.noise(0, 0, 15, 15, PAPER_LIT, rng, weights=[3, 5, 3])
    for y in (2, 6, 10, 14):
        img.rect(0, y, 15, y, GLOW[2])
    img.rect(0, 0, 15, 0, LACQUER[0])
    img.rect(0, 15, 15, 15, LACQUER[0])
    img.rect(7, 4, 8, 11, GLOW[1])
    return img


def lantern_end():
    img = Img()
    rng = random.Random(62)
    img.noise(0, 0, 15, 15, LACQUER, rng)
    img.rect(5, 5, 10, 10, BRASS[1])
    img.rect(6, 6, 9, 9, BRASS[2])
    return img


def plaque_front():
    """Plaque: cherry frame around a dark ink plate (the BER draws the text on it)."""
    img = Img()
    rng = random.Random(71)
    grain(img, 0, 0, 15, 15, rng)
    img.noise(2, 2, 13, 13, INK, rng, weights=[4, 1])
    img.rect(1, 1, 14, 1, CHERRY_DARK[0])
    img.rect(1, 14, 14, 14, CHERRY_DARK[0])
    img.rect(1, 1, 1, 14, CHERRY_DARK[0])
    img.rect(14, 1, 14, 14, CHERRY_DARK[0])
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        img.set(x, y, BRASS[1])
    return img


def railing():
    rng = random.Random(81)
    img = Img()
    grain(img, 0, 0, 15, 15, rng)
    img.rect(0, 0, 15, 0, CHERRY[0])
    img.rect(0, 15, 15, 15, CHERRY_DARK[1])
    return img


def pot():
    rng = random.Random(91)
    img = Img()
    img.noise(0, 0, 15, 15, CLAY, rng, weights=[4, 3, 1])
    img.rect(0, 0, 15, 1, CLAY[2])
    img.rect(0, 8, 15, 8, TEAL[2])
    return img


def soil():
    rng = random.Random(92)
    img = Img()
    img.noise(0, 0, 15, 15, [hexc("#4B3527"), hexc("#3C2A1F"), hexc("#5A4130")] + MOSS, rng, weights=[4, 4, 2, 1, 1])
    return img


def blossom():
    rng = random.Random(93)
    img = Img()
    img.noise(0, 0, 15, 15, BLOSSOM, rng, weights=[2, 5, 4, 2])
    for _ in range(6):
        img.set(rng.randrange(16), rng.randrange(16), hexc("#FFFFFF"))
    return img


def bark():
    rng = random.Random(94)
    img = Img()
    grain(img, 0, 0, 15, 15, rng, vertical=True, base=BARK + [BARK[0], BARK[1]])
    return img


def stone_lantern(light):
    rng = random.Random(101 if light else 102)
    img = Img()
    img.noise(0, 0, 15, 15, STONE[1:], rng, weights=[3, 4, 3, 1])
    img.noise(0, 12, 15, 15, STONE[1:] + MOSS, rng, weights=[3, 4, 3, 1, 2, 1])
    if light:
        img.rect(4, 4, 11, 11, STONE[4])
        img.noise(5, 5, 10, 10, GLOW, rng, weights=[3, 3, 1])
        img.rect(7, 5, 8, 10, STONE[3])
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    textures = {
        "kit_cherry_wood": cherry_wood(),
        "kit_desk_top": desk_top(),
        "kit_desk_leg": desk_leg(),
        "kit_shoji": shoji(False),
        "kit_shoji_lit": shoji(True),
        "kit_floor_cherry": floor_cherry(),
        "kit_floor_stone": floor_stone(),
        "kit_beam_side": beam_side(),
        "kit_beam_end": beam_end(),
        "kit_cap_side": cap_side(),
        "kit_cap_top": cap_top(),
        "kit_lantern_side": lantern_side(),
        "kit_lantern_end": lantern_end(),
        "kit_plaque_front": plaque_front(),
        "kit_railing": railing(),
        "kit_pot": pot(),
        "kit_soil": soil(),
        "kit_blossom": blossom(),
        "kit_bark": bark(),
        "kit_stone": stone_lantern(False),
        "kit_stone_light": stone_lantern(True),
    }
    for name, img in textures.items():
        img.png(name)
    print("wrote", len(textures), "textures to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
