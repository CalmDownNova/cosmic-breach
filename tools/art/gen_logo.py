"""The mod's logo for the mod list (F1): the Starfall Shard beside the name in a small pixel font.

Writes src/main/resources/cosmicbreach_logo.png (named by logoFile in the mods.toml). Crisp pixel art on a
transparent ground: every source pixel is a square of SCALE pixels, and the mods.toml turns blur off.

    python tools/art/gen_logo.py
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

from common import REPO, TEX, rgba

SCALE = 4
OUT = REPO / "src" / "main" / "resources" / "cosmicbreach_logo.png"

# A 5 by 7 pixel font, just the letters the name needs.
FONT = {
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
}

FILL = rgba("#FFF4D6")
EDGE = rgba("#F3C969")
SHADOW = rgba("#3B2A5C")
STAR = rgba("#FFFFFF")


def text_cells(word: str) -> list[tuple[int, int]]:
    cells = []
    for i, ch in enumerate(word):
        for y, row in enumerate(FONT[ch]):
            for x, bit in enumerate(row):
                if bit == "1":
                    cells.append((i * 6 + x, y))
    return cells


def main() -> None:
    shard = np.array(Image.open(TEX / "item" / "starfall_shard.png").convert("RGBA"))
    # the grid in source pixels: the shard (16) and a gap, then two lines of 7-pixel letters and a gap
    width = 16 + 3 + 6 * 6 - 1 + 2
    height = 20
    grid = np.zeros((height, width, 4), dtype=np.uint8)
    grid[2:18, 0:16] = shard
    x0 = 19
    for line, word in enumerate(["COSMIC", "BREACH"]):
        y0 = 2 + line * 9
        cells = text_cells(word)
        for (cx, cy) in cells:  # a shadow one down and one right, drawn first
            if 0 <= y0 + cy + 1 < height and x0 + cx + 1 < width:
                grid[y0 + cy + 1, x0 + cx + 1] = SHADOW
        for (cx, cy) in cells:
            grid[y0 + cy, x0 + cx] = EDGE if cy >= 5 else FILL
    for (sx, sy) in [(17, 1), (width - 1, 0), (18, 17), (width - 2, 19)]:
        if grid[sy, sx, 3] == 0:
            grid[sy, sx] = STAR
    img = Image.fromarray(grid).resize((width * SCALE, height * SCALE), Image.NEAREST)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(f"wrote {OUT} ({img.size[0]} by {img.size[1]})")


if __name__ == "__main__":
    main()
