"""The Deep's block textures (16x16, in textures/block/).

The Deep is the only dark place in Aetheria (GDD 2.2): black basalt, dark glass, and
bioluminescent lichen in magenta and teal. Dark, but never a flat black: every texture
keeps enough value range to read by the lichen's light.

    umbral_basalt / _top           basalt columns with an indigo cast (a pillar: side and end)
    polished_umbral_basalt         a dark polished face with a faint violet sheen
    umbral_basalt_bricks           small dark bricks in an uneven bond
    rift_glass                     dark translucent glass, magenta and teal glints
    eclipsium_ore                  basalt with dark nuggets rimmed in gold
    eclipsium_block                dark metal plates trimmed with gold
    magenta_neon_lichen            glowing lichen patches (cutout)
    teal_neon_lichen

Run:  python tools/art/gen_blocks_deep.py
"""
from __future__ import annotations

import numpy as np

from blockart import brick_layout, by_share, fbm, from_ramp, ramp, rng_for, scatter, stamp
from common import TEX, rel, save_png

OUT = TEX / "block"

UMBRAL = ramp("#16131e", "#1f1b2a", "#292436", "#342e44", "#413953", "#524866")
SHEEN = ramp("#5e4f80", "#7a67a6")
RIFT = ramp("#140c24", "#1e1334", "#2b1c47", "#3b285e")
MAGENTA = ramp("#5c1254", "#9a2185", "#d93bbd", "#ff7ae0", "#ffd0f4")
TEAL = ramp("#0a4f4a", "#11867c", "#1ec3b1", "#5ef0de", "#cffff8")
ECLIPSE = ramp("#0f0b15", "#1b1424", "#2a2037")
GOLD = ramp("#7a4c18", "#b77f27", "#e2ae45", "#f8d77c", "#fff2c2")


def basalt_side(name="umbral_basalt") -> np.ndarray:
    """Columns about four texels wide, each lit on its left and shaded on its right."""
    r = rng_for(name)
    f = fbm(r, ((2, 0.3), (4, 0.4), (8, 0.3)), aspect=(1, 4))
    idx = by_share(f, [0.1, 0.25, 0.35, 0.22, 0.08])
    ys, xs = np.mgrid[0:16, 0:16]
    col = xs % 4
    idx = np.where(col == 0, np.maximum(idx, 3), idx)
    idx = np.where(col == 3, np.minimum(idx, 1), idx)
    img = from_ramp(np.clip(idx, 0, 5), UMBRAL)
    # joints between columns, and a few horizontal cracks
    for x in (3, 7, 11, 15):
        img[:, x, :3] = UMBRAL[0]
    for x, y in scatter(rng_for(name + "/cracks"), 4, 5):
        c = (x // 4) * 4
        img[y, c:c + 3, :3] = UMBRAL[1]
        img[(y + 1) % 16, c:c + 3, :3] = UMBRAL[4]
    for x, y in scatter(rng_for(name + "/sheen"), 3, 6):
        stamp(img, x, y, [(0, 0, SHEEN[0])])
    return img


def basalt_top() -> np.ndarray:
    """The end of the column bundle: four rounded column ends with dark gaps."""
    r = rng_for("umbral_basalt_top")
    f = fbm(r, ((4, 0.5), (8, 0.5)))
    idx = by_share(f, [0.2, 0.5, 0.3]) + 2
    ys, xs = np.mgrid[0:16, 0:16]
    cx, cy = xs % 8, ys % 8
    d = np.maximum(np.abs(cx - 3.5), np.abs(cy - 3.5)) + 0.45 * np.minimum(np.abs(cx - 3.5), np.abs(cy - 3.5))
    idx = np.where(d > 3.6, 0, idx)                               # gaps between columns
    idx = np.where((d > 2.7) & (d <= 3.6) & (cx + cy < 7), 4, idx)  # lit rim, top left
    idx = np.where((d > 2.7) & (d <= 3.6) & (cx + cy > 7), 1, idx)  # shaded rim, bottom right
    img = from_ramp(np.clip(idx, 0, 5), UMBRAL)
    return img


def polished_umbral_basalt() -> np.ndarray:
    r = rng_for("polished_umbral_basalt")
    f = fbm(r, ((2, 0.5), (4, 0.5)))
    img = from_ramp(by_share(f, [0.3, 0.5, 0.2]) + 2, UMBRAL)
    img[0, :, :3] = UMBRAL[0]
    img[:, 0, :3] = UMBRAL[0]
    img[15, :, :3] = UMBRAL[0]
    img[:, 15, :3] = UMBRAL[0]
    img[1, 1:15, :3] = UMBRAL[5]
    img[1:15, 1, :3] = UMBRAL[5]
    img[14, 2:15, :3] = UMBRAL[1]
    img[2:15, 14, :3] = UMBRAL[1]
    # a faint violet sheen across the face, top left to bottom right
    for k in range(4):
        img[3 + k, 6 - k, :3] = SHEEN[1] if k == 1 else SHEEN[0]
        img[10 + k // 2, 12 - k, :3] = SHEEN[0]
    return img


def umbral_basalt_bricks() -> np.ndarray:
    ids, mortar, iy, ix = brick_layout(4, 8, [0, 5, 2, 7])
    r = rng_for("umbral_basalt_bricks")
    f = fbm(r, ((8, 0.6), (16, 0.4)))
    idx = by_share(f, [0.2, 0.55, 0.25]) + 2
    tones = {i: int(r.integers(-1, 2)) for i in np.unique(ids)}
    idx = np.clip(idx + np.vectorize(tones.get)(ids), 1, 5)
    body = ~mortar
    idx[body & (iy == 0)] = np.maximum(idx[body & (iy == 0)], 4)
    idx[body & (ix == 0)] = np.maximum(idx[body & (ix == 0)], 3)
    idx[body & (iy == 2)] = np.minimum(idx[body & (iy == 2)], 2)
    img = from_ramp(idx, UMBRAL)
    img[mortar, :3] = UMBRAL[0]
    return img


def rift_glass() -> np.ndarray:
    """Dark smoked glass: mostly see-through, a firmer frame, glints of the lichen's colours."""
    r = rng_for("rift_glass")
    f = fbm(r, ((2, 0.5), (4, 0.5)))
    idx = by_share(f, [0.4, 0.4, 0.2])
    img = from_ramp(idx, RIFT)
    alpha = np.array([165, 180, 195])[idx]
    ys, xs = np.mgrid[0:16, 0:16]
    frame = (xs == 0) | (ys == 0) | (xs == 15) | (ys == 15)
    img[frame, :3] = RIFT[3]
    alpha[frame] = 235
    img[(xs == 0) | (ys == 0), :3] = RIFT[3]
    # glints: a magenta streak top left, a teal one bottom right
    for k, (x, y) in enumerate(((2, 4), (3, 3), (4, 2))):
        img[y, x, :3] = MAGENTA[3] if k == 1 else MAGENTA[2]
        alpha[y, x] = 230
    for k, (x, y) in enumerate(((11, 13), (12, 12), (13, 11))):
        img[y, x, :3] = TEAL[3] if k == 1 else TEAL[2]
        alpha[y, x] = 230
    img[..., 3] = alpha
    return img


ECLIPSIUM_BLOBS = [
    [(0, 0, 0), (1, 0, 1), (0, 1, 1), (1, 1, 0), (2, 1, 1), (1, 2, 1)],
    [(1, 0, 0), (0, 1, 0), (1, 1, 1), (2, 1, 0), (1, 2, 0), (2, 2, 1)],
    [(0, 0, 1), (1, 0, 0), (1, 1, 0), (0, 1, 0), (2, 2, 1), (1, 2, 1)],
]


def eclipsium_ore() -> np.ndarray:
    """Dark nuggets with a gold rim: bright on the lit side, deep amber on the shadow side."""
    img = basalt_side("eclipsium_ore")
    r = rng_for("eclipsium_ore/nuggets")
    for i, (x, y) in enumerate(scatter(r, 5, 5.2)):
        blob = ECLIPSIUM_BLOBS[(i + int(r.integers(3))) % len(ECLIPSIUM_BLOBS)]
        cells = {(dx, dy) for dx, dy, _ in blob}
        pattern = []
        for dx, dy, _ in blob:
            for ox, oy in ((-1, 0), (0, -1), (1, 0), (0, 1)):
                n = (dx + ox, dy + oy)
                if n not in cells:
                    lit = ox < 0 or oy < 0
                    pattern.append((n[0], n[1], GOLD[3] if lit else GOLD[1]))
        pattern += [(dx, dy, ECLIPSE[s]) for dx, dy, s in blob]
        pattern.append((min(c[0] for c in cells), min(c[1] for c in cells) - 1, GOLD[4]))
        stamp(img, x, y, pattern)
    return img


def eclipsium_block() -> np.ndarray:
    r = rng_for("eclipsium_block")
    f = fbm(r, ((2, 0.5), (4, 0.5)), aspect=(4, 1))
    idx = by_share(f, [0.3, 0.5, 0.2])
    img = from_ramp(idx, ECLIPSE)
    ys, xs = np.mgrid[0:16, 0:16]
    # a gold frame, lit top left, and a gold star-cross in the middle of each quarter
    img[(ys == 0) | (xs == 0), :3] = GOLD[3]
    img[(ys == 15) | (xs == 15), :3] = GOLD[0]
    img[0, 15, :3] = GOLD[1]
    img[15, 0, :3] = GOLD[1]
    img[(ys == 1) & (xs > 0) & (xs < 15), :3] = ECLIPSE[2]
    img[(xs == 1) & (ys > 0) & (ys < 15), :3] = ECLIPSE[2]
    for cx, cy in ((7, 7),):
        for k in range(-4, 5):
            img[cy, cx + k, :3] = GOLD[2] if abs(k) < 3 else GOLD[1]
            img[cy + k, cx, :3] = GOLD[2] if abs(k) < 3 else GOLD[1]
        img[cy, cx, :3] = GOLD[4]
        for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            img[cy + dy, cx + dx, :3] = GOLD[1]
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img[y, x, :3] = GOLD[3]
        img[y + 1, x + 1, :3] = GOLD[0]
    return img


def lichen(name: str, colours) -> np.ndarray:
    """Glow-lichen style: small clusters over the face, each with a bright heart."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    r = rng_for(name)
    shapes = [
        [(0, 0, 2), (1, 0, 1), (0, 1, 1)],
        [(0, 0, 3), (1, 0, 2), (0, 1, 2), (1, 1, 1), (2, 1, 0)],
        [(1, 0, 2), (0, 1, 2), (1, 1, 4), (2, 1, 2), (1, 2, 1)],
        [(0, 0, 2), (1, 1, 1)],
        [(0, 0, 1)],
    ]
    for i, (x, y) in enumerate(scatter(r, 22, 2.6)):
        shape = shapes[int(r.integers(len(shapes)))] if i >= 4 else shapes[2]
        stamp(img, x, y, [(dx, dy, colours[s]) for dx, dy, s in shape])
    return img


def build() -> dict[str, np.ndarray]:
    return {
        "umbral_basalt": basalt_side(),
        "umbral_basalt_top": basalt_top(),
        "polished_umbral_basalt": polished_umbral_basalt(),
        "umbral_basalt_bricks": umbral_basalt_bricks(),
        "rift_glass": rift_glass(),
        "eclipsium_ore": eclipsium_ore(),
        "eclipsium_block": eclipsium_block(),
        "magenta_neon_lichen": lichen("magenta_neon_lichen", MAGENTA),
        "teal_neon_lichen": lichen("teal_neon_lichen", TEAL),
    }


def main():
    built = build()
    for name, img in built.items():
        save_png(img, OUT / f"{name}.png")
    print("wrote", len(built), "Deep block textures to", rel(OUT))


if __name__ == "__main__":
    main()
