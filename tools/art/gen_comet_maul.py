"""Comet Maul, the meteor-forged greathammer: textures/item/comet_maul.png (32x32)
plus comet_maul_glow1..3.png, the same sprite with the fissures in the meteorite
head heating up with Resonance (stage 1 embers, stage 3 white hot with sparks).

Same diagonal layout as Meridian (gen_meridian.py), handle bottom left, head top
right, light from the top left:

    u = x - y        along the haft (+ towards the head)
    v = x + y - 31   across it (0 is the haft's centre line, negative the upper-left side)

One unit of u or v is half a pixel along the diagonal (0.707 px of true length).
The grip is where Meridian's is (texels (8.2, 22.8) and (5.6, 25.4)), so the
engine's shared animations (dash, parry, stagger) hold both weapons alike.

    haft    Driftwood: silver wood with a cyan vein, three lines wide
    grip    Meridian's blue cord, u -21 to -12
    bands   Nebulite (violet to cyan) rings above the grip, mid haft, and a collar
    pommel  a knuckle of meteorite with an ember in it
    head    a block of meteorite across the haft, u 10 to 21, v -8 to 8: pitted
            dark rock with fissures (where the glow runs), both striking faces
            shod in Nebulite, a Starshard set in the crown

Run:  python tools/art/gen_comet_maul.py
"""
from __future__ import annotations

import numpy as np

from common import PREVIEWS, TEX, blank, outline_mask, over, rel, rgb, save_png, upscale

S = 32

# ---------------------------------------------------------------- palette
ROCK = {
    "hi": rgb("#a08a86"),
    "light": rgb("#7a6468"),
    "mid": rgb("#56444f"),
    "shade": rgb("#3d3040"),
    "deep": rgb("#2a2131"),
    "outline": rgb("#150f1c"),
}
NEB = {  # Nebulite: cyan on the lit side, violet in the shade (gen_materials.py)
    "hi": rgb("#e0fcff"),
    "cyan": rgb("#7fe6f6"),
    "blue": rgb("#6aa8f2"),
    "violet": rgb("#7a62e6"),
    "deep": rgb("#4b3fae"),
    "outline": rgb("#1b1540"),
}
WOOD = {  # Driftwood: petrified silver wood
    "hi": rgb("#e4e8ee"),
    "light": rgb("#c3cad6"),
    "mid": rgb("#9aa3b4"),
    "shade": rgb("#737c90"),
    "vein": rgb("#7fd8e6"),
    "outline": rgb("#2e3346"),
}
GRIP = {  # Meridian's cord
    "cord": rgb("#6b77b0"),
    "wrap": rgb("#454c82"),
    "deep": rgb("#2d3160"),
    "outline": rgb("#191a36"),
}
SHARD = {  # Starshard crystal in the crown
    "hi": rgb("#ffffff"),
    "light": rgb("#c4fff6"),
    "mid": rgb("#60e8d8"),
    "dark": rgb("#2a9aa0"),
}
# Fissure light by stage (0 = cold): dull rust at rest, ember, bright orange, white hot.
FISSURE = [rgb("#5c2a24"), rgb("#c2461c"), rgb("#ff8a2e"), rgb("#fff0c8")]
FISSURE_EDGE = [rgb("#48242a"), rgb("#8a3420"), rgb("#e0601e"), rgb("#ffb347")]
SPARK = rgb("#ffffff")
SPARK_ARM = rgb("#fff0c8")

# ---------------------------------------------------------------- geometry
U_POMMEL0, U_POMMEL1 = -25, -22
U_GRIP0, U_GRIP1 = -21, -12
U_HAFT1 = 11               # the haft runs into the head here
U_HEAD0, U_HEAD1 = 11, 20  # the head, along the haft
V_HEAD = 11                # the head, across: -11 to 11 (a greathammer is a T)
V_FACE = 9                 # |v| from here out is the Nebulite-shod striking face
BANDS = [(-11, -10), (-1, 0), (8, 10)]
# fissures in the head, as (u, v) polylines; the glow stages light them in this order
FISSURES = [
    [(12, -2), (14, -1), (15, 1), (16, 3), (18, 4)],        # the main split across the middle
    [(14, -1), (15, -4), (14, -6), (15, -8)],               # toward the upper face
    [(16, 3), (15, 6), (16, 8)],                            # toward the lower face
    [(17, -3), (18, -5), (19, -6)],                         # a crack under the crown
    [(13, 5), (12, 7)],                                     # a chip by the lower face
]


def uv_grid():
    ys, xs = np.mgrid[0:S, 0:S]
    return xs - ys, xs + ys - (S - 1), xs, ys


def head_mask():
    """The meteorite block: chamfered, with pits bitten out of its edges."""
    u, v, _, _ = uv_grid()
    av = np.abs(v)
    m = (u >= U_HEAD0) & (u <= U_HEAD1) & (av <= V_HEAD)
    # chamfer the four corners of the block
    m &= (u - U_HEAD0) + (V_HEAD - av) >= 1
    m &= (U_HEAD1 - u) + (V_HEAD - av) >= 1
    # pits and notches: meteorite is never a clean casting
    for pu, pv in ((U_HEAD1, -5), (U_HEAD1, 5), (U_HEAD0, -7), (U_HEAD0, 6), (U_HEAD1 - 1, 2)):
        m &= ~((u == pu) & (v == pv))
    return m


def face_mask():
    """The striking faces at both ends of the head, shod in Nebulite."""
    u, v, _, _ = uv_grid()
    return head_mask() & (np.abs(v) >= V_FACE)


def fissure_mask(upto: int):
    """Pixels on the first `upto` fissures (a diagonal step counts as connected)."""
    u, v, _, _ = uv_grid()
    m = np.zeros((S, S), dtype=bool)
    for line in FISSURES[:upto]:
        for (u0, v0), (u1, v1) in zip(line, line[1:]):
            n = max(abs(u1 - u0), abs(v1 - v0))
            for k in range(n + 1):
                uu = round(u0 + (u1 - u0) * k / n)
                vv = round(v0 + (v1 - v0) * k / n)
                # a (u, v) pair names a pixel only when u + v is odd (x = (u+v+31)/2)
                for du, dv in ((0, 0), (1, 0), (0, 1)):
                    cand = (u == uu + du) & (v == vv + dv) & ((uu + du + vv + dv) % 2 == 1)
                    if cand.any():
                        m |= cand
                        break
    return m & head_mask() & ~face_mask()


def shard_mask():
    """The Starshard in the crown: a small diamond on the haft's line at the top of the head."""
    u, v, _, _ = uv_grid()
    return (np.abs(u - (U_HEAD1 - 1)) + np.abs(v) <= 2) & head_mask()


def build_base(stage: int = 0) -> np.ndarray:
    img = blank(S, S)
    u, v, xs, ys = uv_grid()
    av = np.abs(v)
    part = np.zeros((S, S), dtype=int)  # 1 wood, 2 grip, 3 band, 4 pommel, 5 head rock, 6 face, 7 shard
    col = np.zeros((S, S, 3), dtype=int)

    # ---- haft: driftwood, lit on the upper-left line, a cyan vein on the centre line now and then
    haft = (av <= 1) & (u >= U_POMMEL1) & (u <= U_HAFT1)
    col[haft & (v == -1)] = WOOD["light"]
    col[haft & (v == 0)] = WOOD["mid"]
    col[haft & (v == 1)] = WOOD["shade"]
    col[haft & (v == -1) & (u % 7 == 0)] = WOOD["hi"]
    col[haft & (v == 0) & ((u % 9 == 2) | (u % 9 == 3))] = WOOD["vein"]
    part[haft] = 1

    # ---- grip: Meridian's cord
    grip = (av <= 1) & (u >= U_GRIP0) & (u <= U_GRIP1)
    phase = (u + v) % 4
    grip_col = np.where(phase[..., None] < 2, GRIP["cord"], GRIP["wrap"])
    grip_col = np.where(((v == 1) & (phase >= 2))[..., None], GRIP["deep"], grip_col)
    col[grip] = grip_col[grip]
    part[grip] = 2

    # ---- Nebulite bands, a little proud of the haft
    for b0, b1 in BANDS:
        band = (u >= b0) & (u <= b1) & (av <= 2)
        col[band] = NEB["violet"]
        col[band & (v < 0)] = NEB["cyan"]
        col[band & (v == -2)] = NEB["hi"]
        col[band & (v == 2)] = NEB["deep"]
        part[band] = 3

    # ---- pommel: a knuckle of meteorite with an ember
    pommel = (u >= U_POMMEL0) & (u <= U_POMMEL1) & (av <= 2) & ~((u == U_POMMEL0) & (av == 2))
    col[pommel] = ROCK["mid"]
    col[pommel & (v < 0)] = ROCK["light"]
    col[pommel & (v == 2)] = ROCK["shade"]
    col[pommel & (u == U_POMMEL0 + 1) & (v == -1)] = FISSURE[max(1, stage)]
    col[pommel & (u == U_POMMEL0 + 3) & (v == -1)] = ROCK["hi"]
    part[pommel] = 4

    # ---- head: rock shaded from the lit top-left corner (v < 0, u high) to the deep lower-right
    head = head_mask()
    shade = (-v * 0.42 + (u - 15.5) * 0.45)
    rock = np.select([shade > 3.2, shade > 1.0, shade > -1.6, shade > -3.8],
                     [0, 1, 2, 3], default=4)
    tones = [ROCK["hi"], ROCK["light"], ROCK["mid"], ROCK["shade"], ROCK["deep"]]
    for i, t in enumerate(tones):
        col[head & (rock == i)] = t
    # pitting: a scatter of darker specks keeps the rock from reading as a smooth casting
    rng = np.random.default_rng(3)
    specks = head & (rng.random((S, S)) < 0.16)
    col[specks & (rock <= 2)] = ROCK["shade"]
    col[specks & (rock > 2)] = ROCK["deep"]
    part[head] = 5

    # ---- striking faces shod in Nebulite
    face = face_mask()
    col[face] = NEB["blue"]
    col[face & (v < 0)] = NEB["cyan"]
    col[face & (v == -V_HEAD)] = NEB["hi"]
    col[face & (v > 0) & (u < 16)] = NEB["violet"]
    col[face & (v > 0) & (u >= 16)] = NEB["blue"]
    col[face & (v == V_HEAD)] = NEB["deep"]
    part[face] = 6

    # ---- fissures: cold rust at rest; the glow stages light them
    lit = {0: 0, 1: 1, 2: 3, 3: 3}[stage]
    all_f = fissure_mask(len(FISSURES))
    col[all_f] = FISSURE[0]
    if stage > 0:
        hot = fissure_mask(lit)
        col[hot] = FISSURE[stage]
        # the rock right beside a lit fissure catches its light
        near = np.zeros_like(hot)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            near |= np.roll(np.roll(hot, dy, axis=0), dx, axis=1)
        near &= head & ~face & ~all_f
        near &= ~hot
        mixw = {1: 0.0, 2: 0.22, 3: 0.3}[stage]
        col[near] = np.clip((col[near] * (1 - mixw) + np.array(FISSURE_EDGE[stage]) * mixw), 0, 255).astype(int)

    # ---- Starshard in the crown
    shard = shard_mask()
    col[shard] = SHARD["mid"]
    col[shard & (v < 0)] = SHARD["light"]
    col[shard & (v > 0)] = SHARD["dark"]
    col[shard & (u == U_HEAD1 - 1) & (v == -2)] = SHARD["hi"]
    part[shard] = 7

    filled = part > 0
    img[filled, :3] = col[filled]
    img[filled, 3] = 255

    # ---- outline by the part it wraps
    ol = outline_mask(filled)
    part_of = np.zeros((S, S), dtype=int)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        part_of = np.maximum(part_of, np.roll(np.roll(part, -dy, axis=0), -dx, axis=1))
    img[ol & (part_of == 1), :3] = WOOD["outline"]
    img[ol & (part_of == 2), :3] = GRIP["outline"]
    img[ol & ((part_of == 3) | (part_of == 6)), :3] = NEB["outline"]
    img[ol & ((part_of == 4) | (part_of == 5) | (part_of == 7)), :3] = ROCK["outline"]
    img[ol, 3] = 255
    return img


def build_glow(stage: int) -> np.ndarray:
    """Stage 1..3: the fissures heat up (more of them, hotter); stage 3 adds white-hot faces' rims and a glint."""
    img = build_base(stage)
    if stage >= 2:
        u, v, xs, ys = uv_grid()
        face = face_mask()
        rim = face & (np.abs(v) == V_FACE)
        img[rim, :3] = FISSURE_EDGE[stage]
    if stage == 3:
        # a four-point glint on the crown's Starshard
        u, v, xs, ys = uv_grid()
        cy, cx = np.argwhere(shard_mask() & (u == U_HEAD1 - 1) & (v == -2))[0]
        arms = {(0, 0): SPARK, (-1, 0): SPARK_ARM, (1, 0): SPARK_ARM, (0, -1): SPARK_ARM, (0, 1): SPARK_ARM,
                (2, 0): SPARK_ARM, (0, -2): SPARK_ARM}
        for (dx, dy), c in arms.items():
            x, y = cx + dx, cy + dy
            if 0 <= x < S and 0 <= y < S:
                img[y, x, :3] = c
                img[y, x, 3] = 255
        # two sparks thrown off the upper face
        for (x, y) in ((15, 1), (12, 4)):
            if img[y, x, 3] == 0:
                img[y, x, :3] = FISSURE[3]
                img[y, x, 3] = 255
    return img


def key_points():
    """Texel coordinates (x right, y down) of the points the animation rig and the effects use."""
    def t(u, v):
        return ((u + v + 31) / 2.0, (v - u + 31) / 2.0)
    return {"pommel": t(-24, 0), "head_near": t(9, 0), "head_far": t(21, 0), "head_half_width": V_HEAD * 0.7071}


def debug_strip(images, path):
    k = 8
    pad = 10
    cell = S * k + pad
    h = S * k + pad * 2 + S * 2 + pad
    w = pad + cell * len(images)
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = 110
    out[..., 3] = 255
    for i, im in enumerate(images):
        x0 = pad + i * cell
        over(out, upscale(im, k), x0, pad)
        over(out, im, x0, pad * 2 + S * k)
        over(out, upscale(im, 2), x0 + S + pad, pad * 2 + S * k)
    save_png(out, path)


def main():
    out = TEX / "item"
    base = build_base(0)
    stages = [build_glow(st) for st in (1, 2, 3)]
    paths = [save_png(base, out / "comet_maul.png")]
    for st, im in zip((1, 2, 3), stages):
        paths.append(save_png(im, out / f"comet_maul_glow{st}.png"))
    debug_strip([base] + stages, PREVIEWS / "comet_maul_stages.png")
    for p in paths:
        print("wrote", rel(p))
    print("key points:", key_points())


if __name__ == "__main__":
    main()
