#!/usr/bin/env python3
"""feat/status-screen: a mock PNG of the status screen's face (a big screen bound `beacon`), to review the composition
without the game.

The headless HQ render (hqRender) draws blocks only: the screen's content is a block entity renderer (MonitorRenderer ->
StatusScreen), which it does not run. This script ports StatusScreen.build (tiles, cells, scales) and the FAR layout of
MonitorLod (64 px per block, 2/16 bezel, 5/16 chin) to plain Python and draws the screen with sample data, plus a copy
downsampled to about 96 px per block (what a 1080p screen shows at ~8 blocks) and an empty and a busy variant.
It is a layout check, not a render: the font is the 5x7 stand-in of thread_board_mock.py with a 6 px advance (the game's font
is proportional, about 5.5 px on average), so widths are close, not exact.

Standard library only. From the repo root:

    python3 tools/art/status_screen_mock.py [out_dir]        # default: build/status-screen-mock
    python3 tools/art/status_screen_mock.py out --width 9 --height 3

Keep the constants in sync with StatusScreen.java.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from thread_board_mock import ADV, Canvas, shade, text_w  # noqa: E402

PPB = 64.0
GLASS, CELL, CELL_EMPTY = 0x0B0D0F, 0x14181C, 0x101316
TEXT, MUTED, ZERO_NUMBER = 0xE8E8E8, 0x8A9096, 0x4A5056
PR_COLOR, PROJECT_COLOR = 0x55FF55, 0x55FFFF
GAP, PAD, STRIPE = 6.0, 5.0, 5.0
# StationStatus colours and words, in tile order (most urgent first)
TILES = [("NEEDS YOU", 0xFFAA00), ("ERROR", 0xFF5555), ("WORKING", 0x55FFFF), ("DONE", 0x55FF55), ("IDLE", 0xAAAAAA)]

SAMPLES = {
    "busy": dict(counts=[3, 1, 6, 4, 2], prs=5, proj_active=3, proj_total=4, oldest="Approve: rm -rf build/ in the render relay", age="27m"),
    "calm": dict(counts=[0, 0, 0, 2, 5], prs=0, proj_active=0, proj_total=3, oldest=None, age=None),
    "empty": dict(counts=[0, 0, 0, 0, 0], prs=0, proj_active=0, proj_total=0, oldest=None, age=None),
}


def clip(s, px):
    if px <= 0:
        return ""
    if text_w(s) <= px:
        return s
    room = px - text_w("…")
    while s and text_w(s) > room:
        s = s[:-1]
    return s.rstrip() + "…"


class Face:
    """Collects the rectangles and texts like StatusScreen.Panel, then paints them in layer order."""

    def __init__(self, c):
        self.c = c
        self.items = []

    def rect(self, x0, y0, x1, y1, layer, col):
        self.items.append((layer, 0, ("r", x0, y0, x1, y1, col)))

    def text(self, s, x, y, scale, col):
        self.items.append((9, 1, ("t", s, x, y, scale, col)))

    def paint(self):
        for _, _, it in sorted(self.items, key=lambda i: (i[0], i[1])):
            if it[0] == "r":
                self.c.rect(*it[1:])
            else:
                self.c.text(*it[1:])


def tile(f, label, color, count, x0, y0, x1, y1, k):
    on = count > 0
    bg = shade(color, 0.22) if on else CELL_EMPTY
    f.rect(x0, y0, x1, y1, 1, bg)
    f.rect(x0, y0, x1, y0 + STRIPE * k, 2, color if on else shade(color, 0.35))
    num = str(count)
    w = x1 - x0 - 8 * k
    label_scale = max(0.75, min(1.75 * k, w / max(1, text_w(label))))
    num_scale = min(6 * k, w / max(1, text_w(num)))
    area_top = y0 + STRIPE * k
    area_h = y1 - area_top
    inner = 5 * k
    label_h = 8 * label_scale
    if 8 * num_scale + inner + label_h > area_h - 6 * k:
        num_scale = max(1.0, (area_h - 6 * k - inner - label_h) / 8)
    num_h = 8 * num_scale
    top = area_top + (area_h - (num_h + inner + label_h)) / 2
    cx = (x0 + x1) / 2
    f.text(num, cx - text_w(num) * num_scale / 2, top, num_scale, color if on else ZERO_NUMBER)
    f.text(label, cx - text_w(label) * label_scale / 2, top + num_h + inner, label_scale, TEXT if on else MUTED)


def cell(f, x0, y0, x1, y1, k, accent, label, label_col, value, value_col):
    f.rect(x0, y0, x1, y1, 1, CELL)
    f.rect(x0, y0, x0 + 4 * k, y1, 2, accent)
    left = x0 + 4 * k + 7 * k
    w = x1 - left - 4 * k
    label_scale = max(0.75, min(1.25 * k, w / max(1, text_w(label))))
    label_h = 8 * label_scale
    h = y1 - y0
    value_scale = max(1.0, min(4 * k, min((h - label_h - 14 * k) / 8, w / max(1, text_w(value)))))
    block = label_h + 4 * k + 8 * value_scale
    top = y0 + (h - block) / 2
    f.text(label, left, top, label_scale, label_col)
    f.text(value, left, top + label_h + 4 * k, value_scale, value_col)


def draw_face(c, wb, hb, s):
    k = 1.0  # ppb / 64
    W, H = wb * PPB, hb * PPB
    bezel, chin = 2 * PPB / 16, 5 * PPB / 16
    f = Face(c)
    x0, x1 = bezel + PAD * k, W - bezel - PAD * k
    y0, y1 = bezel + PAD * k, H - chin - PAD * 0.8 * k
    c.rect(0, 0, W, H, 0x1B1714)  # the casing
    f.rect(bezel, bezel, W - bezel, H - chin, 0, GLASS)
    gap = GAP * k
    tiles_h = round((y1 - y0 - gap) * 0.60)
    tw = (x1 - x0 - gap * (len(TILES) - 1)) / len(TILES)
    for i, ((label, color), n) in enumerate(zip(TILES, s["counts"])):
        tx = x0 + i * (tw + gap)
        tile(f, label, color, n, tx, y0, tx + tw, y0 + tiles_h, k)
    sy0 = y0 + tiles_h + gap
    pr_w = round((x1 - x0) * 0.22)
    pr_x = x0
    pj_x = pr_x + pr_w + gap
    old_x = pj_x + pr_w + gap
    pr_on, pj_on = s["prs"] > 0, s["proj_active"] > 0
    cell(f, pr_x, sy0, pr_x + pr_w, y1, k, PR_COLOR if pr_on else ZERO_NUMBER, "PRS TO REVIEW", MUTED, str(s["prs"]),
         PR_COLOR if pr_on else ZERO_NUMBER)
    cell_value = "%d/%d" % (s["proj_active"], s["proj_total"])
    cell(f, pj_x, sy0, pj_x + pr_w, y1, k, PROJECT_COLOR if pj_on else ZERO_NUMBER, "ACTIVE PROJECTS", MUTED, cell_value,
         PROJECT_COLOR if pj_on else ZERO_NUMBER)
    total = sum(s["counts"])
    any_waiting = s["oldest"] is not None
    needs, done = TILES[0][1], TILES[3][1]
    accent = needs if any_waiting else ZERO_NUMBER if total == 0 else done
    f.rect(old_x, sy0, x1, y1, 1, CELL)
    f.rect(old_x, sy0, old_x + 4 * k, y1, 2, accent)
    left = old_x + 4 * k + 7 * k
    w = x1 - left - 4 * k
    label = ("OLDEST WAITING · " + s["age"] if s["age"] else "OLDEST WAITING") if any_waiting else "WAITING FOR YOU"
    label_scale = max(0.75, min(1.25 * k, w / max(1, text_w(label))))
    body = s["oldest"] if any_waiting else "NO THREADS" if total == 0 else "NOBODY"
    h = y1 - sy0
    body_scale = max(1.0, min(2.0 * k, (h - 8 * label_scale - 14 * k) / 8))
    shown = clip(body, w / body_scale)
    block = 8 * label_scale + 4 * k + 8 * body_scale
    top = sy0 + (h - block) / 2
    f.text(label.replace("·", "-"), left, top, label_scale, needs if any_waiting else MUTED)
    f.text(shown, left, top + 8 * label_scale + 4 * k, body_scale, TEXT if any_waiting else MUTED if total == 0 else done)
    f.paint()
    # the frame's LED: the most urgent state present
    led = next((col for (_, col), n in zip(TILES, s["counts"]) if n > 0), TILES[4][1])
    c.rect(W - 5 * PPB / 16, H - 4.5 * PPB / 16, W - 3 * PPB / 16, H - 3.5 * PPB / 16, led)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out", nargs="?", default="build/status-screen-mock")
    ap.add_argument("--width", type=int, default=9)
    ap.add_argument("--height", type=int, default=3)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    k = 4
    for name, s in SAMPLES.items():
        c = Canvas(a.width * PPB, a.height * PPB, k)
        draw_face(c, a.width, a.height, s)
        base = os.path.join(a.out, "status_%s_%dx%d" % (name, a.width, a.height))
        c.png(base + ".png")
        c.png(base + "_at8blocks.png", scale_down=max(1, round(k * PPB / 96)))
        print("wrote", base + ".png")


if __name__ == "__main__":
    main()
