#!/usr/bin/env python3
"""kit/thread-board: the thread wall anchor block's textures (16x16): a plain dark panel, the colour of the board's
backing (ThreadBoardRenderer.BACK), so the anchor cell does not show up as a small picture inside the wall where the
renderer's content is not drawn (far LOD, headless renders). Standard library only; from the repo root:

    python3 tools/art/thread_board_art.py
"""
import os

import status_lamp_art as S

BACK = 0x110D0B
EDGE = 0x1C1612


def panel():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            c = EDGE if (x in (0, 15) or y in (0, 15)) else BACK
            row.append(S.px(S.rgb(c)))
        rows.append(row)
    return rows


if __name__ == "__main__":
    for name in ("thread_board_front", "thread_board_side"):
        S.write_png(os.path.join(S.TEX, name + ".png"), panel())
    print("thread board textures written")
