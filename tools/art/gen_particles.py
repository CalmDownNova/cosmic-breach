"""Particle sprites, white on transparent so the game can tint them:

    textures/particle/star_glint.png   32x32  four-point star with a soft core
    textures/particle/spark.png        16x16  thin vertical streak, hot in the middle
    textures/particle/shard_0.png      16x16  crystal fragments: long splinter,
    textures/particle/shard_1.png      16x16  small faceted chip,
    textures/particle/shard_2.png      16x16  broken wedge
    textures/particle/ring.png         32x32  thin ring

Soft sprites (glint, spark, ring) are drawn as analytic alpha shapes and
supersampled. The shards are crisp pixel art in greys (255 lit, 214 mid,
168 shade, 128 edge), which still tints cleanly because the game multiplies.
Every sprite is exactly zero alpha on its border.

Run:  python tools/art/gen_particles.py
"""
from __future__ import annotations

import numpy as np

from common import TEX, blank, outline_mask, pixel_centres, point_in_poly, rel, save_png, supersample, white_alpha

OUT = TEX / "particle"


# ---------------------------------------------------------------- soft sprites
def star_glint(size: int = 32) -> np.ndarray:
    c = size / 2
    ray_len = size / 2 - 1.0         # rays die just inside the border
    ray_w = 0.75                     # half width of a ray at its root, pixels

    def f(x, y):
        dx, dy = x - c, y - c
        r = np.hypot(dx, dy)
        core = np.exp(-(r / 2.6) ** 2)
        halo = 0.35 * np.exp(-(r / 5.5) ** 2)

        def ray(along, across):
            t = np.clip(np.abs(along) / ray_len, 0, 1)
            width = ray_w * (1 - t) + 0.3
            return np.exp(-(across / width) ** 2) * (1 - t) ** 1.25

        rays = np.maximum(ray(dx, dy), ray(dy, dx))
        return np.clip(np.maximum(core + halo, rays), 0, 1)

    a = supersample(f, size, size, 6)
    return white_alpha(_zero_border(a))


def spark(size: int = 16) -> np.ndarray:
    """A two pixel core (columns 7 and 8 at 16px) that tapers to points, with a
    faint glow one pixel either side. Symmetric, so any end can lead."""
    c = size / 2
    half_len = size / 2 - 0.8

    def f(x, y):
        dx, dy = np.abs(x - c), y - c
        t = np.clip(np.abs(dy) / half_len, 0, 1)
        along = (1 - t ** 2) ** 1.2
        half_w = 1.0 * (1 - t ** 3) + 0.15       # the core narrows towards the ends
        core = (dx <= half_w).astype(float)
        glow = 0.28 * np.exp(-((dx - half_w).clip(0) / 0.9) ** 2)
        return np.clip(np.maximum(core, glow) * along, 0, 1)

    a = supersample(f, size, size, 6)
    return white_alpha(_zero_border(a))


def ring(size: int = 32) -> np.ndarray:
    c = size / 2
    radius = size / 2 - 3.2
    sigma = 0.95

    def f(x, y):
        r = np.hypot(x - c, y - c)
        return np.exp(-((r - radius) / sigma) ** 2)

    a = supersample(f, size, size, 6)
    return white_alpha(_zero_border(a))


def _zero_border(a: np.ndarray) -> np.ndarray:
    a = a.copy()
    a[a < 1.5 / 255] = 0
    a[0, :] = a[-1, :] = 0
    a[:, 0] = a[:, -1] = 0
    return a


# ---------------------------------------------------------------- crisp shards
LIT, MID, SHADE, EDGE = 255, 214, 168, 128


def shard_sprite(facets, size: int = 16) -> np.ndarray:
    """facets: list of (grey, polygon in pixel coords). Later facets win."""
    img = blank(size, size)
    px, py = pixel_centres(size, size)
    body = np.zeros((size, size), dtype=bool)
    grey = np.zeros((size, size), dtype=int)
    for g, poly in facets:
        m = point_in_poly(px, py, poly)
        grey[m] = g
        body |= m
    img[body, :3] = grey[body, None]
    img[body, 3] = 255
    ol = outline_mask(body)
    img[ol, :3] = EDGE
    img[ol, 3] = 255
    return img


def shard_0() -> np.ndarray:
    # long thin splinter, point up-right
    return shard_sprite([
        (LIT, [(13.6, 1.6), (6.0, 7.4), (3.2, 12.6), (6.6, 9.6)]),
        (SHADE, [(13.6, 1.6), (6.6, 9.6), (3.2, 12.6), (8.8, 9.2)]),
    ])


def shard_1() -> np.ndarray:
    # small faceted chip: a four-facet diamond, like a tiny starshard
    c = (8.0, 8.0)
    top, right, bottom, left = (8.6, 2.4), (13.4, 8.4), (7.4, 13.6), (2.6, 7.6)
    return shard_sprite([
        (LIT, [top, left, c]),
        (MID, [top, c, right]),
        (SHADE, [c, bottom, right]),
        (MID, [left, bottom, c]),
    ])


def shard_2() -> np.ndarray:
    # broken wedge: a triangle with one chipped corner
    # long sharp point down-left, jagged break across the top right
    point = (2.4, 13.6)
    return shard_sprite([
        (LIT, [point, (5.0, 4.4), (9.2, 2.6), (8.2, 6.8)]),
        (MID, [(9.2, 2.6), (10.4, 4.6), (13.6, 4.2), (8.2, 6.8)]),
        (SHADE, [point, (8.2, 6.8), (13.6, 4.2), (11.6, 8.4)]),
    ])


def main():
    outs = {
        "star_glint": star_glint(32),
        "spark": spark(16),
        "shard_0": shard_0(),
        "shard_1": shard_1(),
        "shard_2": shard_2(),
        "ring": ring(32),
    }
    for name, im in outs.items():
        border = np.concatenate([im[0, :, 3], im[-1, :, 3], im[:, 0, 3], im[:, -1, 3]])
        assert border.max() == 0, f"{name}: alpha on the border"
        print("wrote", rel(save_png(im, OUT / f"{name}.png")), f"{im.shape[1]}x{im.shape[0]}")


if __name__ == "__main__":
    main()
