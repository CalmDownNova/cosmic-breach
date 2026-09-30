"""Helpers for the 16x16 block and item textures of the World lane (W1).

On top of common.py's conventions:

* Block textures are 16x16 and tile seamlessly: every noise field here wraps around,
  so a texture repeated over a wall shows no seams. Hard alpha (0 or 255) everywhere
  except the two glasses (Rimeglass, Rift Glass), which are translucent.
* A material is a short ramp of hand-picked colours, darkest first. Noise fields are
  floats mapped to ramp indices *by share* (the fraction of texels that gets each
  shade), so tuning a texture means tuning shares and ramps, not thresholds.
* Light comes from the top left, as in the item art.
* Every texture has its own random stream seeded from its name, so the output is
  identical on every run and changing one texture never reshuffles another.
"""
from __future__ import annotations

import zlib

import numpy as np

from common import rgb

S = 16


def rng_for(name: str) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32(name.encode("utf-8")))


def ramp(*hexes: str) -> list[tuple[int, int, int]]:
    return [rgb(h) for h in hexes]


# ---------------------------------------------------------------- noise fields

def periodic_noise(rng: np.random.Generator, cells: int, size: int = S, aspect: tuple[int, int] = (1, 1)) -> np.ndarray:
    """Value noise on a cells x cells lattice that wraps around the texture.

    ``aspect`` multiplies the cells down and across: (1, 3) has 3x as many cells across as
    down, so features are narrow and tall (vertical grain); (3, 1) gives horizontal bands.
    """
    cy, cx = cells * aspect[0], cells * aspect[1]
    lat = rng.random((cy, cx))

    def axis(n_cells):
        u = (np.arange(size) + 0.5) * n_cells / size - 0.5
        i0 = np.floor(u).astype(int)
        f = u - i0
        f = f * f * (3 - 2 * f)
        return i0 % n_cells, (i0 + 1) % n_cells, f

    y0, y1, fy = axis(cy)
    x0, x1, fx = axis(cx)
    a = lat[np.ix_(y0, x0)]
    b = lat[np.ix_(y0, x1)]
    c = lat[np.ix_(y1, x0)]
    d = lat[np.ix_(y1, x1)]
    fx = fx[None, :]
    fy = fy[:, None]
    top = a + (b - a) * fx
    bot = c + (d - c) * fx
    return top + (bot - top) * fy


def fbm(rng: np.random.Generator, octaves=((2, 0.5), (4, 0.3), (8, 0.2)), size: int = S, aspect=(1, 1)) -> np.ndarray:
    out = np.zeros((size, size))
    for cells, w in octaves:
        out += w * periodic_noise(rng, cells, size, aspect)
    out -= out.min()
    return out / max(out.max(), 1e-9)


def by_share(field: np.ndarray, shares) -> np.ndarray:
    """Ramp index per texel: the lowest ``shares[0]`` fraction of the field gets 0, and so on."""
    flat = field.ravel()
    order = np.argsort(flat, kind="stable")
    idx = np.empty(flat.size, dtype=int)
    cum = np.round(np.cumsum(shares) / float(sum(shares)) * flat.size).astype(int)
    start = 0
    for k, end in enumerate(cum):
        idx[order[start:end]] = k
        start = end
    return idx.reshape(field.shape)


def from_ramp(idx: np.ndarray, colours) -> np.ndarray:
    pal = np.array([(*c, 255) for c in colours], dtype=np.uint8)
    return pal[np.clip(idx, 0, len(colours) - 1)]


def wrap_shift(a: np.ndarray, dy: int, dx: int) -> np.ndarray:
    return np.roll(np.roll(a, dy, axis=0), dx, axis=1)


# ---------------------------------------------------------------- stamping

def stamp(img: np.ndarray, x: int, y: int, pattern, wrap: bool = True) -> None:
    """Paints ``pattern`` (list of (dx, dy, rgb)) at (x, y), wrapping round the edges."""
    h, w = img.shape[:2]
    for dx, dy, c in pattern:
        px, py = x + dx, y + dy
        if wrap:
            px %= w
            py %= h
        elif not (0 <= px < w and 0 <= py < h):
            continue
        img[py, px] = (*c, 255)


def scatter(rng: np.random.Generator, count: int, min_dist: float, allowed: np.ndarray | None = None,
            size: int = S, tries: int = 400) -> list[tuple[int, int]]:
    """Up to ``count`` points at least ``min_dist`` apart on the wrapping 16x16 torus."""
    pts: list[tuple[int, int]] = []
    for _ in range(tries):
        if len(pts) >= count:
            break
        x, y = int(rng.integers(size)), int(rng.integers(size))
        if allowed is not None and not allowed[y, x]:
            continue
        ok = True
        for px, py in pts:
            ddx = min(abs(px - x), size - abs(px - x))
            ddy = min(abs(py - y), size - abs(py - y))
            if ddx * ddx + ddy * ddy < min_dist * min_dist:
                ok = False
                break
        if ok:
            pts.append((x, y))
    return pts


# ---------------------------------------------------------------- bricks and bevels

def brick_layout(course_h: int, brick_w: int, offsets, size: int = S):
    """Brick ids, mortar mask and each texel's position inside its brick.

    Each course is ``course_h`` texels tall with a one-texel mortar line along its bottom;
    bricks are ``brick_w`` wide with a mortar column at their right edge; ``offsets[k]``
    shifts course k sideways. Returns (ids, mortar, iy, ix): iy/ix count from the brick's
    top-left texel, which the shading uses for the highlight and shadow edges.
    """
    ys, xs = np.mgrid[0:size, 0:size]
    course = ys // course_h
    off = np.array(offsets)[course % len(offsets)]
    xx = (xs + off) % size
    ids = course * 100 + xx // brick_w
    mortar = ((ys % course_h) == course_h - 1) | ((xx % brick_w) == brick_w - 1)
    return ids, mortar, ys % course_h, xx % brick_w


def bevel_frame(img: np.ndarray, light, dark, corner=None) -> None:
    """A one-texel frame lit from the top left: top row and left column light, the rest dark."""
    img[0, :, :3] = light
    img[:, 0, :3] = light
    img[-1, :, :3] = dark
    img[:, -1, :3] = dark
    if corner is not None:
        img[0, -1, :3] = corner
        img[-1, 0, :3] = corner


# ---------------------------------------------------------------- ASCII sprites

def sprite(rows: list[str], palette: dict[str, str]) -> np.ndarray:
    """A texture from ASCII art: one character per texel, '.' transparent."""
    h = len(rows)
    w = len(rows[0])
    out = np.zeros((h, w, 4), dtype=np.uint8)
    for y, row in enumerate(rows):
        if len(row) != w:
            raise ValueError(f"row {y} is {len(row)} wide, expected {w}: {row!r}")
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in palette:
                raise ValueError(f"no colour for {ch!r} at ({x}, {y})")
            out[y, x] = (*rgb(palette[ch]), 255)
    return out


def recolour(rows: list[str], mapping: dict[str, str]) -> list[str]:
    """Swaps characters in an ASCII sprite (a template shared by several materials)."""
    return ["".join(mapping.get(c, c) for c in row) for row in rows]


def outline(img: np.ndarray, colour, diagonal: bool = False) -> np.ndarray:
    """Adds a one-texel outline round the opaque shape (in place and returned)."""
    solid = img[..., 3] > 0
    grown = solid.copy()
    grown[1:, :] |= solid[:-1, :]
    grown[:-1, :] |= solid[1:, :]
    grown[:, 1:] |= solid[:, :-1]
    grown[:, :-1] |= solid[:, 1:]
    if diagonal:
        grown[1:, 1:] |= solid[:-1, :-1]
        grown[1:, :-1] |= solid[:-1, 1:]
        grown[:-1, 1:] |= solid[1:, :-1]
        grown[:-1, :-1] |= solid[1:, 1:]
    ring = grown & ~solid
    img[ring] = (*colour, 255)
    return img


def voronoi(rng: np.random.Generator, count: int, min_dist: float, size: int = S):
    """Cells round ``count`` scattered seeds on the wrapping torus.

    Returns (cell, rel_x, rel_y, edge): the seed index per texel, each texel's offset
    from its seed (wrapped, in texels), and a mask of texels on a cell boundary.
    """
    pts = np.array(scatter(rng, count, min_dist, size=size), dtype=float) + 0.5
    ys, xs = np.mgrid[0:size, 0:size] + 0.5
    best = np.full((size, size), 1e9)
    second = np.full((size, size), 1e9)
    cell = np.zeros((size, size), int)
    for k, (px, py) in enumerate(pts):
        dx = ((xs - px + size / 2) % size) - size / 2
        dy = ((ys - py + size / 2) % size) - size / 2
        d = np.hypot(dx, dy)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        cell = np.where(closer, k, cell)
        best = np.minimum(best, d)
    rel_x = np.zeros((size, size))
    rel_y = np.zeros((size, size))
    for k, (px, py) in enumerate(pts):
        m = cell == k
        rel_x[m] = (((xs - px + size / 2) % size) - size / 2)[m]
        rel_y[m] = (((ys - py + size / 2) % size) - size / 2)[m]
    return cell, rel_x, rel_y, (second - best) < 0.9
