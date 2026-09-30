"""Preview sheets for the Driftweave and the Choir Regalia armor.

Renders each set on a plain mannequin (the vanilla humanoid's boxes) from the front, the side, the back and
three-quarters with GeckoLib's own maths (bedrock_model.world_quads and the Shardling previewer's rasteriser, as
preview_vanguard.py), at rest and in the poses the game's bone drivers give it while moving (the Driftweave's
scarf and cape streaming, fins flared; the Regalia's halo turned, mantle lifted), the glow alone, and the icons.

Outputs (tools/art/previews/):
    <set>_views.png     front, side, back, 3/4 at rest
    <set>_moving.png    side and back while moving (and, for the Driftweave, falling)
    <set>_glow.png      only the glowmask lit
    <set>_icons.png     the four icons, 8x, plain and with each tier's trim

Run:  python tools/art/preview_sets.py [driftweave|choir_regalia]
"""
from __future__ import annotations

import importlib
import json
import sys

import numpy as np

import preview_shardling as ps
import preview_vanguard as pv
from bedrock_model import split_geo, world_quads
from common import PREVIEWS, TEX, load_png, save_png

SETS = {"driftweave": "gen_driftweave", "choir_regalia": "gen_regalia"}


def combined(gen, data, tex, glow):
    pv.TEX_H = gen.TEX_H
    pv.TEX_W = gen.TEX_W
    return pv.combined(data, tex, glow)


def views(data, tex, glow, names, pose=None, glow_only=False):
    desc, bones, order = split_geo(data)
    quads = world_quads(desc, bones, order, pose or {})
    out = []
    for view in names:
        img = ps.render(quads, tex, view, scale=6, size=(300, 330), centre=(0.0, 18.5, 3.0), glow=glow if glow_only else None)
        out.append(ps.label(img, view))
    return out


def icons(name, pieces):
    tiles = []
    for piece in pieces:
        base = load_png(TEX / "item" / f"{name}_{piece}.png")
        trim = load_png(TEX / "item" / f"{name}_{piece}_trim.png")
        row = []
        for tier in range(4):
            img = base.copy()
            if tier > 0:
                m = trim[..., 3] > 0
                img[m, :3] = pv.TIER_COLORS[tier]
            big = np.kron(img, np.ones((8, 8, 1), np.uint8))
            bg = np.zeros((128, 128, 3), np.uint8)
            bg[:] = (139, 139, 139)
            a = big[..., 3:4] / 255.0
            bg = (bg * (1 - a) + big[..., :3] * a).astype(np.uint8)
            row.append(bg)
        tiles.append(ps.hstack(row))
    return ps.vstack(tiles)


def run(name: str) -> int:
    gen = importlib.import_module(SETS[name])
    data = json.loads(gen.GEO_PATH.read_text(encoding="utf-8"))
    tex = load_png(gen.TEX_PATH)
    glow = load_png(gen.GLOW_PATH)
    both, btex, bglow = combined(gen, data, tex, glow)
    save_png(ps.to_rgba(ps.hstack(views(both, btex, bglow, ["front", "side", "back", "front34"]))), PREVIEWS / f"{name}_views.png")
    poses = gen.PREVIEW_POSES
    row = []
    for label, pose in poses.items():
        for view in ("side", "back"):
            img = views(both, btex, bglow, [view], pose=pose)[0]
            row.append(ps.label(img, f"{label} {view}", xy=(6, 18)))
    save_png(ps.to_rgba(ps.hstack(row)), PREVIEWS / f"{name}_moving.png")
    save_png(ps.to_rgba(ps.hstack(views(both, btex, bglow, ["front34", "back"], glow_only=True))), PREVIEWS / f"{name}_glow.png")
    save_png(ps.to_rgba(icons(name, gen.PIECES.keys())), PREVIEWS / f"{name}_icons.png")
    print(f"wrote previews/{name}_*.png")
    return 0


def main() -> int:
    names = sys.argv[1:] or list(SETS)
    status = 0
    for name in names:
        status |= run(name)
    return status


if __name__ == "__main__":
    raise SystemExit(main())
