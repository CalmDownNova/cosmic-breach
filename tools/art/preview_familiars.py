"""Previews of the three familiars from gen_familiars.py, posed the way the game shows them at rest.

Writes tools/art/previews/familiar_views.png (each familiar from the front, the side and three quarters with its glow
added, and small) and familiar_items.png (the four Star Eggs, the lantern lit by kind, out and dark at 8x, and the
Refract, Kindled and Gravity Drag icons).

Run:  python tools/art/preview_familiars.py
"""
from __future__ import annotations

import numpy as np

import preview_shardling as ps
from bedrock_model import load_anims, load_geo, pose_at, world_quads
from common import ASSETS, PREVIEWS, TEX, load_png, rel, save_png

KINDS = [("emberwisp", "idle", 1.0), ("gravikin", "idle", 1.0), ("prism_moth", "fly", 0.5)]


def render(q, tex, view, add, scale, size=(260, 240), centre_y=6.0):
    import preview_colossus as pc
    old = ps.camera
    ps.camera = pc.camera
    try:
        return ps.render(q, tex, view, scale=scale, size=size, centre=(0.0, centre_y, 0.0), glow=None, ground=False, add=add)
    finally:
        ps.camera = old


def main():
    rows = []
    for name, idle, s in KINDS:
        desc, bones, order = load_geo(ASSETS / "geo" / "entity" / f"{name}.geo.json")
        anims = load_anims(ASSETS / "animations" / "entity" / f"{name}.animation.json")
        tex = load_png(ASSETS / "textures" / "entity" / f"{name}.png")
        glow = load_png(ASSETS / "textures" / "entity" / f"{name}_glowmask.png")
        q = world_quads(desc, bones, order, pose_at(anims[idle], 0.05))
        views = [ps.label(render(q, tex, v, glow, 14 * s, centre_y=6.0 / s * s if s == 1 else 6.4), f"{name} {v}")
                 for v in ("front", "side", "34")]
        small = render(q, tex, "34", glow, 1.6 * s, size=(40, 40), centre_y=6.0 if s == 1 else 6.4)
        views.append(ps.label(np.kron(small, np.ones((3, 3, 1), dtype=np.uint8)).astype(np.uint8), "at 4 blocks x3"))
        rows.append(ps.hstack(views))
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    out = save_png(ps.to_rgba(ps.vstack(rows)), PREVIEWS / "familiar_views.png")
    print("wrote", rel(out))
    print("wrote", rel(items()))


def tile(img, k, pad):
    big = np.kron(img, np.ones((k, k, 1), dtype=np.uint8))
    bg = np.zeros((136, 136, 4), np.uint8)
    bg[..., :3] = (96, 100, 110)
    bg[..., 3] = 255
    a = big[..., 3:4] / 255.0
    h, w = big.shape[:2]
    bg[pad:pad + h, pad:pad + w, :3] = (big[..., :3] * a + bg[pad:pad + h, pad:pad + w, :3] * (1 - a)).astype(np.uint8)
    return bg


def items():
    names = ["star_egg", "star_egg_emberwisp", "star_egg_gravikin", "star_egg_prism_moth", "familiar_lantern_emberwisp",
             "familiar_lantern_gravikin", "familiar_lantern_prism_moth", "familiar_lantern_out", "familiar_lantern_dark"]
    tiles = [tile(load_png(TEX / "item" / f"{n}.png"), 8, 4) for n in names]
    tiles += [tile(load_png(TEX / "mob_effect" / f"{n}.png"), 7, 5) for n in ("refract", "kindled", "gravity_drag")]
    sheet = np.concatenate([np.concatenate(tiles[:6], axis=1), np.concatenate(tiles[6:], axis=1)], axis=0)
    return save_png(sheet, PREVIEWS / "familiar_items.png")


if __name__ == "__main__":
    main()
