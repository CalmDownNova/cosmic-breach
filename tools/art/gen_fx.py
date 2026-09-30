"""FX textures, white on transparent (shape carried in alpha):

    textures/fx/slash.png   128x32  swept ribbon: crisp bright leading edge at the
                                    right (u = 1), fading to nothing on the left
                                    (u = 0); soft falloff at the top and bottom
    textures/fx/glow.png     64x64  soft radial glow, zero at the border
    textures/fx/beam.png    16x128  vertical beam: bright core, soft sides,
                                    fading out at both ends
    textures/fx/crack.png  128x128  ground cracks radiating from the centre: a
                                    crushed middle and jagged fissures that
                                    branch and thin toward the rim (the Comet
                                    Maul's slams; drawn dark, then as embers)

These are smooth on purpose (they are drawn on world geometry, not as items).
RGB is white everywhere, including fully transparent texels, so bilinear
filtering never pulls a dark fringe in.

Run:  python tools/art/gen_fx.py
"""
from __future__ import annotations

import numpy as np

from common import TEX, rel, save_png, smoothstep, supersample, white_alpha

OUT = TEX / "fx"


def slash(w: int = 128, h: int = 32) -> np.ndarray:
    edge_px = 5.0        # width of the bright leading edge
    edge_soft = 0.6      # its left boundary, crisp (pixels)
    body_peak = 0.78     # alpha of the trail right behind the edge
    tail_power = 1.9     # how fast the trail fades to the left
    band_soft = 0.34     # fraction of the height given to the top/bottom falloff

    rng = np.random.default_rng(7)
    streaks = rng.uniform(0.86, 1.0, size=h * 4)   # faint speed lines along the ribbon

    def f(x, y):
        xn = x / w                 # 0 at the tail, 1 at the head
        yn = y / h
        across = smoothstep(0.0, band_soft, yn) * smoothstep(0.0, band_soft, 1.0 - yn)
        # the edge is a touch taller than the trail so it reads as the blade's path
        across_edge = smoothstep(0.0, band_soft * 0.8, yn) * smoothstep(0.0, band_soft * 0.8, 1.0 - yn)
        trail = body_peak * xn ** tail_power
        idx = np.clip((y * 4).astype(int), 0, h * 4 - 1)
        trail = trail * streaks[idx]
        edge = smoothstep(w - edge_px - edge_soft, w - edge_px + edge_soft, x)
        return np.clip(np.maximum(trail * across, edge * across_edge), 0, 1)

    a = supersample(f, w, h, 4)
    a[a < 1.5 / 255] = 0
    return white_alpha(a)


def glow(size: int = 64) -> np.ndarray:
    c = size / 2
    R = size / 2 - 0.5

    def f(x, y):
        r = np.hypot(x - c, y - c) / R
        window = np.clip(1 - r * r, 0, 1) ** 2      # reaches exactly 0 at the rim
        core = np.exp(-(r / 0.22) ** 2)
        wide = np.exp(-(r / 0.55) ** 2)
        return np.clip(0.55 * core + 0.62 * wide, 0, 1) * window

    a = supersample(f, size, size, 4)
    a[a < 1.5 / 255] = 0
    return white_alpha(a)


def beam(w: int = 16, h: int = 128) -> np.ndarray:
    c = w / 2
    end_fade = 22.0      # pixels of fade at each end

    def f(x, y):
        d = np.abs(x - c) / c            # 0 at the core, 1 at the side border
        core = np.exp(-(np.abs(x - c) / 1.35) ** 2)
        sides = 0.55 * np.exp(-(np.abs(x - c) / 3.6) ** 2)
        window = np.clip(1 - d * d, 0, 1) ** 2
        across = np.clip(np.maximum(core, sides) * window * 1.05, 0, 1)
        along = smoothstep(0.0, end_fade, y) * smoothstep(0.0, end_fade, h - y)
        return across * along

    a = supersample(f, w, h, 4)
    a[a < 1.5 / 255] = 0
    return white_alpha(a)


def crack(size: int = 128, seed: int = 7) -> np.ndarray:
    """Fissures as polylines from the centre: each main crack walks outward in jagged steps,
    thinning as it goes and splitting now and then; a ring of short cracks crushes the middle."""
    rng = np.random.default_rng(seed)
    c = size / 2.0
    segs = []  # (x0, y0, x1, y1, w0, w1) in pixels

    def walk(x, y, ang, length, width, depth):
        step = 5.5
        travelled = 0.0
        heading = ang
        while travelled < length:
            # straight runs with sharp kinks, pulled back toward the outward heading: stone splits, it doesn't curl
            kink = rng.uniform(-0.55, 0.55) if rng.random() < 0.22 else rng.normal(0.0, 0.1)
            ang += kink + (heading - ang) * 0.25
            nx, ny = x + np.cos(ang) * step, y + np.sin(ang) * step
            w1 = width * (1.0 - (travelled + step) / length) + 0.4
            segs.append((x, y, nx, ny, width * (1.0 - travelled / length) + 0.4, w1))
            if depth < 2 and rng.random() < 0.2 and travelled > 10:
                side = rng.choice([-1, 1]) * rng.uniform(0.4, 0.75)
                walk(nx, ny, ang + side, (length - travelled) * rng.uniform(0.3, 0.55), w1 * 0.7, depth + 1)
            x, y = nx, ny
            travelled += step

    mains = 9
    angles = [2 * np.pi * i / mains + rng.uniform(-0.18, 0.18) for i in range(mains)]
    for ang in angles:
        r0 = rng.uniform(5.0, 8.0)
        walk(c + np.cos(ang) * r0, c + np.sin(ang) * r0, ang, rng.uniform(0.7, 0.95) * (c - 5) - r0, 2.8, 0)
    # the crushed middle: a broken ring between the fissures, and short splinters inside it
    ring_r = 9.5
    for a0, a1 in zip(angles, angles[1:] + [angles[0] + 2 * np.pi]):
        if rng.random() < 0.7:
            n = 3
            pts = [(c + np.cos(a) * (ring_r + rng.uniform(-1.2, 1.2)), c + np.sin(a) * (ring_r + rng.uniform(-1.2, 1.2)))
                   for a in np.linspace(a0, a1, n + 1)]
            for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
                segs.append((xa, ya, xb, yb, 1.5, 1.3))
    for i in range(10):
        ang = rng.uniform(0, 2 * np.pi)
        r0 = rng.uniform(2.0, 7.0)
        walk(c + np.cos(ang) * r0, c + np.sin(ang) * r0, ang + rng.uniform(-0.8, 0.8), rng.uniform(4.0, 8.0), 1.4, 2)
    s_arr = np.array(segs, dtype=np.float64)

    def f(x, y):
        best = np.zeros_like(x)
        for x0, y0, x1, y1, w0, w1 in s_arr:
            dx, dy = x1 - x0, y1 - y0
            ll = dx * dx + dy * dy
            t = np.clip(((x - x0) * dx + (y - y0) * dy) / ll, 0.0, 1.0)
            d = np.hypot(x - (x0 + t * dx), y - (y0 + t * dy))
            w = (w0 + (w1 - w0) * t) * 0.5
            best = np.maximum(best, 1.0 - smoothstep(w - 0.5, w + 0.6, d))
        r = np.hypot(x - c, y - c) / (c - 1)
        crushed = 0.35 * np.exp(-(r / 0.09) ** 2)   # a faint dent under the impact
        return np.clip(np.maximum(best * np.clip(1.15 - 0.35 * r, 0, 1), crushed), 0, 1) * (r < 1)

    a = supersample(f, size, size, 3)
    a[a < 1.5 / 255] = 0
    return white_alpha(a)


def main():
    for name, im in (("slash", slash()), ("glow", glow()), ("beam", beam()), ("crack", crack())):
        print("wrote", rel(save_png(im, OUT / f"{name}.png")), f"{im.shape[1]}x{im.shape[0]}")


if __name__ == "__main__":
    main()
