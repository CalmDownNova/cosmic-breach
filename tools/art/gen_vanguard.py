"""Starfall Vanguard: the GeckoLib armor, its textures, item icons and the tier trims.

Writes (under src/main/resources/assets/cosmicbreach/):
    geo/armor/starfall_vanguard.geo.json            one model for all four pieces (GeckoLib armor bones)
    animations/armor/starfall_vanguard.animation.json   empty: the game poses the crest from the wearer's motion
    textures/armor/starfall_vanguard.png            the plate, 1 texel a unit like the player's skin
    textures/armor/starfall_vanguard_glowmask.png   the seams, cracks and crest (the game colours them by tier and Heat)
    textures/item/starfall_vanguard_<piece>.png     16x16 icons, and <piece>_trim.png (the tier trim layer)
    textures/item/<weapon>_trim.png                 the tier trim layer of Meridian, the Comet Maul and the Edges

The look (GDD 5.1): meteor-forged heavy plate, dark and pitted like a meteorite (thumbprint dimples lit on
their lower rims), seams that glow white-orange, pale Starsteel bands, pauldrons that are chunks of meteor
rock with glowing cracks, and a comet-tail crest (white-hot at the helm, orange at the tip) in three
segments the game swings with the wearer's speed. Silhouette: broad shoulders and a trailing crest.

Coordinates are Bedrock's humanoid (GeckoLib armor): units of 1/16 block, feet at y = 0, the head from y = 24
to 32, front to -Z, the wearer's right to -X. Bone names are GeckoLib's armor bones (checked in the 4.9.3 jar:
armorHead, armorBody, armorRightArm, armorLeftArm, armorRightLeg, armorLeftLeg, armorRightBoot,
armorLeftBoot); their pivots match the vanilla humanoid's, which GeoArmorRenderer copies the pose from.

Run:  python tools/art/gen_vanguard.py   (then preview_vanguard.py for the review sheets)
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field

import numpy as np

from common import ASSETS, TEX, blank, load_png, rgb, save_png

TEX_W, TEX_H = 128, 64
NAME = "starfall_vanguard"
GEO_PATH = ASSETS / "geo" / "armor" / f"{NAME}.geo.json"
ANIM_PATH = ASSETS / "animations" / "armor" / f"{NAME}.animation.json"
TEX_PATH = TEX / "armor" / f"{NAME}.png"
GLOW_PATH = TEX / "armor" / f"{NAME}_glowmask.png"

# ------------------------------------------------------------------ palette
# meteoric iron: warm charcoal, darkest first
IRON = [rgb(h) for h in ("#241e1c", "#352d2a", "#4a403b", "#5f544d", "#7a6d63", "#9c8e81")]
ROCK = [rgb(h) for h in ("#1c1716", "#2a2321", "#3a302d", "#4b3f3a", "#5e514a")]
STARSTEEL = [rgb(h) for h in ("#6e5f43", "#9a875f", "#c4b184", "#e3d4a6", "#f6ecc8")]
HEAT = [rgb(h) for h in ("#7a3a1e", "#b8542a", "#ff8a3a", "#ffc070", "#fff0cc")]
CREST = [rgb(h) for h in ("#fff6e0", "#ffe2a0", "#ffc070", "#ff9a3c", "#e86a2a", "#b8482a")]


# ------------------------------------------------------------------ model
@dataclass
class Cube:
    origin: tuple
    size: tuple
    paint: str                      # plate | rock | trim | crest
    seams: tuple = ()               # named seam rules for plate cubes
    rotation: tuple | None = None
    pivot: tuple | None = None
    inflate: float = 0.0
    tag: str = ""
    uv: tuple | None = None

    @property
    def dims(self):
        return tuple(int(math.floor(s + 1e-6)) for s in self.size)


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple
    rotation: tuple | None = None
    cubes: list = field(default_factory=list)


# How far each crest segment droops at rest (Bedrock degrees; negative droops a segment that points back).
# The game overrides these every frame (VanguardCrest); they must stay equal to its REST values.
CREST_REST = (-18.0, -9.0, -6.0)


def mirror_cube(c: Cube, tag: str) -> Cube:
    """The same cube on the wearer's left: x mirrored, rotations about Y and Z turned the other way."""
    ox, oy, oz = c.origin
    sx, sy, sz = c.size
    rot = None if c.rotation is None else (c.rotation[0], -c.rotation[1], -c.rotation[2])
    piv = None if c.pivot is None else (-c.pivot[0], c.pivot[1], c.pivot[2])
    seams = tuple(s.replace("xneg", "XPOS_").replace("xpos", "xneg").replace("XPOS_", "xpos") for s in c.seams)
    return Cube((-(ox + sx), oy, oz), c.size, c.paint, seams, rot, piv, c.inflate, tag)


def build_model() -> list[Bone]:
    head = Bone("armorHead", None, (0, 24, 0), cubes=[
        Cube((-4, 24, -4), (8, 8, 8), "plate", ("visor", "helm_sides"), inflate=1.0, tag="helm"),
        Cube((-4.5, 29.2, -5.7), (9, 2, 1), "trim", inflate=0.05, tag="brow"),
        Cube((-5.7, 24.3, -5.3), (1, 4, 5), "plate", ("edge_low",), tag="cheek_r"),
        Cube((4.7, 24.3, -5.3), (1, 4, 5), "plate", ("edge_low",), tag="cheek_l"),
        Cube((-4.6, 24.0, 4.5), (9.2, 3, 1.2), "plate", ("edge_low",), tag="nape"),
        Cube((-1, 32.9, -5.2), (2, 1.6, 10), "crest", tag="ridge"),
    ])
    tail1 = Bone("crest_tail_1", "armorHead", (0, 34.0, 4.8), (CREST_REST[0], 0, 0), cubes=[
        Cube((-1.0, 32.6, 4.8), (2, 3, 5), "crest", tag="tail1"),
    ])
    tail2 = Bone("crest_tail_2", "crest_tail_1", (0, 34.0, 9.6), (CREST_REST[1], 0, 0), cubes=[
        Cube((-0.75, 32.9, 9.6), (1.5, 2.2, 4.6), "crest", tag="tail2"),
    ])
    tail3 = Bone("crest_tail_3", "crest_tail_2", (0, 34.0, 14.0), (CREST_REST[2], 0, 0), cubes=[
        Cube((-0.5, 33.3, 14.0), (1, 1.4, 4.4), "crest", tag="tail3"),
    ])

    body = Bone("armorBody", None, (0, 24, 0), cubes=[
        Cube((-4, 12, -2), (8, 12, 4), "plate", ("chest_center", "chest_band", "back_center"), inflate=1.05, tag="cuirass"),
        Cube((-3, 15.5, -4.1), (6, 7, 1), "plate", ("front_center", "edge_low"), tag="keel"),
        Cube((-3.5, 23.4, -2.6), (7, 1.5, 5.2), "trim", inflate=0.35, tag="collar"),
        Cube((-3.5, 15, 3.1), (7, 8, 1), "plate", ("back_center",), tag="backplate"),
        Cube((-4.5, 11.6, -2.6), (9, 2, 5.2), "trim", inflate=0.25, tag="belt"),
    ])

    # the right pauldron: a chunk of meteor rock, lumps turned this way and that, wider than the arm
    r_arm_cubes = [
        Cube((-11.0, 20.4, -3.7), (7, 5, 7.4), "rock", tag="pauldron_r"),
        Cube((-12.2, 21.4, -2.4), (2, 3.4, 4.6), "rock", rotation=(0, 0, 16), pivot=(-11.0, 23.0, 0), tag="lump_r1"),
        Cube((-9.6, 24.8, -2.9), (5, 2, 5), "rock", rotation=(8, 20, -6), pivot=(-7.1, 25.8, -0.4), tag="lump_r2"),
        Cube((-10.4, 19.4, -4.4), (3, 2.4, 2.4), "rock", rotation=(-18, 0, 12), pivot=(-8.9, 20.6, -3.2), tag="lump_r3"),
        Cube((-8, 16, -2), (4, 5, 4), "plate", (), inflate=0.85, tag="upper_r"),
        Cube((-8, 12, -2), (4, 4, 4), "plate", ("edge_low",), inflate=1.05, tag="vambrace_r"),
        Cube((-8.6, 15.3, -2.6), (5.2, 1, 5.2), "trim", tag="cuff_r"),
    ]
    r_arm = Bone("armorRightArm", None, (-5, 22, 0), cubes=r_arm_cubes)
    l_arm = Bone("armorLeftArm", None, (5, 22, 0),
                 cubes=[mirror_cube(c, c.tag.replace("_r", "_l")) for c in r_arm_cubes])

    r_leg_cubes = [
        Cube((-3.9, 6, -2), (4, 6, 4), "plate", ("front_center",), inflate=0.55, tag="cuisse_r"),
        Cube((-3.6, 4.6, -3.2), (3.4, 2.4, 1.2), "trim", tag="knee_r"),
        Cube((-4.5, 8.2, -3.0), (4.6, 3.6, 1), "plate", (), tag="tasset_r"),
        Cube((-3.9, 3, -2), (4, 3, 4), "plate", ("front_center",), inflate=0.55, tag="shin_r"),
    ]
    r_leg = Bone("armorRightLeg", None, (-1.9, 12, 0), cubes=r_leg_cubes)
    l_leg = Bone("armorLeftLeg", None, (1.9, 12, 0),
                 cubes=[mirror_cube(c, c.tag.replace("_r", "_l")) for c in r_leg_cubes])

    r_boot_cubes = [
        Cube((-3.9, 0, -2), (4, 4, 4), "plate", ("boot_band",), inflate=1.0, tag="boot_r"),
        Cube((-3.6, 0, -4.2), (3.4, 2, 1.4), "plate", (), tag="toe_r"),
        Cube((-3.4, 3.8, -3.2), (3, 1, 1), "trim", tag="instep_r"),
    ]
    r_boot = Bone("armorRightBoot", None, (-1.9, 12, 0), cubes=r_boot_cubes)
    l_boot = Bone("armorLeftBoot", None, (1.9, 12, 0),
                  cubes=[mirror_cube(c, c.tag.replace("_r", "_l")) for c in r_boot_cubes])
    return [head, tail1, tail2, tail3, body, r_arm, l_arm, r_leg, l_leg, r_boot, l_boot]


# ------------------------------------------------------------------ box UV
def face_rects(c: Cube):
    """Box UV rectangles (u, v, w, h) per face, as GeckoLib's BakedModelFactory picks them."""
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
    """Cube-local Bedrock point at the centre of a face texel (same mapping as gen_shardling)."""
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    w, h, d = c.dims
    fx = (col + 0.5) / max(1, {"xneg": d, "xpos": d, "front": w, "back": w, "top": w, "bottom": w}[face])
    fy = (row + 0.5) / max(1, {"top": d, "bottom": d}.get(face, h))
    if face == "xneg":
        return (x0, y1 - fy * sy, z1 - fx * sz)
    if face == "front":
        return (x0 + fx * sx, y1 - fy * sy, z0)
    if face == "xpos":
        return (x1, y1 - fy * sy, z0 + fx * sz)
    if face == "back":
        return (x1 - fx * sx, y1 - fy * sy, z1)
    if face == "top":
        return (x0 + fx * sx, y1, z1 - fy * sz)
    return (x0 + fx * sx, y0, z1 - fy * sz)


def pack_uvs(bones: list[Bone]):
    cubes = [c for b in bones for c in b.cubes]
    order = sorted(cubes, key=lambda c: (-(c.dims[2] + c.dims[1]), -(2 * (c.dims[0] + c.dims[2]))))
    gap = 1
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


# ------------------------------------------------------------------ painting
def hash01(*vals) -> float:
    h = 2166136261
    for v in vals:
        if isinstance(v, str):
            for ch in v.encode():
                h ^= ch
                h = (h * 16777619) & 0xFFFFFFFF
        else:
            h ^= int(round(v * 8)) & 0xFFFFFFFF
            h = (h * 16777619) & 0xFFFFFFFF
    return (h % 10007) / 10007.0


def pits_for(c: Cube, face: str, w: int, h: int):
    """Thumbprint dimples on one face: (u, v, radius), fewer on small faces."""
    area = w * h
    n = max(0, int(area / 26))
    out = []
    for i in range(n):
        u = hash01(c.tag, face, i, 1) * w
        v = hash01(c.tag, face, i, 2) * h
        r = 0.9 + hash01(c.tag, face, i, 3) * 0.9
        out.append((u, v, r))
    return out


def cracks_for(c: Cube, face: str, w: int, h: int):
    """A few crack paths across a rock face (random walks), as a set of (col, row)."""
    cells = set()
    n = 1 + int(w * h / 40)
    for i in range(n):
        col = int(hash01(c.tag, face, i, 7) * w)
        row = int(hash01(c.tag, face, i, 8) * h)
        steps = 3 + int(hash01(c.tag, face, i, 9) * max(3, (w + h) // 2))
        dc = 1 if hash01(c.tag, face, i, 10) > 0.5 else -1
        for s in range(steps):
            if 0 <= col < w and 0 <= row < h:
                cells.add((col, row))
            k = hash01(c.tag, face, i, s, 11)
            if k < 0.45:
                row += 1
            elif k < 0.8:
                col += dc
            else:
                col += dc
                row += 1
    return cells


def seam_cells(c: Cube, face: str, w: int, h: int):
    """The glowing seam texels of a plate face, by the cube's seam rules."""
    cells = set()
    for rule in c.seams:
        if rule == "visor" and face == "front":
            for col in range(1, w - 1):
                cells.add((col, 3))                         # the eye slit
            cells.add((w // 2, 4))
            cells.add((w // 2 - 1, 4))
        elif rule == "helm_sides" and face in ("xneg", "xpos"):
            for col in range(0, 3):
                cells.add((col if face == "xpos" else w - 1 - col, 3))
            for row in range(4, h - 1):
                cells.add((w // 2, row))                    # the seam down the cheek plate
        elif rule == "chest_center" and face == "front":
            for row in range(1, h - 1):
                cells.add((w // 2 - 1, row) if row % 5 else (w // 2, row))
        elif rule == "chest_band" and face in ("front", "xneg", "xpos", "back"):
            for col in range(w if face in ("front", "back") else w):
                cells.add((col, h - 3))                     # the seam above the belt, all round
        elif rule == "back_center" and face == "back":
            for row in range(1, h - 3):
                cells.add((w // 2, row))
        elif rule == "keel_rim" and face == "front":
            for col in range(w):
                cells.add((col, 0))
                cells.add((col, h - 1))
            for row in range(h):
                cells.add((0, row))
                cells.add((w - 1, row))
        elif rule == "front_center" and face == "front":
            for row in range(0, h):
                cells.add((w // 2, row))
        elif rule == "boot_band" and face in ("front", "xneg", "xpos", "back"):
            for col in range(w):
                cells.add((col, 1))
        elif rule == "edge_low" and face in ("front", "xneg", "xpos", "back"):
            for col in range(w):
                cells.add((col, h - 1))
    return cells


def near(cells, col, row):
    return any((col + dc, row + dr) in cells for dc in (-1, 0, 1) for dr in (-1, 0, 1))


def paint_face(c: Cube, face: str, w: int, h: int):
    """Colours and glow for one face: two lists of rows of (r, g, b) or None."""
    col_out = [[None] * w for _ in range(h)]
    glow_out = [[None] * w for _ in range(h)]
    side = face in ("xneg", "xpos", "front", "back")
    if c.paint == "crest":
        for row in range(h):
            for col in range(w):
                x, y, z = texel_point(c, face, col, row)
                # white-hot at the helm, orange at the tip, hotter along the top edge; never the dull reds.
                # The glow fades toward the tip (the mask's brightness), so Heat lights the crest from the helm back.
                t = (z + 5.5) / 24.0
                k = min(3, max(1, int(1 + t * 3.2 + hash01(c.tag, face, col, row) * 0.6)))
                if face == "top" or (side and row == 0):
                    k = max(1, k - 1)
                if face == "bottom":
                    k = min(4, k + 1)
                col_out[row][col] = CREST[k]
                lum = int(255 * (1.0 - 0.5 * min(1.0, max(0.0, t))))
                glow_out[row][col] = (lum, lum, lum)
        return col_out, glow_out

    if c.paint == "trim":
        for row in range(h):
            for col in range(w):
                k = 2
                if face == "top" or (side and row == 0):
                    k = 3
                elif face == "bottom" or (side and row == h - 1 and h > 1):
                    k = 1
                n = hash01(c.tag, face, col, row)
                if n < 0.1:
                    k = min(4, k + 1)
                elif n > 0.9:
                    k = max(0, k - 1)
                col_out[row][col] = STARSTEEL[k]
        return col_out, glow_out

    if c.paint == "rock":
        cracks = cracks_for(c, face, w, h)
        for row in range(h):
            for col in range(w):
                n = hash01(c.tag, face, col, row)
                k = 2 + (1 if n > 0.72 else 0) - (1 if n < 0.22 else 0)
                if face == "top":
                    k += 1
                elif face == "bottom":
                    k -= 1
                if (col, row) in cracks:
                    core = hash01(c.tag, face, col, row, 5) > 0.75
                    colour = HEAT[3] if core else HEAT[2]
                    col_out[row][col] = colour
                    glow_out[row][col] = colour
                    continue
                col_out[row][col] = ROCK[max(0, min(len(ROCK) - 1, k))]
        return col_out, glow_out

    # plate: meteoric iron with thumbprint pits, bevelled edges and glowing seams
    seams = seam_cells(c, face, w, h)
    pits = pits_for(c, face, w, h)
    for row in range(h):
        for col in range(w):
            if (col, row) in seams:
                core = hash01(c.tag, face, col, row, 4) > 0.7
                colour = HEAT[3] if core else HEAT[2]
                col_out[row][col] = colour
                glow_out[row][col] = colour
                continue
            k = 3
            n = hash01(c.tag, face, col, row)
            if n < 0.22:
                k = 2
            elif n > 0.95:
                k = 4
            if face == "top":
                k = 4 if n > 0.2 else 3
            elif face == "bottom":
                k = 1
            elif side and row == 0:
                k = 4                                        # bevel light along the top edge
            elif side and row == h - 1 and h > 2:
                k = 2
            for (pu, pv, pr) in pits:
                du, dv = col + 0.5 - pu, row + 0.5 - pv
                d = math.hypot(du, dv)
                if d < pr * 0.6:
                    k = 1
                elif d < pr and (du + dv) > 0.6:
                    k = 4                                    # the dimple's lower rim catches the light
            if (col, row - 1) in seams or (col, row + 1) in seams:
                if n < 0.25:
                    col_out[row][col] = HEAT[0]              # a little heat bleeding out of the seam
                    continue
            col_out[row][col] = IRON[max(0, min(len(IRON) - 1, k))]
    return col_out, glow_out


def paint_texture(bones: list[Bone]):
    tex = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    for b in bones:
        for c in b.cubes:
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                colours, glows = paint_face(c, face, rw, rh)
                for r in range(rh):
                    for col in range(rw):
                        tex[rv + r, ru + col, :3] = colours[r][col]
                        tex[rv + r, ru + col, 3] = 255
                        g = glows[r][col]
                        if g is not None:
                            # the mask is a warm white: the game tints it by tier and brightens it with Heat
                            lum = max(g) / 255.0
                            glow[rv + r, ru + col, :3] = (int(255 * lum), int(238 * lum), int(214 * lum))
                            glow[rv + r, ru + col, 3] = 255
    return tex, glow


# ------------------------------------------------------------------ json
def r4(v):
    return [round(float(a), 4) for a in v]


def geo_json(bones: list[Bone]) -> dict:
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
                "identifier": "geometry." + NAME,
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": out,
        }],
    }


def pretty(data) -> str:
    return json.dumps(data, indent=2) + "\n"


# ------------------------------------------------------------------ item icons (16x16)
ICON = {
    "o": rgb("#150f0e"),       # outline
    "r": ROCK[1], "R": ROCK[3],
    "d": IRON[1], "m": IRON[2], "M": IRON[3], "l": IRON[4], "h": IRON[5],
    "t": STARSTEEL[1], "T": STARSTEEL[3], "W": STARSTEEL[4],
    "s": HEAT[2], "S": HEAT[4], "e": HEAT[1],
    "c": CREST[3], "C": CREST[1], "w": CREST[0],
}

HELM = [
    "................",
    "...........oooo.",
    "......oooooCwwCo",
    ".....oCwwwwCCcco",
    "....ohhlMMMmooo.",
    "...ohlMMMMMmmo..",
    "...olMMMmMMmdo..",
    "..oTTTTTTTTTTto.",
    "..olMsssssssmdo.",
    "..olMmMmmMMmmdo.",
    "..odMmmooommmdo.",
    "..odmmo..odmmdo.",
    "..odmdo..odmddo.",
    "...oddo..oddo...",
    "....oo....oo....",
    "................",
]

CHEST = [
    "................",
    ".ooo.......ooo..",
    "oRRRoo...ooRRRo.",
    "oRsRRloooMRRsRRo",
    "orRRsMTTTTMsRRro",
    ".orrMhMMsMMlMrro",
    "..odMlMMsMMMdo..",
    "...odMMmsMmMdo..",
    "...omMMmsmMmmo..",
    "...odmMmsmmmdo..",
    "...odmmMsmMmdo..",
    "...ossssssssso..",
    "...oTTTTTTTTto..",
    "...odmmmmmmmdo..",
    "....oooooooooo..",
    "................",
]

GREAVES = [
    "................",
    "...oooooooooo...",
    "...oTTTTTTTTo...",
    "...ohMMsMMMmo...",
    "...olMMsMsMmo...",
    "...olMMsosMmo...",
    "...olMsooosMo...",
    "...oTTsoooTTo...",
    "...olMsooosmo...",
    "...olMso.osmo...",
    "...olMso.osmo...",
    "...olmmo.ommo...",
    "...odmdo.odmo...",
    "...oooo..oooo...",
    "................",
    "................",
]

BOOTS = [
    "................",
    "................",
    "................",
    "...oooo..oooo...",
    "...ohMo..ohMo...",
    "...olMo..olMo...",
    "...oTTo..oTTo...",
    "...olMo..olMo...",
    "...olMo..olMo...",
    "..olMMmo.olMMmo.",
    ".olMssmo.olMssmo",
    ".oTTTTdo.oTTTTdo",
    ".oooooo..oooooo.",
    "................",
    "................",
    "................",
]


def sprite(rows) -> np.ndarray:
    img = blank(16, 16)
    for y, line in enumerate(rows):
        assert len(line) == 16, (y, line)
        for x, ch in enumerate(line):
            if ch == ".":
                continue
            img[y, x, :3] = ICON[ch]
            img[y, x, 3] = 255
    return img


def trim_of(img: np.ndarray) -> np.ndarray:
    """The tier trim: the sprite's outermost texels (opaque ones touching transparency), white; the game tints it."""
    a = img[..., 3] > 0
    pad = np.pad(a, 1)
    inner = pad[1:-1, 1:-1]
    edge = inner & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    out = np.zeros_like(img)
    out[edge, :3] = 255
    out[edge, 3] = 255
    return out


PIECES = {"helm": HELM, "chestplate": CHEST, "greaves": GREAVES, "boots": BOOTS}
WEAPON_TRIMS = ["meridian", "comet_maul", "binary_edges", "binary_edges_right"]


def main() -> int:
    bones = build_model()
    pack_uvs(bones)
    tex, glow = paint_texture(bones)
    GEO_PATH.parent.mkdir(parents=True, exist_ok=True)
    GEO_PATH.write_bytes(pretty(geo_json(bones)).encode("utf-8"))
    ANIM_PATH.parent.mkdir(parents=True, exist_ok=True)
    ANIM_PATH.write_bytes(pretty({"format_version": "1.8.0", "animations": {}}).encode("utf-8"))
    save_png(tex, TEX_PATH)
    save_png(glow, GLOW_PATH)
    for piece, rows in PIECES.items():
        img = sprite(rows)
        save_png(img, TEX / "item" / f"{NAME}_{piece}.png")
        save_png(trim_of(img), TEX / "item" / f"{NAME}_{piece}_trim.png")
    for weapon in WEAPON_TRIMS:
        save_png(trim_of(load_png(TEX / "item" / f"{weapon}.png")), TEX / "item" / f"{weapon}_trim.png")
    print(f"wrote {GEO_PATH.name}, {TEX_PATH.name} ({TEX_W}x{TEX_H}), glowmask, 4 icons, {len(PIECES) + len(WEAPON_TRIMS)} trims")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
