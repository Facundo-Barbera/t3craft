#!/usr/bin/env python3
"""kit/thread-board: the board lamp ("luz de aviso" of the attention board): textures (plain + animated strips),
their .mcmeta and the six block models of t3craft:board_lamp.

Standard library only; reuses the status lamp's pixel code, so it has to live next to status_lamp_art.py.
Run from the repo root:

    python3 tools/art/board_lamp_art.py

Why not the status lamp's capsule: the board lamp sits in a row of beams on the board's frame, not on a desk.
It is a pilot light of a control panel: a low flange (steel plate + gold collar) with a short glass lens and a
steel cap, 13 px tall, readable from every side (the block has no `facing`, so it cannot face one way only).
Same family as the status lamp: same steel/gold base texture (`status_lamp_base`), same lens colours and glyphs,
same silhouette language, so a state reads the same on a desk and on the board:

    off        short dark thin glass (no glow)            idle       short dim gray lens, pause bars
    working    lens with a bright band, cyan spinner      done       plain green lens, check mark
    attention  lens + 4 horns (a siren star), "!" blinks slowly (frametime 10)
    error      lens + 4 corner claws with spikes (a cage of thorns), X blinks fast (frametime 4)
Only the lens and its horns/claws glow (light_emission per element).
"""
import json
import os

import status_lamp_art as S

# state -> (colour, lens crop (x0, y0, x1, y1) = face size in px, frames, frametime)
STATES = {
    "off": (0x2A2A2A, (5, 4, 11, 11), 1, 0),
    "idle": (0xAAAAAA, (5, 4, 11, 11), 1, 0),
    "working": (0x55FFFF, (4, 3, 12, 12), 4, 3),
    "attention": (0xFFAA00, (4, 3, 12, 12), 2, 10),
    "error": (0xFF5555, (4, 3, 12, 12), 2, 4),
    "done": (0x55FF55, (4, 3, 12, 12), 1, 0),
}
EMIT = {"off": 0, "idle": 7, "working": 13, "attention": 15, "error": 15, "done": 13}


def write_textures():
    saved = S.STATES
    S.STATES = STATES  # lens_frame() reads the module's table: draw with this lamp's crops
    try:
        for name, (col, crop, n, ft) in STATES.items():
            rows = []
            for f in range(n):
                rows += S.lens_frame(name, f)
            S.write_png(os.path.join(S.TEX, "board_lamp_%s.png" % name), rows)
            meta = os.path.join(S.TEX, "board_lamp_%s.png.mcmeta" % name)
            if n > 1:
                with open(meta, "w") as fh:
                    json.dump({"animation": {"frametime": ft}}, fh, indent=2)
                    fh.write("\n")
            elif os.path.exists(meta):
                os.remove(meta)
    finally:
        S.STATES = saved


def model(name):
    col, crop, n, ft = STATES[name]
    emit = EMIT[name]
    small = name in ("off", "idle")
    el = S.element
    x0, y0, x1, y1 = crop
    els = [
        el([2, 0, 2], [14, 1, 14], "steel"),                      # mounting plate
        el([3, 1, 3], [13, 2, 13], "gold"),                       # collar
    ]
    top = 9 if small else 12                                      # top of the lens
    els.append(el([x0, 2, x0], [x1, top, x1], "lens", crop=crop, emit=emit, skip=("up", "down")))
    cap = [x0, top, x0], [x1, top + 1, x1]
    els.append(el(cap[0], cap[1], "steel"))
    if not small:
        els.append(el([x0 + 2, top + 1, x0 + 2], [x1 - 2, top + 2, x1 - 2], "gold" if name != "attention" else "steel"))
    if name == "working":
        els.append(el([x0 - 0.5, 6, x0 - 0.5], [x1 + 0.5, 7.5, x1 + 0.5], "bright", emit=emit))
    elif name == "attention":
        # four horns at mid height: a siren star 12 px across
        els.append(el([12, 5, 7], [14, 9, 9], "plain", emit=emit))
        els.append(el([2, 5, 7], [4, 9, 9], "plain", emit=emit))
        els.append(el([7, 5, 12], [9, 9, 14], "plain", emit=emit))
        els.append(el([7, 5, 2], [9, 9, 4], "plain", emit=emit))
    elif name == "error":
        # a cage of thorns: four claws at the corners, each with a spike
        for cx in (2, 12):
            for cz in (2, 12):
                els.append(el([cx, 2, cz], [cx + 2, 9, cz + 2], "claw", emit=emit))
                els.append(el([cx + 0.5, 9, cz + 0.5], [cx + 1.5, 11.5, cz + 1.5], "claw", emit=emit))
    return {
        "parent": "minecraft:block/block",
        "textures": {"base": "t3craft:block/status_lamp_base", "lens": "t3craft:block/board_lamp_%s" % name, "particle": "t3craft:block/status_lamp_base"},
        "elements": els,
    }


def write_models():
    for name in STATES:
        with open(os.path.join(S.MODELS, "board_lamp_%s.json" % name), "w") as f:
            json.dump(model(name), f, indent=2)
            f.write("\n")


if __name__ == "__main__":
    write_textures()
    write_models()
    print("board lamp art written")
