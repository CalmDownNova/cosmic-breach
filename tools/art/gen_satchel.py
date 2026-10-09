"""The Satchel (1.2): textures/item/satchel.png (16x16).

A rounded leather bag on a strap: a flap with a starsteel clasp and a pale star stitched on the front. Light from the
top left; hard alpha; a few leather tones, a dark outline.

Run:  python tools/art/gen_satchel.py
"""
from __future__ import annotations

import numpy as np

from common import TEX, blank, rel, rgb, save_png

LEATHER = [rgb("#d9a066"), rgb("#b9793f"), rgb("#8f5a2b"), rgb("#6a4020")]
OUTLINE = rgb("#2c1a0c")
STEEL = [rgb("#f2f6fc"), rgb("#9fb1ca"), rgb("#4b5a74")]
STAR = rgb("#ffe9a8")

# bag body rows: (y, x_from, x_to)
BODY = [
    (5, 4, 12), (6, 3, 13), (7, 3, 13), (8, 2, 14), (9, 2, 14), (10, 2, 14), (11, 2, 14), (12, 3, 13), (13, 4, 12),
]
FLAP = {5, 6, 7}
STRAP = [(3, 6), (2, 7), (2, 8), (3, 9), (4, 10), (4, 5)]


def build() -> np.ndarray:
    img = blank(16, 16)
    inside = np.zeros((16, 16), dtype=bool)
    for y, x0, x1 in BODY:
        inside[y, x0:x1] = True
    for x, y in STRAP:
        inside[y, x] = True
    # outline first, then fill
    for y in range(16):
        for x in range(16):
            if inside[y, x]:
                continue
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < 16 and 0 <= nx < 16 and inside[ny, nx]:
                    img[y, x] = (*OUTLINE, 255)
                    break
    for y, x0, x1 in BODY:
        for x in range(x0, x1):
            tone = 1
            if x - x0 <= 1 or y == BODY[0][0]:
                tone = 0
            if x1 - x <= 2 or y >= 12:
                tone = 2
            if y == 13 or x1 - x == 1:
                tone = 3
            if y in FLAP and y != 5:
                tone = min(tone + 0, 3)
            img[y, x] = (*LEATHER[tone], 255)
    for x, y in STRAP:
        img[y, x] = (*LEATHER[2], 255)
    # the flap's lower edge: a dark seam
    for x in range(3, 13):
        img[8, x] = (*LEATHER[3], 255)
    # clasp
    img[8, 7] = (*STEEL[1], 255)
    img[8, 8] = (*STEEL[0], 255)
    img[9, 7] = (*STEEL[2], 255)
    img[9, 8] = (*STEEL[1], 255)
    # stitched star
    for x, y in ((8, 11), (7, 12), (9, 12), (8, 13)):
        img[y, x] = (*STAR, 255)
    img[12, 8] = (*STAR, 255)
    return img


def main():
    path = save_png(build(), TEX / "item" / "satchel.png")
    print("wrote", rel(path))


if __name__ == "__main__":
    main()
