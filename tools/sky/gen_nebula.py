"""Bakes Aetheria's nebula dome: one cubemap atlas per layer.

    python tools/sky/gen_nebula.py            # all three layers
    python tools/sky/gen_nebula.py reach      # one

Writes assets/cosmicbreach/textures/sky/nebula_<layer>.png (RGBA, the 3 by 2 atlas of
cube.py) and tools/sky/previews/nebula_<layer>.jpg (an equirectangular unwrap on black
and over the layer's day sky, for review).

RGB is the nebula's light (added to the sky, scaled by the time of day in the shader);
A is dust (how much of the sky behind it the nebula dims). The pattern is domain-warped
fBm (4 octaves) sampled on the unit sphere, so the six faces meet with no seams, shaped
by a tilted band (the galactic plane of this sky) and a few broad clouds.
"""
from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.dont_write_bytecode = True

import cube  # noqa: E402
from noise import Noise3, fbm, warp  # noqa: E402

REPO = HERE.parents[1]
OUT = REPO / "src" / "main" / "resources" / "assets" / "cosmicbreach" / "textures" / "sky"
PREVIEWS = HERE / "previews"


def hexc(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


@dataclass
class Recipe:
    seed: int
    band_normal: tuple       # the band's great circle, as its normal (celestial frame)
    band_width: float        # sin of the band's half width
    color_a: str             # the two cloud colours
    color_b: str
    highlight: str           # filament edges
    dust: str                # tint of the dust lanes' dimming (for the preview only)
    coverage: float          # how much of the band holds cloud (0..1)
    brightness: float        # peak light
    dust_strength: float     # peak dust alpha
    blob_count: int          # broad clouds off the band


RECIPES = {
    # Gold and teal: sunlit veils, soft, with few dust lanes (the Reach is daylight).
    "reach": Recipe(seed=31, band_normal=(0.35, 0.80, -0.48), band_width=0.40,
                    color_a="#FFC766", color_b="#3FD8C8", highlight="#FFF1C8", dust="#6A4A20",
                    coverage=0.72, brightness=1.0, dust_strength=0.35, blob_count=5),
    # Silver and cyan: cold streamers and dust lanes, like looking through the belt's haze.
    "drift": Recipe(seed=47, band_normal=(-0.55, 0.62, 0.56), band_width=0.40,
                    color_a="#DCE6F4", color_b="#46D2FF", highlight="#FFFFFF", dust="#20304A",
                    coverage=0.66, brightness=0.9, dust_strength=0.55, blob_count=4),
    # Magenta and indigo: glowing through the dark, deep dust lanes.
    "deep": Recipe(seed=59, band_normal=(0.10, 0.95, 0.30), band_width=0.46,
                   color_a="#F043B8", color_b="#5A2FD8", highlight="#FFB8F0", dust="#10061E",
                   coverage=0.72, brightness=1.0, dust_strength=0.75, blob_count=5),
}


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def nebula(d: np.ndarray, r: Recipe) -> np.ndarray:
    """RGBA floats in 0..1 for unit directions d (..., 3), celestial frame."""
    n1, n2, n3, n4 = (Noise3(r.seed + k) for k in range(4))
    rng = np.random.default_rng(r.seed)

    # where cloud may be: the band plus a few broad clouds
    bn = np.array(r.band_normal, dtype=float)
    bn /= np.linalg.norm(bn)
    lat = np.abs(d @ bn)
    band = np.exp(-(lat / r.band_width) ** 2)
    for _ in range(r.blob_count):
        c = rng.normal(size=3)
        c /= np.linalg.norm(c)
        size = rng.uniform(0.45, 0.7)
        band = np.maximum(band, 0.8 * np.exp(-((1.0 - d @ c) / (size * size * 0.5)) ** 1.3))
    # the band's edges wander
    band = np.clip(band * (0.7 + 0.6 * (fbm(n4, d * 1.1, 3) * 0.5 + 0.5)), 0.0, 1.0)

    # domain-warped fBm: one broad warp for billowing shapes, four octaves of cloud
    p = d * 1.35
    q = warp(n1, p, amount=0.55, octaves=3, scale=0.9)
    cloud = fbm(n3, q * 1.3, 4) * 0.5 + 0.5
    fine = fbm(n2, q * 4.2, 3, offset=(2.2, 8.8, 5.5)) * 0.5 + 0.5
    lo = 1.0 - r.coverage * band
    density = smoothstep(lo - 0.12, lo + 0.42, cloud) * band
    density = np.clip(density * (0.72 + 0.45 * fine), 0.0, 1.0)

    # filaments: soft ridges of a finer warped field, lit where they run through cloud
    q2 = warp(n2, q, amount=0.3, octaves=2, scale=2.0)
    ridge = 1.0 - np.abs(fbm(n1, q2 * 2.6, 4, offset=(3.3, 7.7, 1.1)))
    filament = (ridge ** 4) * smoothstep(0.1, 0.6, density) * 0.6

    # colour: two cloud colours by a slow field, a highlight on filaments
    mixf = smoothstep(-0.45, 0.45, fbm(n2, q * 0.6, 2, offset=(9.9, 4.4, 2.2)))[..., None]
    col = hexc(r.color_a) * (1 - mixf) + hexc(r.color_b) * mixf
    light = col * (density ** 1.2)[..., None] * r.brightness
    light += hexc(r.highlight) * (filament * r.brightness * 0.5)[..., None]
    # unresolved glow under the whole band, so the clouds sit in something
    light += col * (band ** 1.5 * 0.16 * r.brightness)[..., None]

    # dust: a few coherent dark lanes where the band is thick but cloud is thin
    lanes = smoothstep(0.55, 0.85, 1.0 - np.abs(fbm(n4, q * 1.6, 4, offset=(6.1, 2.9, 8.4))))
    dust = np.clip(lanes * band * smoothstep(0.7, 0.15, density), 0.0, 1.0) * r.dust_strength
    light *= (1.0 - dust * 0.85)[..., None]
    return np.concatenate([np.clip(light, 0, 1), dust[..., None]], axis=-1)


def quick(name: str, w: int = 768, h: int = 384) -> np.ndarray:
    """An equirectangular preview straight from the field (no atlas): seconds, for tuning."""
    lon = (np.arange(w) + 0.5) / w * 2 * np.pi - np.pi
    lat = np.pi / 2 - (np.arange(h) + 0.5) / h * np.pi
    lo, la = np.meshgrid(lon, lat)
    d = np.stack([np.cos(la) * np.sin(lo), np.sin(la), -np.cos(la) * np.cos(lo)], -1)
    return nebula(d, RECIPES[name]) * 255.0


def bake(name: str) -> np.ndarray:
    r = RECIPES[name]
    tiles = []
    for f in range(6):
        d = cube.face_directions(f)
        rgba = nebula(d, r)
        tiles.append(np.round(rgba * 255.0).astype(np.uint8))
    return cube.assemble(tiles)


def equirect(atlas: np.ndarray, w: int = 1024, h: int = 512) -> np.ndarray:
    lon = (np.arange(w) + 0.5) / w * 2 * np.pi - np.pi
    lat = np.pi / 2 - (np.arange(h) + 0.5) / h * np.pi
    lo, la = np.meshgrid(lon, lat)
    d = np.stack([np.cos(la) * np.sin(lo), np.sin(la), -np.cos(la) * np.cos(lo)], -1)
    return cube.sample(atlas, d)


def preview(name: str, atlas: np.ndarray, eq=None):
    eq = (equirect(atlas) if eq is None else eq) / 255.0
    light, dust = eq[..., :3], eq[..., 3:4]
    on_black = np.clip(light, 0, 1)
    day = np.array([0.30, 0.55, 0.92])
    on_day = np.clip(day * (1 - dust * 0.35) + light * 0.35, 0, 1)
    night = np.array([0.09, 0.08, 0.22])
    on_night = np.clip(night * (1 - dust) + light * 0.9, 0, 1)
    sheet = np.concatenate([on_black, on_day, on_night], axis=0)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.round(sheet * 255).astype(np.uint8)).save(PREVIEWS / f"nebula_{name}.jpg", quality=88)


def main(names=None) -> int:
    names = list(names or [])
    if "--quick" in names:
        names.remove("--quick")
        for name in names or RECIPES:
            preview(name, None, quick(name))
            print(f"previews/nebula_{name}.jpg (quick)")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    for name in names or RECIPES:
        atlas = bake(name)
        Image.fromarray(atlas).save(OUT / f"nebula_{name}.png", optimize=True)
        preview(name, atlas)
        kb = (OUT / f"nebula_{name}.png").stat().st_size / 1024
        print(f"nebula_{name}.png {atlas.shape[1]}x{atlas.shape[0]}, {kb:.0f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
