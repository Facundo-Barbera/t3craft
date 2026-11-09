#!/usr/bin/env python3
"""kit/thread-board: a mock PNG of the thread wall's face, to review the composition without the game.

The headless HQ render (hqRender) draws blocks only: the thread wall's content is a block entity renderer
(ThreadBoardRenderer), which it does not run. This script ports WallLayout's numbers (lanes, cards, gaps, text
scales) and the renderer's colours to plain Python and draws the 9 x 4 wall with sample threads, plus a copy
downsampled to what the wall looks like from 8 blocks away (about 96 px per block on a 1080p screen).
It is a layout check, not a render: the font is a 5x7 stand-in with a 6 px advance (the game's font is
proportional, about 5.5 px on average), so widths are close, not exact.

Standard library only. From the repo root:

    python3 tools/art/thread_board_mock.py [out_dir]        # default: build/thread-board-mock
    python3 tools/art/thread_board_mock.py out --width 9 --height 4 --cards 14

Keep the constants in sync with WallLayout.java / ThreadBoardRenderer.java.
"""
import argparse
import os
import struct
import zlib

# ---- WallLayout / ThreadBoardRenderer constants (board px, 64 per block)
PPB = 64
EDGE, FRAME_PX, GAP, CARD_GAP, CARD_INSET, HEAD_GAP, CHIP_H = 8, 6, 6, 6, 5, 6, 14
CARD_PAD, STRIPE, BADGE, FOOT_H = 4, 5, 9, 8
WIDE_LANE, MID_LANE = 120, 88

KEYLINE = 0x6F5A20
FRAME = 0x2E2620
BACK = 0x110D0B
LANE = 0x1C1612
CARD = 0x39312B
SHADOW = 0x080605
PILL = 0x17120F
CHIP = 0x3B3028
CREAM = 0xF4EFE6
INK = 0x1F1E1D
SOFT = 0xC4BAAA
# ThreadState colours (the lamps' and the NPC nametags'): WallColumns.color
C_NEEDS, C_ERROR, C_WORKING, C_DONE = 0xFFAA00, 0xFF5555, 0x55FFFF, 0x55FF55
BADGES = {"V": 0x2F7F8A, "M": 0x8A5A2F, "P": 0x5A6FA8}


def metrics(lane_w):
    """(title scale, head scale, header h, card h)"""
    if lane_w >= WIDE_LANE:
        return 1.25, 1.75, 20, 30
    if lane_w >= MID_LANE:
        return 1.25, 1.5, 18, 30
    return 1.0, 1.0, 13, 27


# ---- a 5x7 stand-in font (capitals; lower case is drawn as capitals), 6 px advance
FONT = {
    "A": "01110 10001 10001 11111 10001 10001 10001", "B": "11110 10001 10001 11110 10001 10001 11110",
    "C": "01110 10001 10000 10000 10000 10001 01110", "D": "11110 10001 10001 10001 10001 10001 11110",
    "E": "11111 10000 10000 11110 10000 10000 11111", "F": "11111 10000 10000 11110 10000 10000 10000",
    "G": "01110 10001 10000 10111 10001 10001 01111", "H": "10001 10001 10001 11111 10001 10001 10001",
    "I": "01110 00100 00100 00100 00100 00100 01110", "J": "00111 00010 00010 00010 00010 10010 01100",
    "K": "10001 10010 10100 11000 10100 10010 10001", "L": "10000 10000 10000 10000 10000 10000 11111",
    "M": "10001 11011 10101 10101 10001 10001 10001", "N": "10001 11001 10101 10011 10001 10001 10001",
    "O": "01110 10001 10001 10001 10001 10001 01110", "P": "11110 10001 10001 11110 10000 10000 10000",
    "Q": "01110 10001 10001 10001 10101 10010 01101", "R": "11110 10001 10001 11110 10100 10010 10001",
    "S": "01111 10000 10000 01110 00001 00001 11110", "T": "11111 00100 00100 00100 00100 00100 00100",
    "U": "10001 10001 10001 10001 10001 10001 01110", "V": "10001 10001 10001 10001 10001 01010 00100",
    "W": "10001 10001 10001 10101 10101 11011 10001", "X": "10001 10001 01010 00100 01010 10001 10001",
    "Y": "10001 10001 01010 00100 00100 00100 00100", "Z": "11111 00001 00010 00100 01000 10000 11111",
    "0": "01110 10001 10011 10101 11001 10001 01110", "1": "00100 01100 00100 00100 00100 00100 01110",
    "2": "01110 10001 00001 00010 00100 01000 11111", "3": "11110 00001 00001 01110 00001 00001 11110",
    "4": "00010 00110 01010 10010 11111 00010 00010", "5": "11111 10000 11110 00001 00001 10001 01110",
    "6": "00110 01000 10000 11110 10001 10001 01110", "7": "11111 00001 00010 00100 01000 01000 01000",
    "8": "01110 10001 10001 01110 10001 10001 01110", "9": "01110 10001 10001 01111 00001 00010 01100",
    "+": "00000 00100 00100 11111 00100 00100 00000", "-": "00000 00000 00000 11111 00000 00000 00000",
    ".": "00000 00000 00000 00000 00000 01100 01100", ":": "00000 01100 01100 00000 01100 01100 00000",
    "/": "00001 00010 00010 00100 01000 01000 10000", "…": "00000 00000 00000 00000 00000 00000 10101",
    " ": "00000 00000 00000 00000 00000 00000 00000", "!": "00100 00100 00100 00100 00100 00000 00100",
    "?": "01110 10001 00001 00010 00100 00000 00100", "_": "00000 00000 00000 00000 00000 00000 11111",
}
FONT = {k: [row for row in v.split()] for k, v in FONT.items()}
ADV = 6


def text_w(s):
    return ADV * len(s)


class Canvas:
    def __init__(self, w, h, k):
        self.k = k
        self.w, self.h = int(w * k), int(h * k)
        self.px = bytearray(self.w * self.h * 3)

    def rect(self, x0, y0, x1, y1, col):
        k = self.k
        a, b, c, d = max(0, int(round(x0 * k))), max(0, int(round(y0 * k))), min(self.w, int(round(x1 * k))), min(self.h, int(round(y1 * k)))
        r, g, bl = (col >> 16) & 255, (col >> 8) & 255, col & 255
        row = bytes((r, g, bl)) * max(0, c - a)
        for y in range(b, d):
            self.px[(y * self.w + a) * 3:(y * self.w + c) * 3] = row

    def text(self, s, x, y, scale, col):
        """x, y = top-left of the text in board px; the 8 px font cell is 7 tall in this stand-in, centred in 8."""
        for i, ch in enumerate(s):
            g = FONT.get(ch.upper(), FONT["?"])
            for j, row in enumerate(g):
                for n, bit in enumerate(row):
                    if bit == "1":
                        self.rect(x + (i * ADV + n) * scale, y + (j + 0.5) * scale, x + (i * ADV + n + 1) * scale, y + (j + 1.5) * scale, col)

    def png(self, path, scale_down=1):
        w, h, px = self.w, self.h, self.px
        if scale_down > 1:
            nw, nh = w // scale_down, h // scale_down
            out = bytearray(nw * nh * 3)
            n2 = scale_down * scale_down
            for y in range(nh):
                for x in range(nw):
                    acc = [0, 0, 0]
                    for dy in range(scale_down):
                        base = ((y * scale_down + dy) * w + x * scale_down) * 3
                        for dx in range(scale_down):
                            o = base + dx * 3
                            acc[0] += px[o]
                            acc[1] += px[o + 1]
                            acc[2] += px[o + 2]
                    o = (y * nw + x) * 3
                    out[o], out[o + 1], out[o + 2] = acc[0] // n2, acc[1] // n2, acc[2] // n2
            w, h, px = nw, nh, out
        raw = b"".join(b"\x00" + bytes(px[y * w * 3:(y + 1) * w * 3]) for y in range(h))

        def chunk(t, d):
            c = struct.pack(">I", len(d)) + t + d
            return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                    + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def on_color(rgb):
    r, g, b = ((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255
    return INK if 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.45 else CREAM


def clip(s, max_w):
    if max_w <= 0:
        return ""
    if text_w(s) <= max_w:
        return s
    room = max_w - text_w("…")
    while s and text_w(s) > room:
        s = s[:-1]
    return s.rstrip() + "…" if s else "…"


def wrap(s, max_w):
    """WallLayout.wrap: (line 1, line 2); line 2 is '' when the title fits on one line."""
    if text_w(s) <= max_w:
        return s, ""
    end = len(s)
    while end > 0 and text_w(s[:end]) > max_w:
        end -= 1
    if end == 0:
        return clip(s, max_w), ""
    brk = s.rfind(" ", 0, end + 1)
    if brk <= 0 or brk < end // 2:
        brk = end
    return s[:brk].rstrip(), clip(s[brk:].lstrip(), max_w)


SAMPLE = {
    "Working": (C_WORKING, [
        ("Wire the thread board to ThreadState", "t3craft", "now", "V"), ("Chat panel: scroll anchoring", "t3-code", "3m", "V"),
        ("Port the queue module to 26.1", "t3craft", "12m", "M"), ("Citadel hub: aisle lighting", "t3craft", "41m", "V"),
        ("Render relay retries", "tools", "1h", "P"), ("Archive terminal screen", "t3craft", "2h", "V"), ("Lang files es_mx", "t3craft", "3h", "M")]),
    "Needs you": (C_NEEDS, [
        ("Approve: rm -rf build/", "t3craft", "now", "V"), ("Which palette for the hub?", "t3craft", "8m", "V"),
        ("Merge conflict in HqKit", "t3craft", "27m", "M")]),
    "Error": (C_ERROR, [("gradle build failed", "t3craft", "5m", "V"), ("Relay timed out", "tools", "1h", "P")]),
    "Done": (C_DONE, [
        ("Status lamp capsule", "t3craft", "4h", "V"), ("Decision podium", "t3craft", "6h", "V"), ("Console terminal", "t3craft", "1d", "M"),
        ("Hub facade pass", "t3craft", "2d", "V"), ("Docs: feat-blocks", "t3craft", "3d", "V")]),
}


def draw(out, wblocks, hblocks, ncards, k=4):
    W, H = wblocks * PPB, hblocks * PPB
    c = Canvas(W, H, k)
    c.rect(0, 0, W, H, KEYLINE)
    c.rect(1.5, 1.5, W - 1.5, H - 1.5, FRAME)
    c.rect(FRAME_PX, FRAME_PX, W - FRAME_PX, H - FRAME_PX, BACK)
    cols = list(SAMPLE.items())
    n = len(cols)
    lane_w = (W - 2 * EDGE - GAP * (n - 1)) / n
    ts, hs, head_h, card_h = metrics(lane_w)
    line_step = int(-(-8 * ts // 1))
    card_w = lane_w - 2 * CARD_INSET
    bottom = H - EDGE - 4
    shown_total = 0
    for i, (label, (color, cards)) in enumerate(cols):
        cards = cards[:ncards]
        x0 = EDGE + i * (lane_w + GAP)
        x1 = x0 + lane_w
        c.rect(x0, EDGE, x1, H - EDGE, LANE)
        c.rect(x0, EDGE, x1, EDGE + head_h, color)
        count = str(len(cards))
        pw = max(head_h - 6, text_w(count) * hs + 8)
        c.rect(x1 - 3 - pw, EDGE + 3, x1 - 3, EDGE + head_h - 3, PILL)
        ty = EDGE + (head_h - 8 * hs) / 2 + 0.5
        label_room = (lane_w - 2 * CARD_PAD - pw - 4) / hs
        c.text(clip(label, label_room), x0 + CARD_PAD, ty, hs, on_color(color))
        c.text(count, x1 - 3 - pw + (pw - text_w(count) * hs) / 2, ty, hs, CREAM)
        title_room = (card_w - STRIPE - 2 * CARD_PAD) / ts
        wrapped = [wrap(cd[0], title_room) for cd in cards]
        heights = [card_h + (line_step if w2[1] else 0) for w2 in wrapped]
        y = EDGE + head_h + HEAD_GAP
        fits_all = y + sum(h + CARD_GAP for h in heights) - CARD_GAP <= bottom
        limit = bottom if fits_all else bottom - (CHIP_H + CARD_GAP)
        shown = 0
        for (title, proj, age, env), (l1, l2), h in zip(cards, wrapped, heights):
            if y + h > limit:
                break
            cx = x0 + CARD_INSET
            c.rect(cx + 1, y + 2, cx + card_w + 1.5, y + h + 2.5, SHADOW)
            c.rect(cx, y, cx + card_w, y + h, CARD)
            c.rect(cx, y, cx + STRIPE, y + h, color)
            tx = cx + STRIPE + CARD_PAD
            c.text(l1, tx, y + 4, ts, CREAM)
            if l2:
                c.text(l2, tx, y + 4 + line_step, ts, CREAM)
            fy = y + h - CARD_PAD - FOOT_H
            c.rect(tx, fy - 0.5, tx + BADGE, fy + BADGE - 0.5, BADGES[env])
            c.text(env, tx + (BADGE - ADV + 1) / 2, fy, 1, CREAM)
            foot_room = card_w - STRIPE - 2 * CARD_PAD - BADGE - 3 - (text_w(age) + 5)
            c.text(clip(proj, foot_room), tx + BADGE + 3, fy, 1, SOFT)
            c.text(age, cx + card_w - CARD_PAD - text_w(age), fy, 1, SOFT)
            y += h + CARD_GAP
            shown += 1
            shown_total += 1
        hidden = len(cards) - shown
        if hidden > 0 and y + CHIP_H <= bottom:
            t = "+%d more" % hidden
            cw = min(card_w, text_w(t) + 12)
            mx = x0 + (lane_w - cw) / 2
            c.rect(mx, y, mx + cw, y + CHIP_H, CHIP)
            c.text(t, mx + (cw - text_w(t)) / 2, y + (CHIP_H - 8) / 2 + 0.5, 1, CREAM)
    os.makedirs(out, exist_ok=True)
    full = os.path.join(out, "wall_face_%dx%d.png" % (wblocks, hblocks))
    c.png(full)  # 4 px per board px
    c.png(os.path.join(out, "wall_face_%dx%d_at8blocks.png" % (wblocks, hblocks)), scale_down=max(1, round(k * PPB / 96)))
    print("lane %.1f px, title %.2fx, header %.2fx, card %d px (+%d for a 2nd line), cards drawn %d -> %s" % (lane_w, ts, hs, card_h, line_step, shown_total, out))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("out", nargs="?", default="build/thread-board-mock")
    ap.add_argument("--width", type=int, default=9)
    ap.add_argument("--height", type=int, default=4)
    ap.add_argument("--cards", type=int, default=99, help="at most this many cards per column")
    a = ap.parse_args()
    draw(a.out, a.width, a.height, a.cards)
