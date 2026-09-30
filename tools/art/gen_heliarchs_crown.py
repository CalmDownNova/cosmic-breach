"""The Heliarch's Crown (GDD 7.3, "Trophies"): the regent's crown set on a pedestal; the game draws a small sun orbiting
it (client.relic.CrownRenderer) and the block gives light 12.

Writes textures/block/heliarchs_crown_{plinth,plinth_top,gold,core}.png (16x16), the block model (a Starfall stone
plinth: foot, column and top; on it an octagonal crown of eight gold plates, a tall tine on the four facing the sides
and a short one on the diagonals, a sun gem at its heart), the block state, the item model and the loot table (it drops
itself). The plinth's stone is W1's polished Starfall Stone with a gold inlay. Runs after gen_blocks_reach.py.

Run:  python tools/art/gen_heliarchs_crown.py
"""
from __future__ import annotations

import json
import math

import numpy as np

from common import ASSETS, PREVIEWS, REPO, TEX, blank, load_png, over, rel, rgb, save_png, upscale

GOLD = [rgb("#ffe58a"), rgb("#ffc62e"), rgb("#e39a16"), rgb("#b36d10"), rgb("#7a430b")]
INLAY = rgb("#f1c25c")
INLAY_DARK = rgb("#a4712c")
CORE = [rgb("#ffffff"), rgb("#fff6d0"), rgb("#ffdc7a"), rgb("#f0a83c")]


def plinth(top: bool) -> np.ndarray:
    img = load_png(TEX / "block" / "polished_starfall_stone.png").copy()
    if top:
        ys, xs = np.mgrid[0:16, 0:16]
        r = np.sqrt((xs - 7.5) ** 2 + (ys - 7.5) ** 2)
        ring = (r > 5.2) & (r < 6.3)
        img[ring, :3] = INLAY
        img[(r > 6.3) & (r < 6.9), :3] = INLAY_DARK
        rays = (r > 1.5) & (r < 4.4) & ((np.abs(xs - 7.5) < 0.6) | (np.abs(ys - 7.5) < 0.6))
        img[rays, :3] = INLAY
    else:
        img[0, :, :3] = INLAY_DARK
        img[1, :, :3] = INLAY
        img[14, :, :3] = INLAY
        img[15, :, :3] = INLAY_DARK
        for x in range(2, 14, 4):  # a row of small suns round the plinth
            img[7, x:x + 3, :3] = INLAY
            img[6:9, x + 1, :3] = INLAY
    img[..., 3] = 255
    return img


def gold() -> np.ndarray:
    img = blank(16, 16)
    ys, xs = np.mgrid[0:16, 0:16]
    shade = np.clip((ys * 0.22 + (xs % 4 == 3) * 0.9 + ((xs + ys) % 7 == 0) * 0.6), 0, 4).astype(int)
    for i, c in enumerate(GOLD):
        img[shade == i, :3] = c
    img[0, :, :3] = GOLD[0]
    img[15, :, :3] = GOLD[4]
    img[..., 3] = 255
    return img


def core() -> np.ndarray:
    img = blank(16, 16)
    ys, xs = np.mgrid[0:16, 0:16]
    r = np.sqrt((xs - 7.5) ** 2 + (ys - 7.5) ** 2)
    idx = np.clip((r / 2.6).astype(int), 0, 3)
    for i, c in enumerate(CORE):
        img[idx == i, :3] = c
    img[..., 3] = 255
    return img


def element(frm, to, textures, rotation=None, cull=None):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        tex = textures.get(face, textures.get("side"))
        if tex is None:
            continue
        f = {"texture": tex}
        if cull and face in cull:
            f["cullface"] = cull[face]
        faces[face] = f
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def model():
    stone = {"side": "#plinth", "up": "#plinth_top", "down": "#plinth_top"}
    g = {"side": "#gold", "up": "#gold", "down": "#gold"}
    els = [
        element((2, 0, 2), (14, 2, 14), stone, cull=None),
        element((4, 2, 4), (12, 8, 12), stone),
        element((3, 8, 3), (13, 10, 13), stone),
        element((7, 10, 7), (9, 12.5, 9), {"side": "#core", "up": "#core", "down": "#core"}),
    ]
    half = 1.3   # half a plate's width
    thick = 0.8
    rad = 3.0    # the octagon's apothem
    gem = {"side": "#core", "up": "#core", "down": "#core"}
    for k in range(8):
        a = math.radians(45 * k)
        cx, cz = 8 + rad * math.sin(a), 8 - rad * math.cos(a)
        tall = k % 2 == 0
        # a plate of the band, then a tine rising from it: the tall ones (facing the sides) taper to a point with a
        # sun gem at the tip, the short ones (on the diagonals) are a single narrow step
        top = 15.4 if tall else 13.4
        if k % 2 == 0:
            along_x = k in (0, 4)
            def box(w, y0, y1):
                if along_x:
                    return (cx - w, y0, cz - thick / 2), (cx + w, y1, cz + thick / 2)
                return (cx - thick / 2, y0, cz - w), (cx + thick / 2, y1, cz + w)
            rot = None
        else:
            def box(w, y0, y1):
                return (cx - w, y0, cz - thick / 2), (cx + w, y1, cz + thick / 2)
            rot = {"origin": [round(cx, 3), 10, round(cz, 3)], "axis": "y", "angle": -45 if k in (1, 5) else 45}
        parts = [(half, 10.0, 12.0), (0.75, 12.0, top - 1.2), (0.4, top - 1.2, top)]
        for w, y0, y1 in parts:
            frm, to = box(w, y0, y1)
            els.append(element(_r(frm), _r(to), g, rotation=rot))
        if tall:
            frm, to = box(0.55, top, top + 1.0)
            els.append(element(_r(frm), _r(to), gem, rotation=rot))
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": "cosmicbreach:block/heliarchs_crown_gold",
                     "plinth": "cosmicbreach:block/heliarchs_crown_plinth",
                     "plinth_top": "cosmicbreach:block/heliarchs_crown_plinth_top",
                     "gold": "cosmicbreach:block/heliarchs_crown_gold",
                     "core": "cosmicbreach:block/heliarchs_crown_core"},
        "elements": els,
    }


def _r(p):
    return [round(v, 3) for v in p]


def _write(path, doc):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(doc, indent=2) + "\n")
    return path


def main():
    block = TEX / "block"
    images = {"plinth": plinth(False), "plinth_top": plinth(True), "gold": gold(), "core": core()}
    paths = [save_png(im, block / f"heliarchs_crown_{name}.png") for name, im in images.items()]
    paths.append(_write(ASSETS / "models" / "block" / "heliarchs_crown.json", model()))
    paths.append(_write(ASSETS / "blockstates" / "heliarchs_crown.json",
                        {"variants": {"": {"model": "cosmicbreach:block/heliarchs_crown"}}}))
    paths.append(_write(ASSETS / "models" / "item" / "heliarchs_crown.json", {"parent": "cosmicbreach:block/heliarchs_crown"}))
    loot = REPO / "src" / "main" / "resources" / "data" / "cosmicbreach" / "loot_table" / "blocks" / "heliarchs_crown.json"
    paths.append(_write(loot, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "cosmicbreach:heliarchs_crown"}]}],
        "random_sequence": "cosmicbreach:blocks/heliarchs_crown"}))
    out = np.zeros((16 * 6 + 20, 16 * 6 * 4 + 50, 4), dtype=np.uint8)
    out[..., :3] = 110
    out[..., 3] = 255
    for i, im in enumerate(images.values()):
        over(out, upscale(im, 6), 10 + i * (16 * 6 + 10), 10)
    save_png(out, PREVIEWS / "heliarchs_crown_textures.png")
    for p in paths:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
