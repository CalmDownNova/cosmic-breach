"""Reverie Draught: textures/item/reverie_draught.png (16x16).

A round flask like a vanilla potion, so it reads as something to drink:

    cap        gold with a darker lower band (the set's gold)
    neck       clear pale glass
    bulb       a dreamy lilac draught, lighter at the top, with a small gold
               four-point star suspended in it (Aetheria's star glint)
    glass      a white highlight arc on the upper left of the bulb
    outline    deep indigo, one pixel, all round

Shapes are masks on pixel centres (a circle for the bulb, rectangles for the
neck and cap), so the silhouette stays tidy while it is tuned.

Run:  python tools/art/gen_reverie_draught.py
"""
from __future__ import annotations

import numpy as np

from common import TEX, blank, outline_mask, pixel_centres, rel, rgb, save_png

S = 16

OUTLINE = rgb("#241c45")
CAP = rgb("#f2c354")
CAP_HI = rgb("#fbe08a")
CAP_DARK = rgb("#c98d2a")
GLASS = rgb("#d7e8f2")
GLASS_EDGE = rgb("#a9c0d2")
HIGHLIGHT = rgb("#ffffff")
LIQUID_TOP = rgb("#e1d4ff")
LIQUID = rgb("#b9a2f4")
LIQUID_DEEP = rgb("#8f74de")
STAR = rgb("#fff6dc")
STAR_ARM = rgb("#f2c354")

BULB_CX, BULB_CY, BULB_R = 8.0, 10.6, 4.75
LIQUID_TOP_Y = 8.0          # pixel rows whose centre is below this hold the draught
STAR_AT = (8, 11)           # the star's centre pixel (x, y)


def build() -> np.ndarray:
    img = blank(S, S)
    px, py = pixel_centres(S, S)
    bulb = (px - BULB_CX) ** 2 + (py - BULB_CY) ** 2 <= BULB_R ** 2
    neck = (px > 6.0) & (px < 10.0) & (py > 3.0) & (py < 7.5)
    cap = (px > 5.0) & (px < 11.0) & (py > 1.0) & (py < 4.0)
    body = bulb | neck | cap

    col = np.zeros((S, S, 3), dtype=int)
    # glass everywhere first, darker toward the right edge
    col[body] = GLASS
    col[body & (px > BULB_CX + 2.5)] = GLASS_EDGE
    # the draught fills the bulb below its surface, lighter near the top
    liquid = bulb & (py > LIQUID_TOP_Y)
    col[liquid] = LIQUID
    col[liquid & (py < LIQUID_TOP_Y + 1.5)] = LIQUID_TOP
    deep = liquid & ((py > BULB_CY + 2.0) | (px > BULB_CX + 2.6))
    col[deep] = LIQUID_DEEP
    # highlight arc on the upper left of the bulb
    ring = ((px - BULB_CX) ** 2 + (py - BULB_CY) ** 2 >= (BULB_R - 1.3) ** 2) & bulb
    col[ring & (px < BULB_CX - 1.0) & (py < BULB_CY + 0.5)] = HIGHLIGHT
    # the cap: light top row, dark bottom row, a bright pixel on the left
    col[cap] = CAP
    col[cap & (py > 3.0)] = CAP_DARK
    col[cap & (py < 2.0) & (px < 7.0)] = CAP_HI
    # the star: a white centre with four gold arms
    sx, sy = STAR_AT
    col[sy, sx] = STAR
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if bulb[sy + dy, sx + dx]:
            col[sy + dy, sx + dx] = STAR_ARM

    img[body, :3] = col[body]
    img[body, 3] = 255
    ol = outline_mask(body)
    img[ol, :3] = OUTLINE
    img[ol, 3] = 255
    return img


def main():
    p = save_png(build(), TEX / "item" / "reverie_draught.png")
    print("wrote", rel(p), "16x16  (review it in previews/items.png)")


if __name__ == "__main__":
    main()
