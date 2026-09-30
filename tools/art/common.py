"""Shared helpers for the Cosmic Breach art generators.

Every texture in this folder is produced by code so it can be regenerated and
tuned. Conventions used by all scripts:

* Images are numpy arrays of shape (H, W, 4), dtype uint8, RGBA, row 0 at top.
* Item and entity textures use hard alpha (0 or 255) and a small palette.
* Particle and FX textures are white RGB with the shape carried in alpha
  (straight, not premultiplied), so the game can tint them.
"""
from __future__ import annotations

import os
from pathlib import Path

import numpy as np
from PIL import Image

ART_DIR = Path(__file__).resolve().parent
REPO = ART_DIR.parent.parent
ASSETS = REPO / "src" / "main" / "resources" / "assets" / "cosmicbreach"
TEX = ASSETS / "textures"
PREVIEWS = ART_DIR / "previews"


def rgb(hex_str: str) -> tuple[int, int, int]:
    h = hex_str.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def rgba(hex_str: str, a: int = 255) -> tuple[int, int, int, int]:
    return (*rgb(hex_str), a)


def blank(w: int, h: int) -> np.ndarray:
    return np.zeros((h, w, 4), dtype=np.uint8)


def save_png(img: np.ndarray, path: Path) -> Path:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    arr = np.ascontiguousarray(np.clip(img, 0, 255).astype(np.uint8))
    Image.fromarray(arr, "RGBA").save(path, optimize=True)
    return path


def load_png(path: Path) -> np.ndarray:
    return np.array(Image.open(path).convert("RGBA"))


def white_alpha(alpha: np.ndarray) -> np.ndarray:
    """White RGB everywhere, shape in the alpha channel (0..1 float input)."""
    h, w = alpha.shape
    out = np.full((h, w, 4), 255, dtype=np.uint8)
    out[..., 3] = np.clip(np.round(alpha * 255.0), 0, 255).astype(np.uint8)
    # Fully transparent texels stay white so linear filtering never pulls in black.
    return out


def smoothstep(e0: float, e1: float, x):
    t = np.clip((np.asarray(x, dtype=np.float64) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def supersample(fn, w: int, h: int, ss: int = 4) -> np.ndarray:
    """Evaluate fn(x, y) -> alpha on an ss x ss grid per pixel and box-filter.

    x and y are in pixel units with (0, 0) at the top-left corner of the image,
    so the centre of pixel (i, j) is (i + 0.5, j + 0.5).
    """
    offs = (np.arange(ss) + 0.5) / ss
    xs = (np.arange(w)[None, :, None, None] + offs[None, None, None, :])
    ys = (np.arange(h)[:, None, None, None] + offs[None, None, :, None])
    xs = np.broadcast_to(xs, (h, w, ss, ss))
    ys = np.broadcast_to(ys, (h, w, ss, ss))
    return fn(xs, ys).mean(axis=(2, 3))


def point_in_poly(px: np.ndarray, py: np.ndarray, poly) -> np.ndarray:
    """Even-odd point in polygon test, vectorised over px/py arrays."""
    inside = np.zeros(np.broadcast(px, py).shape, dtype=bool)
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        cond = (y1 > py) != (y2 > py)
        with np.errstate(divide="ignore", invalid="ignore"):
            xint = x1 + (py - y1) * (x2 - x1) / (y2 - y1 + 1e-12)
        inside ^= cond & (px < xint)
    return inside


def pixel_centres(w: int, h: int):
    ys, xs = np.mgrid[0:h, 0:w]
    return xs + 0.5, ys + 0.5


def outline_mask(mask: np.ndarray, diagonal: bool = False) -> np.ndarray:
    """Pixels outside `mask` that touch it (4-neighbour, optionally 8)."""
    m = mask.astype(bool)
    grown = m.copy()
    grown[1:, :] |= m[:-1, :]
    grown[:-1, :] |= m[1:, :]
    grown[:, 1:] |= m[:, :-1]
    grown[:, :-1] |= m[:, 1:]
    if diagonal:
        grown[1:, 1:] |= m[:-1, :-1]
        grown[1:, :-1] |= m[:-1, 1:]
        grown[:-1, 1:] |= m[1:, :-1]
        grown[:-1, :-1] |= m[1:, 1:]
    return grown & ~m


def paint(img: np.ndarray, mask: np.ndarray, colour) -> None:
    c = tuple(colour)
    if len(c) == 3:
        c = (*c, 255)
    img[mask] = c


def upscale(img: np.ndarray, k: int) -> np.ndarray:
    return np.repeat(np.repeat(img, k, axis=0), k, axis=1)


def over(dst: np.ndarray, src: np.ndarray, x: int, y: int) -> None:
    """Alpha-composite src onto dst (both uint8 RGBA) at (x, y), in place."""
    h, w = src.shape[:2]
    d = dst[y:y + h, x:x + w].astype(np.float64)
    s = src.astype(np.float64)
    sa = s[..., 3:4] / 255.0
    da = d[..., 3:4] / 255.0
    out_a = sa + da * (1 - sa)
    rgb_ = (s[..., :3] * sa + d[..., :3] * da * (1 - sa)) / np.maximum(out_a, 1e-6)
    dst[y:y + h, x:x + w, :3] = np.clip(np.round(rgb_), 0, 255).astype(np.uint8)
    dst[y:y + h, x:x + w, 3:4] = np.clip(np.round(out_a * 255), 0, 255).astype(np.uint8)


def checker(w: int, h: int, cell: int = 8, a=(96, 96, 96), b=(112, 112, 112)) -> np.ndarray:
    ys, xs = np.mgrid[0:h, 0:w]
    sel = ((xs // cell) + (ys // cell)) % 2 == 0
    out = np.empty((h, w, 4), dtype=np.uint8)
    out[sel] = (*a, 255)
    out[~sel] = (*b, 255)
    return out


def palette_of(img: np.ndarray) -> list[tuple[int, int, int, int]]:
    flat = img.reshape(-1, 4)
    flat = flat[flat[:, 3] > 0]
    return sorted({tuple(int(v) for v in p) for p in flat})


def rel(path: Path) -> str:
    try:
        return os.path.relpath(path, REPO).replace("\\", "/")
    except ValueError:
        return str(path)
