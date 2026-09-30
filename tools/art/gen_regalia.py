"""Choir Regalia: the GeckoLib armor, its textures, item icons and tier trims, plus the Hymn's ring, the Aligned
glyph and the effect icon.

Writes (under src/main/resources/assets/cosmicbreach/):
    geo/armor/choir_regalia.geo.json              one model for all four pieces (GeckoLib armor bones)
    animations/armor/choir_regalia.animation.json empty: the game turns the halo and the rings, sways the robe
    textures/armor/choir_regalia.png              ivory robe and plate, gold, 1 texel a unit
    textures/armor/choir_regalia_glowmask.png     the engraved orbit lines and their dots, the halo, the rings and
                                                  gems (the game adds them as gold light and pulses them on Vesper's
                                                  beat)
    textures/item/choir_regalia_<piece>.png       16x16 icons, and <piece>_trim.png (the tier trim layer)
    textures/fx/hymn_ring.png                     the Hymn of Alignment's ring on the ground (white, shape in alpha)
    textures/fx/aligned_glyph.png                 the glyph drawn over Aligned enemies (white, shape in alpha)
    textures/mob_effect/aligned.png               the Aligned effect's icon (18x18)

The look (GDD 5.1), the top tier's: a robe and plate hybrid in ivory and gold, engraved with orbit lines (open arcs
with small planets on them; nothing that reads as a letter, a digit or a cross). A circlet with a tall crest and two
lesser points, and a halo raised above and behind the head: a thin bright gold circle on which three small round
rings orbit (the game spins them, faster when an echo is primed). A tall fan collar behind the head. Layered
pauldrons, each with a thin gold ring standing out of it at a slant (turning slowly), bell sleeves. A long mantle
down the back in three hinged pieces with a flared gold hem, and a front skirt that hangs between the legs (both
lag behind the motion, and the skirt follows the forward leg, so the robe never turns into trousers). Silhouette: a
tall robed figure with a halo.

Run:  python tools/art/gen_regalia.py   (then preview_sets.py for the review sheets)
"""
from __future__ import annotations

import math

import numpy as np

import armor_model as am
from armor_model import Bone, Cube, grid, hash01, mirror_bone, ring_cubes, texel_point
from common import ASSETS, TEX, rgb, save_png, smoothstep, supersample, white_alpha

TEX_W, TEX_H = 128, 128
NAME = "choir_regalia"
GEO_PATH = ASSETS / "geo" / "armor" / f"{NAME}.geo.json"
ANIM_PATH = ASSETS / "animations" / "armor" / f"{NAME}.animation.json"
TEX_PATH = TEX / "armor" / f"{NAME}.png"
GLOW_PATH = TEX / "armor" / f"{NAME}_glowmask.png"

# ------------------------------------------------------------------ palette (ivory and gold)
IVORY = [rgb(h) for h in ("#b4a78c", "#d3c7ab", "#e6ddc8", "#f2ecdf", "#fdfaf2")]
GOLD = [rgb(h) for h in ("#7a5418", "#a8761f", "#d19d34", "#ecc35a", "#fbe29a")]
BRIGHT = rgb("#f5c460")        # the halo and the rings: a clear bright gold (their glow adds the rest)
LINE = rgb("#d6ad55")          # the engraved orbit lines on ivory: fine, a lighter gold
GEM = rgb("#fff4c8")
LINE_GLOW = (110, 84, 34)      # added light: the engravings glow softly, the rings and gems more
DOT_GLOW = (200, 150, 60)
RING_GLOW = (120, 84, 26)

# Rest angles the game's RegaliaLook starts from (Bedrock degrees about X; -90 hangs a back piece straight down).
MANTLE_REST = (-89.0, -1.0, -1.0)
HALO_CENTRE = (0.0, 37.2, 5.2)
HALO_RADIUS = 4.2
HALO_TILT = -14.0
RING_RADIUS = 1.25
SHOULDER_RING = (-7.2, 24.9, 0.0)
SHOULDER_RING_RADIUS = 2.4
SHOULDER_RING_TURN = (8.0, 0.0, -22.0)

am.SHAREABLE = {"halo_line", "ring"}


# ------------------------------------------------------------------ model
def hanging(prefix, parent, y, z0, widths, lengths, rest, paint):
    """The mantle: segments extending back (+Z) from their pivots in the model file, hung by their rest angles, each
    hinged at the end of the one before."""
    bones = []
    z = z0
    for i, (w, length) in enumerate(zip(widths, lengths)):
        name = f"{prefix}{i + 1}"
        cube = Cube((-w / 2, y - 0.55, z), (w, 1.1, length), paint, (f"seg{i}", f"of{len(lengths)}"), tag=name)
        # its edges fold back (+Y here, away from the body once it hangs), deep enough that the mantle is a cloak from
        # the side and not a line (F1: G2c's open note)
        flanges = [am.thin_cube((sx, y + 0.1, z), (0.7, 2.6, length), paint, (f"seg{i}", "flange"), thickness_axis=0,
                                thin=0.7, tag=f"{name}_{side}")
                   for side, sx in (("r", -w / 2), ("l", w / 2 - 0.7))]
        cubes = [cube, *flanges]
        if i == len(lengths) - 1:
            # the flared hem: a gold band along the foot, a little wider and as deep as the flanges
            cubes.append(Cube((-w / 2 - 0.4, y - 0.65, z + length - 1.2), (w + 0.8, 3.3, 1.2), "gold", ("hem",),
                              tag=f"{name}_hem"))
        bones.append(Bone(name, parent if i == 0 else f"{prefix}{i}", (0.0, y, z), (rest[i], 0.0, 0.0), cubes))
        z += length
    return bones


def ring_bone(name, parent, centre, radius, n, thin, plane, turn=None, bead=True, bead_axis="up"):
    """A thin round ring of `n` bars around `centre`, with a bead on its rim so its turning shows (on top, or for a
    ring lying flat, at its front)."""
    cubes = ring_cubes(centre, radius, n, thin, plane, "ring", f"{name}_", width=thin)
    if bead:
        cx, cy, cz = centre
        bx, by, bz = (cx, cy + radius, cz) if bead_axis == "up" else (cx, cy, cz - radius)
        cubes.append(Cube((bx - 0.5, by - 0.5, bz - 0.5), (1.0, 1.0, 1.0), "gem", tag=f"{name}_bead", inflate=-0.15))
    return Bone(name, parent, centre, turn, cubes)


def build_model() -> list[Bone]:
    head = Bone("armorHead", None, (0, 24, 0), cubes=[
        Cube((-4.5, 27.8, -4.5), (9, 1.8, 9), "gold", ("band",), tag="circlet"),
        am.thin_cube((-1.3, 29.4, -5.0), (2.6, 6.2, 0.8), "gold", ("crest",), thickness_axis=2, thin=0.8, tag="crest"),
        am.thin_cube((-3.6, 29.4, -4.9), (1.4, 3.6, 0.7), "gold", ("point",), thickness_axis=2, thin=0.7, tag="point_r"),
        am.thin_cube((2.2, 29.4, -4.9), (1.4, 3.6, 0.7), "gold", ("point",), thickness_axis=2, thin=0.7, tag="point_l"),
    ])
    tilt = Bone("halo_tilt", "armorHead", HALO_CENTRE, (HALO_TILT, 0.0, 0.0))
    halo = Bone("halo", "halo_tilt", HALO_CENTRE, cubes=ring_cubes(HALO_CENTRE, HALO_RADIUS, 28, 0.3, "xy", "halo_line", "orbit",
                                                                   width=0.3))
    rings = []
    for i in range(3):
        a = math.radians(i * 120.0)
        c = (HALO_CENTRE[0] + HALO_RADIUS * math.sin(a), HALO_CENTRE[1] + HALO_RADIUS * math.cos(a), HALO_CENTRE[2])
        rings.append(ring_bone(f"halo_ring_{i + 1}", "halo", c, RING_RADIUS, 14, 0.32, "xy"))

    body = Bone("armorBody", None, (0, 24, 0), cubes=[
        Cube((-4, 12, -2), (8, 12, 4), "robe", ("vestment",), inflate=0.6, tag="vestment"),
        am.thin_cube((-3.7, 16.0, -3.1), (7.4, 7.0, 0.9), "ivory_plate", ("breast",), thickness_axis=2, thin=0.9, tag="breast"),
        Cube((-4.4, 22.8, -2.85), (8.8, 2.4, 5.7), "gold", ("collar",), tag="collar"),
        Cube((-4.6, 11.8, -2.8), (9.2, 1.8, 5.6), "gold", ("belt",), tag="belt"),
        Cube((-0.7, 12.0, -3.5), (1.4, 1.4, 1.0), "gem", tag="clasp", inflate=-0.1),
    ])
    # the tall fan collar behind the head, leaning back
    fan = Bone("collar_fan", "armorBody", (0.0, 23.6, 3.4), (-16.0, 0.0, 0.0), cubes=[
        am.thin_cube((-4.3, 23.6, 3.05), (8.6, 5.4, 0.7), "fan", (), thickness_axis=2, thin=0.7, tag="fan"),
        am.thin_cube((-5.8, 25.6, 3.0), (2.0, 3.8, 0.8), "gold", ("fan_tip",), thickness_axis=2, thin=0.8, tag="fan_tip_r"),
        am.thin_cube((3.8, 25.6, 3.0), (2.0, 3.8, 0.8), "gold", ("fan_tip",), thickness_axis=2, thin=0.8, tag="fan_tip_l"),
    ])
    mantle = hanging("mantle_", "armorBody", 23.4, 3.3, (7.2, 7.8, 8.8), (6.8, 6.4, 6.2), MANTLE_REST, "mantle")
    skirt_1 = Bone("skirt_1", "armorBody", (0.0, 12.2, -3.05), cubes=[
        am.thin_cube((-2.9, 5.6, -3.35), (5.8, 6.6, 0.6), "skirt", ("seg0",), thickness_axis=2, thin=0.6, tag="skirt_1"),
    ])
    skirt_2 = Bone("skirt_2", "skirt_1", (0.0, 5.6, -3.05), cubes=[
        am.thin_cube((-3.3, 0.9, -3.4), (6.6, 4.7, 0.6), "skirt", ("seg1",), thickness_axis=2, thin=0.6, tag="skirt_2"),
        am.thin_cube((-3.6, 0.8, -3.5), (7.2, 1.1, 0.8), "gold", ("hem",), thickness_axis=2, thin=0.8, tag="skirt_hem"),
    ])

    r_arm = Bone("armorRightArm", None, (-5, 22, 0), cubes=[
        Cube((-8, 12, -2), (4, 12, 4), "robe", ("sleeve",), inflate=0.3, tag="sleeve_r"),
        Cube((-8.9, 10.2, -2.9), (5.8, 4.2, 5.8), "robe", ("cuff",), tag="cuff_r"),
        Cube((-9.2, 19.8, -3.0), (5.2, 3.2, 6.0), "ivory_plate", ("rim",), tag="pauldron_r"),
        Cube((-8.8, 22.7, -2.6), (4.4, 1.3, 5.2), "gold", ("layer",), tag="pauldron_top_r"),
    ])
    sring_r = Bone("shoulder_ring_r", "armorRightArm", SHOULDER_RING, SHOULDER_RING_TURN)
    sspin_r = ring_bone("shoulder_ring_r_spin", "shoulder_ring_r", SHOULDER_RING, SHOULDER_RING_RADIUS, 16, 0.32, "xz",
                        bead_axis="xz")
    l_arm = mirror_bone(r_arm, "armorLeftArm", None)
    sring_l = mirror_bone(sring_r, "shoulder_ring_l", "armorLeftArm")
    sspin_l = mirror_bone(sspin_r, "shoulder_ring_l_spin", "shoulder_ring_l")

    r_leg = Bone("armorRightLeg", None, (-1.9, 12, 0), cubes=[
        Cube((-3.9, 1, -2), (4, 11, 4), "robe", ("leg",), inflate=0.25, tag="leg_r"),
        am.thin_cube((-4.85, 0.8, -2.4), (0.6, 11.0, 4.8), "robe", ("panel",), thickness_axis=0, thin=0.6, tag="panel_s_r"),
    ])
    l_leg = mirror_bone(r_leg, "armorLeftLeg", None)

    r_boot = Bone("armorRightBoot", None, (-1.9, 12, 0), cubes=[
        Cube((-3.9, 0, -2), (4, 4, 4), "ivory_plate", ("boot",), inflate=0.45, tag="boot_r"),
        Cube((-3.6, 0, -3.9), (3.4, 1.4, 1.6), "gold", ("toe",), tag="toe_r"),
    ])
    l_boot = mirror_bone(r_boot, "armorLeftBoot", None)
    bones = [head, tilt, halo, *rings, body, fan, *mantle, skirt_1, skirt_2, r_arm, sring_r, sspin_r, l_arm, sring_l, sspin_l,
             r_leg, l_leg, r_boot, l_boot]
    am.check_uv(bones)
    return bones


# ------------------------------------------------------------------ painting
MANTLE_TOP = 3.3


def face_uv(c: Cube, face: str, col: int, row: int):
    """A texel's place on its face's plane, in model units: across (from the cube's middle) and up (the model's
    height; for the mantle, down it from the collar)."""
    x, y, z = texel_point(c, face, col, row)
    mx = c.origin[0] + c.size[0] / 2
    mz = c.origin[2] + c.size[2] / 2
    if face in ("front", "back"):
        return x - mx, y
    if face in ("xneg", "xpos"):
        return z - mz, y
    return x - mx, z - MANTLE_TOP


# The engravings: open orbit arcs (part of a tilted ellipse) with planets (dots) on them, per region.
# (region test, centre u, centre v, a, b, tilt, arc from, arc to (radians), planet angles)
# Orbit paths engraved as dotted lines (a gold point every two texels along a tilted ellipse) with a brighter
# planet on each, around a star: the look of a star chart. Dotted, they read as paths, never as a letter, a digit,
# a cross or a closed shape. (centre u, centre v, a, b, tilt, arc from, arc to, planet angles); the mantle's v runs
# down its length from the collar.
FULL = (0.0, 2 * math.pi)
ORBITS = {
    "mantle": [(0.0, 9.6, 2.6, 6.2, 0.18, *FULL, (0.9,)), (0.0, 9.6, 1.4, 3.4, 0.18, *FULL, (3.8,))],
    "skirt": [(0.0, 7.0, 2.1, 4.4, -0.12, *FULL, (5.3,))],
    "panel": [(0.0, 6.4, 1.6, 4.0, 0.2, 0.6, 2 * math.pi - 0.6, (2.3,))],
}
STARS = {"mantle": [(0.0, 9.6)], "skirt": [], "panel": []}
DOT_SPACING = 2.0


def _dotted(cu, cv, a, b, tilt, t0, t1):
    """Points every DOT_SPACING along the arc."""
    ct, st = math.cos(tilt), math.sin(tilt)
    fine = [t0 + (t1 - t0) * i / 720 for i in range(721)]
    pts = []
    run = DOT_SPACING
    last = None
    for t in fine:
        eu, ev = a * math.cos(t), b * math.sin(t)
        p = (cu + eu * ct - ev * st, cv + eu * st + ev * ct)
        if last is not None:
            run += math.dist(p, last)
        last = p
        if run >= DOT_SPACING:
            pts.append(p)
            run = 0.0
    return pts


ARCS = {region: [(_dotted(cu, cv, a, b, tilt, t0, t1),
                  [(cu + a * math.cos(t) * math.cos(tilt) - b * math.sin(t) * math.sin(tilt),
                    cv + a * math.cos(t) * math.sin(tilt) + b * math.sin(t) * math.cos(tilt)) for t in planets])
                 for (cu, cv, a, b, tilt, t0, t1, planets) in orbits]
        for region, orbits in ORBITS.items()}


def engraving(c: Cube, face: str, col: int, row: int, region: str):
    """'dot', 'line' or None for a texel of a broad face in an engraved region: each path's points as single
    texels, its planet and the star as bright ones."""
    u, v = face_uv(c, face, col, row)
    for (su, sv) in STARS[region]:
        if abs(u - su) < 0.5 and abs(v - sv) < 0.5:
            return "dot"
    for points, planets in ARCS[region]:
        for (pu, pv) in planets:
            if abs(u - pu) < 0.5 and abs(v - pv) < 0.5:
                return "dot"
    for points, planets in ARCS[region]:
        for (pu, pv) in points:
            if abs(u - pu) < 0.5 and abs(v - pv) < 0.5:
                return "line"
    return None


def paint_face(c: Cube, face: str, w: int, h: int):
    cols = grid(w, h)
    glows = grid(w, h)
    side = face in ("xneg", "xpos", "front", "back")
    rules = set(c.rules)

    def engrave(region, col, row, colour):
        mark = engraving(c, face, col, row, region)
        if mark == "dot":
            return GOLD[4], DOT_GLOW
        if mark == "line":
            return LINE, LINE_GLOW
        return colour, None

    if c.paint in ("robe", "mantle", "skirt", "fan"):
        for row in range(h):
            for col in range(w):
                k = 3 - (1 if hash01(c.tag, face, col, row) < 0.16 else 0)
                if face == "top":
                    k = 4
                elif face == "bottom":
                    k = 2
                colour = IVORY[k]
                glow = None
                # engravings only on the broad faces seen from outside: the mantle's back, the skirt's front, the
                # leg panels' outer sides (small faces are too few texels for an arc to read)
                region = None
                if c.paint == "mantle" and face == "top":
                    region = "mantle"
                elif c.paint == "skirt" and face == "front":
                    region = "skirt"
                elif "panel" in rules and face in ("xneg", "xpos"):
                    region = "panel"
                if region is not None:
                    colour, glow = engrave(region, col, row, colour)
                cols[row][col] = colour
                glows[row][col] = glow
        if c.paint == "mantle" and "flange" in rules:
            for row in range(h):
                for col in range(w):
                    cols[row][col] = GOLD[2] if (row + col) % 5 else GOLD[3]     # its folded edges are gold
        if c.paint == "skirt" and face in ("front", "back"):
            for row in range(h):
                cols[row][0] = GOLD[2]
                cols[row][w - 1] = GOLD[2]
        if c.paint == "fan":
            for row in range(h):
                for col in range(w):
                    if face in ("front", "back") and (row == 0 or col in (0, w - 1)):
                        cols[row][col] = GOLD[3]
        if "panel" in rules and side:
            for col in range(w):
                cols[h - 1][col] = GOLD[2]
                cols[h - 2][col] = GOLD[1]
        if "cuff" in rules and side:
            for col in range(w):
                cols[h - 1][col] = GOLD[3]
                cols[0][col] = GOLD[2]
        if "sleeve" in rules and side:
            for col in range(w):
                cols[0][col] = GOLD[3]
        return cols, glows

    if c.paint == "ivory_plate":
        for row in range(h):
            for col in range(w):
                k = 3
                if face == "top" or (side and row == 0):
                    k = 4
                elif face == "bottom" or (side and row == h - 1 and h > 2):
                    k = 1
                colour = IVORY[k]
                glow = None
                rim = side and (row == 0 or (row == h - 1 and h > 3))
                if rim:
                    colour = GOLD[3] if row == 0 else GOLD[2]
                elif "breast" in rules and face == "front" and abs(col + 0.5 - w / 2) <= 1.0 and abs(row + 0.5 - h / 2) <= 1.0:
                    colour, glow = (GOLD[4], DOT_GLOW)      # a gold star set in the middle of the breastplate
                cols[row][col] = colour
                glows[row][col] = glow
        return cols, glows

    if c.paint == "gold":
        for row in range(h):
            for col in range(w):
                k = 2
                if face == "top" or (side and row == 0):
                    k = 4
                elif face == "bottom" or (side and row == h - 1 and h > 1):
                    k = 1
                elif hash01(c.tag, face, col, row) < 0.2:
                    k = 3
                cols[row][col] = GOLD[k]
        if "crest" in rules and face == "front" and h > 2:
            cx = w // 2
            cols[1][cx] = GEM
            glows[1][cx] = DOT_GLOW
        if "collar" in rules and face == "front":
            for col in range(1, w - 1, 3):
                cols[h // 2][col] = GOLD[4]
                glows[h // 2][col] = LINE_GLOW
        return cols, glows

    if c.paint in ("halo_line", "ring"):
        for row in range(h):
            for col in range(w):
                cols[row][col] = BRIGHT
                glows[row][col] = RING_GLOW
        return cols, glows

    if c.paint == "gem":
        for row in range(h):
            for col in range(w):
                cols[row][col] = GEM
                glows[row][col] = (255, 246, 214)
        return cols, glows

    raise ValueError(c.paint)


# ------------------------------------------------------------------ fx textures
def hymn_ring() -> np.ndarray:
    """The ring on the ground, 256x256, white with its shape in alpha: a bright band near the edge, a thin inner
    circle, twelve glyph marks between them and a faint wash inside."""
    size = 256

    def shape(x, y):
        u = (x - size / 2) / (size / 2)
        v = (y - size / 2) / (size / 2)
        r = np.sqrt(u * u + v * v)
        a = np.arctan2(v, u)
        band = smoothstep(0.035, 0.0, np.abs(r - 0.93)) * 1.0
        inner = smoothstep(0.018, 0.0, np.abs(r - 0.78)) * 0.8
        # glyph marks: short ticks and small circles at twelve angles between the two circles
        k = np.round(a / (math.pi / 6)) * (math.pi / 6)
        da = np.abs(a - k) * r
        ticks = smoothstep(0.018, 0.0, da) * smoothstep(0.06, 0.0, np.abs(r - 0.855)) * 0.9
        cx = 0.855 * np.cos(k + math.pi / 12)
        cy = 0.855 * np.sin(k + math.pi / 12)
        dot = np.sqrt((u - cx) ** 2 + (v - cy) ** 2)
        circles = smoothstep(0.012, 0.0, np.abs(dot - 0.03)) * 0.8
        wash = smoothstep(0.93, 0.2, r) * 0.10 * (r < 0.93)
        return np.clip(band + inner + ticks + circles + wash, 0.0, 1.0)

    return white_alpha(supersample(shape, size, size, 3))


def aligned_glyph() -> np.ndarray:
    """The glyph over Aligned enemies, 64x64: a small orbit (ring) crossed by an arrow pointing down, white in alpha."""
    size = 64

    def shape(x, y):
        u = (x - size / 2) / (size / 2)
        v = (y - size / 2) / (size / 2)
        r = np.sqrt((u / 1.0) ** 2 + (v / 0.55) ** 2)
        ring = smoothstep(0.09, 0.0, np.abs(r - 0.72))
        stem = smoothstep(0.07, 0.0, np.abs(u)) * (v > -0.85) * (v < 0.45)
        head = ((np.abs(u) < (0.9 - v) * 0.6) & (v > 0.35) & (v < 0.9)) * 1.0
        core = smoothstep(0.2, 0.0, np.sqrt(u * u + v * v)) * 0.6
        return np.clip(ring + stem + head + core, 0.0, 1.0)

    return white_alpha(supersample(shape, size, size, 3))


EFFECT_ICON = [
    "..................",
    "......oooooo......",
    "....oo555555oo....",
    "...o5544444455o...",
    "..o54o......o45o..",
    "..o4o...33...o4o..",
    ".o54o...33...o45o.",
    ".o4o....33....o4o.",
    ".o4o....33....o4o.",
    ".o4o..3333333.o4o.",
    ".o54o..33333.o45o.",
    "..o4o...333..o4o..",
    "..o54o...3..o45o..",
    "...o5544444455o...",
    "....oo555555oo....",
    "......oooooo......",
    "..................",
    "..................",
]


def effect_icon() -> np.ndarray:
    pal = {"o": rgb("#3a2a10"), "3": rgb("#fff4c8"), "4": GOLD[2], "5": GOLD[3]}
    img = np.zeros((18, 18, 4), np.uint8)
    for y, line in enumerate(EFFECT_ICON):
        for x, ch in enumerate(line):
            if ch != ".":
                img[y, x, :3] = pal[ch]
                img[y, x, 3] = 255
    return img


# ------------------------------------------------------------------ item icons (16x16)
ICON = am.palette({
    "o": "#3a2a14",
    "i": IVORY[1], "I": IVORY[2], "j": IVORY[3], "J": IVORY[4],
    "g": GOLD[1], "G": GOLD[2], "h": GOLD[3], "H": GOLD[4],
    "e": GEM, "l": LINE,
})

CIRCLET = [
    "....oo....oo....",
    "...ohho..ohho...",
    "...oHeo..oeHo...",
    "....oo.oo.oo....",
    ".......ohho.....",
    "......oheHho....",
    ".......ohho.....",
    "..ooooooGoooooo.",
    ".ohhhhhhHhhhhhho",
    ".oGGGGGgeGgGGGGo",
    ".ogGGGGGGGGGGGgo",
    "..oo.......oo.o.",
    "................",
    "................",
    "................",
    "................",
]

VESTMENT = [
    "................",
    "..ooooo..ooooo..",
    ".ohhhhHooHhhhho.",
    ".oGjjJGhhGJjjGo.",
    ".oijJjJJJJjJjio.",
    "..oiJlllJJJjIo..",
    "..oilJJJlJjjIo..",
    "..oiJJJJJlhJIo..",
    "..oijJJJlJJjio..",
    "..ohhhhHeHhhho..",
    "..oiJjJJJJjJio..",
    "..oiJJlllJJJio..",
    "..oijlJJJlhjio..",
    "..ohhhhhhhhhho..",
    "..oooooooooooo..",
    "................",
]

TASSETS = [
    "................",
    "..oooooooooooo..",
    "..ohhhhHHhhhho..",
    "..oiJjJoojJjio..",
    "..oiJlJooJlJio..",
    "..oijJJooJJjio..",
    "..oiJJjooJjJio..",
    "..oiJhJooJhJio..",
    "..oijJJooJJjio..",
    "..oiJJjooJjJio..",
    "..oijJJooJJjio..",
    "..oGGGGooGGGGo..",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
]

SABATONS = [
    "................",
    "................",
    "................",
    "...oooo...oooo..",
    "...oJJo...oJJo..",
    "...ojJo...ojJo..",
    "...oGGo...oGGo..",
    "...ojJo...ojJo..",
    "...oijo...oijo..",
    "..ojJJjo.ojJJjo.",
    ".ohjJJIo.ohjJJIo",
    "oHhGGGgoohGGGgo.",
    "oooooooooooooo..",
    "................",
    "................",
    "................",
]

PIECES = {"circlet": CIRCLET, "vestment": VESTMENT, "tassets": TASSETS, "sabatons": SABATONS}


def _p(**bones):
    return {name: (list(rot), None, None) for name, rot in bones.items()}


# Offsets from the rest pose the game's RegaliaLook gives while moving (for the previews).
PREVIEW_POSES = {
    "rest": {},
    "walk": _p(mantle_1=(12, 0, 0), mantle_2=(-4, 0, 0), mantle_3=(-3, 0, 0), skirt_1=(-14, 0, 0), skirt_2=(6, 0, 0),
               halo=(0, 0, 40), shoulder_ring_r_spin=(0, 35, 0), shoulder_ring_l_spin=(0, 35, 0)),
    "run": _p(mantle_1=(24, 0, 0), mantle_2=(-9, 0, 0), mantle_3=(-7, 0, 0), skirt_1=(-26, 0, 0), skirt_2=(12, 0, 0),
              halo=(0, 0, 80)),
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
    save_png(hymn_ring(), TEX / "fx" / "hymn_ring.png")
    save_png(aligned_glyph(), TEX / "fx" / "aligned_glyph.png")
    save_png(effect_icon(), TEX / "mob_effect" / "aligned.png")
    cubes = sum(len(b.cubes) for b in bones)
    print(f"wrote {GEO_PATH.name} ({len(bones)} bones, {cubes} cubes), {TEX_PATH.name}, glowmask, 4 icons, 4 trims, "
          f"fx/hymn_ring.png, fx/aligned_glyph.png, mob_effect/aligned.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
