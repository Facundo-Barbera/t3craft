#!/usr/bin/env python3
"""Writes the block models, blockstates and item models of the kit's paper lanterns.

    python3 mod/tools/kit_lantern_models.py

Four silhouettes, so each reads on its own from far away (warm paper, cherry structure, dark finishes):

- paper_lantern (standing andon, 1 block): a squat lacquered box on the ground, four cherry posts, a stepped roof.
- paper_lantern_hanging (chochin, 1 block): a round barrel of paper on a cord; hangs from a chain, beam or eave.
- paper_lantern_wall (bracket lantern, 1 block): a small box on an iron arm out of a wall, beam or post.
- paper_lantern_tall (2 blocks): a plaza lantern: stone plinth, cherry post, a big paper box under a hip roof.

Textures come from kit_lantern_textures.py. Deterministic, stdlib only.
"""
import json
import os

RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "t3craft")
TONES = ("paper", "rose", "vermilion")
FACES = ("north", "south", "east", "west", "up", "down")
SIDES = ("north", "south", "east", "west")
GLOW = 15  # light_emission of the paper


def default_uv(face, f, t):
    """Vanilla's default uv of a face of the box f..t (so a rescaled copy can keep its texture window)."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]


def el(frm, to, tex, faces=FACES, uv=None, name=None, glow=False):
    """A box; `tex` is a texture variable or a dict face -> variable. `uv` maps face -> uv for overrides."""
    f = {}
    for face in faces:
        t = tex[face] if isinstance(tex, dict) else tex
        d = {"texture": "#" + t}
        if uv and face in uv:
            d["uv"] = uv[face]
        f[face] = d
    e = {"from": list(frm), "to": list(to), "faces": f}
    if name:
        e["name"] = name
    if glow:
        e["light_emission"] = GLOW
    return e


def posts(xs, zs, y0, y1, tex, size=2):
    return [el((x, y0, z), (x + size, y1, z + size), tex, faces=SIDES + ("down",), name="post") for x in xs for z in zs]


def write(path, obj):
    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")


def model(textures, elements):
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": textures, "elements": elements}


def andon():
    """
    Standing andon: four dark feet with a gap under the body, four 2x2 cherry posts round a lit paper body
    (3 px in, where the lamp BER lays its status glow, KitLanternBlock.BODY_INSET), a dark foot and head rail, and
    a flared two-tier lacquer roof (eaves 1 px past the posts) whose corners rise in four dark tips.
    """
    side_uv = {s: [3, 3, 13, 13] for s in SIDES}
    els = [
        el((3, 2, 3), (13, 3, 13), "dark", faces=SIDES + ("down",), name="foot rail"),
        el((3, 3, 3), (13, 13, 13), "paper", faces=SIDES, uv=side_uv, name="paper", glow=True),
        el((3.5, 3, 3.5), (12.5, 13, 12.5), "paper", faces=("up", "down"), name="paper ends", glow=True),
        el((3, 13, 3), (13, 14, 13), "dark", faces=SIDES + ("down",), name="head rail"),
        el((1, 14, 1), (15, 15, 15), {**{s: "dark" for s in SIDES}, "up": "roof", "down": "dark"}, name="roof"),
        el((4, 15, 4), (12, 16, 12), {**{s: "dark" for s in SIDES}, "up": "finial", "down": "dark"}, name="roof cap"),
    ]
    els += posts((2, 12), (2, 12), 2, 14, "cherry")
    els += posts((2, 12), (2, 12), 0, 2, "dark")  # the feet
    for x in (1, 13):
        for z in (1, 13):
            els.append(el((x, 15, z), (x + 2, 16, z + 2), {**{s: "dark" for s in SIDES}, "up": "roof", "down": "dark"}, name="roof tip"))
    return model({
        "paper": "t3craft:block/kit_lantern_andon",
        "dark": "t3craft:block/kit_lantern_lacquer",
        "cherry": "t3craft:block/kit_lantern_cherry",
        "roof": "t3craft:block/kit_lantern_roof",
        "finial": "t3craft:block/kit_lantern_finial",
        "brass": "t3craft:block/kit_lantern_brass",
        "particle": "t3craft:block/kit_lantern_andon",
    }, els)


def chochin(tone):
    """
    Hanging chochin: a round barrel of lit paper (widths 10, 12, 14, 12, 10 px) between dark lacquer rings,
    a brass lid, a cord up to the top of the block (it meets a chain or the block above) and a brass drop.
    """
    return model({
        "paper": "t3craft:block/kit_lantern_chochin_" + tone,
        "band": "t3craft:block/kit_lantern_band",
        "brass": "t3craft:block/kit_lantern_brass",
        "particle": "t3craft:block/kit_lantern_chochin_" + tone,
    }, [
        el((7, 0.5, 7), (9, 2, 9), "brass", name="drop"),
        el((4, 2, 4), (12, 3, 12), "band", name="bottom ring"),
        el((3, 3, 3), (13, 4, 13), "paper", name="tier 10", glow=True),
        el((2, 4, 2), (14, 6, 14), "paper", name="tier 12", glow=True),
        el((1, 6, 1), (15, 10, 15), "paper", name="belly", glow=True),
        el((2, 10, 2), (14, 12, 14), "paper", name="tier 12 top", glow=True),
        el((3, 12, 3), (13, 13, 13), "paper", name="tier 10 top", glow=True),
        el((4, 13, 4), (12, 14, 12), "band", name="top ring"),
        el((5, 14, 5), (11, 15, 11), "brass", name="lid"),
        el((7.5, 15, 7.5), (8.5, 16, 8.5), "band", faces=SIDES, name="cord"),
    ])


def wall(tone):
    """
    Wall lantern facing north (the wall is south, z = 16): a tall iron back plate, a long bracket arm with a brace
    and a hook, and a TALL NARROW lantern (8 px wide, 9 px of paper, cherry corner posts) under a pointed dark roof,
    so it is neither the round chochin nor the squat standing box. Hung 8 px off the wall.
    """
    els = [
        el((5, 2, 15), (11, 15, 16), "iron", name="back plate"),
        el((7, 3, 15.5), (9, 4, 16), "brass", name="plate nail low"),
        el((7, 13, 15.5), (9, 14, 16), "brass", name="plate nail high"),
        el((7, 13, 7), (9, 14, 15), "iron", name="arm"),
        el((7.5, 10, 13.5), (8.5, 13, 15), "iron", name="brace"),
        el((7.5, 11, 11), (8.5, 12, 13.5), "iron", name="brace step"),
        el((7.5, 12, 6.5), (8.5, 13, 7.5), "iron", name="hook"),
        el((3, 11, 3), (13, 12, 13), {**{s: "dark" for s in FACES}, "up": "roof"}, name="roof"),
        el((5, 12, 5), (11, 13, 11), {**{s: "dark" for s in FACES}, "up": "roof"}, name="roof mid"),
        el((7, 13, 7), (9, 14, 9), "brass", faces=SIDES + ("up",), name="finial"),
        el((4, 3, 4), (12, 11, 12), "paper", faces=SIDES, name="shade", glow=True),
        el((4, 3, 4), (12, 11, 12), "paper", faces=("up", "down"), name="shade ends", glow=True),
        el((3, 1, 3), (13, 3, 13), "dark", name="base"),
    ]
    els += posts((3.5, 11.5), (3.5, 11.5), 3, 11, "cherry", size=1)
    return model({
        "paper": "t3craft:block/kit_lantern_box_" + tone,
        "dark": "t3craft:block/kit_lantern_lacquer",
        "cherry": "t3craft:block/kit_lantern_cherry",
        "roof": "t3craft:block/kit_lantern_roof",
        "finial": "t3craft:block/kit_lantern_finial",
        "brass": "t3craft:block/kit_lantern_brass",
        "iron": "t3craft:block/kit_lantern_iron",
        "particle": "t3craft:block/kit_lantern_box_" + tone,
    }, els)


TALL_TEXTURES = {
    "paper": "t3craft:block/kit_lantern_tall",
    "dark": "t3craft:block/kit_lantern_lacquer",
    "cherry": "t3craft:block/kit_lantern_cherry",
    "stone": "t3craft:block/kit_lantern_stone",
    "roof": "t3craft:block/kit_lantern_roof",
    "finial": "t3craft:block/kit_lantern_finial",
    "brass": "t3craft:block/kit_lantern_brass",
    "particle": "t3craft:block/kit_lantern_tall",
}


def tall_lower():
    """
    Lower half of the plaza lantern, a post with a proper base: two stone steps, a lacquer plinth cap, an 8 px cherry
    shaft with a lacquer collar ring (collarin), a slimmer neck, and a flared lacquer capital (6, 10, 12, 14 px wide)
    that cradles the head, which starts in the cell above.
    """
    return model(TALL_TEXTURES, [
        el((1, 0, 1), (15, 2, 15), "stone", name="base foot"),
        el((2, 2, 2), (14, 4, 14), "stone", name="base step"),
        el((3, 4, 3), (13, 5, 13), "dark", name="plinth cap"),
        el((4, 5, 4), (12, 10, 12), "cherry", faces=SIDES + ("down",), name="shaft"),
        el((3, 10, 3), (13, 11, 13), "dark", name="collar"),
        el((3.5, 11, 3.5), (12.5, 12, 12.5), "brass", faces=SIDES, name="collar band"),
        el((5, 11, 5), (11, 14, 11), "cherry", faces=SIDES + ("down",), name="neck"),
        el((4, 14, 4), (12, 15, 12), "dark", name="capital 8"),
        el((2, 15, 2), (14, 16, 14), "dark", name="capital 12"),
    ])


def tall_upper():
    """
    Upper half: the head of a plaza lantern, much bigger than the standing andon. A full-width lacquer tray, a 12 x 10 px
    paper box (cherry corner posts, dark head rail) that glows, and a pyramid roof of its own (eaves across the whole
    cell, two steps, brass-vented crown) whose corners rise in four dark tips, like the andon's but at plaza scale.
    """
    side_uv = {s: [2, 3, 14, 13] for s in SIDES}
    roof = lambda a: {**{s: "dark" for s in SIDES}, "up": "roof", "down": "dark"}
    els = [
        el((0, 0, 0), (16, 1, 16), "dark", name="tray"),
        el((2, 1, 2), (14, 11, 14), "paper", faces=SIDES, uv=side_uv, name="paper", glow=True),
        el((2.5, 1, 2.5), (13.5, 11, 13.5), "paper", faces=("up", "down"), name="paper ends", glow=True),
        el((1, 11, 1), (15, 12, 15), "dark", faces=SIDES + ("down",), name="head rail"),
        el((0, 12, 0), (16, 13, 16), roof(0), name="roof eaves"),
        el((3, 13, 3), (13, 14, 13), roof(0), name="roof mid"),
        el((5, 14, 5), (11, 15, 11), roof(0), name="roof upper"),
        el((6, 15, 6), (10, 16, 10), {**{s: "brass" for s in SIDES}, "up": "finial", "down": "dark"}, name="roof crown"),
    ]
    els += posts((1, 13), (1, 13), 1, 11, "cherry")
    for x in (0, 14):
        for z in (0, 14):
            els.append(el((x, 13, z), (x + 2, 14, z + 2), roof(0), name="roof tip"))
    return model(TALL_TEXTURES, els)


def tall_item():
    """Inventory model of the 2-block lantern: both halves squeezed into one cube (half size), textures unchanged."""
    out = []
    for dy, half in ((0, tall_lower()), (8, tall_upper())):
        for e in half["elements"]:
            f, t = e["from"], e["to"]
            c = dict(e)
            c["from"] = [4 + f[0] / 2, dy + f[1] / 2, 4 + f[2] / 2]
            c["to"] = [4 + t[0] / 2, dy + t[1] / 2, 4 + t[2] / 2]
            faces = {}
            for face, d in e["faces"].items():
                dd = dict(d)
                dd.setdefault("uv", default_uv(face, f, t))
                faces[face] = dd
            c["faces"] = faces
            out.append(c)
    return model(TALL_TEXTURES, out)


def main():
    write("models/block/paper_lantern.json", andon())
    for t in TONES:
        write("models/block/paper_lantern_hanging_%s.json" % t, chochin(t))
        write("models/block/paper_lantern_wall_%s.json" % t, wall(t))
    write("models/block/paper_lantern_tall_lower.json", tall_lower())
    write("models/block/paper_lantern_tall_upper.json", tall_upper())
    write("models/block/paper_lantern_tall_item.json", tall_item())

    write("blockstates/paper_lantern.json", {"variants": {"": {"model": "t3craft:block/paper_lantern"}}})
    write("blockstates/paper_lantern_hanging.json", {"variants": {
        "tone=%s" % t: {"model": "t3craft:block/paper_lantern_hanging_" + t} for t in TONES}})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for t in TONES:
        for f, y in rot.items():
            v = {"model": "t3craft:block/paper_lantern_wall_" + t}
            if y:
                v["y"] = y
            variants["facing=%s,tone=%s" % (f, t)] = v
    write("blockstates/paper_lantern_wall.json", {"variants": variants})
    write("blockstates/paper_lantern_tall.json", {"variants": {
        "half=lower": {"model": "t3craft:block/paper_lantern_tall_lower"},
        "half=upper": {"model": "t3craft:block/paper_lantern_tall_upper"}}})

    for item, mdl in (("paper_lantern_hanging", "paper_lantern_hanging_paper"), ("paper_lantern_wall", "paper_lantern_wall_paper"),
                      ("paper_lantern_tall", "paper_lantern_tall_item")):
        write("items/%s.json" % item, {"model": {"type": "minecraft:model", "model": "t3craft:block/" + mdl}})
    print("wrote lantern models")


if __name__ == "__main__":
    main()
