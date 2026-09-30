"""Cosmic weather textures (W3b), white on transparent (shape carried in alpha):

    textures/fx/meteor_ring.png    128x128  a crisp ring at the rim with a soft glow
                                            inside it and nothing in the middle: the
                                            meteor circle's edge, its closing ring and
                                            the impact's shock ring
    textures/fx/meteor_disc.png     64x64   an even disc with a soft edge and faint
                                            concentric bands: the circle's red fill
    textures/gui/weather_vignette.png 256x256 clear in the middle, rising to the screen's
                                            edges and corners: tinted orange for a player
                                            caught in a Flare, violet black in a Surge

RGB is white everywhere, including transparent texels, so filtering never pulls a
dark fringe in; the game tints them.

Run:  python tools/art/gen_weather.py
"""
from __future__ import annotations

import numpy as np

from common import TEX, rel, save_png, smoothstep, supersample, white_alpha


def ring(size: int = 128) -> np.ndarray:
    c = size / 2
    R = size / 2 - 1.0

    def f(x, y):
        r = np.hypot(x - c, y - c) / R            # 1 at the rim
        edge = np.exp(-((r - 0.955) / 0.028) ** 2)  # the crisp band
        inner = 0.32 * smoothstep(0.55, 0.94, r) * (r < 0.955)  # glow creeping in from the rim
        outer = 1.0 - smoothstep(0.985, 1.0, r)
        return np.clip(np.maximum(edge, inner) * outer, 0, 1)

    a = supersample(f, size, size, 4)
    a[a < 1.5 / 255] = 0
    return white_alpha(a)


def disc(size: int = 64) -> np.ndarray:
    c = size / 2
    R = size / 2 - 0.5

    def f(x, y):
        r = np.hypot(x - c, y - c) / R
        bands = 0.86 + 0.14 * np.cos(r * np.pi * 6.0) ** 2
        return np.clip(bands * (1.0 - smoothstep(0.9, 1.0, r)), 0, 1)

    a = supersample(f, size, size, 4)
    a[a < 1.5 / 255] = 0
    return white_alpha(a)


def vignette(size: int = 256) -> np.ndarray:
    c = size / 2

    def f(x, y):
        # a rounded square falloff, so the sides glow as well as the corners
        dx = np.abs(x - c) / c
        dy = np.abs(y - c) / c
        d = (dx ** 4 + dy ** 4) ** 0.25
        return np.clip(smoothstep(0.45, 1.02, d) ** 1.6, 0, 1)

    a = supersample(f, size, size, 2)
    a[a < 1.5 / 255] = 0
    return white_alpha(a)


def main():
    for path, im in (("fx/meteor_ring.png", ring()), ("fx/meteor_disc.png", disc()),
                     ("gui/weather_vignette.png", vignette())):
        print("wrote", rel(save_png(im, TEX / path)), f"{im.shape[1]}x{im.shape[0]}")


if __name__ == "__main__":
    main()
