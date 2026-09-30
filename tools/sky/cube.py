"""The sky's cubemap atlas layout, shared by the bakes and the offline preview.

One PNG holds the six faces in a 3 by 2 grid (+X -X +Y on the top row, -Y +Z -Z below).
Each tile is FACE + 2 * GUTTER pixels square: the gutter is baked with the true
continuation of the view beyond the face edge (the face is rendered with a
slightly wider field of view), so bilinear filtering never mixes in a wrong
neighbour and the sky shows no seams. The GLSL twin of `face_uv` is
`sky_cube_uv` in assets/cosmicbreach/shaders/include/sky_common.glsl.
"""
from __future__ import annotations

import numpy as np

FACE = 512
GUTTER = 4
TILE = FACE + 2 * GUTTER
WIDTH, HEIGHT = 3 * TILE, 2 * TILE


def face_directions(face: int, face_size: int = FACE, gutter: int = GUTTER) -> np.ndarray:
    """Unit view directions for every pixel of one tile (rows top to bottom), shape (T, T, 3)."""
    tile = face_size + 2 * gutter
    c = ((np.arange(tile) + 0.5 - gutter) / face_size) * 2.0 - 1.0
    u, v = np.meshgrid(c, c)            # u across, v down
    one = np.ones_like(u)
    if face == 0:
        d = np.stack([one, -v, -u], -1)     # +X
    elif face == 1:
        d = np.stack([-one, -v, u], -1)     # -X
    elif face == 2:
        d = np.stack([u, one, v], -1)       # +Y
    elif face == 3:
        d = np.stack([u, -one, -v], -1)     # -Y
    elif face == 4:
        d = np.stack([u, -v, one], -1)      # +Z
    else:
        d = np.stack([-u, -v, -one], -1)    # -Z
    return d / np.linalg.norm(d, axis=-1, keepdims=True)


def face_uv(d: np.ndarray):
    """Direction(s) to (face, u, v) with u, v in [-1, 1]; the inverse of face_directions."""
    a = np.abs(d)
    x, y, z = d[..., 0], d[..., 1], d[..., 2]
    face = np.where((a[..., 0] >= a[..., 1]) & (a[..., 0] >= a[..., 2]), np.where(x > 0, 0, 1),
                    np.where(a[..., 1] >= a[..., 2], np.where(y > 0, 2, 3), np.where(z > 0, 4, 5)))
    u = np.select([face == 0, face == 1, face == 2, face == 3, face == 4, face == 5],
                  [-z / a[..., 0], z / a[..., 0], x / a[..., 1], x / a[..., 1], x / a[..., 2], -x / a[..., 2]])
    v = np.select([face == 0, face == 1, face == 2, face == 3, face == 4, face == 5],
                  [-y / a[..., 0], -y / a[..., 0], z / a[..., 1], -z / a[..., 1], -y / a[..., 2], -y / a[..., 2]])
    return face, u, v


def assemble(tiles: list[np.ndarray]) -> np.ndarray:
    """Six (T, T, C) tiles into the 3 by 2 atlas."""
    t = tiles[0].shape[0]
    out = np.zeros((2 * t, 3 * t, tiles[0].shape[2]), dtype=tiles[0].dtype)
    for f, tile in enumerate(tiles):
        r, c = divmod(f, 3)
        out[r * t:(r + 1) * t, c * t:(c + 1) * t] = tile
    return out


def sample(atlas: np.ndarray, d: np.ndarray) -> np.ndarray:
    """Bilinear lookup of directions d in an atlas (for previews), like the shader does."""
    face, u, v = face_uv(d)
    h, w = atlas.shape[:2]
    t = h // 2
    fs = t - 2 * GUTTER
    col, row = face % 3, face // 3
    px = col * t + GUTTER + (u * 0.5 + 0.5) * fs - 0.5
    py = row * t + GUTTER + (v * 0.5 + 0.5) * fs - 0.5
    x0 = np.clip(np.floor(px).astype(int), 0, w - 2)
    y0 = np.clip(np.floor(py).astype(int), 0, h - 2)
    fx = (px - x0)[..., None]
    fy = (py - y0)[..., None]
    a = atlas.astype(np.float64)
    top = a[y0, x0] * (1 - fx) + a[y0, x0 + 1] * fx
    bot = a[y0 + 1, x0] * (1 - fx) + a[y0 + 1, x0 + 1] * fx
    return top * (1 - fy) + bot * fy
