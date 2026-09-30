"""Preview sheets for the Starfall Vanguard armor.

Renders the armor on a plain mannequin (the vanilla humanoid's boxes) from the front, the side, the back
and three-quarters, with GeckoLib's own maths (bedrock_model.world_quads, the Shardling previewer's
rasteriser), plus the crest at rest, running and falling (the angles the game's VanguardCrest sets), the
glow alone, the icons at 1x and 8x with their tier trims, and a UV check.

Outputs (tools/art/previews/):
    vanguard_views.png      front, side, back, 3/4: armor on the mannequin
    vanguard_crest.png      side views: the crest at rest, running, falling
    vanguard_glow.png       only the glowmask lit
    vanguard_icons.png      the four icons, 8x and 1x, plain and with each tier's trim
    vanguard_uv.png         the texture at 4x with every island boxed

Run:  python tools/art/preview_vanguard.py
"""
from __future__ import annotations

import copy
import math

import numpy as np
from PIL import Image, ImageDraw

import preview_shardling as ps
from bedrock_model import split_geo, world_quads
from common import PREVIEWS, TEX, load_png, save_png
from gen_vanguard import GEO_PATH, GLOW_PATH, NAME, TEX_PATH, TEX_H, TEX_W

TIER_COLORS = [(0xFF, 0xD2, 0x7A), (0x5F, 0xE3, 0xFF), (0xB9, 0x8C, 0xFF), (0xFF, 0xF1, 0xC9)]
# crest segment angles (Bedrock degrees) the game sets: at rest, running flat out, falling fast
CREST_POSES = {
    "rest": (-32.0, -14.0, -12.0),
    "run": (-32.0 + 28, -14.0 + 12, -12.0 + 11),
    "fall": (-32.0 + 62, -14.0 + 16, -12.0 + 10),
}

_camera = ps.camera


def camera(view):
    if view == "back":
        f = np.array([0, 0, -1.0]); up = np.array([0, 1.0, 0])
        r = np.cross(f, up); r /= np.linalg.norm(r)
        return r, np.cross(r, f), f
    if view == "front34":
        el, az = math.radians(14), math.radians(-35)
        f = np.array([math.sin(az) * math.cos(el), -math.sin(el), math.cos(az) * math.cos(el)])
        up = np.array([0, 1.0, 0])
        f = f / np.linalg.norm(f)
        r = np.cross(f, up); r /= np.linalg.norm(r)
        return r, np.cross(r, f), f
    return _camera(view)


ps.camera = camera

# The mannequin: the vanilla humanoid's boxes, flat colours (skin, shirt, trousers, shoes).
MANNEQUIN = [
    ("head", (-4, 24, -4), (8, 8, 8), (196, 150, 120)),
    ("body", (-4, 12, -2), (8, 12, 4), (70, 150, 160)),
    ("rarm", (-8, 12, -2), (4, 12, 4), (196, 150, 120)),
    ("larm", (4, 12, -2), (4, 12, 4), (196, 150, 120)),
    ("rleg", (-3.9, 0, -2), (4, 12, 4), (60, 70, 150)),
    ("lleg", (-0.1, 0, -2), (4, 12, 4), (60, 70, 150)),
]


def combined(geo_data, tex, glow):
    """Armor geo plus the mannequin, on one texture (armor on top, the mannequin's colours below)."""
    data = copy.deepcopy(geo_data)
    geo = data["minecraft:geometry"][0]
    extra_h = 32
    geo["description"]["texture_height"] = TEX_H + extra_h
    big = np.zeros((TEX_H + extra_h, TEX_W, 4), np.uint8)
    big[:TEX_H] = tex
    gbig = np.zeros_like(big)
    gbig[:TEX_H] = glow
    x = 0
    for name, origin, size, colour in MANNEQUIN:
        w, h, d = (int(s) for s in size)
        u, v = x, TEX_H
        big[v:v + d + h, u:u + 2 * (d + w), :3] = colour
        big[v:v + d + h, u:u + 2 * (d + w), 3] = 255
        # a darker lower edge so the limbs read
        big[v + d + h - 1, u:u + 2 * (d + w), :3] = (np.array(colour) * 0.7).astype(np.uint8)
        geo["bones"].append({"name": "m_" + name, "pivot": [0, 0, 0],
                             "cubes": [{"origin": list(origin), "size": list(size), "uv": [u, v]}]})
        x += 2 * (d + w) + 1
    return data, big, gbig


def crest_pose(angles):
    return {f"crest_tail_{i + 1}": ([a - CREST_POSES["rest"][i], 0, 0], None, None) for i, a in enumerate(angles)}


def views(data, tex, glow, names, pose=None, glow_only=False, scale=7, size=(300, 300)):
    desc, bones, order = split_geo(data)
    quads = world_quads(desc, bones, order, pose or {})
    out = []
    for view in names:
        img = ps.render(quads, tex, view, scale=scale, size=size, centre=(0.0, 17.0, 3.0),
                        glow=glow if glow_only else None)
        out.append(ps.label(img, view))
    return out


def icons_sheet():
    tiles = []
    for piece in ("helm", "chestplate", "greaves", "boots"):
        base = load_png(TEX / "item" / f"{NAME}_{piece}.png")
        trim = load_png(TEX / "item" / f"{NAME}_{piece}_trim.png")
        row = []
        for tier in range(4):
            img = base.copy()
            if tier > 0:
                m = trim[..., 3] > 0
                img[m, :3] = TIER_COLORS[tier]
            big = np.kron(img, np.ones((8, 8, 1), np.uint8))
            bg = np.zeros((128, 128, 3), np.uint8)
            bg[:] = (139, 139, 139)
            a = big[..., 3:4] / 255.0
            bg = (bg * (1 - a) + big[..., :3] * a).astype(np.uint8)
            row.append(bg)
        small = np.zeros((128, 40, 3), np.uint8)
        small[:] = (139, 139, 139)
        a = base[..., 3:4] / 255.0
        small[56:72, 12:28] = (small[56:72, 12:28] * (1 - a) + base[..., :3] * a).astype(np.uint8)
        row.append(small)
        tiles.append(ps.hstack(row))
    return ps.vstack(tiles)


def uv_sheet(data, tex, k=4):
    img = np.kron(tex[..., :3], np.ones((k, k, 1), np.uint8))
    pil = Image.fromarray(img)
    dr = ImageDraw.Draw(pil)
    for b in data["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            w, h, d = (math.floor(s + 1e-6) for s in c["size"])
            u, v = c["uv"]
            dr.rectangle([u * k, v * k, (u + 2 * (d + w)) * k - 1, (v + d + h) * k - 1], outline=(90, 220, 255))
    return np.array(pil)


def main() -> int:
    import json
    data = json.loads(GEO_PATH.read_text(encoding="utf-8"))
    tex = load_png(TEX_PATH)
    glow = load_png(GLOW_PATH)
    both, btex, bglow = combined(data, tex, glow)
    sheet = ps.hstack(views(both, btex, bglow, ["front", "side", "back", "front34"]))
    save_png(ps.to_rgba(sheet), PREVIEWS / "vanguard_views.png")
    crest = ps.hstack([views(both, btex, bglow, ["side"], pose=crest_pose(a))[0] for a in CREST_POSES.values()])
    save_png(ps.to_rgba(crest), PREVIEWS / "vanguard_crest.png")
    save_png(ps.to_rgba(ps.hstack(views(both, btex, bglow, ["front34", "back"], glow_only=True))), PREVIEWS / "vanguard_glow.png")
    save_png(ps.to_rgba(icons_sheet()), PREVIEWS / "vanguard_icons.png")
    save_png(ps.to_rgba(uv_sheet(data, tex)), PREVIEWS / "vanguard_uv.png")
    print("wrote previews/vanguard_*.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
