"""Review sheets for the 2D art:

    tools/art/previews/items.png  every item and particle sprite at 8x on mid grey,
                                  plus each item at 1x and 2x, and the particles
                                  tinted gold and turquoise the way the game will
    tools/art/previews/fx.png     the FX textures at 4x, on grey and on black

Run after the generators:  python tools/art/preview_items.py
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

from common import PREVIEWS, TEX, load_png, over, rel, save_png, upscale

GREY = (118, 118, 118)
ITEMS = ["item/meridian", "item/meridian_glow1", "item/meridian_glow2", "item/meridian_glow3", "item/starshard"]
PARTICLES = ["particle/star_glint", "particle/spark", "particle/shard_0", "particle/shard_1",
             "particle/shard_2", "particle/ring"]
FX = ["fx/slash", "fx/glow", "fx/beam"]
TINTS = [(255, 214, 120), (96, 232, 216)]


def canvas(w, h, colour=GREY):
    c = np.zeros((h, w, 4), dtype=np.uint8)
    c[..., :3] = colour
    c[..., 3] = 255
    return c


def tint(img, rgb_):
    out = img.copy()
    out[..., :3] = (out[..., :3].astype(int) * np.array(rgb_) // 255).astype(np.uint8)
    return out


def label(sheet: np.ndarray, text: str, x: int, y: int):
    im = Image.fromarray(sheet)
    ImageDraw.Draw(im).text((x, y), text, fill=(235, 235, 235, 255))
    sheet[...] = np.array(im)


def items_sheet():
    k = 8
    pad = 16
    cell_w = 32 * k + pad
    row_h = 32 * k + 40
    rows = 4
    w = pad + cell_w * max(len(ITEMS), len(PARTICLES))
    h = pad + row_h * rows
    sheet = canvas(w, h)
    # row 1: items at 8x, with 1x and 2x beside the label
    for i, name in enumerate(ITEMS):
        im = load_png(TEX / f"{name}.png")
        x = pad + i * cell_w
        over(sheet, upscale(im, k * 32 // im.shape[1]), x, pad)
        yb = pad + 32 * k + 4
        over(sheet, im, x, yb)
        over(sheet, upscale(im, 2 * 32 // im.shape[1] if im.shape[1] == 16 else 2), x + 40, yb)
        label(sheet, f"{name.split('/')[1]} {im.shape[1]}x{im.shape[0]}", x + 120, yb + 4)
    # row 2: particles white; rows 3-4: tinted
    for r, t in enumerate([None] + TINTS):
        y0 = pad + row_h * (r + 1)
        for i, name in enumerate(PARTICLES):
            im = load_png(TEX / f"{name}.png")
            if t is not None:
                im = tint(im, t)
            x = pad + i * cell_w
            scale = k * 32 // max(im.shape[:2])
            big = upscale(im, scale)
            over(sheet, big, x + (32 * k - big.shape[1]) // 2, y0 + (32 * k - big.shape[0]) // 2)
            tag = "white" if t is None else f"tint {t}"
            label(sheet, f"{name.split('/')[1]} {im.shape[1]}x{im.shape[0]} ({tag})", x, y0 + 32 * k + 6)
    return sheet


def fx_sheet():
    k = 4
    pad = 16
    ims = [load_png(TEX / f"{n}.png") for n in FX]
    w = pad * 4 + sum(im.shape[1] * k for im in ims) + 200
    h = pad * 3 + max(im.shape[0] * k for im in ims) * 2 + 40
    sheet = canvas(w, h)
    x = pad
    for n, im in zip(FX, ims):
        big = upscale(im, k)
        over(sheet, big, x, pad)
        black = canvas(big.shape[1], big.shape[0], (0, 0, 0))
        over(black, big, 0, 0)
        over(sheet, black, x, pad * 2 + max(i.shape[0] * k for i in ims))
        label(sheet, f"{n.split('/')[1]} {im.shape[1]}x{im.shape[0]}", x, h - 24)
        x += big.shape[1] + pad
    return sheet


def main():
    p1 = save_png(items_sheet(), PREVIEWS / "items.png")
    p2 = save_png(fx_sheet(), PREVIEWS / "fx.png")
    print("wrote", rel(p1))
    print("wrote", rel(p2))


if __name__ == "__main__":
    main()
