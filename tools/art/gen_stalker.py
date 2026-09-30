"""The Hollow Stalker and its Mask Shard, for GeckoLib 4 and the block models.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/hollow_stalker.geo.json                 Bedrock geometry 1.12.0, per-face UV, full size (16 units a block)
    animations/entity/hollow_stalker.animation.json    idle, walk, caught, rend_tell, rend, grasp_tell, grasp, stagger
    textures/entity/hollow_stalker.png                 translucent void (the texture's alpha is the body's), a porcelain mask
    textures/entity/hollow_stalker_glowmask.png        the eye slits' magenta (light added over the model in the game)
    textures/block/mask_shard.png, models/block/mask_shard.json, blockstates/mask_shard.json, models/item/mask_shard.json

GDD 7.1: a tall, thin shape of void, 2.6 blocks high, nearly invisible except for a porcelain mask with magenta eye
slits. So: long thin limbs (arms hanging past the knees, three long fingers), a narrow chest hunched forward, a
narrow head under a hood of void, trailing tatters from the shoulders; all of it near-black and translucent (alpha
120 to 200, a hair lighter at the edges so it reads as a shape and never as a solid box). The mask is the only
clean surface: ivory porcelain, a faint crack, a brow ridge and a nose ridge, and two thin slanted eye slits, no mouth.
The mask's front is painted at 8 texels a unit (the rest at 1), so the slits are fine lines, not dots.

Conventions as the other GeckoLib generators: Bedrock model space, y up, the head faces -Z, the creature's left is +X.
The mask's middle is 2.35 blocks up (StalkerRules.MASK_Y).

Run:  python tools/art/gen_stalker.py
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np

from common import ASSETS, TEX, rel, save_png

TEX_W, TEX_H = 128, 128
MASK_SCALE = 8                  # texels a unit on the mask's front
VOID = (12, 7, 20)
VOID_EDGE = (40, 22, 60)
PORCELAIN = (240, 240, 238)
PORCELAIN_SIDE = (208, 208, 210)
PORCELAIN_SHADE = (176, 176, 182)
CRACK = (140, 128, 124)
SLIT = (46, 8, 40)
MAGENTA = (255, 64, 222)


class Cube:
    def __init__(self, origin, size, kind, pivot=None, rotation=None, faces=None, scale=1):
        self.origin = origin
        self.size = size
        self.kind = kind
        self.pivot = pivot
        self.rotation = rotation
        self.faces = faces or ["north", "south", "east", "west", "up", "down"]
        self.scale = scale
        self.uv = {}


class Bone:
    def __init__(self, name, parent, pivot, cubes=(), rotation=None):
        self.name = name
        self.parent = parent
        self.pivot = pivot
        self.cubes = list(cubes)
        self.rotation = rotation


def box(cx, cy, cz, w, h, d, kind, **kw):
    return Cube((cx - w / 2, cy - h / 2, cz - d / 2), (w, h, d), kind, **kw)


def seg(x, y0, y1, z, w, d, kind, **kw):
    """A limb segment hanging from y1 down to y0."""
    return Cube((x - w / 2, y0, z - d / 2), (w, y1 - y0, d), kind, **kw)


def build():
    bones = [Bone("root", None, (0, 0, 0))]
    # legs: long and thin, a little bent back at the knee
    for side, name in ((1, "left"), (-1, "right")):
        bones.append(Bone(f"leg_{name}", "root", (side * 1.9, 20, 0), [
            seg(side * 1.9, 10.5, 20.5, 0.2, 1.6, 1.6, "void"),
            seg(side * 1.9, 1.0, 11.0, 0.8, 1.2, 1.2, "void", pivot=(side * 1.9, 11, 0.8), rotation=(-6, 0, 0)),
            box(side * 1.9, 0.5, -0.4, 1.4, 1.0, 3.0, "void"),
        ]))
    bones.append(Bone("body", "root", (0, 20, 0), [
        box(0, 21, 0, 3.2, 2.0, 2.0, "void"),                   # pelvis
        box(0, 25, 0.3, 2.0, 6.0, 1.5, "void"),                 # the thin waist
        box(0, 30.5, 0.1, 5.2, 5.5, 2.4, "void"),               # the narrow chest
        box(0, 33.4, 0.2, 6.4, 1.2, 2.0, "void"),               # shoulders
    ], rotation=(8, 0, 0)))
    bones.append(Bone("neck", "body", (0, 34, 0), [box(0, 35.2, -0.3, 1.0, 2.6, 1.0, "void")], rotation=(-6, 0, 0)))
    bones.append(Bone("head", "neck", (0, 36.2, -0.4), [
        box(0, 38.4, 0.2, 3.2, 4.4, 3.0, "void"),               # the skull under the mask
        box(0, 41.0, 0.9, 4.0, 1.2, 3.6, "hood"),                # a hood of void rising behind
        box(0, 41.1, 2.3, 2.4, 1.0, 1.8, "hood"),
    ]))
    # the mask: a plate on the face, a brow ridge, a nose ridge; front at 8 texels a unit
    bones.append(Bone("mask", "head", (0, 38.4, -1.6), [
        box(0, 38.4, -1.9, 3.8, 5.6, 0.5, "mask", faces=["north", "east", "west", "up", "down"], scale=MASK_SCALE),
        Cube((1.55, 36.9, -1.9), (0.35, 3.0, 1.0), "mask_cheek"),      # the shell's sides, wrapping back toward the head
        Cube((-1.9, 36.9, -1.9), (0.35, 3.0, 1.0), "mask_cheek"),
        box(0, 39.9, -2.2, 3.2, 0.5, 0.3, "mask_ridge"),
        box(0, 37.9, -2.25, 0.6, 2.4, 0.4, "mask_ridge"),
    ]))
    for side, name in ((1, "left"), (-1, "right")):
        bones.append(Bone(f"arm_{name}", "body", (side * 3.2, 33, 0.2), [
            seg(side * 3.4, 21.5, 33.5, 0.2, 1.2, 1.2, "void"),
        ], rotation=(-4, 0, side * -4)))
        bones.append(Bone(f"forearm_{name}", f"arm_{name}", (side * 3.4, 21.8, 0.2), [
            seg(side * 3.5, 11.0, 22.0, 0.2, 1.0, 1.0, "void"),
        ], rotation=(-8, 0, 0)))
        fingers = []
        for k, dz in enumerate((-0.45, 0.0, 0.45)):
            fingers.append(Cube((side * 3.5 - 0.25 + side * (k - 1) * 0.15, 5.5, 0.2 + dz - 0.25), (0.5, 5.8, 0.5), "finger",
                                pivot=(side * 3.5, 11.2, 0.2 + dz), rotation=(0, 0, side * (k - 1) * -6)))
        bones.append(Bone(f"hand_{name}", f"forearm_{name}", (side * 3.5, 11.2, 0.2), fingers))
        bones.append(Bone(f"tatter_{name}", "body", (side * 2.4, 33, 1.2), [
            seg(side * 2.4, 17.0, 33.0, 1.3, 1.4, 0.3, "tatter"),
            seg(side * 2.2, 11.0, 17.0, 1.5, 0.9, 0.3, "tatter"),
        ], rotation=(6, 0, side * 3)))
    bones.append(Bone("tatter_back", "body", (0, 33, 1.3), [
        seg(0, 14.0, 33.0, 1.4, 2.0, 0.3, "tatter"),
        seg(0.3, 8.0, 14.0, 1.6, 1.2, 0.3, "tatter"),
    ], rotation=(8, 0, 0)))
    return bones


# ------------------------------------------------------------------ UV layout (per face)
FACE_DIMS = {  # (width axis, height axis) of each face's rect
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2),
}


def layout(bones):
    """Packs every face's rect into the texture (shelves); returns [(cube, face, x, y, w, h)]."""
    rects = []
    for b in bones:
        for c in b.cubes:
            for f in c.faces:
                a, bb = FACE_DIMS[f]
                s = c.scale if f == "north" else 1
                w = max(1, int(round(c.size[a] * s)))
                h = max(1, int(round(c.size[bb] * s)))
                rects.append([c, f, 0, 0, w, h])
    order = sorted(rects, key=lambda r: -r[5])
    x = y = row = 0
    for r in order:
        if x + r[4] > TEX_W:
            x = 0
            y += row + 1
            row = 0
        r[2], r[3] = x, y
        x += r[4] + 1
        row = max(row, r[5])
    if y + row > TEX_H:
        raise SystemExit(f"UV layout needs {y + row} rows")
    for c, f, x, y, w, h in rects:
        c.uv[f] = {"uv": [x, y], "uv_size": [w, h]}
    return rects


def hash01(*v):
    h = 2166136261
    for x in v:
        h ^= int(round(x * 97)) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    return (h % 10007) / 10007.0


def paint(rects):
    tex = np.zeros((TEX_H, TEX_W, 4), np.uint8)
    glow = np.zeros_like(tex)
    for c, f, x0, y0, w, h in rects:
        for r in range(h):
            for col in range(w):
                edge = r == 0 or col == 0 or r == h - 1 or col == w - 1
                colour, alpha, g = texel(c, f, col, r, w, h, edge)
                tex[y0 + r, x0 + col, :3] = colour
                tex[y0 + r, x0 + col, 3] = alpha
                if g is not None:
                    glow[y0 + r, x0 + col, :3] = g
                    glow[y0 + r, x0 + col, 3] = 255
    return tex, glow


def texel(c, face, col, row, w, h, edge):
    n = hash01(col, row, c.origin[0], c.origin[1], len(face))
    if c.kind in ("void", "hood", "finger", "tatter"):
        base = {"void": 120, "hood": 95, "finger": 100, "tatter": 70}[c.kind]
        # smoke-like: streaks down the length, thinning toward the lower end of a limb
        streak = 22 * math.sin(col * 2.1 + c.origin[0] * 3.0) * (1 if face in ("north", "south", "east", "west") else 0)
        low = c.origin[1] < 12 and face in ("north", "south", "east", "west")
        fadeout = (row / max(1, h - 1)) * (45 if low else 20)
        a = int(max(30, base + streak + 24 * (n - 0.5) - fadeout))
        if edge:
            return VOID_EDGE, min(190, a + 35), None
        return VOID, a, None
    if c.kind in ("mask_ridge", "mask_cheek"):
        if face == "north":
            return (PORCELAIN if c.kind == "mask_ridge" else PORCELAIN_SIDE), 255, None
        return PORCELAIN_SIDE if face != "down" else PORCELAIN_SHADE, 255, None
    # the mask plate
    if face != "north":
        return (PORCELAIN_SIDE if face in ("east", "west", "up") else PORCELAIN_SHADE), 255, None
    u = (col + 0.5) / w           # 0 at the plate's west edge as seen from the front... mirrored by the viewer
    v = (row + 0.5) / h           # 0 at the top
    x = u - 0.5
    y = v - 0.5
    # an oval-ish plate: the corners cut away (transparent), a soft shade toward the rim
    if (x / 0.5) ** 2 + (y / 0.52) ** 2 > 1.0:
        return (0, 0, 0), 0, None
    rim = (x / 0.5) ** 2 + (y / 0.52) ** 2
    shade = 1.0 - 0.14 * max(0.0, rim - 0.55) / 0.45
    colour = tuple(int(ch * shade) for ch in PORCELAIN)
    # eye slits: thin and slanted down toward the middle, at 36% height
    for sx in (-1, 1):
        cx, cy = sx * 0.21, -0.12
        dx = x - cx
        slope = sx * 0.25
        dy = y - (cy + slope * dx * -1)
        if abs(dx) < 0.17 and abs(dy) < 0.022 + 0.022 * (1 - abs(dx) / 0.17):
            return SLIT, 255, MAGENTA
    # a hairline crack from the right brow down across the cheek
    t = (y + 0.35) / 0.6
    if 0.0 <= t <= 1.0:
        crack_x = 0.18 - 0.1 * t + 0.03 * math.sin(t * 9)
        if abs(x - crack_x) < 0.012:
            return CRACK, 255, None
    return colour, 255, None


# ------------------------------------------------------------------ geometry JSON
def r4(v):
    return [round(float(a), 4) for a in v]


def geo(bones):
    out = []
    for b in bones:
        d = {"name": b.name}
        if b.parent:
            d["parent"] = b.parent
        d["pivot"] = r4(b.pivot)
        if b.rotation:
            d["rotation"] = r4(b.rotation)
        if b.cubes:
            cubes = []
            for c in b.cubes:
                cd = {"origin": r4(c.origin), "size": r4(c.size), "uv": c.uv}
                if c.rotation:
                    cd["pivot"] = r4(c.pivot)
                    cd["rotation"] = r4(c.rotation)
                cubes.append(cd)
            d["cubes"] = cubes
        out.append(d)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.hollow_stalker", "texture_width": TEX_W, "texture_height": TEX_H,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out}]}


# ------------------------------------------------------------------ animations
def kf(times, fn):
    return {f"{t:.4f}".rstrip("0").rstrip(".") if t else "0.0": [round(a, 3) for a in fn(t)] for t in times}


def loop_times(length, steps):
    return [length * i / steps for i in range(steps + 1)]


def anims():
    out = {}
    L = 3.0
    ts = loop_times(L, 12)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * t / L + ph)  # noqa: E731
    out["idle"] = {"loop": True, "animation_length": L, "bones": {
        "body": {"rotation": kf(ts, lambda t: (1.5 * s(t), 0, 2.0 * s(t, 1.2)))},
        "head": {"rotation": kf(ts, lambda t: (3 * s(t, 0.5), 6 * s(t, 2.0), 7 * s(t, 0.9)))},
        "arm_left": {"rotation": kf(ts, lambda t: (3 * s(t, 0.3), 0, -2 * s(t, 1.1)))},
        "arm_right": {"rotation": kf(ts, lambda t: (3 * s(t, 1.9), 0, 2 * s(t, 0.4)))},
        "hand_left": {"rotation": kf(ts, lambda t: (6 * s(t, 2.4), 0, 4 * s(t, 0.2)))},
        "hand_right": {"rotation": kf(ts, lambda t: (6 * s(t, 0.7), 0, -4 * s(t, 1.6)))},
        "tatter_left": {"rotation": kf(ts, lambda t: (6 + 5 * s(t, 0.2), 0, 3 * s(t, 1.0)))},
        "tatter_right": {"rotation": kf(ts, lambda t: (6 + 5 * s(t, 1.4), 0, -3 * s(t, 0.6)))},
        "tatter_back": {"rotation": kf(ts, lambda t: (8 + 6 * s(t, 2.2), 0, 2 * s(t, 0.5)))},
    }}
    W = 1.3
    tw = loop_times(W, 8)
    w = lambda t, ph=0.0: math.sin(2 * math.pi * t / W + ph)  # noqa: E731
    out["walk"] = {"loop": True, "animation_length": W, "bones": {
        "leg_left": {"rotation": kf(tw, lambda t: (28 * w(t), 0, 0))},
        "leg_right": {"rotation": kf(tw, lambda t: (-28 * w(t), 0, 0))},
        "body": {"rotation": kf(tw, lambda t: (12, 3 * w(t), 0)), "position": kf(tw, lambda t: (0, 0.4 * abs(w(t)), 0))},
        "arm_left": {"rotation": kf(tw, lambda t: (-8 * w(t) + 6, 0, 0))},
        "arm_right": {"rotation": kf(tw, lambda t: (8 * w(t) + 6, 0, 0))},
        "head": {"rotation": kf(tw, lambda t: (-8, 0, 4 * w(t, 0.8)))},
        "tatter_left": {"rotation": kf(tw, lambda t: (22 + 6 * w(t), 0, 4))},
        "tatter_right": {"rotation": kf(tw, lambda t: (22 + 6 * w(t, 1.5), 0, -4))},
        "tatter_back": {"rotation": kf(tw, lambda t: (26 + 7 * w(t, 0.7), 0, 0))},
    }}
    out["caught"] = {"loop": "hold_on_last_frame", "animation_length": 0.25, "bones": {
        "head": {"rotation": {"0.0": [0, 0, 0], "0.1": [-14, 0, 26], "0.25": [-12, 0, 24]}},
        "body": {"rotation": {"0.0": [8, 0, 0], "0.1": [-4, 0, 0], "0.25": [-3, 0, 0]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.1": [-30, 0, -18], "0.25": [-28, 0, -16]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.1": [-24, 0, 20], "0.25": [-22, 0, 18]}},
        "hand_left": {"rotation": {"0.0": [0, 0, 0], "0.1": [-20, 0, 0]}},
        "hand_right": {"rotation": {"0.0": [0, 0, 0], "0.1": [-20, 0, 0]}},
    }}
    out["rend_tell"] = {"loop": "hold_on_last_frame", "animation_length": 1.0, "bones": {
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.6": [-150, 20, 25], "1.0": [-160, 22, 28]}},
        "forearm_right": {"rotation": {"0.0": [0, 0, 0], "0.6": [-40, 0, 0], "1.0": [-45, 0, 0]}},
        "hand_right": {"rotation": {"0.0": [0, 0, 0], "0.8": [-30, 0, 20], "1.0": [-35, 0, 24]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.6": [-35, 0, -20]}},
        "body": {"rotation": {"0.0": [8, 0, 0], "0.6": [-10, -18, 0], "1.0": [-12, -22, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.6": [8, 10, -14], "1.0": [10, 12, -18]}},
    }}
    out["rend"] = {"loop": "hold_on_last_frame", "animation_length": 0.7, "bones": {
        "arm_right": {"rotation": {"0.0": [-160, 22, 28], "0.1": [-40, -30, -10], "0.2": [10, -40, -20], "0.7": [0, 0, 0]}},
        "forearm_right": {"rotation": {"0.0": [-45, 0, 0], "0.1": [-10, 0, 0], "0.7": [0, 0, 0]}},
        "hand_right": {"rotation": {"0.0": [-35, 0, 24], "0.15": [10, 0, -10], "0.7": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [-12, -22, 0], "0.1": [22, 18, 0], "0.3": [26, 20, 0], "0.7": [8, 0, 0]},
                 "position": {"0.0": [0, 0, 0], "0.1": [0, -0.5, -2.0], "0.7": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [10, 12, -18], "0.15": [-6, -8, 6], "0.7": [0, 0, 0]}},
    }}
    out["grasp_tell"] = {"loop": "hold_on_last_frame", "animation_length": 0.7, "bones": {
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.7": [-72, -14, -10]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.7": [-72, 14, 10]}},
        "forearm_left": {"rotation": {"0.0": [0, 0, 0], "0.7": [-18, 0, 0]}},
        "forearm_right": {"rotation": {"0.0": [0, 0, 0], "0.7": [-18, 0, 0]}},
        "hand_left": {"rotation": {"0.0": [0, 0, 0], "0.7": [-30, 0, -25]}},
        "hand_right": {"rotation": {"0.0": [0, 0, 0], "0.7": [-30, 0, 25]}},
        "body": {"rotation": {"0.0": [8, 0, 0], "0.7": [20, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.7": [14, 0, 16]}},
    }}
    G = 1.0
    tg = loop_times(G, 4)
    g = lambda t: math.sin(2 * math.pi * t / G)  # noqa: E731
    out["grasp"] = {"loop": True, "animation_length": G, "bones": {
        "arm_left": {"rotation": kf(tg, lambda t: (-82 + 3 * g(t), -38, -6))},
        "arm_right": {"rotation": kf(tg, lambda t: (-82 + 3 * g(t), 38, 6))},
        "forearm_left": {"rotation": kf(tg, lambda t: (-30, -20, 0))},
        "forearm_right": {"rotation": kf(tg, lambda t: (-30, 20, 0))},
        "hand_left": {"rotation": kf(tg, lambda t: (-40, 0, -30 + 6 * g(t)))},
        "hand_right": {"rotation": kf(tg, lambda t: (-40, 0, 30 - 6 * g(t)))},
        "body": {"rotation": kf(tg, lambda t: (24, 0, 0))},
        "head": {"rotation": kf(tg, lambda t: (22, 0, 18 + 3 * g(t)))},
    }}
    out["stagger"] = {"loop": "hold_on_last_frame", "animation_length": 1.0, "bones": {
        "body": {"rotation": {"0.0": [8, 0, 0], "0.12": [-22, 0, 8], "0.6": [-16, 0, 6], "1.0": [4, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.12": [-30, 0, -20], "0.6": [-22, 0, -16], "1.0": [0, 0, 0]}},
        "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.12": [-40, 0, -50], "1.0": [0, 0, 0]}},
        "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.12": [-30, 0, 55], "1.0": [0, 0, 0]}},
    }}
    return {"format_version": "1.8.0", "animations": out}


# ------------------------------------------------------------------ the Mask Shard block
def mask_shard_texture():
    """16x16: the shard's porcelain front (left 10 columns: a broken half-mask with one slit), its edges (right)."""
    img = np.zeros((16, 16, 4), np.uint8)
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    front = (xs < 10.5)
    img[front, :3] = PORCELAIN
    img[front, 3] = 255
    # a shade toward the broken edge and the bottom
    for y in range(16):
        for x in range(11):
            k = 1.0 - 0.18 * (y / 15.0) - 0.1 * (x / 10.0)
            img[y, x, :3] = [int(c * k) for c in PORCELAIN]
    for x in range(2, 7):  # the slit
        y = 5 + (x - 2) // 3
        img[y, x, :3] = SLIT
    for y in range(7, 14):  # a crack
        img[y, 7 + (y % 3 == 0), :3] = CRACK
    img[:, 11:, :3] = PORCELAIN_SIDE
    img[:, 11:, 3] = 255
    return img


def main():
    written = []
    bones = build()
    rects = layout(bones)
    geo_path = ASSETS / "geo" / "entity" / "hollow_stalker.geo.json"
    geo_path.parent.mkdir(parents=True, exist_ok=True)
    geo_path.write_bytes((json.dumps(geo(bones), indent=1) + "\n").encode("utf-8"))
    written.append(geo_path)
    anim_path = ASSETS / "animations" / "entity" / "hollow_stalker.animation.json"
    anim_path.write_bytes((json.dumps(anims(), indent=1) + "\n").encode("utf-8"))
    written.append(anim_path)
    tex, glow = paint(rects)
    written.append(save_png(tex, TEX / "entity" / "hollow_stalker.png"))
    written.append(save_png(glow, TEX / "entity" / "hollow_stalker_glowmask.png"))
    written.append(save_png(mask_shard_texture(), TEX / "block" / "mask_shard.png"))
    model = {"parent": "minecraft:block/block", "textures": {"shard": "cosmicbreach:block/mask_shard", "particle": "cosmicbreach:block/mask_shard"},
             "elements": [
                 {"from": [4, 0, 7], "to": [12, 11, 9], "faces": {
                     "north": {"uv": [0, 2, 8, 13], "texture": "#shard"}, "south": {"uv": [8, 2, 0, 13], "texture": "#shard"},
                     "east": {"uv": [11, 2, 13, 13], "texture": "#shard"}, "west": {"uv": [11, 2, 13, 13], "texture": "#shard"},
                     "up": {"uv": [11, 0, 13, 8], "texture": "#shard", "rotation": 90},
                     "down": {"uv": [11, 0, 13, 8], "texture": "#shard", "rotation": 90}}},
                 {"from": [5, 11, 7.2], "to": [10, 13, 8.8], "faces": {
                     "north": {"uv": [1, 0, 6, 2], "texture": "#shard"}, "south": {"uv": [6, 0, 1, 2], "texture": "#shard"},
                     "east": {"uv": [11, 0, 13, 2], "texture": "#shard"}, "west": {"uv": [11, 0, 13, 2], "texture": "#shard"},
                     "up": {"uv": [11, 0, 13, 5], "texture": "#shard", "rotation": 90}}},
                 {"from": [6, 3, 6.4], "to": [8, 9, 7], "faces": {
                     "north": {"uv": [4, 6, 6, 12], "texture": "#shard"}, "east": {"uv": [11, 6, 12, 12], "texture": "#shard"},
                     "west": {"uv": [11, 6, 12, 12], "texture": "#shard"}, "up": {"uv": [11, 6, 12, 8], "texture": "#shard"}}},
             ],
             "display": {"gui": {"rotation": [20, 200, 0], "translation": [0, 1.5, 0], "scale": [0.9, 0.9, 0.9]},
                         "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                         "fixed": {"rotation": [0, 180, 0], "scale": [0.8, 0.8, 0.8]}}}
    for path, data in ((ASSETS / "models" / "block" / "mask_shard.json", model),
                       (ASSETS / "models" / "item" / "mask_shard.json", {"parent": "cosmicbreach:block/mask_shard"}),
                       (ASSETS / "blockstates" / "mask_shard.json", {"variants": {
                           "facing=north": {"model": "cosmicbreach:block/mask_shard"},
                           "facing=east": {"model": "cosmicbreach:block/mask_shard", "y": 90},
                           "facing=south": {"model": "cosmicbreach:block/mask_shard", "y": 180},
                           "facing=west": {"model": "cosmicbreach:block/mask_shard", "y": 270}}})):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))
        written.append(path)
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
