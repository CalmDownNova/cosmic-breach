"""Driftweave: the GeckoLib armor, its textures, item icons and tier trims.

Writes (under src/main/resources/assets/cosmicbreach/):
    geo/armor/driftweave.geo.json              one model for all four pieces (GeckoLib armor bones)
    animations/armor/driftweave.animation.json empty: the game poses the scarf, cape, fins and halo from the wearer
    textures/armor/driftweave.png              cloth and plates, 1 texel a unit like the player's skin
    textures/armor/driftweave_glowmask.png     the star dust: scarf, flecks, edges, visor slit, halo, fins (the game
                                               adds it as light, stronger with more dash charges ready)
    textures/item/driftweave_<piece>.png       16x16 icons, and <piece>_trim.png (the tier trim layer)

The look (GDD 5.1): slim pale Nebulite plates over flowing night-blue cloth flecked with stars; a scarf of star
dust wrapped at the neck whose two tails trail down the back (the game streams and ripples them with the
wearer's velocity); a half-cape on the left shoulder; a hood with a dark visor band and a thin halo over it;
small fins at the ankles that flare on every dash. Silhouette: lean, with a trailing scarf.

The scarf and cape hang clear of the back at rest (their pivots sit behind the coat) and the game lifts them as
soon as the wearer moves, so the legs swing clear of them. Tails are wide flat ribbons of two lengths, knotted at
one point, so they read as a scarf and not as tentacles.

Run:  python tools/art/gen_driftweave.py   (then preview_sets.py for the review sheets)
"""
from __future__ import annotations

import math

import numpy as np

import armor_model as am
from armor_model import Bone, Cube, grid, hash01, mirror_bone, mirror_cube, ring_cubes, texel_point
from common import ASSETS, TEX, rgb, save_png

TEX_W, TEX_H = 128, 128
NAME = "driftweave"
GEO_PATH = ASSETS / "geo" / "armor" / f"{NAME}.geo.json"
ANIM_PATH = ASSETS / "animations" / "armor" / f"{NAME}.animation.json"
TEX_PATH = TEX / "armor" / f"{NAME}.png"
GLOW_PATH = TEX / "armor" / f"{NAME}_glowmask.png"

# ------------------------------------------------------------------ palette (the Drift: indigo night, Nebulite)
CLOTH = [rgb(h) for h in ("#100e2c", "#18163f", "#221f55", "#2d2a6c", "#3b3786")]
PLATE = [rgb(h) for h in ("#3f4f7e", "#5f73a8", "#8599c9", "#b0c1e6", "#dbe6ff")]
TRIM = [rgb(h) for h in ("#3b3192", "#5047b0", "#6a62cc", "#8d86e2")]
DUST = [rgb(h) for h in ("#3b2c96", "#5a4bc8", "#5a8ee8", "#6fd9ee", "#c6f7ff")]
CYAN = rgb("#6fd9ee")
RIM = rgb("#7fc4dc")           # the plates' painted edge line: a quieter cyan
ICE = rgb("#c6f7ff")
STAR = rgb("#e8f8ff")
SPECK_DIM = rgb("#6f7fb8")       # the cloth's specks: dim, a few brighter
SPECK_BRIGHT = rgb("#b9c8f0")
VISOR = [rgb(h) for h in ("#07081a", "#0f1330", "#1a2150")]

# How far the scarf's and cape's segments hang at rest (Bedrock degrees about X; -90 points straight down).
# The game overrides these every frame (client DriftweaveLook); they must stay equal to its REST values.
SCARF_A_REST = (-86.0, 1.5, 1.5, 1.0)
SCARF_B_REST = (-84.0, 1.5, 1.0)
CAPE_REST = (-87.0, 1.5, 1.5)
HALO_TILT = -14.0
FIN_REST = 8.0

am.SHAREABLE = {"halo"}


# ------------------------------------------------------------------ model
def chain(prefix, parent, x0, width, y, z0, lengths, rest, paint, rolls, taper=0.2, thin=0.6):
    """A hanging ribbon of segments behind the body: each extends back (+Z) from its pivot and the next hangs from
    its end (Bedrock pivots are in the unrotated model frame, like the Vanguard's crest). Each segment is turned a
    little further about its own length ({@code rolls}, degrees), so the ribbon twists and shows its width from
    the side and the back instead of an edge."""
    bones = []
    z = z0
    w = width
    cx = x0 + width / 2
    for i, length in enumerate(lengths):
        name = f"{prefix}{i + 1}"
        cube = am.thin_cube((cx - w / 2, y - thin / 2, z), (w, thin, length), paint, (f"seg{i}", f"of{len(lengths)}"),
                            thickness_axis=1, thin=thin, tag=name)
        if rolls[i]:
            cube.rotation = (0.0, 0.0, rolls[i])
            cube.pivot = (cx, y, z + length / 2)
        bones.append(Bone(name, parent if i == 0 else f"{prefix}{i}", (cx, y, z), (rest[i], 0.0, 0.0), [cube]))
        parent = name
        z += length
        w = max(1.4, w - taper)
    return bones


def plate(origin, size, rules, tag, axis=2, thin=0.7, **kw):
    if axis is None:
        return Cube(origin, size, "plate", tuple(rules), tag=tag, **kw)
    return am.thin_cube(origin, size, "plate", rules, thickness_axis=axis, thin=thin, tag=tag, **kw)


def build_model() -> list[Bone]:
    head = Bone("armorHead", None, (0, 24, 0), cubes=[
        Cube((-4, 24, -4), (8, 8, 8), "cloth", ("hood",), inflate=0.8, tag="hood"),
        Cube((-3.6, 22.8, 3.3), (7.2, 2.6, 1.3), "cloth", ("drape",), tag="nape"),
        Cube((-4.35, 27.0, -5.2), (8.7, 1.5, 1.0), "visor", tag="visor"),
        plate((-2.4, 29.3, -5.1), (4.8, 1.2, 0.7), ("rim_top",), "brow"),
    ])
    halo = Bone("halo", "armorHead", (0, 33.6, 0.4), (HALO_TILT, 0, 0),
                cubes=ring_cubes((0, 34.4, 0.4), 4.7, 12, 0.45, "xz", "halo", "halo", width=0.4))

    body = Bone("armorBody", None, (0, 24, 0), cubes=[
        Cube((-4, 12, -2), (8, 12, 4), "cloth", ("coat",), inflate=0.55, tag="coat"),
        plate((-3.9, 18.2, -3.0), (3.4, 4.4, 0.8), ("rim_top",), "pec_r"),
        plate((0.5, 18.2, -3.0), (3.4, 4.4, 0.8), ("rim_top",), "pec_l"),
        Cube((-4.5, 12.1, -2.65), (9, 1.6, 5.3), "trim", tag="sash"),
        Cube((-4.45, 22.3, -2.75), (8.9, 2.5, 5.5), "scarf", ("wrap",), tag="wrap"),
        Cube((-3.6, 21.2, 2.55), (3.2, 2.6, 1.5), "scarf", ("knot",), tag="knot"),
    ])
    scarf_a = chain("scarf_a", "armorBody", -3.7, 3.4, 22.4, 3.4, (4.2, 4.0, 3.8, 3.4), SCARF_A_REST, "scarf",
                    (18.0, 32.0, 46.0, 58.0))
    scarf_b = chain("scarf_b", "armorBody", -3.0, 2.6, 21.9, 4.15, (3.6, 3.4, 3.0), SCARF_B_REST, "scarf",
                    (-22.0, -38.0, -52.0))
    cape = chain("cape_", "armorBody", 0.5, 3.2, 23.7, 2.95, (4.6, 4.2, 3.8), CAPE_REST, "cape", (-6.0, -14.0, -24.0), taper=0.2)
    # the dash charges ready: three bands round the long tail, lit one per charge (the game shows the lit or the dark
    # one of each pair)
    bands = []
    z = 3.4
    w = 3.4
    for k, (length, roll) in enumerate(zip((4.2, 4.0, 3.8), (18.0, 32.0, 46.0)), start=1):
        mid = z + length / 2
        for suffix, paint in (("", "band"), ("_off", "band_off")):
            cube = Cube((-2.0 - (w + 0.5) / 2, 22.4 - 0.5, mid - 0.7), (w + 0.5, 1.0, 1.4), paint, (), tag=f"charge_{k}{suffix}")
            cube.rotation = (0.0, 0.0, roll)
            cube.pivot = (-2.0, 22.4, mid)
            bands.append(Bone(f"charge_{k}{suffix}", f"scarf_a{k}", (-2.0, 22.4, z), None, [cube]))
        z += length
        w = max(1.4, w - 0.2)

    r_arm = Bone("armorRightArm", None, (-5, 22, 0), cubes=[
        Cube((-8, 12, -2), (4, 12, 4), "cloth", ("sleeve",), inflate=0.35, tag="sleeve_r"),
        plate((-8.6, 20.6, -2.6), (4.0, 1.8, 5.2), ("rim_top",), "shoulder_r", axis=None, rotation=(0, 0, 12),
              pivot=(-6.6, 21.5, 0)),
        Cube((-8.5, 12.6, -2.5), (5, 3.2, 5), "plate", ("band",), tag="bracer_r"),
    ])
    l_arm = mirror_bone(r_arm, "armorLeftArm", None)

    r_leg = Bone("armorRightLeg", None, (-1.9, 12, 0), cubes=[
        Cube((-3.9, 3, -2), (4, 9, 4), "cloth", ("legging",), inflate=0.4, tag="legging_r"),
        plate((-3.6, 5.0, -3.0), (3.4, 2.2, 0.8), ("rim_top",), "knee_r"),
        plate((-4.55, 7.2, -1.6), (0.7, 3.8, 3.2), ("rim_top",), "thigh_r", axis=0),
    ])
    l_leg = mirror_bone(r_leg, "armorLeftLeg", None)

    r_boot = Bone("armorRightBoot", None, (-1.9, 12, 0), cubes=[
        Cube((-3.9, 0, -2), (4, 4, 4), "plate", ("boot",), inflate=0.5, tag="boot_r"),
        Cube((-3.7, 0, -3.4), (3.6, 1.5, 1.3), "plate", ("rim_top",), tag="toe_r"),
    ])
    l_boot = mirror_bone(r_boot, "armorLeftBoot", None)
    fin_r = Bone("fin_r", "armorRightBoot", (-4.65, 2.4, 0.4), (0, -FIN_REST, 0), cubes=[
        am.thin_cube((-4.85, 1.2, 0.4), (0.4, 2.6, 2.4), "fin", ("root",), thickness_axis=0, thin=0.4, tag="fin_r_a"),
        am.thin_cube((-4.85, 2.1, 2.8), (0.4, 1.5, 1.9), "fin", ("tip",), thickness_axis=0, thin=0.4, tag="fin_r_b"),
    ])
    fin_l = mirror_bone(fin_r, "fin_l", "armorLeftBoot")
    bones = [head, halo, body, *scarf_a, *bands, *scarf_b, *cape, r_arm, l_arm, r_leg, l_leg, r_boot, l_boot, fin_r, fin_l]
    am.check_uv(bones)
    return bones


# ------------------------------------------------------------------ painting
def fleck(c: Cube, face: str, col: int, row: int, density: float) -> bool:
    return hash01(c.tag, face, col, row, 91) < density


_SPECKS: dict = {}


def specks(c: Cube, face: str, w: int, h: int, density: float) -> dict:
    """The cloth's star specks on one face: few, scattered, never two side by side, one above the other or in a
    pair on a row (pairs read as eyes). {(col, row): bright}."""
    key = (c.tag, face, w, h, density)
    if key not in _SPECKS:
        order = sorted(((hash01(c.tag, face, col, row, 91), col, row) for row in range(h) for col in range(w)))
        chosen = {}
        for value, col, row in order:
            if value >= density:
                break
            clash = any(max(abs(col - oc), abs(row - orow)) <= 2 or (orow == row and abs(oc - col) <= 5)
                        or (oc == col and abs(orow - row) <= 4) for (oc, orow) in chosen)
            if not clash:
                chosen[(col, row)] = hash01(c.tag, face, col, row, 17) < 0.3
        _SPECKS[key] = chosen
    return _SPECKS[key]


def paint_face(c: Cube, face: str, w: int, h: int):
    cols = grid(w, h)
    glows = grid(w, h)
    side = face in ("xneg", "xpos", "front", "back")
    rules = set(c.rules)

    if c.paint == "cloth":
        for row in range(h):
            for col in range(w):
                k = 2 + (1 if (row + (col // 2)) % 3 == 0 else 0) - (1 if hash01(c.tag, face, col, row) < 0.2 else 0)
                if face == "top":
                    k += 1
                elif face == "bottom" or (side and row == h - 1 and h > 2):
                    k -= 1
                colour = CLOTH[max(0, min(4, k))]
                glow = None
                speck = specks(c, face, w, h, 0.02 if "hood" in rules else 0.03).get((col, row))
                if speck is not None:
                    colour, glow = (SPECK_BRIGHT, (150, 185, 235)) if speck else (SPECK_DIM, (70, 88, 140))
                cols[row][col] = colour
                glows[row][col] = glow
        if "hood" in rules and face == "front":
            # the opening: the face shows under the hood (cut out), a rim of cloth round it
            for row in range(2, h):
                for col in range(1, w - 1):
                    cols[row][col] = None
                    glows[row][col] = None
            for col in range(1, w - 1):
                cols[1][col] = CLOTH[4]
        if "coat" in rules and face == "front":
            # the coat's overlap: a diagonal seam from the right collar to the left hip
            for row in range(h):
                col = int(round(1 + (w - 3) * row / max(1, h - 1)))
                cols[row][col] = PLATE[3]
        if "sleeve" in rules and side:
            for col in range(w):
                cols[h - 1][col] = TRIM[2]
        if "legging" in rules and face in ("xneg", "xpos"):
            mid = w // 2
            for row in range(h):
                cols[row][mid] = TRIM[3]
        return cols, glows

    if c.paint == "plate":
        for row in range(h):
            for col in range(w):
                k = 2
                n = hash01(c.tag, face, col, row)
                if face == "top" or (side and row == 0):
                    k = 4
                elif face == "bottom" or (side and row == h - 1 and h > 2):
                    k = 1
                elif n < 0.18:
                    k = 3
                colour = PLATE[k]
                glow = None
                if side and h > 1 and col in (0, w - 1) and w > 2:
                    colour = PLATE[1]                           # a darker rim down the sides
                if "rim_top" in rules and ((side and row == 0) or (face == "top" and row == h - 1)):
                    colour = RIM                                # one cyan line along the top edge (paint, not light)
                if "band" in rules and side and row == h // 2:
                    colour = RIM
                if "boot" in rules and side and row == 1:
                    colour = RIM
                cols[row][col] = colour
                glows[row][col] = glow
        return cols, glows

    if c.paint == "trim":
        for row in range(h):
            for col in range(w):
                k = 2 if row != 0 else 3
                if face == "bottom":
                    k = 0
                cols[row][col] = TRIM[k]
                if side and h > 1 and row == h // 2 and col % 3 == 1:
                    cols[row][col] = ICE
                    glows[row][col] = (150, 230, 255)
        return cols, glows

    if c.paint in ("scarf", "cape"):
        for row in range(h):
            for col in range(w):
                x, y, z = texel_point(c, face, col, row)
                if c.paint == "scarf":
                    # along the tail: violet at the knot to cyan at the tip; the wrap and knot violet-blue
                    t = min(1.0, max(0.0, (z - 3.0) / 16.0)) if any(r.startswith("seg") for r in c.rules) else 0.25
                    k = min(4, int(t * 3.4 + hash01(c.tag, face, col, row) * 0.9))
                    colour = DUST[k]
                    lum = 0.35 + 0.35 * t
                    glow = tuple(int(ch * lum) for ch in DUST[min(4, k + 1)])
                    if fleck(c, face, col, row, 0.07):
                        colour, glow = STAR, (235, 250, 255)
                    if "wrap" in rules and side and (row == 0 or row == h - 1):
                        colour = DUST[1]
                else:
                    # the half-cape: star dust like the scarf, darker, deepening from violet at the shoulder to blue,
                    # with a bright hem at its tip (the last piece's far end: its broad faces' first rows)
                    t = min(1.0, max(0.0, (z - 3.0) / 12.6))
                    k = min(3, int(t * 2.6 + hash01(c.tag, face, col, row) * 0.8))
                    colour = DUST[k]
                    glow = tuple(int(ch * (0.18 + 0.2 * t)) for ch in DUST[min(4, k + 1)])
                    hem = "seg2" in c.rules and face in ("top", "bottom") and row <= 1
                    if hem:
                        colour = DUST[4] if row == 0 else DUST[3]
                        glow = (150, 225, 250) if row == 0 else (80, 150, 200)
                    elif fleck(c, face, col, row, 0.1):
                        colour, glow = STAR, (200, 235, 255)
                cols[row][col] = colour
                glows[row][col] = glow
        return cols, glows

    if c.paint in ("band", "band_off"):
        for row in range(h):
            for col in range(w):
                if c.paint == "band":
                    cols[row][col] = CYAN if (row + col) % 4 else ICE     # a lit charge: bright cyan, unlike the white flecks
                    glows[row][col] = (90, 230, 255)
                else:
                    cols[row][col] = VISOR[1]
        return cols, glows

    if c.paint == "visor":
        for row in range(h):
            for col in range(w):
                cols[row][col] = VISOR[1 if row else 2]
                if face == "front" and row == h // 2 and 0 < col < w - 1:
                    cols[row][col] = CYAN
                    glows[row][col] = (130, 235, 255)
        return cols, glows

    if c.paint == "halo":
        for row in range(h):
            for col in range(w):
                cols[row][col] = ICE
                glows[row][col] = (220, 250, 255)
        return cols, glows

    if c.paint == "fin":
        for row in range(h):
            for col in range(w):
                cols[row][col] = DUST[1] if (row + col) % 3 else DUST[2]
                glows[row][col] = (60, 70, 170)
                if side and row == 0:
                    cols[row][col] = CYAN
                    glows[row][col] = (140, 235, 255)
        return cols, glows

    raise ValueError(c.paint)


# ------------------------------------------------------------------ item icons (16x16)
ICON = am.palette({
    "o": "#0a0920",
    "n": CLOTH[1], "c": CLOTH[2], "C": CLOTH[3], "k": CLOTH[4],
    "p": PLATE[1], "P": PLATE[2], "l": PLATE[3], "L": PLATE[4],
    "v": DUST[1], "d": DUST[2], "D": DUST[3], "w": DUST[4],
    "s": STAR, "y": CYAN, "g": VISOR[0],
})

HOOD = [
    "....oooooooo....",
    "...owwwwwwwwo...",
    "....oooooooo....",
    "....occCCCco....",
    "...occCCkCCco...",
    "..occCsCCCCCco..",
    "..ocCgggggggco..",
    "..ocgyyyyyygco..",
    "..ocCgggggggco..",
    "..occo....occo..",
    "..occo....occo..",
    "..occco..occco..",
    "...occcooccco...",
    "....ovddddDo....",
    ".....oooooo.....",
    "................",
]

COAT = [
    "................",
    "..oooo....oooo..",
    ".oPPpooooooppPo.",
    ".oLPvddDDddvPLo.",
    ".oPPcvddddvcpPo.",
    "..occlPoopLcco..",
    "..occlPccPlcco..",
    "..occcsccCccco..",
    "..occcclcccsco..",
    "..occCcclcccco..",
    "..oyyyyyyyyyyo..",
    "..occccccclccov.",
    "..occcsccccclcvD",
    "..oooooooooooovD",
    ".............vDw",
    "..............w.",
]

LEGGINGS = [
    "................",
    "...oooooooooo...",
    "...oyyyyyyyyo...",
    "...occCccCcco...",
    "...ocyCcoCycco..",
    "...ocyCco.cyco..",
    "...olLco..oLlo..",
    "...ocyco..ocyo..",
    "...ocsco..ocyo..",
    "...ocyco..ocyo..",
    "...occco..occo..",
    "...ooooo..oooo..",
    "................",
    "................",
    "................",
    "................",
]

BOOTS = [
    "................",
    "................",
    "................",
    "...oooo...oooo..",
    "...oPPo...oPPo..",
    "..doPPo..doPPo..",
    ".dDoyyo.dDoyyo..",
    ".dDoPPo.dDoPPo..",
    "..dolPo..dolPo..",
    "..oLLPPo.oLLPPo.",
    ".oLLPPPo.oLLPPPo",
    ".oyyyyyo.oyyyyyo",
    ".ooooooo.ooooooo",
    "................",
    "................",
    "................",
]

PIECES = {"hood": HOOD, "coat": COAT, "leggings": LEGGINGS, "boots": BOOTS}


def _p(**bones):
    return {name: (list(rot), None, None) for name, rot in bones.items()}


# Offsets from the rest pose the game's DriftweaveLook gives while running flat out and while falling fast
# (for the previews; the game computes them every frame).
PREVIEW_POSES = {
    "rest": {},
    "run": _p(scarf_a1=(50, 0, 0), scarf_a2=(8, 0, 0), scarf_a3=(-6, 0, 0), scarf_a4=(8, 0, 0),
              scarf_b1=(44, 0, 0), scarf_b2=(-5, 0, 0), scarf_b3=(7, 0, 0), cape_1=(40, 0, 0), cape_2=(6, 0, 0), cape_3=(-4, 0, 0),
              fin_r=(0, -38, 0), fin_l=(0, 38, 0)),
    "fall": _p(scarf_a1=(118, 0, 0), scarf_a2=(-6, 0, 0), scarf_a3=(-4, 0, 0), scarf_a4=(-4, 0, 0),
               scarf_b1=(112, 0, 0), scarf_b2=(-5, 0, 0), scarf_b3=(-4, 0, 0), cape_1=(90, 0, 0), cape_2=(-4, 0, 0), cape_3=(-4, 0, 0)),
}


def main() -> int:
    bones = build_model()
    am.pack_uvs(bones, TEX_W, TEX_H)
    tex, glow = am.paint_texture(bones, paint_face, TEX_W, TEX_H)
    GEO_PATH.parent.mkdir(parents=True, exist_ok=True)
    GEO_PATH.write_bytes(am.pretty(am.geo_json(bones, NAME, TEX_W, TEX_H)).encode("utf-8"))
    ANIM_PATH.parent.mkdir(parents=True, exist_ok=True)
    ANIM_PATH.write_bytes(am.pretty({"format_version": "1.8.0", "animations": {}}).encode("utf-8"))
    save_png(tex, TEX_PATH)
    save_png(glow, GLOW_PATH)
    for piece, rows in PIECES.items():
        img = am.sprite(rows, ICON)
        save_png(img, TEX / "item" / f"{NAME}_{piece}.png")
        save_png(am.trim_of(img), TEX / "item" / f"{NAME}_{piece}_trim.png")
    cubes = sum(len(b.cubes) for b in bones)
    print(f"wrote {GEO_PATH.name} ({len(bones)} bones, {cubes} cubes), {TEX_PATH.name} ({TEX_W}x{TEX_H}), glowmask, 4 icons, 4 trims")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
