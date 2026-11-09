#!/usr/bin/env python3
"""Draws the 16x16 textures of t3craft's HQ blocks kit (cherry campus style).

Original pixel art for t3craft (MIT), drawn procedurally: nothing is copied or derived from
Mojang's textures. Deterministic (fixed seeds), stdlib only (zlib + struct), so re-running it gives
byte-identical PNGs:

    python3 tools/art/blocks_kit_textures.py

Writes into mod/src/main/resources/assets/t3craft/textures/block/kit_*.png.
"""
import json
import os
import random
import struct
import sys
import zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mod", "src", "main", "resources", "assets", "t3craft", "textures", "block")


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
    def __init__(self, fill=(0, 0, 0, 0), n=16):
        self.n = n
        self.p = [[fill for _ in range(n)] for _ in range(n)]

    def set(self, x, y, c):
        if 0 <= x < self.n and 0 <= y < self.n:
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

        data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.n, self.n, 8, 6, 0, 0, 0))
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


# ---- floor tiles (32x32, kit/floor-tiles) -----------------------------------------------------
# Both floors are 32x32 so one block carries a whole bit of paving. Every pattern wraps on all four edges (the
# texture is periodic) and the block blockstates pick a texture variant and a rotation per cell
# (weights, assets only), so a big floor never repeats block by block.
FN = 32


def shade(c, d):
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), 255)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def wrap_rect(img, x0, y0, w, h, fn):
    """Calls fn(x, y, lx, ly) for every pixel of a rect that wraps around the 32x32 tile."""
    for ly in range(h):
        for lx in range(w):
            fn((x0 + lx) % FN, (y0 + ly) % FN, lx, ly)


# stone: warm greys (andesite family) with a hint of cherry and of calcite, never a saturated dot
FLAG = [hexc(c) for c in ("#938F89", "#908C86", "#8C8882", "#96928C")]
FLAG_CHERRY = hexc("#9A8B86")
FLAG_CALCITE = hexc("#A8A59C")
JOINT = hexc("#625E58")

# layouts: rectangles (x, y, w, h) that start on the tile edge, so every block edge keeps a joint. No piece is
# smaller than 16 x 8 px: 2 x 2 flags, or a 2 x 1 flag over two squares.
STONE_LAYOUTS = (
    # A: 2 x 2 square flags
    ((0, 0, 16, 16), (16, 0, 16, 16), (0, 16, 16, 16), (16, 16, 16, 16)),
    # B: the same grid, one flag of calcite (the quiet accent, 1 block in 4 or so)
    ((0, 0, 16, 16), (16, 0, 16, 16), (0, 16, 16, 16), (16, 16, 16, 16)),
    # C: one long flag across the top, two squares under it
    ((0, 0, 32, 16), (0, 16, 16, 16), (16, 16, 16, 16)),
)
STONE_TINT = {0: (3, "cherry"), 1: (1, "calcite"), 2: (2, "cherry")}  # variant -> (rect index, accent)


def floor_stone(variant=0, seed=32):
    """Stone paving: big flags with a thin, even joint, 2-3 close warm greys, no coloured dots.

    variant 0: 2 x 2 square flags, one leaning cherry; 1: the same with one calcite flag; 2: a long flag over two
    squares. Joints are 1 px everywhere, also on the block edge (a lighter bevel runs along the inside).
    """
    rng = random.Random(seed + variant * 101)
    img = Img(n=FN)
    rects = STONE_LAYOUTS[variant]
    tint_i, tint_kind = STONE_TINT[variant]
    for i, (x0, y0, w, h) in enumerate(rects):
        base = FLAG[(i + variant) % len(FLAG)]
        if i == tint_i:
            base = mix(base, FLAG_CHERRY if tint_kind == "cherry" else FLAG_CALCITE, 0.7 if tint_kind == "calcite" else 0.6)
        cells = {}
        # coarse 8x8 clouds so a flag shades softly instead of salt-and-pepper
        for cy in range(0, h + 8, 8):
            for cx in range(0, w + 8, 8):
                cells[(cx // 8, cy // 8)] = rng.choice((-3, -2, -1, 0, 0, 1, 2))

        def paint(x, y, lx, ly, base=base, cells=cells, w=w, h=h, calcite=(i == tint_i and tint_kind == "calcite")):
            if lx == 0 or ly == 0:
                img.set(x, y, JOINT)
                return
            d = cells[(lx // 8, ly // 8)]
            if lx == 1 or ly == 1:
                d += 4 if calcite else 3  # chamfer next to the joint
            elif lx == w - 1 or ly == h - 1:
                d -= 2
            img.set(x, y, shade(base, d))

        wrap_rect(img, x0, y0, w, h, paint)
        # one or two 2 px pits per flag, neutral grey only (never at the edge, never single-pixel slivers)
        for _ in range(max(1, w * h // 200)):
            fx = rng.randrange(3, w - 4)
            fy = rng.randrange(3, h - 3)
            d = rng.choice((-6, -5, 5))
            for dx in (0, 1):
                img.set((x0 + fx + dx) % FN, (y0 + fy) % FN, shade(base, d))
    return img


# cherry: strip flooring with boards of different widths and lengths, seams, grain
# two tones that alternate row by row (light / dark board), a warm wood salmon rather than pink
BOARD_TONES = [hexc("#D09C84"), hexc("#B98568")]
BOARD_SEAM = hexc("#6E4130")
BOARD_END = hexc("#5F3627")
BOARD_ROWS = (8, 8, 8, 8)  # sums to 32

# end joints per variant: for every board row, the x of its butt joint (empty: the board runs the whole tile and
# on into the next block)
CHERRY_BREAKS = (
    ((20,), (), (6,), (13,)),
    ((4,), (), (17,), ()),
    ((), (26,), (), (10,)),
)


def floor_cherry(variant=0, seed=31):
    """Cherry strip flooring: 4 boards of 8 px, butt joints at different places, long grain, no frame."""
    rng = random.Random(seed + variant * 103)
    img = Img(n=FN)
    y = 0
    for r, bh in enumerate(BOARD_ROWS):
        breaks = CHERRY_BREAKS[variant][r]
        cuts = sorted(breaks) if breaks else [0]
        n = len(cuts)
        for k in range(n):
            start = cuts[k]
            end = cuts[(k + 1) % n] + (FN if (k + 1) == n else 0)
            # the two tones alternate by row (same on every variant so the rows line up across blocks); each
            # board drifts a few levels so neighbours in one row are not identical
            tone = shade(BOARD_TONES[r % 2], rng.choice((-4, -2, 0, 2, 4)))
            # grain: a few 1 px streaks along the whole board, lighter or darker, plus a lit top edge
            streak = [0] * bh
            streak[1] = 6
            for _ in range(2):
                streak[rng.randrange(2, bh)] = rng.choice((-6, -4, 3))
            for xx in range(start, end):
                x = xx % FN
                for ly in range(bh):
                    yy = (y + ly) % FN
                    if ly == 0:
                        img.set(x, yy, BOARD_SEAM)
                        continue
                    img.set(x, yy, shade(tone, streak[ly]))
            # a few short dark flecks of grain inside the board
            for _ in range(3):
                gx = start + rng.randrange(2, max(3, end - start - 4))
                gy = rng.randrange(2, bh)
                for dx in range(rng.choice((2, 3, 4))):
                    img.set((gx + dx) % FN, (y + gy) % FN, shade(tone, -10))
            if breaks:
                for ly in range(bh):
                    img.set(start % FN, (y + ly) % FN, BOARD_END)
                    if ly:
                        img.set((start + 1) % FN, (y + ly) % FN, shade(tone, -14))  # shadow in the end joint
                        img.set((start + 2) % FN, (y + ly) % FN, shade(tone, 5))
        y += bh
    return img


# blockstate picks per floor: texture variants (a, b, c) with weights, and the y rotations allowed. Stone is square
# paving, so it turns every way; cherry only turns 180 degrees so the boards keep running the same way.
FLOOR_WEIGHTS = {"stone": (8, 3, 3), "cherry": (5, 4, 4)}
FLOOR_ROTS = {"stone": (0, 90, 180, 270), "cherry": (0, 180)}
FLOOR_SUFFIXES = ("", "_b", "_c")


def write_floor_assets():
    """Models and weighted blockstates of the two floor tiles (assets only, no registry change)."""
    base = os.path.join(OUT, "..", "..")
    for kind in ("cherry", "stone"):
        for suf in FLOOR_SUFFIXES:
            with open(os.path.join(base, "models", "block", "%s_floor_tile%s.json" % (kind, suf)), "w") as f:
                json.dump({"parent": "minecraft:block/cube_all",
                           "textures": {"all": "t3craft:block/kit_floor_%s%s" % (kind, suf)}}, f, indent=2)
                f.write("\n")
        entries = []
        for suf, wt in zip(FLOOR_SUFFIXES, FLOOR_WEIGHTS[kind]):
            for rot in FLOOR_ROTS[kind]:
                e = {"model": "t3craft:block/%s_floor_tile%s" % (kind, suf), "weight": wt}
                if rot:
                    e["y"] = rot
                entries.append(e)
        with open(os.path.join(base, "blockstates", "%s_floor_tile.json" % kind), "w") as f:
            json.dump({"variants": {"": entries}}, f, indent=2)
            f.write("\n")


def floor_variants():
    """name -> Img for every floor texture. Variant 0 keeps the old file names."""
    out = {}
    for v, suffix in enumerate(("", "_b", "_c")):
        out["kit_floor_cherry" + suffix] = floor_cherry(v)
        out["kit_floor_stone" + suffix] = floor_stone(v)
    return out


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


def write_rgb(path, rows):
    """Writes a plain RGB PNG from a list of rows of (r, g, b) tuples (review images only)."""
    h = len(rows)
    w = len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px[:3]) for px in row) for row in rows)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def rotate(rows, k):
    for _ in range(k % 4):
        rows = [list(r) for r in zip(*rows[::-1])]
    return rows


def preview(outdir, blocks=12):
    """Review images: a floor of random variants + rotations as the blockstate picks them, then the same box-filtered
    to what a mipmapped distance looks like. Files: floor_<kind>_{near,mid,far}.png."""
    os.makedirs(outdir, exist_ok=True)
    kinds = {k: ("kit_floor_" + k, tuple(r // 90 for r in FLOOR_ROTS[k])) for k in ("cherry", "stone")}
    vars_ = floor_variants()
    for kind, (prefix, rots) in kinds.items():
        rng = random.Random(7)
        tiles = [vars_[prefix + s].p for s in FLOOR_SUFFIXES]
        wts = FLOOR_WEIGHTS[kind]
        n = FN * blocks
        grid = [[None] * n for _ in range(n)]
        for bz in range(blocks):
            for bx in range(blocks):
                t = rotate(rng.choices(tiles, weights=wts)[0], rng.choice(rots))
                for y in range(FN):
                    for x in range(FN):
                        grid[bz * FN + y][bx * FN + x] = t[y][x]
        write_rgb(os.path.join(outdir, "floor_%s_near.png" % kind), [[px for px in row for _ in (0, 1)] for row in grid for _ in (0, 1)])
        for name, f in (("mid", 4), ("far", 16)):
            m = n // f
            out = []
            for y in range(m):
                row = []
                for x in range(m):
                    acc = [0, 0, 0]
                    for yy in range(f):
                        for xx in range(f):
                            px = grid[y * f + yy][x * f + xx]
                            for i in range(3):
                                acc[i] += px[i]
                    row.append(tuple(v // (f * f) for v in acc))
                out.append(row)
            z = 384 // m if m < 384 else 1
            write_rgb(os.path.join(outdir, "floor_%s_%s.png" % (kind, name)), [[px for px in row for _ in range(z)] for row in out for _ in range(z)])
    print("preview in", outdir)


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
        return
    os.makedirs(OUT, exist_ok=True)
    textures = {
        "kit_cherry_wood": cherry_wood(),
        "kit_desk_top": desk_top(),
        "kit_desk_leg": desk_leg(),
        "kit_shoji": shoji(False),
        "kit_shoji_lit": shoji(True),
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
    textures.update(floor_variants())
    write_floor_assets()
    for name, img in textures.items():
        img.png(name)
    print("wrote", len(textures), "textures to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
