"""Starshard: textures/item/starshard.png (16x16).

A small faceted crystal shard lying on the diagonal like the vanilla shards:
sharp point top right, broken base bottom left. It is four flat facets that
meet at one point, which is what makes it read as cut crystal rather than a
leaf (a leaf has one midrib, a crystal has facets that cross):

    upper front   white, the lit face
    lower front   pale turquoise
    upper back    cool white
    lower back    gold, the warm reflection

Shapes are written in diagonal coordinates (s along the shard towards the
point, t across it towards the lower right, both in pixels from the canvas
centre), so the silhouette stays symmetric about the 45 degree axis while you
tune it. Each facet darkens one step along its outer edge.

Run:  python tools/art/gen_starshard.py
"""
from __future__ import annotations

import math

import numpy as np

from common import TEX, blank, outline_mask, pixel_centres, point_in_poly, rel, rgb, save_png

S = 16
R2 = math.sqrt(2)

WHITE_HI = rgb("#ffffff")
WHITE = rgb("#e6f1f8")
WHITE_MID = rgb("#c3d6e4")
WHITE_EDGE = rgb("#a9c0d2")
TURQ_LIGHT = rgb("#8becE2")
TURQ = rgb("#34c2b8")
GOLD_HI = rgb("#fbe08a")
GOLD = rgb("#f2c354")
GOLD_DARK = rgb("#c98d2a")
OUTLINE = rgb("#0f3a47")

# key points (s, t)
TIP = (9.4, 0.0)
UPPER = (2.6, -4.0)      # widest point, upper-left side
LOWER = (0.0, 4.4)       # widest point, lower-right side
BREAK_A = (-5.4, -2.6)   # the broken base: two corners and a notch
BREAK_NOTCH = (-4.4, -0.2)
BREAK_B = (-6.2, 1.2)
HEART = (1.6, 0.1)       # where the four facets meet

# (name, fill, outer-edge colour, polygon)
FACETS = [
    ("upper_front", WHITE_HI, WHITE, [TIP, UPPER, HEART]),
    ("lower_front", TURQ_LIGHT, TURQ, [TIP, HEART, LOWER]),
    ("upper_back", WHITE_MID, WHITE_EDGE, [HEART, UPPER, BREAK_A, BREAK_NOTCH]),
    ("lower_back", GOLD, GOLD_DARK, [HEART, BREAK_NOTCH, BREAK_B, LOWER]),
]


def to_px(p):
    s, t = p
    return (S / 2 + (s + t) / R2, S / 2 + (-s + t) / R2)


def build() -> np.ndarray:
    img = blank(S, S)
    px, py = pixel_centres(S, S)
    col = np.zeros((S, S, 3), dtype=int)
    body = np.zeros((S, S), dtype=bool)
    masks = {}
    for name, fill, _, poly in FACETS:
        m = point_in_poly(px, py, [to_px(p) for p in poly]) & ~body
        masks[name] = m
        col[m] = fill
        body |= m
    # outer edge of each facet (touching the outline) one step darker, but only on
    # the lower-right side, away from the light
    ol = outline_mask(body)
    ys, xs = np.mgrid[0:S, 0:S]
    shadow_side = (xs + ys) > (S - 1)
    touching = np.zeros_like(body)
    for dy, dx in ((0, 1), (1, 0)):
        touching |= np.roll(np.roll(ol, -dy, axis=0), -dx, axis=1)
    for name, _, edge, _ in FACETS:
        m = masks[name] & touching & shadow_side
        col[m] = edge
    # the heart of the gold facet catches a little more light
    hx, hy = to_px(HEART)
    gold_heart = masks["lower_back"] & (np.abs(xs + 0.5 - hx) + np.abs(ys + 0.5 - hy) < 2.6)
    col[gold_heart] = GOLD_HI

    img[body, :3] = col[body]
    img[body, 3] = 255
    img[ol, :3] = OUTLINE
    img[ol, 3] = 255
    # close the notch at the very point so it ends in one clean corner
    tip = np.argwhere(body & (xs - ys == (xs - ys)[body].max()))
    for y, x in tip:
        if x + 1 < S and y - 1 >= 0 and img[y - 1, x + 1, 3] == 0:
            img[y - 1, x + 1] = (*OUTLINE, 255)
    return img


def main():
    p = save_png(build(), TEX / "item" / "starshard.png")
    print("wrote", rel(p), "16x16  (review it in previews/items.png)")


if __name__ == "__main__":
    main()
