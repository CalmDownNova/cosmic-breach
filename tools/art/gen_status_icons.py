"""The status effect icons that had none (playtest 3: they showed as the missing texture in the inventory and HUD).

    textures/mob_effect/rift.png       18 by 18: a violet tear opening across a dark disc, its edges lit
    textures/mob_effect/scorch.png     18 by 18: an ember flame, white-hot at its root
    textures/mob_effect/silenced.png   18 by 18: a pale note struck through

Same conventions as the other effect icons (voidsick, vesper_slow, aligned): 18 by 18, hard alpha, a few tones.

Run:  python tools/art/gen_status_icons.py
"""
from __future__ import annotations

import math

import numpy as np

from common import TEX, rel, rgb, save_png

N = 18
VIOLET = [rgb("#f4e6ff"), rgb("#c99bff"), rgb("#8f55e8"), rgb("#5a2aa8"), rgb("#2c1458")]
EMBER = [rgb("#fffbe0"), rgb("#ffd36b"), rgb("#ff9a2e"), rgb("#e0521c"), rgb("#8a2410")]
PALE = [rgb("#f3f6fb"), rgb("#c3cbdb"), rgb("#8792ab"), rgb("#4e5873")]
STRIKE = [rgb("#ff8fa3"), rgb("#d0304f")]


def blank():
    return np.zeros((N, N, 4), dtype=np.uint8)


def put(img, x, y, c):
    if 0 <= x < N and 0 <= y < N:
        img[y, x] = (*c, 255)


def rift_icon():
    img = blank()
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - 9, y + 0.5 - 9
            d = math.hypot(dx, dy)
            if d > 8.2:
                continue
            # the tear: a jagged line from the top right to the bottom left
            along = (dx - dy) / math.sqrt(2)
            across = (dx + dy) / math.sqrt(2) + 0.9 * math.sin(along * 1.7)
            width = 1.6 * max(0.0, 1.0 - abs(along) / 8.0) + 0.3
            if abs(across) < width * 0.45:
                img[y, x] = (*VIOLET[0], 255)
            elif abs(across) < width:
                img[y, x] = (*VIOLET[1], 255)
            elif abs(across) < width + 1.1:
                img[y, x] = (*VIOLET[2], 255)
            elif d > 7.2:
                img[y, x] = (*VIOLET[3], 255)
            else:
                img[y, x] = (*VIOLET[4], 255)
    return img


def scorch_icon():
    img = blank()
    for y in range(N):
        for x in range(N):
            fx = x + 0.5 - 9
            fy = y + 0.5  # 0 at the top
            if fy < 1.5 or fy > 16.5:
                continue
            t = (fy - 1.5) / 15.0  # 0 at the tip, 1 at the root
            sway = 1.2 * math.sin(t * 3.4) * (1.0 - t)
            half = 6.6 * math.sin(min(1.0, t * 1.15) * math.pi * 0.5) * (1.0 - 0.35 * max(0.0, t - 0.8) / 0.2)
            off = abs(fx - sway)
            if off > half:
                continue
            inner = off / max(half, 0.01)
            if t > 0.55 and inner < 0.35:
                tone = 0
            elif t > 0.35 and inner < 0.6:
                tone = 1
            elif inner < 0.8:
                tone = 2
            else:
                tone = 3 if t < 0.85 else 4
            img[y, x] = (*EMBER[tone], 255)
    return img


def silenced_icon():
    img = blank()
    # a note: an oval head at the bottom left and a stem with a flag
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - 6.5, y + 0.5 - 13.0
            e = (dx / 3.2) ** 2 + (dy / 2.4) ** 2
            if e <= 1.0:
                img[y, x] = (*PALE[0 if dx < -0.5 and dy < 0 else 1 if e < 0.6 else 2], 255)
    for y in range(3, 13):
        put(img, 9, y, PALE[1])
        put(img, 10, y, PALE[2])
    for i, y in enumerate(range(3, 8)):
        for x in range(11, 11 + max(1, 4 - i // 2)):
            put(img, x, y + i // 3, PALE[1] if x < 13 else PALE[2])
    # struck through, top left to bottom right
    for k in range(-1, 19):
        for w in (0, 1):
            x, y = k, k + w - 1
            if 1 <= x <= 16 and 1 <= y <= 16:
                put(img, x, y, STRIKE[w])
    return img


def main():
    out = TEX / "mob_effect"
    for name, fn in (("rift", rift_icon), ("scorch", scorch_icon), ("silenced", silenced_icon)):
        print("wrote", rel(save_png(fn(), out / f"{name}.png")))


if __name__ == "__main__":
    main()
