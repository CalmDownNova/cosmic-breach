"""The combat familiars (G10, GDD 8.2) for GeckoLib 4, and their items and block.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/{emberwisp,gravikin,prism_moth}.geo.json       Bedrock geometry 1.12.0, box UV
    animations/entity/{emberwisp,gravikin,prism_moth}.animation.json
    textures/entity/{emberwisp,gravikin,prism_moth}.png and _glowmask.png (the game adds the glowmask as light)
    textures/item/star_egg(_emberwisp|_gravikin|_prism_moth).png and their models (the egg's kind picks its look)
    textures/item/familiar_lantern(_emberwisp|_gravikin|_prism_moth|_out|_dark).png and their models (kind and state)
    textures/block/brazier_of_solenne_{stone,gold,coals,embers}.png, models/block/brazier_of_solenne(_lit).json,
    blockstates/brazier_of_solenne.json, models/item/brazier_of_solenne.json
    textures/mob_effect/{refract,kindled,gravity_drag}.png

The looks (each has to read at a few dozen pixels, with nothing that reads as a face or letters):
    Emberwisp   a tiny sun: a white-hot round core in a spiked corona of gold and orange rays on three planes,
                two crowns the game turns against each other; nearly all of it glows.
    Gravikin    a pebble golem: a round grey-blue boulder on stubby legs, two arms of stacked pebbles, a flat
                pebble cap, a jagged violet crack down its chest (vertical, so it never reads as a mouth) and
                three pebbles orbiting its middle (the Drift's gravity).
    Prism Moth  a glass moth: an ivory body with a banded abdomen, feathery antennae, four see-through wings of
                pale glass with iridescent rainbow edges and fine veins; built at twice the size, drawn at half,
                so the wings get the texels.

Run:  python tools/art/gen_familiars.py
"""
from __future__ import annotations

import colorsys
import math

import numpy as np

from common import ASSETS, TEX, rel, rgb, save_png
from gen_colossus import Bone, Cube, face_rects, geo_json, kf, loop_times, pack_uvs, pretty_json, texel_point, write_json, write_text


def cbox(cx, cy, cz, w, h, d, paint, **kw):
    """A cube by its middle."""
    return Cube((cx - w / 2, cy - h / 2, cz - d / 2), (w, h, d), paint, **kw)


def hash3(x, y, z, seed=0):
    h = (int(math.floor(x * 7.31)) * 73856093) ^ (int(math.floor(y * 7.31)) * 19349663) ^ (int(math.floor(z * 7.31)) * 83492791) ^ (seed * 2654435761)
    h &= 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def lerp_rgb(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def paint_model(bones, tex_w, tex_h, texel):
    """Every face of every cube, texel by texel: texel(cube, face, col, row, w, h, point) -> (rgba, glow rgb or None)."""
    tex = np.zeros((tex_h, tex_w, 4), np.uint8)
    glow = np.zeros_like(tex)
    for b in bones:
        for c in b.cubes:
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                for r in range(rh):
                    for col in range(rw):
                        p = texel_point(c, face, col, r)
                        colour, g = texel(c, face, col, r, rw, rh, p)
                        tex[rv + r, ru + col] = colour if len(colour) == 4 else (*colour, 255)
                        if g is not None:
                            glow[rv + r, ru + col, :3] = g
                            glow[rv + r, ru + col, 3] = 255
    return tex, glow


# ====================================================================== the Emberwisp
WISP_Y = 3.6          # the core's middle over the feet (the box is 0.45 blocks, 7.2 units)
SUN_CORE = rgb("#fffbe6")
SUN_GOLD = rgb("#ffcf5a")
SUN_ORANGE = rgb("#ff9a2e")
SUN_RED = rgb("#f2601c")


def wisp_spike(axis, angle, r0=2.1, length=2.0):
    """A ray from the core outward, built along +Z (or +Y for the XY plane), turned `angle` degrees about `axis`."""
    pivot = (0, WISP_Y, 0)
    if axis == "z":   # the XY plane: built along +Y
        base = Cube((-0.6, WISP_Y + r0, -0.6), (1.2, length, 1.2), "ray", rotation=(0, 0, angle), pivot=pivot)
        tip = Cube((-0.5, WISP_Y + r0 + length, -0.5), (1, 1.5, 1), "tip", rotation=(0, 0, angle), pivot=pivot, inflate=-0.22)
        return [base, tip]
    rot = (angle, 0, 0) if axis == "x" else (0, angle, 0)
    base = Cube((-0.6, WISP_Y - 0.6, r0), (1.2, 1.2, length), "ray", rotation=rot, pivot=pivot)
    tip = Cube((-0.5, WISP_Y - 0.5, r0 + length), (1, 1, 1.5), "tip", rotation=rot, pivot=pivot, inflate=-0.22)
    return [base, tip]


def build_wisp():
    bones = [Bone("root", None, (0, 0, 0))]
    bones.append(Bone("core", "root", (0, WISP_Y, 0), None, [
        cbox(0, WISP_Y, 0, 4, 4, 4, "core"),
        cbox(0, WISP_Y, 0, 5, 3, 3, "core"),
        cbox(0, WISP_Y, 0, 3, 5, 3, "core"),
        cbox(0, WISP_Y, 0, 3, 3, 5, "core"),
    ]))
    outer = []
    for k in range(8):                       # the XZ plane: a crown of eight, long and short in turn
        outer += wisp_spike("y", k * 45.0, length=2.2 if k % 2 == 0 else 1.4)
    bones.append(Bone("rays_outer", "root", (0, WISP_Y, 0), None, outer))
    inner = []
    for a in (45, 90, 135, 225, 270, 315):   # the YZ plane
        inner += wisp_spike("x", float(a), length=1.8 if a % 90 == 0 else 1.3)
    for a in (45, 135, 225, 315):            # the XY plane
        inner += wisp_spike("z", float(a), length=1.3)
    bones.append(Bone("rays_inner", "root", (0, WISP_Y, 0), None, inner))
    return bones


def wisp_texel(c, face, col, row, w, h, p):
    d = math.dist(p, (0, WISP_Y, 0))
    n = hash3(*p, seed=3)
    if c.paint == "core":
        t = max(0.0, (d - 1.2) / 1.5)
        colour = lerp_rgb(SUN_CORE, SUN_GOLD, t * t + 0.15 * n)
        g = lerp_rgb((255, 250, 225), (255, 205, 110), t)
        return colour, g
    if c.paint == "ray":
        t = (d - 2.0) / 2.4
        colour = lerp_rgb(SUN_GOLD, SUN_ORANGE, t + 0.2 * n)
        g = lerp_rgb((255, 190, 80), (230, 120, 40), t)
        return colour, g
    # the tips burn red-orange
    return lerp_rgb(SUN_ORANGE, SUN_RED, 0.5 + 0.5 * n), (225, 95, 30)


def wisp_anims():
    out = {}
    L = 1.6
    ts = loop_times(L, 8)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * t / L + ph)
    out["idle"] = {"loop": True, "animation_length": L, "bones": {
        "core": {"scale": kf(ts, lambda t: (1 + 0.07 * s(t),) * 3)},
        "rays_outer": {"scale": kf(ts, lambda t: (1 + 0.06 * s(t, 2.1),) * 3)},
        "rays_inner": {"scale": kf(ts, lambda t: (1 + 0.08 * s(t, 4.0),) * 3)},
    }}
    out["strike"] = {"loop": False, "animation_length": 0.35, "bones": {
        "root": {"position": kf([0, 0.1, 0.35], lambda t: (0, 0, -3.0 if t == 0.1 else 0.0))},
        "core": {"scale": kf([0, 0.1, 0.35], lambda t: (1.35 if t == 0.1 else 1.0,) * 3)},
        "rays_outer": {"scale": kf([0, 0.1, 0.35], lambda t: (1.4 if t == 0.1 else 1.0,) * 3)},
    }}
    out["flare"] = {"loop": False, "animation_length": 0.5, "bones": {
        "core": {"scale": kf([0, 0.12, 0.5], lambda t: (1.45 if t == 0.12 else 1.0,) * 3)},
        "rays_outer": {"scale": kf([0, 0.12, 0.5], lambda t: (1.7 if t == 0.12 else 1.0,) * 3)},
        "rays_inner": {"scale": kf([0, 0.15, 0.5], lambda t: (1.6 if t == 0.15 else 1.0,) * 3)},
    }}
    return {"format_version": "1.8.0", "animations": out}


# ====================================================================== the Gravikin
STONE = [rgb("#aebbd6"), rgb("#8d9dc0"), rgb("#7282a8"), rgb("#5b6a91"), rgb("#475478"), rgb("#374262")]
CRACK = rgb("#a9b4ff")
CRACK_GLOW = (70, 82, 215)
GK_BODY_Y = 6.5


def build_gravikin():
    bones = [Bone("root", None, (0, 0, 0))]
    for side, name in ((1, "left"), (-1, "right")):
        bones.append(Bone(f"leg_{name}", "root", (side * 2.2, 3.0, 0.2), None, [
            cbox(side * 2.2, 1.5, 0.2, 3, 3, 3, "stone_dark"),
        ]))
    body = [
        cbox(0, GK_BODY_Y, 0, 8, 7, 7, "stone"),
        cbox(0, GK_BODY_Y, 0, 6, 8, 5, "stone"),
        cbox(0, GK_BODY_Y, 0, 9, 5, 6, "stone"),
        cbox(0, GK_BODY_Y - 0.3, 0, 6, 5, 8, "stone"),
        # the flat pebble cap, tipped a little
        cbox(0.8, 10.9, 0.4, 5, 2, 4, "stone_light", rotation=(0, 0, -10), pivot=(0.8, 10.4, 0.4)),
    ]
    bones.append(Bone("body", "root", (0, 3.0, 0), None, body))
    for side, name in ((1, "left"), (-1, "right")):
        bones.append(Bone(f"arm_{name}", "body", (side * 4.4, 8.6, 0), None, [
            cbox(side * 5.3, 7.4, -0.1, 2.6, 3.0, 2.6, "stone"),
            cbox(side * 5.6, 4.9, -0.5, 3.2, 3.0, 3.2, "stone_dark"),
        ]))
    pebbles = []
    for k, (a, y) in enumerate(((20, 7.8), (140, 5.6), (260, 9.0))):
        r = 6.6
        x, z = r * math.cos(math.radians(a)), r * math.sin(math.radians(a))
        pebbles.append(cbox(x, y, z, 1.6, 1.6, 1.6, "pebble", inflate=0.05 * k))
    bones.append(Bone("orbit", "body", (0, GK_BODY_Y, 0), None, pebbles))
    return bones


def on_crack(x, y):
    """The jagged crack down the Gravikin's chest: one texel wide, three offset runs joined, top to bottom."""
    runs = ((7.2, 8.6, 1.0), (5.6, 7.4, 0.0), (4.0, 5.8, 1.0))
    return any(lo <= y <= hi and abs(x - cx - 0.5) < 0.51 for lo, hi, cx in runs)


def gravikin_texel(c, face, col, row, w, h, p):
    n = hash3(*p, seed=11)
    if face == "front" and c.paint == "stone" and c.size[2] >= 8 and on_crack(p[0], p[1]):
        return CRACK, CRACK_GLOW
    n2 = hash3(p[0] * 0.5, p[1] * 0.5, p[2] * 0.5, seed=5)
    light = {"top": 0, "front": 1, "xpos": 1, "xneg": 2, "back": 2, "bottom": 3}[face]
    if c.paint == "crack":
        return CRACK, CRACK_GLOW
    if c.paint == "pebble":
        edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
        return STONE[1 + light // 2], (40, 48, 130) if edge else (18, 22, 60)
    base = {"stone": 1, "stone_light": 0, "stone_dark": 1}[c.paint]
    idx = base + light + (1 if n2 > 0.62 else 0) - (1 if n2 < 0.18 else 0)
    colour = STONE[max(0, min(5, idx))]
    if n > 0.985 and c.paint == "stone" and face in ("top", "xpos", "xneg"):
        # a violet mineral fleck
        return rgb("#9aa6ff"), (80, 90, 190)
    if n < 0.08:
        colour = STONE[min(5, max(0, idx + 1))]
    return colour, None


def gravikin_anims():
    out = {}
    L = 2.4
    ts = loop_times(L, 12)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * t / L + ph)
    out["idle"] = {"loop": True, "animation_length": L, "bones": {
        "body": {"scale": kf(ts, lambda t: (1.0, 1 + 0.03 * s(t), 1.0)), "position": kf(ts, lambda t: (0, 0.15 * s(t), 0))},
        "arm_left": {"rotation": kf(ts, lambda t: (6 * s(t), 0, -4 - 3 * s(t, 1.0)))},
        "arm_right": {"rotation": kf(ts, lambda t: (-6 * s(t), 0, 4 + 3 * s(t, 1.0)))},
    }}
    out["hop"] = {"loop": False, "animation_length": 0.6, "bones": {
        "body": {"position": {"0.0": [0, 0, 0], "0.08": [0, -1.2, 0], "0.2": [0, 0.6, 0], "0.5": [0, 0.2, 0], "0.6": [0, 0, 0]},
                 "scale": {"0.0": [1, 1, 1], "0.08": [1.08, 0.88, 1.08], "0.2": [0.95, 1.08, 0.95], "0.5": [1, 1, 1],
                           "0.55": [1.1, 0.86, 1.1], "0.6": [1, 1, 1]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.08": [35, 0, -10], "0.2": [-40, 0, -70], "0.45": [-20, 0, -35], "0.6": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.08": [35, 0, 10], "0.2": [-40, 0, 70], "0.45": [-20, 0, 35], "0.6": [0, 0, 0]}},
        "leg_left": {"rotation": {"0.0": [0, 0, 0], "0.2": [-25, 0, 0], "0.45": [15, 0, 0], "0.6": [0, 0, 0]}},
        "leg_right": {"rotation": {"0.0": [0, 0, 0], "0.2": [-25, 0, 0], "0.45": [15, 0, 0], "0.6": [0, 0, 0]}},
    }}
    out["taunt"] = {"loop": False, "animation_length": 1.1, "bones": {
        "body": {"rotation": {"0.0": [0, 0, 0], "0.3": [-12, 0, 0], "0.55": [-12, 0, 0], "0.65": [22, 0, 0], "1.1": [0, 0, 0]},
                 "position": {"0.0": [0, 0, 0], "0.3": [0, 0.8, 0], "0.55": [0, 1.0, 0], "0.65": [0, -0.8, 0], "1.1": [0, 0, 0]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.3": [0, 0, -155], "0.55": [0, 0, -160], "0.65": [-80, 0, -20], "1.1": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.3": [0, 0, 155], "0.55": [0, 0, 160], "0.65": [-80, 0, 20], "1.1": [0, 0, 0]}},
    }}
    out["strike"] = {"loop": False, "animation_length": 0.45, "bones": {
        "body": {"rotation": {"0.0": [0, 0, 0], "0.12": [-8, -15, 0], "0.22": [18, 10, 0], "0.45": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.12": [-130, 0, 10], "0.22": [-30, 0, 0], "0.45": [0, 0, 0]}},
    }}
    return {"format_version": "1.8.0", "animations": out}


# ====================================================================== the Prism Moth (built at x2, drawn at x0.5)
MOTH_Y = 6.4
IVORY = [rgb("#fbf7ee"), rgb("#ece6d8"), rgb("#d9d1c0"), rgb("#bfb6a4")]
LAVENDER = rgb("#d8d2f0")
GLASS = (222, 242, 255)


def build_moth():
    bones = [Bone("root", None, (0, 0, 0))]
    bones.append(Bone("body", "root", (0, MOTH_Y, 0), None, [
        cbox(0, MOTH_Y, 0, 4, 4, 5, "fur"),                                   # the thorax, a fluffy ball
        cbox(0, MOTH_Y + 0.3, -0.6, 5, 3, 3, "fur"),                           # its ruff
        cbox(0, MOTH_Y - 0.1, 3.8, 2.8, 2.6, 3.4, "abdomen"),                 # the abdomen, banded, in two
        cbox(0, MOTH_Y - 0.4, 6.8, 2, 2, 3, "abdomen", inflate=-0.1),
    ]))
    bones.append(Bone("head", "body", (0, MOTH_Y + 0.2, -2.0), None, [
        cbox(0, MOTH_Y + 0.3, -3.1, 2.4, 2.4, 2.2, "fur"),
    ]))
    for side, name in ((1, "left"), (-1, "right")):
        bones.append(Bone(f"antenna_{name}", "head", (side * 0.6, MOTH_Y + 1.3, -3.6), None, [
            Cube((side * 0.6 - 0.5, MOTH_Y + 1.0, -8.1), (1, 1, 4.5), "stalk", rotation=(-52, side * 38, 0),
                 pivot=(side * 0.6, MOTH_Y + 1.3, -3.6), inflate=-0.35),
            Cube((side * 0.6 - 1.0, MOTH_Y + 1.0, -7.6), (2, 1, 3.6), "feather", rotation=(-52, side * 38, 0),
                 pivot=(side * 0.6, MOTH_Y + 1.3, -3.6), inflate=-0.45),
        ]))
    for side, name in ((1, "left"), (-1, "right")):
        x0 = 1.4 if side > 0 else -1.4 - 14
        hx0 = 1.2 if side > 0 else -1.2 - 10
        bones.append(Bone(f"wing_{name}", "body", (side * 1.4, MOTH_Y + 0.6, -0.6), None, [
            Cube((x0, MOTH_Y + 0.1, -6.6), (14, 1, 8), "forewing", inflate=-0.45),
        ]))
        bones.append(Bone(f"hindwing_{name}", "body", (side * 1.2, MOTH_Y + 0.3, 1.2), None, [
            Cube((hx0, MOTH_Y - 0.2, 0.6), (10, 1, 7), "hindwing", inflate=-0.45),
        ]))
    return bones


def rainbow(t):
    r, g, b = colorsys.hsv_to_rgb(t % 1.0, 0.55, 1.0)
    return int(r * 255), int(g * 255), int(b * 255)


def in_forewing(x, z):
    """The forewing's outline over its plane: a broad rounded triangle from the root (x 0) to the tip (x 14)."""
    # leading edge from (0, -1.6) to (14, -6.4); outer edge from the tip back to (9.5, 1.4); trailing edge to the root
    lead = -1.6 + (-6.4 + 1.6) * (x / 14.0)
    if z < lead - 0.2:
        return False, 0.0
    if x > 9.5:
        # the outer edge
        t = (x - 9.5) / 4.5
        outer = 1.4 + (-6.4 - 1.4) * t
        if z > outer + 0.35:
            return False, 0.0
        edge = min(z - lead, outer - z)
    else:
        edge = min(z - lead, 1.4 - z)
    if z > 1.4 + 0.2:
        return False, 0.0
    return True, edge


def in_hindwing(x, z):
    """The hindwing: a rounded fan from the root, widest at its middle."""
    rx, rz = 10.0, 6.6
    u = x / rx
    v = z / rz
    if u < 0 or v < 0 or u * u + (v - 0.45) ** 2 / 0.35 > 1.0:
        return False, 0.0
    return True, 1.0 - (u * u + (v - 0.45) ** 2 / 0.35)


def moth_texel(c, face, col, row, w, h, p):
    x, y, z = p
    n = hash3(*p, seed=21)
    light = {"top": 0, "front": 1, "xpos": 1, "xneg": 1, "back": 2, "bottom": 2}[face]
    if c.paint in ("forewing", "hindwing"):
        if face not in ("top", "bottom"):
            return (0, 0, 0, 0), None
        ax = abs(x) - (1.4 if c.paint == "forewing" else 1.2)
        az = z - (-0.6 if c.paint == "forewing" else 0.6)
        if c.paint == "forewing":
            inside, edge = in_forewing(ax, z + 0.6 - 0.6)
        else:
            inside, edge = in_hindwing(ax, z - 0.6)
        if not inside:
            return (0, 0, 0, 0), None
        hue = (ax / 14.0) * 0.8 + (0.55 if c.paint == "forewing" else 0.15)
        if (c.paint == "forewing" and edge < 1.35) or (c.paint == "hindwing" and edge < 0.26):
            col_ = rainbow(hue)
            return (*col_, 235), tuple(int(v * 0.85) for v in col_)
        # the veins: lines fanning from the root
        ang = math.atan2(az, max(0.3, ax))
        vein = abs(math.sin(ang * 5.0)) < 0.16 and ax > 1.0
        if vein:
            return (175, 205, 230, 190), (30, 40, 55)
        tint = lerp_rgb(GLASS, rainbow(hue + 0.3), 0.28 + 0.14 * n)
        return (*tint, 160), (34, 42, 56)
    if c.paint in ("stalk", "feather"):
        return IVORY[1 + (1 if c.paint == "feather" else 0)], (40, 38, 30)
    if c.paint == "abdomen":
        band = int(math.floor(z * 1.1)) % 2 == 0
        colour = IVORY[min(3, light + (0 if band else 1))] if band else lerp_rgb(IVORY[light], LAVENDER, 0.6)
        return colour, (18, 16, 24)
    # the fur of the thorax and head: soft ivory tufts with a lilac sheen, lit a little from within
    colour = IVORY[min(3, light + (1 if n > 0.72 else 0))]
    if n < 0.18:
        colour = lerp_rgb(colour, LAVENDER, 0.7)
    return colour, (30, 28, 34)


def moth_anims():
    out = {}
    L = 0.3
    flap = lambda t: 38 * math.sin(2 * math.pi * t / L)
    ts = loop_times(L, 6)
    L2 = 2.4
    ts2 = loop_times(L2, 8)
    out["fly"] = {"loop": True, "animation_length": L, "bones": {
        "wing_left": {"rotation": kf(ts, lambda t: (0, 0, -14 - flap(t)))},
        "wing_right": {"rotation": kf(ts, lambda t: (0, 0, 14 + flap(t)))},
        "hindwing_left": {"rotation": kf(ts, lambda t: (0, 0, -8 - 0.8 * flap(t - 0.03)))},
        "hindwing_right": {"rotation": kf(ts, lambda t: (0, 0, 8 + 0.8 * flap(t - 0.03)))},
        "body": {"position": kf(ts, lambda t: (0, -0.4 * math.sin(2 * math.pi * t / L), 0))},
    }}
    out["glint"] = {"loop": False, "animation_length": 0.5, "bones": {
        "wing_left": {"rotation": {"0.0": [0, 0, -14], "0.1": [0, -12, 6], "0.35": [0, -12, 6], "0.5": [0, 0, -14]}},
        "wing_right": {"rotation": {"0.0": [0, 0, 14], "0.1": [0, 12, -6], "0.35": [0, 12, -6], "0.5": [0, 0, 14]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.1": [-14, 0, 0], "0.35": [-14, 0, 0], "0.5": [0, 0, 0]}},
    }}
    out["strike"] = {"loop": False, "animation_length": 0.35, "bones": {
        "root": {"position": {"0.0": [0, 0, 0], "0.1": [0, 0, -6], "0.35": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 0, 0], "0.1": [22, 0, 0], "0.35": [0, 0, 0]}},
    }}
    return {"format_version": "1.8.0", "animations": out}


# ====================================================================== items (16 x 16)
def egg_icon(kind):
    img = np.zeros((16, 16, 4), np.uint8)
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    cx, cy = 8.0, 8.8
    yn = (ys - cy) / 6.6
    rx = 4.9 * (1.0 + 0.16 * yn)          # narrower at the top, rounder below
    inside = ((xs - cx) / rx) ** 2 + yn ** 2 <= 1.0
    shade = np.clip(0.5 + 0.35 * ((cx - xs) / 5.0 + (cy - ys) / 7.0), 0, 1)
    palettes = {
        None: (rgb("#fffaf0"), rgb("#e9dcc0"), rgb("#b89f78"), rgb("#ffd66b")),
        "emberwisp": (rgb("#ffe7b0"), rgb("#f4b25a"), rgb("#b8662a"), rgb("#ff5a1f")),
        "gravikin": (rgb("#d7deea"), rgb("#9aa6ba"), rgb("#5f6a80"), rgb("#9d8cff")),
        "prism_moth": (rgb("#fbfdff"), rgb("#dbe6f2"), rgb("#9fb0c4"), None),
    }
    hi, mid, lo, speck = palettes[kind]
    for y in range(16):
        for x in range(16):
            if not inside[y, x]:
                continue
            s = shade[y, x]
            col = lerp_rgb(lo, mid, s * 1.6) if s < 0.62 else lerp_rgb(mid, hi, (s - 0.62) / 0.38)
            img[y, x, :3] = col
            img[y, x, 3] = 255
    # outline: the darkest of its colours, on the shadow side a shade darker still
    edge = inside & ~(np.roll(inside, 1, 0) & np.roll(inside, -1, 0) & np.roll(inside, 1, 1) & np.roll(inside, -1, 1))
    for y in range(16):
        for x in range(16):
            if edge[y, x]:
                img[y, x, :3] = lerp_rgb(lo, (30, 26, 34), 0.35 if x + y > 16 else 0.1)
    # specks: small stars (four-point) and dots, fixed places
    spots = [(6, 6), (10, 9), (7, 11), (9, 5), (5, 9), (10, 12)]
    for i, (x, y) in enumerate(spots):
        if not inside[y, x] or edge[y, x]:
            continue
        c = speck if speck is not None else rainbow(i / len(spots))
        img[y, x, :3] = c
    # a highlight
    img[5, 6, :3] = hi
    img[4, 7, :3] = lerp_rgb(hi, (255, 255, 255), 0.6)
    return img


LANTERN_FRAME = [rgb("#f3dc8c"), rgb("#d6ab4f"), rgb("#9c7430"), rgb("#5e4320")]
LANTERN_COLD = [rgb("#a6a6ae"), rgb("#7c7c86"), rgb("#55555e"), rgb("#34343a")]


def lantern_icon(state):
    """state: 'emberwisp', 'gravikin', 'prism_moth' (lit with it inside), 'out' (empty) or 'dark'."""
    img = np.zeros((16, 16, 4), np.uint8)
    frame = LANTERN_COLD if state == "dark" else LANTERN_FRAME

    def px(x, y, c, a=255):
        img[y, x, :3] = c
        img[y, x, 3] = a

    # the ring on top
    for x in (6, 7, 8, 9):
        px(x, 0, frame[1])
    px(6, 1, frame[2])
    px(9, 1, frame[2])
    # the cap
    for x in range(5, 11):
        px(x, 2, frame[0] if x < 8 else frame[1])
    for x in range(4, 12):
        px(x, 3, frame[1] if x < 9 else frame[2])
    # the glass body, rows 4 to 12, columns 5 to 10 inside a frame at 4 and 11
    glass = {"out": rgb("#dfeef2"), "dark": rgb("#1d1e26")}
    for y in range(4, 13):
        px(4, y, frame[1])
        px(11, y, frame[2])
        for x in range(5, 11):
            if state in glass:
                a = 150 if state == "out" else 255
                c = glass[state]
                if state == "out" and (x == 5 or y == 4):
                    c = rgb("#ffffff")
                px(x, y, c, a)
            else:
                px(x, y, rgb("#fff4d8"), 255)
    # what is inside
    if state == "emberwisp":
        for y in range(4, 13):
            for x in range(5, 11):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8.5)
                px(x, y, lerp_rgb(rgb("#fffbe6"), rgb("#ff8a2a"), d / 3.6))
        for (x, y) in ((8, 5), (7, 12), (5, 8), (10, 9), (6, 6), (9, 11)):
            px(x, y, rgb("#ff5a1f"))
        px(7, 8, rgb("#ffffff"))
        px(8, 8, rgb("#ffffff"))
    elif state == "gravikin":
        for y in range(4, 13):
            for x in range(5, 11):
                px(x, y, lerp_rgb(rgb("#dfe3ff"), rgb("#8c9cff"), math.hypot(x + 0.5 - 8, y + 0.5 - 8.5) / 3.8))
        # a round pebble, lit from the top left, a thread of violet light down its middle
        pebble = [(7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (9, 8), (6, 9), (7, 9), (8, 9), (9, 9),
                  (7, 10), (8, 10)]
        for (x, y) in pebble:
            px(x, y, STONE[1] if x + y < 14 else STONE[2] if x + y < 17 else STONE[3])
        px(7, 7, STONE[0])
        px(8, 8, rgb("#b9c3ff"))
        px(7, 9, rgb("#b9c3ff"))
    elif state == "prism_moth":
        for y in range(4, 13):
            for x in range(5, 11):
                px(x, y, lerp_rgb(rgb("#ffffff"), rgb("#cfe7ff"), math.hypot(x + 0.5 - 8, y + 0.5 - 8.5) / 3.8))
        wing = [(5, 6), (6, 7), (5, 8), (6, 9), (10, 6), (9, 7), (10, 8), (9, 9)]
        for i, (x, y) in enumerate(wing):
            px(x, y, rainbow(i / len(wing)))
        for y in (7, 8, 9, 10):
            px(7, y, IVORY[2])
            px(8, y, IVORY[1])
    # the middle bars of the frame
    for y in (4, 12):
        for x in range(5, 11):
            if state in ("out", "dark") or y == 12:
                px(x, y, frame[1] if y == 4 else frame[2])
    # the base
    for x in range(4, 12):
        px(x, 13, frame[1] if x < 9 else frame[2])
    for x in range(5, 11):
        px(x, 14, frame[2] if x < 9 else frame[3])
    if state == "dark":
        px(6, 6, rgb("#3a3b46"))
        px(9, 10, rgb("#2c2d36"))
    return img


def effect_icon(kind):
    img = np.zeros((18, 18, 4), np.uint8)

    def px(x, y, c, a=255):
        if 0 <= x < 18 and 0 <= y < 18:
            img[y, x, :3] = c
            img[y, x, 3] = a

    if kind == "refract":
        # a prism, a white ray in, a spread of colours out
        for y in range(4, 15):
            half = (y - 4) * 0.55
            for x in range(18):
                if abs(x + 0.5 - 7.5) <= half + 0.5:
                    px(x, y, rgb("#eef8ff") if abs(x + 0.5 - 7.5) < half - 0.6 else rgb("#9fc2dc"))
        for x in range(0, 6):
            px(x, 10, rgb("#ffffff"))
        for i in range(5):
            c = rainbow(i / 5.0)
            for x in range(11, 18):
                px(x, 8 + i + (x - 11) // 3 * (i - 2) // 2, c)
    elif kind == "kindled":
        for y in range(18):
            for x in range(18):
                d = math.hypot(x + 0.5 - 9, (y + 0.5 - 10) * 0.8)
                tip = (9 - abs(x + 0.5 - 9) * 1.4) > (y + 0.5) * 0.9 - 1
                if d < 5.5 or (y < 10 and tip and abs(x + 0.5 - 9) < 4):
                    px(x, y, lerp_rgb(rgb("#fff4c8"), rgb("#ff7a1a"), d / 6.0))
        px(9, 10, rgb("#ffffff"))
    else:  # gravity drag: rings pressed down on an arrow
        for x in range(8, 10):
            for y in range(3, 14):
                px(x, y, rgb("#c6ceff"))
        for i in range(4):
            px(8 - i, 13 - i, rgb("#c6ceff"))
            px(9 + i, 13 - i, rgb("#c6ceff"))
        for y, r in ((5, 6), (9, 5)):
            for x in range(18):
                if abs(x + 0.5 - 9) <= r and (x < 7 or x > 10):
                    px(x, y, rgb("#6e7cc8"))
    return img


# ====================================================================== the brazier (16 x 16 block textures)
def brazier_textures():
    out = {}
    ys, xs = np.mgrid[0:16, 0:16]

    def noise(seed):
        return np.vectorize(lambda x, y: hash3(x, y, 0.37, seed=seed))(xs, ys)

    n = noise(41)
    stone = np.zeros((16, 16, 4), np.uint8)
    ramp = [rgb("#f4efe4"), rgb("#e4dccb"), rgb("#cfc5b0"), rgb("#b5aa93")]
    for y in range(16):
        for x in range(16):
            i = 1 if n[y, x] < 0.55 else 0 if n[y, x] < 0.8 else 2
            if (x + y * 3) % 11 == 0:
                i = 3
            stone[y, x, :3] = ramp[i]
            stone[y, x, 3] = 255
    for x in range(16):
        stone[1, x, :3] = rgb("#e8bf5a")
        stone[2, x, :3] = rgb("#b98a34")
    out["stone"] = stone
    gold = np.zeros((16, 16, 4), np.uint8)
    g = [rgb("#fff3c4"), rgb("#f6d676"), rgb("#e2b454"), rgb("#b8883a"), rgb("#7a5620")]
    n2 = noise(43)
    for y in range(16):
        for x in range(16):
            # polished gold: light from above in soft bands, a faint grain, fluted every four texels
            i = 1 if y < 5 else 2
            if n2[y, x] > 0.9:
                i -= 1
            if x % 4 == 3 and 3 < y < 13:
                i = 3
            if y in (0, 15):
                i = 3
            if y == 1:
                i = 0
            gold[y, x, :3] = g[max(0, i)]
            gold[y, x, 3] = 255
    out["gold"] = gold
    coals = np.zeros((16, 16, 4), np.uint8)
    embers = np.zeros((16, 16, 4), np.uint8)
    n3 = noise(47)
    n4 = noise(53)
    for y in range(16):
        for x in range(16):
            lump = n3[y, x]
            coals[y, x, :3] = lerp_rgb(rgb("#141216"), rgb("#3b3336"), lump)
            if n4[y, x] > 0.9:
                coals[y, x, :3] = rgb("#7a2a14")
            coals[y, x, 3] = 255
            hot = lump * 0.7 + n4[y, x] * 0.3
            embers[y, x, :3] = lerp_rgb(rgb("#b23c12"), rgb("#ffe08a"), hot * hot * 1.3) if hot > 0.35 else lerp_rgb(rgb("#4a1a10"), rgb("#b23c12"), hot / 0.35)
            embers[y, x, 3] = 255
    out["coals"] = coals
    out["embers"] = embers
    return out


def el(fr, to, faces, emissive=False):
    e = {"from": fr, "to": to, "faces": faces}
    if emissive:
        e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return e


def face(tex, uv, cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def brazier_model(lit):
    bowl_top = "#embers" if lit else "#coals"
    s, g = "#stone", "#gold"
    elements = [
        # the foot and the stem
        el([3, 0, 3], [13, 2, 13], {d: face(s, [3, 14, 13, 16]) for d in ("north", "south", "east", "west")}
           | {"up": face(s, [3, 3, 13, 13]), "down": face(s, [3, 3, 13, 13], "down")}),
        el([5, 2, 5], [11, 8, 11], {d: face(s, [5, 3, 11, 9]) for d in ("north", "south", "east", "west")}),
        # the bowl: its base, the coals inside, four walls, four prongs
        el([1, 8, 1], [15, 10, 15], {d: face(g, [1, 10, 15, 12]) for d in ("north", "south", "east", "west")}
           | {"down": face(g, [1, 1, 15, 15])}),
        el([2, 10, 2], [14, 10.5, 14], {"up": face(bowl_top, [2, 2, 14, 14])}, emissive=lit),
        el([1, 10, 1], [15, 12, 2], {d: face(g, [1, 4, 15, 6]) for d in ("north", "south", "up", "down")}
           | {"east": face(g, [14, 4, 15, 6]), "west": face(g, [1, 4, 2, 6])}),
        el([1, 10, 14], [15, 12, 15], {d: face(g, [1, 4, 15, 6]) for d in ("north", "south", "up", "down")}
           | {"east": face(g, [14, 4, 15, 6]), "west": face(g, [1, 4, 2, 6])}),
        el([1, 10, 2], [2, 12, 14], {d: face(g, [2, 4, 14, 6]) for d in ("east", "west", "up", "down")}),
        el([14, 10, 2], [15, 12, 14], {d: face(g, [2, 4, 14, 6]) for d in ("east", "west", "up", "down")}),
    ]
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        elements.append(el([x, 12, z], [x + 2, 14, z + 2], {d: face(g, [x, 2, x + 2, 4]) for d in ("north", "south", "east", "west", "up")}))
    textures = {
        "stone": "cosmicbreach:block/brazier_of_solenne_stone",
        "gold": "cosmicbreach:block/brazier_of_solenne_gold",
        "coals": "cosmicbreach:block/brazier_of_solenne_coals",
        "embers": "cosmicbreach:block/brazier_of_solenne_embers",
        "particle": "cosmicbreach:block/brazier_of_solenne_gold",
    }
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


# ====================================================================== main
def model_item(path_tex):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": path_tex}}


def main():
    written = []
    kinds = [("emberwisp", build_wisp, wisp_texel, wisp_anims, 64, 64, 1, 1),
             ("gravikin", build_gravikin, gravikin_texel, gravikin_anims, 64, 64, 1, 1),
             ("prism_moth", build_moth, moth_texel, moth_anims, 64, 64, 2, 1)]
    for name, build, texel, anims, tw, th, bw, bh in kinds:
        bones = build()
        pack_uvs(bones, tw, th)
        written.append(write_text(ASSETS / "geo" / "entity" / f"{name}.geo.json", pretty_json(geo_json(bones, name, tw, th, bw, bh))))
        written.append(write_text(ASSETS / "animations" / "entity" / f"{name}.animation.json", pretty_json(anims())))
        tex, glow = paint_model(bones, tw, th, texel)
        written.append(save_png(tex, TEX / "entity" / f"{name}.png"))
        written.append(save_png(glow, TEX / "entity" / f"{name}_glowmask.png"))
    # the Star Egg: plain and by kind, the plain model picking by the egg's kind
    written.append(save_png(egg_icon(None), TEX / "item" / "star_egg.png"))
    overrides = []
    for i, k in enumerate(("emberwisp", "gravikin", "prism_moth")):
        written.append(save_png(egg_icon(k), TEX / "item" / f"star_egg_{k}.png"))
        written.append(write_json(ASSETS / "models" / "item" / f"star_egg_{k}.json", model_item(f"cosmicbreach:item/star_egg_{k}")))
        overrides.append({"predicate": {"cosmicbreach:familiar_kind": round(0.1 * (i + 1), 1)}, "model": f"cosmicbreach:item/star_egg_{k}"})
    egg = model_item("cosmicbreach:item/star_egg")
    egg["overrides"] = overrides
    written.append(write_json(ASSETS / "models" / "item" / "star_egg.json", egg))
    # the Familiar Lantern: lit by kind, out, dark
    overrides = []
    for i, k in enumerate(("emberwisp", "gravikin", "prism_moth")):
        written.append(save_png(lantern_icon(k), TEX / "item" / f"familiar_lantern_{k}.png"))
        written.append(write_json(ASSETS / "models" / "item" / f"familiar_lantern_{k}.json", model_item(f"cosmicbreach:item/familiar_lantern_{k}")))
        overrides.append({"predicate": {"cosmicbreach:familiar_kind": round(0.1 * (i + 1), 1)}, "model": f"cosmicbreach:item/familiar_lantern_{k}"})
    for state, value in (("out", 0.5), ("dark", 1.0)):
        written.append(save_png(lantern_icon(state), TEX / "item" / f"familiar_lantern_{state}.png"))
        written.append(write_json(ASSETS / "models" / "item" / f"familiar_lantern_{state}.json", model_item(f"cosmicbreach:item/familiar_lantern_{state}")))
        overrides.append({"predicate": {"cosmicbreach:lantern": value}, "model": f"cosmicbreach:item/familiar_lantern_{state}"})
    written.append(save_png(lantern_icon("out"), TEX / "item" / "familiar_lantern.png"))
    lantern = model_item("cosmicbreach:item/familiar_lantern")
    lantern["overrides"] = overrides
    written.append(write_json(ASSETS / "models" / "item" / "familiar_lantern.json", lantern))
    # the Brazier of Solenne
    for part, img in brazier_textures().items():
        written.append(save_png(img, TEX / "block" / f"brazier_of_solenne_{part}.png"))
    written.append(write_json(ASSETS / "models" / "block" / "brazier_of_solenne.json", brazier_model(False)))
    written.append(write_json(ASSETS / "models" / "block" / "brazier_of_solenne_lit.json", brazier_model(True)))
    written.append(write_json(ASSETS / "blockstates" / "brazier_of_solenne.json", {"variants": {
        "lit=false": {"model": "cosmicbreach:block/brazier_of_solenne"},
        "lit=true": {"model": "cosmicbreach:block/brazier_of_solenne_lit"}}}))
    written.append(write_json(ASSETS / "models" / "item" / "brazier_of_solenne.json", {"parent": "cosmicbreach:block/brazier_of_solenne"}))
    # the statuses' icons
    for k in ("refract", "kindled", "gravity_drag"):
        written.append(save_png(effect_icon(k), TEX / "mob_effect" / f"{k}.png"))
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
