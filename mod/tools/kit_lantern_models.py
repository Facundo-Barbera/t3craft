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
    Standing andon: a dark lacquered foot, four 2x2 cherry posts from the floor to the roof round a lit paper
    body (3 px in, where the lamp BER lays its status glow, KitLanternBlock.BODY_INSET), dark head rail, a
    two-step lacquer roof with a 1 px overhang and a brass-vented finial.
    """
    side_uv = {s: [3, 3, 13, 14] for s in SIDES}
    els = [
        el((3, 0, 3), (13, 1, 13), "dark", name="plinth"),
        el((3, 1, 3), (13, 2, 13), "dark", faces=SIDES, name="foot rail"),
        el((3, 2, 3), (13, 13, 13), "paper", faces=SIDES, uv=side_uv, name="paper", glow=True),
        el((3.5, 2, 3.5), (12.5, 13, 12.5), "paper", faces=("up", "down"), name="paper ends", glow=True),
        el((3, 13, 3), (13, 14, 13), "dark", faces=SIDES + ("down",), name="head rail"),
        el((2, 14, 2), (14, 15, 14), {**{s: "dark" for s in SIDES}, "up": "roof", "down": "dark"}, name="roof"),
        el((5, 15, 5), (11, 16, 11), {**{s: "brass" for s in SIDES}, "up": "finial", "down": "dark"}, name="finial"),
    ]
    els += posts((2, 12), (2, 12), 0, 14, "cherry")
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
    Wall lantern facing north (the wall is south, z = 16): an iron back plate and a bracket arm over a square
    lantern (8 px paper shade, cherry corner posts, dark cap and base), hung 7 px off the wall so it reads
    from the street.
    """
    side_uv = {s: [4, 6, 12, 13] for s in SIDES}
    els = [
        el((6, 4, 15), (10, 15, 16), "iron", name="back plate"),
        el((7.5, 13, 8), (8.5, 14, 15), "iron", name="arm"),
        el((7.5, 10, 14), (8.5, 13, 15), "iron", name="brace"),
        el((7.5, 12, 7.5), (8.5, 13, 8.5), "iron", name="hook"),
        el((3, 10, 3), (13, 11, 13), {**{s: "dark" for s in FACES}, "up": "roof"}, name="cap"),
        el((6, 11, 6), (10, 12, 10), {**{s: "brass" for s in SIDES}, "up": "finial", "down": "dark"}, name="vent"),
        el((4, 3, 4), (12, 10, 12), "paper", faces=SIDES, uv=side_uv, name="shade", glow=True),
        el((4, 3, 4), (12, 10, 12), "paper", faces=("up", "down"), name="shade ends", glow=True),
        el((3, 2, 3), (13, 3, 13), "dark", name="base"),
    ]
    els += posts((3.5, 11.5), (3.5, 11.5), 3, 10, "cherry", size=1)
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
    """Lower half of the plaza lantern: a two-step dark stone plinth, a lacquer collar and a 6 px cherry post."""
    return model(TALL_TEXTURES, [
        el((1, 0, 1), (15, 2, 15), "stone", name="plinth foot"),
        el((2, 2, 2), (14, 4, 14), "stone", name="plinth"),
        el((3, 4, 3), (13, 5, 13), "dark", name="collar"),
        el((5, 5, 5), (11, 16, 11), "cherry", faces=SIDES + ("down",), name="post"),
        el((4, 10, 4), (12, 11, 12), "dark", name="post ring"),
    ])


def tall_upper():
    """
    Upper half: the post's last 3 px, a lacquer tray, a big paper box (12 px, cherry corner posts, dark head rail)
    and a two-step hip roof (eaves across the whole cell) with a brass-vented cap. The paper glows.
    """
    side_uv = {s: [2, 3, 14, 12] for s in SIDES}
    els = [
        el((5, 0, 5), (11, 3, 11), "cherry", faces=SIDES + ("down",), name="post"),
        el((1, 3, 1), (15, 4, 15), "dark", name="tray"),
        el((2, 4, 2), (14, 13, 14), "paper", faces=SIDES, uv=side_uv, name="paper", glow=True),
        el((2.5, 4, 2.5), (13.5, 13, 13.5), "paper", faces=("up", "down"), name="paper ends", glow=True),
        el((1, 13, 1), (15, 14, 15), "dark", faces=SIDES + ("down",), name="head rail"),
        el((0, 14, 0), (16, 15, 16), {**{s: "dark" for s in SIDES}, "up": "roof", "down": "dark"}, name="roof eaves"),
        el((3, 15, 3), (13, 16, 13), {**{s: "brass" for s in SIDES}, "up": "finial", "down": "dark"}, name="roof cap"),
    ]
    els += posts((1, 13), (1, 13), 4, 14, "cherry")
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
