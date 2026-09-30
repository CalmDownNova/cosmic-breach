"""Review sheets for the World lane's textures (W1):

    tools/art/previews/blocks.png   every block texture at 8x on mid grey, with a 3x3
                                    tiling at 2x beside it (seams show up there)
    tools/art/previews/items2.png   every new item at 8x, 2x and 1x on mid grey, and at
                                    4x on the sky blue of the Reach

Run after the generators:  python tools/art/preview_blocks.py
For a quick look at a few textures:  python tools/art/preview_blocks.py out.png starfall_stone meteorite ...
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from common import PREVIEWS, TEX, load_png, over, rel, save_png, upscale

GREY = (118, 118, 118)
SKY = (132, 178, 232)

BLOCKS = [
    # the Reach
    "starfall_stone", "polished_starfall_stone", "starfall_stone_bricks", "glimmer_grass_top",
    "glimmer_grass_side", "spire_quartz", "starsteel_ore", "starsteel_block", "meteorite",
    "halo_moss", "halo_moss_plant", "starbloom", "starbloom_stage0", "starbloom_stage1",
    "starbloom_stage2", "starbloom_stage3", "breach_frame_top", "breach_frame_side",
    # the Drift
    "driftstone", "driftstone_bricks", "nebulite_ore", "nebulite_block", "driftwood_log",
    "driftwood_log_top", "stripped_driftwood_log", "stripped_driftwood_log_top", "driftwood_planks",
    "driftwood_door_top", "driftwood_door_bottom", "driftwood_trapdoor", "rimeglass",
    # the Deep
    "umbral_basalt", "umbral_basalt_top", "polished_umbral_basalt", "umbral_basalt_bricks",
    "rift_glass", "eclipsium_ore", "eclipsium_block", "magenta_neon_lichen", "teal_neon_lichen",
]

ITEMS = [
    "raw_starsteel", "starsteel_ingot", "starsteel_nugget", "raw_nebulite", "nebulite_ingot",
    "raw_eclipsium", "eclipsium_ingot", "eclipsium_nugget", "heartstone", "gyre_core", "gyre_blade",
    "leviathan_scale", "leviathan_pearl", "prism_heart", "silent_sigil", "solar_ember", "hymn_crystal",
    "solar_heart", "umbral_silk", "starbloom_seeds", "driftwood_door", "starshard",
]


def canvas(w, h, colour=GREY):
    c = np.zeros((h, w, 4), dtype=np.uint8)
    c[..., :3] = colour
    c[..., 3] = 255
    return c


def label(sheet: np.ndarray, text: str, x: int, y: int):
    im = Image.fromarray(sheet)
    ImageDraw.Draw(im).text((x, y), text, fill=(240, 240, 240, 255))
    sheet[...] = np.array(im)


def tex(name: str, folder: str) -> np.ndarray:
    path = TEX / folder / f"{name}.png"
    if not path.exists():
        img = np.zeros((16, 16, 4), dtype=np.uint8)
        img[::2, ::2] = (255, 0, 255, 255)
        img[1::2, 1::2] = (255, 0, 255, 255)
        return img
    return load_png(path)


def blocks_sheet(names, cols=6):
    k, t = 8, 2
    cell_w = 16 * k + 8 + 16 * 3 * t + 16
    cell_h = 16 * k + 22
    rows = (len(names) + cols - 1) // cols
    sheet = canvas(16 + cell_w * cols, 16 + cell_h * rows)
    for i, name in enumerate(names):
        im = tex(name, "block")[:16]
        x = 16 + (i % cols) * cell_w
        y = 16 + (i // cols) * cell_h
        over(sheet, upscale(im, k), x, y)
        over(sheet, upscale(np.tile(im, (3, 3, 1)), t), x + 16 * k + 8, y)
        label(sheet, name, x, y + 16 * k + 5)
    return sheet


def items_sheet(names, cols=6):
    k = 8
    cell_w = 16 * k + 16 + 16 * 4 + 16
    cell_h = 16 * k + 22
    rows = (len(names) + cols - 1) // cols
    sheet = canvas(16 + cell_w * cols, 16 + cell_h * rows)
    for i, name in enumerate(names):
        im = tex(name, "item")
        x = 16 + (i % cols) * cell_w
        y = 16 + (i // cols) * cell_h
        over(sheet, upscale(im, k), x, y)
        sky = canvas(16 * 4, 16 * 4, SKY)
        over(sky, upscale(im, 4), 0, 0)
        over(sheet, sky, x + 16 * k + 16, y)
        over(sheet, upscale(im, 2), x + 16 * k + 16, y + 16 * 4 + 8)
        over(sheet, im, x + 16 * k + 16 + 40, y + 16 * 4 + 8)
        label(sheet, name, x, y + 16 * k + 5)
    return sheet


def main(argv=None):
    argv = [] if argv is None else argv
    if argv:
        out = Path(argv[0])
        names = argv[1:]
        blocks = [n for n in names if (TEX / "block" / f"{n}.png").exists()]
        items = [n for n in names if n not in blocks]
        parts = []
        if blocks:
            parts.append(blocks_sheet(blocks, cols=min(4, len(blocks))))
        if items:
            parts.append(items_sheet(items, cols=min(4, len(items))))
        w = max(p.shape[1] for p in parts)
        sheet = canvas(w, sum(p.shape[0] for p in parts))
        y = 0
        for p in parts:
            over(sheet, p, 0, y)
            y += p.shape[0]
        save_png(sheet, out)
        print("wrote", out)
        return
    p1 = save_png(blocks_sheet(BLOCKS), PREVIEWS / "blocks.png")
    p2 = save_png(items_sheet(ITEMS), PREVIEWS / "items2.png")
    print("wrote", rel(p1))
    print("wrote", rel(p2))


if __name__ == "__main__":
    main(sys.argv[1:])
