"""The Drift's block textures (16x16, in textures/block/, plus the door's item sprite).

The Drift is the asteroid belt (GDD 2.2): grey-blue stone, silver and cyan, dust lanes.

    driftstone                     grey-blue stone in horizontal dust bands, silver and cyan specks
    driftstone_bricks              big ashlar blocks (2 courses)
    nebulite_ore                   Driftstone with cyan-to-violet crystals
    nebulite_block                 a cyan-to-violet crystal slab in a dark frame
    driftwood_log / _top           petrified wood: silver bark, rings with cyan veins
    stripped_driftwood_log / _top  the pale inner wood
    driftwood_planks               four silver boards
    driftwood_door_top / _bottom   boards in a frame, a round window up top
    driftwood_trapdoor             a frame with four windows
    rimeglass                      translucent ice-like crystal
    item/driftwood_door            the door's inventory sprite

Run:  python tools/art/gen_blocks_drift.py
"""
from __future__ import annotations

import numpy as np

from blockart import (brick_layout, by_share, fbm, from_ramp, ramp, rng_for, scatter, sprite, stamp, voronoi,
                      wrap_shift)
from common import TEX, rel, rgb, save_png

OUT = TEX / "block"

DRIFT = ramp("#4b5667", "#5d697c", "#707e92", "#8594a8", "#9caec1", "#b9c9d8")
SILVER = ramp("#c9d3dd", "#e6edf3", "#ffffff")
CYAN = ramp("#2f9fb0", "#5fd4e2", "#aef3fa")
NEBULITE = ramp("#2a2463", "#4b3fae", "#7a62e6", "#6aa8f2", "#7fe6f6", "#e0fcff")
BARK = ramp("#434c56", "#56606b", "#6a7580", "#808b96", "#98a4ae", "#b3bec6")
WOOD = ramp("#6a7680", "#7f8b95", "#94a0a9", "#a8b4bc", "#bcc7ce", "#d0d9de")
STRIPPED = ramp("#7f959c", "#95aab0", "#a9bdc2", "#bccdd1", "#cfdde0", "#e2edef")
VEIN = ramp("#3d8f9c", "#63c3cf", "#a3eaf1")
RIME = ramp("#8fcde0", "#b7e3ef", "#d6f1f8", "#effbfe")


def driftstone_base(name: str) -> np.ndarray:
    r = rng_for(name)
    f = 0.78 * fbm(r, ((2, 0.45), (4, 0.55)), aspect=(4, 1)) + 0.22 * fbm(r, ((8, 1.0),))
    return from_ramp(by_share(f, [0.07, 0.2, 0.4, 0.24, 0.09]), DRIFT)


def specks(img, name, silver=5, cyan=2, allowed=None):
    for x, y in scatter(rng_for(name + "/silver"), silver, 4.5, allowed):
        stamp(img, x, y, [(0, 0, SILVER[1]), (1, 0, SILVER[0])])
    for x, y in scatter(rng_for(name + "/cyan"), cyan, 7, allowed):
        stamp(img, x, y, [(0, 0, CYAN[1]), (1, 1, CYAN[0])])


def driftstone():
    img = driftstone_base("driftstone")
    specks(img, "driftstone")
    return img


def driftstone_bricks():
    ids, mortar, iy, ix = brick_layout(8, 8, [0, 4])
    r = rng_for("driftstone_bricks")
    f = 0.6 * fbm(r, ((2, 0.5), (4, 0.5)), aspect=(3, 1)) + 0.4 * fbm(r, ((8, 0.6), (16, 0.4)))
    idx = by_share(f, [0.15, 0.45, 0.3, 0.1]) + 2
    body = ~mortar
    idx[body & (iy == 0)] = 5
    idx[body & (iy == 1)] = np.maximum(idx[body & (iy == 1)], 4)
    idx[body & (ix == 0)] = np.maximum(idx[body & (ix == 0)], 4)
    idx[body & (iy == 6)] = 1
    idx[body & (ix == 6)] = np.minimum(idx[body & (ix == 6)], 2)
    img = from_ramp(np.clip(idx, 0, 5), DRIFT)
    img[mortar, :3] = DRIFT[0]
    specks(img, "driftstone_bricks", silver=3, cyan=1, allowed=body & (iy > 1) & (iy < 6))
    return img


NEBULITE_BLOBS = [
    # crystals: a bright cyan tip on the upper left, violet toward the lower right
    [(1, 0, 5), (0, 1, 4), (1, 1, 4), (2, 1, 3), (1, 2, 3), (2, 2, 2), (3, 2, 1), (2, 3, 1)],
    [(0, 0, 4), (1, 0, 5), (1, 1, 3), (2, 1, 2), (0, 1, 3), (1, 2, 2), (2, 2, 1)],
    [(0, 0, 5), (0, 1, 4), (1, 1, 3), (1, 2, 2), (2, 2, 1), (2, 3, 1)],
    [(1, 0, 4), (2, 0, 5), (0, 1, 3), (1, 1, 4), (2, 1, 3), (1, 2, 2), (2, 2, 1), (3, 1, 2)],
]


def nebulite_ore():
    img = driftstone_base("nebulite_ore")
    specks(img, "nebulite_ore", silver=2, cyan=0)
    r = rng_for("nebulite_ore/crystals")
    for i, (x, y) in enumerate(scatter(r, 5, 5.2)):
        blob = NEBULITE_BLOBS[(i + int(r.integers(4))) % len(NEBULITE_BLOBS)]
        cells = {(dx, dy) for dx, dy, _ in blob}
        pattern = []
        for dx, dy, _ in blob:
            for ox, oy in ((1, 0), (0, 1), (1, 1)):
                if (dx + ox, dy + oy) not in cells:
                    pattern.append((dx + ox, dy + oy, NEBULITE[0]))
        pattern += [(dx, dy, NEBULITE[s]) for dx, dy, s in blob]
        stamp(img, x, y, pattern)
    return img


def nebulite_block():
    ys, xs = np.mgrid[0:16, 0:16]
    cell, rx, ry, edge = voronoi(rng_for("nebulite_block"), 6, 5.0)
    # each facet follows the block's diagonal (cyan top left, violet bottom right) and is
    # brighter on its own side that faces the light
    t = (xs + ys) / 30.0
    idx = np.round(4.2 - 3.0 * t).astype(int)
    idx = idx + (-(rx + ry) > 2.0).astype(int) - ((rx + ry) > 2.5).astype(int)
    idx[edge] = 1
    idx[edge & ~wrap_shift(edge, 1, 1)] = 4
    img = from_ramp(np.clip(idx, 1, 5), NEBULITE)
    # a dark frame with a lit inner edge
    frame = (xs == 0) | (ys == 0) | (xs == 15) | (ys == 15)
    img[frame, :3] = NEBULITE[0]
    img[(ys == 1) & (xs > 0) & (xs < 15), :3] = NEBULITE[4]
    img[(xs == 1) & (ys > 0) & (ys < 15), :3] = NEBULITE[4]
    img[(ys == 14) & (xs > 0) & (xs < 15), :3] = NEBULITE[1]
    img[(xs == 14) & (ys > 0) & (ys < 15), :3] = NEBULITE[1]
    return img


def driftwood_log():
    """Silver bark in vertical plates split by dark fissures, a hint of cyan in the cracks."""
    r = rng_for("driftwood_log")
    f = fbm(r, ((2, 0.3), (4, 0.4), (8, 0.3)), aspect=(1, 3))
    img = from_ramp(by_share(f, [0.08, 0.18, 0.3, 0.26, 0.13, 0.05]), BARK)
    fissure_x = [1, 5, 10, 13]
    for i, fx in enumerate(fissure_x):
        wob = np.round(1.2 * np.sin((np.arange(16) + 3 * i) * np.pi / 8)).astype(int)
        for y in range(16):
            x = (fx + wob[y]) % 16
            img[y, x, :3] = BARK[0]
            img[y, (x + 1) % 16, :3] = BARK[4]            # the lit edge of the next plate
            if (y + 5 * i) % 11 == 0:
                img[y, x, :3] = VEIN[1]
    return img


def rings(name, outer, colours, bark=None, vein=True):
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    d = np.hypot(xs - 8, ys - 8)
    r = rng_for(name)
    jitter = 0.5 * fbm(r, ((4, 1.0),))
    ring = np.floor(d + jitter).astype(int)
    idx = np.where(ring % 2 == 0, 3, 2)
    idx = np.where(ring <= 1, 4, idx)
    idx = np.where(ring == 0, 5, idx)
    img = from_ramp(idx, colours)
    # light from the top left across the whole face
    img[(xs + ys < 9) & (ring % 2 == 1), :3] = colours[3]
    if vein:
        # petrified: cyan veins radiate from the heart
        for ang in (0.6, 2.4, 4.1):
            for k in range(2, 7):
                x = int(8 + k * np.cos(ang))
                y = int(8 + k * np.sin(ang))
                img[y, x, :3] = VEIN[1] if k % 2 else VEIN[2]
    edge = (xs < 1) | (ys < 1) | (xs > 15) | (ys > 15)
    if bark is not None:
        img[edge, :3] = bark[1]
        img[(xs < 1) | (ys < 1), :3] = bark[3]
    else:
        img[edge, :3] = colours[1]
    return img


def stripped_driftwood_log():
    """The pale inner wood: long vertical grain, a few cyan veins where it petrified."""
    r = rng_for("stripped_driftwood_log")
    f = fbm(r, ((2, 0.5), (4, 0.5)), aspect=(1, 4))
    img = from_ramp(by_share(f, [0.12, 0.3, 0.4, 0.18]) + 1, STRIPPED)
    for x, y in scatter(rng_for("stripped_driftwood_log/grain"), 5, 3.5):
        stamp(img, x, y, [(0, 0, STRIPPED[1]), (0, 1, STRIPPED[1]), (0, 2, STRIPPED[2])])
    for x, y in scatter(rng_for("stripped_driftwood_log/veins"), 3, 6):
        stamp(img, x, y, [(0, 0, VEIN[1]), (0, 1, VEIN[0]), (0, 2, VEIN[1])])
    return img


def driftwood_planks():
    """Four silver boards: horizontal grain, a lit top edge and a dark seam, two butt joints."""
    r = rng_for("driftwood_planks")
    f = fbm(r, ((2, 0.5), (4, 0.5)), aspect=(4, 1))
    idx = by_share(f, [0.2, 0.5, 0.3]) + 2
    ys, xs = np.mgrid[0:16, 0:16]
    board = ys // 4
    idx = np.clip(idx + np.array([0, -1, 0, -1])[board], 1, 5)
    idx[ys % 4 == 0] = 4
    idx[ys % 4 == 3] = 0
    img = from_ramp(idx, WOOD)
    # grain: long dark streaks along each board
    g = rng_for("driftwood_planks/grain")
    for b in range(4):
        for _ in range(2):
            y = 4 * b + 1 + int(g.integers(2))
            x0 = int(g.integers(16))
            for k in range(4 + int(g.integers(5))):
                img[y, (x0 + k) % 16, :3] = WOOD[1]
    # butt joints on two of the boards
    for b, x in ((1, 5), (3, 12)):
        img[4 * b:4 * b + 3, x, :3] = WOOD[0]
        img[4 * b, (x + 1) % 16, :3] = WOOD[5]
    return img


DOOR_PAL = {"o": "#4a545d", "a": "#6a7680", "b": "#7f8b95", "c": "#94a0a9", "d": "#a8b4bc", "e": "#bcc7ce",
            "f": "#d0d9de", "g": "#3d8f9c", "h": "#63c3cf", "i": "#a3eaf1", "k": "#2c343b"}

def door() -> tuple[np.ndarray, np.ndarray]:
    """The whole 16x32 door as (top half, bottom half): vertical boards in a frame, a window
    up top, rails at the top, the middle and the bottom, a diagonal brace below."""
    P = {k: rgb(v) for k, v in DOOR_PAL.items()}
    img = np.zeros((32, 16, 4), dtype=np.uint8)
    img[..., 3] = 255
    r = rng_for("driftwood_door")
    for y in range(32):
        for x in range(16):
            if x in (0, 15):
                c = "o"
            elif x == 1:
                c = "f"
            elif x == 14:
                c = "b"
            elif x in (5, 9):
                c = "a"                              # seams between the boards
            elif x in (2, 6, 10):
                c = "e"                              # the lit edge of each board
            elif x == 13:
                c = "c"
            else:
                c = "d" if r.random() > 0.2 else "c"
            img[y, x, :3] = P[c]
    img[0, :, :3] = P["o"]
    img[1, 1:15, :3] = P["f"]
    img[2, 1:15, :3] = P["c"]
    for y in (15, 29):                               # middle and bottom rails
        img[y, 1:15, :3] = P["f"]
        img[y + 1, 1:15, :3] = P["a"]
    img[31, :, :3] = P["o"]
    # the window: a dark frame round a clear pane with a glint
    img[4:11, 3:13, :3] = P["g"]
    img[4, 3:13, :3] = P["k"]
    img[4:11, 3, :3] = P["k"]
    img[5:10, 4:12, 3] = 0
    for x, y in ((5, 6), (6, 5), (10, 8)):
        img[y, x] = (*P["i"], 255)
    # the brace, lit along its top edge
    for k in range(10):
        x, y = 3 + k, 27 - k
        img[y, x, :3] = P["f"]
        img[y + 1, x, :3] = P["d"]
        img[y + 2, x, :3] = P["a"]
    return img[:16], img[16:]


TRAPDOOR = [
    "oooooooooooooooo",
    "offfffffffffffbo",
    "ofe....ff....dbo",
    "of.hhh.fd.hhh.bo",
    "of.h...fd.h...bo",
    "of.h...fd.h...bo",
    "ofd....fd....dbo",
    "offffffddffffdbo",
    "ofdddddddddddcbo",
    "ofe....ff....dbo",
    "of.hhh.fd.hhh.bo",
    "of.h...fd.h...bo",
    "of.h...fd.h...bo",
    "ofd....fd....dbo",
    "obbbbbbbbbbbbbbo",
    "oooooooooooooooo",
]

DOOR_ITEM = [
    "................",
    "....oooooooo....",
    "....offfffbo....",
    "....ofkkkkbo....",
    "....ofk.igbo....",
    "....ofkgggbo....",
    "....ofedeabo....",
    "....offfffbo....",
    "....ofedeabo....",
    "....ofeddfbo....",
    "....ofedfabo....",
    "....offdeabo....",
    "....ofedeabo....",
    "....offfffbo....",
    "....oooooooo....",
    "................",
]


def rimeglass():
    """Translucent ice crystal: a clear body, frosted streaks, a brighter rim."""
    r = rng_for("rimeglass")
    f = fbm(r, ((2, 0.4), (4, 0.4), (8, 0.2)))
    idx = by_share(f, [0.3, 0.45, 0.25])
    img = from_ramp(idx, RIME)
    alpha = np.array([110, 130, 150])[idx]
    ys, xs = np.mgrid[0:16, 0:16]
    # frost streaks: diagonal cracks, opaque white
    for x0, length in ((3, 6), (9, 5), (12, 4)):
        for k in range(length):
            x, y = (x0 + k) % 16, (2 + 2 * k + x0) % 16
            img[y, x, :3] = RIME[3]
            alpha[y, x] = 205
            img[(y + 1) % 16, x, :3] = RIME[2]
            alpha[(y + 1) % 16, x] = 170
    rim = (xs == 0) | (ys == 0) | (xs == 15) | (ys == 15)
    img[rim, :3] = RIME[2]
    alpha[rim] = 185
    img[(ys == 0) | (xs == 0), :3] = RIME[3]
    alpha[(ys == 0) | (xs == 0)] = 200
    img[..., 3] = alpha
    return img


def build() -> dict[str, np.ndarray]:
    return {
        "driftstone": driftstone(),
        "driftstone_bricks": driftstone_bricks(),
        "nebulite_ore": nebulite_ore(),
        "nebulite_block": nebulite_block(),
        "driftwood_log": driftwood_log(),
        "driftwood_log_top": rings("driftwood_log_top", 7, WOOD, bark=BARK),
        "stripped_driftwood_log": stripped_driftwood_log(),
        "stripped_driftwood_log_top": rings("stripped_driftwood_log_top", 7, STRIPPED, vein=True),
        "driftwood_planks": driftwood_planks(),
        "driftwood_door_top": door()[0],
        "driftwood_door_bottom": door()[1],
        "driftwood_trapdoor": sprite(TRAPDOOR, DOOR_PAL),
        "rimeglass": rimeglass(),
    }


def main():
    built = build()
    for name, img in built.items():
        save_png(img, OUT / f"{name}.png")
    save_png(sprite(DOOR_ITEM, DOOR_PAL), TEX / "item" / "driftwood_door.png")
    print("wrote", len(built), "Drift block textures to", rel(OUT), "and item/driftwood_door.png")


if __name__ == "__main__":
    main()
