"""The Upper Reach's block textures (16x16, in textures/block/).

The Reach is daylight (GDD 2.2): white stone with gold flecks, pale gold grass,
turquoise crystal, colour from materials rather than glow.

    starfall_stone                 white stone, warm grey mottling, gold flecks
    polished_starfall_stone        smooth white slab with a groove and a lit bevel
    starfall_stone_bricks          small white bricks (4 courses), grey mortar
    glimmer_grass_top/_side        pale gold grass on Starfall Stone, a few glints
    spire_quartz                   faceted turquoise crystal
    starsteel_ore                  Starfall Stone with pale silver-gold nuggets
    starsteel_block                pale silver-gold plates
    meteorite                      charred iron-brown rock, ember seams, starsteel specks
    halo_moss / halo_moss_plant    hanging mint strands with gold motes (tip / body)
    starbloom                      the flower: a white-gold star on a teal stem
    starbloom_stage0..3            the crop's four stages
    breach_frame_top/_side         cobblestone ring piece with copper corners

Run:  python tools/art/gen_blocks_reach.py
"""
from __future__ import annotations

import numpy as np

from blockart import (brick_layout, by_share, fbm, from_ramp, ramp, rng_for, scatter, sprite, stamp,
                      wrap_shift)
from common import TEX, rel, rgb, save_png

OUT = TEX / "block"

# Ramps, darkest first.
STARFALL = ramp("#a79f90", "#c2bbac", "#d6d0c3", "#e4dfd4", "#efece3")
GOLD = ramp("#9b6a24", "#c98f35", "#e8b94f", "#f7dc86", "#fff4c8")
GRASS = ramp("#857a36", "#a69446", "#c3ac5b", "#d8c476", "#e9d992", "#f7edbd")
QUARTZ = ramp("#12524f", "#1b7671", "#259790", "#3db6ab", "#6dd6ca", "#aaf0e7", "#e6fffb")
STARSTEEL = ramp("#6b5a38", "#a88f55", "#d2b978", "#eadba8", "#f8f3e4", "#ffffff")
STEEL_PLATE = ramp("#7d7258", "#a99e80", "#cbc2a2", "#e0d8bb", "#eee8d2", "#fbf8ec")
METEOR = ramp("#1f1a18", "#2c2522", "#3a312c", "#4b3f37", "#5e5046", "#726257")
EMBER = ramp("#8f2f14", "#d05a22", "#ff9a3d", "#ffd27f")
MOSS = ramp("#236355", "#348676", "#4fa792", "#77c8b1", "#a9e6d2", "#dbfff3")
PETAL = ramp("#b5852f", "#e3bb5a", "#f7dc8c", "#fff6d8", "#ffffff")
STEM = ramp("#22523f", "#2f6d53", "#3f8a68", "#58a982", "#7cc79f")
TURQ = ramp("#157a73", "#2aa89f", "#5fd8cc", "#b5f7ef")
COBBLE = ramp("#4f4f52", "#646467", "#78787b", "#8d8d90", "#a3a3a6")
COPPER = ramp("#6e3620", "#9b4b2b", "#c0663f", "#dc8558", "#f2a77b", "#ffd0b0")


def fleck(img, rng, count, allowed=None, min_dist=4.5, kinds=(0, 1, 2)):
    """Gold flecks: a lit texel with a darker one below right, some with a second lit texel."""
    for x, y in scatter(rng, count, min_dist, allowed):
        kind = kinds[int(rng.integers(len(kinds)))]
        if kind == 0:      # single glint
            stamp(img, x, y, [(0, 0, GOLD[3]), (1, 1, GOLD[1])])
        elif kind == 1:    # two-texel diagonal
            stamp(img, x, y, [(0, 0, GOLD[4]), (1, 0, GOLD[2]), (1, 1, GOLD[1])])
        else:              # small L
            stamp(img, x, y, [(0, 0, GOLD[3]), (0, 1, GOLD[2]), (1, 1, GOLD[0])])


def starfall_base(name: str) -> np.ndarray:
    r = rng_for(name)
    f = fbm(r, ((4, 0.35), (8, 0.45), (16, 0.2)))
    return from_ramp(by_share(f, [0.06, 0.18, 0.42, 0.26, 0.08]), STARFALL)


def starfall_stone():
    img = starfall_base("starfall_stone")
    fleck(img, rng_for("starfall_stone/flecks"), 6)
    return img


def polished_starfall_stone():
    r = rng_for("polished_starfall_stone")
    f = fbm(r, ((4, 0.5), (8, 0.5)))
    img = from_ramp(by_share(f, [0.25, 0.55, 0.2]), STARFALL[2:5])
    # a groove round the edge, and a lit bevel inside it
    img[0, :, :3] = STARFALL[1]
    img[:, 0, :3] = STARFALL[1]
    img[1, 1:15, :3] = STARFALL[4]
    img[1:15, 1, :3] = STARFALL[4]
    img[14, 2:15, :3] = STARFALL[2]
    img[2:15, 14, :3] = STARFALL[2]
    img[15, :, :3] = STARFALL[0]
    img[:, 15, :3] = STARFALL[0]
    inner = np.zeros((16, 16), bool)
    inner[3:13, 3:13] = True
    fleck(img, rng_for("polished_starfall_stone/flecks"), 3, inner, min_dist=5, kinds=(0, 1))
    return img


def starfall_stone_bricks():
    ids, mortar, iy, ix = brick_layout(4, 8, [0, 4, 2, 6])
    r = rng_for("starfall_stone_bricks")
    noise = fbm(r, ((8, 0.6), (16, 0.4)))
    shade = by_share(noise, [0.2, 0.6, 0.2]) + 2          # ramp 2..4
    brick_tone = {i: int(r.integers(-1, 1)) for i in np.unique(ids)}
    tone = np.vectorize(brick_tone.get)(ids)
    idx = np.clip(shade + tone, 1, 4)
    idx[(iy == 0) & ~mortar] = np.maximum(idx[(iy == 0) & ~mortar], 4)       # lit top edge
    idx[(ix == 0) & ~mortar] = np.maximum(idx[(ix == 0) & ~mortar], 3)       # lit left edge
    idx[(iy == 2) & ~mortar] = np.minimum(idx[(iy == 2) & ~mortar], 2)       # shaded bottom row
    idx[(ix == 6) & ~mortar] = np.minimum(idx[(ix == 6) & ~mortar], 2)       # shaded right column
    img = from_ramp(idx, STARFALL)
    img[mortar, :3] = STARFALL[0]
    fleck(img, rng_for("starfall_stone_bricks/flecks"), 3, (~mortar) & (iy == 1), min_dist=6, kinds=(0,))
    return img


def glimmer_grass_top():
    """Pale gold grass: fine speckle like vanilla grass, olive in the shadows so it reads as a plant,
    blades standing out as lit tips over dark roots, and a few white-turquoise glints."""
    r = rng_for("glimmer_grass_top")
    f = fbm(r, ((4, 0.25), (8, 0.35), (16, 0.4)))
    idx = by_share(f, [0.08, 0.2, 0.34, 0.26, 0.12])
    img = from_ramp(idx, GRASS)
    for x, y in scatter(r, 16, 2.8):
        stamp(img, x, y, [(0, 0, GRASS[5]), (0, 1, GRASS[3]), (0, 2, GRASS[0])])
    for x, y in scatter(rng_for("glimmer_grass_top/glints"), 3, 7):
        stamp(img, x, y, [(0, 0, (255, 255, 255)), (1, 1, TURQ[2])])
    return img


def glimmer_grass_side():
    img = starfall_base("glimmer_grass_side")
    fleck(img, rng_for("glimmer_grass_side/flecks"), 3, np.broadcast_to(np.arange(16)[:, None] > 7, (16, 16)))
    top = glimmer_grass_top()
    # grass covers the top rows and hangs down in an uneven fringe (it has to tile sideways)
    depth = [3, 4, 3, 2, 3, 4, 5, 4, 3, 3, 2, 3, 4, 4, 3, 2]
    for x in range(16):
        d = depth[x]
        img[:d, x] = top[:d, x]
        img[d - 1, x, :3] = GRASS[0]          # the fringe's shaded lip
        img[d, x, :3] = STARFALL[0]           # its shadow on the stone
    img[0, :, :3] = np.where((np.arange(16) % 3 == 0)[:, None], GRASS[5], GRASS[4])
    return img


def spire_quartz():
    """Faceted crystal: Voronoi cells on the torus, each shaded by a tilt facing the light."""
    r = rng_for("spire_quartz")
    pts = np.array(scatter(r, 7, 5.0), dtype=float) + 0.5
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    best = np.full((16, 16), 1e9)
    second = np.full((16, 16), 1e9)
    cell = np.zeros((16, 16), int)
    for k, (px, py) in enumerate(pts):
        dx = np.abs(xs - px)
        dx = np.minimum(dx, 16 - dx)
        dy = np.abs(ys - py)
        dy = np.minimum(dy, 16 - dy)
        d = np.hypot(dx, dy)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        cell = np.where(closer, k, cell)
        best = np.minimum(best, d)
    tilt = r.random(len(pts))
    base = np.array([1, 2, 3, 3, 4, 2, 4])[np.argsort(np.argsort(tilt))]
    idx = base[cell].copy()
    # a gradient inside each facet: brighter towards its top left
    gx = np.zeros((16, 16))
    for k, (px, py) in enumerate(pts):
        m = cell == k
        dxw = ((xs - px + 8) % 16) - 8
        dyw = ((ys - py + 8) % 16) - 8
        gx[m] = -(dxw[m] + dyw[m])
    idx = idx + (gx > 2.5).astype(int) - (gx < -3.5).astype(int)
    edge = (second - best) < 0.9
    idx[edge] = 1
    # the facet edge on the lit side of each boundary is a bright ridge
    lit_edge = edge & ~wrap_shift(edge, 1, 1)
    idx[lit_edge] = 5
    idx = np.clip(idx, 0, 6)
    img = from_ramp(idx, QUARTZ)
    for x, y in scatter(rng_for("spire_quartz/sparks"), 3, 6):
        stamp(img, x, y, [(0, 0, QUARTZ[6])])
    return img


ORE_BLOBS = [
    # (dx, dy, shade): shade 1 = the ore's dark side .. 5 = the spark on its lit corner.
    [(1, 0, 5), (2, 0, 3), (0, 1, 4), (1, 1, 4), (2, 1, 2), (0, 2, 3), (1, 2, 2), (2, 2, 1), (1, 3, 1)],
    [(0, 0, 4), (1, 0, 3), (0, 1, 3), (1, 1, 5), (2, 1, 2), (1, 2, 2), (2, 2, 1)],
    [(1, 0, 4), (2, 0, 4), (3, 0, 3), (0, 1, 4), (1, 1, 5), (2, 1, 2), (3, 1, 1), (1, 2, 2), (2, 2, 1)],
    [(0, 0, 5), (1, 0, 3), (0, 1, 3), (1, 1, 2), (0, 2, 2), (1, 2, 1)],
]


def ore(base: np.ndarray, name: str, colours, count: int = 5, rim=None) -> np.ndarray:
    """Ore nuggets stamped over a stone texture. ``colours`` is a 6-step ramp (rim .. spark)."""
    img = base.copy()
    r = rng_for(name)
    for i, (x, y) in enumerate(scatter(r, count, 5.2)):
        blob = ORE_BLOBS[(i + int(r.integers(4))) % len(ORE_BLOBS)]
        pattern = [(dx, dy, colours[s]) for dx, dy, s in blob]
        if rim is not None:
            # a dark rim on the shadow side so pale ore reads on pale stone
            cells = {(dx, dy) for dx, dy, _ in blob}
            for dx, dy, _ in blob:
                for ox, oy in ((1, 0), (0, 1), (1, 1)):
                    if (dx + ox, dy + oy) not in cells:
                        pattern.insert(0, (dx + ox, dy + oy, rim))
        stamp(img, x, y, pattern)
    return img


def starsteel_ore():
    return ore(starfall_base("starsteel_ore"), "starsteel_ore/nuggets", STARSTEEL, 5, rim=rgb("#857b6a"))


def starsteel_block():
    """Pale silver-gold: one bevelled plate, brushed across, riveted in the corners."""
    r = rng_for("starsteel_block")
    f = fbm(r, ((2, 0.4), (4, 0.6)), aspect=(4, 1))
    img = from_ramp(by_share(f, [0.3, 0.45, 0.25]) + 2, STEEL_PLATE)
    img[0, :, :3] = STEEL_PLATE[5]
    img[:, 0, :3] = STEEL_PLATE[5]
    img[15, :, :3] = STEEL_PLATE[0]
    img[:, 15, :3] = STEEL_PLATE[0]
    img[1, 1:15, :3] = STEEL_PLATE[4]
    img[1:15, 1, :3] = STEEL_PLATE[4]
    img[14, 1:15, :3] = STEEL_PLATE[1]
    img[1:15, 14, :3] = STEEL_PLATE[1]
    # a gold inlay square
    for i in range(4, 12):
        for x, y in ((i, 4), (4, i)):
            img[y, x, :3] = GOLD[3]
        for x, y in ((i, 11), (11, i)):
            img[y, x, :3] = GOLD[1]
    img[4, 4, :3] = GOLD[4]
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img[y, x, :3] = STEEL_PLATE[5]
        img[min(y + 1, 14), min(x + 1, 14), :3] = STEEL_PLATE[1]
    return img


def meteorite():
    r = rng_for("meteorite")
    f = fbm(r, ((4, 0.4), (8, 0.4), (16, 0.2)))
    idx = by_share(f, [0.08, 0.2, 0.34, 0.24, 0.1, 0.04])
    img = from_ramp(idx, METEOR)
    # pits (regmaglypts): a dark cup with a lit lower-right rim
    for x, y in scatter(rng_for("meteorite/pits"), 4, 6):
        stamp(img, x, y, [(0, 0, METEOR[0]), (1, 0, METEOR[1]), (0, 1, METEOR[1]),
                          (1, 1, METEOR[5]), (2, 1, METEOR[4]), (1, 2, METEOR[4])])
    # ember seams: short hot cracks
    seams = [[(0, 0), (1, 0), (2, 1), (3, 1)], [(0, 0), (0, 1), (1, 2), (1, 3)], [(0, 0), (1, 1), (2, 1)]]
    for i, (x, y) in enumerate(scatter(rng_for("meteorite/seams"), 3, 7)):
        pts = seams[i % len(seams)]
        pattern = [(dx, dy, EMBER[2] if k in (1, 2) else EMBER[1]) for k, (dx, dy) in enumerate(pts)]
        pattern += [(dx + 1, dy + 1, EMBER[0]) for dx, dy in pts[::2]]
        stamp(img, x, y, pattern)
        stamp(img, x + pts[1][0], y + pts[1][1], [(0, 0, EMBER[3])])
    # starsteel specks, the reason to mine it
    for x, y in scatter(rng_for("meteorite/specks"), 4, 5):
        stamp(img, x, y, [(0, 0, STARSTEEL[5]), (1, 0, STARSTEEL[3]), (1, 1, STARSTEEL[1])])
    return img


# ---------------------------------------------------------------- plants (cutout sprites)

MOSS_PAL = {"a": "#236355", "b": "#348676", "c": "#4fa792", "d": "#77c8b1", "e": "#a9e6d2", "f": "#dbfff3",
            "g": "#f3cf6e", "G": "#fff4c8"}

# Body: strands running the full height so it tiles vertically.
HALO_MOSS_PLANT = [
    "..c...b....d..c.",
    "..d...c...de..c.",
    ".cd...c...d...b.",
    ".c...bd...d..cd.",
    ".c...c.....d.c..",
    ".d...c....cd.c..",
    "..d..b....d..d..",
    "..d...c...d..cg.",
    "..c...c...c...d.",
    "..cg..c..cd...d.",
    "..b...d..d....c.",
    ".cb...d..d...cc.",
    ".c....e..c...c..",
    ".c...de...c..b..",
    "..c..d....c..c..",
    "..c...b....d.c..",
]

# Tip: the strands thin out and end in curls and gold motes.
HALO_MOSS = [
    "..c...b....d..c.",
    "..d...c...de..c.",
    ".cd...c...d...b.",
    ".c...bd...d..cd.",
    ".c...c.....d.c..",
    ".d...c....cd.c..",
    "..d..b....d..d..",
    "..d...c...d..cg.",
    "..c...c...c...d.",
    "..eg..d..cd...d.",
    "..G...d..d....e.",
    "......e..e...eG.",
    "......f..G......",
    ".....dG.........",
    "......g.........",
    "................",
]

FLOWER_PAL = {"o": "#8a5f1e", "p": "#e3bb5a", "q": "#f7dc8c", "r": "#fff6d8", "w": "#ffffff",
              "t": "#157a73", "u": "#2aa89f", "v": "#5fd8cc",
              "1": "#22523f", "2": "#2f6d53", "3": "#3f8a68", "4": "#58a982", "5": "#7cc79f"}

STARBLOOM = [
    "................",
    ".......w........",
    "......wrq.......",
    "......rqp.......",
    ".wrrrqruvqqqpo..",
    "..rqqqvutqqpo...",
    "...qqptutqpo....",
    "....qqpptpo.....",
    "...qqpo..pqo....",
    "..qpo..4..pqo...",
    "..po...3...po...",
    ".......3..45....",
    "...54..3.43.....",
    "....43.33.......",
    ".....3.3........",
    ".......2........",
]

CROP_STAGES = [
    [  # 0: sprouts
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..5......4....5.",
        "..4..5...3...45.",
        "...3.4..43...3..",
        "...3.3...3...3..",
        "...2.2...2...2..",
    ],
    [  # 1: leaves
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "....5......5....",
        "...54.....45..5.",
        "..54...5..4..54.",
        "...4..54..3..4..",
        "..53..4...3.43..",
        "...3.43..43..3..",
        "...3..3...3..3..",
        "...2..3...2..2..",
        "...2..2...2..2..",
    ],
    [  # 2: buds
        "................",
        "................",
        "................",
        "................",
        "....q......p....",
        "...pqo....qpo...",
        "....3......3....",
        "..5.3..5...3.5..",
        "..44.3.4..3.54..",
        "...4.3.43.3.4...",
        "..53.3..3.3.3...",
        "...3.43.3.33..5.",
        "...3..3.3.3..4..",
        "...3..3.3.3.3...",
        "...2..2.2.2.2...",
        "...2..2.2.2.2...",
    ],
    [  # 3: in bloom
        "................",
        "...w.......w....",
        "..rvq.....rvq...",
        ".rqupo...rqupo..",
        "..qpo.....qpo...",
        "..p.3..w...p3...",
        "....3.rvq...3...",
        "..5.3rqupo..3.5.",
        "..44.3qpo..3.54.",
        "...4.3.p3..3.4..",
        "..53.3..3.3..3..",
        "...3.43.3.33..5.",
        "...3..3.3.3..4..",
        "...3..3.3.3.3...",
        "...2..2.2.2.2...",
        "...2..2.2.2.2...",
    ],
]


def breach_frame_side():
    r = rng_for("breach_frame_side")
    f = fbm(r, ((8, 0.6), (16, 0.4)))
    idx = by_share(f, [0.15, 0.3, 0.35, 0.2]) + 1
    ids, mortar, iy, ix = brick_layout(5, 8, [2, 6, 2])
    idx[(iy == 0) & ~mortar] += 1
    idx[mortar] = 0
    img = from_ramp(np.clip(idx, 0, 4), COBBLE)
    # copper bands top and bottom, riveted
    img[0, :, :3] = COPPER[4]
    img[1, :, :3] = COPPER[2]
    img[2, :, :3] = COPPER[1]
    img[13, :, :3] = COPPER[3]
    img[14, :, :3] = COPPER[2]
    img[15, :, :3] = COPPER[0]
    for x in (3, 12):
        img[1, x, :3] = COPPER[5]
        img[14, x, :3] = COPPER[4]
    return img


def breach_frame_top():
    r = rng_for("breach_frame_top")
    f = fbm(r, ((8, 0.6), (16, 0.4)))
    idx = by_share(f, [0.15, 0.35, 0.35, 0.15]) + 1
    img = from_ramp(np.clip(idx, 0, 4), COBBLE)
    # a copper frame with corner plates, and a star inlay in the middle
    img[0, :, :3] = COPPER[4]
    img[:, 0, :3] = COPPER[4]
    img[15, :, :3] = COPPER[0]
    img[:, 15, :3] = COPPER[0]
    img[1, 1:15, :3] = COPPER[2]
    img[1:15, 1, :3] = COPPER[2]
    img[14, 1:15, :3] = COPPER[1]
    img[1:15, 14, :3] = COPPER[1]
    for cx, cy in ((2, 2), (11, 2), (2, 11), (11, 11)):
        img[cy:cy + 3, cx:cx + 3, :3] = COPPER[3]
        img[cy, cx:cx + 3, :3] = COPPER[4]
        img[cy:cy + 3, cx, :3] = COPPER[4]
        img[cy + 2, cx:cx + 3, :3] = COPPER[1]
        img[cy:cy + 3, cx + 2, :3] = COPPER[1]
        img[cy + 1, cx + 1, :3] = COPPER[5]
    star = [(7, 5), (8, 5), (7, 6), (8, 6), (5, 7), (6, 7), (9, 7), (10, 7), (5, 8), (6, 8), (9, 8),
            (10, 8), (7, 9), (8, 9), (7, 10), (8, 10), (7, 7), (8, 7), (7, 8), (8, 8)]
    for x, y in star:
        img[y, x, :3] = COPPER[3]
    for x, y in ((7, 7), (8, 7), (7, 8)):
        img[y, x, :3] = COPPER[5]
    img[8, 8, :3] = COPPER[2]
    for x, y in ((7, 4), (4, 7), (11, 8), (8, 11)):
        img[y, x, :3] = COPPER[4]
    return img


def build() -> dict[str, np.ndarray]:
    return {
        "starfall_stone": starfall_stone(),
        "polished_starfall_stone": polished_starfall_stone(),
        "starfall_stone_bricks": starfall_stone_bricks(),
        "glimmer_grass_top": glimmer_grass_top(),
        "glimmer_grass_side": glimmer_grass_side(),
        "spire_quartz": spire_quartz(),
        "starsteel_ore": starsteel_ore(),
        "starsteel_block": starsteel_block(),
        "meteorite": meteorite(),
        "halo_moss": sprite(HALO_MOSS, MOSS_PAL),
        "halo_moss_plant": sprite(HALO_MOSS_PLANT, MOSS_PAL),
        "starbloom": sprite(STARBLOOM, FLOWER_PAL),
        **{f"starbloom_stage{i}": sprite(rows, FLOWER_PAL) for i, rows in enumerate(CROP_STAGES)},
        "breach_frame_top": breach_frame_top(),
        "breach_frame_side": breach_frame_side(),
    }


def main():
    for name, img in build().items():
        p = save_png(img, OUT / f"{name}.png")
    print("wrote", len(build()), "Reach block textures to", rel(OUT))


if __name__ == "__main__":
    main()
