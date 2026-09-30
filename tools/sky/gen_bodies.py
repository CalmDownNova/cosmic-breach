"""Bakes the textures of Aetheria's sky bodies and aurora.

    python tools/sky/gen_bodies.py

Writes to assets/cosmicbreach/textures/sky/:
  thalassa_bands.png  512 x 256  the gas giant's cloud bands, equirectangular (u longitude, v latitude),
                                 seamless in longitude; the shader drifts the bands at speeds by latitude
  thalassa_ring.png   512 x 4    the ring by radius (left: inner edge): RGB colour, A opacity
  aurora.png          256 x 128  tileable: R vertical rays (varies along u), G slow folds
and tools/sky/previews/bodies.png for review.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.dont_write_bytecode = True

from noise import Noise3, fbm, warp  # noqa: E402

REPO = HERE.parents[1]
OUT = REPO / "src" / "main" / "resources" / "assets" / "cosmicbreach" / "textures" / "sky"
PREVIEWS = HERE / "previews"


def hexc(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def ramp(t: np.ndarray, stops: list[tuple[float, str]]) -> np.ndarray:
    """Piecewise-linear colour ramp over t in 0..1."""
    xs = np.array([s[0] for s in stops])
    cs = np.array([hexc(s[1]) for s in stops])
    out = np.zeros(t.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(t, xs, cs[:, c])
    return out


def thalassa_bands(w: int = 512, h: int = 256) -> np.ndarray:
    """Aquamarine, cream and pale gold belts with turbulent edges and one pale storm."""
    n1, n2 = Noise3(71), Noise3(72)
    lon = (np.arange(w) + 0.5) / w * 2 * np.pi
    lat = np.pi / 2 - (np.arange(h) + 0.5) / h * np.pi
    lo, la = np.meshgrid(lon, lat)
    # sample on a cylinder so the map wraps in longitude with no seam
    p = np.stack([np.cos(lo) * 1.6, np.sin(lo) * 1.6, la * 3.2], -1)
    q = warp(n1, p * np.array([1.0, 1.0, 0.35]), amount=0.35, octaves=3, scale=1.2)
    turb = fbm(n2, q * np.array([1.4, 1.4, 5.0]), 4)
    # latitude, pushed around by the turbulence: the belts' edges curl
    y = np.sin(la) + 0.045 * turb + 0.02 * fbm(n1, p * 3.0, 3)
    # belts: a fixed pattern of zones in sin(latitude), symmetric-ish but not mirrored
    t = 0.5 + 0.5 * np.sin(y * 9.5 + 0.35 * np.sin(y * 3.0)) * np.cos(y * 2.2)
    col = ramp(t, [(0.0, "#3C9EA8"), (0.22, "#63C3C2"), (0.42, "#A6E0D6"),
                   (0.58, "#EFE9D2"), (0.75, "#E3CC96"), (0.9, "#C7A566"), (1.0, "#B08F58")])
    # fine streaks along the flow
    streak = fbm(n2, q * np.array([2.5, 2.5, 18.0]), 3) * 0.06
    col = col * (1.0 + streak[..., None])
    # darker, bluer poles
    pole = smoothstep(0.62, 0.95, np.abs(np.sin(la)))[..., None]
    col = col * (1 - pole * 0.35) + hexc("#2B6E8C") * pole * 0.3
    # the storm: a pale oval in the southern tropics
    sx, sy = np.pi * 0.7, -0.38
    dx = np.angle(np.exp(1j * (lo - sx))) / 0.30
    dy = (la - sy) / 0.085
    d2 = dx * dx + dy * dy
    swirl = 0.5 + 0.5 * np.sin(np.sqrt(d2) * 9.0 - np.arctan2(dy, dx) * 2.0)
    storm = np.exp(-d2 * 1.2)[..., None]
    col = col * (1 - storm * 0.75) + (hexc("#F4F8F2") * (0.85 + 0.15 * swirl[..., None])) * storm * 0.75
    return np.clip(col, 0, 1)


def thalassa_ring(w: int = 512) -> np.ndarray:
    """Ringlets across the ring's width, one dark gap, pale gold dust fading at both edges."""
    r = (np.arange(w) + 0.5) / w
    rng = np.random.default_rng(73)
    opacity = 0.55 + 0.25 * np.sin(r * 60.0) * np.sin(r * 23.0 + 1.0)
    for _ in range(26):     # ringlets and thin gaps
        c = rng.uniform(0.05, 0.95)
        width = rng.uniform(0.002, 0.012)
        opacity += rng.uniform(-0.35, 0.3) * np.exp(-((r - c) / width) ** 2)
    opacity *= 1.0 - 0.92 * np.exp(-((r - 0.62) / 0.022) ** 2)         # the great gap
    opacity *= smoothstep(0.0, 0.08, r) * (1.0 - smoothstep(0.88, 1.0, r)) * 0.85 + 0.0
    opacity = np.clip(opacity, 0.0, 0.95)
    col = ramp(r, [(0.0, "#C9B98E"), (0.35, "#EADBB2"), (0.6, "#F4EBD0"), (0.8, "#D8C699"), (1.0, "#BFAE86")])
    col *= (0.9 + 0.1 * np.sin(r * 140.0))[..., None]
    rgba = np.concatenate([np.clip(col, 0, 1), opacity[..., None]], -1)
    return np.repeat(rgba[None, :, :], 4, axis=0)


def aurora(w: int = 256, h: int = 128) -> np.ndarray:
    """R: vertical rays (a function of u only, so it is sampled along u); G: slow 2D folds. Both tile."""
    n1, n2 = Noise3(81), Noise3(82)
    u = (np.arange(w) + 0.5) / w * 2 * np.pi
    v = (np.arange(h) + 0.5) / h * 2 * np.pi
    uu, vv = np.meshgrid(u, v)
    # rays: several frequencies around the u circle (a torus in noise space keeps both axes seamless)
    pr = np.stack([np.cos(uu) * 6.0, np.sin(uu) * 6.0, np.zeros_like(uu)], -1)
    rays = fbm(n1, pr, 4, gain=0.6) * 0.5 + 0.5
    rays = smoothstep(0.3, 0.85, rays) ** 1.3
    pf = np.stack([np.cos(uu) * 1.5, np.sin(uu) * 1.5, np.cos(vv) * 1.2 + np.sin(vv) * 0.7], -1)
    folds = smoothstep(-0.6, 0.7, fbm(n2, pf, 3))
    out = np.zeros((h, w, 4))
    out[..., 0] = rays
    out[..., 1] = folds
    out[..., 3] = 1.0
    return out


def save(arr: np.ndarray, name: str, mode_rgba: bool) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    img = np.round(np.clip(arr, 0, 1) * 255).astype(np.uint8)
    path = OUT / name
    Image.fromarray(img).save(path, optimize=True)
    return path


def main() -> int:
    bands = thalassa_bands()
    ring = thalassa_ring()
    aur = aurora()
    save(bands, "thalassa_bands.png", False)
    save(ring, "thalassa_ring.png", True)
    save(aur, "aurora.png", True)

    # review sheet: bands, the ring profile as a strip over black and over blue, the aurora channels
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    W = 512
    strip = np.repeat(ring[:1], 40, axis=0)
    over_blue = strip[..., :3] * strip[..., 3:4] + np.array([0.3, 0.55, 0.9]) * (1 - strip[..., 3:4])
    over_black = strip[..., :3] * strip[..., 3:4]
    aur_r = np.repeat(np.repeat(aur[..., :1], 3, -1), 2, axis=1)
    aur_g = np.repeat(np.repeat(aur[..., 1:2], 3, -1), 2, axis=1)
    sheet = np.concatenate([bands, over_black, over_blue, aur_r[:, :W], aur_g[:, :W]], axis=0)
    Image.fromarray(np.round(np.clip(sheet, 0, 1) * 255).astype(np.uint8)).save(PREVIEWS / "bodies.png")
    for n in ("thalassa_bands.png", "thalassa_ring.png", "aurora.png"):
        print(f"{n}: {(OUT / n).stat().st_size / 1024:.0f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
