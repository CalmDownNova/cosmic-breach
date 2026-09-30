"""The Shardling: a crystal hunting animal the size of a fox, for GeckoLib 4.

Writes
    geo/entity/shardling.geo.json                Bedrock geometry 1.12.0, box UV
    animations/entity/shardling.animation.json   Bedrock animation 1.8.0
    textures/entity/shardling.png                64x64
    textures/entity/shardling_glowmask.png       64x64, only spines and eyes opaque
    textures/entity/shardling_tell.png (+ _glowmask)   the lunge telegraph: crest spines gold
    textures/entity/shardling_spit.png (+ _glowmask)   the spit telegraph: eyes and jaw line red

Conventions (Bedrock model space, what the geo file stores):
    16 units = 1 block, y up, the head points to -Z, the creature's LEFT is +X.
    Rotations are degrees. Positive X rotation tips the front of a part down
    and swings the bottom of a hanging part (a leg) backwards; negative X
    lifts a head and swings a foot forwards. Spines lean back with negative X.
    Animated rotations are added to the bind rotation in the geo file.

The texture is painted in model space: for every face texel we know the 3D
point it lands on (the box UV rules below match GeckoLib's BakedModelFactory),
so the painters say things like "turquoise facet where y < 7 on the flank"
and the layout can be re-packed freely.

Run:  python tools/art/gen_shardling.py   (then preview_shardling.py)
"""
from __future__ import annotations

import copy
import json
import math
import re
from dataclasses import dataclass, field

import numpy as np

from bedrock_model import lowest_point, plant_leg, pose_at, split_geo
from common import ASSETS, rel, rgb, save_png

TEX_W, TEX_H = 64, 64

# ------------------------------------------------------------------ palette
# Slate, not chalk: the first cut (#fbfcfd .. #8f9bb0) vanished on the arena's white calcite.
STONE = [rgb("#d9e0e8"), rgb("#bcc6d3"), rgb("#9ca8b9"), rgb("#7c889c"), rgb("#5b6579")]  # hi .. dark
TURQ = [rgb("#c4f8f0"), rgb("#74e3d7"), rgb("#33bfb5"), rgb("#1a8b8d"), rgb("#105a63")]
GOLD = [rgb("#fff0a8"), rgb("#f2c354"), rgb("#c98d2a"), rgb("#8a5a1c")]
SPINE = [rgb("#f4fffd"), rgb("#c6fbf3"), rgb("#86efe3"), rgb("#44d4c8"), rgb("#22a9a8")]  # tip .. root
EYE = rgb("#f0fffc")
EYE_RIM = rgb("#34e3d3")
# Telegraph variants (GDD 4.1): gold means parryable, red means dodge.
GOLD_SPINE = [rgb("#fffbe8"), rgb("#fff0a8"), rgb("#ffd966"), rgb("#f2b33c"), rgb("#c98d2a")]  # tip .. root
RED_EYE = rgb("#ffe0d8")
RED_RIM = rgb("#ff3b2f")
RED_JAW = rgb("#ff5a3c")


# ------------------------------------------------------------------ model data
@dataclass
class Cube:
    origin: tuple
    size: tuple
    paint: str
    rotation: tuple | None = None
    pivot: tuple | None = None
    glow: bool = False
    uv: tuple | None = None
    tag: str = ""
    inflate: float = 0.0

    @property
    def dims(self):
        return tuple(int(math.floor(s)) for s in self.size)


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple
    rotation: tuple | None = None
    cubes: list = field(default_factory=list)


SPINE_STYLE = "diamond"   # "diamond": square prisms turned 45 degrees; "blade": flat fins
EARS = False              # two small crystal ears; tried, they muddle the crest and the wedge head
SNOUT_CRYSTAL = False     # a crystal ridge on the snout; tried, from the front it stands up like a unicorn's horn
LEG_DROP = 1.0            # the shins are this much shorter than the first cut and all above them drops with
                          # them: the first cut stood tall on thin legs and read as a pony, not a fox


def spine_bone(i: int, z: float, root_y: float, base_h: int, tip_h: int, lean: float) -> Bone:
    """One crystal spine, leaning back from its root on the back.

    diamond: a square prism turned 45 degrees about its own axis (a diamond in
    cross section, so it shows a lit and a shaded facet from any side), with a
    thinner prism for the point.
    blade:   a fin two units deep at the root and one at the point.
    """
    piv = (0, root_y, z)
    if SPINE_STYLE == "blade":
        cubes = [
            Cube((-0.5, root_y - 1.5, z - 1), (1, base_h + 1, 2), "spine", glow=True, tag=f"spine_{i}_base"),
            Cube((-0.5, root_y - 0.5 + base_h, z - 0.3), (1, tip_h, 1), "spine_tip", glow=True,
                 inflate=-0.12, tag=f"spine_{i}_tip"),
        ]
    else:
        cubes = [
            Cube((-0.5, root_y - 1.5, z - 0.5), (1, base_h + 1, 1), "spine", rotation=(0, 45, 0), pivot=piv,
                 inflate=0.06, glow=True, tag=f"spine_{i}_base"),
            Cube((-0.5, root_y - 0.3 + base_h, z - 0.5), (1, tip_h, 1), "spine_tip", rotation=(0, 45, 0), pivot=piv,
                 inflate=-0.14, glow=True, tag=f"spine_{i}_tip"),
        ]
    splay = CREST_SPLAY if i % 2 else -CREST_SPLAY
    return Bone(f"spine_{i}", "spines", piv, (lean, 0, splay), cubes)


# (z, base height, tip height, lean): tallest over the shoulders, shrinking to the rump
# Spines lean out to alternate sides by this much: in one straight row the crest lines up, seen
# from the front, into a single spike over the head that reads as a horn; splayed, it reads as a fan.
CREST_SPLAY = 10.0
CREST = [(-3.1, 3, 3, -28), (-1.8, 4, 3, -34), (-0.5, 3, 3, -40), (0.8, 3, 2, -46), (2.1, 2, 2, -52), (3.4, 1, 2, -58)]


def build_model() -> list[Bone]:
    d = LEG_DROP
    shin = 3 - d
    bones = [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 7 - d, 0), None, [
            Cube((-2, 4.5 - d, -3.5), (4, 5, 4), "chest", tag="chest"),
            Cube((-1.5, 5 - d, 0), (3, 4, 4), "hind", tag="hind"),
        ]),
        Bone("neck", "body", (0, 8 - d, -3), None, [
            Cube((-1.5, 7 - d, -4.6), (3, 3, 3), "neck", rotation=(-18, 0, 0), pivot=(0, 8 - d, -3), inflate=-0.15, tag="neck"),
        ]),
        Bone("head", "neck", (0, 9.5 - d, -4.2), None, [
            Cube((-1.5, 8.5 - d, -6.7), (3, 3, 3), "skull", tag="skull"),
            Cube((-1, 8.5 - d, -8.7), (2, 2, 2), "snout", tag="snout"),
            Cube((-0.5, 8.7 - d, -9.7), (1, 1, 2), "nose", tag="nose"),
        ] + ([
            Cube((-0.5, 10.9 - d, -9.7), (1, 1, 4), "snout_crystal", rotation=(29, 0, 0), pivot=(0, 11.9 - d, -5.7),
                 inflate=0.05, tag="snout_crystal"),
        ] if SNOUT_CRYSTAL else []) + ([
            # ears: small swept-back crystal points, stone outside, turquoise inside
            Cube((-0.1, 11.2 - d, -5.6), (2, 2, 1), "ear", rotation=(-12, 0, -12), pivot=(0.9, 11.5 - d, -5.1), inflate=-0.1, tag="ear_left"),
            Cube((-1.9, 11.2 - d, -5.6), (2, 2, 1), "ear", rotation=(-12, 0, 12), pivot=(-0.9, 11.5 - d, -5.1), inflate=-0.1, tag="ear_right"),
        ] if EARS else [])),
    ]
    # front legs straight: slim stone thigh, short dark shin ("socks") with crystal claws
    for side, x in (("left", 1.35), ("right", -1.35)):
        bones.append(Bone(f"leg_front_{side}", "body", (x, 6 - d, -1.5), None, [
            Cube((x - 1, 2.5 - d, -2.5), (2, 4, 2), "thigh", inflate=-0.3, tag=f"front_{side}_thigh"),
            Cube((x - 0.5, 0, -2), (1, shin, 1), "shin", tag=f"front_{side}_shin"),
        ]))
    # back legs with a hock: thigh swept back, shin angled forward to the foot
    for side, x in (("left", 1.1), ("right", -1.1)):
        hip = (x, 6.5 - d, 2.8)
        bones.append(Bone(f"leg_back_{side}", "body", hip, None, [
            Cube((x - 1, 2.5 - d, 1.8), (2, 4, 2), "thigh", rotation=(24, 0, 0), pivot=hip, inflate=-0.3,
                 tag=f"back_{side}_thigh"),
            Cube((x - 0.5, -0.03, 3.9), (1, shin, 1), "shin", rotation=(-22, 0, 0), pivot=(x, 2.97 - d, 4.4),
                 tag=f"back_{side}_shin"),
        ]))
    bones.append(Bone("spines", "body", (0, 9.2 - d, 0)))
    for i, (z, bh, th, lean) in enumerate(CREST, start=1):
        root_y = (9.5 if z < 0 else 9.0) - d
        bones.append(spine_bone(i, z, root_y, bh, th, lean))
    # tail: three spines fanning back from the rump, the middle one longest
    tail = Bone("tail", "body", (0, 7.5 - d, 4), None, [])
    for j, (ry, rx, length) in enumerate(((0, -64, 7), (28, -84, 5), (-28, -84, 5))):
        tail.cubes.append(Cube((-1, 6.5 - d, 3), (2, length, 2), "tail_spine", rotation=(rx, ry, 0), pivot=(0, 7.5 - d, 4),
                               inflate=-0.4, glow=True, tag=f"tail_{j}"))
    bones.append(tail)
    return bones


# ------------------------------------------------------------------ box UV (matches GeckoLib 4.9)
# face_rects: where each face sits in the texture (u, v, width, height).
# texel_point: which 3D point a texel (col, row) of that face lands on.
# preview_shardling.py checks both against GeckoLib's own face and corner order.
def face_rects(c: Cube):
    w, h, d = c.dims
    u, v = c.uv
    return {
        "xneg": (u, v + d, d, h),              # GeckoLib EAST: Bedrock -X side (creature's right)
        "front": (u + d, v + d, w, h),         # NORTH, -Z
        "xpos": (u + d + w, v + d, d, h),      # WEST: Bedrock +X side (creature's left)
        "back": (u + 2 * d + w, v + d, w, h),  # SOUTH, +Z
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
    }


def texel_point(c: Cube, face: str, col: int, row: int):
    """Cube-local (unrotated) Bedrock point at the centre of a face texel."""
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    cc, rr = col + 0.5, row + 0.5
    if face == "xneg":
        return (x0, y1 - rr, z1 - cc)
    if face == "front":
        return (x0 + cc, y1 - rr, z0)
    if face == "xpos":
        return (x1, y1 - rr, z0 + cc)
    if face == "back":
        return (x1 - cc, y1 - rr, z1)
    if face == "top":
        return (x0 + cc, y1, z1 - rr)
    if face == "bottom":
        return (x0 + cc, y0, z1 - rr)
    raise ValueError(face)


def pack_uvs(bones: list[Bone]):
    cubes = [c for b in bones for c in b.cubes]
    order = sorted(cubes, key=lambda c: (-(c.dims[2] + c.dims[1]), -(2 * (c.dims[0] + c.dims[2]))))
    gap = 1   # a free texel between islands so filtering never bleeds one part into another
    x = y = 0
    row_h = 0
    for c in order:
        w, h, d = c.dims
        fw, fh = 2 * (d + w), d + h
        if x + fw > TEX_W:
            x = 0
            y += row_h + gap
            row_h = 0
        c.uv = (x, y)
        x += fw + gap
        row_h = max(row_h, fh)
    if y + row_h > TEX_H:
        raise SystemExit(f"UV layout needs {y + row_h} rows, texture has {TEX_H}")


# ------------------------------------------------------------------ painters
def hash01(*vals) -> float:
    h = 2166136261
    for v in vals:
        h ^= int(round(v * 8)) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    return (h % 1000) / 1000.0


def grain(p, base: int) -> tuple:
    """Stone tone `base` with a sparse lighter or darker texel for grain."""
    n = hash01(*p)
    if n < 0.08:
        base += 1
    elif n > 0.93:
        base -= 1
    return STONE[int(np.clip(base, 0, len(STONE) - 1))]


def paint_texel(c: Cube, face: str, col: int, row: int, w: int, h: int, variant: str = "base"):
    """The colour of one texel. variant: "base", "tell" (crest spines gold) or "spit" (eyes and jaw red)."""
    p = texel_point(c, face, col, row)
    x, y, z = p
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    side = face in ("xneg", "xpos")
    kind = c.paint
    top_row = y > y1 - 1
    bottom_row = y < y0 + 1

    if kind in ("chest", "hind", "neck"):
        if face == "top":
            if abs(x) < 0.6 and kind != "neck":
                return GOLD[1]                          # gold seam the crest grows from
            return grain(p, 1)
        if face == "bottom":
            return STONE[3]
        if side:
            if top_row:
                return STONE[1]                         # rim light along the back
            if bottom_row:
                return STONE[3]
            if kind == "chest":
                # shoulder facet: a small turquoise triangle at the front of the flank
                if z < z0 + 1 and y1 - 3 < y < y1 - 1:
                    return TURQ[1]
                if z < z0 + 2 and y1 - 3 < y < y1 - 2:
                    return TURQ[2]
            if kind == "hind":
                # haunch facet near the rump
                if z > z1 - 2 and y1 - 2 < y < y1 - 1:
                    return TURQ[2] if z > z1 - 1 else TURQ[1]
            return grain(p, 2)
        if face == "front":
            if kind == "chest" and abs(x) < 1 and y1 - 3 < y < y1 - 1:
                return TURQ[1] if y > y1 - 2 else TURQ[2]   # breast facet
            return STONE[3] if bottom_row else STONE[2]
        if face == "back":
            if kind == "hind" and abs(x) < 0.6 and y > y1 - 2:
                return GOLD[1]                          # gold boss where the tail roots
            return STONE[3] if bottom_row else STONE[2]

    if kind == "skull":
        spit = variant == "spit"
        if side:
            front_col = z < z0 + 1
            mid_col = z0 + 1 <= z < z0 + 2
            if top_row:
                return STONE[3] if (front_col or mid_col) else STONE[1]   # a shaded brow over the eye
            if bottom_row:
                return (RED_JAW if spit else GOLD[1]) if front_col else STONE[3]     # gold jaw line
            if front_col:
                return RED_EYE if spit else EYE
            if mid_col:
                return RED_RIM if spit else EYE_RIM          # the glow trails back
            return STONE[4]                                  # dark corner frames the eye
        if face == "front":
            if abs(x) > 0.6 and abs(y - (y1 - 1.5)) < 0.6:
                return RED_RIM if spit else EYE_RIM          # eyes glint from the front too
            return STONE[2]
        if face == "top":
            return TURQ[1] if abs(x) < 0.6 else STONE[1]      # crystal ridge over the brow
        if face == "bottom":
            return STONE[3]
        return STONE[2]

    if kind == "snout":
        if face == "top":
            return STONE[1]
        if face == "bottom":
            return STONE[3]
        if face == "front":
            return TURQ[2] if top_row else TURQ[3]
        if side:
            if bottom_row and z < z0 + 2:
                if z > z0 + 1:
                    return RED_JAW if variant == "spit" else GOLD[1]   # gold lip line runs on from the jaw
                return STONE[3]
            return STONE[1] if top_row else STONE[2]
        return STONE[2]

    if kind == "ear":
        if face == "front":
            return TURQ[1] if not bottom_row else TURQ[2]     # crystal inner ear
        if face == "top":
            return STONE[0]
        return STONE[1] if top_row else STONE[2]

    if kind == "nose":
        if face == "top":
            return TURQ[1]
        if face in ("bottom", "back"):
            return TURQ[3]
        return TURQ[2] if face in ("front", "xpos") else TURQ[3]

    if kind == "snout_crystal":
        if face == "top":
            return TURQ[0]
        if face == "bottom":
            return TURQ[3]
        return TURQ[1] if face in ("front", "xpos") else TURQ[2]

    if kind == "thigh":
        if face == "top":
            return STONE[1]
        if face == "bottom" or bottom_row:
            return STONE[3]
        return STONE[2] if face in ("xneg", "back") else grain(p, 2)

    if kind == "shin":
        # dark stone "socks" down to a crystal claw at the toe: a paw, not the first cut's gold-banded hoof
        if face == "top":
            return STONE[3]
        if face == "bottom":
            return STONE[4]
        if bottom_row and face == "front":
            return TURQ[1]
        if bottom_row:
            return TURQ[3]
        return STONE[3] if face in ("front", "xpos") else STONE[4]

    if kind in ("spine", "spine_tip", "tail_spine"):
        # glow ramps from a turquoise root to a white tip along the spine (gold on the crest in the tell)
        ramp = GOLD_SPINE if variant == "tell" and kind != "tail_spine" else SPINE
        f = np.clip((y - y0) / max(sy, 1e-6), 0, 1)
        if kind == "spine":
            f = 0.55 * f
        elif kind == "spine_tip":
            f = 0.6 + 0.4 * f
        colr_i = int(np.clip(round((1 - f) * (len(ramp) - 1)), 0, len(ramp) - 1))
        if face == "front":
            colr_i = max(colr_i - 1, 0)               # leading edge catches the light
        if face in ("back", "xneg"):
            colr_i = min(colr_i + 1, len(ramp) - 1)
        if face == "top":
            colr_i = 0
        if face == "bottom":
            colr_i = len(ramp) - 1
        return ramp[colr_i]
    raise ValueError(f"{kind}/{face}")


def paint_texture(bones: list[Bone], variant: str = "base"):
    tex = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    lit = (EYE, EYE_RIM, RED_EYE, RED_RIM, RED_JAW)
    for b in bones:
        for c in b.cubes:
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                for r in range(rh):
                    for col in range(rw):
                        colour = paint_texel(c, face, col, r, rw, rh, variant)
                        tex[rv + r, ru + col, :3] = colour
                        tex[rv + r, ru + col, 3] = 255
                        is_eye = c.paint in ("skull", "snout") and colour in lit
                        if c.glow or is_eye:
                            glow[rv + r, ru + col, :3] = colour
                            glow[rv + r, ru + col, 3] = 255
    return tex, glow


# ------------------------------------------------------------------ geo json
def r4(v):
    return [round(float(a), 4) for a in v]


def geo_json(bones: list[Bone]) -> dict:
    out_bones = []
    for b in bones:
        d = {"name": b.name}
        if b.parent:
            d["parent"] = b.parent
        d["pivot"] = r4(b.pivot)
        if b.rotation and any(b.rotation):
            d["rotation"] = r4(b.rotation)
        if b.cubes:
            cl = []
            for c in b.cubes:
                cd = {"origin": r4(c.origin), "size": r4(c.size), "uv": list(c.uv)}
                if c.inflate:
                    cd["inflate"] = c.inflate
                if c.rotation and any(c.rotation):
                    cd["pivot"] = r4(c.pivot)
                    cd["rotation"] = r4(c.rotation)
                cl.append(cd)
            d["cubes"] = cl
        out_bones.append(d)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.shardling",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 2,
                "visible_bounds_height": 1.5,
                "visible_bounds_offset": [0, 0.75, 0],
            },
            "bones": out_bones,
        }],
    }


# ------------------------------------------------------------------ animations
# Degrees, Bedrock conventions (see the top of the file). Loops are sampled
# densely from sines because GeckoLib interpolates linearly between keys.
# Posed clips are built from whole-body key poses; poses that must stand on
# the ground name their planted legs, and the leg X rotation is solved against
# the real geometry (bedrock_model.plant_leg) so the feet touch y = 0 even
# after the legs are re-proportioned.
SPINE_NAMES = [f"spine_{i}" for i in range(1, len(CREST) + 1)]
GEO_CTX = None   # (desc, bones, order) of the geometry being written; set in main()


def _tkey(t: float) -> str:
    s = f"{t:.4f}".rstrip("0")
    return s + "0" if s.endswith(".") else s


def kf(times, fn, nd=3):
    """{time: [x, y, z]} with values rounded; fn(t) -> (x, y, z)."""
    return {_tkey(t): [round(float(a), nd) for a in fn(t)] for t in times}


def loop_times(length, steps):
    return [length * i / steps for i in range(steps + 1)]


def track(**chans):
    return {k: v for k, v in chans.items() if v}


def anim_idle():
    L = 2.0
    ts = loop_times(L, 16)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * (t / L) + ph)
    bones = {
        # breathing lifts the chest; it never pushes the feet into the ground
        "body": track(position=kf(ts, lambda t: (0, 0.08 * (1 + s(t)), 0)),
                      rotation=kf(ts, lambda t: (-0.8 * s(t), 0, 0))),
        "neck": track(rotation=kf(ts, lambda t: (-1.5 * s(t, 0.6), 0, 0))),
        "head": track(rotation=kf(ts, lambda t: (2.0 * s(t, 1.2), 3.0 * math.sin(math.pi * t / L), 0))),
        "tail": track(rotation=kf(ts, lambda t: (2.0 * s(t, 1.0), 6.0 * s(t, 0.4), 0))),
    }
    for i, n in enumerate(SPINE_NAMES):   # a slow wave runs down the crest
        bones[n] = track(rotation=kf(ts, lambda t, i=i: (4.0 * s(t, -0.7 * i), 0, 1.5 * s(t, 1.3 - 0.5 * i))))
    return {"loop": True, "animation_length": L, "bones": bones}


def anim_walk():
    L = 0.6
    ts = loop_times(L, 12)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * (t / L) + ph)
    swing = 24.0
    bones = {
        # the body dips as the legs spread, so the feet stay down
        "body": track(position=kf(ts, lambda t: (0, 0, 0)),
                      rotation=kf(ts, lambda t: (0, 0, 2.0 * s(t)))),
        "neck": track(rotation=kf(ts, lambda t: (3.0 * s(2 * t, 0.5), 0, 0))),
        "head": track(rotation=kf(ts, lambda t: (-2.5 * s(2 * t, 0.5), 4.0 * s(t), 0))),
        "tail": track(rotation=kf(ts, lambda t: (0, 10.0 * s(t, 1.2), 0))),
        # diagonal pairs move together: front-left with back-right
        "leg_front_left": track(rotation=kf(ts, lambda t: (-swing * s(t), 0, 0))),
        "leg_back_right": track(rotation=kf(ts, lambda t: (-swing * s(t), 0, 0))),
        "leg_front_right": track(rotation=kf(ts, lambda t: (swing * s(t), 0, 0))),
        "leg_back_left": track(rotation=kf(ts, lambda t: (swing * s(t), 0, 0))),
    }
    for i, n in enumerate(SPINE_NAMES):
        bones[n] = track(rotation=kf(ts, lambda t, i=i: (3.0 * s(2 * t, -0.6 * i), 0, 0)))
    clip = {"loop": True, "animation_length": L, "bones": bones}
    ground_body(clip)
    return clip


def ground_body(clip: dict) -> None:
    """Rewrite the body's position keys so the lowest point of the model sits on
    y = 0 at every key (at least one foot is always down in a walk)."""
    desc, bones, order = GEO_CTX
    body = clip["bones"]["body"]
    pos = body.setdefault("position", {})
    keys = sorted({k for ch in clip["bones"].values() for tl in ch.values() for k in tl}, key=float)
    new = {}
    for k in keys:
        t = float(k)
        pose = pose_at(clip, t)
        r, _, sc = pose.get("body", (None, None, None))
        pose["body"] = (r, [0.0, 0.0, 0.0], sc)
        low = lowest_point(desc, bones, order, pose)
        new[k] = [0.0, round(-low, 3), 0.0]
    body["position"] = new


def anim_run():
    """Bounding gait: the front pair and the back pair each move together
    (slightly staggered), the body pitches and leaves the ground mid-stride."""
    L = 0.4
    ts = loop_times(L, 12)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * (t / L) + ph)
    c = lambda t, ph=0.0: math.cos(2 * math.pi * (t / L) + ph)
    bones = {
        "body": track(position=kf(ts, lambda t: (0, 1.1 * max(0.0, s(t)) + 0.35, 0)),
                      rotation=kf(ts, lambda t: (-9.0 * c(t), 0, 0))),
        "neck": track(rotation=kf(ts, lambda t: (10.0 + 6.0 * c(t), 0, 0))),
        "head": track(rotation=kf(ts, lambda t: (-8.0 - 5.0 * c(t), 0, 0))),
        "tail": track(rotation=kf(ts, lambda t: (-12.0 + 8.0 * c(t, 0.8), 0, 0))),
        "leg_front_left": track(rotation=kf(ts, lambda t: (-42.0 * s(t) + 6, 0, 0))),
        "leg_front_right": track(rotation=kf(ts, lambda t: (-42.0 * s(t, -0.35) + 6, 0, 0))),
        "leg_back_left": track(rotation=kf(ts, lambda t: (42.0 * s(t) - 6, 0, 0))),
        "leg_back_right": track(rotation=kf(ts, lambda t: (42.0 * s(t, -0.35) - 6, 0, 0))),
        "spines": track(rotation=kf(ts, lambda t: (-6.0 + 4.0 * c(t), 0, 0))),
    }
    for i, n in enumerate(SPINE_NAMES):
        bones[n] = track(rotation=kf(ts, lambda t, i=i: (-10.0 + 5.0 * s(t, -0.9 - 0.5 * i), 0, 0)))
    return {"loop": True, "animation_length": L, "bones": bones}


# ---- key poses: {bone: {"rotation" | "position" | "scale": [x, y, z]}}
def _fk(pose: dict) -> dict:
    return {b: (ch.get("rotation"), ch.get("position"), ch.get("scale")) for b, ch in pose.items()}


def planted(pose: dict, legs: dict) -> dict:
    """Copy of pose with each named leg's X rotation solved (within its range)
    so the leg's lowest point sits at its target height (default: the ground).
    legs: {leg: (lo, hi)} or {leg: (lo, hi, target_y)}."""
    desc, bones, order = GEO_CTX
    p = copy.deepcopy(pose)
    for leg, spec in legs.items():
        lo, hi = spec[0], spec[1]
        target = spec[2] if len(spec) > 2 else 0.0
        rot = p.setdefault(leg, {}).setdefault("rotation", [0, 0, 0])
        a, err = plant_leg(desc, bones, order, _fk(p), leg, lo, hi, target)
        if err > 0.3:
            print(f"   warning: {leg} cannot reach y={target} within [{lo}, {hi}] (off by {err:.2f})")
        p[leg]["rotation"] = [a, rot[1], rot[2]]
    return p


def keyed(keys: list, subdiv: int = 3) -> dict:
    """keys: [(time, pose, legs spec or None)]. Returns {time: pose} with the
    key poses planted and `subdiv - 1` extra planted frames in every segment,
    so linear interpolation between keys never swings a foot through the
    ground. A leg is only planted in a segment when both ends plant it."""
    frames = {}
    solved = [(t, planted(pose, spec) if spec else pose, spec or {}) for t, pose, spec in keys]
    for (t0, p0, s0), (t1, p1, s1) in zip(solved, solved[1:]):
        frames[t0] = p0
        common = {leg: s0[leg] for leg in s0 if leg in s1}
        for k in range(1, subdiv):
            f = k / subdiv
            spec = {}
            for leg in common:
                a, b = s0[leg], s1[leg]
                ta = a[2] if len(a) > 2 else 0.0
                tb = b[2] if len(b) > 2 else 0.0
                spec[leg] = (min(a[0], b[0]), max(a[1], b[1]), ta + (tb - ta) * f)
            mid = mix(p0, p1, f)
            frames[round(t0 + (t1 - t0) * f, 3)] = planted(mid, spec) if spec else mid
    frames[solved[-1][0]] = solved[-1][1]
    return frames


def mix(a: dict, b: dict, f: float) -> dict:
    """Blend two poses; entries missing from one side count as neutral."""
    out = {}
    for bone in set(a) | set(b):
        out[bone] = {}
        for ch in ("rotation", "position", "scale"):
            n = [1, 1, 1] if ch == "scale" else [0, 0, 0]
            va = a.get(bone, {}).get(ch, n)
            vb = b.get(bone, {}).get(ch, n)
            out[bone][ch] = [x + (y - x) * f for x, y in zip(va, vb)]
    return out


def ease_out(f):
    return 1 - (1 - f) ** 3


CROUCH_LEGS = {"leg_front_left": (-85, 0), "leg_front_right": (-85, 0),
               "leg_back_left": (0, 85), "leg_back_right": (0, 85)}


def crouch_pose():
    """Lunge warning: chest dropped and pitched down, head low, front paws
    forward, hind legs stretched back, the crest standing up and fanned."""
    p = {
        "body": {"position": [0, -1.8, 0.6], "rotation": [6, 0, 0]},
        "neck": {"rotation": [18, 0, 0]},
        "head": {"rotation": [4, 0, 0]},
        "spines": {"rotation": [8, 0, 0]},
        "tail": {"rotation": [-22, 0, 0]},
    }
    for i, n in enumerate(SPINE_NAMES):
        p[n] = {"rotation": [16 - 4 * i, 0, 7 if i % 2 else -7], "scale": [1.1, 1.2, 1.1]}
    return planted(p, CROUCH_LEGS)


def lunge_pose():
    """Mid-leap: stretched long, legs thrown out fore and aft, crest flattened."""
    p = {
        "body": {"position": [0, 0.4, -0.6], "rotation": [-4, 0, 0]},
        "neck": {"rotation": [-8, 0, 0], "position": [0, 0, -0.8]},
        "head": {"rotation": [5, 0, 0]},
        "leg_front_left": {"rotation": [-76, 0, 0]},
        "leg_front_right": {"rotation": [-70, 0, 0]},
        "leg_back_left": {"rotation": [66, 0, 0]},
        "leg_back_right": {"rotation": [72, 0, 0]},
        "spines": {"rotation": [-16, 0, 0]},
        "tail": {"rotation": [-4, 0, 0]},
    }
    for n in SPINE_NAMES:
        p[n] = {"rotation": [-10, 0, 0]}
    return p


def spit_back_pose():
    """Spit warning: rears back, head and neck up, weight on the hind legs."""
    p = {
        "body": {"rotation": [-7, 0, 0], "position": [0, 0.3, 0.6]},
        "neck": {"rotation": [-40, 0, 0], "position": [0, 0.2, 0.8]},
        "head": {"rotation": [14, 0, 0]},
        "tail": {"rotation": [-10, 0, 0]},
        "leg_front_left": {"rotation": [-14, 0, 0]},    # front paws lift, weight on the hind legs
        "leg_front_right": {"rotation": [-10, 0, 0]},
    }
    for n in SPINE_NAMES:
        p[n] = {"rotation": [8, 0, 0], "scale": [1.0, 1.1, 1.0]}
    return planted(p, {"leg_back_left": (-30, 30), "leg_back_right": (-30, 30)})


def from_poses(frames: dict, length: float, loop):
    """frames {time: pose} -> Bedrock bone timelines; still channels are dropped."""
    names = set()
    for pose in frames.values():
        names |= set(pose)
    out = {}
    for n in sorted(names):
        chans = {}
        for ch in ("rotation", "position", "scale"):
            neutral = [1, 1, 1] if ch == "scale" else [0, 0, 0]
            tl = {}
            for t, pose in sorted(frames.items()):
                v = pose.get(n, {}).get(ch, neutral)
                tl[_tkey(t)] = [round(float(a), 3) for a in v]
            if any(v != neutral for v in tl.values()):
                chans[ch] = tl
        if chans:
            out[n] = chans
    return {"loop": loop, "animation_length": length, "bones": out}


def anim_crouch_tell():
    crouch = crouch_pose()
    frames = {0.0: {}}
    for t in (0.05, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5):
        frames[t] = planted(mix({}, crouch, ease_out(t / 0.5)), CROUCH_LEGS)
    frames[0.6] = crouch
    return from_poses(frames, 0.6, "hold_on_last_frame")


def anim_lunge():
    crouch, lunge = crouch_pose(), lunge_pose()
    frames = {0.0: crouch, 0.05: mix(crouch, lunge, 0.55), 0.1: mix(crouch, lunge, 0.88), 0.2: lunge}
    return from_poses(frames, 0.2, "hold_on_last_frame")


def anim_recover():
    """Lands from the lunge nose-first, pushes the body back up before any leg
    swings through vertical (rigid legs would dig in), stumbles sideways on
    one lifted paw, catches itself and settles."""
    free = (-85, 85)
    all4 = {"leg_front_left": free, "leg_front_right": free, "leg_back_left": free, "leg_back_right": free}
    land = {
        "body": {"position": [0, -1.0, 0], "rotation": [9, 0, 4]},
        "neck": {"rotation": [16, 0, 0]},
        "head": {"rotation": [6, 0, -6]},
        "leg_front_left": {"rotation": [-40, 0, -8]},     # front paws brace ahead
        "leg_front_right": {"rotation": [-40, 0, 6]},
        "leg_back_left": {"rotation": [30, 0, 0]},        # hind legs still trailing
        "leg_back_right": {"rotation": [30, 0, 0]},
        "spines": {"rotation": [-8, 0, 0]},
        "tail": {"rotation": [8, 12, 0]},
    }
    push = {
        "body": {"position": [0, -0.3, 0.2], "rotation": [3, 0, -6]},
        "neck": {"rotation": [-4, 0, 0]},
        "head": {"rotation": [-6, 8, 5]},
        "leg_front_left": {"rotation": [-55, 0, -6]},     # this paw is up, flung forward
        "leg_front_right": {"rotation": [-20, 0, 4]},
        "leg_back_left": {"rotation": [18, 0, 0]},
        "leg_back_right": {"rotation": [14, 0, 0]},
        "spines": {"rotation": [6, 0, 0]},
        "tail": {"rotation": [0, -12, 0]},
    }
    catch = {
        "body": {"position": [0, -0.12, 0.1], "rotation": [-2, 0, 4]},
        "neck": {"rotation": [-3, 0, 0]},
        "head": {"rotation": [-2, -5, -3]},
        "leg_front_left": {"rotation": [-10, 0, 0]},
        "leg_front_right": {"rotation": [-6, 0, 0]},
        "leg_back_left": {"rotation": [4, 0, 0]},
        "leg_back_right": {"rotation": [6, 0, 0]},
        "spines": {"rotation": [-3, 0, 0]},
        "tail": {"rotation": [0, 6, 0]},
    }
    lunge = lunge_pose()
    frames = keyed([
        (0.0, lunge, None),
        (0.07, mix(lunge, land, 0.6), {"leg_front_left": free, "leg_front_right": free}),
        (0.12, land, all4),
        (0.26, push, {**all4, "leg_front_left": (*free, 0.9)}),
        (0.42, catch, all4),
        (0.58, mix({}, catch, -0.3), all4),
        (0.8, {}, None),
    ])
    return from_poses(frames, 0.8, False)


def anim_spit_tell():
    back = spit_back_pose()
    frames = {0.0: {}}
    for t in (0.1, 0.2, 0.3):
        frames[t] = mix({}, back, ease_out(t / 0.35))
    frames[0.35] = back
    frames[0.5] = back
    return from_poses(frames, 0.5, "hold_on_last_frame")


def anim_spit():
    back = spit_back_pose()
    thrust = planted({
        "body": {"rotation": [3, 0, 0], "position": [0, -0.2, -0.8]},
        "neck": {"rotation": [10, 0, 0], "position": [0, -0.2, -1.2]},
        "head": {"rotation": [-6, 0, 0]},
        "tail": {"rotation": [6, 0, 0]},
        **{n: {"rotation": [-8, 0, 0]} for n in SPINE_NAMES},
    }, {"leg_front_left": (-20, 30), "leg_front_right": (-20, 30), "leg_back_left": (-40, 10), "leg_back_right": (-40, 10)})
    frames = {0.0: back, 0.05: mix(back, thrust, 0.7), 0.1: thrust, 0.17: mix(thrust, {}, 0.5), 0.25: {}}
    return from_poses(frames, 0.25, False)


def anim_stagger():
    hit = planted({
        "body": {"position": [0, 0.2, 1.4], "rotation": [-12, 6, -8]},
        "neck": {"rotation": [-16, 0, 0]},
        "head": {"rotation": [-10, -14, 10]},
        "leg_front_left": {"rotation": [-26, 0, -10]},  # thrown up by the hit
        "leg_front_right": {"rotation": [0, 0, 12]},
        "spines": {"rotation": [-12, 0, 0]},
        "tail": {"rotation": [14, -14, 0]},
        **{n: {"rotation": [-8 - 2 * i, 0, 6 if i % 2 else -6]} for i, n in enumerate(SPINE_NAMES)},
    }, {"leg_front_right": (-40, 40), "leg_back_left": (-10, 40), "leg_back_right": (-10, 40)})
    wobble = mix({}, hit, -0.3)
    frames = {0.0: {}, 0.06: mix({}, hit, 0.8), 0.12: hit, 0.25: mix({}, hit, 0.35), 0.36: wobble,
              0.44: mix({}, hit, 0.08), 0.5: {}}
    return from_poses(frames, 0.5, False)


def animations() -> dict:
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": anim_idle(),
            "walk": anim_walk(),
            "run": anim_run(),
            "crouch_tell": anim_crouch_tell(),
            "lunge": anim_lunge(),
            "recover": anim_recover(),
            "spit_tell": anim_spit_tell(),
            "spit": anim_spit(),
            "stagger": anim_stagger(),
        },
    }


NUMBER_LIST = re.compile(r"\[\s*(-?[\d.eE+-]+(?:,\s*-?[\d.eE+-]+)*)\s*\]")


def pretty_json(data) -> str:
    """Indented JSON with number lists kept on one line: [x, y, z]."""
    text = json.dumps(data, indent=2)
    return NUMBER_LIST.sub(lambda m: "[" + ", ".join(v.strip() for v in m.group(1).split(",")) + "]", text) + "\n"


# ------------------------------------------------------------------ main
def main():
    global GEO_CTX
    bones = build_model()
    pack_uvs(bones)
    tex, glow = paint_texture(bones)
    geo = geo_json(bones)
    GEO_CTX = split_geo(geo)
    anims = animations()

    geo_path = ASSETS / "geo" / "entity" / "shardling.geo.json"
    anim_path = ASSETS / "animations" / "entity" / "shardling.animation.json"
    geo_path.parent.mkdir(parents=True, exist_ok=True)
    anim_path.parent.mkdir(parents=True, exist_ok=True)
    geo_path.write_text(pretty_json(geo), encoding="utf-8")
    anim_path.write_text(pretty_json(anims), encoding="utf-8")
    p1 = save_png(tex, ASSETS / "textures" / "entity" / "shardling.png")
    p2 = save_png(glow, ASSETS / "textures" / "entity" / "shardling_glowmask.png")
    written = [geo_path, anim_path, p1, p2]
    for variant in ("tell", "spit"):
        vtex, vglow = paint_texture(bones, variant)
        written.append(save_png(vtex, ASSETS / "textures" / "entity" / f"shardling_{variant}.png"))
        written.append(save_png(vglow, ASSETS / "textures" / "entity" / f"shardling_{variant}_glowmask.png"))
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
