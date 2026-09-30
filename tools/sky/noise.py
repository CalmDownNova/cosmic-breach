"""Seeded 3D gradient noise for the sky bakes (numpy, vectorised, deterministic).

Improved Perlin noise (Ken Perlin 2002) on arrays of points, plus fBm and domain
warping. Everything the sky bakes sample lives on the unit sphere, so sampling a
3D field at the direction vector gives textures with no seams anywhere.
"""
from __future__ import annotations

import numpy as np

_GRAD = np.array([
    (1, 1, 0), (-1, 1, 0), (1, -1, 0), (-1, -1, 0),
    (1, 0, 1), (-1, 0, 1), (1, 0, -1), (-1, 0, -1),
    (0, 1, 1), (0, -1, 1), (0, 1, -1), (0, -1, -1),
    (1, 1, 0), (0, -1, 1), (-1, 1, 0), (0, -1, -1),
], dtype=np.float64)


class Noise3:
    """One seeded noise field. Calling it on arrays x, y, z returns values in about [-1, 1]."""

    def __init__(self, seed: int):
        rng = np.random.default_rng(seed)
        p = rng.permutation(256)
        self.perm = np.concatenate([p, p]).astype(np.int64)

    def __call__(self, x, y, z):
        perm = self.perm
        xi = np.floor(x).astype(np.int64)
        yi = np.floor(y).astype(np.int64)
        zi = np.floor(z).astype(np.int64)
        xf, yf, zf = x - xi, y - yi, z - zi
        xi &= 255
        yi &= 255
        zi &= 255
        u, v, w = _fade(xf), _fade(yf), _fade(zf)

        a = perm[xi] + yi
        aa = perm[a] + zi
        ab = perm[a + 1] + zi
        b = perm[xi + 1] + yi
        ba = perm[b] + zi
        bb = perm[b + 1] + zi

        def g(h, dx, dy, dz):
            gr = _GRAD[perm[h] & 15]
            return gr[..., 0] * dx + gr[..., 1] * dy + gr[..., 2] * dz

        x1 = _lerp(u, g(aa, xf, yf, zf), g(ba, xf - 1, yf, zf))
        x2 = _lerp(u, g(ab, xf, yf - 1, zf), g(bb, xf - 1, yf - 1, zf))
        y1 = _lerp(v, x1, x2)
        x3 = _lerp(u, g(aa + 1, xf, yf, zf - 1), g(ba + 1, xf - 1, yf, zf - 1))
        x4 = _lerp(u, g(ab + 1, xf, yf - 1, zf - 1), g(bb + 1, xf - 1, yf - 1, zf - 1))
        y2 = _lerp(v, x3, x4)
        return _lerp(w, y1, y2)


def _fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)


def _lerp(t, a, b):
    return a + t * (b - a)


def fbm(noise: Noise3, p: np.ndarray, octaves: int, lacunarity: float = 2.0, gain: float = 0.5,
        offset=(0.0, 0.0, 0.0)) -> np.ndarray:
    """Fractal sum of `octaves` layers at points p (shape (..., 3)); normalised to about [-1, 1]."""
    total = np.zeros(p.shape[:-1])
    amp, freq, norm = 1.0, 1.0, 0.0
    for o in range(octaves):
        # every octave is shifted so the lattice points never line up
        sx, sy, sz = offset[0] + 17.3 * o, offset[1] + 31.7 * o, offset[2] + 47.9 * o
        total += amp * noise(p[..., 0] * freq + sx, p[..., 1] * freq + sy, p[..., 2] * freq + sz)
        norm += amp
        amp *= gain
        freq *= lacunarity
    return total / norm * 1.6


def warp(noise: Noise3, p: np.ndarray, amount: float, octaves: int = 3, scale: float = 1.0) -> np.ndarray:
    """Domain warp: p moved by a vector fBm field (three decorrelated fBms)."""
    q = p * scale
    dx = fbm(noise, q, octaves, offset=(5.2, 1.3, 9.1))
    dy = fbm(noise, q, octaves, offset=(1.7, 9.2, 3.4))
    dz = fbm(noise, q, octaves, offset=(8.3, 2.8, 6.6))
    return p + amount * np.stack([dx, dy, dz], axis=-1)
