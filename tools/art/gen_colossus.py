"""The Prism Colossus, its Prism Shards and its lair's blocks and items, for GeckoLib 4 and the block models.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/prism_colossus.geo.json                 Bedrock geometry 1.12.0, box UV, built at half size
    animations/entity/prism_colossus.animation.json    Bedrock animation 1.8.0
    textures/entity/prism_colossus.png (+ _glowmask)            awake
    textures/entity/prism_colossus_dormant.png (+ _glowmask)    the dim statue
    textures/entity/prism_colossus_cracked.png (+ _glowmask)    phase 2: white cracks
    textures/entity/prism_colossus_white.png                    a Prism Burst building (light added over the model)
    geo/entity/prism_shard.geo.json, animations/entity/prism_shard.animation.json
    textures/entity/prism_shard.png, prism_shard_{red,green,blue}_glowmask.png   one body, three colours of light
    textures/block/: crown_crystal(_lit), crown_crystal_tip(_lit), crown_pillar_side/top(_lit),
                     rising_light, falling_light (animated), gilded_starfall_bricks, prism_glass,
                     prism_altar_side/top, crown_quartz
    textures/item/: guardian_echo, heart_of_a_dying_star
    blockstates/ and models/block/, models/item/ for the lair's blocks and the two items

Conventions as gen_shardling.py: Bedrock model space, 16 units a block before the renderer's scale (the
Colossus is drawn twice its built size, so a unit is an eighth of a block in game), y up, the head faces -Z,
the creature's LEFT is +X. The texture is painted in model space: every texel knows the 3D point it lands on.

The silhouette the design asks for: broad shoulders and two hanging fists over a pillar. Turquoise crystal
with gold veins, one bright facet for an eye, a white prism of light for a chest core, a gold ring round
each wrist. Prismatic but not noisy: big facets, few veins, glints only at the tips. The Colossus owns
turquoise: the lair's crystals, rim and points are pale crown quartz, its floor inlay muted brass.

Glowmasks are light ADDED over the lit model, full bright (GuardianGlowLayer in the game): the core and the
eye burn white in any light, the veins and rings catch light on the shaded side.

Numbers shared with the game (PrismColossus, CrownArena, ColossusMoves): the model stands on the pillar's
stump (1 block over the floor); the fist's middle hangs 0.35 blocks over the stump, 1.875 blocks to the side
and 0.25 ahead; the chest core's middle is 3.4 blocks up and the eye 5.1.

Run:  python tools/art/gen_colossus.py   (then preview_colossus.py)
"""
from __future__ import annotations

import colorsys
import json
import math
import re
from dataclasses import dataclass, field

import numpy as np

from blockart import brick_layout, by_share, fbm, from_ramp, ramp, rng_for, scatter, stamp
from common import ASSETS, TEX, rel, rgb, save_png

# ------------------------------------------------------------------ palette
CRYSTAL = [rgb("#e6fffb"), rgb("#aaf0e7"), rgb("#6dd6ca"), rgb("#3db6ab"), rgb("#259790"), rgb("#1b7671"), rgb("#12524f")]  # light .. dark
DEEP = [rgb("#8fded4"), rgb("#57c1b6"), rgb("#2f9c93"), rgb("#217b76"), rgb("#185c5a"), rgb("#10403f"), rgb("#0b2d2d")]
GOLD = [rgb("#fff4c8"), rgb("#f7dc86"), rgb("#e8b94f"), rgb("#c98f35"), rgb("#9b6a24")]
WHITE = rgb("#ffffff")
CORE = [rgb("#ffffff"), rgb("#fff8e2"), rgb("#ffeab0"), rgb("#bff8f0")]
EYE = rgb("#ffffff")
# (the Prism Shards' colours are light over one turquoise body: SHARD_GLOWS, with the shard below)

# ------------------------------------------------------------------ model data


@dataclass
class Cube:
    origin: tuple
    size: tuple
    paint: str
    rotation: tuple | None = None
    pivot: tuple | None = None
    uv: tuple | None = None
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


# Shared with the game (see the module doc). Units: 1/16 block as built; the game draws the Colossus at x2.
FIST_X = 21.0      # 2.625 blocks at x2
FIST_Y = 4.5       # the fist's middle, 0.56 blocks over the stump
FIST_Z = -3.0      # 0.375 blocks ahead
FIST = 12.0
SHOULDER_X = 19.5  # the pauldrons' middle
ELBOW_BACK = 12.0       # degrees the upper arm swings back at rest
FOREARM_FORWARD = 32.0  # degrees the forearm swings forward from it (net 20 forward)


def box(cx, y0, cz, w, h, d, paint, **kw):
    """A cube by the middle of its foot."""
    return Cube((cx - w / 2, y0, cz - d / 2), (w, h, d), paint, **kw)


def shard(foot, height, width, lean=(0.0, 0.0), paint="shard") -> list[Cube]:
    """A faceted crystal shard standing on `foot` (the middle of its base): a square prism turned 45 degrees about
    its own axis, so its sides show as facets, and a point in two narrowing steps; the whole leaning by `lean`
    (degrees about X, then about Z) round its foot. Every side at least one texel."""
    fx, fy, fz = foot
    rot = (lean[0], 45.0, lean[1])
    body = height * 0.58
    h1 = height * 0.24
    h2 = max(1.0, height - body - h1)
    w1 = max(1.0, width * 0.62)
    w2 = max(1.0, width * 0.3)
    return [
        Cube((fx - width / 2, fy, fz - width / 2), (width, body, width), paint, rotation=rot, pivot=foot, inflate=-0.05),
        Cube((fx - w1 / 2, fy + body, fz - w1 / 2), (w1, h1, w1), paint + "_tip", rotation=rot, pivot=foot, inflate=-0.05),
        Cube((fx - w2 / 2, fy + body + h1, fz - w2 / 2), (w2, h2, w2), paint + "_tip", rotation=rot, pivot=foot),
    ]


def arm(side: int) -> list[Bone]:
    """One arm; side -1 is the creature's right (-X), +1 its left."""
    s = "right" if side < 0 else "left"
    x = side * FIST_X
    sx = side * SHOULDER_X
    shoulder = Bone(f"shoulder_{s}", "torso", (side * 16.0, 34.0, FIST_Z), None, [
        # deep pauldrons (the side view is broad too), a turned block on top, crystal shards on them
        box(sx, 29.0, 0.5, 14, 10, 22, "pauldron"),
        box(sx + side * 0.5, 31.0, 0.5, 14, 9, 14, "pauldron", rotation=(0, 45, 0), pivot=(sx, 31.0, 0.5), inflate=-0.3),
        box(sx, 28.0, 0.5, 12, 3, 20, "crystal_dark"),
        *shard((side * 22.0, 38.5, -3.0), 10, 4.6, lean=(0, side * -30)),
        *shard((side * 17.5, 38.5, 7.0), 9, 4.2, lean=(-32, side * -10)),
    ])
    # the arm bends at the elbow (the upper arm swung back, the forearm forward): no straight column from the side
    upper = Bone(f"arm_upper_{s}", f"shoulder_{s}", (x, 30.0, FIST_Z), (ELBOW_BACK, 0, 0), [
        box(x, 19.0, FIST_Z, 8, 11.5, 8, "crystal"),
    ])
    lower = Bone(f"arm_lower_{s}", f"arm_upper_{s}", (x, 19.5, FIST_Z), (-FOREARM_FORWARD, 0, 0), [
        box(x, 9.5, FIST_Z, 9, 10.5, 9, "crystal"),
        box(x, 13.0, FIST_Z, 7, 5, 10.5, "crystal_dark", rotation=(0, 45, 0), pivot=(x, 15.0, FIST_Z)),
    ])
    ring = Bone(f"wrist_ring_{s}", f"arm_lower_{s}", (x, 12.0, FIST_Z), None, [
        box(x, 11.0, FIST_Z, 11, 2.5, 11, "gold"),
    ])
    fist = Bone(f"fist_{s}", f"arm_lower_{s}", (x, FIST_Y, FIST_Z), None, fist_cubes(x, FIST_Y, FIST_Z))
    # the fist in flight is its own bone (the game places and turns it), with its own gold band at the wrist
    free = Bone(f"free_fist_{s}", "root", (0.0, 0.0, 0.0), None, fist_cubes(0.0, 0.0, 0.0, band=True))
    return [shoulder, upper, lower, ring, fist, free]


def fist_cubes(cx, cy, cz, band=False) -> list[Cube]:
    """A clenched fist: the hand, a row of four big knuckles along its front-bottom edge and a row of four smaller
    finger bumps over them (proud of the face, light on top, dark gaps between; no thumb, which read as letters head
    on), a raised plate on the back of the hand. A fist in flight also wears its own gold cuff at the wrist with the
    stump of the wrist above it (the arm's ring stays on the forearm), so it has a shape from behind too."""
    h = FIST / 2
    cubes = [Cube((cx - h, cy - h + 1, cz - h), (FIST, FIST - 1, FIST), "fist")]
    for k in range(4):
        kx = cx - h + 0.15 + k * 2.95
        cubes.append(Cube((kx, cy - h - 1.2, cz - h - 1.6), (2.75, 5.4, 4.4), "knuckle"))
        cubes.append(Cube((kx + 0.2, cy + 0.2, cz - h - 1.0), (2.35, 3.0, 1.2), "knuckle"))
    cubes.append(Cube((cx - 4.0, cy - 3.5, cz + h - 0.4), (8.0, 7.5, 1.6), "pauldron"))
    if band:
        cubes.append(Cube((cx - h - 0.7, cy + h - 1.2, cz - h - 0.7), (FIST + 1.4, 2.8, FIST + 1.4), "band"))
        cubes.append(Cube((cx - 3.5, cy + h + 1.4, cz - 3.5), (7.0, 2.6, 7.0), "crystal_dark"))
    return cubes


def build_colossus() -> list[Bone]:
    bones = [
        Bone("root", None, (0, 0, 0)),
        Bone("pillar", "root", (0, 0, 0), None, [
            Cube((-8.5, 0, -8.5), (17, 4, 17), "pillar"),
            Cube((-8, 0, -8), (16, 4, 16), "pillar", rotation=(0, 45, 0), pivot=(0, 0, 0)),
            Cube((-6.5, 4, -6.5), (13, 14, 13), "pillar"),
            Cube((-6, 4, -6), (12, 14, 12), "pillar", rotation=(0, 45, 0), pivot=(0, 0, 0)),
            Cube((-7.5, 17, -7.5), (15, 3, 15), "crystal_dark"),
            # crystal shards at the foot, where the body grows out of the stump
            *shard((9.0, 0, -1.0), 7, 3.6, lean=(0, -26)),
            *shard((-9.0, 0, 1.5), 6, 3.4, lean=(0, 28)),
            *shard((0.0, 0, 8.5), 8, 3.6, lean=(-28, 0)),
            *shard((-1.0, 0, -9.0), 5, 3.2, lean=(26, 0)),
        ]),
        Bone("torso", "root", (0, 19, 0), None, [
            Cube((-7.5, 19, -6), (15, 7, 12), "crystal_dark"),        # the waist
            Cube((-11, 24, -7), (22, 10, 16), "crystal"),             # the chest, deep
            Cube((-14.5, 30, -6.5), (29, 7, 16.5), "crystal"),        # the yoke under the pauldrons
            Cube((-11, 24.5, 8), (22, 12, 7), "crystal"),             # the back's mass: broad from the side too
            Cube((-9, 31, -8), (18, 5, 2.5), "crystal_dark"),         # the collar
            Cube((-11.5, 24.5, -8.6), (7, 7.5, 2.2), "crystal"),      # a chest plate each side of the core
            Cube((4.5, 24.5, -8.6), (7, 7.5, 2.2), "crystal"),
            # crystal shards on the back, leaning out
            *shard((0.0, 32.0, 14.5), 15, 6.0, lean=(-44, 0)),
            *shard((-7.0, 29.5, 14.0), 12, 5.0, lean=(-36, 22)),
            *shard((7.0, 29.5, 14.0), 12, 5.0, lean=(-36, -22)),
        ]),
        # a prism of light: three diamonds stepping out, white-hot at the point
        Bone("chest_core", "torso", (0, 29, -7), None, [
            Cube((-3.3, 26.2, -8.9), (6.6, 6.6, 2.9), "core", rotation=(0, 0, 45), pivot=(0, 29.5, -7.5)),
            Cube((-2.2, 27.3, -9.8), (4.4, 4.4, 1.3), "core_mid", rotation=(0, 0, 45), pivot=(0, 29.5, -9.1)),
            Cube((-1.1, 28.4, -10.4), (2.2, 2.2, 1.0), "core_hot", rotation=(0, 0, 45), pivot=(0, 29.5, -9.9)),
        ]),
        Bone("head", "torso", (0, 37, -1), None, [
            Cube((-3.5, 35, -4.5), (7, 3.5, 7), "crystal_dark"),
            Cube((-6.5, 37.5, -7.5), (13, 9, 13), "head", rotation=(0, 45, 0), pivot=(0, 42, -1)),
            Cube((-4.5, 45.5, -5.5), (9, 3, 9), "head", rotation=(0, 45, 0), pivot=(0, 47, -1)),
            *shard((0.0, 47.5, -1.0), 8, 3.0),
            *shard((-4.25, 46.5, -0.5), 6, 2.6, lean=(0, 26)),
            *shard((4.25, 46.5, -0.5), 6, 2.6, lean=(0, -26)),
        ]),
        # the eye: one bright facet, a diamond on the head's front edge
        Bone("eye", "head", (0, 42, -10.3), None, [
            Cube((-2.4, 39.6, -10.9), (4.8, 4.8, 1.4), "eye", rotation=(0, 0, 45), pivot=(0, 42, -10.2)),
            Cube((-1.1, 40.9, -11.5), (2.2, 2.2, 1.0), "eye_hot", rotation=(0, 0, 45), pivot=(0, 42, -11.0)),
        ]),
    ]
    for side in (-1, 1):
        bones += arm(side)
    return bones


# ------------------------------------------------------------------ box UV (GeckoLib 4.9's layout)
def face_rects(c: Cube):
    w, h, d = c.dims
    u, v = c.uv
    return {
        "xneg": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "xpos": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
    }


def texel_point(c: Cube, face: str, col: int, row: int):
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    cc, rr = col + 0.5, row + 0.5
    return {
        "xneg": (x0, y1 - rr, z1 - cc),
        "front": (x0 + cc, y1 - rr, z0),
        "xpos": (x1, y1 - rr, z0 + cc),
        "back": (x1 - cc, y1 - rr, z1),
        "top": (x0 + cc, y1, z1 - rr),
        "bottom": (x0 + cc, y0, z1 - rr),
    }[face]


def pack_uvs(bones: list[Bone], tex_w: int, tex_h: int):
    cubes = [c for b in bones for c in b.cubes]
    order = sorted(cubes, key=lambda c: (-(c.dims[2] + c.dims[1]), -(2 * (c.dims[0] + c.dims[2]))))
    gap = 1
    x = y = 0
    row_h = 0
    for c in order:
        w, h, d = c.dims
        fw, fh = 2 * (d + w), d + h
        if x + fw > tex_w:
            x = 0
            y += row_h + gap
            row_h = 0
        c.uv = (x, y)
        x += fw + gap
        row_h = max(row_h, fh)
    if y + row_h > tex_h:
        raise SystemExit(f"UV layout needs {y + row_h} rows, texture has {tex_h}")


# ------------------------------------------------------------------ painting
def hash01(*vals) -> float:
    h = 2166136261
    for v in vals:
        h ^= int(round(v * 8)) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    return (h % 10000) / 10000.0


FACE_TONE = {"top": 1, "front": 2, "xpos": 2, "xneg": 3, "back": 3, "bottom": 5}
# the parts that carry gold veins
VEINED = ("crystal", "pauldron", "head", "fist")


def vein_field(p) -> float:
    """A slow field through the body; the gold veins run along its zero set (few, long, unbroken lines)."""
    x, y, z = p
    return (math.sin(0.19 * x + 0.12 * y + 0.9) + math.sin(0.15 * z - 0.17 * y + 2.1)
            + 0.4 * math.sin(0.11 * x + 0.23 * y - 0.4))


def vein_mask(c: Cube, face: str, w: int, h: int):
    """Texels of one face the vein field changes sign across (to the next texel right or down): unbroken lines."""
    f = [[vein_field(texel_point(c, face, col, row)) for col in range(w)] for row in range(h)]
    mask = [[False] * w for _ in range(h)]
    for row in range(h):
        for col in range(w):
            here = f[row][col] > 0
            if (col + 1 < w and (f[row][col + 1] > 0) != here) or (row + 1 < h and (f[row + 1][col] > 0) != here):
                mask[row][col] = True
    return mask


def hue_rgb(hue: float, s: float, v: float):
    r, g, b = colorsys.hsv_to_rgb(hue % 1.0, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def colossus_texel(c: Cube, face: str, col: int, row: int, w: int, h: int, veined: bool = False):
    """(colour, added light or None) of one texel of the awake Colossus.

    The glowmask is light added over the lit model, full bright (the game's GuardianGlowLayer draws it additively,
    unshaded, pulled toward the camera): so the core and the eye burn white whatever the sun does, the gold veins
    and rings catch light on the shaded side too, and the shards' points glint.
    """
    p = texel_point(c, face, col, row)
    kind = c.paint
    tone = FACE_TONE[face]
    # a cut-gem facet split: the diagonal halves of each face shade apart
    u = (col + 0.5) / max(1, w)
    v = (row + 0.5) / max(1, h)
    facet = 0 if (u + v) < 1.0 else 1
    if face in ("top", "bottom"):
        facet = 0 if u < v else 1
    edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
    if kind in ("eye_hot", "core_hot"):
        return WHITE, WHITE
    if kind == "eye":
        return (200, 248, 242), ((190, 245, 240) if face == "front" else (80, 170, 165))
    if kind == "core_mid":
        return (252, 246, 228), ((240, 226, 186) if face == "front" else (150, 140, 110))
    if kind == "core":
        if face == "front" and edge:
            # a prismatic rim round the diamond, over a dark base so the colours show
            hue = math.atan2(v - 0.5, u - 0.5) / (2 * math.pi) + 0.5
            return DEEP[3], hue_rgb(hue, 0.8, 0.92)
        if face != "front":
            return CORE[3], (70, 150, 140)
        return CORE[1], (220, 200, 150)
    if kind == "band" and face in ("top", "bottom"):
        # a cuff seen end on: the wrist's dark crystal inside a gold rim
        return (GOLD[2], (40, 30, 10)) if edge else (DEEP[2], None)
    if kind in ("gold", "band"):
        g = GOLD[min(4, max(0, tone - 1 + facet))]
        if edge and face != "bottom":
            g = GOLD[0] if face == "top" else GOLD[1]
        return g, tuple(int(ch * 0.28) for ch in g)
    if kind in ("shard", "shard_tip"):
        # turquoise crystal: the facets shade apart, lit edges where two meet, lighter toward the point
        t = 1.0 - v if face not in ("top", "bottom") else 1.0
        idx = tone + facet - (1 if kind == "shard_tip" else 0) - (1 if t > 0.7 else 0)
        if face not in ("top", "bottom") and (col == 0 or col == w - 1):
            idx -= 1
        idx = max(1, min(5, idx))
        glow = (30, 90, 84) if kind == "shard_tip" and (t > 0.5 or face == "top") else None
        return CRYSTAL[idx], glow
    if kind == "knuckle":
        idx = max(1, min(5, tone + facet - (1 if edge and face != "bottom" else 0)))
        return CRYSTAL[idx], None
    ramp_ = DEEP if kind in ("pillar", "crystal_dark") else CRYSTAL
    idx = tone + facet
    if kind == "pillar":
        # the pillar darkens toward the stump and carries vertical facet lines
        y = p[1]
        idx += int(max(0.0, 8.0 - y) / 5.0)
        if face not in ("top", "bottom") and col % 5 == 0:
            idx += 1
    if kind == "head":
        idx = max(0, idx - 1)
    # (the fists' grooves are gone: with the thumb they read as letters head on)
    if edge and face != "bottom":
        idx -= 1                       # a lit rim on every block's edges: the facets read at a distance
    idx = max(0, min(6, idx))
    colour = ramp_[idx]
    if kind in VEINED and face != "bottom" and veined:
        bright = face == "top" or (v < 0.5 and face != "back")
        return (GOLD[0] if bright else GOLD[1]), (96, 76, 26)
    if kind in VEINED and face != "bottom" and hash01(*p) < 0.002:
        return WHITE, (150, 190, 186)
    return colour, None


def paint_colossus(bones, tex_w, tex_h, variant="base"):
    tex = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    glow = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    crack_rng = np.random.default_rng(1234)
    for b in bones:
        for c in b.cubes:
            cracks = set()
            if variant == "cracked" and c.paint not in ("gold", "band", "eye", "eye_hot", "core", "core_mid", "core_hot"):
                for face, (ru, rv, rw, rh) in face_rects(c).items():
                    if rw < 4 or rh < 4 or crack_rng.random() > 0.55:
                        continue
                    # a jagged crack across the face
                    x = int(crack_rng.integers(0, rw))
                    for y in range(rh):
                        cracks.add((face, x, y))
                        if crack_rng.random() < 0.45:
                            x = max(0, min(rw - 1, x + int(crack_rng.choice([-1, 1]))))
                            cracks.add((face, x, y))
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                veins = vein_mask(c, face, rw, rh) if c.paint in VEINED else None
                for r in range(rh):
                    for col in range(rw):
                        colour, g = colossus_texel(c, face, col, r, rw, rh, veins is not None and veins[r][col])
                        if variant == "dormant":
                            grey = sum(colour) / 3.0
                            colour = tuple(int(0.55 * (0.45 * ch + 0.55 * grey) + 20) for ch in colour)
                            # a dim statue: only the eye and the core keep a faint light
                            g = (34, 40, 38) if c.paint in ("eye", "eye_hot", "core_mid", "core_hot") else None
                        elif variant == "white":
                            # the Prism Burst's light, added over the lit model: near white, the facets still faint in it
                            colour = tuple(int(ch + (255 - ch) * 0.78) for ch in colour)
                            g = None
                        elif variant == "cracked" and (face, col, r) in cracks:
                            colour = WHITE
                            g = (230, 255, 250)
                        tex[rv + r, ru + col, :3] = colour
                        tex[rv + r, ru + col, 3] = 255
                        if g is not None:
                            glow[rv + r, ru + col, :3] = g
                            glow[rv + r, ru + col, 3] = 255
    return tex, glow


# ------------------------------------------------------------------ geo json
def r4(v):
    return [round(float(a), 4) for a in v]


def geo_json(bones, name, tex_w, tex_h, bounds_w, bounds_h):
    out = []
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
        out.append(d)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{name}",
                "texture_width": tex_w,
                "texture_height": tex_h,
                "visible_bounds_width": bounds_w,
                "visible_bounds_height": bounds_h,
                "visible_bounds_offset": [0, bounds_h / 2, 0],
            },
            "bones": out,
        }],
    }


# ------------------------------------------------------------------ animations
def _tkey(t: float) -> str:
    s = f"{t:.4f}".rstrip("0")
    return s + "0" if s.endswith(".") else s


def kf(times, fn, nd=3):
    return {_tkey(t): [round(float(a), nd) for a in fn(t)] for t in times}


def loop_times(length, steps):
    return [length * i / steps for i in range(steps + 1)]


def from_poses(frames: dict, length: float, loop):
    """frames {time: pose}; pose {bone: {channel: [x, y, z]}} -> Bedrock bone timelines."""
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


def mix(a: dict, b: dict, f: float) -> dict:
    out = {}
    for n in set(a) | set(b):
        out[n] = {}
        for ch in ("rotation", "position", "scale"):
            neutral = [1, 1, 1] if ch == "scale" else [0, 0, 0]
            va = a.get(n, {}).get(ch, neutral)
            vb = b.get(n, {}).get(ch, neutral)
            out[n][ch] = [x + (y - x) * f for x, y in zip(va, vb)]
    return out


def ease(f):
    return f * f * (3 - 2 * f)


def arms(rot_r, rot_l, lower_r=(0, 0, 0), lower_l=(0, 0, 0)):
    return {"shoulder_right": {"rotation": list(rot_r)}, "shoulder_left": {"rotation": list(rot_l)},
            "arm_lower_right": {"rotation": list(lower_r)}, "arm_lower_left": {"rotation": list(lower_l)}}


IDLE_POSE = {}
DORMANT_POSE = {
    "torso": {"rotation": [14, 0, 0], "position": [0, -1.5, 0]},
    "head": {"rotation": [24, 0, 0]},
    **arms((6, 0, -9), (6, 0, 9), (-8, 0, 0), (-8, 0, 0)),
}
BROKEN_POSE = {
    "torso": {"rotation": [62, 0, 0], "position": [0, -8, -2]},
    "head": {"rotation": [28, 0, 0]},
    **arms((-40, 0, 6), (-40, 0, -6), (-20, 0, 0), (-20, 0, 0)),
}
ROAR_POSE = {
    "torso": {"rotation": [-12, 0, 0]},
    "head": {"rotation": [-28, 0, 0]},
    **arms((0, 0, 58), (0, 0, -58), (0, 0, 30), (0, 0, -30)),
}
CHARGE_POSE = {
    "torso": {"rotation": [-6, 0, 0]},
    "head": {"rotation": [8, 0, 0]},
    **arms((10, 0, 28), (10, 0, -28), (-18, 0, 0), (-18, 0, 0)),
}
HUNCH_POSE = {
    "torso": {"rotation": [18, 0, 0], "position": [0, -1, 0]},
    "head": {"rotation": [10, 0, 0]},
    **arms((-30, 0, -14), (-30, 0, 14), (-50, 0, 0), (-50, 0, 0)),
}
FLUNG_POSE = {
    "torso": {"rotation": [-8, 0, 0]},
    "head": {"rotation": [-10, 0, 0]},
    **arms((0, 0, 70), (0, 0, -70), (0, 0, 20), (0, 0, -20)),
}


def anim_idle():
    L = 4.0
    ts = loop_times(L, 16)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * t / L + ph)
    return {"loop": True, "animation_length": L, "bones": {
        "torso": {"rotation": kf(ts, lambda t: (1.2 * s(t), 0, 0)), "position": kf(ts, lambda t: (0, 0.25 * (1 + s(t)), 0))},
        "head": {"rotation": kf(ts, lambda t: (-1.5 * s(t, 0.8), 2.5 * s(t * 0.5, 0.3), 0))},
        "shoulder_right": {"rotation": kf(ts, lambda t: (2.0 * s(t, 1.2), 0, -1.5 * s(t, 0.4)))},
        "shoulder_left": {"rotation": kf(ts, lambda t: (2.0 * s(t, 1.9), 0, 1.5 * s(t, 1.1)))},
    }}


def anim_dormant():
    return from_poses({0.0: DORMANT_POSE, 1.0: DORMANT_POSE}, 1.0, True)


def anim_intro():
    """10 s: still, trembling, the arms torn out of the pillar, the roar (4.5 s), then rising to stand."""
    frames = {0.0: DORMANT_POSE, 2.8: DORMANT_POSE}
    for i, t in enumerate((3.0, 3.2, 3.4, 3.6, 3.8)):
        j = 1.5 if i % 2 == 0 else -1.5
        p = mix(DORMANT_POSE, {}, 0.05)
        p["torso"]["rotation"][2] = j
        frames[t] = p
    tear = {"torso": {"rotation": [4, 0, 0]}, "head": {"rotation": [6, 0, 0]}, **arms((-25, 0, 45), (-25, 0, -45), (-30, 0, 0), (-30, 0, 0))}
    frames[4.2] = tear
    frames[4.6] = ROAR_POSE
    frames[6.0] = mix(ROAR_POSE, {}, 0.1)
    frames[7.0] = mix(ROAR_POSE, {}, 0.6)
    frames[8.5] = {}
    frames[10.0] = {}
    return from_poses(frames, 10.0, False)


def anim_charge():
    return from_poses({0.0: {}, 0.5: mix({}, CHARGE_POSE, 0.7), 2.0: CHARGE_POSE}, 2.0, "hold_on_last_frame")


def anim_fire():
    L = 0.5
    ts = loop_times(L, 6)
    base = CHARGE_POSE
    return {"loop": True, "animation_length": L, "bones": {
        "torso": {"rotation": kf(ts, lambda t: (base["torso"]["rotation"][0] + 1.2 * math.sin(2 * math.pi * t / L * 2), 0, 0))},
        "head": {"rotation": kf(ts, lambda t: (base["head"]["rotation"][0], 0, 0.8 * math.sin(2 * math.pi * t / L)))},
        "shoulder_right": {"rotation": kf(ts, lambda t: base["shoulder_right"]["rotation"])},
        "shoulder_left": {"rotation": kf(ts, lambda t: base["shoulder_left"]["rotation"])},
        "arm_lower_right": {"rotation": kf(ts, lambda t: base["arm_lower_right"]["rotation"])},
        "arm_lower_left": {"rotation": kf(ts, lambda t: base["arm_lower_left"]["rotation"])},
    }}


def anim_burst_tell():
    frames = {0.0: {}, 0.3: HUNCH_POSE}
    for i, t in enumerate((0.4, 0.5, 0.6, 0.7, 0.8)):
        p = mix(HUNCH_POSE, {}, 0.0)
        p["torso"]["rotation"][2] = 1.5 if i % 2 == 0 else -1.5
        frames[t] = p
    return from_poses(frames, 0.8, "hold_on_last_frame")


def anim_burst():
    return from_poses({0.0: HUNCH_POSE, 0.1: FLUNG_POSE, 0.35: FLUNG_POSE, 0.6: {}}, 0.6, False)


def anim_break():
    frames = {0.0: {}, 0.15: mix({}, BROKEN_POSE, 0.5), 0.35: mix({}, BROKEN_POSE, 1.08), 0.6: BROKEN_POSE}
    return from_poses(frames, 0.6, False)


def anim_broken():
    L = 1.6
    ts = loop_times(L, 8)
    b = BROKEN_POSE
    return {"loop": True, "animation_length": L, "bones": {
        "torso": {"rotation": kf(ts, lambda t: (b["torso"]["rotation"][0] + 1.5 * math.sin(2 * math.pi * t / L), 0, 0)),
                  "position": kf(ts, lambda t: b["torso"]["position"])},
        "head": {"rotation": kf(ts, lambda t: b["head"]["rotation"])},
        "shoulder_right": {"rotation": kf(ts, lambda t: b["shoulder_right"]["rotation"])},
        "shoulder_left": {"rotation": kf(ts, lambda t: b["shoulder_left"]["rotation"])},
        "arm_lower_right": {"rotation": kf(ts, lambda t: b["arm_lower_right"]["rotation"])},
        "arm_lower_left": {"rotation": kf(ts, lambda t: b["arm_lower_left"]["rotation"])},
    }}


def anim_rise():
    return from_poses({0.0: BROKEN_POSE, 0.6: mix(BROKEN_POSE, {}, 0.6), 1.5: {}}, 1.5, False)


def anim_fracture():
    frames = {0.0: {}, 0.4: ROAR_POSE}
    for i in range(10):
        t = 0.5 + i * 0.2
        p = mix(ROAR_POSE, {}, 0.0)
        p["torso"]["rotation"][2] = 2.0 if i % 2 == 0 else -2.0
        p["head"]["rotation"][1] = 3.0 if i % 2 else -3.0
        frames[round(t, 2)] = p
    frames[2.6] = ROAR_POSE
    frames[3.0] = {}
    return from_poses(frames, 3.0, False)


def anim_shattered():
    return from_poses({0.0: {"torso": {"scale": [0.01, 0.01, 0.01]}}, 1.0: {"torso": {"scale": [0.01, 0.01, 0.01]}}}, 1.0, True)


def anim_reform():
    small = {"torso": {"scale": [0.2, 0.2, 0.2], "rotation": [0, 180, 0]}}
    return from_poses({0.0: small, 1.0: small, 2.0: {"torso": {"scale": [1.08, 1.08, 1.08]}}, 2.5: {}}, 2.5, False)


def anim_dying():
    sunk = {"pillar": {"position": [0, -6, 0], "scale": [0.85, 0.7, 0.85]}}
    return from_poses({0.0: {}, 3.0: sunk}, 3.0, "hold_on_last_frame")


def colossus_animations():
    return {"format_version": "1.8.0", "animations": {
        "idle": anim_idle(), "dormant": anim_dormant(), "intro": anim_intro(),
        "refraction_charge": anim_charge(), "refraction_fire": anim_fire(),
        "burst_tell": anim_burst_tell(), "burst": anim_burst(),
        "break": anim_break(), "broken": anim_broken(), "rise": anim_rise(),
        "fracture": anim_fracture(), "shattered": anim_shattered(), "reform": anim_reform(), "dying": anim_dying(),
    }}


# ------------------------------------------------------------------ the Prism Shard
# The shards' own colours, added as light: each burns red, green or blue over the same turquoise crystal body.
SHARD_GLOWS = {"red": (255, 52, 40), "green": (60, 255, 96), "blue": (64, 116, 255)}


def build_shard() -> list[Bone]:
    """A Prism Shard: a low crystal beast of the Colossus's own turquoise, crystalline rather than cute. Built at
    half size (the game draws it twice as big: a block and a half to the tips of its shards, two blocks long): a
    long faceted body with a ridge, a wedge of a head whose only face is a slit of light, four crystal-prism legs,
    and a crest of crystal shards along its back that burn with its colour (the glowmask)."""
    bones = [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 5.5, 0), None, [
            Cube((-3.0, 3.5, -5.0), (6, 4, 10), "shard"),
            Cube((-1.8, 7.5, -4.2), (3.6, 1, 8.5), "shard"),
        ]),
        # the head: a spear of crystal thrust forward, its point burning; no face to speak of
        Bone("head", "body", (0, 6.0, -5.0), None, [
            *shard((0.0, 5.8, -4.0), 7.0, 4.0, lean=(90, 0)),
            Cube((-2.2, 5.3, -7.2), (4.4, 1, 1), "slit"),
            *shard((0.0, 7.0, -6.0), 3.0, 1.6, lean=(-50, 0), paint="spine"),
        ]),
        Bone("crest", "body", (0, 8.5, 0), None, [
            *shard((0.0, 8.2, -2.5), 4.0, 2.4, lean=(-12, 0), paint="spine"),
            *shard((0.0, 8.2, 0.8), 3.6, 2.2, lean=(-28, 0), paint="spine"),
            *shard((0.0, 8.0, 3.8), 2.8, 1.8, lean=(-50, 0), paint="spine"),
            *shard((-2.2, 7.4, -0.5), 3.0, 1.6, lean=(-15, 32), paint="spine"),
            *shard((2.2, 7.4, -0.5), 3.0, 1.6, lean=(-15, -32), paint="spine"),
        ]),
    ]
    for side, x in (("left", 2.4), ("right", -2.4)):
        for end, z in (("front", -3.2), ("back", 3.2)):
            bones.append(Bone(f"leg_{end}_{side}", "body", (x, 4.0, z), None, [
                Cube((x - 0.9, 0.0, z - 0.9), (1.8, 4.2, 1.8), "leg", rotation=(0, 45, 0), pivot=(x, 4.0, z))]))
    return bones


def shard_texel(c: Cube, face, col, row, w, h, glow_rgb):
    """(colour, added light or None) of one texel of a Prism Shard: turquoise crystal like the Colossus, its crest
    and the slit of its face dark so its own colour, added over them, burns pure."""
    tone = FACE_TONE[face]
    u = (col + 0.5) / max(1, w)
    v = (row + 0.5) / max(1, h)
    facet = 0 if (u + v) < 1.0 else 1
    if face in ("top", "bottom"):
        facet = 0 if u < v else 1
    edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
    burn = tuple(int(ch) for ch in glow_rgb)
    if c.paint == "slit":
        return DEEP[6], burn
    if c.paint in ("spine", "spine_tip"):
        # the burning crest: a dark crystal core, its colour at full over it, white-hot toward the points
        t = 1.0 - v if face not in ("top", "bottom") else 1.0
        hot = 0.45 if c.paint == "spine_tip" and (t > 0.5 or face == "top") else 0.0
        light = tuple(int(min(255, ch + (255 - ch) * hot)) for ch in burn)
        return DEEP[6 - (1 if face == "top" else 0)], light
    if c.paint == "leg":
        idx = max(2, min(6, tone + 1 + facet))
        return DEEP[idx], (tuple(int(ch * 0.5) for ch in burn) if v > 0.8 and face != "top" else None)
    if c.paint == "shard_tip":
        # the head's point: crystal, burning with the colour toward its tip
        t = 1.0 - v if face not in ("top", "bottom") else 1.0
        idx = max(1, min(5, tone + facet))
        return CRYSTAL[idx], (burn if t > 0.55 or face == "top" else None)
    idx = tone + facet + 1 - (1 if edge and face != "bottom" else 0)
    colour = CRYSTAL[max(2, min(6, idx))]
    # the colour seeps along the body's edges
    return colour, (tuple(int(ch * 0.22) for ch in burn) if edge and face in ("top", "front", "xpos", "xneg") else None)


def paint_shard(bones, tex_w, tex_h, glow_rgb):
    tex = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    glow = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    for b in bones:
        for c in b.cubes:
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                for r in range(rh):
                    for col in range(rw):
                        colour, g = shard_texel(c, face, col, r, rw, rh, glow_rgb)
                        tex[rv + r, ru + col, :3] = colour
                        tex[rv + r, ru + col, 3] = 255
                        if g is not None:
                            glow[rv + r, ru + col, :3] = g
                            glow[rv + r, ru + col, 3] = 255
    return tex, glow


def shard_animations():
    L = 1.6
    ts = loop_times(L, 8)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * t / L + ph)
    idle = {"loop": True, "animation_length": L, "bones": {
        "body": {"position": kf(ts, lambda t: (0, 0.1 * (1 + s(t)), 0))},
        "head": {"rotation": kf(ts, lambda t: (2 * s(t, 0.7), 4 * s(t * 0.5), 0))},
        "crest": {"rotation": kf(ts, lambda t: (3 * s(t, 1.3), 0, 0))},
    }}
    R = 0.4
    tr = loop_times(R, 8)
    q = lambda t, ph=0.0: math.sin(2 * math.pi * t / R + ph)
    run = {"loop": True, "animation_length": R, "bones": {
        "body": {"position": kf(tr, lambda t: (0, 0.5 * abs(q(t)), 0)), "rotation": kf(tr, lambda t: (3 * q(t, 1.5), 0, 0))},
        "leg_front_left": {"rotation": kf(tr, lambda t: (45 * q(t), 0, 0))},
        "leg_front_right": {"rotation": kf(tr, lambda t: (45 * q(t, math.pi * 0.8), 0, 0))},
        "leg_back_left": {"rotation": kf(tr, lambda t: (-45 * q(t, math.pi * 0.2), 0, 0))},
        "leg_back_right": {"rotation": kf(tr, lambda t: (-45 * q(t, math.pi), 0, 0))},
        "head": {"rotation": kf(tr, lambda t: (-4 * q(t, 0.5), 0, 0))},
    }}
    crouch = {"body": {"position": [0, -1.5, 1], "rotation": [10, 0, 0]}, "head": {"rotation": [-14, 0, 0]},
              "leg_front_left": {"rotation": [-30, 0, 0]}, "leg_front_right": {"rotation": [-30, 0, 0]},
              "leg_back_left": {"rotation": [35, 0, 0]}, "leg_back_right": {"rotation": [35, 0, 0]},
              "crest": {"rotation": [20, 0, 0]}}
    stretch = {"body": {"position": [0, 1, -1], "rotation": [-12, 0, 0]}, "head": {"rotation": [6, 0, 0]},
               "leg_front_left": {"rotation": [-70, 0, 0]}, "leg_front_right": {"rotation": [-70, 0, 0]},
               "leg_back_left": {"rotation": [60, 0, 0]}, "leg_back_right": {"rotation": [60, 0, 0]},
               "crest": {"rotation": [-15, 0, 0]}}
    tell = from_poses({0.0: {}, 0.3: crouch, 0.5: crouch}, 0.5, "hold_on_last_frame")
    lunge = from_poses({0.0: crouch, 0.08: stretch, 0.3: stretch}, 0.3, "hold_on_last_frame")
    recover = from_poses({0.0: stretch, 0.3: mix(stretch, {}, 0.7), 0.6: {}}, 0.6, False)
    shake = {}
    for i in range(6):
        shake[round(i * 0.1, 2)] = {"body": {"rotation": [0, 0, 12 if i % 2 == 0 else -12]}, "head": {"rotation": [15, 0, 0]}}
    shake[0.6] = {}
    stagger = from_poses(shake, 0.6, False)
    curled = {"body": {"rotation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]}, "leg_front_left": {"rotation": [-80, 0, 0]},
              "leg_front_right": {"rotation": [-80, 0, 0]}, "leg_back_left": {"rotation": [80, 0, 0]},
              "leg_back_right": {"rotation": [80, 0, 0]}, "head": {"rotation": [30, 0, 0]}}
    fly = from_poses({0.0: curled, 0.4: curled}, 0.4, True)
    return {"format_version": "1.8.0", "animations": {
        "idle": idle, "run": run, "tell": tell, "lunge": lunge, "recover": recover, "stagger": stagger, "fly": fly}}


# ------------------------------------------------------------------ block and item textures (16x16)
QUARTZ = ramp("#12524f", "#1b7671", "#259790", "#3db6ab", "#6dd6ca", "#aaf0e7", "#e6fffb")
# the crown's own stone: near-white quartz with a faint cool tint (the Colossus owns turquoise), dark to light
PALE = ramp("#8e99a6", "#a8b2bd", "#c1c9d1", "#d6dce2", "#e5e9ed", "#f0f3f6", "#fafbfd")
# a crown crystal holding light: saturated gold to white (gold, as the telegraphs use it: it can be turned)
LIT = ramp("#d99a1c", "#ffc43a", "#ffdf7a", "#fff3c4", "#ffffff")
STARFALL = ramp("#a79f90", "#c2bbac", "#d6d0c3", "#e4dfd4", "#efece3")
GOLDR = ramp("#9b6a24", "#c98f35", "#e8b94f", "#f7dc86", "#fff4c8")
# the floor's inlay: muted brass, so the slam's ring is the only bright gold underfoot
BRASS = ramp("#6f5f3d", "#86744b", "#9e895a", "#b19c6a", "#c4b07e")


def crystal_face(name, lit: bool):
    """A big-facet crystal block face in pale crown quartz: two bevelled diagonal facets and a lit rim, calm (no
    speckle). Lit, it holds gold-white light."""
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., 3] = 255
    ys, xs = np.mgrid[0:16, 0:16]
    facet = (xs + ys) < 16
    idx = np.where(facet, 4, 3)
    idx = np.where((xs < 1) | (ys < 1), 5, idx)
    idx = np.where((xs > 14) | (ys > 14), 1, idx)
    idx = np.where(np.abs(xs + ys - 15.5) < 0.6, 6, idx)
    img[..., :3] = np.array(PALE)[idx]
    if lit:
        img[..., :3] = np.array(LIT)[np.clip(idx - 2, 0, 4)]
    return img


def pillar_side(lit: bool):
    """The stump and the lamps: pale quartz framing the prism motif, a diamond that burns gold-white when lit and
    is a brass outline when dim."""
    img = crown_quartz()
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    d = np.abs(xs - 8) + np.abs(ys - 8)
    if lit:
        img[d <= 6.2, :3] = LIT[1]
        img[d <= 4.6, :3] = LIT[2]
        img[d <= 3.0, :3] = LIT[3]
        img[d <= 1.5, :3] = LIT[4]
        img[(d > 6.2) & (d <= 7.2), :3] = BRASS[4]
    else:
        img[(d > 5.2) & (d <= 6.2), :3] = BRASS[2]
        img[d <= 1.5, :3] = BRASS[3]
    return img


def pillar_top(lit: bool):
    img = crown_quartz()
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    d = np.hypot(xs - 8, ys - 8)
    if lit:
        ramp_ = np.array(LIT)
        inner = d < 6.0
        rings = np.clip((d / 1.3).astype(int), 0, len(ramp_) - 1)
        img[inner, :3] = ramp_[(len(ramp_) - 1) - rings][inner]    # brightest in the middle
    else:
        img[(d > 5.0) & (d < 6.0), :3] = BRASS[2]
    return img


def crown_quartz():
    """Crown Quartz: the rim, the crown's points and the lift's gates. Pale and calm, read as crystal rather than
    stone: two broad facets split on the diagonal with a lit seam, a soft bevel, nothing that competes with the
    Colossus."""
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., 3] = 255
    ys, xs = np.mgrid[0:16, 0:16]
    idx = np.where(xs + ys < 15, 5, 4)
    idx[np.abs(xs + ys - 15) < 0.6] = 6
    idx[(xs < 1) | (ys < 1)] = 6
    idx[(xs > 14) | (ys > 14)] = 3
    img[..., :3] = np.array(PALE)[idx]
    return img


def light_frames(rising: bool, frames=16):
    """A column of light: bright streaks drifting up (or down) over a soft translucent glow; one frame per row of 16."""
    out = np.zeros((16 * frames, 16, 4), np.uint8)
    r = rng_for("rising_light" if rising else "falling_light")
    streaks = [(int(r.integers(0, 16)), float(r.random() * 16), 3 + int(r.integers(0, 5))) for _ in range(9)]
    base = np.array((255, 236, 170) if rising else (170, 225, 255), float)
    hot = np.array((255, 255, 255), float)
    for f in range(frames):
        img = np.zeros((16, 16, 4), float)
        xs = np.arange(16)
        edge = np.minimum(xs, 15 - xs) / 7.5
        img[..., :3] = base
        img[..., 3] = (60 + 60 * edge)[None, :]
        for x, y0, length in streaks:
            shift = (f * 16.0 / frames) * (-1 if rising else 1)
            for k in range(length):
                y = int((y0 + shift + k) % 16)
                a = 1.0 - k / length if rising else k / length
                img[y, x, :3] = hot * a + base * (1 - a)
                img[y, x, 3] = max(img[y, x, 3], 120 + 120 * a)
        out[f * 16:(f + 1) * 16] = np.clip(img, 0, 255).astype(np.uint8)
    return out


def gilded_bricks():
    from gen_blocks_reach import starfall_stone_bricks
    img = starfall_stone_bricks().copy()
    _, mortar, _, _ = brick_layout(4, 8, [0, 4, 2, 6])
    img[mortar, :3] = BRASS[2]
    ys, xs = np.nonzero(mortar)
    for y, x in zip(ys, xs):
        if (x + y) % 5 == 0:
            img[y, x, :3] = BRASS[4]
        elif y % 4 == 3:
            img[y, x, :3] = BRASS[1]
    return img


def prism_glass():
    """Nearly clear glass for the gaps of the rim's battlements: a faint cool tint, a thin lit edge on two sides and
    one glint, no frame to read as a handrail."""
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., :3] = (236, 244, 250)
    img[..., 3] = 26
    img[0, :, :3] = img[:, 0, :3] = (255, 255, 255)
    img[0, :, 3] = img[:, 0, 3] = 70
    for k in range(3, 7):
        img[k, 10 - k, :3] = (255, 255, 255)
        img[k, 10 - k, 3] = 110
    return img


def altar_side():
    from gen_blocks_reach import starfall_stone_bricks
    img = starfall_stone_bricks().copy()
    img[5:7, :, :3] = GOLDR[2]
    img[5, :, :3] = GOLDR[3]
    for x in range(2, 16, 4):
        img[9:12, x, :3] = QUARTZ[4]
        img[9, x, :3] = QUARTZ[6]
    return img


def altar_top():
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., 3] = 255
    img[..., :3] = STARFALL[3]
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    d = np.hypot(xs - 8, ys - 8)
    img[(d > 5.5) & (d < 6.6), :3] = GOLDR[2]
    img[d < 3.2, :3] = QUARTZ[4]
    img[d < 1.6, :3] = QUARTZ[6]
    img[(xs < 1) | (ys < 1), :3] = STARFALL[4]
    img[(xs > 15) | (ys > 15), :3] = STARFALL[1]
    return img


def guardian_echo_icon():
    """A mote of prismatic light held in a thin gold ring: the Echo that wakes a guardian again."""
    img = np.zeros((16, 16, 4), np.uint8)
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    d = np.hypot(xs - 8, ys - 8)
    ring = (d > 5.4) & (d < 6.6)
    img[ring] = (*GOLDR[2], 255)
    img[ring & (ys < 8)] = (*GOLDR[3], 255)
    img[ring & (xs + ys > 19)] = (*GOLDR[1], 255)
    core = d < 3.6
    img[core] = (*QUARTZ[4], 255)
    img[d < 2.4] = (*QUARTZ[5], 255)
    img[d < 1.2] = (255, 255, 255, 255)
    for (x, y), c in (((8, 2), (255, 255, 255)), ((8, 13), (255, 255, 255)), ((2, 8), (255, 255, 255)), ((13, 8), (255, 255, 255))):
        img[y, x] = (*c, 255)
    img[4, 11] = (*QUARTZ[6], 255)
    img[11, 4] = (*QUARTZ[6], 255)
    # rainbow fringe on the core's rim
    for (x, y), c in (((6, 6), (255, 120, 110)), ((10, 6), (255, 230, 120)), ((10, 10), (120, 230, 255)), ((6, 10), (170, 140, 255))):
        img[y, x] = (*c, 255)
    return img


def heart_icon():
    """The Heart of a Dying Star: a gold chain in a loop and a dim red-gold star heart hanging from it."""
    img = np.zeros((16, 16, 4), np.uint8)
    chain = [(4, 1), (5, 0), (6, 0), (7, 0), (8, 0), (9, 0), (10, 0), (11, 1), (12, 2), (12, 3), (11, 4), (3, 2), (3, 3), (4, 4)]
    for i, (x, y) in enumerate(chain):
        img[y, x] = (*(GOLDR[3] if i % 2 else GOLDR[1]), 255)
    img[5, 5] = (*GOLDR[2], 255)
    img[5, 10] = (*GOLDR[2], 255)
    img[6, 6] = (*GOLDR[1], 255)
    img[6, 9] = (*GOLDR[1], 255)
    star = [
        "......X.....",
        ".....XXX....",
        ".XXXXXOXXXX.",
        "..XXOOOOXX..",
        "...XOOOOX...",
        "..XXOYYOXX..",
        "..XX.XX.XX..",
        ".X.......X..",
    ]
    colours = {"X": (184, 64, 40), "O": (255, 150, 70), "Y": (255, 240, 200)}
    for r, row in enumerate(star):
        for cidx, ch in enumerate(row):
            if ch in colours:
                img[7 + r, 2 + cidx] = (*colours[ch], 255)
    img[9, 7] = (255, 255, 255, 255)
    return img


# ------------------------------------------------------------------ block models and states
def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))
    return path


def full_cube(texture_all=None, faces=None, emissive=False, render_type=None, shade=True):
    tex = faces or {"all": texture_all}
    names = {f: (tex.get(f) or tex.get("side") or tex.get("all")) for f in ("north", "south", "east", "west")}
    names["up"] = tex.get("up") or tex.get("end") or tex.get("all")
    names["down"] = tex.get("down") or tex.get("end") or tex.get("all")
    element = {"from": [0, 0, 0], "to": [16, 16, 16], "shade": shade, "faces": {
        f: {"uv": [0, 0, 16, 16], "texture": "#" + f, "cullface": f} for f in ("north", "south", "east", "west", "up", "down")}}
    if emissive:
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    model = {"parent": "minecraft:block/block", "textures": {**{f: names[f] for f in names}, "particle": names["north"]},
             "elements": [element]}
    if render_type:
        model["render_type"] = render_type
    return model


def tip_model(texture, emissive):
    """The top block of a crown crystal: steps up to the point where the four columns meet (the south-east corner)."""
    boxes = [([2, 0, 2], [16, 6, 16]), ([6, 6, 6], [16, 11, 16]), ([11, 11, 11], [16, 15, 16])]
    elements = []
    for f, t in boxes:
        e = {"from": f, "to": t, "faces": {}}
        for face in ("north", "south", "east", "west", "up", "down"):
            e["faces"][face] = {"texture": "#crystal"}
        if emissive:
            e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        elements.append(e)
    return {"parent": "minecraft:block/block", "textures": {"crystal": texture, "particle": texture}, "elements": elements}


def altar_model():
    def box(f, t, side, top, emissive=False):
        e = {"from": f, "to": t, "faces": {
            "north": {"texture": side}, "south": {"texture": side}, "east": {"texture": side}, "west": {"texture": side},
            "up": {"texture": top}, "down": {"texture": top}}}
        if emissive:
            e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        return e
    return {"parent": "minecraft:block/block",
            "textures": {"side": "cosmicbreach:block/prism_altar_side", "top": "cosmicbreach:block/prism_altar_top",
                         "crystal": "cosmicbreach:block/crown_crystal_lit", "particle": "cosmicbreach:block/prism_altar_side"},
            "elements": [box([1, 0, 1], [15, 3, 15], "#side", "#top"), box([4, 3, 4], [12, 11, 12], "#side", "#top"),
                         box([2, 11, 2], [14, 14, 14], "#side", "#top"), box([6, 14, 6], [10, 19, 10], "#crystal", "#crystal", True)]}


def write_block_assets():
    models = ASSETS / "models" / "block"
    states = ASSETS / "blockstates"
    written = []
    b = "cosmicbreach:block/"
    written.append(write_json(models / "crown_crystal.json", full_cube(b + "crown_crystal")))
    written.append(write_json(models / "crown_crystal_lit.json", full_cube(b + "crown_crystal_lit", emissive=True)))
    written.append(write_json(models / "crown_crystal_tip.json", tip_model(b + "crown_crystal", False)))
    written.append(write_json(models / "crown_crystal_tip_lit.json", tip_model(b + "crown_crystal_lit", True)))
    variants = {}
    for lit in (False, True):
        for section in range(4):
            for q in range(4):
                name = ("crown_crystal_tip" if section == 3 else "crown_crystal") + ("_lit" if lit else "")
                v = {"model": b + name}
                if section == 3:
                    y = {0: 0, 1: 90, 2: 270, 3: 180}[q]
                    if y:
                        v["y"] = y
                variants[f"lit={str(lit).lower()},quadrant={q},section={section}"] = v
    written.append(write_json(states / "crown_crystal.json", {"variants": variants}))
    for lit in (False, True):
        sfx = "_lit" if lit else ""
        written.append(write_json(models / f"crown_pillar{sfx}.json",
                                  full_cube(faces={"side": b + f"crown_pillar_side{sfx}", "end": b + f"crown_pillar_top{sfx}"},
                                            emissive=lit)))
    written.append(write_json(states / "crown_pillar.json", {"variants": {
        "lit=false": {"model": b + "crown_pillar"}, "lit=true": {"model": b + "crown_pillar_lit"}}}))
    for name in ("rising_light", "falling_light"):
        m = full_cube(b + name, emissive=True, render_type="minecraft:translucent", shade=False)
        for face in m["elements"][0]["faces"].values():
            face.pop("cullface", None)
        written.append(write_json(models / f"{name}.json", m))
        written.append(write_json(states / f"{name}.json", {"variants": {"": {"model": b + name}}}))
    written.append(write_json(models / "crown_quartz.json", full_cube(b + "crown_quartz")))
    written.append(write_json(states / "crown_quartz.json", {"variants": {"": {"model": b + "crown_quartz"}}}))
    written.append(write_json(models / "gilded_starfall_bricks.json", full_cube(b + "gilded_starfall_bricks")))
    written.append(write_json(states / "gilded_starfall_bricks.json", {"variants": {"": {"model": b + "gilded_starfall_bricks"}}}))
    glass = full_cube(b + "prism_glass", render_type="minecraft:translucent")
    written.append(write_json(models / "prism_glass.json", glass))
    written.append(write_json(states / "prism_glass.json", {"variants": {"": {"model": b + "prism_glass"}}}))
    written.append(write_json(models / "prism_altar.json", altar_model()))
    written.append(write_json(states / "prism_altar.json", {"variants": {"": {"model": b + "prism_altar"}}}))
    items = ASSETS / "models" / "item"
    for name in ("guardian_echo", "heart_of_a_dying_star"):
        written.append(write_json(items / f"{name}.json", {"parent": "minecraft:item/generated",
                                                           "textures": {"layer0": f"cosmicbreach:item/{name}"}}))
    for name in ("gilded_starfall_bricks", "prism_glass", "crown_quartz"):
        written.append(write_json(items / f"{name}.json", {"parent": f"cosmicbreach:block/{name}"}))
    return written


# ------------------------------------------------------------------ main
NUMBER_LIST = re.compile(r"\[\s*(-?[\d.eE+-]+(?:,\s*-?[\d.eE+-]+)*)\s*\]")


def pretty_json(data) -> str:
    text = json.dumps(data, indent=2)
    return NUMBER_LIST.sub(lambda m: "[" + ", ".join(v.strip() for v in m.group(1).split(",")) + "]", text) + "\n"


def write_text(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode("utf-8"))
    return path


def main():
    written = []
    # the Colossus
    bones = build_colossus()
    pack_uvs(bones, 256, 256)
    geo = geo_json(bones, "prism_colossus", 256, 256, 8, 7)
    written.append(write_text(ASSETS / "geo" / "entity" / "prism_colossus.geo.json", pretty_json(geo)))
    written.append(write_text(ASSETS / "animations" / "entity" / "prism_colossus.animation.json", pretty_json(colossus_animations())))
    ent = TEX / "entity"
    for variant, suffix in (("base", ""), ("dormant", "_dormant"), ("cracked", "_cracked"), ("white", "_white")):
        tex, glow = paint_colossus(bones, 256, 256, variant)
        written.append(save_png(tex, ent / f"prism_colossus{suffix}.png"))
        if variant != "white":
            written.append(save_png(glow, ent / f"prism_colossus{suffix}_glowmask.png"))
    # the shards
    sb = build_shard()
    pack_uvs(sb, 64, 64)
    written.append(write_text(ASSETS / "geo" / "entity" / "prism_shard.geo.json", pretty_json(geo_json(sb, "prism_shard", 64, 64, 2.5, 2.0))))
    written.append(write_text(ASSETS / "animations" / "entity" / "prism_shard.animation.json", pretty_json(shard_animations())))
    tex, _ = paint_shard(sb, 64, 64, SHARD_GLOWS["red"])
    written.append(save_png(tex, ent / "prism_shard.png"))
    for name, glow_rgb in SHARD_GLOWS.items():
        _, glow = paint_shard(sb, 64, 64, glow_rgb)
        written.append(save_png(glow, ent / f"prism_shard_{name}_glowmask.png"))
    # blocks and items
    blk = TEX / "block"
    written.append(save_png(crystal_face("crown_crystal", False), blk / "crown_crystal.png"))
    written.append(save_png(crystal_face("crown_crystal", True), blk / "crown_crystal_lit.png"))
    written.append(save_png(pillar_side(False), blk / "crown_pillar_side.png"))
    written.append(save_png(pillar_side(True), blk / "crown_pillar_side_lit.png"))
    written.append(save_png(pillar_top(False), blk / "crown_pillar_top.png"))
    written.append(save_png(pillar_top(True), blk / "crown_pillar_top_lit.png"))
    for name, rising in (("rising_light", True), ("falling_light", False)):
        written.append(save_png(light_frames(rising), blk / f"{name}.png"))
        written.append(write_text(blk / f"{name}.png.mcmeta", json.dumps({"animation": {"frametime": 2, "interpolate": False}}, indent=2) + "\n"))
    written.append(save_png(gilded_bricks(), blk / "gilded_starfall_bricks.png"))
    written.append(save_png(prism_glass(), blk / "prism_glass.png"))
    written.append(save_png(crown_quartz(), blk / "crown_quartz.png"))
    written.append(save_png(altar_side(), blk / "prism_altar_side.png"))
    written.append(save_png(altar_top(), blk / "prism_altar_top.png"))
    itm = TEX / "item"
    written.append(save_png(guardian_echo_icon(), itm / "guardian_echo.png"))
    written.append(save_png(heart_icon(), itm / "heart_of_a_dying_star.png"))
    written += write_block_assets()
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
