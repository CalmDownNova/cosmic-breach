"""The layer 3 zones' block textures (16x16, in textures/block/), Aetheria 1.2.

Each zone has its own ground, so the four read as different places:

    lichen_moss / _side    the Lichen Gardens: violet moss studded with magenta and teal lichen, over basalt
    rootstone              the Hanging Wood: pale, bone-like stone with fine grain and teal stains
    ember_basalt           the Shattered Field: basalt split by glowing magenta veins
    rift_ember             the Shattered Field's undersides: a glowing magenta seam
    ashen_basalt / _top    the Spans' second stone: a lighter grey-violet basalt in columns
    giant_umbral_cap       a giant umbral cap: bright violet flesh, pale spots, lichen freckles
    umbral_stem / _top     its stem: pale violet-grey fibres; growth rings on the cut end
    teal_curtain           a strand of the Hanging Wood's teal curtains (cutout, cross model)

Run:  python tools/art/gen_blocks_zones.py
"""
from __future__ import annotations

import numpy as np

from blockart import by_share, fbm, from_ramp, ramp, rng_for, scatter, stamp
from common import TEX, rel, save_png

OUT = TEX / "block"

CAP = ramp("#3a1660", "#52208a", "#6a2cae", "#8438cc", "#a050e0", "#c27cf0")
SPOT = ramp("#d8c4f0", "#f0e4ff")
FRECKLE = ramp("#ff5ad8", "#3ff0dc")
STEM = ramp("#4a4452", "#5c5566", "#6f687a", "#847c8f", "#9a93a4", "#b3adbb")
MOSS = ramp("#2a1640", "#3a1f58", "#4c2a72", "#5f378c", "#7446a6", "#8c5cc0")
UMBRAL = ramp("#16131e", "#1f1b2a", "#292436", "#342e44", "#413953", "#524866")
MAGENTA = ramp("#9a2185", "#d93bbd", "#ff7ae0")
TEAL = ramp("#11867c", "#1ec3b1", "#5ef0de")
ROOT = ramp("#4f7378", "#628a8f", "#78a2a6", "#90b9bc", "#a9cfd0", "#c6e4e3")
ASH = ramp("#5e5866", "#6f6978", "#827b8a", "#958e9c", "#a9a2b0", "#bfb8c4")
EMBER = ramp("#4a1050", "#9a1e9a", "#e040d0", "#ff78ec", "#ffc8f6")


def giant_umbral_cap() -> np.ndarray:
    r = rng_for("giant_umbral_cap")
    f = fbm(r, ((2, 0.4), (4, 0.4), (8, 0.2)))
    img = from_ramp(by_share(f, [0.0, 0.1, 0.35, 0.4, 0.15, 0.0]), CAP)
    for x, y in scatter(rng_for("giant_umbral_cap/spots"), 2, 7.0):
        stamp(img, x, y, [(0, 0, SPOT[1]), (1, 0, SPOT[0]), (0, 1, SPOT[0]), (1, 1, CAP[5])])
    for i, (x, y) in enumerate(scatter(rng_for("giant_umbral_cap/freckles"), 4, 5)):
        stamp(img, x, y, [(0, 0, FRECKLE[i % 2])])
    return img


def umbral_stem() -> np.ndarray:
    r = rng_for("umbral_stem")
    f = fbm(r, ((2, 0.3), (4, 0.4), (8, 0.3)), aspect=(1, 6))
    img = from_ramp(by_share(f, [0.06, 0.18, 0.32, 0.28, 0.12, 0.04]), STEM)
    for x in (2, 6, 9, 13):
        img[:, x, :3] = np.minimum(img[:, x, :3], np.array(STEM[1], dtype=np.uint8))
    return img


def umbral_stem_top() -> np.ndarray:
    ys, xs = np.mgrid[0:16, 0:16]
    d = np.hypot(xs - 7.5, ys - 7.5)
    f = fbm(rng_for("umbral_stem_top"), ((4, 0.6), (8, 0.4)))
    idx = np.clip((3 + (np.floor(d) % 3 == 0) * -1 + (f > 0.6) * 1).astype(int), 0, 5)
    idx = np.where(d > 7.2, 1, idx)
    return from_ramp(idx, STEM)


def lichen_moss() -> np.ndarray:
    """A violet moss carpet with lichen clusters glowing in it."""
    f = fbm(rng_for("lichen_moss"), ((2, 0.3), (4, 0.4), (8, 0.3)))
    img = from_ramp(by_share(f, [0.06, 0.16, 0.3, 0.28, 0.14, 0.06]), MOSS)
    for i, (x, y) in enumerate(scatter(rng_for("lichen_moss/lichen"), 3, 6.0)):
        c = MAGENTA if i % 2 == 0 else TEAL
        stamp(img, x, y, [(0, 0, c[2]), (1, 0, c[1]), (0, 1, c[1]), (-1, 0, c[0])])
    return img


def lichen_moss_side() -> np.ndarray:
    """Basalt under a hanging fringe of the moss."""
    f = fbm(rng_for("lichen_moss_side"), ((2, 0.3), (4, 0.4), (8, 0.3)), aspect=(1, 4))
    img = from_ramp(by_share(f, [0.1, 0.25, 0.35, 0.22, 0.08]), UMBRAL)
    moss = from_ramp(by_share(fbm(rng_for("lichen_moss_side/moss"), ((4, 0.5), (8, 0.5))), [0.2, 0.3, 0.3, 0.2]) + 1, MOSS)
    r = rng_for("lichen_moss_side/fringe")
    for x in range(16):
        depth = 3 + int(r.integers(0, 4))
        img[:depth, x] = moss[:depth, x]
    for i, (x, y) in enumerate(scatter(rng_for("lichen_moss_side/lichen"), 3, 5, None)):
        c = MAGENTA if i % 2 == 0 else TEAL
        stamp(img, x, min(y, 3), [(0, 0, c[2])])
    return img


def rootstone() -> np.ndarray:
    """Pale stone with a fine, root-like grain and a few teal stains."""
    f = fbm(rng_for("rootstone"), ((2, 0.3), (4, 0.3), (16, 0.4)), aspect=(1, 3))
    img = from_ramp(by_share(f, [0.05, 0.15, 0.3, 0.3, 0.15, 0.05]), ROOT)
    for x, y in scatter(rng_for("rootstone/stain"), 3, 6):
        stamp(img, x, y, [(0, 0, TEAL[0]), (0, 1, TEAL[0]), (1, 1, ROOT[1])])
    return img


def ember_basalt() -> np.ndarray:
    """Basalt cracked by glowing veins: magenta at the edges, ember at the heart."""
    f = fbm(rng_for("ember_basalt"), ((2, 0.4), (4, 0.4), (8, 0.2)))
    img = from_ramp(by_share(f, [0.15, 0.3, 0.3, 0.18, 0.07]), UMBRAL)
    v = fbm(rng_for("ember_basalt/veins"), ((2, 0.5), (4, 0.5)))
    ridge = 1.0 - np.abs(v - 0.5) * 2.0
    img[ridge > 0.93, :3] = EMBER[1]
    img[ridge > 0.965, :3] = EMBER[2]
    img[ridge > 0.985, :3] = EMBER[3]
    return img


def rift_ember() -> np.ndarray:
    """A glowing magenta seam: bright crystal facets in dark glassy rock."""
    f = fbm(rng_for("rift_ember"), ((2, 0.4), (4, 0.4), (8, 0.2)))
    img = from_ramp(by_share(f, [0.1, 0.25, 0.35, 0.2, 0.1]), EMBER)
    for x, y in scatter(rng_for("rift_ember/facets"), 5, 5):
        stamp(img, x, y, [(0, 0, EMBER[4]), (1, 0, EMBER[3]), (0, 1, EMBER[3])])
    return img


def ashen_basalt() -> np.ndarray:
    f = fbm(rng_for("ashen_basalt"), ((2, 0.3), (4, 0.4), (8, 0.3)), aspect=(1, 4))
    idx = by_share(f, [0.08, 0.22, 0.35, 0.25, 0.1])
    xs = np.mgrid[0:16, 0:16][1]
    idx = np.where(xs % 4 == 0, np.maximum(idx, 3), idx)
    idx = np.where(xs % 4 == 3, np.minimum(idx, 1), idx)
    img = from_ramp(np.clip(idx, 0, 5), ASH)
    for x in (3, 7, 11, 15):
        img[:, x, :3] = ASH[0]
    return img


def ashen_basalt_top() -> np.ndarray:
    f = fbm(rng_for("ashen_basalt_top"), ((4, 0.5), (8, 0.5)))
    idx = by_share(f, [0.2, 0.5, 0.3]) + 2
    ys, xs = np.mgrid[0:16, 0:16]
    cx, cy = xs % 8, ys % 8
    d = np.maximum(np.abs(cx - 3.5), np.abs(cy - 3.5))
    idx = np.where(d > 3.6, 0, idx)
    return from_ramp(np.clip(idx, 0, 5), ASH)


def teal_curtain() -> np.ndarray:
    """Three hanging strands with glowing beads, on transparency."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    r = rng_for("teal_curtain")
    for x in (3, 8, 12):
        for y in range(16):
            if r.random() < 0.88:
                c = TEAL[1] if r.random() < 0.7 else TEAL[2]
                xx = x + (1 if (y // 4) % 2 and x < 15 else 0)
                img[y, xx, :3] = c
                img[y, xx, 3] = 255
        for _ in range(2):
            y = int(r.integers(0, 16))
            img[y, x, :3] = TEAL[2]
            img[y, x, 3] = 255
            if x + 1 < 16:
                img[y, x + 1, :3] = TEAL[1]
                img[y, x + 1, 3] = 255
    return img


def build() -> dict[str, np.ndarray]:
    return {
        "giant_umbral_cap": giant_umbral_cap(),
        "umbral_stem": umbral_stem(),
        "umbral_stem_top": umbral_stem_top(),
        "lichen_moss": lichen_moss(),
        "lichen_moss_side": lichen_moss_side(),
        "rootstone": rootstone(),
        "ember_basalt": ember_basalt(),
        "rift_ember": rift_ember(),
        "ashen_basalt": ashen_basalt(),
        "ashen_basalt_top": ashen_basalt_top(),
        "teal_curtain": teal_curtain(),
    }


def main():
    built = build()
    for name, img in built.items():
        save_png(img, OUT / f"{name}.png")
    print("wrote", len(built), "zone block textures to", rel(OUT))


if __name__ == "__main__":
    main()
